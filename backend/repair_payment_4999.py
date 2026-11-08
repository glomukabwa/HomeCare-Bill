
from firebase_admin import firestore
from firebase_setup import db


invoice_id = input("Enter invoice document ID: ").strip()

invoice_ref = db.collection("invoices").document(invoice_id)


@firestore.transactional
def repair_payment(tx):
    invoice_snapshot = invoice_ref.get(transaction=tx)

    if not invoice_snapshot.exists:
        raise ValueError("Invoice not found.")

    invoice = invoice_snapshot.to_dict()
    payment_id = invoice.get("currentPaymentId")

    if not payment_id:
        raise ValueError("No payment linked to invoice.")

    payment_ref = db.collection("payments").document(payment_id)
    payment_snapshot = payment_ref.get(transaction=tx)

    if not payment_snapshot.exists:
        raise ValueError("Payment record not found.")

    payment = payment_snapshot.to_dict()

    # Verify that this is precisely the known
    # incorrectly classified payment scenario.
    if invoice.get("status") != "APPROVED":
        raise ValueError("Invoice is not approved.")

    if invoice.get("paymentState") != "FAILED":
        raise ValueError("Invoice payment state has changed.")

    if payment.get("status") != "FAILED":
        raise ValueError("Payment status has changed.")

    if str(payment.get("resultCode")) != "4999":
        raise ValueError(
            "Payment does not have the expected result code."
        )

    if (
        payment.get("invoiceId") != invoice_id
        or payment.get("clientId") != invoice.get("clientId")
        or payment.get("amountCents") != invoice.get("totalCents")
        or not payment.get("checkoutRequestId")
    ):
        raise ValueError(
            "Payment and invoice consistency checks failed."
        )

    tx.update(payment_ref, {
        "status": "PENDING",
        "resultDescription":
            "Previously misclassified result 4999; "
            "actual payment outcome remains unconfirmed.",
        "reconciliationRequired": True,
        "reconciledAt": firestore.SERVER_TIMESTAMP
    })

    tx.update(invoice_ref, {
        "paymentState": "PENDING"
    })

    return payment_id


confirmation = input(
    "Type REPAIR to correct this payment classification: "
).strip()

if confirmation != "REPAIR":
    print("Cancelled. No changes made.")
    raise SystemExit(0)

try:
    payment_id = repair_payment(db.transaction())
    print("Payment classification corrected.")
    print("Payment status: PENDING")
    print("Invoice payment state: PENDING")
    print("Actual payment outcome: UNCONFIRMED")

except ValueError as error:
    print("Repair cancelled:", error)
