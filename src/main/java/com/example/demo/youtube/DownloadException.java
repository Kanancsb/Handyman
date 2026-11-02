package com.example.demo.youtube;

public class DownloadException extends RuntimeException {

	private final String diagnosticId;

	private final String diagnosticDetails;

	public DownloadException(String message) {
		this(message, null, null, null);
	}

	public DownloadException(String message, Throwable cause) {
		this(message, null, null, cause);
	}

	public DownloadException(String message, String diagnosticDetails) {
		this(message, null, diagnosticDetails, null);
	}

	public DownloadException(String message, String diagnosticDetails, Throwable cause) {
		this(message, null, diagnosticDetails, cause);
	}

	private DownloadException(String message, String diagnosticId, String diagnosticDetails, Throwable cause) {
		super(message, cause);
		this.diagnosticId = diagnosticId;
		this.diagnosticDetails = diagnosticDetails;
	}

	public String diagnosticId() {
		return diagnosticId;
	}

	public String diagnosticDetails() {
		return diagnosticDetails;
	}

	public DownloadException withDiagnosticId(String value) {
		return new DownloadException(getMessage(), value, diagnosticDetails, this);
	}
}
