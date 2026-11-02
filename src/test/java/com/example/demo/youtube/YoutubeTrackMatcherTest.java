package com.example.demo.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.spotify.SpotifyProperties;
import com.example.demo.spotify.SpotifyTrack;

class YoutubeTrackMatcherTest {

	@Test
	void searchesWithTrackMetadataAndRanksLikelyOfficialResultFirst() {
		SpotifyTrack track = new SpotifyTrack(
				"spotify-id", "Test Song", List.of("Test Artist"), 180000, "https://open.spotify.com/track/id");
		YoutubeDownloaderProperties youtubeProperties = new YoutubeDownloaderProperties();
		youtubeProperties.setBinary("yt-dlp-test");
		SpotifyProperties spotifyProperties = new SpotifyProperties();
		spotifyProperties.setYoutubeResults(3);
		CapturingSearchExecutor executor = new CapturingSearchExecutor("""
				cover123456\tTest Song cover\t240\tOther Channel
				official123\tTest Artist - Test Song (Official Audio)\t180\tTest Artist
				""");
		YoutubeTrackMatcher matcher = new YoutubeTrackMatcher(youtubeProperties, spotifyProperties, executor);

		List<YoutubeSearchCandidate> candidates = matcher.search(track, Path.of("."));

		assertEquals("https://www.youtube.com/watch?v=official123", candidates.getFirst().url());
		assertTrue(executor.command.getLast().contains("Test Artist - Test Song official audio"));
		assertTrue(executor.command.contains("--flat-playlist"));
	}

	private static class CapturingSearchExecutor implements ProcessExecutor {

		private final String output;

		private List<String> command;

		CapturingSearchExecutor(String output) {
			this.output = output;
		}

		@Override
		public void execute(List<String> command, Path workingDirectory, Duration timeout) {
		}

		@Override
		public String executeForOutput(List<String> command, Path workingDirectory, Duration timeout) {
			this.command = List.copyOf(command);
			return output;
		}
	}
}
