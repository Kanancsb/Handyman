const form = document.querySelector("#download-form");
const urlInput = document.querySelector("#youtube-url");
const message = document.querySelector("#format-error");
const statusText = document.querySelector("#download-status");
const submitButton = form.querySelector("button[type='submit']");

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

form.addEventListener("submit", (event) => {
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
	statusText.textContent = formats.length === 1 ? "Starting 1 archive" : `Starting ${formats.length} archives`;

	formats.forEach((format, index) => {
		window.setTimeout(() => startDownload(url, format), index * 450);
	});

	window.setTimeout(() => {
		submitButton.disabled = false;
		statusText.textContent = "Ready";
	}, Math.max(1800, formats.length * 650));

	message.textContent = formats.length === 1 ? "Download started." : "Downloads started.";
});

function startDownload(url, format) {
	const frame = document.createElement("iframe");
	frame.hidden = true;
	frame.src = `/api/youtube/download?url=${encodeURIComponent(url)}&format=${encodeURIComponent(format)}`;
	document.body.appendChild(frame);
	window.setTimeout(() => frame.remove(), 120000);
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
	statusText.textContent = "Check form";
}

function clearMessage() {
	message.textContent = "";
	message.classList.remove("error");
}
