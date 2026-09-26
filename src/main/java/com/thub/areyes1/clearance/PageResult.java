package com.thub.areyes1.clearance;

import java.util.List;

/**
 * One page of results.
 *
 * @param items the items on this page
 * @param page  zero-based page number
 * @param size  page size
 * @param total total matching items across all pages
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

	public int totalPages() {
		return (int) Math.max(1, (total + size - 1) / size);
	}

	public boolean hasPrevious() {
		return page > 0;
	}

	public boolean hasNext() {
		return page + 1 < totalPages();
	}

	/** 1-based index of the first item on this page, for "Showing 26–50 of 120". */
	public long firstItem() {
		return total == 0 ? 0 : (long) page * size + 1;
	}

	public long lastItem() {
		return (long) page * size + items.size();
	}
}
