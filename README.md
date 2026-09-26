# Barangay Business Clearance

A web app for barangay offices to register business clearances and print them. It
runs on the office computer and you use it in a browser. There is no internet
service and no separate database server.

- **Dashboard:** totals for all time and this year, new vs. renewal, recent clearances.
- **Clearances:** search by name, address or control number; filter by type; sort by
  any column; 25 per page.
- **Register and edit:** a form with every field of the clearance.
  - It checks required fields and number formats as you go, and warns when a control
    number is already used. 0 counts as "not assigned" and may repeat, as in the
    desktop app.
  - It suggests the next control number and previous types of business.
  - Amounts can be typed as `1,250.50` or `₱1250.5`.
  - It warns before you leave with unsaved changes, and Ctrl+S saves.
- **Print:** each clearance prints as a one-page clearance, on letter or long bond
  (8.5 × 13 in) paper as chosen in Settings, with the
  barangay letterhead, the certification, the business and payment details, and the
  signature lines. You can send it straight to a printer, or open it as a PDF.
- **Reports:** clearances issued and amounts collected for any period (today, this
  month, last month, this year, last year or any dates), optionally only new or only
  renewals.
  - A collections chart by day, month or year, a breakdown by type of business, and
    the full list with totals.
  - Print it or open it as a PDF with the letterhead and signature lines,
    or download the clearances as a spreadsheet (CSV for Excel or Google Sheets).
- **Pre-printed forms:** if the office's clearance forms are already printed, the
  app prints only the values, each on its blank. A layout editor in Settings places
  them by dragging over a picture of the blank form or by millimetres, with a
  printer adjustment and an alignment test print. The starting positions come from
  the desktop app's original clearance template.
- **Printers:** the app finds printers installed on the computer and printers on the
  office network, and lets you add one by IP address. You choose a default in Settings.
- **Settings:** the barangay name, city or municipality, province, punong barangay and
  secretary, as printed on clearances, plus the printers.
- It works on phones and tablets, and has a dark mode.

**New to the app?** The [user guide](docs/GUIDE.md) walks through installing it, setting
it up, registering and printing clearances, backups and troubleshooting, step by step.

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

## Printers

Open **Settings → Printers** and pick the printer to use for clearances. Once one is
chosen, each clearance page has a **Print to …** button. **Open PDF** is always
available too. Printers come from three places:

| Source | How it's found | How the app prints to it |
|--------|----------------|--------------------------|
| **Installed on this computer** | Every printer set up in Windows (or CUPS on Linux and macOS), including network printers someone already added there. | Through the computer's own printer driver, so anything that prints from other programs works. |
| **Found on the network** | The app looks for network printers the same way phones and laptops do (Bonjour / AirPrint, "IPP Everywhere"). It scans when it starts and when you press **Scan again**. | It sends the PDF directly to the printer over IPP. |
| **Added by address** | For networks where scanning is blocked, type the printer's IP address (for example `192.168.1.20`) or its full `ipp://` address. | Directly over IPP, as above. |

Notes:
- **Test first:** use **Test page** to check a printer before relying on it.
- **Printers that don't take PDF:** some network printers only accept their own
  formats. The app detects this and greys them out. Add such a printer to the
  computer's printers (with its driver) and choose it from the installed list instead.
- **Messages when printing fails** say what went wrong in plain words: the printer is
  off or unreachable, busy, or not accepting jobs.
- **Settings:** `-Dbgy.printers.discovery=false` turns network scanning off,
  `-Dbgy.printers.installed=false` hides the computer's printers, and
  `-Dbgy.printers.scan-time=6s` scans longer on slow networks.
- **Linux:** printing to installed printers uses CUPS's `lpr` command (package
  `cups-bsd`). If it's missing, the error message says so.

## Changing the printed clearance

The printout is an ordinary HTML page with CSS:
[`src/main/resources/templates/print/clearance.html`](src/main/resources/templates/print/clearance.html).
Edit the wording or layout there and rebuild. Keep it well-formed XHTML: close every
tag, and write `&#160;` instead of `&nbsp;`.

## Fonts

All fonts are bundled inside the app, so they work without an internet connection and
look the same on every computer. Both families are licensed under the SIL Open Font
License; the license files ship next to the fonts.

| Font | Used for | Files |
|------|----------|-------|
| **Inter** (variable, by Rasmus Andersson) | Every screen of the app | `src/main/resources/static/fonts/` |
| **Source Serif 4** (Adobe): Regular, Italic, Semibold, Bold, and Display Semibold for titles | The printed clearance and the printer test page | `src/main/resources/fonts/` |

Both cover the peso sign (₱), ñ/Ñ and accented names.

## How it's built

