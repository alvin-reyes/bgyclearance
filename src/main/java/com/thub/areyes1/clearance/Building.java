package com.thub.areyes1.clearance;

/** Whether the business owns or rents the building it operates in. */
public enum Building {
	OWNED("Owned"),
	RENTED("Rented");

	private final String label;

	Building(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
