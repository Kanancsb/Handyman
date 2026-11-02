package com.example.demo.spotify;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.youtube.DownloadException;

@RestController
@RequestMapping("/api/spotify")
public class SpotifyController {

	private static final String TOKEN_ATTRIBUTE = SpotifyController.class.getName() + ".token";

	private static final String STATE_ATTRIBUTE = SpotifyController.class.getName() + ".state";

	private final SpotifyProperties properties;

	private final SpotifyPlaylistImportService importService;

	private final SecureRandom secureRandom = new SecureRandom();

	public SpotifyController(SpotifyProperties properties, SpotifyPlaylistImportService importService) {
		this.properties = properties;
		this.importService = importService;
	}

	@GetMapping("/status")
	public SpotifyStatus status(HttpSession session) {
		return new SpotifyStatus(properties.isConfigured(), session.getAttribute(TOKEN_ATTRIBUTE) instanceof SpotifyAccessToken);
	}

	@GetMapping("/login")
	public ResponseEntity<Void> login(@RequestParam(defaultValue = "false") boolean originChecked, HttpSession session) {
		ensureConfigured();
		if (!originChecked) {
			return ResponseEntity.status(HttpStatus.FOUND)
					.location(loginUriOnConfiguredOrigin())
					.build();
		}
		byte[] stateBytes = new byte[32];
		secureRandom.nextBytes(stateBytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
		session.setAttribute(STATE_ATTRIBUTE, state);
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(importService.authorizationUri(state))
				.build();
	}

	@GetMapping("/callback")
	public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			HttpSession session) {
		Object expectedState = session.getAttribute(STATE_ATTRIBUTE);
		session.removeAttribute(STATE_ATTRIBUTE);
		if (StringUtils.hasText(error) || !StringUtils.hasText(code) || !(expectedState instanceof String expected)
				|| !sameValue(expected, state)) {
			return youtubeRedirect("error");
		}

		try {
			session.setAttribute(TOKEN_ATTRIBUTE, importService.authorize(code));
			return youtubeRedirect("connected");
		}
		catch (SpotifyApiException ex) {
			return youtubeRedirect("error");
		}
	}

	@PostMapping("/disconnect")
	public ResponseEntity<Void> disconnect(HttpSession session) {
		session.removeAttribute(TOKEN_ATTRIBUTE);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/import", consumes = "application/json")
	public SpotifyImportResult importPlaylist(@RequestBody SpotifyImportRequest request, HttpSession session) {
		ensureConfigured();
		if (request == null || !StringUtils.hasText(request.url())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a Spotify playlist URL.");
		}

		SpotifyAccessToken token = session.getAttribute(TOKEN_ATTRIBUTE) instanceof SpotifyAccessToken value ? value : null;
		if (token == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connect Spotify before importing a playlist.");
		}

		try {
			token = importService.refreshIfNeeded(token);
			session.setAttribute(TOKEN_ATTRIBUTE, token);
			return importService.importPlaylist(request.url(), token);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
		}
		catch (SpotifyApiException ex) {
			if (ex.status() == HttpStatus.UNAUTHORIZED) {
				session.removeAttribute(TOKEN_ATTRIBUTE);
			}
			throw new ResponseStatusException(ex.status(), ex.getMessage(), ex);
		}
		catch (DownloadException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
		}
	}

	private void ensureConfigured() {
		if (!properties.isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Spotify import is not configured on this server.");
		}
	}

	private boolean sameValue(String expected, String actual) {
		if (actual == null) {
			return false;
		}
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8));
	}

	private ResponseEntity<Void> youtubeRedirect(String result) {
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create("/youtube?spotify=" + result))
				.build();
	}

	private URI loginUriOnConfiguredOrigin() {
		URI redirectUri = URI.create(properties.getRedirectUri());
		return URI.create("%s://%s/api/spotify/login?originChecked=true"
				.formatted(redirectUri.getScheme(), redirectUri.getAuthority()));
	}

	public record SpotifyStatus(boolean configured, boolean connected) {
	}

	public record SpotifyImportRequest(String url) {
	}
}
