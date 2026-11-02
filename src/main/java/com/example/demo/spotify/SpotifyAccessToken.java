package com.example.demo.spotify;

import java.io.Serializable;
import java.time.Instant;

public record SpotifyAccessToken(String accessToken, String refreshToken, Instant expiresAt) implements Serializable {

	public boolean expiresWithinSeconds(long seconds) {
		return expiresAt == null || expiresAt.isBefore(Instant.now().plusSeconds(seconds));
	}
}
