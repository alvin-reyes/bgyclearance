# Barangay Business Clearance

A web app for barangay offices to register business clearances and print them. It
runs on the office computer and you use it in a browser. There is no internet
service and no separate database server.

- **Dashboard:** totals for all time and this year, new vs. renewal, recent clearances.
- **Clearances:** search by name, address or control number; filter by type; sort by
  any column; 25 per page.
- **Register and edit:** a form with every field of the clearance. It checks required
  fields and number formats, and warns when a control number is already used
  (0 counts as "not assigned" and may repeat, as in the desktop app).
- **Print:** each clearance prints as a one-page, letter-size PDF with the barangay
  letterhead, the certification, the business details, the payment details and the
  signature lines.
- **Settings:** the barangay name, city or municipality, province, punong barangay and
  secretary, as printed on clearances.
- It works on phones and tablets, and has a dark mode.

## Quick start

You need Java 21 or newer. Maven is not needed; the included `./mvnw` wrapper downloads it.

```
./mvnw package -DskipTests                  # Windows: mvnw.cmd package -DskipTests
java -jar target/bgyclearance.jar
```

Then open <http://localhost:8080>, go to **Settings**, and enter your barangay's details.

| To… | Do this |
|-----|---------|
| Use a specific database file | `java -DDB_LOCATION=D:\clearances\clearances.db -jar bgyclearance.jar` (or set the `DB_LOCATION` environment variable). The default is `clearances.db` in the folder you start the app from. |
| Change the port | add `--server.port=9090` |
| Let other computers on the office network use it | add `--server.address=0.0.0.0`. The app has no login, so only do this on a network you trust. |
| Run from source while developing | `./mvnw spring-boot:run` |

**Back up your data** by copying the `.db` file while the app is stopped.

### Upgrading from the desktop version

Point the app at your existing database file:

```
java -DDB_LOCATION=path/to/SampleDB.db -jar bgyclearance.jar
```

On first start, the app adds the new columns it needs (date issued, OR number and so
on) and keeps every existing record. Records from the desktop app have no issue date,
and many have no control number. They display "—" for those, and editing one asks you
to fill in the required details before it saves.

## Changing the printed clearance

The printout is an ordinary HTML page with CSS:
[`src/main/resources/templates/print/clearance.html`](src/main/resources/templates/print/clearance.html).
Edit the wording or layout there and rebuild. Keep it well-formed XHTML: close every
tag, and write `&#160;` instead of `&nbsp;`. The PDF uses the bundled Liberation Serif
font (SIL Open Font License, see `src/main/resources/fonts/`) so the peso sign and
accented names print on any computer.

## How it's built

- Java 21, Spring Boot 4.1 (Spring MVC, Thymeleaf, validation, `JdbcClient`)
- SQLite through `sqlite-jdbc`: one file, no server
- openhtmltopdf renders the printed clearance from a Thymeleaf template

```
src/main/java/com/thub/areyes1/
  BgyClearanceApplication.java   entry point
  clearance/   Clearance record, ClearanceRepository (SQL), query/sort/paging types
  settings/    BarangaySettings and SettingsRepository
  print/       ClearancePrinter: HTML template -> PDF
  db/          SchemaMigrator: creates or upgrades the tables on startup
  web/         controllers, the form object and list link helper
src/main/resources/
  application.properties    database, address and port
  templates/                pages (dashboard, clearances/*, settings) and print/clearance.html
  static/                   stylesheet and a small script (theme toggle, delete confirmation)
  fonts/                    Liberation Serif for the PDF, with its license
```

The database keeps the table and column names used by the original desktop app, so
old and new versions can read the same file.

## Tests

```
./mvnw verify
```

This runs every test and builds `target/bgyclearance.jar`. No display or browser needs
to be installed.

| Test | What it checks |
|------|----------------|
| `WebAppTest` | Starts the real app on a random port and uses it through a headless browser (HtmlUnit). Covers the dashboard, registering (every field reaches the database), validation messages, duplicate control numbers, sorting, filtering, searching and paging, editing, deleting, 404 pages, settings, and the printed PDF's contents. |
| `ClearanceRepositoryTest` | Saving, loading, updating and deleting; search, filters, every sort order, paging and totals; values written by the old desktop app. |
| `LegacyDatabaseTest` | Opens the desktop app's `SampleDB.db`, checks it is upgraded, and that all 59 records survive. |
| `ClearancePrinterTest` | The PDF is one letter-size page with every detail, embedded fonts, and correct renewal wording and blanks. |
| `SettingsRepositoryTest`, `ClearanceFormTest`, `ListViewTest` | Settings storage, form conversion, and list link building. |
| `PackagedJarIT` | Runs `java -jar target/bgyclearance.jar` from an empty folder. Checks that it creates its database, saves a clearance, and prints a PDF. |

Tests pin "today" to 15 March 2026, so date-based results never change.

CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on JDK 21 for every pull
request and every push to `master`.

## Known limitations

- There is no login. Anyone who can open the page can add, edit or delete clearances,
  so keep it on `127.0.0.1` (the default) or a trusted network.
- It is built for one office. SQLite handles one writer at a time, which is plenty for
  a few people at the counter, but it isn't meant for many simultaneous users.
- The certification wording and the "Not valid without the official seal" note are
  generic. Adjust them in the print template to match your barangay's practice.
