package com.example.demo.youtube;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class YoutubeDownloadControllerTest {

	private final FakeDownloadService downloadService = new FakeDownloadService();

	private final PreparedDownloadStore preparedDownloadStore = preparedDownloadStore();

	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new YoutubeDownloadController(
					downloadService, new YoutubeUrlValidator(), preparedDownloadStore))
			.build();

	@AfterEach
	void closeStore() {
		preparedDownloadStore.close();
	}

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
		downloadService.failure = new DownloadException("yt-dlp failed with exit code 1.", "ERROR: video unavailable");

		mockMvc.perform(get("/api/youtube/download")
						.param("url", "https://www.youtube.com/watch?v=abc123")
						.param("format", "wav"))
				.andExpect(status().isBadGateway())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("yt-dlp failed with exit code 1."))
				.andExpect(jsonPath("$.diagnosticId").isNotEmpty())
				.andExpect(jsonPath("$.log").value("ERROR: video unavailable"));
	}

	@Test
	void downloadsValidatedUrlBatch() throws Exception {
		byte[] body = "batch-zip".getBytes(StandardCharsets.UTF_8);
		downloadService.archive = new DownloadArchive("youtube-mp3-download.zip", new ByteArrayResource(body), body.length);

		mockMvc.perform(post("/api/youtube/download-batch")
						.param("format", "mp3")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"urls":["https://www.youtube.com/watch?v=first","https://youtu.be/second"]}
								"""))
				.andExpect(status().isOk())
				.andExpect(content().bytes(body));

		assertEquals(java.util.List.of(
				"https://www.youtube.com/watch?v=first",
				"https://youtu.be/second"), downloadService.urls);
	}

	@Test
	void rejectsBatchContainingNonYoutubeUrl() throws Exception {
		mockMvc.perform(post("/api/youtube/download-batch")
						.param("format", "mp3")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"urls":["https://example.com/not-youtube"]}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void preparesArchiveThenStreamsItThroughNativeDownloadEndpoint() throws Exception {
		byte[] body = "prepared-zip".getBytes(StandardCharsets.UTF_8);
		downloadService.archive = new DownloadArchive("youtube-wav-download.zip", new ByteArrayResource(body), body.length);
		MockHttpSession session = new MockHttpSession();

		MvcResult prepareResult = mockMvc.perform(post("/api/youtube/prepare")
						.session(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "urls":["https://www.youtube.com/playlist?list=PL123"],
								  "format":"wav"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.downloadUrl").isNotEmpty())
				.andExpect(jsonPath("$.fileName").value("youtube-wav-download.zip"))
				.andExpect(jsonPath("$.contentLength").value(body.length))
				.andReturn();

		Matcher matcher = Pattern.compile("\"downloadUrl\":\"([^\"]+)\"")
				.matcher(prepareResult.getResponse().getContentAsString());
		if (!matcher.find()) {
			throw new AssertionError("Prepared response did not contain a download URL.");
		}
		String downloadUrl = matcher.group(1);

		mockMvc.perform(get(downloadUrl).session(session))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(content().bytes(body));

		mockMvc.perform(get(downloadUrl).session(session))
				.andExpect(status().isNotFound());
	}

	private static class FakeDownloadService implements YoutubeDownloadService {

		private DownloadArchive archive;

		private DownloadException failure;

		private String url;

		private ArchiveFormat format;

		private java.util.List<String> urls;

		@Override
		public DownloadArchive download(String youtubeUrl, ArchiveFormat format) {
			if (failure != null) {
				throw failure;
			}
			this.url = youtubeUrl;
			this.format = format;
			return archive;
		}

		@Override
		public DownloadArchive download(java.util.List<String> youtubeUrls, ArchiveFormat format) {
			if (failure != null) {
				throw failure;
			}
			this.urls = java.util.List.copyOf(youtubeUrls);
			this.format = format;
			return archive;
		}
	}

	private static PreparedDownloadStore preparedDownloadStore() {
		YoutubeDownloaderProperties properties = new YoutubeDownloaderProperties();
		return new PreparedDownloadStore(properties);
	}
}
