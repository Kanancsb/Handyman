package com.example.demo.youtube;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/youtube")
public class YoutubeDownloadController {

	private static final int MAX_BATCH_SIZE = 200;

	private final YoutubeDownloadService downloadService;

	private final YoutubeUrlValidator urlValidator;

	private final PreparedDownloadStore preparedDownloadStore;

	public YoutubeDownloadController(YoutubeDownloadService downloadService, YoutubeUrlValidator urlValidator,
			PreparedDownloadStore preparedDownloadStore) {
		this.downloadService = downloadService;
		this.urlValidator = urlValidator;
		this.preparedDownloadStore = preparedDownloadStore;
	}

	@PostMapping(value = "/prepare", consumes = "application/json", produces = "application/json")
	public PreparedDownload prepare(@RequestBody PrepareDownloadRequest request, HttpSession session) {
		ArchiveFormat archiveFormat = ArchiveFormat.fromRequestValue(request.format())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported archive type."));
		List<String> urls = validatedUrls(request.urls());
		DownloadArchive archive = downloadService.download(urls, archiveFormat);
		String token = preparedDownloadStore.put(archive, session.getId());
		return new PreparedDownload(
				"/api/youtube/prepared/" + token,
				archive.fileName(),
				archive.contentLength());
	}

	@GetMapping(value = "/prepared/{token}", produces = "application/zip")
	public ResponseEntity<Resource> preparedDownload(@PathVariable String token, HttpSession session) {
		DownloadArchive archive = preparedDownloadStore.take(token, session.getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"This prepared archive expired or was already downloaded."));
		return archiveResponse(archive);
	}

	@GetMapping(value = "/download", produces = "application/zip")
	public ResponseEntity<Resource> download(@RequestParam String url, @RequestParam String format) {
		ArchiveFormat archiveFormat = ArchiveFormat.fromRequestValue(format)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported archive type."));

		if (!urlValidator.isSupported(url)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid YouTube URL.");
		}

		return archiveResponse(() -> downloadService.download(urlValidator.normalize(url), archiveFormat));
	}

	@PostMapping(value = "/download-batch", consumes = "application/json", produces = "application/zip")
	public ResponseEntity<Resource> downloadBatch(@RequestParam String format, @RequestBody YoutubeBatchRequest request) {
		ArchiveFormat archiveFormat = ArchiveFormat.fromRequestValue(format)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported archive type."));

		if (request.urls() == null || request.urls().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one matched YouTube video.");
		}
		if (request.urls().size() > MAX_BATCH_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A download batch can contain at most 200 videos.");
		}
		if (request.urls().stream().anyMatch(url -> !urlValidator.isIndividualVideo(url))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The batch contains an invalid or non-video YouTube URL.");
		}

		var normalizedUrls = request.urls().stream().map(urlValidator::normalize).distinct().toList();
		return archiveResponse(() -> downloadService.download(normalizedUrls, archiveFormat));
	}

	private List<String> validatedUrls(List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one YouTube URL.");
		}
		if (urls.size() > MAX_BATCH_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A download can contain at most 200 URLs.");
		}
		if (urls.size() == 1 && !urlValidator.isSupported(urls.getFirst())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid YouTube URL.");
		}
		if (urls.size() > 1 && urls.stream().anyMatch(url -> !urlValidator.isIndividualVideo(url))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"A multi-video download contains an invalid or non-video YouTube URL.");
		}
		return urls.stream().map(urlValidator::normalize).distinct().toList();
	}

	private ResponseEntity<Resource> archiveResponse(ArchiveSupplier supplier) {
		return archiveResponse(supplier.get());
	}

	private ResponseEntity<Resource> archiveResponse(DownloadArchive archive) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/zip"))
				.contentLength(archive.contentLength())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(archive.fileName())
						.build()
						.toString())
				.body(archive.resource());
	}

	@ExceptionHandler(DownloadException.class)
	public ResponseEntity<DownloadFailure> downloadFailure(DownloadException exception) {
		String diagnosticId = StringUtils.hasText(exception.diagnosticId())
				? exception.diagnosticId()
				: UUID.randomUUID().toString().substring(0, 8);
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(new DownloadFailure(
						"YouTube archive failed",
						exception.getMessage(),
						diagnosticId,
						exception.diagnosticDetails()));
	}

	public record YoutubeBatchRequest(java.util.List<String> urls) {
	}

	public record PrepareDownloadRequest(java.util.List<String> urls, String format) {
	}

	public record PreparedDownload(String downloadUrl, String fileName, long contentLength) {
	}

	public record DownloadFailure(String title, String detail, String diagnosticId, String log) {
	}

	@FunctionalInterface
	private interface ArchiveSupplier {

		DownloadArchive get();
	}
}
