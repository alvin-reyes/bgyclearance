package com.thub.areyes1.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.thub.areyes1.settings.SettingsRepository;

/** Values every page's layout needs: the barangay details and the current section for the nav. */
@ControllerAdvice
public class GlobalModel {

	private final SettingsRepository settings;

	public GlobalModel(SettingsRepository settings) {
		this.settings = settings;
	}

	@ModelAttribute
	void addGlobals(Model model, HttpServletRequest request) {
		model.addAttribute("barangay", settings.load());
		String path = request.getRequestURI();
		model.addAttribute("section", path.startsWith("/clearances") ? "clearances"
				: path.startsWith("/reports") ? "reports"
				: path.startsWith("/settings") ? "settings" : "dashboard");
	}
}
