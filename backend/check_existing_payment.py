import requests

from firebase_setup import db
from mpesa_service import query_stk_push_status

invoice_id = input("Enter invoice document ID: ").strip()

invoice = db.collection("invoices").document(invoice_id).get()

if not invoice.exists:
    print("Invoice not found.")
    raise SystemExit(1)

payment_id = invoice.get("currentPaymentId")

if not payment_id:
    print("Invoice has no current payment ID.")
    raise SystemExit(1)

payment = db.collection("payments").document(payment_id).get()

if not payment.exists:
    print("Payment record not found.")
    raise SystemExit(1)

checkout_id = payment.get("checkoutRequestId")

if not checkout_id:
    print("Payment has no checkout request ID.")
    raise SystemExit(1)

print("Checking existing payment with Daraja...")

try:
    result = query_stk_push_status(checkout_id)

    print("Status:", result["status"])
    print("Result code:", result["resultCode"])
    print("Description:", result["description"])

except requests.HTTPError as error:
    if (
        error.response is not None
        and error.response.status_code == 429
    ):
        print(
            "Daraja rate limit reached (HTTP 429). "
            "Payment outcome remains unconfirmed."
        )
    else:
        print("Daraja HTTP error:", error)

except requests.RequestException as error:
    print("Network error while checking Daraja:", error)

except ValueError as error:
    print("Invalid Daraja response:", error)
