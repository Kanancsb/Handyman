package com.example.demo.youtube;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class YoutubeStaticAssetsTest {

	@Test
	void pageContainsUrlInputAndAllFormatCheckboxes() throws IOException {
		String html = Files.readString(Path.of("src/main/resources/static/youtube.html"));

		assertTrue(html.contains("id=\"youtube-url\""));
		assertTrue(html.contains("id=\"download-queue\""));
		assertTrue(html.contains("value=\"mp3\""));
		assertTrue(html.contains("value=\"wav\""));
		assertTrue(html.contains("value=\"video\""));
		assertFalse(html.contains("signal-panel"));
	}

	@Test
	void scriptsTrackDownloadsWithTimerRows() throws IOException {
		String script = Files.readString(Path.of("src/main/resources/static/js/youtube.js"));

		assertTrue(script.contains("formats.map"));
		assertTrue(script.contains("fetch("));
		assertTrue(script.contains("/api/youtube/download"));
		assertTrue(script.contains("createDownloadTimer"));
		assertTrue(script.contains("formatDownloadTime"));
	}

	@Test
	void stylesIncludeResponsiveLayoutRules() throws IOException {
		String styles = Files.readString(Path.of("src/main/resources/static/css/youtube.css"));

		assertTrue(styles.contains("@media (max-width: 860px)"));
		assertTrue(styles.contains("@media (max-width: 620px)"));
		assertTrue(styles.contains(".download-timer"));
		assertTrue(styles.contains("@keyframes wait-pulse"));
		assertFalse(styles.contains(".signal-panel"));
	}
}
