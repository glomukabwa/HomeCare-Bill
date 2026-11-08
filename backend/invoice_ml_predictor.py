
import json
from pathlib import Path

import joblib
import pandas as pd

from invoice_ml_features import (
    FEATURE_NAMES,
    extract_invoice_features
)


BASE_DIR = Path(__file__).resolve().parent
ML_DIR = BASE_DIR / "ML"

MODEL_PATH = ML_DIR / "invoice_isolation_forest.joblib"
SCALER_PATH = ML_DIR / "invoice_scaler.joblib"
METADATA_PATH = ML_DIR / "invoice_model_metadata.json"


def load_invoice_model():
    """
    Load trusted model artifacts produced by our
    HomeCare Bill training pipeline.
    """
    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)

    with METADATA_PATH.open(
        "r",
        encoding="utf-8"
    ) as file:
        metadata = json.load(file)

    if metadata.get("features") != FEATURE_NAMES:
        raise ValueError(
            "Invoice model feature definitions do not match."
        )

    return model, scaler, metadata


model, scaler, metadata = load_invoice_model()


def analyze_invoice_ml(invoice: dict) -> dict:
    """
    Predict invoice anomalies using the trained
    Isolation Forest model.
    """
    features = extract_invoice_features(invoice)

    # Preserve the exact column names and order
    # used during model training.
    feature_frame = pd.DataFrame(
        [features],
        columns=FEATURE_NAMES
    )

    scaled_features = scaler.transform(feature_frame)

    prediction = int(
        model.predict(scaled_features)[0]
    )

    anomaly_score = float(
        model.decision_function(scaled_features)[0]
    )

    is_anomaly = prediction == -1

    return {
        "isAnomaly": is_anomaly,
        "status": (
            "FLAGGED_FOR_REVIEW"
            if is_anomaly
            else "NORMAL"
        ),
        "anomalyScore": anomaly_score,
        "detectionMethod": "ISOLATION_FOREST",
        "modelVersion": metadata["modelVersion"]
    }
