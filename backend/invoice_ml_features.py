
FEATURE_NAMES = [
    "total_amount_kes",
    "item_count",
    "distinct_services",
    "total_duration_minutes",
    "total_sessions",
    "max_line_amount_kes",
    "average_line_amount_kes"
]


def extract_invoice_features(invoice: dict) -> list[float]:
    """
    Extract features for the invoice Isolation Forest model.

    HOUR quantities represent minutes.
    SESSION quantities represent session counts.
    """

    items = invoice.get("items", [])

    if not isinstance(items, list) or not items:
        raise ValueError("Invoice must contain service items.")

    total_cents = invoice.get("totalCents")

    if type(total_cents) is not int or total_cents <= 0:
        raise ValueError("Invoice total must be positive.")

    total_duration_minutes = 0
    total_sessions = 0
    line_amounts = []
    service_ids = set()

    for item in items:
        if not isinstance(item, dict):
            raise ValueError("Invalid invoice item.")

        quantity = item.get("quantity")
        line_total = item.get("lineTotalCents")
        billing_unit = item.get("billingUnit")
        service_id = item.get("serviceId")

        if (
            type(quantity) is not int
            or quantity <= 0
            or type(line_total) is not int
            or line_total < 0
            or not isinstance(service_id, str)
            or not service_id.strip()
        ):
            raise ValueError("Invalid invoice feature data.")

        if billing_unit == "HOUR":
            total_duration_minutes += quantity

        elif billing_unit == "SESSION":
            total_sessions += quantity

        else:
            raise ValueError("Unsupported billing unit.")

        service_ids.add(service_id)
        line_amounts.append(line_total / 100)

    if sum(
        item["lineTotalCents"] for item in items
    ) != total_cents:
        raise ValueError("Invoice line totals do not match.")

    return [
        total_cents / 100,
        float(len(items)),
        float(len(service_ids)),
        float(total_duration_minutes),
        float(total_sessions),
        float(max(line_amounts)),
        float(sum(line_amounts) / len(line_amounts))
    ]
