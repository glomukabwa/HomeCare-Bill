
import csv
import random
from pathlib import Path

from firebase_setup import db
from invoice_ml_features import (
    FEATURE_NAMES,
    extract_invoice_features
)


random.seed(42)

OUTPUT_PATH = Path("ML") / "invoice_training_data.csv"
NUMBER_OF_INVOICES = 5000


def load_services():
    services = []

    for snapshot in db.collection("services").stream():
        service = snapshot.to_dict()

        if service.get("isActive") is not True:
            continue

        rate = service.get("rateCents")
        unit = service.get("billingUnit")

        if (
            type(rate) is not int
            or rate <= 0
            or unit not in {"HOUR", "SESSION"}
        ):
            continue

        services.append({
            "serviceId": snapshot.id,
            "billingUnit": unit,
            "rateCents": rate
        })

    if not services:
        raise ValueError(
            "No active services found in Firestore."
        )

    return services


def generate_invoice(services):
    # Most invoices contain a small number of services.
    number_of_items = random.choices(
        population=[1, 2, 3, 4],
        weights=[50, 30, 15, 5],
        k=1
    )[0]

    number_of_items = min(
        number_of_items,
        len(services)
    )

    selected_services = random.sample(
        services,
        number_of_items
    )

    items = []
    total_cents = 0

    for service in selected_services:
        unit = service["billingUnit"]
        rate = service["rateCents"]

        if unit == "HOUR":
            # Typical duration: 30 minutes to 8 hours.
            quantity = random.randrange(30, 481, 15)
            line_total = (rate * quantity + 30) // 60

        else:
            # Typical number of sessions: 1 to 4.
            quantity = random.randint(1, 4)
            line_total = rate * quantity

        total_cents += line_total

        items.append({
            "serviceId": service["serviceId"],
            "billingUnit": unit,
            "quantity": quantity,
            "lineTotalCents": line_total
        })

    return {
        "items": items,
        "totalCents": total_cents
    }


def main():
    services = load_services()

    print("Active services found:", len(services))

    OUTPUT_PATH.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    with OUTPUT_PATH.open(
        "w",
        newline="",
        encoding="utf-8"
    ) as file:
        writer = csv.writer(file)

        writer.writerow(FEATURE_NAMES)

        for _ in range(NUMBER_OF_INVOICES):
            invoice = generate_invoice(services)

            features = extract_invoice_features(
                invoice
            )

            writer.writerow(features)

    print("\nDATASET GENERATED")
    print("Number of invoices:", NUMBER_OF_INVOICES)
    print("Number of features:", len(FEATURE_NAMES))
    print("Saved to:", OUTPUT_PATH)


if __name__ == "__main__":
    main()

#How the generator works
# The script reads the active services from Firestore, then creates 5,000 fictional invoices.

# It uses the same billing calculations as your backend:
# - For HOUR services, quantities represent minutes and increase in 15-minute increments.
# - For SESSION services, quantities represent the number of sessions.
# - Line totals are calculated using each service's official rate.
# It then uses your tested extract_invoice_features() function to convert each invoice into seven numerical features.

# The script doesn't create any invoices in Firestore. It only reads your service catalogue and writes a local CSV file.

# Important: The generated invoices represent assumed ordinary billing patterns. They are not real customer invoices, and the selected ranges are modelling assumptions rather than verified industry standards.