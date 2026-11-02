const form = document.querySelector("#download-form");
const urlInput = document.querySelector("#youtube-url");
const message = document.querySelector("#format-error");
const downloadQueue = document.querySelector("#download-queue");
const submitButton = document.querySelector("#download-button");
const sourceButtons = [...document.querySelectorAll("[data-source]")];
const youtubeSource = document.querySelector("#youtube-source");
const spotifySource = document.querySelector("#spotify-source");
const spotifyStatusText = document.querySelector("#spotify-status");
const spotifyConnect = document.querySelector("#spotify-connect");
const spotifyDisconnect = document.querySelector("#spotify-disconnect");
const spotifyTools = document.querySelector("#spotify-tools");
const spotifyUrlInput = document.querySelector("#spotify-url");
const spotifyImportButton = document.querySelector("#spotify-import");
const spotifyResults = document.querySelector("#spotify-results");
const spotifySummary = document.querySelector("#spotify-summary");
const spotifyMatchList = document.querySelector("#spotify-match-list");
const downloadErrorDetails = document.querySelector("#download-error-details");
const downloadErrorLog = document.querySelector("#download-error-log");

let activeSource = "youtube";
let spotifyConnected = false;
let spotifyMatchesLoaded = false;
let downloadInProgress = false;

const formatLabels = new Map([
	["mp3", "MP3"],
	["wav", "WAV"],
	["video", "Video"]
]);

const youtubeHosts = new Set([
	"youtube.com",
	"www.youtube.com",
	"m.youtube.com",
	"music.youtube.com",
	"youtu.be",
	"www.youtu.be",
	"youtube-nocookie.com",
	"www.youtube-nocookie.com"
]);

sourceButtons.forEach((button) => {
	button.addEventListener("click", () => setSource(button.dataset.source));
});

spotifyImportButton.addEventListener("click", importSpotifyPlaylist);
spotifyDisconnect.addEventListener("click", disconnectSpotify);

form.addEventListener("submit", async (event) => {
	event.preventDefault();
	clearMessage();

	const formats = selectedFormats();
	if (formats.length === 0) {
		showError("Select at least one archive type.");
		return;
	}

	let downloadStarter;
	if (activeSource === "youtube") {
		const url = urlInput.value.trim();
		if (!isYoutubeUrl(url)) {
			showError("Enter a valid YouTube URL.");
			return;
		}
		downloadStarter = (format, timer) => startDownload(url, format, timer);
	}
	else {
		const urls = selectedSpotifyMatches();
		if (!spotifyMatchesLoaded || urls.length === 0) {
			showError("Find a playlist and select at least one YouTube match.");
			return;
		}
		downloadStarter = (format, timer) => startBatchDownload(urls, format, timer);
	}

	downloadInProgress = true;
	updateSubmitState();
	downloadQueue.hidden = false;
	downloadQueue.replaceChildren();
	message.textContent = formats.length === 1 ? "Preparing 1 archive." : `Preparing ${formats.length} archives.`;

	const downloads = formats.map((format) => downloadStarter(format, createDownloadTimer(format)));
	const results = await Promise.allSettled(downloads);
	const failures = results.filter((result) => result.status === "rejected");

	downloadInProgress = false;
	updateSubmitState();
	if (failures.length > 0) {
		showDownloadFailure(
			failures[0].reason,
			failures.length === 1 ? "One archive failed." : `${failures.length} archives failed.`
		);
	}
	else {
		message.textContent = formats.length === 1 ? "Download ready." : "Downloads ready.";
	}
});

async function startDownload(url, format, timer) {
	return prepareArchive([url], format, timer);
}

async function startBatchDownload(urls, format, timer) {
	return prepareArchive(urls, format, timer);
}

