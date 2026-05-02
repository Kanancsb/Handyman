package com.example.demo.youtube;

import org.springframework.core.io.Resource;

public record DownloadArchive(String fileName, Resource resource, long contentLength) {
}
