
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from sklearn.ensemble import IsolationForest
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

from invoice_ml_features import FEATURE_NAMES


DATA_PATH = Path("ML") / "invoice_training_data.csv"
MODEL_PATH = Path("ML") / "invoice_isolation_forest.joblib"
SCALER_PATH = Path("ML") / "invoice_scaler.joblib"
METADATA_PATH = Path("ML") / "invoice_model_metadata.json"

RANDOM_STATE = 42


def main():
    # 1. Load the dataset.
    data = pd.read_csv(DATA_PATH)

    if list(data.columns) != FEATURE_NAMES:
        raise ValueError(
            "Dataset features do not match the feature extractor."
        )

    if data.isnull().any().any():
        raise ValueError("Dataset contains missing values.")

    X = data[FEATURE_NAMES]

    # 2. Split the data.
    X_train, X_test = train_test_split(
        X,
        test_size=0.2,
        random_state=RANDOM_STATE
    )

    # 3. Fit the scaler using training data only.
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    # 4. Train the Isolation Forest.
    model = IsolationForest(
        n_estimators=200,
        contamination=0.05,
        random_state=RANDOM_STATE,
        n_jobs=-1
    )

    model.fit(X_train_scaled)

    # 5. Evaluate anomaly detection behaviour.
    train_predictions = model.predict(X_train_scaled)
    test_predictions = model.predict(X_test_scaled)

    train_anomalies = np.sum(train_predictions == -1)
    test_anomalies = np.sum(test_predictions == -1)

    train_rate = train_anomalies / len(X_train)
    test_rate = test_anomalies / len(X_test)

    print("\nISOLATION FOREST TRAINING RESULTS")
    print("Training samples:", len(X_train))
    print("Testing samples:", len(X_test))
    print("Number of features:", len(FEATURE_NAMES))
    print("Training anomalies:", train_anomalies)
    print("Testing anomalies:", test_anomalies)
    print(f"Training anomaly rate: {train_rate:.2%}")
    print(f"Testing anomaly rate: {test_rate:.2%}")

    # 6. Save the model and scaler.
    MODEL_PATH.parent.mkdir(
        parents=True,
        exist_ok=True
    )

    joblib.dump(model, MODEL_PATH)
    joblib.dump(scaler, SCALER_PATH)

    metadata = {
        "modelName": "HomeCare Bill Invoice Isolation Forest",
        "modelVersion": "1.0",
        "algorithm": "IsolationForest",
        "trainingSamples": len(X_train),
        "testingSamples": len(X_test),
        "features": FEATURE_NAMES,
        "contamination": 0.05,
        "randomState": RANDOM_STATE,
        "trainingAnomalyRate": float(train_rate),
        "testingAnomalyRate": float(test_rate),
        "datasetType": "Synthetic home-care invoices"
    }

    with METADATA_PATH.open(
        "w",
        encoding="utf-8"
    ) as file:
        json.dump(metadata, file, indent=4)

    print("\nMODEL SAVED")
    print("Model:", MODEL_PATH)
    print("Scaler:", SCALER_PATH)
    print("Metadata:", METADATA_PATH)


if __name__ == "__main__":
    main()
