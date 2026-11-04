
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore

# Find the backend folder regardless of the terminal's location.
BASE_DIR = Path(__file__).resolve().parent

# Load the private Firebase service account credentials.
SERVICE_ACCOUNT_PATH = BASE_DIR / "serviceAccountKey.json"

if not firebase_admin._apps:
    credential = credentials.Certificate(str(SERVICE_ACCOUNT_PATH))
    firebase_admin.initialize_app(credential)

# Create a Firestore client for our backend.
db = firestore.client()
