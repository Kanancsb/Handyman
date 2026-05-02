package com.example.demo.youtube;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class DefaultProcessExecutor implements ProcessExecutor {

	private static final int ERROR_TAIL_BYTES = 8_192;

	@Override
	public void execute(List<String> command, Path workingDirectory, Duration timeout) {
		Path logFile = workingDirectory.resolve("download.log");
		try {
			Files.createDirectories(workingDirectory);
			Process process = new ProcessBuilder(command)
					.directory(workingDirectory.toFile())
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
					.start();

			boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!completed) {
				process.destroyForcibly();
				throw new DownloadException("The download process timed out.");
			}

			int exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new DownloadException("The download process failed with exit code %d.%s"
						.formatted(exitCode, processOutput(logFile)));
			}
		}
		catch (IOException ex) {
			throw new DownloadException("Could not start the download process. Make sure yt-dlp is installed.", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new DownloadException("The download process was interrupted.", ex);
		}
	}

	private String processOutput(Path logFile) {
		try {
			if (!Files.exists(logFile)) {
				return "";
			}
			byte[] bytes = Files.readAllBytes(logFile);
			int offset = Math.max(0, bytes.length - ERROR_TAIL_BYTES);
			return System.lineSeparator() + new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "";
		}
	}
}
