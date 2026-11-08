
import re
from typing import Any
from uuid import uuid4

import requests
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from firebase_admin import auth, firestore

from firebase_setup import db
from mpesa_service import (
    initiate_stk_push,
    query_stk_push_status
)

router = APIRouter(prefix="/payments", tags=["Payments"])

ACTIVE_STATES = {"INITIATING", "PENDING", "UNKNOWN", "PAID"}
FINAL_STATES = {"SUCCESS", "FAILED"}


class InitiatePaymentInput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    invoiceId: str = Field(min_length=1, max_length=200)
    phoneNumber: str = Field(min_length=10, max_length=15)


class MpesaCallbackInput(BaseModel):
    model_config = ConfigDict(extra="allow")
    Body: dict[str, Any]


def verify_client(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication required.")

    token = authorization.removeprefix("Bearer ").strip()

    try:
        decoded = auth.verify_id_token(
            token, check_revoked=True
        )
    except Exception:
        raise HTTPException(401, "Invalid authentication token.")

    uid = decoded["uid"]

    if not decoded.get("email_verified", False):
        raise HTTPException(403, "Email verification required.")

    user = db.collection("users").document(uid).get()

    if (
        not user.exists
        or user.get("role") != "CLIENT"
        or user.get("email") != decoded.get("email")
    ):
        raise HTTPException(403, "Verified client required.")

    return uid


def normalize_phone(phone: str) -> str:
    cleaned = re.sub(r"[\s-]", "", phone)

    if cleaned.startswith("+254"):
        cleaned = cleaned[1:]
    elif cleaned.startswith("0"):
        cleaned = "254" + cleaned[1:]

    if not re.fullmatch(r"254(?:7|1)\d{8}", cleaned):
        raise HTTPException(
            400, "Enter a valid Kenyan mobile number."
        )

    return cleaned


def finalize_payment(payment_id: str, result: dict):
    """
    Apply an independently queried Daraja result.

    Invoice and payment changes happen atomically.
    Repeated finalization is safe.
    """
    payment_ref = db.collection("payments").document(payment_id)
    transaction = db.transaction()

    @firestore.transactional
    def apply_result(tx):
        payment_snapshot = payment_ref.get(transaction=tx)

        if not payment_snapshot.exists:
            raise HTTPException(404, "Payment not found.")

        payment = payment_snapshot.to_dict()
        invoice_ref = db.collection("invoices").document(
            payment["invoiceId"]
        )
        invoice_snapshot = invoice_ref.get(transaction=tx)

        if not invoice_snapshot.exists:
            raise HTTPException(409, "Invoice no longer exists.")

        invoice = invoice_snapshot.to_dict()

        if payment.get("status") in FINAL_STATES:
            return payment["status"]

        if (
            invoice.get("currentPaymentId") != payment_id
            or payment.get("clientId") != invoice.get("clientId")
            or payment.get("amountCents") != invoice.get("totalCents")
        ):
            raise HTTPException(
                409, "Payment and invoice do not match."
            )

        verified_status = result["status"]

        if verified_status == "SUCCESS":
            if invoice.get("status") != "APPROVED":
                raise HTTPException(
                    409, "Invoice is not approved."
                )

            tx.update(payment_ref, {
                "status": "SUCCESS",
                "resultCode": result["resultCode"],
                "resultDescription": result["description"],
                "verifiedAt": firestore.SERVER_TIMESTAMP
            })

            tx.update(invoice_ref, {
                "status": "PAID",
                "paymentState": "PAID",
                "paidAt": firestore.SERVER_TIMESTAMP
            })

            return "SUCCESS"

        if verified_status == "FAILED":
            tx.update(payment_ref, {
                "status": "FAILED",
                "resultCode": result["resultCode"],
                "resultDescription": result["description"],
                "verifiedAt": firestore.SERVER_TIMESTAMP
            })

            tx.update(invoice_ref, {
                "paymentState": "FAILED"
            })

            return "FAILED"

        return payment.get("status", "UNKNOWN")

    return apply_result(transaction)


def verify_and_finalize(payment_id: str):
    payment_ref = db.collection("payments").document(payment_id)
    snapshot = payment_ref.get()

    if not snapshot.exists:
        raise HTTPException(404, "Payment not found.")

    payment = snapshot.to_dict()

    if payment.get("status") in FINAL_STATES:
        return {
            "status": payment["status"],
            "resultCode": payment.get("resultCode"),
            "description": payment.get(
                "resultDescription", "Already finalized."
            )
        }

    checkout_id = payment.get("checkoutRequestId")

    if not checkout_id:
        return {
            "status": payment.get("status", "UNKNOWN"),
            "resultCode": None,
            "description": "Checkout ID not yet available."
        }

    try:
        result = query_stk_push_status(checkout_id)
    except (requests.RequestException, ValueError):
        return {
            "status": "UNKNOWN",
            "resultCode": None,
            "description": "Daraja verification unavailable."
        }

    # Daraja can return 4999 while a transaction
    # is still being processed.
    if str(result.get("resultCode")) == "4999":
        result["status"] = "PENDING"

    if result["status"] in FINAL_STATES:
        final_status = finalize_payment(payment_id, result)
        result["status"] = final_status

    return result


@router.post("/initiate", status_code=202)
def initiate_payment(
    payload: InitiatePaymentInput,
    authorization: str | None = Header(default=None)
):
    client_uid = verify_client(authorization)
    phone = normalize_phone(payload.phoneNumber)

    invoice_ref = db.collection("invoices").document(
        payload.invoiceId
    )

    attempt_id = uuid4().hex
    payment_ref = db.collection("payments").document(attempt_id)

    transaction = db.transaction()

    @firestore.transactional
    def reserve_payment(tx):
        snapshot = invoice_ref.get(transaction=tx)

        if not snapshot.exists:
            raise HTTPException(404, "Invoice not found.")

        invoice = snapshot.to_dict()

        if invoice.get("clientId") != client_uid:
            raise HTTPException(403, "Invoice access denied.")

        if invoice.get("status") != "APPROVED":
            raise HTTPException(
                409, "Only approved invoices can be paid."
            )

        if invoice.get("paymentState") in ACTIVE_STATES:
            raise HTTPException(
                409, "Payment already active or completed."
            )

        # Protect against an earlier processing response
        # that was incorrectly classified as FAILED.
        previous_payment_id = invoice.get("currentPaymentId")

        if previous_payment_id:
            previous_payment_ref = db.collection(
                "payments"
            ).document(previous_payment_id)

            previous_payment = previous_payment_ref.get(
                transaction=tx
            )

            if not previous_payment.exists:
                raise HTTPException(
                    409,
                    "Previous payment record is missing. "
                    "Payment requires review."
                )

            previous_data = previous_payment.to_dict()

            if (
                previous_data.get("invoiceId") != payload.invoiceId
                or previous_data.get("clientId") != client_uid
            ):
                raise HTTPException(
                    409,
                    "Previous payment does not match this invoice."
                )

            previous_status = previous_data.get("status")
            previous_code = previous_data.get("resultCode")

            if previous_status != "FAILED":
                raise HTTPException(
                    409,
                    "Previous payment is not confirmed as failed."
                )

            # ResultCode 4999 means the earlier query
            # did not establish a final payment outcome.
            if str(previous_code) == "4999":
                raise HTTPException(
                    409,
                    "Previous payment is still unconfirmed. "
                    "Do not initiate another payment."
                )

            # Require an explicitly recognized failure code.
            confirmed_failure_codes = {1032, 1037}

            if (
                not isinstance(previous_code, int)
                or previous_code not in confirmed_failure_codes
            ):
                raise HTTPException(
                    409,
                    "Previous payment failure requires verification "
                    "before retrying."
                )

        total_cents = invoice.get("totalCents")

        if (
            type(total_cents) is not int
            or total_cents <= 0
            or total_cents % 100 != 0
        ):
            raise HTTPException(
                400, "Amount must be in whole KES."
            )

        tx.update(invoice_ref, {
            "paymentState": "INITIATING",
            "currentPaymentId": attempt_id
        })

        tx.create(payment_ref, {
            "invoiceId": payload.invoiceId,
            "clientId": client_uid,
            "caregiverId": invoice.get("caregiverId"),
            "amountCents": total_cents,
            "currency": "KES",
            "phoneNumber": phone,
            "provider": "MPESA_SANDBOX",
            "status": "INITIATING",
            "createdAt": firestore.SERVER_TIMESTAMP
        })

        return total_cents // 100

    amount_kes = reserve_payment(transaction)

    try:
        result = initiate_stk_push(
            phone_number=phone,
            amount_kes=amount_kes,
            account_reference="HC" + attempt_id[:10]
        )
    except (requests.RequestException, ValueError) as error:
        # Preserve uncertainty: Daraja may have received
        # the request even if our connection failed.
        transaction = db.transaction()

        @firestore.transactional
        def mark_unknown(tx):
            payment = payment_ref.get(transaction=tx)
            invoice = invoice_ref.get(transaction=tx)

            if (
                payment.exists
                and invoice.exists
                and invoice.get("currentPaymentId") == attempt_id
            ):
                tx.update(payment_ref, {
                    "status": "UNKNOWN",
                    "errorType": type(error).__name__,
                    "updatedAt": firestore.SERVER_TIMESTAMP
                })
                tx.update(invoice_ref, {
                    "paymentState": "UNKNOWN"
                })

        mark_unknown(transaction)

        raise HTTPException(
            502,
            "Payment initiation uncertain. Do not retry yet."
        )

    # Save both records together.
    transaction = db.transaction()

    @firestore.transactional
    def mark_pending(tx):
        payment = payment_ref.get(transaction=tx)
        invoice = invoice_ref.get(transaction=tx)

        if (
            not payment.exists
            or not invoice.exists
            or invoice.get("currentPaymentId") != attempt_id
        ):
            raise HTTPException(
                409, "Payment reservation changed."
            )

        tx.update(payment_ref, {
            "status": "PENDING",
            "checkoutRequestId": result["checkoutRequestId"],
            "merchantRequestId": result.get("merchantRequestId"),
            "updatedAt": firestore.SERVER_TIMESTAMP
        })

        tx.update(invoice_ref, {
            "paymentState": "PENDING"
        })

    mark_pending(transaction)

    # A callback may have arrived before the checkout ID
    # was stored. If so, verify the result now.
    unmatched = db.collection(
        "mpesaUnmatchedCallbacks"
    ).document(result["checkoutRequestId"]).get()

    if unmatched.exists:
        verify_and_finalize(attempt_id)

    return {
        "paymentId": attempt_id,
        "invoiceId": payload.invoiceId,
        "status": "PENDING",
        "amountKES": amount_kes,
        "checkoutRequestId": result["checkoutRequestId"],
        "message": result["message"]
    }


@router.post("/callback")
def mpesa_callback(payload: MpesaCallbackInput):
    callback = payload.Body.get("stkCallback")

    if not isinstance(callback, dict):
        raise HTTPException(400, "Invalid callback format.")

    checkout_id = callback.get("CheckoutRequestID")
    result_code = callback.get("ResultCode")

    if (
        not isinstance(checkout_id, str)
        or not checkout_id
        or type(result_code) is not int
    ):
        raise HTTPException(400, "Invalid callback fields.")

    matches = (
        db.collection("payments")
        .where("checkoutRequestId", "==", checkout_id)
        .limit(1)
        .get()
    )

    if not matches:
        db.collection("mpesaUnmatchedCallbacks").document(
            checkout_id
        ).set({
            "checkoutRequestId": checkout_id,
            "resultCode": result_code,
            "resultDescription": str(
                callback.get("ResultDesc", "")
            )[:500],
            "receivedAt": firestore.SERVER_TIMESTAMP
        })

        return {
            "ResultCode": 0,
            "ResultDesc": "Received"
        }

    payment_ref = matches[0].reference

    payment_ref.update({
        "callbackReceived": True,
        "callbackResultCode": result_code,
        "callbackResultDescription": str(
            callback.get("ResultDesc", "")
        )[:500],
        "callbackReceivedAt": firestore.SERVER_TIMESTAMP
    })

    # Callback content is untrusted. Query Daraja
    # independently before changing payment status.
    verify_and_finalize(payment_ref.id)

    return {
        "ResultCode": 0,
        "ResultDesc": "Received"
    }


@router.get("/{payment_id}/status")
def get_payment_status(
    payment_id: str,
    authorization: str | None = Header(default=None)
):
    client_uid = verify_client(authorization)

    payment_ref = db.collection("payments").document(payment_id)
    snapshot = payment_ref.get()

    if not snapshot.exists:
        raise HTTPException(404, "Payment not found.")

    payment = snapshot.to_dict()

    if payment.get("clientId") != client_uid:
        raise HTTPException(403, "Payment access denied.")

    result = verify_and_finalize(payment_id)

    return {
        "paymentId": payment_id,
        "invoiceId": payment["invoiceId"],
        "status": result["status"],
        "resultCode": result.get("resultCode"),
        "message": result["description"]
    }
