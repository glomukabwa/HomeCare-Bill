
import os
import requests
import base64

from dotenv import load_dotenv
from pathlib import Path

from datetime import datetime
from zoneinfo import ZoneInfo

BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")

CONSUMER_KEY = os.getenv("DARAJA_CONSUMER_KEY")
CONSUMER_SECRET = os.getenv("DARAJA_CONSUMER_SECRET")
BASE_URL = os.getenv(
    "DARAJA_BASE_URL",
    "https://sandbox.safaricom.co.ke"
)


def get_access_token():
    if not CONSUMER_KEY or not CONSUMER_SECRET:
        raise ValueError(
            "Daraja credentials are missing from the .env file."
        )

    url = (
        f"{BASE_URL}/oauth/v1/generate"
        "?grant_type=client_credentials"
    )

    response = requests.get(
        url,
        auth=(CONSUMER_KEY, CONSUMER_SECRET),
        timeout=30
    )

    response.raise_for_status()

    data = response.json()
    access_token = data.get("access_token")

    if not access_token:
        raise ValueError(
            "Daraja did not return an access token."
        )

    return access_token


def initiate_stk_push(
    phone_number: str,
    amount_kes: int,
    account_reference: str
):
    """
    Initiates a Daraja sandbox STK Push.

    A successful API response only means the request
    was accepted for processing, not that payment succeeded.
    """

    shortcode = os.getenv("DARAJA_SHORTCODE")
    passkey = os.getenv("DARAJA_PASSKEY")
    callback_url = os.getenv("DARAJA_CALLBACK_URL")

    if not shortcode or not passkey:
        raise ValueError(
            "Daraja shortcode or passkey is missing."
        )

    if (
        not callback_url
        or not callback_url.startswith("https://")
    ):
        raise ValueError(
            "A public HTTPS Daraja callback URL is required."
        )

    if not isinstance(amount_kes, int) or amount_kes <= 0:
        raise ValueError(
            "Payment amount must be a positive whole number of KES."
        )

    if (
        not phone_number.isdigit()
        or len(phone_number) != 12
        or not phone_number.startswith(("2547", "2541"))
    ):
        raise ValueError(
            "Phone number must use the format 2547XXXXXXXX "
            "or 2541XXXXXXXX."
        )

    if not account_reference or len(account_reference) > 12:
        raise ValueError(
            "Account reference must contain 1 to 12 characters."
        )

    timestamp = datetime.now(
        ZoneInfo("Africa/Nairobi")
    ).strftime("%Y%m%d%H%M%S")

    password_string = f"{shortcode}{passkey}{timestamp}"

    password = base64.b64encode(
        password_string.encode("utf-8")
    ).decode("utf-8")

    payload = {
        "BusinessShortCode": shortcode,
        "Password": password,
        "Timestamp": timestamp,
        "TransactionType": "CustomerPayBillOnline",
        "Amount": amount_kes,
        "PartyA": phone_number,
        "PartyB": shortcode,
        "PhoneNumber": phone_number,
        "CallBackURL": callback_url,
        "AccountReference": account_reference,
        "TransactionDesc": "Care invoice"
    }

    access_token = get_access_token()

    response = requests.post(
        f"{BASE_URL}/mpesa/stkpush/v1/processrequest",
        json=payload,
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json"
        },
        timeout=30
    )

    response.raise_for_status()
    result = response.json()

    if str(result.get("ResponseCode")) != "0":
        raise ValueError(
            result.get("errorMessage")
            or result.get("ResponseDescription")
            or "Daraja rejected the STK Push request."
        )

    checkout_request_id = result.get("CheckoutRequestID")

    if not checkout_request_id:
        raise ValueError(
            "Daraja accepted the request but did not "
            "return a CheckoutRequestID."
        )

    return {
        "status": "PENDING",
        "checkoutRequestId": checkout_request_id,
        "merchantRequestId": result.get("MerchantRequestID"),
        "message": result.get(
            "CustomerMessage",
            "Payment request submitted."
        )
    }



def query_stk_push_status(checkout_request_id: str):
    """
    Queries Daraja for the outcome of an STK Push.

    This does not change Firestore records.
    """

    if not checkout_request_id:
        raise ValueError("Checkout request ID is required.")

    shortcode = os.getenv("DARAJA_SHORTCODE")
    passkey = os.getenv("DARAJA_PASSKEY")

    if not shortcode or not passkey:
        raise ValueError(
            "Daraja shortcode or passkey is missing."
        )

    timestamp = datetime.now(
        ZoneInfo("Africa/Nairobi")
    ).strftime("%Y%m%d%H%M%S")

    password = base64.b64encode(
        f"{shortcode}{passkey}{timestamp}".encode("utf-8")
    ).decode("utf-8")

    payload = {
        "BusinessShortCode": shortcode,
        "Password": password,
        "Timestamp": timestamp,
        "CheckoutRequestID": checkout_request_id
    }

    response = requests.post(
        f"{BASE_URL}/mpesa/stkpushquery/v1/query",
        json=payload,
        headers={
            "Authorization": f"Bearer {get_access_token()}",
            "Content-Type": "application/json"
        },
        timeout=30
    )

    response.raise_for_status()
    result = response.json()

    response_code = result.get("ResponseCode")

    if str(response_code) != "0":
        return {
            "status": "UNKNOWN",
            "resultCode": None,
            "description": result.get(
                "errorMessage",
                result.get(
                    "ResponseDescription",
                    "Unable to determine payment status."
                )
            )
        }

    result_code = result.get("ResultCode")

    if result_code is None:
        return {
            "status": "PENDING",
            "resultCode": None,
            "description": result.get(
                "ResponseDescription",
                "Payment result is not available yet."
            )
        }

    try:
        result_code = int(result_code)
    except (TypeError, ValueError):
        raise ValueError(
            "Daraja returned an invalid payment result code."
        )

    description = result.get(
        "ResultDesc",
        "Payment status retrieved."
    )

    if result_code == 0:
        status = "SUCCESS"
    elif result_code == 4999:
        status = "PENDING"
    else:
        status = "FAILED"

    return {
        "status": status,
        "resultCode": result_code,
        "description": description
    }
