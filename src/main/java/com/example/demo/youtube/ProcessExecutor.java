package com.example.demo.youtube;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface ProcessExecutor {

	void execute(List<String> command, Path workingDirectory, Duration timeout);
}
