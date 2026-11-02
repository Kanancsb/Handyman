package com.example.demo.youtube;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreparedDownloadStoreTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void keepsPreparedArchivePrivateToSessionAndCleansItOnShutdown() throws Exception {
		Path workspace = temporaryDirectory.resolve("workspace");
		Path archivePath = workspace.resolve("archive.zip");
		Files.createDirectories(workspace);
		Files.writeString(archivePath, "zip");
		DownloadArchive archive = new DownloadArchive(
				"archive.zip", new AutoDeletingPathResource(archivePath, workspace), Files.size(archivePath));
		PreparedDownloadStore store = new PreparedDownloadStore(new YoutubeDownloaderProperties());

		String token = store.put(archive, "owner-session");

		assertTrue(store.take(token, "other-session").isEmpty());
		assertTrue(Files.exists(archivePath));
		store.close();
		assertFalse(Files.exists(workspace));
	}
}
