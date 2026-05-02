package com.example.demo.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArchiveFormatTest {

	@Test
	void resolvesRequestValuesCaseInsensitively() {
		assertEquals(ArchiveFormat.MP3, ArchiveFormat.fromRequestValue("mp3").orElseThrow());
		assertEquals(ArchiveFormat.WAV, ArchiveFormat.fromRequestValue(" WAV ").orElseThrow());
		assertEquals(ArchiveFormat.VIDEO, ArchiveFormat.fromRequestValue("Video").orElseThrow());
	}

	@Test
	void rejectsUnknownRequestValues() {
		assertTrue(ArchiveFormat.fromRequestValue("pdf").isEmpty());
		assertTrue(ArchiveFormat.fromRequestValue(null).isEmpty());
	}
}
