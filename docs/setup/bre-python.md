# BRE Service Setup

This guide explains how to run and deploy the Business Rules Engine (BRE) service used by the app for loan-decision and offer calculation.

## What The BRE Does

The BRE accepts a request containing:

- applicant details
- employment details
- bureau data
- normalized document extractions
- optional recalculated EMI/obligation values

It returns:

- decision status
- rejection or referral reasons
- approved amount
- min and max amount
- tenure values
- interest rate
- processing fee percentage

## Service Location

```text
services/bre-python
```

Main file:

```text
services/bre-python/worker.py
```

## Endpoints

- `GET /health`
- `POST /calculate-offer`

## 1. Local Prerequisites

Install:

- Python 3.10+ recommended
- `pip`

Optional:

- Docker
- Google Cloud SDK

## 2. Run Locally With Python

```bash
cd services/bre-python
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python worker.py
```

The service runs on:

- `http://localhost:8080`

Health check:

```bash
curl http://localhost:8080/health
```

## 3. Test A Sample Request

Create `sample-request.json`:

```json
{
  "applicationId": "app-123",
  "personalInfo": {
    "dateOfBirth": "1994-01-10",
    "name": "Sample User"
  },
  "employmentDetails": {
    "monthlySalary": 50000,
    "monthlyEmi": 8000
  },
  "bureauData": {
    "creditScore": 720,
    "writtenOffStatus": false,
    "suitFiled": false
  },
  "documentExtractions": {
    "salarySlip": {
      "netSalary": 48000,
      "grossSalary": 60000
    }
  }
}
```

Run:

```bash
curl -X POST http://localhost:8080/calculate-offer \
  -H "Content-Type: application/json" \
  -d @sample-request.json
```

## 4. Docker Build

```bash
cd services/bre-python
docker build -t loan-app-bre .
docker run -p 8080:8080 loan-app-bre
```

## 5. Deploy To Google Cloud Run

This service is well suited for Cloud Run and can be deployed in a low-cost way for learning or demos.

### 5.1 Create Google Cloud project

1. create or select a Google Cloud project
2. enable billing if required for your account setup
3. install and initialize `gcloud`

```bash
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
```

### 5.2 Enable required APIs

```bash
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
```

### 5.3 Build and deploy

Simple Cloud Build + Cloud Run deployment:

```bash
cd services/bre-python
gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/loan-app-bre
gcloud run deploy loan-app-bre \
  --image gcr.io/YOUR_PROJECT_ID/loan-app-bre \
  --platform managed \
  --region YOUR_REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 1 \
  --port 8080
```

### 5.4 Low-cost / free-tier-friendly recommendations

For an educational demo deployment:

- choose a region supported by Cloud Run free tier in your account context
- keep `min-instances=0`
- keep `max-instances=1`
- use low memory unless your workload proves otherwise
- avoid unnecessary always-on traffic

Actual free-tier availability and limits can vary by time and account setup, so confirm in your Google Cloud billing page before assuming zero cost.

### 5.5 Validate deployment

After deployment:

```bash
curl https://YOUR_CLOUD_RUN_URL/health
```

You should receive a healthy response.

## 6. Point The Android App To Cloud Run

Update the BRE base URL in:

```text
apps/mobile-android/app/src/main/java/com/loansai/unassisted/data/local/database/di/NetworkModule.kt
```

Also review:

```text
apps/mobile-android/app/src/main/res/xml/network_security_config.xml
```

## 7. Validate End-To-End

1. deploy the BRE service
2. update the Android app BRE URL
3. rebuild and install the app
4. complete enough of the app flow to trigger offer calculation
5. confirm the app receives decision and offer data

## 8. Troubleshooting

### Cloud Run deploy fails

Check:

- APIs enabled
- correct project selected
- billing enabled if needed
- Docker build succeeds locally

### App cannot reach BRE

Check:

- Cloud Run URL copied correctly
- service is unauthenticated or auth is handled
- network security config and endpoint match

### BRE responds but offer flow still fails

Check:

- request payload shape
- document extraction availability
- app-side Firestore write path
