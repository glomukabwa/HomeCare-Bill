
from firebase_setup import db

print("Connecting to Firebase...")

services = db.collection("services").limit(3).stream()

for service in services:
    print(service.id, service.to_dict().get("name"))

print("Firebase connection successful!")
