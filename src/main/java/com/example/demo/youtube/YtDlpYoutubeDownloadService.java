package com.example.demo.youtube;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class YtDlpYoutubeDownloadService implements YoutubeDownloadService {

	private static final String OUTPUT_TEMPLATE = "%(title).180B_%(id)s.%(ext)s";

	private final YoutubeDownloaderProperties properties;

	private final ProcessExecutor processExecutor;

	public YtDlpYoutubeDownloadService(YoutubeDownloaderProperties properties, ProcessExecutor processExecutor) {
		this.properties = properties;
		this.processExecutor = processExecutor;
	}

	@Override
	public DownloadArchive download(String youtubeUrl, ArchiveFormat format) {
		Path workspace = null;
		try {
			workspace = Files.createTempDirectory("handyman-youtube-");
			Path mediaDirectory = workspace.resolve("media");
			Files.createDirectories(mediaDirectory);

			processExecutor.execute(commandFor(youtubeUrl, format), mediaDirectory, properties.getTimeout());

			Path archive = workspace.resolve("youtube-%s-download.zip".formatted(format.requestValue()));
			writeZip(mediaDirectory, archive);

			return new DownloadArchive(
					archive.getFileName().toString(),
					new AutoDeletingPathResource(archive, workspace),
					Files.size(archive));
		}
		catch (IOException ex) {
			deleteWorkspace(workspace);
			throw new DownloadException("Could not create the download archive.", ex);
		}
		catch (RuntimeException ex) {
			deleteWorkspace(workspace);
			throw ex;
		}
	}

	List<String> commandFor(String youtubeUrl, ArchiveFormat format) {
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

		command.add(youtubeUrl);
		return List.copyOf(command);
	}

	private void writeZip(Path mediaDirectory, Path archive) throws IOException {
		List<Path> outputs;
		try (var stream = Files.walk(mediaDirectory)) {
			outputs = stream
					.filter(Files::isRegularFile)
					.filter(this::isMediaOutput)
					.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
					.toList();
		}

		if (outputs.isEmpty()) {
			throw new DownloadException("No media files were downloaded.");
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
