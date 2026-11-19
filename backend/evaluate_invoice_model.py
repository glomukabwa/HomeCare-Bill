"""Evaluate the *existing saved* invoice model on independent synthetic challenge data.
No model training or overwriting occurs. Run from backend directory.
"""
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.metrics import confusion_matrix, precision_score, recall_score, f1_score
from invoice_ml_features import FEATURE_NAMES

ML_DIR = Path(__file__).resolve().parent / "ML"
TRAIN_CSV = ML_DIR / "invoice_training_data.csv"
EVAL_CSV = ML_DIR / "invoice_evaluation_data.csv"
MODEL_FILE = ML_DIR / "invoice_isolation_forest.joblib"
SCALER_FILE = ML_DIR / "invoice_scaler.joblib"


def report(name, truth, predicted):
    tn, fp, fn, tp = confusion_matrix(truth, predicted, labels=[0, 1]).ravel()
    print(f"\n{name}")
    print("Confusion matrix (rows: actual normal/anomaly; columns: predicted normal/anomaly)")
    print(f"[[{tn:4d}, {fp:4d}],\n [{fn:4d}, {tp:4d}]]")
    print(f"Precision: {precision_score(truth, predicted, zero_division=0):.4f}")
    print(f"Recall:    {recall_score(truth, predicted, zero_division=0):.4f}")
    print(f"F1-score:  {f1_score(truth, predicted, zero_division=0):.4f}")
    return {"method": name, "TN": int(tn), "FP": int(fp), "FN": int(fn), "TP": int(tp),
            "precision": precision_score(truth, predicted, zero_division=0),
            "recall": recall_score(truth, predicted, zero_division=0),
            "f1": f1_score(truth, predicted, zero_division=0)}


def main():
    original = pd.read_csv(TRAIN_CSV)
    evaluation = pd.read_csv(EVAL_CSV)
    if list(original.columns) != FEATURE_NAMES:
        raise ValueError("Original training CSV feature columns do not match")
    if list(evaluation.columns) != FEATURE_NAMES + ["is_anomaly", "scenario"]:
        raise ValueError("Evaluation CSV columns do not match")
    if evaluation.isnull().any().any():
        raise ValueError("Evaluation data contains missing values")
    truth = evaluation["is_anomaly"].to_numpy(dtype=int)
    if set(truth) != {0, 1}:
        raise ValueError("Evaluation must include both normal and anomaly labels")

    model = joblib.load(MODEL_FILE)  # Only load model files you created and trust.
    scaler = joblib.load(SCALER_FILE)
    X = evaluation[FEATURE_NAMES]
    predicted_ml = (model.predict(scaler.transform(X)) == -1).astype(int)

    # Simple baseline: flag invoice totals above the 95th percentile of
    # the original synthetic dataset. Threshold is NOT tuned on evaluation labels.
    threshold = float(np.percentile(original["total_amount_kes"], 95))
    predicted_baseline = (X["total_amount_kes"].to_numpy() > threshold).astype(int)

    print("Independent synthetic challenge evaluation:", len(evaluation), "invoices")
    print("Normal labels:", int((truth == 0).sum()), "Anomaly labels:", int((truth == 1).sum()))
    print(f"Baseline threshold: invoice total > KES {threshold:,.2f} (95th percentile of original CSV)")
    results = [report("Isolation Forest", truth, predicted_ml),
               report("Invoice amount threshold baseline", truth, predicted_baseline)]
    out = ML_DIR / "invoice_evaluation_results.csv"
    pd.DataFrame(results).to_csv(out, index=False)
    print("\nSaved comparison to:", out)
    print("NOTE: These labels are synthetic scenario labels, not verified real-world fraud labels.")


if __name__ == "__main__":
    main()
