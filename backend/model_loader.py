import json
import joblib
from pathlib import Path


# Folder containing this Python file
BASE_DIR = Path(__file__).resolve().parent
# BASE_DIR = backend

#backend/models
MODEL_DIR = BASE_DIR.parent / "ML" / "models"
# BASE_DIR.parent = HomeCare_Bill
# So MODEL_DIR = HomeCare_Bill/ML/models

# Loading metadata
with open(MODEL_DIR / "model_metadata.json", "r") as file:
    metadata = json.load(file)

# Above we are first opening the file before reading it. You will notice that with the models below, we just
# directly loaded them without opening them. That's because json.load() and joblib.load() work differently. 
# json.load() expects an opened file, it can't open it on its own. That is why we use with open(...) above 
# joblib.load() has the functionality of opening the file one its own internally and reading it so that's why we are
# using it directly below. In the with open.. code above u'll notice I've put an "r" right after the file path. 
# That is because with open.. has 3 modes: read, write and append ("r", "w", "a"). Since we only need to read the file
# rn, we are putting "r"

# Loading the five Isolation forest models
models = {
    "CASH_IN": joblib.load(
        MODEL_DIR / "isolation_forest_cash_in.joblib"
    ),

    "CASH_OUT": joblib.load(
        MODEL_DIR / "isolation_forest_cash_out.joblib"
    ),

    "DEBIT": joblib.load(
        MODEL_DIR / "isolation_forest_debit.joblib"
    ),

    "PAYMENT": joblib.load(
        MODEL_DIR / "isolation_forest_payment.joblib"
    ),

    "TRANSFER": joblib.load(
        MODEL_DIR / "isolation_forest_transfer.joblib"
    )
}

print("Models loaded successfully")
print("Available models:", list(models.keys()))