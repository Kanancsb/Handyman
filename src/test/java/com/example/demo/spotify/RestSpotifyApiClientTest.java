package com.example.demo.spotify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestSpotifyApiClientTest {

	@Test
	void exchangesAuthorizationCodeForToken() {
		SpotifyProperties properties = configuredProperties();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestSpotifyApiClient client = new RestSpotifyApiClient(properties, builder.build());
		server.expect(requestTo("https://accounts.spotify.com/api/token"))
				.andRespond(withSuccess("""
						{"access_token":"access-123","refresh_token":"refresh-123","expires_in":3600}
						""", MediaType.APPLICATION_JSON));

		SpotifyAccessToken token = client.exchangeAuthorizationCode("code-123");

		assertEquals("access-123", token.accessToken());
		assertEquals("refresh-123", token.refreshToken());
		assertFalse(token.expiresWithinSeconds(60));
		server.verify();
	}

	@Test
	void readsSpotifyTrackMetadata() {
		SpotifyProperties properties = configuredProperties();
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestSpotifyApiClient client = new RestSpotifyApiClient(properties, builder.build());
		server.expect(requestTo("https://api.spotify.com/v1/playlists/37i9dQZF1DXcBWIGoYBM5M/items?limit=50&offset=0"))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-123"))
				.andRespond(withSuccess("""
						{
						  "total": 1,
						  "next": null,
						  "items": [{
						    "item": {
						      "id": "track-1",
						      "name": "Test Song",
						      "type": "track",
						      "is_local": false,
						      "duration_ms": 183000,
						      "artists": [{"name": "Test Artist"}],
						      "external_urls": {"spotify": "https://open.spotify.com/track/track-1"}
						    }
						  }]
						}
						""", MediaType.APPLICATION_JSON));

		SpotifyPlaylistSnapshot snapshot = client.playlistTracks(
				"access-123", "37i9dQZF1DXcBWIGoYBM5M", 200);

		assertEquals(1, snapshot.totalTracks());
		assertEquals("Test Song", snapshot.tracks().getFirst().name());
		assertEquals("Test Artist", snapshot.tracks().getFirst().artistText());
		assertEquals(183000, snapshot.tracks().getFirst().durationMs());
		server.verify();
	}

	private SpotifyProperties configuredProperties() {
		SpotifyProperties properties = new SpotifyProperties();
		properties.setClientId("client-id");
		properties.setClientSecret("client-secret");
		properties.setRedirectUri("http://127.0.0.1:8080/api/spotify/callback");
		return properties;
	}
}
