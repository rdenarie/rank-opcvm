rank-opcvm
==========

Ranks the funds (OPCVM) sold in France within their Morningstar category, once a quarter, and publishes the
ranking as static pages: https://rank-opcvm.appspot.com/

## How it works

- **Import** (`ImportService`, `/importService`): run by cron on the 1st of each quarter (`cron.yaml`); App Engine
  refuses any other caller (`X-Appengine-Cron` header). In a few minutes it:
  1. reads the funds registered for sale in France from the Morningstar screener (`MorningstarClient`, ~60 calls),
     the ECB exchange rates (`EcbRates`) and the BoursoBank partner lists from Boursorama (`BoursoPartners`, ~140 pages);
  2. keeps the funds of the personal categories (`CategoriesService`), filters them (no performance, last price older
     than a year, no EUR price, minimum purchase above 50 000 €), scores and ranks them (`ImportBuilder`, `Scoring`);
  3. publishes the JSON files to the Cloud Storage bucket `rank-opcvm-data` (`ImportJson`, `CloudStorage`):
     `index.json`, `imports/<date>.json`, `history/<0..63>.json`;
  4. mails the report with its Excel attachment (`MailService`, `ReportService`), or an alert when the import is
     not published (Morningstar or ECB unavailable, or fewer than half the previous import's funds). The mails go
     through Gmail's SMTP server: those of the App Engine Mail API no longer reach Gmail.
- **Pages** (`index.html`, `fund.html`): static files served by App Engine without starting an instance; they read
  the JSON files from the bucket. Add `?data=<url>` to read them from elsewhere (local tests).

## One-time setup

    gcloud storage buckets create gs://rank-opcvm-data --location=europe-west1 --uniform-bucket-level-access
    gcloud storage buckets add-iam-policy-binding gs://rank-opcvm-data --member=allUsers --role=roles/storage.objectViewer
    gcloud storage buckets update gs://rank-opcvm-data --cors-file=tools/cors.json

    gcloud storage buckets add-iam-policy-binding gs://rank-opcvm-data --member=serviceAccount:rank-opcvm@appspot.gserviceaccount.com --role=roles/storage.objectAdmin

The last command lets the App Engine default service account write the files: objectAdmin, not objectCreator, since
the import overwrites `index.json` and the history files.

The mails need a Gmail app password (https://myaccount.google.com/apppasswords) in Secret Manager, readable by the
App Engine service account:

    gcloud services enable secretmanager.googleapis.com
    read -s -p "App password: " P && printf '%s' "$P" | gcloud secrets create gmail-app-password --data-file=-; unset P
    gcloud secrets add-iam-policy-binding gmail-app-password --member=serviceAccount:rank-opcvm@appspot.gserviceaccount.com --role=roles/secretmanager.secretAccessor

The imports made before Morningstar (Datastore era) are converted once by `tools/migrate_datastore.py`, which prints
the upload commands. Run it before the first Morningstar import, which adds itself to the history files it finds.

## Build, test, deploy

    mvn test
    mvn package appengine:deploy
    gcloud app deploy cron.yaml

To run an import by hand: Google Cloud console > App Engine > Cron jobs > `/importService` > Run now.
