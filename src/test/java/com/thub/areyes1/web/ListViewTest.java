package com.thub.areyes1.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.thub.areyes1.clearance.ClearanceQuery;
import com.thub.areyes1.clearance.ClearanceSort;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.clearance.PageResult;

class ListViewTest {

	@Test
	void defaultViewHasCleanLinks() {
		ListView view = new ListView(ClearanceQuery.all());

		assertThat(view.pageLink(0)).isEqualTo("/clearances");
		assertThat(view.pageLink(2)).isEqualTo("/clearances?page=3");
		assertThat(view.ariaSort("issued")).isEqualTo("descending");
		assertThat(view.ariaSort("name")).isEqualTo("none");
	}

	@Test
	void sortLinksToggleDirectionAndKeepFilters() {
		ListView view = new ListView(new ClearanceQuery("rizal st", ClearanceType.NEW, ClearanceSort.AMOUNT, true, 3, 25));

		assertThat(view.sortLink("amount")).isEqualTo("/clearances?q=rizal%20st&type=NEW&sort=amount&dir=asc");
		assertThat(view.sortLink("name")).isEqualTo("/clearances?q=rizal%20st&type=NEW&sort=name&dir=asc");
		assertThat(view.sortLink("control")).isEqualTo("/clearances?q=rizal%20st&type=NEW&sort=control&dir=desc");
		assertThat(view.pageLink(4)).isEqualTo("/clearances?q=rizal%20st&type=NEW&sort=amount&dir=desc&page=5");
	}

	@Test
	void pageMath() {
		assertThat(new PageResult<>(java.util.List.of(), 0, 25, 0).totalPages()).isEqualTo(1);
		assertThat(new PageResult<>(java.util.List.of(), 0, 25, 0).firstItem()).isZero();
		assertThat(new PageResult<>(java.util.List.of(1, 2), 1, 25, 27).firstItem()).isEqualTo(26);
		assertThat(new PageResult<>(java.util.List.of(1, 2), 1, 25, 27).lastItem()).isEqualTo(27);
		assertThat(new PageResult<>(java.util.List.of(1, 2), 1, 25, 27).hasNext()).isFalse();
	}
}
