
from firebase_setup import db

invoice_id = input("Enter invoice document ID: ").strip()

invoice_snapshot = db.collection(
    "invoices"
).document(invoice_id).get()

if not invoice_snapshot.exists:
    print("Invoice not found.")
    raise SystemExit(1)

invoice = invoice_snapshot.to_dict()
payment_id = invoice.get("currentPaymentId")

if not payment_id:
    print("No payment linked to this invoice.")
    raise SystemExit(1)

payment_snapshot = db.collection(
    "payments"
).document(payment_id).get()

if not payment_snapshot.exists:
    print("Linked payment record not found.")
    raise SystemExit(1)

payment = payment_snapshot.to_dict()

print("\nINVOICE")
print("Invoice status:", invoice.get("status"))
print("Invoice payment state:", invoice.get("paymentState"))
print("Invoice total cents:", invoice.get("totalCents"))

print("\nPAYMENT")
print("Payment status:", payment.get("status"))
print("Payment result code:", payment.get("resultCode"))
print("Payment description:", payment.get("resultDescription"))
print("Payment amount cents:", payment.get("amountCents"))
print("Checkout ID present:", bool(payment.get("checkoutRequestId")))

print("\nCONSISTENCY CHECKS")
print(
    "Invoice ID matches:",
    payment.get("invoiceId") == invoice_id
)
print(
    "Client ID matches:",
    payment.get("clientId") == invoice.get("clientId")
)
print(
    "Amount matches:",
    payment.get("amountCents") == invoice.get("totalCents")
)
