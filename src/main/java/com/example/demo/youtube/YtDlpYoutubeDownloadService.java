package com.example.demo.youtube;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class YtDlpYoutubeDownloadService implements YoutubeDownloadService {

	private static final Logger logger = LoggerFactory.getLogger(YtDlpYoutubeDownloadService.class);

	private static final String OUTPUT_TEMPLATE = "%(autonumber)02d_%(title).180B.%(ext)s";

	private final YoutubeDownloaderProperties properties;

	private final ProcessExecutor processExecutor;

	public YtDlpYoutubeDownloadService(YoutubeDownloaderProperties properties, ProcessExecutor processExecutor) {
		this.properties = properties;
		this.processExecutor = processExecutor;
	}

	@Override
	public DownloadArchive download(String youtubeUrl, ArchiveFormat format) {
		return download(List.of(youtubeUrl), format);
	}

	@Override
	public DownloadArchive download(List<String> youtubeUrls, ArchiveFormat format) {
		String diagnosticId = UUID.randomUUID().toString().substring(0, 8);
		long startedAt = System.nanoTime();
		String sourceType = sourceType(youtubeUrls);
		Path workspace = null;
		logger.info("YouTube archive job {} started: source={}, inputs={}, format={}, timeout={}",
				diagnosticId, sourceType, youtubeUrls.size(), format.requestValue(), properties.getTimeout());
		try {
			workspace = Files.createTempDirectory("handyman-youtube-");
			Path mediaDirectory = workspace.resolve("media");
			Files.createDirectories(mediaDirectory);

			processExecutor.execute(commandFor(youtubeUrls, format), mediaDirectory, properties.getTimeout());

			Path archive = workspace.resolve("youtube-%s-download.zip".formatted(format.requestValue()));
			int mediaFileCount = writeZip(mediaDirectory, archive);
			long archiveSize = Files.size(archive);
			logger.info("YouTube archive job {} completed: mediaFiles={}, archiveBytes={}, elapsedMs={}",
					diagnosticId, mediaFileCount, archiveSize, elapsedMillis(startedAt));

			return new DownloadArchive(
					archive.getFileName().toString(),
					new AutoDeletingPathResource(archive, workspace),
					archiveSize);
		}
		catch (IOException ex) {
			deleteWorkspace(workspace);
			DownloadException failure = new DownloadException("Could not create the download archive.", ex)
					.withDiagnosticId(diagnosticId);
			logFailure(failure, sourceType, youtubeUrls.size(), format, startedAt);
			throw failure;
		}
		catch (DownloadException ex) {
			deleteWorkspace(workspace);
			DownloadException failure = ex.withDiagnosticId(diagnosticId);
			logFailure(failure, sourceType, youtubeUrls.size(), format, startedAt);
			throw failure;
		}
		catch (RuntimeException ex) {
			deleteWorkspace(workspace);
			DownloadException failure = new DownloadException("An unexpected archive error occurred.", ex)
					.withDiagnosticId(diagnosticId);
			logFailure(failure, sourceType, youtubeUrls.size(), format, startedAt);
			throw failure;
		}
	}

	List<String> commandFor(String youtubeUrl, ArchiveFormat format) {
		return commandFor(List.of(youtubeUrl), format);
	}

	List<String> commandFor(List<String> youtubeUrls, ArchiveFormat format) {
		List<String> command = new ArrayList<>();
		command.add(properties.getBinary());
		command.add("--no-progress");
		command.add("--restrict-filenames");
		command.add("--windows-filenames");
		command.add("--output");
		command.add(OUTPUT_TEMPLATE);
		if (StringUtils.hasText(properties.getJsRuntime())) {
			command.add("--js-runtimes");
			command.add(properties.getJsRuntime());
		}
		if (youtubeUrls.stream().anyMatch(this::isPlaylistUrl)) {
			command.add("--yes-playlist");
			command.add("--ignore-errors");
		}

		switch (format) {
			case MP3 -> {
				command.add("--extract-audio");
				command.add("--audio-format");
				command.add("mp3");
				command.add("--audio-quality");
				command.add("0");
			}
			case WAV -> {
				command.add("--extract-audio");
				command.add("--audio-format");
				command.add("wav");
			}
			case VIDEO -> {
				command.add("--format");
				command.add("bestvideo+bestaudio/best");
				command.add("--merge-output-format");
				command.add("mp4");
			}
		}

		command.addAll(youtubeUrls);
		return List.copyOf(command);
	}

	private boolean isPlaylistUrl(String youtubeUrl) {
		try {
			String query = URI.create(youtubeUrl).getRawQuery();
			if (!StringUtils.hasText(query)) {
				return false;
			}

			for (String parameter : query.split("&")) {
				String[] parts = parameter.split("=", 2);
				String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
				String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
				if ("list".equals(key) && StringUtils.hasText(value)) {
					return true;
				}
			}
			return false;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private int writeZip(Path mediaDirectory, Path archive) throws IOException {
		List<Path> outputs;
		try (var stream = Files.walk(mediaDirectory)) {
			outputs = stream
					.filter(Files::isRegularFile)
					.filter(this::isMediaOutput)
					.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
					.toList();
		}

		if (outputs.isEmpty()) {
			throw new DownloadException(
					"No media files were downloaded. The videos may be unavailable, private, or blocked.",
					ProcessLogTail.read(mediaDirectory.resolve("download.log")));
		}

		try (OutputStream outputStream = Files.newOutputStream(archive);
				ZipOutputStream zip = new ZipOutputStream(outputStream)) {
			for (Path output : outputs) {
				String entryName = mediaDirectory.relativize(output).toString().replace('\\', '/');
				zip.putNextEntry(new ZipEntry(entryName));
				Files.copy(output, zip);
				zip.closeEntry();
			}
		}
		return outputs.size();
	}

	private String sourceType(List<String> youtubeUrls) {
		if (youtubeUrls.stream().anyMatch(this::isPlaylistUrl)) {
			return "playlist";
		}
		return youtubeUrls.size() > 1 ? "batch" : "video";
	}

	private long elapsedMillis(long startedAt) {
		return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
	}

	private void logFailure(DownloadException failure, String sourceType, int inputCount,
			ArchiveFormat format, long startedAt) {
		String details = StringUtils.hasText(failure.diagnosticDetails())
				? failure.diagnosticDetails()
				: "No yt-dlp output was captured.";
		logger.error("""
				YouTube archive job {} failed: source={}, inputs={}, format={}, elapsedMs={}, reason={}, rootCause={}
				yt-dlp output tail:
				{}""",
				failure.diagnosticId(), sourceType, inputCount, format.requestValue(), elapsedMillis(startedAt),
				failure.getMessage(), rootCauseSummary(failure), details);
	}

	private String rootCauseSummary(Throwable throwable) {
		Throwable rootCause = throwable;
		while (rootCause.getCause() != null) {
			rootCause = rootCause.getCause();
		}
		return "%s: %s".formatted(rootCause.getClass().getSimpleName(), rootCause.getMessage());
	}

	private boolean isMediaOutput(Path path) {
		String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return !fileName.equals("download.log")
				&& !fileName.endsWith(".part")
				&& !fileName.endsWith(".ytdl")
				&& !fileName.endsWith(".temp")
				&& !fileName.endsWith(".tmp");
	}

	private void deleteWorkspace(Path workspace) {
		if (workspace == null) {
			return;
		}

		try (Stream<Path> paths = Files.walk(workspace)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					path.toFile().deleteOnExit();
				}
			});
		}
		catch (IOException ex) {
			workspace.toFile().deleteOnExit();
		}
	}
}
