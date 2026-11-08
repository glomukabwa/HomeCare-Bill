
from invoice_ml_features import (
    FEATURE_NAMES,
    extract_invoice_features
)


sample_invoice = {
    "totalCents": 155000,
    "items": [
        {
            "serviceId": "home_nursing",
            "billingUnit": "HOUR",
            "quantity": 90,
            "lineTotalCents": 75000
        },
        {
            "serviceId": "meal_preparation",
            "billingUnit": "SESSION",
            "quantity": 1,
            "lineTotalCents": 80000
        }
    ]
}

features = extract_invoice_features(sample_invoice)

print("\nINVOICE ML FEATURES")

for name, value in zip(FEATURE_NAMES, features):
    print(f"{name}: {value}")

expected = [
    1550.0,
    2.0,
    2.0,
    90.0,
    1.0,
    800.0,
    775.0
]

assert features == expected

print("\nTEST PASSED")
print("Invoice ML features extracted correctly.")
