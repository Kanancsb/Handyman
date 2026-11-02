package com.example.demo.youtube;

public record YoutubeSearchCandidate(String url, String title, String channel, long durationSeconds, int score) {
}
