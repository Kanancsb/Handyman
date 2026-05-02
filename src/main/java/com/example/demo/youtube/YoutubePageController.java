package com.example.demo.youtube;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class YoutubePageController {

	@GetMapping("/")
	public String home() {
		return "redirect:/youtube";
	}

	@GetMapping("/youtube")
	public String youtube() {
		return "forward:/youtube.html";
	}
}
