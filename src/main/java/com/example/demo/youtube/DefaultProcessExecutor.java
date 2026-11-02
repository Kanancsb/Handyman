package com.example.demo.youtube;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class DefaultProcessExecutor implements ProcessExecutor {

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
				throw new DownloadException(
						"yt-dlp timed out after %s.".formatted(readableTimeout(timeout)),
						ProcessLogTail.read(logFile));
			}

			int exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new DownloadException(
						"yt-dlp failed with exit code %d.".formatted(exitCode),
						ProcessLogTail.read(logFile));
			}
		}
		catch (IOException ex) {
			throw new DownloadException(
					"Could not start the download process. Make sure yt-dlp is installed.",
					"Process start error: " + ex.getMessage(),
					ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new DownloadException("The download process was interrupted.", ex);
		}
	}

	@Override
	public String executeForOutput(List<String> command, Path workingDirectory, Duration timeout) {
		Path outputFile = null;
		try {
			Files.createDirectories(workingDirectory);
			outputFile = Files.createTempFile(workingDirectory, "search-", ".log");
			Process process = new ProcessBuilder(command)
					.directory(workingDirectory.toFile())
					.redirectErrorStream(true)
					.redirectOutput(outputFile.toFile())
					.start();

			boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!completed) {
				process.destroyForcibly();
				throw new DownloadException("The YouTube search timed out.");
			}

			String output = Files.readString(outputFile);
			if (process.exitValue() != 0) {
				throw new DownloadException(
						"The YouTube search failed with exit code %d.".formatted(process.exitValue()),
						ProcessLogTail.read(outputFile));
			}
			return output;
		}
		catch (IOException ex) {
			throw new DownloadException(
					"Could not start the YouTube search. Make sure yt-dlp is installed.",
					"Process start error: " + ex.getMessage(),
					ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new DownloadException("The YouTube search was interrupted.", ex);
		}
		finally {
			if (outputFile != null) {
				try {
					Files.deleteIfExists(outputFile);
				}
				catch (IOException ex) {
					outputFile.toFile().deleteOnExit();
				}
			}
		}
	}

	private String readableTimeout(Duration timeout) {
		if (timeout.toMinutes() >= 1) {
			return "%d minute%s".formatted(timeout.toMinutes(), timeout.toMinutes() == 1 ? "" : "s");
		}
		return "%d second%s".formatted(timeout.toSeconds(), timeout.toSeconds() == 1 ? "" : "s");
	}
}
