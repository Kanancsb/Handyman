package com.example.demo.youtube;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface ProcessExecutor {

	void execute(List<String> command, Path workingDirectory, Duration timeout);

	default String executeForOutput(List<String> command, Path workingDirectory, Duration timeout) {
		throw new UnsupportedOperationException("This process executor does not capture output.");
	}
}
