package com.example.demo.youtube;

import java.util.List;

public interface YoutubeDownloadService {

	DownloadArchive download(String youtubeUrl, ArchiveFormat format);

	default DownloadArchive download(List<String> youtubeUrls, ArchiveFormat format) {
		if (youtubeUrls.size() != 1) {
			throw new IllegalArgumentException("This download service does not support URL batches.");
		}
		return download(youtubeUrls.getFirst(), format);
	}
}
