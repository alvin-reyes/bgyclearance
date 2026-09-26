package com.thub.areyes1.printing;

import java.net.URI;

/**
 * A printer the app can send clearances to.
 *
 * @param id         stable identifier stored as the default printer
 * @param name       name shown to people
 * @param detail     make and model or location, if known
 * @param source     how the printer was found
 * @param uri        IPP address for network printers; null for installed printers
 * @param acceptsPdf true if it takes PDF directly, false if not, null if unknown
 */
public record Printer(String id, String name, String detail, Source source, URI uri, Boolean acceptsPdf) {

	public enum Source {
		INSTALLED("Installed on this computer"),
		DISCOVERED("Found on the network"),
		ADDED("Added by address");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** False only when the printer is known not to take PDF files. */
	public boolean canPrint() {
		return acceptsPdf == null || acceptsPdf;
	}

	/** The host (IP address) of a network printer, or empty for installed printers. */
	public String host() {
		return uri == null ? "" : uri.getHost();
	}
}