- Java 21, Spring Boot 4.1 (Spring MVC, Thymeleaf, validation, `JdbcClient`)
- SQLite through `sqlite-jdbc`: one file, no server
- openhtmltopdf renders the printed clearance and reports from Thymeleaf templates
- Printing: a small built-in IPP client, JmDNS for network discovery, and PDFBox with
  the Java Print Service for installed printers

```
src/main/java/com/thub/areyes1/
  BgyClearanceApplication.java   entry point
  clearance/   Clearance record, ClearanceRepository (SQL), query/sort/paging types
  settings/    BarangaySettings and SettingsRepository
  report/      ReportPeriod, ClearanceReport (totals, collections over time, types of
               business), Reports service, ReportCsv spreadsheet export
  print/       ClearancePrinter: HTML template -> PDF (clearance, report, printer test page),
               PaperSize; form/ places values on pre-printed forms (FormField, FormLayout,
               FormLayouts, FormOverlayPrinter)
  printing/    Printers (all sources, default printer), IppClient, NetworkPrinterDiscovery,
               InstalledPrinters
  db/          SchemaMigrator: creates or upgrades the tables on startup
  web/         controllers, the form object and list link helper
src/main/resources/
  application.properties    database, address and port
  templates/                pages (dashboard, clearances/*, reports, settings), fields.html
                            (form field fragment) and print/ (clearance, report, test page)
  static/                   stylesheet, script (form checks, theme, confirmations), Inter font
  fonts/                    Source Serif 4 for printed documents, with its license
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
| `WebAppTest` | Starts the real app on a random port and uses it through a headless browser (HtmlUnit). Covers the dashboard, registering (every field reaches the database), validation messages and their screen-reader wiring, duplicate control numbers, the next-number and business-type suggestions, amounts with commas and ₱, sorting, filtering, searching and paging, editing, deleting, 404 pages, settings, and the printed PDF's contents. |
| `PrintingWebTest` | Adds a fake network printer through Settings, makes it the default, and prints a clearance and a test page to it. Also covers printers that are busy, unreachable or don't take PDF, and removing the default printer. |
| `IppClientTest`, `NetworkPrinterDiscoveryTest`, `PrinterAddressTest`, `InstalledPrintersTest` | The IPP messages sent and read, error wording, turning network announcements into printers (and confirming PDF support over IPP), parsing typed printer addresses, and root-cause error messages. |
| `MdnsDiscoveryIT` | Announces a pretend printer on the real network (mDNS) and checks the scanner finds it. Skipped on machines without a multicast network interface. |
| `ClearanceRepositoryTest` | Saving, loading, updating and deleting; search, filters, every sort order, paging and totals; next control number and business-type suggestions; values written by the old desktop app. |
| `LegacyDatabaseTest` | Opens the desktop app's `SampleDB.db`, checks it is upgraded, and that all 59 records survive. |
| `ReportWebTest` | Choosing long bond paper in Settings changes the clearance and report PDFs. Opens reports through the browser: this month from the menu, the period buttons, a custom range with reversed dates and a type filter, an empty period, the PDF's contents, the spreadsheet download, and printing a report to a fake printer. Records with no date issued are counted but never placed. |
| `FormLayoutWebTest` | Turns on pre-printed forms in Settings and checks clearances then print only their values; moves, switches off and resizes fields from the table; the printer adjustment; resetting; printing an alignment test to a fake printer; adding, showing and removing a blank-form picture (pictures only); stored layouts with odd values. |
| `FormOverlayPrinterTest` | With the default layout each value lands where the desktop app's Jasper template put it (to within half a point); fields can be moved, switched on and ticked; the printer adjustment moves everything; long values shrink to fit; characters the font lacks are dropped; limits on positions and sizes. |
| `ClearanceReportTest`, `ReportPeriodTest`, `ReportCsvTest` | Totals, grouping by day, month or year (including days with nothing issued), chart gridlines and labels, merging types of business and combining the rare ones; period parsing, names and presets; the CSV's byte order mark, quoting and protection against spreadsheet formulas. |
| `ClearancePrinterTest` | The PDF is one page on letter or long bond paper, with every detail (including every field the desktop app's unfinished Jasper template declared), embedded Source Serif 4, and correct renewal wording and blanks. |
| `SettingsRepositoryTest`, `ClearanceFormTest`, `ListViewTest`, `AmountEditorTest` | Settings storage, form conversion, list link building, and amount parsing. |
| `PackagedJarIT` | Runs `java -jar target/bgyclearance.jar` from an empty folder. Checks that it creates its database, saves a clearance, and prints a PDF. |

Tests never touch real printers: they turn off both network scanning and the
computer's installed printers, and use a fake IPP printer instead. Printing was also
checked by hand against a real CUPS print server, using an installed queue, the same
queue found over Avahi/mDNS, and its `ipp://` address.

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
