
from collections import defaultdict


def analyze_invoice(invoice: dict) -> dict:
    """
    Performs explainable rule-based anomaly checks
    on an existing HomeCare Bill invoice.

    This is separate from the trained Isolation Forest model.
    """

    reasons = []
    items = invoice.get("items", [])
    total_cents = invoice.get("totalCents", 0)

    if not isinstance(items, list) or not items:
        reasons.append("Invoice has no valid service items.")
        items = []

    calculated_total = 0
    service_quantities = defaultdict(int)

    for item in items:
        if not isinstance(item, dict):
            reasons.append("Invalid service item detected.")
            continue

        service_name = item.get("serviceName", "Unknown service")
        quantity = item.get("quantity", 0)
        line_total = item.get("lineTotalCents", 0)
        billing_unit = item.get("billingUnit", "")

        if (
            type(quantity) is not int
            or quantity <= 0
            or type(line_total) is not int
            or line_total < 0
        ):
            reasons.append(
                f"Invalid quantity or amount for {service_name}."
            )
            continue

        calculated_total += line_total
        service_quantities[service_name] += quantity

        # Initial demonstration thresholds.
        # These are configurable rules, not learned ML thresholds.
        if billing_unit == "HOUR" and quantity > 720:
            reasons.append(
                f"Unusually long service duration: {service_name}."
            )

        if billing_unit == "SESSION" and quantity > 10:
            reasons.append(
                f"Unusually high session count: {service_name}."
            )

    if calculated_total != total_cents:
        reasons.append(
            "Invoice total does not match its line-item totals."
        )

    if type(total_cents) is not int or total_cents <= 0:
        reasons.append("Invoice total is invalid.")

    return {
        "isAnomaly": len(reasons) > 0,
        "status": (
            "FLAGGED_FOR_REVIEW" if reasons else "NORMAL"
        ),
        "reasons": reasons,
        "detectionMethod": "RULE_BASED"
    }