async function prepareArchive(urls, format, timer) {
	timer.start();
	try {
		const response = await fetch("/api/youtube/prepare", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ urls, format })
		});
		if (!response.ok) {
			const failure = await archiveResponseError(response, `Download failed with status ${response.status}.`);
			const error = new Error(failure.detail);
			error.diagnosticId = failure.diagnosticId;
			error.downloadLog = failure.log;
			throw error;
		}

		const preparedDownload = await response.json();
		triggerNativeDownload(preparedDownload.downloadUrl, preparedDownload.fileName);
		timer.complete();
	}
	catch (error) {
		timer.fail();
		if (error instanceof TypeError && error.message === "Failed to fetch") {
			throw new Error("The connection to Handyman was lost while preparing the archive. Check the server log.");
		}
		throw error;
	}
}

async function loadSpotifyStatus() {
	try {
		const response = await fetch("/api/spotify/status");
		if (!response.ok) {
			throw new Error("Spotify status is unavailable.");
		}
		const status = await response.json();
		updateSpotifyConnection(status.configured, status.connected);
	}
	catch (error) {
		spotifyStatusText.textContent = error.message;
		updateSpotifyConnection(false, false);
	}

	const result = new URLSearchParams(window.location.search).get("spotify");
	if (result === "connected") {
		setSource("spotify");
		message.textContent = "Spotify connected. Paste a playlist link to find matches.";
	}
	else if (result === "error") {
		setSource("spotify");
		showError("Spotify connection failed or was cancelled. Try connecting again.");
	}
	if (result) {
		window.history.replaceState({}, "", "/youtube");
	}
}

function updateSpotifyConnection(configured, connected) {
	spotifyConnected = configured && connected;
	spotifyConnect.hidden = !configured || spotifyConnected;
	spotifyDisconnect.hidden = !spotifyConnected;
	spotifyTools.hidden = !spotifyConnected;

	if (!configured) {
		spotifyStatusText.textContent = "Not configured. Set SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET on the server.";
	}
	else if (spotifyConnected) {
		spotifyStatusText.textContent = "Connected. Playlist metadata is read from Spotify; audio is downloaded only from confirmed YouTube matches.";
	}
	else {
		spotifyStatusText.textContent = "Connect your Spotify account to read one of your playlists.";
	}
	updateSubmitState();
}

async function importSpotifyPlaylist() {
	clearMessage();
	const playlistUrl = spotifyUrlInput.value.trim();
	if (!isSpotifyPlaylistUrl(playlistUrl)) {
		showError("Enter a valid open.spotify.com playlist URL.");
		return;
	}

	spotifyImportButton.disabled = true;
	spotifyImportButton.textContent = "Finding…";
	spotifyMatchesLoaded = false;
	spotifyResults.hidden = true;
	updateSubmitState();
	message.textContent = "Reading Spotify tracks and searching YouTube. This can take a few minutes for a large playlist.";

	try {
		const response = await fetch("/api/spotify/import", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ url: playlistUrl })
		});
		if (!response.ok) {
			if (response.status === 401) {
				updateSpotifyConnection(true, false);
			}
			throw new Error(await responseError(response, "Could not import the Spotify playlist."));
		}

		const result = await response.json();
		renderSpotifyMatches(result);
		message.textContent = "Review the matches, then choose an archive type and start the download.";
	}
	catch (error) {
		showError(error.message);
	}
	finally {
		spotifyImportButton.disabled = false;
		spotifyImportButton.textContent = "Find matches";
		updateSubmitState();
	}
}

async function disconnectSpotify() {
	try {
		await fetch("/api/spotify/disconnect", { method: "POST" });
	}
	finally {
		spotifyMatchesLoaded = false;
		spotifyResults.hidden = true;
		spotifyMatchList.replaceChildren();
		updateSpotifyConnection(true, false);
	}
}

