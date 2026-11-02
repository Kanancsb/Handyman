package com.example.demo.youtube;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

@Component
public class PreparedDownloadStore {

	private static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofMinutes(30);

	private final Map<String, StoredDownload> downloads = new ConcurrentHashMap<>();

	private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("prepared-download-cleanup").factory());

	private final Duration timeToLive;

	public PreparedDownloadStore(YoutubeDownloaderProperties properties) {
		Duration configuredTimeToLive = properties.getPreparedDownloadTtl();
		this.timeToLive = configuredTimeToLive == null
				|| configuredTimeToLive.isZero()
				|| configuredTimeToLive.isNegative()
						? DEFAULT_TIME_TO_LIVE
						: configuredTimeToLive;
	}

	public String put(DownloadArchive archive, String sessionId) {
		String token = UUID.randomUUID().toString();
		StoredDownload storedDownload = new StoredDownload(archive, sessionId, Instant.now().plus(timeToLive));
		downloads.put(token, storedDownload);
		cleanupExecutor.schedule(() -> discard(token), timeToLive.toMillis(), TimeUnit.MILLISECONDS);
		return token;
	}

	public Optional<DownloadArchive> take(String token, String sessionId) {
		StoredDownload storedDownload = downloads.get(token);
		if (storedDownload == null || !storedDownload.sessionId().equals(sessionId)) {
			return Optional.empty();
		}
		if (!downloads.remove(token, storedDownload)) {
			return Optional.empty();
		}
		if (storedDownload.expiresAt().isBefore(Instant.now())) {
			cleanup(storedDownload.archive());
			return Optional.empty();
		}
		return Optional.of(storedDownload.archive());
	}

	@PreDestroy
	void close() {
		cleanupExecutor.shutdownNow();
		downloads.values().forEach(storedDownload -> cleanup(storedDownload.archive()));
		downloads.clear();
	}

	private void discard(String token) {
		StoredDownload storedDownload = downloads.remove(token);
		if (storedDownload != null) {
			cleanup(storedDownload.archive());
		}
	}

	private void cleanup(DownloadArchive archive) {
		if (archive.resource() instanceof AutoDeletingPathResource resource) {
			resource.deleteNow();
		}
	}

	private record StoredDownload(DownloadArchive archive, String sessionId, Instant expiresAt) {
	}
}
