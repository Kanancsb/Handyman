package com.example.demo.youtube;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;

public class AutoDeletingPathResource extends FileSystemResource {

	private final Path rootToDelete;

	public AutoDeletingPathResource(Path path, Path rootToDelete) {
		super(path);
		this.rootToDelete = rootToDelete;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		InputStream delegate = super.getInputStream();
		return new FilterInputStream(delegate) {
			@Override
			public void close() throws IOException {
				try {
					super.close();
				}
				finally {
					deleteRoot();
				}
			}
		};
	}

	private void deleteRoot() {
		try (Stream<Path> paths = Files.walk(rootToDelete)) {
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
			rootToDelete.toFile().deleteOnExit();
		}
	}
}
