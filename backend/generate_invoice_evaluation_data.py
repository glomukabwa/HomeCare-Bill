"""Create a reproducible, labelled synthetic *challenge* test set.
Run from the backend directory after configuring Firebase credentials.
Does not write to Firestore or modify the trained model.
"""
import csv
import random
from pathlib import Path

from firebase_setup import db
from invoice_ml_features import FEATURE_NAMES, extract_invoice_features

SEED = 20261118
NORMAL_COUNT = 800
ANOMALY_COUNT = 200
OUTPUT = Path(__file__).resolve().parent / "ML" / "invoice_evaluation_data.csv"


def load_services():
    result = []
    for doc in db.collection("services").stream():
        item = doc.to_dict()
        if (item.get("isActive") is True
                and type(item.get("rateCents")) is int
                and 0 < item["rateCents"] <= 100_000_000
                and item.get("billingUnit") in ("HOUR", "SESSION")):
            result.append({"serviceId": doc.id,
                           "billingUnit": item["billingUnit"],
                           "rateCents": item["rateCents"]})
    if not result:
        raise ValueError("No active, valid services found in Firestore")
    return sorted(result, key=lambda s: s["serviceId"])


def charge(service, quantity):
    rate = service["rateCents"]
    return (rate * quantity + 30) // 60 if service["billingUnit"] == "HOUR" else rate * quantity


def ordinary_invoice(services, rng):
    n = min(rng.choices([1, 2, 3, 4], weights=[50, 30, 15, 5], k=1)[0], len(services))
    selected = rng.sample(services, n)
    items = []
    for service in selected:
        quantity = (rng.randrange(30, 481, 15) if service["billingUnit"] == "HOUR"
                    else rng.randint(1, 4))
        items.append({"serviceId": service["serviceId"],
                      "billingUnit": service["billingUnit"],
                      "quantity": quantity,
                      "lineTotalCents": charge(service, quantity)})
    return {"items": items, "totalCents": sum(i["lineTotalCents"] for i in items)}


def unusual_invoice(services, rng):
    # Start with a normal invoice, then alter one service quantity to a
    # documented, application-valid but outside-training-range value.
    eligible_units = sorted({s["billingUnit"] for s in services})
    chosen_unit = rng.choice(eligible_units)
    selected = rng.choice([s for s in services if s["billingUnit"] == chosen_unit])
    invoice = ordinary_invoice(services, rng)
    # Ensure chosen service is included without exceeding the 10-item limit.
    target = next((i for i in invoice["items"] if i["serviceId"] == selected["serviceId"]), None)
    if target is None:
        target = {"serviceId": selected["serviceId"], "billingUnit": chosen_unit,
                  "quantity": 1, "lineTotalCents": 0}
        if len(invoice["items"]) >= 4:
            invoice["items"][-1] = target
        else:
            invoice["items"].append(target)
    # HOUR quantities are minutes; backend requires 15-minute increments.
    quantity = (rng.randrange(495, 1441, 15) if chosen_unit == "HOUR"
                else rng.randint(5, 100))
    target["quantity"] = quantity
    target["lineTotalCents"] = charge(selected, quantity)
    invoice["totalCents"] = sum(i["lineTotalCents"] for i in invoice["items"])
    return invoice, ("long_duration" if chosen_unit == "HOUR" else "high_session_count")


def main():
    rng = random.Random(SEED)
    services = load_services()
    rows = []
    for _ in range(NORMAL_COUNT):
        inv = ordinary_invoice(services, rng)
        if inv["totalCents"] > 1_000_000_000:
            raise ValueError("Normal invoice exceeds backend maximum; check service rates")
        rows.append(extract_invoice_features(inv) + [0, "ordinary"])
    for _ in range(ANOMALY_COUNT):
        inv, scenario = unusual_invoice(services, rng)
        if inv["totalCents"] > 1_000_000_000:
            raise ValueError("Unusual invoice exceeds backend maximum; adjust service rates")
        rows.append(extract_invoice_features(inv) + [1, scenario])
    rng.shuffle(rows)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(FEATURE_NAMES + ["is_anomaly", "scenario"])
        writer.writerows(rows)
    print(f"Saved {len(rows)} labelled invoices to {OUTPUT}")
    print(f"Normal: {NORMAL_COUNT}; deliberately unusual: {ANOMALY_COUNT}")
    print("Active service count:", len(services))
    print("Scenario counts:", {s: sum(r[-1] == s for r in rows) for s in sorted({r[-1] for r in rows})})


if __name__ == "__main__":
    main()
