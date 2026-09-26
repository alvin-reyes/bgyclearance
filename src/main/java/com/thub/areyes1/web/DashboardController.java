package com.thub.areyes1.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.thub.areyes1.clearance.ClearanceRepository;

@Controller
public class DashboardController {

	private final ClearanceRepository clearances;

	public DashboardController(ClearanceRepository clearances) {
		this.clearances = clearances;
	}

	@GetMapping("/")
	public String dashboard(Model model) {
		model.addAttribute("stats", clearances.stats());
		model.addAttribute("recent", clearances.recent(6));
		return "dashboard";
	}
}
