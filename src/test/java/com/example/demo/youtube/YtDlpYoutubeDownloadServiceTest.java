package com.example.demo.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

class YtDlpYoutubeDownloadServiceTest {

	@Test
	void commandForMp3ExtractsAudio() {
		YtDlpYoutubeDownloadService service = newService(new CapturingExecutor("song.mp3"));

		List<String> command = service.commandFor("https://www.youtube.com/watch?v=abc123", ArchiveFormat.MP3);

		assertTrue(command.contains("--extract-audio"));
		assertTrue(command.contains("--audio-format"));
		assertTrue(command.contains("mp3"));
		assertTrue(command.contains("--js-runtimes"));
		assertTrue(command.contains("node"));
		assertFalse(command.contains("--yes-playlist"));
		assertEquals("https://www.youtube.com/watch?v=abc123", command.get(command.size() - 1));
	}

	@Test
	void commandForVideoMergesToMp4() {
		YtDlpYoutubeDownloadService service = newService(new CapturingExecutor("video.mp4"));

		List<String> command = service.commandFor("https://www.youtube.com/playlist?list=PL123", ArchiveFormat.VIDEO);

		assertTrue(command.contains("--format"));
		assertTrue(command.contains("bestvideo+bestaudio/best"));
		assertTrue(command.contains("--merge-output-format"));
		assertTrue(command.contains("mp4"));
	}

	@Test
	void commandForPlaylistForcesPlaylistMode() {
		YtDlpYoutubeDownloadService service = newService(new CapturingExecutor("video.mp4"));

		List<String> command = service.commandFor(
				"https://www.youtube.com/watch?v=abc123&list=PL123",
				ArchiveFormat.VIDEO);

		assertTrue(command.contains("--yes-playlist"));
		assertTrue(command.contains("--ignore-errors"));
	}

	@Test
	void createsZipWithAllDownloadedMediaFiles() throws IOException {
		CapturingExecutor executor = new CapturingExecutor("first.mp3", "second.mp3", "download.log", "partial.part");
		YtDlpYoutubeDownloadService service = newService(executor);

		DownloadArchive archive = service.download("https://www.youtube.com/playlist?list=PL123", ArchiveFormat.MP3);

		assertEquals("youtube-mp3-download.zip", archive.fileName());
		assertTrue(archive.contentLength() > 0);
		assertEquals("https://www.youtube.com/playlist?list=PL123", executor.command.get(executor.command.size() - 1));
		assertEquals(List.of("first.mp3", "second.mp3"), zipEntries(archive));
	}

	@Test
	void failsWhenDownloaderProducesNoMediaFiles() {
		YtDlpYoutubeDownloadService service = newService(new CapturingExecutor());

		assertThrows(DownloadException.class,
				() -> service.download("https://www.youtube.com/watch?v=abc123", ArchiveFormat.WAV));
	}

	private YtDlpYoutubeDownloadService newService(ProcessExecutor executor) {
		YoutubeDownloaderProperties properties = new YoutubeDownloaderProperties();
		properties.setBinary("yt-dlp-test");
		properties.setTimeout(Duration.ofSeconds(5));
		return new YtDlpYoutubeDownloadService(properties, executor);
	}

	private List<String> zipEntries(DownloadArchive archive) throws IOException {
		List<String> entries = new ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(archive.resource().getInputStream())) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				entries.add(entry.getName());
			}
		}
		return entries;
	}

	private static class CapturingExecutor implements ProcessExecutor {

		private final List<String> filesToCreate;

		private List<String> command;

		CapturingExecutor(String... filesToCreate) {
			this.filesToCreate = List.of(filesToCreate);
		}

		@Override
		public void execute(List<String> command, Path workingDirectory, Duration timeout) {
			this.command = List.copyOf(command);
			filesToCreate.forEach(fileName -> {
				try {
					Files.writeString(workingDirectory.resolve(fileName), "media:" + fileName, StandardCharsets.UTF_8);
				}
				catch (IOException ex) {
					throw new DownloadException("Test file setup failed.", ex);
				}
			});
		}
	}
}
