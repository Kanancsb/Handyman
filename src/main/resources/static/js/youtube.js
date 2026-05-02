const form = document.querySelector("#download-form");
const urlInput = document.querySelector("#youtube-url");
const message = document.querySelector("#format-error");
const downloadQueue = document.querySelector("#download-queue");
const submitButton = form.querySelector("button[type='submit']");

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

form.addEventListener("submit", async (event) => {
	event.preventDefault();
	clearMessage();

	const url = urlInput.value.trim();
	const formats = [...form.querySelectorAll("input[name='format']:checked")].map((input) => input.value);

	if (!isYoutubeUrl(url)) {
		showError("Enter a valid YouTube URL.");
		return;
	}

	if (formats.length === 0) {
		showError("Select at least one archive type.");
		return;
	}

	submitButton.disabled = true;
	downloadQueue.hidden = false;
	downloadQueue.replaceChildren();
	message.textContent = formats.length === 1 ? "Preparing 1 archive." : `Preparing ${formats.length} archives.`;

	const downloads = formats.map((format) => startDownload(url, format, createDownloadTimer(format)));
	const results = await Promise.allSettled(downloads);
	const failedCount = results.filter((result) => result.status === "rejected").length;

	submitButton.disabled = false;
	if (failedCount > 0) {
		showError(failedCount === 1 ? "One archive failed." : `${failedCount} archives failed.`);
	}
	else {
		message.textContent = formats.length === 1 ? "Download ready." : "Downloads ready.";
	}
});

async function startDownload(url, format, timer) {
	timer.start();
	try {
		const response = await fetch(`/api/youtube/download?url=${encodeURIComponent(url)}&format=${encodeURIComponent(format)}`);
		if (!response.ok) {
			throw new Error(`Download failed with status ${response.status}.`);
		}

		const blob = await response.blob();
		const fileName = fileNameFromDisposition(response.headers.get("content-disposition"))
			|| `youtube-${format}-download.zip`;
		triggerBrowserDownload(blob, fileName);
		timer.complete();
	}
	catch (error) {
		timer.fail();
		throw error;
	}
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

function triggerBrowserDownload(blob, fileName) {
	const link = document.createElement("a");
	const objectUrl = URL.createObjectURL(blob);
	link.href = objectUrl;
	link.download = fileName;
	document.body.appendChild(link);
	link.click();
	link.remove();
	window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

function fileNameFromDisposition(header) {
	if (!header) {
		return null;
	}

	const match = /filename\*?=(?:UTF-8''|")?([^";]+)/i.exec(header);
	if (!match) {
		return null;
	}

	return decodeURIComponent(match[1].replaceAll("\"", ""));
}

function formatDownloadTime(milliseconds) {
	const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
	const minutes = Math.floor(totalSeconds / 60);
	const seconds = String(totalSeconds % 60).padStart(2, "0");
	return `${minutes}m ${seconds}s`;
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

function showError(text) {
	message.textContent = text;
	message.classList.add("error");
}

function clearMessage() {
	message.textContent = "";
	message.classList.remove("error");
}
