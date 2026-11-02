package com.example.demo.youtube;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class YoutubeUrlValidator {

	private static final Set<String> ALLOWED_HOSTS = Set.of(
			"youtube.com",
			"www.youtube.com",
			"m.youtube.com",
			"music.youtube.com",
			"youtu.be",
			"www.youtu.be",
			"youtube-nocookie.com",
			"www.youtube-nocookie.com");

	public boolean isSupported(String url) {
		if (!StringUtils.hasText(url)) {
			return false;
		}

		try {
			URI uri = URI.create(url.trim());
			String scheme = uri.getScheme();
			String host = uri.getHost();
			if (scheme == null || host == null) {
				return false;
			}

			String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
			String normalizedHost = host.toLowerCase(Locale.ROOT);
			return ("http".equals(normalizedScheme) || "https".equals(normalizedScheme))
					&& ALLOWED_HOSTS.contains(normalizedHost);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	public String normalize(String url) {
		return url.trim();
	}

	public boolean isIndividualVideo(String url) {
		if (!isSupported(url)) {
			return false;
		}

		try {
			URI uri = URI.create(url.trim());
			if (hasQueryValue(uri, "list")) {
				return false;
			}

			String host = uri.getHost().toLowerCase(Locale.ROOT);
			String path = uri.getPath() == null ? "" : uri.getPath();
			if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
				return path.matches("/[A-Za-z0-9_-]{6,20}/?");
			}
			if ("/watch".equals(path)) {
				return hasQueryValue(uri, "v");
			}
			return path.matches("/(shorts|embed|live)/[A-Za-z0-9_-]{6,20}/?");
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private boolean hasQueryValue(URI uri, String expectedKey) {
		if (!StringUtils.hasText(uri.getRawQuery())) {
			return false;
		}
		for (String parameter : uri.getRawQuery().split("&")) {
			String[] parts = parameter.split("=", 2);
			String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
			String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
			if (expectedKey.equals(key) && StringUtils.hasText(value)) {
				return true;
			}
		}
		return false;
	}
}
