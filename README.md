# Bgyclearance

A Java desktop application for registering barangay business clearances and printing
the clearance report.

Staff enter a business's details (name, address, control number, ownership type,
new or renewal, amount paid). The app saves the record to a local SQLite database
and opens a printable clearance generated with JasperReports. Existing records are
listed in the main window and can be reopened for editing with a double-click.

## Tech stack

- Java 8+ (Swing UI)
- Spring 4.1 (dependency injection, JDBC `DataSource`)
- SQLite via `sqlite-jdbc`
- JasperReports 5.5 for the clearance report
- Maven for build and tests
- JUnit 4 for unit and end-to-end tests

## Project layout

```
src/main/java/com/thub/areyes1/
  main/        BarangayClearanceMain: entry point, boots Spring and shows the main window
  config/      Spring configuration (AppConfig, DataSource, DAO and service wiring)
  ui/          Swing UI: BgyClearanceFrame (list) and BgyClearanceRegistrationDialog (form)
  service/     BarangayClearanceService: save, load, list, remove, save + generate report
  dao/         BarangayClearanceDao: SQL against the bgy_clearance table
  obj/         Model: BarangayClearance and its enums; its data map feeds the report
  util/        ReportUtil / ReportConstants: load and fill the compiled Jasper report
src/main/resources/
  SampleDB.db                       sample SQLite database with the bgy_clearance table
  report/bgyclearance_report.jrxml  report template (source)
  report/bgyclearance_report.jasper compiled report used at runtime
src/test/java/com/thub/areyes1/
  obj/         unit tests
  e2e/         end-to-end tests (see below)
```

## Requirements

- JDK 8 or newer (JDK 17 or 21 recommended)
- Maven 3.6+
- A display for the UI. On a headless Linux machine, use Xvfb (`xvfb-run`).

## Build

```
mvn package
```

This produces `target/bgybus-clearance-0.0.1-SNAPSHOT-jar-with-dependencies.jar`, a
single runnable jar that includes all dependencies. The command also runs the unit
tests. Add `-DskipTests` to skip them.

## Run

The app reads two JVM system properties:

| Property          | Meaning                                                                                     |
|-------------------|---------------------------------------------------------------------------------------------|
| `DB_LOCATION`     | Path to the SQLite database file. It must already contain the `bgy_clearance` table.        |
| `REPORT_LOCATION` | Directory that holds `bgyclearance_report.jasper`, **with a trailing slash**.               |

Using the bundled sample database:

```
cp src/main/resources/SampleDB.db ./clearances.db
java -DDB_LOCATION=./clearances.db \
     -DREPORT_LOCATION=src/main/resources/report/ \
     -jar target/bgybus-clearance-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

To start a new database instead, create it from `src/test/resources/e2e/schema.sql`.

`java -jar` works on Java 8 through 21. The jar's manifest opens `java.lang` to Spring,
which Java 17+ requires. If you launch with `-cp` instead of `-jar`, add
`--add-opens java.base/java.lang=ALL-UNNAMED` yourself on Java 9+.

## Tests

```
xvfb-run -a mvn verify     # Linux without a display
mvn verify                 # machine with a display
```

- `mvn test` runs the **unit tests** (`*Test.java`, Surefire).
- `mvn verify` also packages the jar and runs the **end-to-end tests** (`*IT.java`, Failsafe).

Without a display, the UI and packaged-jar tests are **skipped**, not failed. The
service tests still run.

### What the e2e suite covers

Each test gets a fresh SQLite database built from `src/test/resources/e2e/schema.sql`,
so tests never touch `SampleDB.db` or each other's data.

| Test class                       | Scope                                                                                              |
|----------------------------------|----------------------------------------------------------------------------------------------------|
| `BarangayClearanceServiceE2EIT`  | Real Spring context, DAO, SQLite and Jasper. Covers save, list, load by id, update in place, delete, new vs. renewal, report contents, persistence across restarts, and reading the shipped `SampleDB.db`. After every test it asserts that no JDBC connection was left open (`ConnectionTracker`). |
| `BarangayClearanceUiE2EIT`       | Launches the app through `BarangayClearanceMain` and drives the Swing UI. Covers listing records, registering a new clearance (saved row + report viewer), the table refreshing after a save, renewal, cancel, and editing a record via double-click. |
| `PackagedJarE2EIT`               | Runs the jar-with-dependencies in a separate JVM with `java -jar` and checks that it starts and stays up. |
| `BarangayClearanceTest` (unit)   | The report parameter map built by `BarangayClearance`. Every value must be a `String`, as the `.jrxml` declares. |

UI tests locate components by name (`setName(...)`), for example `newButton`,
`clearanceTable`, `businessNameTxt` and `saveButton`. When you add a field that tests
should drive, give it a name.

### Continuous integration

`.github/workflows/ci.yml` runs `xvfb-run -a mvn -B verify` on JDK 21 for every pull
request and every push to `master`. Test reports are uploaded when a run fails.

## Known limitations

- The report template is still a draft. Its text fields are about 100px wide, so long
  business names and addresses are cut off. It also still contains placeholder labels
  (`AAAA`, `BBB`) and prints `null` for the barangay, which is never set. Change the
  `.jrxml` and recompile the `.jasper` to fix this.
- Updating a record changes its name, address, ownership, association president and
  amount paid. It does not change the control number or the new/renewal and ownership
  flags.
- The "Search" button and "Change Bgy Configuration" are not implemented yet.
