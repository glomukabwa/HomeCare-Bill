
from pathlib import Path

import joblib

from invoice_ml_features import extract_invoice_features


MODEL_PATH = Path("ML") / "invoice_isolation_forest.joblib"
SCALER_PATH = Path("ML") / "invoice_scaler.joblib"

model = joblib.load(MODEL_PATH)
scaler = joblib.load(SCALER_PATH)


def make_invoice(items):
    return {
        "items": items,
        "totalCents": sum(
            item["lineTotalCents"] for item in items
        )
    }


def nursing(minutes):
    rate_cents = 50000

    return {
        "serviceId": "home_nursing",
        "billingUnit": "HOUR",
        "quantity": minutes,
        "lineTotalCents": (rate_cents * minutes + 30) // 60
    }


def meal_preparation(sessions):
    rate_cents = 80000

    return {
        "serviceId": "meal_preparation",
        "billingUnit": "SESSION",
        "quantity": sessions,
        "lineTotalCents": rate_cents * sessions
    }


test_cases = {
    "Normal nursing invoice": make_invoice([
        nursing(90)
    ]),
    "Normal mixed-service invoice": make_invoice([
        nursing(90),
        meal_preparation(1)
    ]),
    "Unusually long nursing duration": make_invoice([
        nursing(900)
    ]),
    "Unusually high session count": make_invoice([
        meal_preparation(15)
    ])
}


print("\nINVOICE ISOLATION FOREST TEST RESULTS")

for name, invoice in test_cases.items():
    features = extract_invoice_features(invoice)

    scaled_features = scaler.transform([features])

    prediction = model.predict(scaled_features)[0]
    anomaly_score = model.decision_function(
        scaled_features
    )[0]

    status = (
        "FLAGGED_FOR_REVIEW"
        if prediction == -1
        else "NORMAL"
    )

    print(f"\nScenario: {name}")
    print(f"Invoice total: KES {invoice['totalCents'] / 100:.2f}")
    print(f"Prediction: {status}")
    print(f"Anomaly score: {anomaly_score:.6f}")
