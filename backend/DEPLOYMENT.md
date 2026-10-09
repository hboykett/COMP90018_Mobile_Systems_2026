# Deploy the social API to Cloud Run

Run these commands in PowerShell from this repository's `backend` directory. This guide deploys a live service and changes project permissions; the checked-in files do not deploy anything automatically. The existing GitHub CI workflow only runs checks.

Cloud Run builds `Dockerfile` remotely, starts the API, and provides an HTTPS URL. A local Docker installation is not required. `.gcloudignore` limits uploaded files to the runtime source and build configuration; `.dockerignore` also excludes local credentials and data from the image.

## 1. Project and tools

- Enable billing for Firebase project `com-comp90018-flashcards` (the Firebase project becomes Blaze/pay-as-you-go).
- Confirm Firebase Authentication and the `(default)` Firestore database are configured.
- Install the [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) and restart PowerShell.
- Use a project administrator account for the initial API, service-account, IAM, and secret setup below. A teammate with deployment access alone may need the project owner to do those steps.
- Choose a Cloud Run region near the existing Firestore database. The example uses Sydney; check the database's location before using it.

```powershell
gcloud auth login
$projectId = 'com-comp90018-flashcards'
$region = 'australia-southeast1'
$runtimeAccount = "social-api@$projectId.iam.gserviceaccount.com"
$buildAccount = "social-api-build@$projectId.iam.gserviceaccount.com"
gcloud config set project $projectId

gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com iam.googleapis.com firestore.googleapis.com identitytoolkit.googleapis.com --project $projectId
```

Stop and resolve any failed command before continuing. New IAM permissions can take a few minutes to propagate.

## 2. Create build and runtime identities once

The running API needs database access and Firebase user lookups for token revocation checks. Its identity is separate from the build identity; neither needs a downloaded service-account key.

```powershell
gcloud iam service-accounts create social-api --display-name 'Social API runtime' --project $projectId
gcloud iam service-accounts create social-api-build --display-name 'Social API build' --project $projectId

gcloud projects add-iam-policy-binding $projectId --member "serviceAccount:$runtimeAccount" --role roles/datastore.user
gcloud projects add-iam-policy-binding $projectId --member "serviceAccount:$runtimeAccount" --role roles/firebaseauth.viewer
gcloud projects add-iam-policy-binding $projectId --member "serviceAccount:$buildAccount" --role roles/run.builder
```

If these accounts already exist, skip their creation and check their permissions. Subsequent deployers need Cloud Run deployment permissions and permission to act as both service accounts; making the service public also requires permission to change its IAM policy. See Google's [source deployment permissions](https://cloud.google.com/run/docs/deploying-source-code) and [build service account guide](https://cloud.google.com/run/docs/configuring/services/build-service-account).

## 3. Store the existing identity secret

`SOCIAL_IDENTITY_HMAC_KEY` must stay the same across deployments. If social profiles already exist in the target database, use the original key. Do not generate a replacement: startup deliberately rejects a different key for an existing social namespace.

