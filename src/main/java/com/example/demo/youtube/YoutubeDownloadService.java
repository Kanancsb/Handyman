package com.example.demo.youtube;

public interface YoutubeDownloadService {

	DownloadArchive download(String youtubeUrl, ArchiveFormat format);
}
