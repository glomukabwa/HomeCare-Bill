from model_loader import metadata

def prepare_features(transaction_type, amount, old_balance, new_balance):

    orig_balance_change = old_balance - new_balance
    orig_balance_discrepancy = abs(
        amount - orig_balance_change
    )


    type_stats = metadata["type_amount_statistics"][transaction_type]
    mean_amount = type_stats["mean"]
    std_amount = type_stats["std"]


    amount_zscore_abs = abs(
        (amount - mean_amount) / std_amount
    )

    features = [
        amount,
        old_balance,
        new_balance,
        orig_balance_change,
        orig_balance_discrepancy,
        amount_zscore_abs
    ]

    return features

# test_features = prepare_features(
#     transaction_type="PAYMENT",
#     amount=5000,
#     old_balance=20000,
#     new_balance=15000
# )

# print("Prepared features:")
# print(test_features)