function renderSpotifyMatches(result) {
	spotifyMatchList.replaceChildren();
	let matchedCount = 0;

	for (const match of result.matches || []) {
		const row = document.createElement("div");
		row.className = "spotify-match";

		const toggle = document.createElement("input");
		toggle.type = "checkbox";
		toggle.className = "match-toggle";
		toggle.checked = match.candidates.length > 0;
		toggle.disabled = match.candidates.length === 0;
		toggle.setAttribute("aria-label", `Include ${match.track.name}`);

		const trackCopy = document.createElement("div");
		trackCopy.className = "track-copy";
		const trackName = document.createElement("strong");
		trackName.textContent = match.track.name;
		const artists = document.createElement("small");
		artists.textContent = match.track.artists.join(", ");
		trackCopy.append(trackName, artists);
		if (isSpotifyTrackUrl(match.track.spotifyUrl)) {
			const spotifyLink = document.createElement("a");
			spotifyLink.className = "spotify-track-link";
			spotifyLink.href = match.track.spotifyUrl;
			spotifyLink.target = "_blank";
			spotifyLink.rel = "noopener noreferrer";
			spotifyLink.textContent = "Open in Spotify ↗";
			trackCopy.appendChild(spotifyLink);
		}

		const candidateControl = match.candidates.length > 0
			? candidateSelect(match.candidates, match.track.name)
			: noCandidateMessage();
		if (match.candidates.length > 0) {
			matchedCount += 1;
		}

		row.append(toggle, trackCopy, candidateControl);
		spotifyMatchList.appendChild(row);
	}

	const shownCount = result.matches?.length || 0;
	spotifySummary.textContent = `${matchedCount} of ${shownCount} importable tracks have candidates. `
		+ `${shownCount} tracks were read from ${result.totalTracks} playlist items.`
		+ (result.truncated ? " The importer limit was reached, so some playlist items were not checked." : "");
	spotifyMatchesLoaded = matchedCount > 0;
	spotifyResults.hidden = false;
	updateSubmitState();
}

function candidateSelect(candidates, trackName) {
	const select = document.createElement("select");
	select.className = "candidate-select";
	select.setAttribute("aria-label", `YouTube match for ${trackName}`);
	for (const candidate of candidates) {
		const option = document.createElement("option");
		option.value = candidate.url;
		const duration = candidate.durationSeconds > 0 ? ` · ${formatCandidateTime(candidate.durationSeconds)}` : "";
		option.textContent = `${candidate.title} — ${candidate.channel}${duration}`;
		select.appendChild(option);
	}
	return select;
}

function noCandidateMessage() {
	const text = document.createElement("span");
	text.className = "no-candidate";
	text.textContent = "No YouTube result found";
	return text;
}

function selectedSpotifyMatches() {
	return [...spotifyMatchList.querySelectorAll(".spotify-match")]
		.filter((row) => row.querySelector(".match-toggle")?.checked)
		.map((row) => row.querySelector(".candidate-select")?.value)
		.filter(Boolean);
}

function setSource(source) {
	activeSource = source === "spotify" ? "spotify" : "youtube";
	youtubeSource.hidden = activeSource !== "youtube";
	spotifySource.hidden = activeSource !== "spotify";
	for (const button of sourceButtons) {
		button.setAttribute("aria-pressed", String(button.dataset.source === activeSource));
	}
	clearMessage();
	updateSubmitState();
}

function updateSubmitState() {
	const spotifyUnavailable = activeSource === "spotify" && (!spotifyConnected || !spotifyMatchesLoaded);
	submitButton.disabled = downloadInProgress || spotifyUnavailable;
	submitButton.textContent = activeSource === "spotify" ? "Download selected matches" : "Start download";
}

function selectedFormats() {
	return [...form.querySelectorAll("input[name='format']:checked")].map((input) => input.value);
}

function createDownloadTimer(format) {
	const row = document.createElement("div");
	row.className = "download-timer";
	row.innerHTML = `
		<span class="timer-label">${formatLabels.get(format) || format}</span>
		<span class="timer-track" aria-hidden="true"><span class="timer-fill"></span></span>
		<span class="timer-value">0m 00s</span>
	`;
	downloadQueue.appendChild(row);

	const value = row.querySelector(".timer-value");
	let timerId;
	let startedAt;

	return {
		start() {
			startedAt = Date.now();
			row.classList.add("is-running");
			timerId = window.setInterval(() => {
				value.textContent = formatDownloadTime(Date.now() - startedAt);
			}, 1000);
		},
		complete() {
			window.clearInterval(timerId);
			value.textContent = formatDownloadTime(Date.now() - startedAt);
			row.classList.remove("is-running");
			row.classList.add("is-complete");
		},
		fail() {
			window.clearInterval(timerId);
			value.textContent = formatDownloadTime(Date.now() - startedAt);
			row.classList.remove("is-running");
			row.classList.add("is-error");
		}
	};
}

