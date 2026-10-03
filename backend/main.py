from fastapi import FastAPI
from pydantic import BaseModel
# When we installed fastapi (pip install fastapi uvicorn), 
# pip installed FastAPI and its required dependencies. Pydantic is one of FastAPI's important dependencies. 
# BaseModel is a class provided by Pydantic. We are going to inherit it below and use its functionality
# Remember inheritance in python looks like this: class Dog(Animal):

from model_loader import models, metadata
from feature_engineering import prepare_features


app = FastAPI()


class Transaction(BaseModel):
    # The BaseModel functionality we're inheritting here is validation and conversion
    # It checks that the Transaction data is all following the respective datatypes meaning if a number is expected you can't enter a string
    # and by conversion I mean converting it to the right data type. Eg if you enter amount = 5000 it'll change it to 5000.0, a float
    # Basically it saves us the time and effort of having to manually code all this
    transaction_type: str
    amount: float
    old_balance: float
    new_balance: float

    #Here we're essentially telling FastAPI: If somebody sends me a transaction, I expect these four pieces of information.



@app.get("/")
def home():
    return {
        "message": "HomeCare Bill Anomaly Detection API is running"
    }

# Here's what this means:
# - from fastapi import FastAPI imports FastAPI.
# - app = FastAPI() creates our API application.
# - @app.get("/") says, "When someone sends a GET request to the root / address, run the function below."
# - home() returns a Python dictionary. FastAPI automatically converts it into JSON.
# Basically we've created a simple endpoint just to ask: "Hey backend, are you alive?"


@app.post("/analyze-transaction")
def analyze_transaction(transaction: Transaction):

    # Preparing the six features expected by the model
    features = prepare_features(
        transaction.transaction_type,
        transaction.amount,
        transaction.old_balance,
        transaction.new_balance
    )

    # Selecting the correct Isolation Forest model
    model = models[transaction.transaction_type]

    # Getting the anomaly score
    anomaly_score = model.decision_function([features])[0]
    # decision.function() is a method in the scikit library
    # We have access to it cz the models we have created are sklearn objects
    # So we can use the models we have created to access the method
    # Now, the reason we have put features in an array is cz decision_function expects many transactions. 
    # Remember when we were training the model we were training it with millions of records. Here, we are 
    # gonna use it to calculate a transaction at a time but we still need to put it inside an array. If there
    # were two transactions it would be: [feature1, feature2]
    # The point of the [0] at the end is cz it also returns its results in array form: [-0.5373, -0.22627]
    # Since it's only one transaction, it'll be returning only one array value so why do we need to specify [0]?
    # Cz to access the value of an array, you need to specify its position. If the answer was -0.5373 and not [-0.5373], 
    # we wouldn't need to put the [0] there

    # Getting the threshold selected during model development
    threshold = metadata["threshold"]

    # Deciding whether the transaction should be flagged
    is_anomaly = anomaly_score < threshold
    # Rememeber, with Isolation Forest's decision_function, lower values mean more abnormal.

    return{
        "transaction_type:": transaction.transaction_type,
        "anomaly_score": anomaly_score,
        "threshold": threshold,
        "is_anomaly": bool(is_anomaly)
    }