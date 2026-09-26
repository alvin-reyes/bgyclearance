package com.thub.areyes1.web;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.thub.areyes1.settings.BarangaySettings;
import com.thub.areyes1.settings.SettingsRepository;

@Controller
@RequestMapping("/settings")
public class SettingsController {

	private final SettingsRepository settings;

	public SettingsController(SettingsRepository settings) {
		this.settings = settings;
	}

	@GetMapping
	public String form(Model model) {
		model.addAttribute("form", settings.load());
		return "settings";
	}

	@PostMapping
	public String save(@Valid @ModelAttribute("form") BarangaySettings form, BindingResult errors,
			RedirectAttributes redirect) {
		if (errors.hasErrors()) {
			return "settings";
		}
		settings.save(form);
		redirect.addFlashAttribute("message", "Barangay details saved. They will appear on printed clearances.");
		return "redirect:/settings";
	}
}
