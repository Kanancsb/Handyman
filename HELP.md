# Handyman

Handyman is a Spring Boot Web MVC application that creates MP3, WAV, or MP4 zip archives from YouTube links. The YouTube page can also read a Spotify playlist, search YouTube for each track, and let the user review the matches before downloading.

Only download content you own or have permission to use.

## Requirements

- Java 25
- `yt-dlp`
- `ffmpeg`
- `node` when `handyman.youtube.downloader.js-runtime=node`

## Spotify setup

Create an application in the Spotify Developer Dashboard and register this redirect URI:

```text
http://127.0.0.1:8080/api/spotify/callback
```

Then provide the app credentials to the Spring process:

```bash
export SPOTIFY_CLIENT_ID="your-client-id"
export SPOTIFY_CLIENT_SECRET="your-client-secret"
```

For a deployment with a different public origin, register its callback in Spotify and set `SPOTIFY_REDIRECT_URI` to the same value. The client secret is used only by the backend. Spotify access and refresh tokens are stored in the user's HTTP session.

Spotify currently allows the playlist-items endpoint for playlists owned by the connected user or playlists where that user is a collaborator. The app reads Spotify metadata only; it does not download Spotify audio.

Spotify Development Mode currently requires the app owner to have Spotify Premium and limits new apps to five authorized users. Add each person who will connect to the app in the Spotify Developer Dashboard.

## Run

```bash
./mvnw test
./mvnw spring-boot:run
```

Open `http://127.0.0.1:8080/youtube`. Spotify does not permit `localhost` OAuth redirect aliases; the explicit loopback address keeps the browser session on the same host as the callback.

The Spotify importer searches up to three YouTube candidates per track, ranks them using title, artist, and duration, and limits an import to 200 tracks. These values can be changed in `src/main/resources/application.properties`.

## Download diagnostics

Archive jobs are logged to `logs/handyman.log` by default. A failed request shows a diagnostic ID and the bounded tail of the `yt-dlp` output on the page; the same ID appears in the server log with the source type, format, input count, elapsed time, and failure reason. Set `HANDYMAN_LOG_FILE` to use a different log path.

The server prepares each archive before returning a short-lived, one-use download URL. The browser then streams the zip directly through its native download handling instead of keeping the whole archive in JavaScript memory. Prepared archives expire after 30 minutes by default; change `handyman.youtube.downloader.prepared-download-ttl` if needed.