For a new, empty social database only, generate a key if you have not already created one. The Python virtual environment comes from the [local setup](README.md#run-locally).

```powershell
New-Item -ItemType Directory -Force secrets | Out-Null
if (-not (Test-Path secrets/identity-hmac-key.txt)) {
    .venv/Scripts/python.exe -c "import pathlib,secrets; pathlib.Path('secrets/identity-hmac-key.txt').write_text(secrets.token_hex(32))"
}
```

Upload that key to Secret Manager once and grant the runtime account access to this secret:

```powershell
gcloud secrets create social-identity-hmac-key --replication-policy automatic --data-file secrets/identity-hmac-key.txt --project $projectId
gcloud secrets add-iam-policy-binding social-identity-hmac-key --member "serviceAccount:$runtimeAccount" --role roles/secretmanager.secretAccessor --project $projectId
```

If the secret already exists, reuse its existing version. The deployment below pins version `1`; use the appropriate existing version if different. Never commit or paste the key into the Dockerfile or deployment command. Keep a secure backup. See [Cloud Run secrets](https://cloud.google.com/run/docs/configuring/services/secrets).

## 4. Deploy the combined Firestore configuration

From `backend`, install the checked-in Firebase tooling, sign in with project access, and deploy the root rules and all five indexes:

```powershell
npm ci --no-audit --no-fund
npx firebase login
npx firebase deploy --config ../firebase.json --only 'firestore:rules,firestore:indexes' --project $projectId
```

Wait for the indexes to finish building in the Firebase Console. Review any CLI prompt to remove indexes before accepting it, particularly if teammates have added live indexes outside this repository.

## 5. Deploy the API

From `backend`, run:

```powershell
gcloud run deploy social-api `
    --source . `
    --project $projectId `
    --region $region `
    --service-account $runtimeAccount `
    --build-service-account "projects/$projectId/serviceAccounts/$buildAccount" `
    --allow-unauthenticated `
    --set-env-vars "FIREBASE_PROJECT_ID=$projectId,FIRESTORE_DATABASE_ID=(default),SOCIAL_FIRESTORE_NAMESPACE=default" `
    --set-secrets 'SOCIAL_IDENTITY_HMAC_KEY=social-identity-hmac-key:1' `
    --cpu 1 `
    --memory 512Mi `
    --cpu-throttling `
    --min-instances 0 `
    --max-instances 2 `
    --concurrency 20 `
    --timeout 60
```

`--allow-unauthenticated` lets the Android app reach the HTTP service. The Python API still requires and verifies Firebase ID tokens on every `/v1/*` endpoint; `/health` and API documentation are public. Cloud Run IAM tokens and Firebase ID tokens are different, so the application handles user authentication. If your organization blocks public services, its administrator must resolve that policy or arrange an alternative ingress design.

The runtime automatically uses its attached service account. Do not set `GOOGLE_APPLICATION_CREDENTIALS`, `FIRESTORE_EMULATOR_HOST`, or `FIREBASE_AUTH_EMULATOR_HOST` on Cloud Run. The Dockerfile listens on the `PORT` supplied by Cloud Run.

This configuration uses request-based billing and scales to zero. The first request after inactivity can be slower. Maximum instances reduces scaling but is not a hard spending cap; builds, image storage, secrets, Firestore, and network traffic have separate costs. Configure billing alerts; alerts do not stop charges.

## 6. Check the deployed service

```powershell
$apiUrl = (gcloud run services describe social-api --project $projectId --region $region --format 'value(status.url)').Trim()
Invoke-RestMethod "$apiUrl/health"
Start-Process "$apiUrl/docs"
```

Health should return `{"status":"ok"}`. A request to `/v1/me` without a Firebase ID token should return `401`. In `/docs`, use **Authorize** with a real Firebase ID token from the Android app, then test profile setup and the two-user flow in [README.md](README.md#two-user-walkthrough). Health alone does not verify real Firebase token validation, index readiness, or the entire sharing flow.

Use the resulting HTTPS URL as the base URL when wiring Android's social HTTP client. The Android social client and sharing UI still need implementing; deployment does not connect those screens automatically.

If startup fails, inspect **Cloud Run → social-api → Logs**. Common causes are a missing secret version, missing Firestore/Auth permissions, the wrong database/project, or a changed identity key.

For future code updates, rerun step 5 with the same project, region, namespace, service accounts, and secret version. Rebuild/redeploy periodically to incorporate base-image security updates. Firestore rules/index changes are deployed separately with step 4.

## Updating an existing service for Publish and Save-a-copy

Deploy the updated root Firestore rules (step 4) before deploying API version `0.2.0`. These rules protect the new `copiedFrom` metadata from client creation or edits and remain compatible with existing decks and merge writes. The five indexes are unchanged, and no data migration is required.

Update the existing Cloud Run service rather than creating another service. For the current Melbourne deployment, the service name is `comp90018-mobile-systems-2026` and the region is `australia-southeast2`; substitute those for the example service name and region above. Keep the existing runtime/build identities, database, namespace, and `SOCIAL_IDENTITY_HMAC_KEY`. If using a repository-connected Cloud Build trigger, build the commit containing this change with its existing backend Dockerfile configuration.

After deployment, `/openapi.json` should report version `0.2.0` and include `/v1/decks/{deck_id}/publish` and `/v1/decks/{deck_id}/copies`. Use the authenticated [two-user walkthrough](README.md#two-user-walkthrough) to verify sharing, copying, publishing, and revocation on disposable test decks. Emulator tests do not verify deployed credentials or IAM permissions.
