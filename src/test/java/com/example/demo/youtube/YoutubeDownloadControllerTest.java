package com.example.demo.youtube;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class YoutubeDownloadControllerTest {

	private final FakeDownloadService downloadService = new FakeDownloadService();

	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new YoutubeDownloadController(downloadService, new YoutubeUrlValidator()))
			.build();

	@Test
	void downloadsRequestedArchive() throws Exception {
		byte[] body = "zip-bytes".getBytes(StandardCharsets.UTF_8);
		downloadService.archive = new DownloadArchive("youtube-mp3-download.zip", new ByteArrayResource(body), body.length);

		mockMvc.perform(get("/api/youtube/download")
						.param("url", "https://www.youtube.com/watch?v=abc123")
						.param("format", "mp3"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("youtube-mp3-download.zip")))
				.andExpect(content().bytes(body));

		assertEquals("https://www.youtube.com/watch?v=abc123", downloadService.url);
		assertEquals(ArchiveFormat.MP3, downloadService.format);
	}

	@Test
	void rejectsUnsupportedFormat() throws Exception {
		mockMvc.perform(get("/api/youtube/download")
						.param("url", "https://www.youtube.com/watch?v=abc123")
						.param("format", "pdf"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsNonYoutubeUrl() throws Exception {
		mockMvc.perform(get("/api/youtube/download")
						.param("url", "https://example.com/watch?v=abc123")
						.param("format", "mp3"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void mapsDownloadFailureToBadGateway() throws Exception {
		downloadService.failure = new DownloadException("Downloader failed.");

		mockMvc.perform(get("/api/youtube/download")
						.param("url", "https://www.youtube.com/watch?v=abc123")
						.param("format", "wav"))
				.andExpect(status().isBadGateway());
	}

	private static class FakeDownloadService implements YoutubeDownloadService {

		private DownloadArchive archive;

		private DownloadException failure;

		private String url;

		private ArchiveFormat format;

		@Override
		public DownloadArchive download(String youtubeUrl, ArchiveFormat format) {
			if (failure != null) {
				throw failure;
			}
			this.url = youtubeUrl;
			this.format = format;
			return archive;
		}
	}
}
