# HomeCare Bill

HomeCare Bill is an Android-based mobile application designed to support transparent and efficient billing for home-based elderly care services. The system provides a centralized platform where clients, caregivers, and administrators can manage caregiving activities, generate and review invoices, monitor billing records, and support digital payments.

The application integrates an Isolation Forest machine learning model to identify unusual invoice billing patterns and uses Safaricom's Daraja API for M-Pesa payment processing.

## Project Objectives

- Improve transparency and accountability in home-based elderly care billing.
- Enable caregivers to generate invoices based on services provided.
- Allow clients to review, approve, or dispute invoices.
- Support invoice anomaly detection using machine learning.
- Facilitate M-Pesa payment initiation.
- Provide administrators with tools for service management and invoice monitoring.

## Key Features

### Client Module
- Account registration and authentication.
- Management of care recipient information.
- Invitation of caregivers to provide services.
- Viewing, approval, and disputing of invoices.
- Monitoring of outstanding balances and billing alerts.
- Initiation of M-Pesa payments.

### Caregiver Module
- Account registration and authentication.
- Acceptance or rejection of caregiving invitations.
- Viewing assigned care recipients.
- Access to the active service catalogue.
- Generation and submission of caregiving invoices.
- Viewing submitted invoices.

### Administrator Module
- Administrative dashboard for system monitoring.
- Management of the service catalogue.
- Addition and editing of services.
- Activation and deactivation of services.
- Monitoring and review of invoices flagged for unusual billing patterns.

### Machine Learning Integration
- Uses the Isolation Forest algorithm for invoice anomaly detection.
- Extracts seven numerical features from caregiving invoices.
- Applies a trained feature scaler before prediction.
- Classifies invoices as normal or flagged for review.
- Stores anomaly scores and model information alongside invoice records.

### Payment Integration
- Integration with Safaricom Daraja API.
- M-Pesa STK Push payment initiation.
- Backend processing of payment requests and responses.

## Technologies Used

| Component | Technology |
|-----------|------------|
| Mobile Application | Kotlin |
| User Interface | Jetpack Compose, Material 3 |
| Development Environment | Android Studio |
| Authentication | Firebase Authentication |
| Database | Cloud Firestore |
| Backend API | Python, FastAPI |
| Machine Learning | Scikit-learn, Isolation Forest |
| Data Processing | Pandas |
| Model Serialization | Joblib |
| Payment Integration | Safaricom Daraja API |
| Version Control | Git and GitHub |

## System Architecture

HomeCare Bill consists of the following components:

1. **Android Application:** Provides role-based interfaces for clients, caregivers, and administrators.
2. **Firebase Authentication:** Manages user authentication and email verification.
3. **Cloud Firestore:** Stores user profiles, care recipients, assignments, services, invoices, and related records.
4. **FastAPI Backend:** Handles server-side invoice processing, validation, anomaly detection, and payment integration.
5. **Isolation Forest Model:** Analyzes numerical invoice features and identifies unusual billing patterns.
6. **Safaricom Daraja API:** Supports M-Pesa payment requests.

## Machine Learning Model

The system uses an Isolation Forest model trained on synthetic caregiving billing data to detect potentially unusual invoices.

Seven numerical features are extracted from each invoice:

1. Total invoice amount in Kenyan Shillings.
2. Number of invoice items.
3. Number of distinct services.
4. Total duration of hourly services in minutes.
5. Total number of sessions.
6. Maximum individual invoice item amount.
7. Average invoice item amount.

The extracted features are transformed using a saved scaler and passed to the trained model. Invoices identified as anomalous are flagged for administrative review.

An anomaly flag indicates an unusual billing pattern and does not necessarily establish that an invoice is incorrect or fraudulent.

## Security

HomeCare Bill implements several security measures, including:

- Firebase-based authentication and email verification.
- Role-based access restrictions.
- Backend verification of caregiver identity and assignments.
- Validation of invoice data and service configurations.
- Firestore security rules.
- Unique request identifiers to prevent duplicate invoice creation during retries.

Sensitive credentials, service account keys, and environment configuration files should not be committed to the repository.

## Testing

The system is tested during development to verify authentication, role-based functionality, caregiver assignments, service management, invoice generation, anomaly detection, and integration between application components.

## Limitations and Future Improvements

Potential areas for further development include:

- Evaluation of anomaly detection using larger real-world datasets, subject to appropriate privacy protections.
- Improved explanations for flagged invoices.
- Additional reporting and analytics functionality.
- Further usability and performance testing.
- Expanded payment and notification capabilities.

## Project Purpose

HomeCare Bill is developed as an academic project to demonstrate the application of mobile development, cloud services, machine learning, and digital payment integration to billing management in home-based elderly care.
