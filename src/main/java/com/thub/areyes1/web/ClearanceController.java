package com.thub.areyes1.web;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.thub.areyes1.clearance.Clearance;
import com.thub.areyes1.clearance.ClearanceQuery;
import com.thub.areyes1.clearance.ClearanceRepository;
import com.thub.areyes1.clearance.ClearanceSort;
import com.thub.areyes1.clearance.ClearanceType;
import com.thub.areyes1.print.ClearancePrinter;
import com.thub.areyes1.settings.SettingsRepository;

@Controller
@RequestMapping("/clearances")
public class ClearanceController {

	private final ClearanceRepository clearances;
	private final SettingsRepository settings;
	private final ClearancePrinter printer;
	private final Clock clock;

	public ClearanceController(ClearanceRepository clearances, SettingsRepository settings, ClearancePrinter printer,
			Clock clock) {
		this.clearances = clearances;
		this.settings = settings;
		this.printer = printer;
		this.clock = clock;
	}

	@GetMapping
	public String list(@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "type", required = false) String type,
			@RequestParam(name = "sort", required = false) String sort,
			@RequestParam(name = "dir", required = false) String dir,
			@RequestParam(name = "page", defaultValue = "1") int page, Model model) {
		ClearanceSort sortBy = ClearanceSort.from(sort);
		boolean descending = dir == null ? sortBy != ClearanceSort.NAME : !"asc".equalsIgnoreCase(dir);
		ClearanceQuery query = new ClearanceQuery(q, parseType(type), sortBy, descending, page - 1,
				ClearanceQuery.DEFAULT_PAGE_SIZE);
		model.addAttribute("result", clearances.search(query));
		model.addAttribute("query", query);
		model.addAttribute("view", new ListView(query));
		return "clearances/list";
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("form", ClearanceForm.newFor(LocalDate.now(clock)));
		model.addAttribute("id", null);
		return "clearances/form";
	}

	@PostMapping
	public String create(@Valid @ModelAttribute("form") ClearanceForm form, BindingResult errors, Model model,
			RedirectAttributes redirect) {
		checkControlNumber(form, null, errors);
		if (errors.hasErrors()) {
			model.addAttribute("id", null);
			return "clearances/form";
		}
		Clearance saved = clearances.insert(form.toClearance(null));
		redirect.addFlashAttribute("message", "Clearance for " + saved.businessName() + " saved.");
		return "redirect:/clearances/" + saved.id();
	}

	@GetMapping("/{id}")
	public String show(@PathVariable long id, Model model) {
		model.addAttribute("c", load(id));
		return "clearances/detail";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable long id, Model model) {
		model.addAttribute("form", ClearanceForm.from(load(id)));
		model.addAttribute("id", id);
		return "clearances/form";
	}

	@PostMapping("/{id}")
	public String update(@PathVariable long id, @Valid @ModelAttribute("form") ClearanceForm form,
			BindingResult errors, Model model, RedirectAttributes redirect) {
		load(id);
		checkControlNumber(form, id, errors);
		if (errors.hasErrors()) {
			model.addAttribute("id", id);
			return "clearances/form";
		}
		if (!clearances.update(form.toClearance(id))) {
			throw notFound(id);
		}
		redirect.addFlashAttribute("message", "Changes saved.");
		return "redirect:/clearances/" + id;
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable long id, RedirectAttributes redirect) {
		Clearance c = load(id);
		clearances.delete(id);
		redirect.addFlashAttribute("message", "Clearance for " + c.displayName() + " deleted.");
		return "redirect:/clearances";
	}

	@GetMapping("/{id}/clearance.pdf")
	public ResponseEntity<byte[]> print(@PathVariable long id) {
		Clearance c = load(id);
		byte[] pdf = printer.print(c, settings.load());
		String filename = "clearance-" + (c.controlNumber() == null ? c.id() : c.controlNumber()) + ".pdf";
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header("Content-Disposition", ContentDisposition.inline().filename(filename).build().toString())
				.body(pdf);
	}

	private void checkControlNumber(ClearanceForm form, Long id, BindingResult errors) {
		// 0 means "not assigned": the desktop app saved it on most records, so it may repeat.
		if (form.getControlNumber() != null && form.getControlNumber() > 0 && !errors.hasFieldErrors("controlNumber")) {
			clearances.controlNumberUsedBy(form.getControlNumber(), id).ifPresent(name -> errors.rejectValue(
					"controlNumber", "duplicate",
					"Already used by " + (name.isBlank() ? "another clearance" : name)));
		}
	}

	private Clearance load(long id) {
		return clearances.findById(id).orElseThrow(() -> notFound(id));
	}

	private static ResponseStatusException notFound(long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "No clearance with id " + id);
	}

	private static ClearanceType parseType(String type) {
		if (type == null || type.isBlank()) {
			return null;
		}
		try {
			return ClearanceType.valueOf(type.toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
