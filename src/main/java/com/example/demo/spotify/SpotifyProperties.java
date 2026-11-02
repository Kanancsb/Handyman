package com.example.demo.spotify;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "handyman.spotify")
public class SpotifyProperties {

	private String clientId;

	private String clientSecret;

	private String redirectUri = "http://127.0.0.1:8080/api/spotify/callback";

	private int youtubeResults = 3;

	private int maxTracks = 200;

	private int matchingConcurrency = 4;

	private Duration searchTimeout = Duration.ofSeconds(45);

	public boolean isConfigured() {
		return StringUtils.hasText(clientId)
				&& StringUtils.hasText(clientSecret)
				&& StringUtils.hasText(redirectUri);
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getRedirectUri() {
		return redirectUri;
	}

	public void setRedirectUri(String redirectUri) {
		this.redirectUri = redirectUri;
	}

	public int getYoutubeResults() {
		return youtubeResults;
	}

	public void setYoutubeResults(int youtubeResults) {
		this.youtubeResults = youtubeResults;
	}

	public int getMaxTracks() {
		return maxTracks;
	}

	public void setMaxTracks(int maxTracks) {
		this.maxTracks = maxTracks;
	}

	public int getMatchingConcurrency() {
		return matchingConcurrency;
	}

	public void setMatchingConcurrency(int matchingConcurrency) {
		this.matchingConcurrency = matchingConcurrency;
	}

	public Duration getSearchTimeout() {
		return searchTimeout;
	}

	public void setSearchTimeout(Duration searchTimeout) {
		this.searchTimeout = searchTimeout;
	}
}
