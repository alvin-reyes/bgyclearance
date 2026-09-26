# Bgyclearance

An application for registering barangay business clearances and printing the
clearance report. It comes in two forms that share the same code and database:

- **Web app:** runs on your own computer and is used in a browser at
  <http://localhost:8080>.
- **Desktop app:** the original Java Swing window.

Staff enter a business's details (name, address, control number, ownership type,
new or renewal, OR number, amount paid). The app saves the record to a local SQLite
database and produces a printable clearance generated with JasperReports.

## Quick start (web app)

Requirements: JDK 8 or newer (17 or 21 recommended) and Maven 3.6+.

```
mvn package -DskipTests
java -jar target/bgybus-clearance-0.0.1-SNAPSHOT-web.jar
```

Then open <http://localhost:8080>.

- On first start, the app creates `clearances.db` in the current directory. To use a
  different file, pass `-DDB_LOCATION=/path/to/file.db`, for example
  `java -DDB_LOCATION=/data/clearances.db -jar ...-web.jar`.
- To use a different port, add `--server.port=9090` after the jar name.
- By default the server only accepts connections from this computer. To let other
  computers on the office network use it, add `--server.address=0.0.0.0`. The app has
  no login, so only do this on a trusted network.
- During development you can run it with `mvn spring-boot:run` instead.

### What you can do in the browser

| Page | What it does |
|------|--------------|
| **Clearances** (`/clearances`) | Lists all clearances, newest first. You can search by business name, address or exact control number. |
| **New clearance** | A form with the same fields as the desktop dialog. Required fields and number formats are checked, and mistakes are shown next to the field. |
| **Clearance detail** | Shows every field, with buttons to **Print clearance (PDF)**, **Edit** and **Delete**. Delete asks for confirmation. |
| **Print clearance (PDF)** | Opens the Jasper clearance as a PDF in a new tab, ready to print. |

## Desktop app

```
mvn package -DskipTests
java -DDB_LOCATION=./clearances.db \
     -jar target/bgybus-clearance-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

It needs a display. If the database file doesn't exist yet, it is created.
`REPORT_LOCATION` is optional. It names a directory, **with a trailing slash**, that
holds a replacement `bgyclearance_report.jasper`; without it, the report bundled in
the jar is used.

Both jars work on Java 8 and newer (tested on 21). The desktop jar's manifest opens
`java.lang`, which Spring's proxies need on Java 17+. If you launch it with `-cp`
instead of `-jar` on Java 9+, add `--add-opens java.base/java.lang=ALL-UNNAMED` yourself.

## Tech stack

- Java 8+
- Spring Boot 2.7 (Spring MVC, Thymeleaf, embedded Tomcat) for the web app
- Swing for the desktop app
- Spring 5.3 JDBC and a shared service/DAO layer
- SQLite via `sqlite-jdbc`
- JasperReports 5.5 for the clearance report (PDF on the web, viewer on the desktop)
- Maven. Tests use JUnit 4 and HtmlUnit.

## Project layout

```
src/main/java/com/thub/areyes1/
  web/         Web app: WebApplication (entry point), ClearanceController, ClearanceForm
  main/        Desktop entry point: BarangayClearanceMain
  ui/          Swing UI: BgyClearanceFrame (list) and BgyClearanceRegistrationDialog (form)
  config/      Spring wiring shared by both apps (DataSource, DAO, service)
  service/     BarangayClearanceService: save, load, list, remove, save + generate report
  dao/         BarangayClearanceDao (SQL) and DatabaseSchema (creates/upgrades the table)
  obj/         Model: BarangayClearance and its enums; its data map feeds the report
  util/        ReportUtil: fills the Jasper report and exports PDFs
src/main/resources/
  templates/   Thymeleaf pages (list, detail, form, error)
  static/css/  Stylesheet for the web app
  db/schema.sql                     bgy_clearance table definition
  application.properties            web server settings (address, port)
  SampleDB.db                       sample database with 59 records
  report/bgyclearance_report.jrxml  report template (source)
  report/bgyclearance_report.jasper compiled report used at runtime
src/test/java/com/thub/areyes1/
  obj/         unit tests
  e2e/         end-to-end tests (see below)
```

### Database

Both apps run `DatabaseSchema` on startup. It creates the `bgy_clearance` table if
it is missing, and adds any columns introduced since older databases were made
(`capitalization`, `or_number`, `applicant_member_of`). Existing files such as
`SampleDB.db` keep working. Back up the `.db` file to back up your data.

## Tests

```
xvfb-run -a mvn verify     # Linux without a display
mvn verify                 # machine with a display
```

- `mvn test` runs the **unit tests** (`*Test.java`, Surefire).
- `mvn verify` also builds both jars and runs the **end-to-end tests** (`*IT.java`,
  Failsafe).

Without a display, the desktop UI and desktop-jar tests are **skipped**, not failed.
The service and web tests always run.

### What the e2e suite covers

Each test gets a fresh SQLite database built from `src/main/resources/db/schema.sql`,
so tests never touch `SampleDB.db` or each other's data.

| Test class | Scope |
|------------|-------|
| `BarangayClearanceWebE2EIT` | Starts the real web app on a random port and drives it with a headless browser (HtmlUnit). Covers the empty state, registering a clearance (every field reaches the database), validation messages, newest-first listing and search, editing in place, delete, printing the PDF, the 404 page, and restarting on the shipped `SampleDB.db`. |
| `PackagedWebJarE2EIT` | Runs the `-web.jar` with `java -jar` from an empty directory. Checks that it creates its database, saves a clearance, and serves the PDF from the report bundled in the jar. |
| `BarangayClearanceServiceE2EIT` | Real Spring context, DAO, SQLite and Jasper. Covers save (with generated ids), list, load, update, delete, every stored field, report contents with no `null` text, restarts, and upgrading `SampleDB.db`. After every test it asserts that no JDBC connection was left open (`ConnectionTracker`). |
| `BarangayClearanceUiE2EIT` | Launches the desktop app through `BarangayClearanceMain` and drives the Swing UI: listing, registering, the table refreshing, renewal, cancel, and editing, including that an edit keeps fields it doesn't show. |
| `PackagedJarE2EIT` | Runs the desktop jar with `java -jar` and checks that it starts and stays up. |
| `BarangayClearanceTest` (unit) | The report parameter map built by `BarangayClearance`. Every value must be a `String`, as the `.jrxml` declares. |

Test hooks:
- Web tests locate elements by `id`, for example `new-clearance`, `save`, `print`,
  `flash` and `business-name`.
- Desktop tests locate components by name (`setName(...)`), for example
  `businessNameTxt` and `saveButton`.

Keep these ids and names when you change the pages.

### Continuous integration

`.github/workflows/ci.yml` runs `xvfb-run -a mvn -B verify` on JDK 21 for every pull
request and every push to `master`. Test reports are uploaded when a run fails.

## Known limitations

- **The report template is still a draft.** Its text fields are about 100px wide, so
  long business names and addresses are cut off. It also still contains placeholder
  labels (`AAAA`, `BBB`), and the barangay field is always blank. Change the `.jrxml`
  and recompile the `.jasper` to fix this.
- **The desktop form's manager/operator field is not saved.**
- **The web app has no login.** Keep it bound to `127.0.0.1` (the default) unless the
  network is trusted.
- **The desktop app's "Search" button and "Change Bgy Configuration" are not
  implemented.** The web app has search.
