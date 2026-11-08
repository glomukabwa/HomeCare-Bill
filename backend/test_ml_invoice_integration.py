
from invoice_ml_predictor import analyze_invoice_ml


def prepare_anomaly_fields(invoice):
    ml_result = analyze_invoice_ml(invoice)

    return {
        "anomalyStatus": ml_result["status"],
        "isAnomaly": ml_result["isAnomaly"],
        "anomalyReasons": (
            ["Unusual billing pattern detected by AI."]
            if ml_result["isAnomaly"]
            else []
        ),
        "anomalyDetectionMethod": ml_result["detectionMethod"],
        "mlAnomalyScore": ml_result["anomalyScore"],
        "mlModelVersion": ml_result["modelVersion"]
    }


def create_test_invoice(minutes):
    rate_cents = 50000

    item = {
        "serviceId": "home_nursing",
        "billingUnit": "HOUR",
        "quantity": minutes,
        "lineTotalCents": (rate_cents * minutes + 30) // 60
    }

    return {
        "items": [item],
        "totalCents": item["lineTotalCents"]
    }


scenarios = {
    "Normal invoice": create_test_invoice(90),
    "Unusual invoice": create_test_invoice(900)
}


for name, invoice in scenarios.items():
    fields = prepare_anomaly_fields(invoice)

    assert fields["anomalyDetectionMethod"] == "ISOLATION_FOREST"
    assert isinstance(fields["isAnomaly"], bool)
    assert isinstance(fields["mlAnomalyScore"], float)

    assert fields["anomalyStatus"] in {
        "NORMAL",
        "FLAGGED_FOR_REVIEW"
    }

    if fields["isAnomaly"]:
        assert fields["anomalyReasons"]
    else:
        assert fields["anomalyReasons"] == []

    print(f"\n{name}")
    for key, value in fields.items():
        print(f"{key}: {value}")


print("\nML INVOICE FIELD TEST PASSED")
