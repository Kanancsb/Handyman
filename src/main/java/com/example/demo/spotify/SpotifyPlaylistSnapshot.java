package com.example.demo.spotify;

import java.util.List;

public record SpotifyPlaylistSnapshot(List<SpotifyTrack> tracks, int totalTracks, boolean truncated) {

	public SpotifyPlaylistSnapshot {
		tracks = List.copyOf(tracks);
	}
}
