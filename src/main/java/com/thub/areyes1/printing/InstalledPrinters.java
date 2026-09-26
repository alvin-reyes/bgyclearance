package com.thub.areyes1.printing;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.PrinterInfo;
import javax.print.attribute.standard.PrinterLocation;
import javax.print.attribute.standard.PrinterMakeAndModel;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Printers already set up on this computer (Windows printers, or CUPS queues on
 * Linux and macOS), including network printers someone has added there. Printing
 * goes through the operating system's driver, so any printer that works from
 * other programs works here.
 */
@Component
public class InstalledPrinters {

	static final String PREFIX = "installed:";

	private final boolean enabled;

	public InstalledPrinters(@Value("${bgy.printers.installed:true}") boolean enabled) {
		this.enabled = enabled;
	}

	public List<Printer> list() {
		if (!enabled) {
			return List.of();
		}
		return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
				.map(InstalledPrinters::toPrinter)
				.toList();
	}

	public void print(String name, byte[] pdf, String jobName) {
		PrintService service = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
				.filter(s -> s.getName().equals(name))
				.findFirst()
				.orElseThrow(() -> new PrintFailure("The printer \"" + name + "\" is no longer installed on this computer.", null));
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			PrinterJob job = PrinterJob.getPrinterJob();
			job.setJobName(jobName);
			job.setPrintService(service);
			job.setPageable(new PDFPageable(doc));
			job.print();
		} catch (PrinterException | IOException e) {
			throw new PrintFailure("Could not print to \"" + name + "\": " + explain(e), e);
		}
	}

	/** The most useful message in the cause chain; Java's printing often wraps the real one. */
	static String explain(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String message = root.getMessage();
		if (message != null && message.contains("lpr")) {
			// Linux: Java hands jobs to CUPS through the lpr command (package cups-bsd or cups-client).
			return "this computer's print system is missing the \"lpr\" command. Install CUPS's lpr tools "
					+ "(for example the cups-bsd package), or add the printer by its IP address instead.";
		}
		return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
	}

	private static Printer toPrinter(PrintService s) {
		String detail = firstNonBlank(text(s.getAttribute(PrinterMakeAndModel.class)),
				text(s.getAttribute(PrinterLocation.class)), text(s.getAttribute(PrinterInfo.class)));
		// Rendered to the page by PDFBox and the printer's driver, so PDF support doesn't matter.
		return new Printer(PREFIX + s.getName(), s.getName(), detail, Printer.Source.INSTALLED, null, true);
	}

	private static String text(Object attribute) {
		return attribute == null ? null : attribute.toString();
	}

	static String firstNonBlank(String... values) {
		for (String v : values) {
			if (v != null && !v.isBlank()) {
				return v.strip();
			}
		}
		return "";
	}
}
