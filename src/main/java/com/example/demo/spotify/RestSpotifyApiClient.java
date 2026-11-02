package com.example.demo.spotify;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RestSpotifyApiClient implements SpotifyApiClient {

	private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";

	private static final String API_URL = "https://api.spotify.com/v1";

	private static final int PAGE_SIZE = 50;

	private final SpotifyProperties properties;

	private final RestClient restClient;

	@Autowired
	public RestSpotifyApiClient(SpotifyProperties properties) {
		this(properties, spotifyRestClient());
	}

	RestSpotifyApiClient(SpotifyProperties properties, RestClient restClient) {
		this.properties = properties;
		this.restClient = restClient;
	}

	@Override
	public SpotifyAccessToken exchangeAuthorizationCode(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("redirect_uri", properties.getRedirectUri());
		return requestToken(form, null);
	}

	@Override
	public SpotifyAccessToken refreshAccessToken(String refreshToken) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "refresh_token");
		form.add("refresh_token", refreshToken);
		return requestToken(form, refreshToken);
	}

	@Override
	public SpotifyPlaylistSnapshot playlistTracks(String accessToken, String playlistId, int maximumTracks) {
		List<SpotifyTrack> tracks = new ArrayList<>();
		int offset = 0;
		int total = 0;
		boolean truncated = false;
		try {
			while (tracks.size() < maximumTracks) {
				URI uri = UriComponentsBuilder.fromUriString(API_URL)
						.pathSegment("playlists", playlistId, "items")
						.queryParam("limit", Math.min(PAGE_SIZE, maximumTracks - tracks.size()))
						.queryParam("offset", offset)
						.build()
						.encode()
						.toUri();
				Map<String, Object> response = restClient.get()
						.uri(uri)
						.headers(headers -> headers.setBearerAuth(accessToken))
						.retrieve()
						.body(new ParameterizedTypeReference<>() {
						});
				if (response == null) {
					throw new SpotifyApiException(HttpStatus.BAD_GATEWAY, "Spotify returned an empty playlist response.");
				}

				total = integerValue(response.get("total"), tracks.size());
				List<?> items = listValue(response.get("items"));
				for (Object item : items) {
					SpotifyTrack track = toTrack(mapValue(item));
					if (track != null && tracks.size() < maximumTracks) {
						tracks.add(track);
					}
				}

				boolean hasNextPage = StringUtils.hasText(stringValue(response.get("next")));
				if (!hasNextPage || items.isEmpty()) {
					truncated = false;
					break;
				}
				truncated = true;
				offset += items.size();
			}
			return new SpotifyPlaylistSnapshot(tracks, total, truncated);
		}
		catch (RestClientResponseException ex) {
			throw apiFailure(ex);
		}
		catch (ResourceAccessException ex) {
			throw new SpotifyApiException(HttpStatus.BAD_GATEWAY, "Could not connect to Spotify.", ex);
		}
		catch (RestClientException ex) {
			throw new SpotifyApiException(HttpStatus.BAD_GATEWAY, "Spotify returned an unreadable playlist response.", ex);
		}
	}

	private SpotifyAccessToken requestToken(MultiValueMap<String, String> form, String existingRefreshToken) {
		try {
			Map<String, Object> response = restClient.post()
					.uri(TOKEN_URL)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.headers(headers -> headers.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
					.body(form)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			if (response == null || !StringUtils.hasText(stringValue(response.get("access_token")))) {
				throw new SpotifyApiException(HttpStatus.BAD_GATEWAY, "Spotify did not return an access token.");
			}

			String refreshToken = stringValue(response.get("refresh_token"));
			if (!StringUtils.hasText(refreshToken)) {
				refreshToken = existingRefreshToken;
			}
			long expiresIn = Math.max(60, integerValue(response.get("expires_in"), 3600));
			return new SpotifyAccessToken(
					stringValue(response.get("access_token")),
					refreshToken,
					Instant.now().plusSeconds(expiresIn));
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401) {
				throw new SpotifyApiException(HttpStatus.BAD_GATEWAY,
						"Spotify rejected the authorization code or application credentials.", ex);
			}
			throw apiFailure(ex);
		}
		catch (ResourceAccessException ex) {
			throw new SpotifyApiException(HttpStatus.BAD_GATEWAY, "Could not connect to Spotify.", ex);
		}
		catch (RestClientException ex) {
			throw new SpotifyApiException(HttpStatus.BAD_GATEWAY, "Spotify returned an unreadable token response.", ex);
		}
	}

	private SpotifyTrack toTrack(Map<String, Object> wrapper) {
		Map<String, Object> item = mapValue(wrapper.get("item"));
		if (item.isEmpty()) {
			item = mapValue(wrapper.get("track"));
		}
		if (item.isEmpty() || !"track".equals(stringValue(item.get("type")))
				|| Boolean.TRUE.equals(item.get("is_local"))) {
			return null;
		}

		String id = stringValue(item.get("id"));
		String name = stringValue(item.get("name"));
		if (!StringUtils.hasText(id) || !StringUtils.hasText(name)) {
			return null;
		}

		List<String> artists = listValue(item.get("artists")).stream()
				.map(this::mapValue)
				.map(artist -> stringValue(artist.get("name")))
				.filter(StringUtils::hasText)
				.toList();
		if (artists.isEmpty()) {
			return null;
		}

		String spotifyUrl = stringValue(mapValue(item.get("external_urls")).get("spotify"));
		return new SpotifyTrack(id, name, artists, integerValue(item.get("duration_ms"), 0), spotifyUrl);
	}

	private SpotifyApiException apiFailure(RestClientResponseException ex) {
		int status = ex.getStatusCode().value();
		return switch (status) {
			case 401 -> new SpotifyApiException(HttpStatus.UNAUTHORIZED,
					"Your Spotify connection expired. Connect Spotify again.", ex);
			case 403 -> new SpotifyApiException(HttpStatus.FORBIDDEN,
					"Spotify only permits playlists you own or collaborate on.", ex);
			case 404 -> new SpotifyApiException(HttpStatus.NOT_FOUND,
					"Spotify could not find that playlist.", ex);
			case 429 -> new SpotifyApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Spotify rate-limited this request. Wait a moment and try again.", ex);
			default -> new SpotifyApiException(HttpStatus.BAD_GATEWAY,
					"Spotify could not read the playlist.", ex);
		};
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> mapValue(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	private List<?> listValue(Object value) {
		return value instanceof List<?> list ? list : List.of();
	}

	private String stringValue(Object value) {
		return value instanceof String text ? text : "";
	}

	private int integerValue(Object value, int defaultValue) {
		return value instanceof Number number ? number.intValue() : defaultValue;
	}

	private static RestClient spotifyRestClient() {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(15))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(30));
		return RestClient.builder().requestFactory(requestFactory).build();
	}
}
