package com.example.demo.spotify;

import java.net.URI;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.youtube.YoutubeTrackMatcher;

@Service
public class SpotifyPlaylistImportService {

	private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";

	private final SpotifyProperties properties;

	private final SpotifyPlaylistUrlParser urlParser;

	private final SpotifyApiClient apiClient;

	private final YoutubeTrackMatcher trackMatcher;

	public SpotifyPlaylistImportService(SpotifyProperties properties, SpotifyPlaylistUrlParser urlParser,
			SpotifyApiClient apiClient, YoutubeTrackMatcher trackMatcher) {
		this.properties = properties;
		this.urlParser = urlParser;
		this.apiClient = apiClient;
		this.trackMatcher = trackMatcher;
	}

	public URI authorizationUri(String state) {
		return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
				.queryParam("client_id", properties.getClientId())
				.queryParam("response_type", "code")
				.queryParam("redirect_uri", properties.getRedirectUri())
				.queryParam("scope", "playlist-read-private playlist-read-collaborative")
				.queryParam("state", state)
				.build()
				.encode()
				.toUri();
	}

	public SpotifyAccessToken authorize(String code) {
		return apiClient.exchangeAuthorizationCode(code);
	}

	public SpotifyAccessToken refreshIfNeeded(SpotifyAccessToken token) {
		if (!token.expiresWithinSeconds(60)) {
			return token;
		}
		if (token.refreshToken() == null || token.refreshToken().isBlank()) {
			throw new SpotifyApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
					"Your Spotify connection expired. Connect Spotify again.");
		}
		return apiClient.refreshAccessToken(token.refreshToken());
	}

	public SpotifyImportResult importPlaylist(String playlistUrl, SpotifyAccessToken token) {
		String playlistId = urlParser.playlistId(playlistUrl);
		int maximumTracks = Math.max(1, Math.min(properties.getMaxTracks(), 200));
		SpotifyPlaylistSnapshot snapshot = apiClient.playlistTracks(token.accessToken(), playlistId, maximumTracks);
		if (snapshot.tracks().isEmpty()) {
			throw new SpotifyApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT,
					"The Spotify playlist does not contain importable tracks.");
		}
		return new SpotifyImportResult(
				snapshot.totalTracks(),
				snapshot.truncated(),
				trackMatcher.match(snapshot.tracks()));
	}
}
