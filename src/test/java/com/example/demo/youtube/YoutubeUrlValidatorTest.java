package com.example.demo.youtube;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class YoutubeUrlValidatorTest {

	private final YoutubeUrlValidator validator = new YoutubeUrlValidator();

	@Test
	void acceptsYoutubeVideoAndPlaylistUrls() {
		assertTrue(validator.isSupported("https://www.youtube.com/watch?v=abc123"));
		assertTrue(validator.isSupported("https://www.youtube.com/playlist?list=PL123"));
		assertTrue(validator.isSupported("https://youtu.be/abc123"));
		assertTrue(validator.isSupported("https://music.youtube.com/watch?v=abc123&list=PL123"));
	}

	@Test
	void rejectsUnsupportedOrMalformedUrls() {
		assertFalse(validator.isSupported(""));
		assertFalse(validator.isSupported("not a url"));
		assertFalse(validator.isSupported("ftp://www.youtube.com/watch?v=abc123"));
		assertFalse(validator.isSupported("https://example.com/watch?v=abc123"));
	}
}
