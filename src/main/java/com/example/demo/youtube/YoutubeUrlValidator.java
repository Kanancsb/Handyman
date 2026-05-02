package com.example.demo.youtube;

import java.net.URI;
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
}
