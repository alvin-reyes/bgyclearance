package com.thub.areyes1.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmountEditorTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"250        | 250",
			"250.5      | 250.5",
			"1,250.50   | 1250.50",
			"₱1,250.50  | 1250.50",
			"₱ 300      | 300",
			"PHP 12,000 | 12000",
			"  75.00    | 75.00" })
	void acceptsAmountsAsPeopleTypeThem(String typed, String expected) {
		AmountEditor editor = new AmountEditor();
		editor.setAsText(typed);
		assertThat((BigDecimal) editor.getValue()).isEqualByComparingTo(expected);
	}

	@Test
	void blankIsEmptyAndGarbageIsRejected() {
		AmountEditor editor = new AmountEditor();
		editor.setAsText("  ");
		assertThat(editor.getValue()).isNull();
		assertThatThrownBy(() -> editor.setAsText("abc")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void showsAtLeastTwoDecimals() {
		AmountEditor editor = new AmountEditor();
		editor.setValue(new BigDecimal("250"));
		assertThat(editor.getAsText()).isEqualTo("250.00");
	}
}
