package com.thub.areyes1.printing;

/** A printing problem, with a message that can be shown to the person at the counter. */
public class PrintFailure extends RuntimeException {

	public PrintFailure(String message, Throwable cause) {
		super(message, cause);
	}
}