function triggerNativeDownload(downloadUrl, fileName) {
	const link = document.createElement("a");
	link.href = downloadUrl;
	link.download = fileName;
	document.body.appendChild(link);
	link.click();
	link.remove();
}

function formatDownloadTime(milliseconds) {
	const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
	const minutes = Math.floor(totalSeconds / 60);
	const seconds = String(totalSeconds % 60).padStart(2, "0");
	return `${minutes}m ${seconds}s`;
}

function formatCandidateTime(totalSeconds) {
	const minutes = Math.floor(totalSeconds / 60);
	const seconds = String(totalSeconds % 60).padStart(2, "0");
	return `${minutes}:${seconds}`;
}

function isYoutubeUrl(value) {
	try {
		const parsed = new URL(value);
		return ["http:", "https:"].includes(parsed.protocol) && youtubeHosts.has(parsed.hostname.toLowerCase());
	}
	catch (error) {
		return false;
	}
}

function isSpotifyPlaylistUrl(value) {
	try {
		const parsed = new URL(value);
		const parts = parsed.pathname.split("/").filter(Boolean);
		const playlistIndex = parts.length === 2 ? 0 : parts.length === 3 && parts[0].startsWith("intl-") ? 1 : -1;
		return parsed.protocol === "https:"
			&& ["open.spotify.com", "www.open.spotify.com"].includes(parsed.hostname.toLowerCase())
			&& playlistIndex >= 0
			&& parts[playlistIndex] === "playlist"
			&& /^[A-Za-z0-9]{10,64}$/.test(parts[playlistIndex + 1]);
	}
	catch (error) {
		return false;
	}
}

function isSpotifyTrackUrl(value) {
	try {
		const parsed = new URL(value);
		return parsed.protocol === "https:"
			&& parsed.hostname.toLowerCase() === "open.spotify.com"
			&& parsed.pathname.split("/").filter(Boolean).includes("track");
	}
	catch (error) {
		return false;
	}
}

async function responseError(response, fallback) {
	try {
		const body = await response.json();
		return body.detail || body.message || fallback;
	}
	catch (error) {
		return fallback;
	}
}

async function archiveResponseError(response, fallback) {
	try {
		const body = await response.json();
		return {
			detail: body.detail || body.message || fallback,
			diagnosticId: body.diagnosticId || null,
			log: body.log || null
		};
	}
	catch (error) {
		return { detail: fallback, diagnosticId: null, log: null };
	}
}

function showDownloadFailure(error, fallback) {
	const detail = error?.message || fallback;
	const diagnosticId = error?.diagnosticId;
	showError(diagnosticId ? `${detail} Diagnostic ID: ${diagnosticId}.` : detail);

	const logSections = [];
	if (diagnosticId) {
		logSections.push(`Diagnostic ID: ${diagnosticId}`);
	}
	if (error?.downloadLog) {
		logSections.push(`yt-dlp output tail:\n${error.downloadLog}`);
	}
	if (logSections.length > 0) {
		downloadErrorLog.textContent = logSections.join("\n\n");
		downloadErrorDetails.hidden = false;
		downloadErrorDetails.open = true;
	}
}

function showError(text) {
	message.textContent = text;
	message.classList.add("error");
}

function clearMessage() {
	message.textContent = "";
	message.classList.remove("error");
	downloadErrorDetails.hidden = true;
	downloadErrorDetails.open = false;
	downloadErrorLog.textContent = "";
}

loadSpotifyStatus();
