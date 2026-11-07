
from invoice_anomaly import analyze_invoice

normal_invoice = {
    "totalCents": 155000,
    "items": [
        {
            "serviceName": "Home Nursing",
            "billingUnit": "HOUR",
            "quantity": 90,
            "lineTotalCents": 75000
        },
        {
            "serviceName": "Meal Preparation",
            "billingUnit": "SESSION",
            "quantity": 1,
            "lineTotalCents": 80000
        }
    ]
}

suspicious_invoice = {
    "totalCents": 155000,
    "items": [
        {
            "serviceName": "Home Nursing",
            "billingUnit": "HOUR",
            "quantity": 900,
            "lineTotalCents": 75000
        },
        {
            "serviceName": "Meal Preparation",
            "billingUnit": "SESSION",
            "quantity": 15,
            "lineTotalCents": 80000
        }
    ]
}

print("Normal invoice:")
print(analyze_invoice(normal_invoice))

print("\nSuspicious invoice:")
print(analyze_invoice(suspicious_invoice))
