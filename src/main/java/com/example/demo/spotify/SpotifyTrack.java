package com.example.demo.spotify;

import java.util.List;

public record SpotifyTrack(String id, String name, List<String> artists, long durationMs, String spotifyUrl) {

	public SpotifyTrack {
		artists = List.copyOf(artists);
	}

	public String artistText() {
		return String.join(", ", artists);
	}
}
