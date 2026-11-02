package com.example.demo.spotify;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SpotifyPlaylistUrlParser {

	private static final Set<String> ALLOWED_HOSTS = Set.of("open.spotify.com", "www.open.spotify.com");

	private static final Pattern PLAYLIST_ID = Pattern.compile("[A-Za-z0-9]{10,64}");

	public String playlistId(String value) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException("Enter a Spotify playlist URL.");
		}

		try {
			URI uri = URI.create(value.trim());
			String scheme = uri.getScheme();
			String host = uri.getHost();
			if (scheme == null || host == null
					|| !"https".equals(scheme.toLowerCase(Locale.ROOT))
					|| !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("Enter a valid open.spotify.com playlist URL.");
			}

			String[] pathParts = java.util.Arrays.stream(uri.getPath().split("/"))
					.filter(StringUtils::hasText)
					.toArray(String[]::new);
			int playlistIndex = pathParts.length == 2 ? 0 : pathParts.length == 3 && pathParts[0].startsWith("intl-") ? 1 : -1;
			if (playlistIndex < 0 || !"playlist".equals(pathParts[playlistIndex])
					|| !PLAYLIST_ID.matcher(pathParts[playlistIndex + 1]).matches()) {
				throw new IllegalArgumentException("Enter a valid Spotify playlist URL.");
			}
			return pathParts[playlistIndex + 1];
		}
		catch (IllegalArgumentException ex) {
			if (ex.getMessage() != null && ex.getMessage().startsWith("Enter a")) {
				throw ex;
			}
			throw new IllegalArgumentException("Enter a valid Spotify playlist URL.", ex);
		}
	}
}
