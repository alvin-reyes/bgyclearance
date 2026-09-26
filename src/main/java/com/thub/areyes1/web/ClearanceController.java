/**
 * Class File Name: ClearanceController.java
 * Description: Web pages for listing, registering, editing and printing clearances.
 */

package com.thub.areyes1.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.thub.areyes1.exception.BarangayClearanceServiceException;
import com.thub.areyes1.exception.BarangayClearanceValidationException;
import com.thub.areyes1.obj.BarangayClearance;
import com.thub.areyes1.service.BarangayClearanceService;
import com.thub.areyes1.util.ReportUtil;

/**
 * The browser equivalent of BgyClearanceFrame and BgyClearanceRegistrationDialog.
 */
@Controller
public class ClearanceController {

	private final BarangayClearanceService service;

	public ClearanceController(BarangayClearanceService service) {
		this.service = service;
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/clearances";
	}

	@GetMapping("/clearances")
	public String list(@RequestParam(value = "q", required = false) String query, Model model)
			throws BarangayClearanceServiceException {
		List<BarangayClearance> all = service.getAllBarangayClearance();
		if (all == null) {
			throw new BarangayClearanceServiceException();
		}
		List<BarangayClearance> shown = new ArrayList<BarangayClearance>();
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		for (BarangayClearance c : all) {
			if (q.isEmpty() || contains(c.getBusinessName(), q) || contains(c.getAddress(), q)
					|| q.equals(String.valueOf(c.getControlNumber()))) {
				shown.add(c);
			}
		}
		// Newest first, matching the desktop table.
		Collections.sort(shown, new Comparator<BarangayClearance>() {
			public int compare(BarangayClearance a, BarangayClearance b) {
				return Integer.compare(b.getId(), a.getId());
			}
		});
		model.addAttribute("clearances", shown);
		model.addAttribute("total", all.size());
		model.addAttribute("q", query == null ? "" : query.trim());
		return "clearances/list";
	}

	@GetMapping("/clearances/new")
	public String newForm(Model model) {
		model.addAttribute("form", new ClearanceForm());
		return "clearances/form";
	}

	@PostMapping("/clearances")
	public String create(@Valid @ModelAttribute("form") ClearanceForm form, BindingResult errors,
			RedirectAttributes redirect) throws Exception {
		form.setId(0);
		if (errors.hasErrors()) {
			return "clearances/form";
		}
		BarangayClearance saved = service.saveClearance(form.toClearance());
		redirect.addFlashAttribute("message", "Clearance for " + saved.getBusinessName() + " saved.");
		return "redirect:/clearances/" + saved.getId();
	}

	@GetMapping("/clearances/{id}")
	public String show(@PathVariable int id, Model model) throws BarangayClearanceServiceException {
		BarangayClearance c = load(id);
		List<String> kinds = new ArrayList<String>();
		if (c.isCorporation()) {
			kinds.add("Corporation");
		}
		if (c.isSingleProprietorship()) {
			kinds.add("Single proprietorship");
		}
		if (c.isParntership()) {
			kinds.add("Partnership");
		}
		if (c.isOthers()) {
			kinds.add("Others");
		}
		model.addAttribute("c", c);
		model.addAttribute("ownershipKinds", String.join(", ", kinds));
		return "clearances/detail";
	}

	@GetMapping("/clearances/{id}/edit")
	public String editForm(@PathVariable int id, Model model) throws BarangayClearanceServiceException {
		model.addAttribute("form", ClearanceForm.from(load(id)));
		return "clearances/form";
	}

	@PostMapping("/clearances/{id}")
	public String update(@PathVariable int id, @Valid @ModelAttribute("form") ClearanceForm form,
			BindingResult errors, RedirectAttributes redirect) throws Exception {
		load(id);
		form.setId(id);
		if (errors.hasErrors()) {
			return "clearances/form";
		}
		service.saveClearance(form.toClearance());
		redirect.addFlashAttribute("message", "Changes saved.");
		return "redirect:/clearances/" + id;
	}

	@PostMapping("/clearances/{id}/delete")
	public String delete(@PathVariable int id, RedirectAttributes redirect) throws BarangayClearanceServiceException {
		BarangayClearance c = load(id);
		if (!service.removeClearance(c)) {
			throw new BarangayClearanceServiceException();
		}
		redirect.addFlashAttribute("message", "Clearance for " + c.getBusinessName() + " deleted.");
		return "redirect:/clearances";
	}

	@GetMapping("/clearances/{id}/report.pdf")
	public ResponseEntity<byte[]> report(@PathVariable int id)
			throws BarangayClearanceServiceException, BarangayClearanceValidationException {
		BarangayClearance c = load(id);
		byte[] pdf = ReportUtil.generatePdfReport(c);
		if (pdf == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate the report");
		}
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(ContentDisposition.inline()
				.filename("clearance-" + c.getControlNumber() + ".pdf").build());
		return new ResponseEntity<byte[]>(pdf, headers, HttpStatus.OK);
	}

	private BarangayClearance load(int id) throws BarangayClearanceServiceException {
		BarangayClearance c = service.getBarangayClearanceData(id);
		if (c == null) {
			throw new BarangayClearanceServiceException();
		}
		if (c.getId() == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No clearance with id " + id);
		}
		return c;
	}

	private static boolean contains(String value, String q) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(q);
	}
}
