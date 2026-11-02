package com.example.demo.spotify;

public interface SpotifyApiClient {

	SpotifyAccessToken exchangeAuthorizationCode(String code);

	SpotifyAccessToken refreshAccessToken(String refreshToken);

	SpotifyPlaylistSnapshot playlistTracks(String accessToken, String playlistId, int maximumTracks);
}
