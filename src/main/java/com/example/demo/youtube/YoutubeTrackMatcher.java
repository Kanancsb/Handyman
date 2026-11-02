package com.example.demo.youtube;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.demo.spotify.SpotifyProperties;
import com.example.demo.spotify.SpotifyTrack;
import com.example.demo.spotify.SpotifyTrackMatch;

@Service
public class YoutubeTrackMatcher {

	private static final String PRINT_TEMPLATE = "%(id)s\t%(title)s\t%(duration)s\t%(channel)s";

	private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{6,20}");

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

	private static final Set<String> VERSION_WORDS = Set.of("cover", "karaoke", "live", "remix", "sped up", "slowed");

	private final YoutubeDownloaderProperties youtubeProperties;

	private final SpotifyProperties spotifyProperties;

	private final ProcessExecutor processExecutor;

	public YoutubeTrackMatcher(YoutubeDownloaderProperties youtubeProperties, SpotifyProperties spotifyProperties,
			ProcessExecutor processExecutor) {
		this.youtubeProperties = youtubeProperties;
		this.spotifyProperties = spotifyProperties;
		this.processExecutor = processExecutor;
	}

	public List<SpotifyTrackMatch> match(List<SpotifyTrack> tracks) {
		if (tracks.isEmpty()) {
			return List.of();
		}

		Path workspace = null;
		try {
			workspace = Files.createTempDirectory("handyman-youtube-search-");
			Path searchWorkspace = workspace;
			int concurrency = Math.max(1, Math.min(spotifyProperties.getMatchingConcurrency(), 8));
			try (var executor = Executors.newFixedThreadPool(concurrency)) {
				List<Future<SpotifyTrackMatch>> futures = tracks.stream()
						.map(track -> executor.submit(() -> new SpotifyTrackMatch(track, search(track, searchWorkspace))))
						.toList();
				List<SpotifyTrackMatch> matches = new ArrayList<>(futures.size());
				for (Future<SpotifyTrackMatch> future : futures) {
					matches.add(future.get());
				}
				return List.copyOf(matches);
			}
		}
		catch (IOException ex) {
			throw new DownloadException("Could not create a workspace for YouTube matching.", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new DownloadException("YouTube matching was interrupted.", ex);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new DownloadException("Could not match the Spotify playlist on YouTube.", cause);
		}
		finally {
			deleteWorkspace(workspace);
		}
	}

	List<YoutubeSearchCandidate> search(SpotifyTrack track, Path workspace) {
		String output = processExecutor.executeForOutput(
				commandFor(track), workspace, spotifyProperties.getSearchTimeout());
		return output.lines()
				.map(line -> candidate(line, track))
				.filter(java.util.Objects::nonNull)
				.sorted(Comparator.comparingInt(YoutubeSearchCandidate::score).reversed())
				.toList();
	}

	List<String> commandFor(SpotifyTrack track) {
		int resultCount = Math.max(1, Math.min(spotifyProperties.getYoutubeResults(), 10));
		List<String> command = new ArrayList<>();
		command.add(youtubeProperties.getBinary());
		command.add("--no-warnings");
		command.add("--simulate");
		command.add("--ignore-errors");
		command.add("--flat-playlist");
		command.add("--playlist-end");
		command.add(Integer.toString(resultCount));
		command.add("--print");
		command.add(PRINT_TEMPLATE);
		if (StringUtils.hasText(youtubeProperties.getJsRuntime())) {
			command.add("--js-runtimes");
			command.add(youtubeProperties.getJsRuntime());
		}
		command.add("ytsearch%d:%s - %s official audio".formatted(
				resultCount, track.artistText(), track.name()));
		return List.copyOf(command);
	}

	private YoutubeSearchCandidate candidate(String line, SpotifyTrack track) {
		String[] fields = line.split("\\t", 4);
		if (fields.length < 2 || !VIDEO_ID.matcher(fields[0]).matches()) {
			return null;
		}

		long duration = parseDuration(fields.length > 2 ? fields[2] : "");
		String channel = fields.length > 3 && !"NA".equals(fields[3]) ? fields[3] : "Unknown channel";
		return new YoutubeSearchCandidate(
				"https://www.youtube.com/watch?v=" + fields[0],
				fields[1],
				channel,
				duration,
				score(track, fields[1], channel, duration));
	}

	private int score(SpotifyTrack track, String title, String channel, long durationSeconds) {
		String normalizedTitle = normalize(title);
		String normalizedTrack = normalize(track.name());
		int score = normalizedTitle.contains(normalizedTrack) ? 50 : 0;
		for (String artist : track.artists()) {
			String normalizedArtist = normalize(artist);
			if (normalizedTitle.contains(normalizedArtist) || normalize(channel).contains(normalizedArtist)) {
				score += 20;
			}
		}

		if (durationSeconds > 0 && track.durationMs() > 0) {
			long difference = Math.abs(durationSeconds - track.durationMs() / 1000);
			score += difference <= 2 ? 30 : difference <= 5 ? 20 : difference <= 10 ? 10 : 0;
		}

		for (String word : VERSION_WORDS) {
			if (normalizedTitle.contains(word) && !normalizedTrack.contains(word)) {
				score -= 25;
			}
		}
		return score;
	}

	private String normalize(String value) {
		String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
		return NON_ALPHANUMERIC.matcher(decomposed).replaceAll(" ").trim();
	}

	private long parseDuration(String value) {
		try {
			return Math.round(Double.parseDouble(value));
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private void deleteWorkspace(Path workspace) {
		if (workspace == null) {
			return;
		}
		try (Stream<Path> paths = Files.walk(workspace)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					path.toFile().deleteOnExit();
				}
			});
		}
		catch (IOException ex) {
			workspace.toFile().deleteOnExit();
		}
	}
}
