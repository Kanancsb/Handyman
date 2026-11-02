package com.example.demo.spotify;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpotifyControllerTest {

	@Test
	void reportsUnavailableWhenCredentialsAreMissing() throws Exception {
		SpotifyProperties properties = new SpotifyProperties();
		MockMvc mockMvc = MockMvcBuilders
				.standaloneSetup(new SpotifyController(properties, new FakeImportService()))
				.build();

		mockMvc.perform(get("/api/spotify/status"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"configured\":false")))
				.andExpect(content().string(containsString("\"connected\":false")));

		mockMvc.perform(get("/api/spotify/login"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void connectsWithStateAndImportsUsingSessionToken() throws Exception {
		SpotifyProperties properties = configuredProperties();
		FakeImportService importService = new FakeImportService();
		MockMvc mockMvc = MockMvcBuilders
				.standaloneSetup(new SpotifyController(properties, importService))
				.build();
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(get("/api/spotify/login").session(session))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("http://127.0.0.1:8080/api/spotify/login?originChecked=true"));

		mockMvc.perform(get("/api/spotify/login")
						.session(session)
						.param("originChecked", "true"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("https://accounts.spotify.test/authorize"));

		mockMvc.perform(get("/api/spotify/callback")
						.session(session)
						.param("code", "authorization-code")
						.param("state", importService.state))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/youtube?spotify=connected"));

		mockMvc.perform(get("/api/spotify/status").session(session))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"connected\":true")));

		mockMvc.perform(post("/api/spotify/import")
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"url":"https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"totalTracks\":1")))
				.andExpect(content().string(not(containsString("access-token"))));
	}

	private SpotifyProperties configuredProperties() {
		SpotifyProperties properties = new SpotifyProperties();
		properties.setClientId("client-id");
		properties.setClientSecret("client-secret");
		return properties;
	}

	private static class FakeImportService extends SpotifyPlaylistImportService {

		private String state;

		FakeImportService() {
			super(null, null, null, null);
		}

		@Override
		public URI authorizationUri(String state) {
			this.state = state;
			return URI.create("https://accounts.spotify.test/authorize");
		}

		@Override
		public SpotifyAccessToken authorize(String code) {
			return new SpotifyAccessToken("access-token", "refresh-token", Instant.now().plusSeconds(3600));
		}

		@Override
		public SpotifyAccessToken refreshIfNeeded(SpotifyAccessToken token) {
			return token;
		}

		@Override
		public SpotifyImportResult importPlaylist(String playlistUrl, SpotifyAccessToken token) {
			return new SpotifyImportResult(1, false, List.of());
		}
	}
}
