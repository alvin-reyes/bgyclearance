# Bgyclearance
A Java Application to Cater for the Barangay Clearance Reporting of disbursements

## Tech Stack
Java/J2SE
Jasper Reports

## End-to-end tests
The e2e suite lives in `src/test/java/com/thub/areyes1/e2e` and runs in `mvn verify`.
Each test gets a fresh SQLite database built from `src/test/resources/e2e/schema.sql`.

- `BarangayClearanceServiceE2EIT` boots the Spring data/service config and covers
  create, list, load, update, delete and report generation, as well as reading the
  shipped `SampleDB.db`.
- `BarangayClearanceUiE2EIT` launches the app through `BarangayClearanceMain` and drives
  the Swing UI: listing records, registering a new clearance, and opening a record for editing.

The UI tests need a display. On a headless machine, run:

```
xvfb-run -a mvn verify
```

Without a display the UI tests are skipped, not failed.
