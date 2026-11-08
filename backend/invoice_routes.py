
from datetime import date, datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Header, HTTPException
from firebase_admin import auth, firestore
from pydantic import BaseModel, ConfigDict, Field, StrictInt

from firebase_setup import db
from invoice_ml_predictor import analyze_invoice_ml


router = APIRouter(prefix="/invoices", tags=["Invoices"])


class InvoiceItemInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    serviceId: str = Field(min_length=1, max_length=100)
    quantity: StrictInt = Field(ge=1, le=1440)


class CreateInvoiceInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    assignmentId: str = Field(min_length=1, max_length=150)
    serviceDate: date
    requestId: UUID
    items: list[InvoiceItemInput] = Field(min_length=1, max_length=10)


def get_verified_caregiver(authorization: str | None):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication token is required")

    token = authorization[7:].strip()

    try:
        decoded = auth.verify_id_token(token, check_revoked=True)
    except Exception:
        raise HTTPException(401, "Invalid or expired authentication token")

    uid = decoded["uid"]

    if decoded.get("email_verified") is not True:
        raise HTTPException(403, "Email verification required")

    profile = db.collection("users").document(uid).get()

    if not profile.exists:
        raise HTTPException(403, "User profile not found")

    user = profile.to_dict()

    if (
        user.get("role") != "CAREGIVER"
        or user.get("email") != decoded.get("email")
    ):
        raise HTTPException(403, "Caregiver access required")

    return uid, decoded.get("email")


@router.post("", status_code=201)
def create_invoice(
    payload: CreateInvoiceInput,
    authorization: str | None = Header(default=None)
):
    caregiver_uid, caregiver_email = get_verified_caregiver(
        authorization
    )

    today = datetime.now(timezone.utc).date()

    if payload.serviceDate > today:
        raise HTTPException(400, "Service date cannot be in the future")

    if payload.serviceDate < today - timedelta(days=365):
        raise HTTPException(400, "Service date is too far in the past")

    assignment_ref = db.collection("caregiverInvites").document(
        payload.assignmentId
    )

    assignment_snapshot = assignment_ref.get()

    if not assignment_snapshot.exists:
        raise HTTPException(404, "Assignment not found")

    assignment = assignment_snapshot.to_dict()

    if (
        assignment.get("status") != "ACTIVE"
        or assignment.get("caregiverId") != caregiver_uid
        or assignment.get("caregiverEmail") != caregiver_email
    ):
        raise HTTPException(403, "You do not have an active assignment")

    client_id = assignment.get("clientId")
    recipient_id = assignment.get("recipientId")

    if not client_id or not recipient_id:
        raise HTTPException(400, "Assignment information is incomplete")

    recipient_snapshot = db.collection("careRecipients").document(
        recipient_id
    ).get()

    if (
        not recipient_snapshot.exists
        or recipient_snapshot.to_dict().get("clientId") != client_id
    ):
        raise HTTPException(400, "Invalid care recipient assignment")

    invoice_items = []
    total_cents = 0

    for item in payload.items:
        service_snapshot = db.collection("services").document(
            item.serviceId
        ).get()

        if not service_snapshot.exists:
            raise HTTPException(400, "Selected service does not exist")

        service = service_snapshot.to_dict()

        if service.get("isActive") is not True:
            raise HTTPException(400, "Selected service is inactive")

        rate = service.get("rateCents")
        unit = service.get("billingUnit")
        name = service.get("name")

        if (
            type(rate) is not int
            or rate <= 0
            or rate > 100_000_000
            or not isinstance(name, str)
            or not name.strip()
        ):
            raise HTTPException(400, "Invalid service configuration")

        if unit == "HOUR":
            if item.quantity % 15 != 0:
                raise HTTPException(
                    400,
                    "Duration must be in 15-minute increments"
                )

            # Round the result to the nearest cent.
            line_total = (rate * item.quantity + 30) // 60

        elif unit == "SESSION":
            if item.quantity > 100:
                raise HTTPException(400, "Maximum 100 sessions per item")

            line_total = rate * item.quantity

        else:
            raise HTTPException(400, "Unsupported billing unit")

        total_cents += line_total

        invoice_items.append({
            "serviceId": item.serviceId,
            "serviceName": name,
            "billingUnit": unit,
            "unitRateCents": rate,
            "quantity": item.quantity,
            "lineTotalCents": line_total
        })

    if total_cents > 1_000_000_000:
        raise HTTPException(400, "Invoice amount exceeds the limit")

    # Analyze the invoice using the trained Isolation Forest model.
    ml_result = analyze_invoice_ml({
        "items": invoice_items,
        "totalCents": total_cents
    })

    # One request ID identifies one submission attempt.
    # Retrying the same request cannot create another invoice.
    invoice_id = f"{caregiver_uid}_{payload.requestId.hex}"
    invoice_ref = db.collection("invoices").document(invoice_id)

    invoice_data = {
        "assignmentId": payload.assignmentId,
        "clientId": client_id,
        "caregiverId": caregiver_uid,
        "recipientId": recipient_id,
        "clientName": assignment.get("clientName", ""),
        "recipientName": assignment.get("recipientName", ""),
        "serviceDate": payload.serviceDate.isoformat(),
        "items": invoice_items,
        "totalCents": total_cents,
        "currency": "KES",
        "status": "PENDING_APPROVAL",

        # Primary anomaly detection fields (used by Android).
        "anomalyStatus": ml_result["status"],
        "isAnomaly": ml_result["isAnomaly"],
        "anomalyReasons": (
            ["Unusual billing pattern detected by AI."]
            if ml_result["isAnomaly"]
            else []
        ),
        "anomalyDetectionMethod": ml_result["detectionMethod"],

        # Additional machine-learning information.
        "mlAnomalyScore": ml_result["anomalyScore"],
        "mlModelVersion": ml_result["modelVersion"],

        "createdAt": firestore.SERVER_TIMESTAMP,
    }

    try:
        # create() fails if the document already exists.
        invoice_ref.create(invoice_data)

    except Exception as error:
        from google.api_core.exceptions import AlreadyExists

        if isinstance(error, AlreadyExists):
            raise HTTPException(
                409,
                "This invoice submission has already been received"
            )

        raise

    return {
        "invoiceId": invoice_id,
        "status": "PENDING_APPROVAL",
        "totalCents": total_cents,
        "currency": "KES",
        "anomalyStatus": ml_result["status"],
        "isAnomaly": ml_result["isAnomaly"],
        "anomalyDetectionMethod": ml_result["detectionMethod"],
        "mlAnomalyScore": ml_result["anomalyScore"],
        "mlModelVersion": ml_result["modelVersion"]
    }

# #Why is requestId included?
# Imagine the caregiver presses Submit Invoice, but their internet connection briefly drops after Firestore saves the invoice.
# They might press Submit again, accidentally creating a duplicate invoice.
# To reduce that risk, the Android application will generate a unique requestId for each submission and reuse it when retrying. The backend uses that ID to prevent creating another invoice with the same submission identifier.
# This protects against retries, although it doesn't detect two deliberately separate submissions for identical services. We can add duplicate-invoice detection later.
