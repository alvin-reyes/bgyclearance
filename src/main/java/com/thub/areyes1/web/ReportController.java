package com.thub.areyes1.web;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;

import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.print.ClearancePrinter;
import com.thub.areyes1.printing.PrintFailure;
import com.thub.areyes1.printing.Printer;
import com.thub.areyes1.printing.Printers;
import com.thub.areyes1.report.ClearanceReport;
import com.thub.areyes1.report.ReportCsv;
import com.thub.areyes1.report.ReportPeriod;
import com.thub.areyes1.report.Reports;
import com.thub.areyes1.settings.SettingsRepository;

/** Reports of the clearances issued in a period, on screen, as a PDF, printed, or as a spreadsheet. */
@Controller
@RequestMapping("/reports")
public class ReportController {

	private final Reports reports;
	private final SettingsRepository settings;
	private final ClearancePrinter printer;
	private final Printers printers;
	private final Clock clock;

	public ReportController(Reports reports, SettingsRepository settings, ClearancePrinter printer, Printers printers,
			Clock clock) {
		this.reports = reports;
		this.settings = settings;
		this.printer = printer;
		this.printers = printers;
		this.clock = clock;
	}

	@GetMapping
	public String report(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String type, Model model) {
		ClearanceReport report = build(from, to, type);
		LocalDate today = LocalDate.now(clock);
		model.addAttribute("r", report);
		model.addAttribute("chart", report.chart());
		model.addAttribute("presets", ReportPeriod.presets(today).stream()
				.map(p -> new PresetLink(p.label(), link("/reports", p.period(), report.type()),
						p.period().equals(report.period())))
				.toList());
		model.addAttribute("pdfLink", link("/reports/report.pdf", report.period(), report.type()));
		model.addAttribute("csvLink", link("/reports/clearances.csv", report.period(), report.type()));
		model.addAttribute("printLink", link("/reports/print", report.period(), report.type()));
		model.addAttribute("printerName", printers.defaultPrinter().map(Printer::name).orElse(null));
		return "reports";
	}

	public record PresetLink(String label, String href, boolean active) {
	}

	@GetMapping("/report.pdf")
	public ResponseEntity<byte[]> pdf(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) String type) {
		ClearanceReport report = build(from, to, type);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header("Content-Disposition", ContentDisposition.inline()
						.filename("clearance-report-" + report.period().slug() + ".pdf").build().toString())
				.body(printer.report(report, settings.load()));
	}

	@GetMapping("/clearances.csv")
	public ResponseEntity<byte[]> csv(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) String type) {
		ClearanceReport report = build(from, to, type);
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header("Content-Disposition", ContentDisposition.attachment()
						.filename("clearances-" + report.period().slug() + ".csv").build().toString())
				.body(ReportCsv.write(report));
	}

	/** Sends the report straight to the default printer chosen in Settings. */
	@PostMapping("/print")
	public String print(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String type, RedirectAttributes redirect) {
		ClearanceReport report = build(from, to, type);
		try {
			Printer target = printers.defaultPrinter().orElseThrow(() -> new PrintFailure(
					"No printer is set up. Choose one in Settings, or use Open PDF.", null));
			byte[] pdf = printer.report(report, settings.load());
			redirect.addFlashAttribute("message",
					printers.print(target.id(), pdf, "Clearance report " + report.period().label()));
		} catch (PrintFailure e) {
			redirect.addFlashAttribute("printError", e.getMessage());
		}
		return "redirect:" + link("/reports", report.period(), report.type());
	}

	private ClearanceReport build(String from, String to, String type) {
		return reports.build(ReportPeriod.parse(from, to, LocalDate.now(clock)), type(type));
	}

	private static ClearanceType type(String value) {
		return Arrays.stream(ClearanceType.values())
				.filter(t -> t.name().equalsIgnoreCase(value == null ? "" : value.strip()))
				.findFirst()
				.orElse(null);
	}

	static String link(String path, ReportPeriod period, ClearanceType type) {
		UriComponentsBuilder b = UriComponentsBuilder.fromPath(path)
				.queryParam("from", period.from())
				.queryParam("to", period.to());
		if (type != null) {
			b.queryParam("type", type.name());
		}
		return b.build().toUriString();
	}
}
