package com.thub.areyes1.web;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.thub.areyes1.print.ClearancePrinter;
import com.thub.areyes1.print.PaperSize;
import com.thub.areyes1.print.form.FormField;
import com.thub.areyes1.print.form.FormLayout;
import com.thub.areyes1.print.form.FormLayout.Placement;
import com.thub.areyes1.print.form.FormLayouts;
import com.thub.areyes1.print.form.FormSample;
import com.thub.areyes1.printing.PrintFailure;
import com.thub.areyes1.printing.Printer;
import com.thub.areyes1.printing.Printers;
import com.thub.areyes1.settings.BarangaySettings;
import com.thub.areyes1.settings.SettingsRepository;

/**
 * Settings for offices that print onto pre-printed clearance forms: where each value
 * goes on the form, a shift to make up for the printer, and alignment test prints.
 */
@Controller
@RequestMapping("/settings/form")
public class FormLayoutController {

	private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

	private final FormLayouts forms;
	private final ClearancePrinter printer;
	private final Printers printers;
	private final SettingsRepository settings;

	public FormLayoutController(FormLayouts forms, ClearancePrinter printer, Printers printers,
			SettingsRepository settings) {
		this.forms = forms;
		this.printer = printer;
		this.printers = printers;
		this.settings = settings;
	}

	/** One row of the editor: a field, where it goes, and the sample value shown on the preview. */
	public record Row(FormField field, Placement placement, String sample) {
	}

	@GetMapping
	public String edit(Model model) {
		FormLayout layout = forms.load();
		BarangaySettings sampleSettings = FormSample.settings(settings.load());
		var sample = FormSample.clearance(LocalDate.of(2026, 1, 1));
		List<Row> rows = Arrays.stream(FormField.values())
				.map(f -> new Row(f, layout.placement(f), f.valueFor(sample, sampleSettings)))
				.toList();
		PaperSize paper = printer.paperSize();
		model.addAttribute("layout", layout);
		model.addAttribute("rows", rows);
		model.addAttribute("paper", paper);
		model.addAttribute("pageWidth", 215.9);
		model.addAttribute("pageHeight", paper == PaperSize.LONG ? 330.2 : 279.4);
		model.addAttribute("hasBackground", forms.background().isPresent());
		model.addAttribute("printerName", printers.defaultPrinter().map(Printer::name).orElse(null));
		return "form-layout";
	}

	@PostMapping
	public String save(@RequestParam Map<String, String> params, RedirectAttributes redirect) {
		forms.save(read(params, forms.load()));
		redirect.addFlashAttribute("message", "Form layout saved.");
		return "redirect:/settings/form";
	}

	/** Saves the layout as shown, then prints a sample on the chosen printer. */
	@PostMapping("/print-test")
	public String printTest(@RequestParam Map<String, String> params, RedirectAttributes redirect) {
		FormLayout layout = read(params, forms.load());
		forms.save(layout);
		try {
			Printer target = printers.defaultPrinter().orElseThrow(() -> new PrintFailure(
					"No printer is set up. Choose one in Settings, or use Open test PDF.", null));
			String sent = printers.print(target.id(), printer.formTest(layout, settings.load()), "Form alignment test");
			redirect.addFlashAttribute("message", "Layout saved. Alignment test: " + sent);
		} catch (PrintFailure e) {
			redirect.addFlashAttribute("printError", e.getMessage());
		}
		return "redirect:/settings/form";
	}

	@GetMapping("/test.pdf")
	public ResponseEntity<byte[]> testPdf() {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header("Content-Disposition",
						ContentDisposition.inline().filename("form-alignment-test.pdf").build().toString())
				.body(printer.formTest(forms.load(), settings.load()));
	}

	@PostMapping("/reset")
	public String reset(RedirectAttributes redirect) {
		FormLayout current = forms.load();
		forms.save(new FormLayout(current.preprinted(), 0, 0, Map.of()));
		redirect.addFlashAttribute("message", "Every field is back where the original clearance template put it.");
		return "redirect:/settings/form";
	}

	@PostMapping("/background")
	public String background(@RequestParam("scan") MultipartFile scan, RedirectAttributes redirect) throws IOException {
		String type = scan.getContentType() == null ? "" : scan.getContentType().toLowerCase(Locale.ROOT);
		if (scan.isEmpty() || !IMAGE_TYPES.contains(type)) {
			redirect.addFlashAttribute("printError", "Choose a photo or scan of the blank form (JPG or PNG).");
		} else if (scan.getSize() > FormLayouts.MAX_BACKGROUND_BYTES) {
			redirect.addFlashAttribute("printError", "That picture is too large. Use one under 8 MB.");
		} else {
			forms.saveBackground("data:" + type + ";base64," + Base64.getEncoder().encodeToString(scan.getBytes()));
			redirect.addFlashAttribute("message", "Blank form added. Drag the values into place over it.");
		}
		return "redirect:/settings/form";
	}

	@PostMapping("/background/remove")
	public String removeBackground(RedirectAttributes redirect) {
		forms.saveBackground("");
		redirect.addFlashAttribute("message", "Blank form picture removed.");
		return "redirect:/settings/form";
	}

	/** The scan of the blank form, shown behind the editor only; it is never printed. */
	@GetMapping("/background")
	public ResponseEntity<byte[]> backgroundImage() {
		return forms.background()
				.filter(url -> url.startsWith("data:") && url.contains(";base64,"))
				.map(url -> ResponseEntity.ok()
						.cacheControl(CacheControl.noCache())
						.contentType(MediaType.parseMediaType(url.substring(5, url.indexOf(';'))))
						.body(Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1))))
				.orElse(ResponseEntity.notFound().build());
	}

	/** The layout from the editor's fields; anything missing or unreadable keeps its current value. */
	static FormLayout read(Map<String, String> params, FormLayout current) {
		Map<FormField, Placement> fields = new EnumMap<>(FormField.class);
		for (FormField f : FormField.values()) {
			Placement p = current.placement(f);
			String key = f.name();
			fields.put(f, new Placement(
					params.containsKey(key + ".x") ? params.containsKey(key + ".on") : p.on(),
					number(params.get(key + ".x"), p.x()),
					number(params.get(key + ".y"), p.y()),
					number(params.get(key + ".width"), p.width()),
					number(params.get(key + ".size"), p.fontSize())));
		}
		boolean preprinted = params.containsKey("mode") ? "preprinted".equals(params.get("mode")) : current.preprinted();
		return new FormLayout(preprinted, number(params.get("shiftX"), current.shiftX()),
				number(params.get("shiftY"), current.shiftY()), fields);
	}

	/** Reads "12.5" or "12,5"; blank or unreadable input keeps the old value. */
	static double number(String text, double fallback) {
		if (text == null || text.isBlank()) {
			return fallback;
		}
		try {
			double v = Double.parseDouble(text.strip().replace(',', '.'));
			return Double.isFinite(v) ? v : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
