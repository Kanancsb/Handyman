package com.example.demo.youtube;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/youtube")
public class YoutubeDownloadController {

	private final YoutubeDownloadService downloadService;

	private final YoutubeUrlValidator urlValidator;

	public YoutubeDownloadController(YoutubeDownloadService downloadService, YoutubeUrlValidator urlValidator) {
		this.downloadService = downloadService;
		this.urlValidator = urlValidator;
	}

	@GetMapping(value = "/download", produces = "application/zip")
	public ResponseEntity<Resource> download(@RequestParam String url, @RequestParam String format) {
		ArchiveFormat archiveFormat = ArchiveFormat.fromRequestValue(format)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported archive type."));

		if (!urlValidator.isSupported(url)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid YouTube URL.");
		}

		try {
			DownloadArchive archive = downloadService.download(urlValidator.normalize(url), archiveFormat);
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("application/zip"))
					.contentLength(archive.contentLength())
					.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
							.filename(archive.fileName())
							.build()
							.toString())
					.body(archive.resource());
		}
		catch (DownloadException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
		}
	}
}
