package com.thub.areyes1.web;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.thub.areyes1.print.ClearancePrinter;
import com.thub.areyes1.printing.NetworkPrinterDiscovery;
import com.thub.areyes1.printing.PrintFailure;
import com.thub.areyes1.printing.Printer;
import com.thub.areyes1.printing.Printers;
import com.thub.areyes1.settings.BarangaySettings;
import com.thub.areyes1.settings.SettingsRepository;

@Controller
@RequestMapping("/settings")
public class SettingsController {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");
	private static final Duration SCAN_LIMIT = Duration.ofSeconds(20);

	private final SettingsRepository settings;
	private final Printers printers;
	private final NetworkPrinterDiscovery discovery;
	private final ClearancePrinter printer;

	public SettingsController(SettingsRepository settings, Printers printers, NetworkPrinterDiscovery discovery,
			ClearancePrinter printer) {
		this.settings = settings;
		this.printers = printers;
		this.discovery = discovery;
		this.printer = printer;
	}

	@GetMapping
	public String form(Model model) {
		model.addAttribute("form", settings.load());
		return view(model);
	}

	@PostMapping
	public String save(@Valid @ModelAttribute("form") BarangaySettings form, BindingResult errors, Model model,
			RedirectAttributes redirect) {
		if (errors.hasErrors()) {
			return view(model);
		}
		settings.save(form);
		redirect.addFlashAttribute("message", "Barangay details saved. They will appear on printed clearances.");
		return "redirect:/settings";
	}

	@PostMapping("/printers/default")
	public String chooseDefault(@RequestParam(name = "defaultPrinter", defaultValue = "") String id,
			RedirectAttributes redirect) {
		try {
			printers.setDefault(id);
			redirect.addFlashAttribute("message", id.isBlank()
					? "No default printer. The Print button will open the PDF instead."
					: "Clearances will print to " + printers.find(id).map(Printer::name).orElse("that printer") + ".");
		} catch (PrintFailure e) {
			redirect.addFlashAttribute("printError", e.getMessage());
		}
		return "redirect:/settings#printers";
	}

	@PostMapping("/printers/scan")
	public String scan(RedirectAttributes redirect) {
		try {
			NetworkPrinterDiscovery.Scan scan = discovery.scanAsync().get(SCAN_LIMIT.toSeconds(), TimeUnit.SECONDS);
			int n = scan.printers().size();
			redirect.addFlashAttribute("message", n == 0 ? "Scan finished. No printers answered on the network."
					: "Scan finished. Found " + n + (n == 1 ? " printer" : " printers") + " on the network.");
		} catch (TimeoutException e) {
			redirect.addFlashAttribute("message", "Still scanning. Refresh this page in a few seconds.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			redirect.addFlashAttribute("printError", "The scan failed: " + e.getCause().getMessage());
		}
		return "redirect:/settings#printers";
	}

	@PostMapping("/printers/add")
	public String add(@RequestParam(name = "address", defaultValue = "") String address, RedirectAttributes redirect) {
		try {
			Printer added = printers.addByAddress(address);
			redirect.addFlashAttribute("message", "Added " + added.name() + " (" + added.host() + ")."
					+ (added.canPrint() ? "" : " It doesn't accept PDF files, so add it to this computer's printers to use it."));
		} catch (PrintFailure e) {
			redirect.addFlashAttribute("printError", e.getMessage());
			redirect.addFlashAttribute("address", address);
		}
		return "redirect:/settings#printers";
	}

	@PostMapping("/printers/remove")
	public String remove(@RequestParam("printer") String id, RedirectAttributes redirect) {
		String name = printers.find(id).map(Printer::name).orElse("The printer");
		printers.remove(id);
		redirect.addFlashAttribute("message", name + " was removed.");
		return "redirect:/settings#printers";
	}

	@PostMapping("/printers/test")
	public String test(@RequestParam("printer") String id, RedirectAttributes redirect) {
		try {
			String name = printers.find(id).map(Printer::name).orElse("printer");
			String result = printers.print(id, printer.testPage(settings.load(), name), "Printer test page");
			redirect.addFlashAttribute("message", "Test page: " + result);
		} catch (PrintFailure e) {
			redirect.addFlashAttribute("printError", e.getMessage());
		}
		return "redirect:/settings#printers";
	}

	private String view(Model model) {
		NetworkPrinterDiscovery.Scan scan = discovery.lastScan();
		model.addAttribute("printers", printers.list());
		model.addAttribute("defaultPrinter", printers.defaultPrinterId().orElse(""));
		model.addAttribute("discoveryEnabled", discovery.enabled());
		model.addAttribute("scanning", scan.running());
		model.addAttribute("scannedAt", scan.finishedAt() == null ? null
				: TIME.format(scan.finishedAt().atZone(ZoneId.systemDefault())));
		return "settings";
	}
}
