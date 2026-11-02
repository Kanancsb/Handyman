package com.example.demo.spotify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpotifyPlaylistUrlParserTest {

	private final SpotifyPlaylistUrlParser parser = new SpotifyPlaylistUrlParser();

	@Test
	void extractsPlaylistIdFromSpotifyUrl() {
		assertEquals("37i9dQZF1DXcBWIGoYBM5M",
				parser.playlistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"));
		assertEquals("37i9dQZF1DXcBWIGoYBM5M",
				parser.playlistId("https://open.spotify.com/intl-pt/playlist/37i9dQZF1DXcBWIGoYBM5M"));
	}

	@Test
	void rejectsNonSpotifyAndNonPlaylistUrls() {
		assertThrows(IllegalArgumentException.class,
				() -> parser.playlistId("https://example.com/playlist/37i9dQZF1DXcBWIGoYBM5M"));
		assertThrows(IllegalArgumentException.class,
				() -> parser.playlistId("https://open.spotify.com/track/37i9dQZF1DXcBWIGoYBM5M"));
	}
}
