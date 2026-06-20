# Email Verification Setup

This project uses Firebase Functions and Brevo for OTP and email-based workflows.

## What This Covers

- Brevo account setup
- Firebase Functions configuration
- sender verification
- deploying and validating OTP/email flows

## 1. Create A Brevo Account

1. Sign up for Brevo
2. verify your sender email or domain
3. generate an API key

Keep the API key private.

## 2. Review The Email Backend

The main backend email logic is in:

```text
services/firebase-support/functions-ver4/functions/src/index.ts
```

The Android app also contains an app-side Brevo email helper here:

```text
apps/mobile-android/app/src/main/java/com/loansai/unassisted/service/email/BrevoEmailService.kt
```

For most real setups, prefer backend-controlled email sending through Firebase Functions.

## 3. Configure Firebase Functions With Brevo

Set config values:

```bash
cd services/firebase-support/functions-ver4
firebase functions:config:set \
  brevo.key="YOUR_BREVO_API_KEY" \
  brevo.from="verified-sender@example.com" \
  brevo.to="alerts@example.com"
```

Then deploy:

```bash
firebase deploy --only functions
```

## 4. Configure The Android App

If you want the app-side Brevo helper available for local testing, add:

```properties
brevo_api_key=YOUR_BREVO_API_KEY
```

to local:

```text
apps/mobile-android/keystore.properties
```

Review sender-related values in:

```text
apps/mobile-android/app/src/main/java/com/loansai/unassisted/service/email/BrevoEmailService.kt
```

Update them to your own verified sender configuration.

## 5. Deploy And Test

After deploying Firebase Functions:

1. launch the app
2. trigger the login or verification flow
3. confirm the OTP email arrives
4. confirm OTP values are stored and validated correctly

## 6. Common Failure Points

- Brevo API key missing
- sender email not verified
- Firebase Functions not deployed
- wrong Firebase project selected
- app still pointing to a different environment
