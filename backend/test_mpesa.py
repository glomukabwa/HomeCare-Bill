from mpesa_service import get_access_token

try:
    token = get_access_token()

    if token:
        print("SUCCESS: Connected to Safaricom Daraja Sandbox!")
        print("Access token obtained successfully.")

except Exception as error:
    print("ERROR: Daraja authentication failed.")
    print(type(error).__name__)
    print(str(error))