package com.thub.areyes1.web;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;

/**
 * Reads money the way people type it: "1,250.50", "₱ 1250.5" and "PHP 300" are all accepted.
 * Anything else is rejected, which shows the form's "Enter an amount" message.
 */
class AmountEditor extends PropertyEditorSupport {

	@Override
	public void setAsText(String text) {
		if (text == null || text.isBlank()) {
			setValue(null);
			return;
		}
		String cleaned = text.strip().replaceFirst("(?i)^(₱|php|p)\\s*", "").replace(",", "").replace(" ", "");
		try {
			setValue(new BigDecimal(cleaned));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Not an amount: " + text, e);
		}
	}

	@Override
	public String getAsText() {
		BigDecimal value = (BigDecimal) getValue();
		return value == null ? "" : value.setScale(Math.max(2, value.scale())).toPlainString();
	}
}
