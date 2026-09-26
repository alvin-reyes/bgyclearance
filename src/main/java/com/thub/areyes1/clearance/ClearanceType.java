package com.thub.areyes1.clearance;

/** Whether a clearance is issued to a new business or renews an existing one. */
public enum ClearanceType {
	NEW("New"),
	RENEWAL("Renewal");

	private final String label;

	ClearanceType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
