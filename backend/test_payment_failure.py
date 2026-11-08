
from uuid import uuid4
from unittest.mock import patch

from firebase_setup import db
from payment_routes import verify_and_finalize


test_id = uuid4().hex
invoice_id = f"TEST_INVOICE_{test_id}"
payment_id = f"TEST_PAYMENT_{test_id}"

invoice_ref = db.collection("invoices").document(invoice_id)
payment_ref = db.collection("payments").document(payment_id)

test_client_id = f"TEST_CLIENT_{test_id}"
test_amount = 155000

fake_failure = {
    "status": "FAILED",
    "resultCode": 1032,
    "description": "Request cancelled by user."
}

try:
    batch = db.batch()

    batch.set(invoice_ref, {
        "clientId": test_client_id,
        "status": "APPROVED",
        "paymentState": "PENDING",
        "currentPaymentId": payment_id,
        "totalCents": test_amount,
        "testRecord": True
    })

    batch.set(payment_ref, {
        "invoiceId": invoice_id,
        "clientId": test_client_id,
        "amountCents": test_amount,
        "checkoutRequestId": f"TEST_CHECKOUT_{test_id}",
        "status": "PENDING",
        "testRecord": True
    })

    batch.commit()

    with patch(
        "payment_routes.query_stk_push_status",
        return_value=fake_failure
    ):
        result = verify_and_finalize(payment_id)

    updated_invoice = invoice_ref.get().to_dict()
    updated_payment = payment_ref.get().to_dict()

    print("\nFAILED PAYMENT TEST")
    print("Verification result:", result["status"])
    print("Payment status:", updated_payment["status"])
    print("Invoice status:", updated_invoice["status"])
    print(
        "Invoice payment state:",
        updated_invoice["paymentState"]
    )

    assert result["status"] == "FAILED"
    assert updated_payment["status"] == "FAILED"
    assert updated_payment["resultCode"] == 1032
    assert updated_invoice["status"] == "APPROVED"
    assert updated_invoice["paymentState"] == "FAILED"

    with patch(
        "payment_routes.query_stk_push_status"
    ) as mock_query:
        second_result = verify_and_finalize(payment_id)
        mock_query.assert_not_called()

    assert second_result["status"] == "FAILED"

    print("\nTEST PASSED")
    print("Confirmed failure updated both records correctly.")
    print("Invoice remained approved.")
    print("Repeated verification was handled safely.")

finally:
    payment_ref.delete()
    invoice_ref.delete()
    print("\nTemporary test records cleaned up.")
