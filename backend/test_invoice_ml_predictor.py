
from invoice_ml_predictor import analyze_invoice_ml


def make_invoice(items):
    return {
        "items": items,
        "totalCents": sum(
            item["lineTotalCents"] for item in items
        )
    }


def nursing(minutes):
    return {
        "serviceId": "home_nursing",
        "billingUnit": "HOUR",
        "quantity": minutes,
        "lineTotalCents": (50000 * minutes + 30) // 60
    }


def meal(sessions):
    return {
        "serviceId": "meal_preparation",
        "billingUnit": "SESSION",
        "quantity": sessions,
        "lineTotalCents": 80000 * sessions
    }


scenarios = {
    "Normal nursing": make_invoice([
        nursing(90)
    ]),
    "Normal mixed services": make_invoice([
        nursing(90),
        meal(1)
    ]),
    "Long nursing duration": make_invoice([
        nursing(900)
    ]),
    "High session count": make_invoice([
        meal(15)
    ])
}


for name, invoice in scenarios.items():
    result = analyze_invoice_ml(invoice)

    print(f"\nScenario: {name}")
    print("Status:", result["status"])
    print("Score:", round(result["anomalyScore"], 6))
    print("Method:", result["detectionMethod"])
    print("Model version:", result["modelVersion"])


print("\nTEST COMPLETED")
