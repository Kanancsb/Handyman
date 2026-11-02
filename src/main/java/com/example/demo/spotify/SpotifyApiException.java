package com.example.demo.spotify;

import org.springframework.http.HttpStatus;

public class SpotifyApiException extends RuntimeException {

	private final HttpStatus status;

	public SpotifyApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public SpotifyApiException(HttpStatus status, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
