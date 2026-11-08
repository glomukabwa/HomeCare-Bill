
from unittest.mock import patch

from payment_routes import verify_and_finalize


payment_id = input(
    "Enter the existing payment document ID: "
).strip()

fake_daraja_response = {
    "status": "PENDING",
    "resultCode": 4999,
    "description": "The transaction is still under processing"
}

with patch(
    "payment_routes.query_stk_push_status",
    return_value=fake_daraja_response
):
    result = verify_and_finalize(payment_id)

print("\nPAYMENT VERIFICATION TEST")
print("Status:", result["status"])
print("Result code:", result["resultCode"])
print("Description:", result["description"])

assert result["status"] == "PENDING", (
    "Expected PENDING, but received another status."
)

print("\nTEST PASSED")
print("Processing response was not classified as FAILED.")
