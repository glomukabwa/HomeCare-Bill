# Machine Learning - Transaction Anomaly Detection

This folder contains the machine learning component of the Payment and Billing System for Home-Based Elderly Care Services.

The purpose of this component is to detect unusual payment transactions using the Isolation Forest anomaly detection algorithm.

## Dataset

The model was developed using the PaySim dataset, which contains simulated mobile money transactions.

The dataset is not included in this repository due to its large file size.

## Model Development

The machine learning development process included:

- Data exploration and preprocessing
- Feature engineering
- Chronological splitting into training, validation, and final evaluation periods
- Development of a baseline Isolation Forest model
- Model improvement and validation
- Final evaluation

The selected model uses separate Isolation Forest models for each PaySim transaction type:

- CASH_IN
- CASH_OUT
- DEBIT
- PAYMENT
- TRANSFER

Each transaction is therefore evaluated relative to transactions of the same type.

## Features

The final model uses the following features:

- Transaction amount
- Original account balance before the transaction
- Original account balance after the transaction
- Change in the original account balance
- Original account balance discrepancy
- Transaction amount deviation from the normal amount for its transaction type

## Final Model

The selected anomaly threshold is:

`-0.26`

On the final chronological evaluation period, the model achieved:

- Precision: 90.85%
- Recall: 25.79%
- F1-score: 40.17%
- Transactions flagged: 612 out of 201,405

The model is intended to identify unusual transactions for administrator review rather than classify transactions as definitively fraudulent.

## Folder Structure

- `notebooks/` - Contains the Kaggle notebook used for data analysis, model development, validation, and evaluation.
- `models/` - Contains the five trained Isolation Forest models and the model metadata required for inference.

## Model Files

The `models` folder contains:

- `isolation_forest_cash_in.joblib`
- `isolation_forest_cash_out.joblib`
- `isolation_forest_debit.joblib`
- `isolation_forest_payment.joblib`
- `isolation_forest_transfer.joblib`
- `model_metadata.json`

The metadata file contains information such as the selected anomaly threshold, model features, supported transaction types, and training statistics required during preprocessing.

## Next Step

The trained models will be integrated into a FastAPI service, which will provide anomaly detection functionality to the main application.
