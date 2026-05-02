package com.example.demo.youtube;

import java.util.Arrays;
import java.util.Optional;

public enum ArchiveFormat {
	MP3("mp3", "MP3 audio"),
	WAV("wav", "WAV audio"),
	VIDEO("video", "MP4 video");

	private final String requestValue;
	private final String label;

	ArchiveFormat(String requestValue, String label) {
		this.requestValue = requestValue;
		this.label = label;
	}

	public String requestValue() {
		return requestValue;
	}

	public String label() {
		return label;
	}

	public static Optional<ArchiveFormat> fromRequestValue(String value) {
		if (value == null) {
			return Optional.empty();
		}
		String normalized = value.trim().toLowerCase();
		return Arrays.stream(values())
				.filter(format -> format.requestValue.equals(normalized))
				.findFirst();
	}
}
