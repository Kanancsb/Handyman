package com.example.demo.youtube;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class YoutubePageControllerTest {

	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new YoutubePageController())
			.build();

	@Test
	void homeRedirectsToYoutubePage() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/youtube"));
	}

	@Test
	void youtubePageForwardsToStaticPage() throws Exception {
		mockMvc.perform(get("/youtube"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/youtube.html"));
	}
}
