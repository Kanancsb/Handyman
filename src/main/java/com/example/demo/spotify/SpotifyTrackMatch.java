package com.example.demo.spotify;

import java.util.List;

import com.example.demo.youtube.YoutubeSearchCandidate;

public record SpotifyTrackMatch(SpotifyTrack track, List<YoutubeSearchCandidate> candidates) {

	public SpotifyTrackMatch {
		candidates = List.copyOf(candidates);
	}
}
