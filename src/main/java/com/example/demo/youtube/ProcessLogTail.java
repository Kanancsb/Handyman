package com.example.demo.youtube;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

final class ProcessLogTail {

	static final int DEFAULT_MAX_BYTES = 8_192;

	private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B\\[[;\\d]*[ -/]*[@-~]");

	private ProcessLogTail() {
	}

	static String read(Path logFile) {
		if (logFile == null || !Files.exists(logFile)) {
			return "";
		}

		try (SeekableByteChannel channel = Files.newByteChannel(logFile)) {
			long size = channel.size();
			int length = (int) Math.min(size, DEFAULT_MAX_BYTES);
			channel.position(Math.max(0, size - length));
			ByteBuffer buffer = ByteBuffer.allocate(length);
			while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
				// Keep reading until the bounded tail is complete.
			}
			buffer.flip();
			String output = StandardCharsets.UTF_8.decode(buffer).toString();
			return ANSI_ESCAPE.matcher(output).replaceAll("").trim();
		}
		catch (IOException ex) {
			return "";
		}
	}
}
