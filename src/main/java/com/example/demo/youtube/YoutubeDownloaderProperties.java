package com.example.demo.youtube;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "handyman.youtube.downloader")
public class YoutubeDownloaderProperties {

	private String binary = "yt-dlp";

	private String jsRuntime = "node";

	private Duration timeout = Duration.ofMinutes(30);

	public String getBinary() {
		return binary;
	}

	public void setBinary(String binary) {
		this.binary = binary;
	}

	public String getJsRuntime() {
		return jsRuntime;
	}

	public void setJsRuntime(String jsRuntime) {
		this.jsRuntime = jsRuntime;
	}

	public Duration getTimeout() {
		return timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}
}
