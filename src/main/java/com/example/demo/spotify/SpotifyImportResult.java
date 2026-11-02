package com.example.demo.spotify;

import java.util.List;

public record SpotifyImportResult(int totalTracks, boolean truncated, List<SpotifyTrackMatch> matches) {

	public SpotifyImportResult {
		matches = List.copyOf(matches);
	}
}
