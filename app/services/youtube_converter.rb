require "open3"
require "tmpdir"
require "uri"

class YoutubeConverter
  class ValidationError < StandardError; end
  class ConversionError < StandardError; end

  Result = Struct.new(:content, :filename, :mime_type, keyword_init: true)

  DOWNLOAD_TYPES = %w[video mp3 wav].freeze
  MIME_TYPES = {
    "mp4" => "video/mp4",
    "webm" => "video/webm",
    "mkv" => "video/x-matroska",
    "mp3" => "audio/mpeg",
    "wav" => "audio/wav",
    "m4a" => "audio/mp4"
  }.freeze

  def initialize(video_url:, download_type:)
    @video_url = normalize_url(video_url)
    @download_type = download_type.to_s
  end

  def call
    validate_request!

    Dir.mktmpdir("youtube-convert-") do |dir|
      output_path = download_to_path(dir)
      extension = File.extname(output_path).delete(".").downcase

      Result.new(
        content: File.binread(output_path),
        filename: "youtube_download.#{extension}",
        mime_type: MIME_TYPES.fetch(extension, "application/octet-stream")
      )
    end
  end

  private

  attr_reader :video_url, :download_type

  def validate_request!
    raise ValidationError, "Please enter a YouTube video link." if video_url.blank?
    raise ValidationError, "Select a download option." unless DOWNLOAD_TYPES.include?(download_type)
    raise ValidationError, "Enter a valid YouTube link." unless youtube_url?

    ensure_tool!("yt-dlp")
    ensure_tool!("ffmpeg") if audio_download?
  end

  def normalize_url(value)
    raw_value = value.to_s.strip
    return "" if raw_value.empty?
    return raw_value if raw_value.match?(/\Ahttps?:\/\//i)

    "https://#{raw_value}"
  end

  def youtube_url?
    uri = URI.parse(video_url)
    host = uri.host.to_s.downcase
    return false if host.empty?

    host == "youtu.be" || host.end_with?(".youtube.com") || host == "youtube.com"
  rescue URI::InvalidURIError
    false
  end

  def audio_download?
    %w[mp3 wav].include?(download_type)
  end

  def ensure_tool!(tool_name)
    _, _, status = Open3.capture3("sh", "-c", "command -v #{tool_name}")
    return if status.success?

    message = if tool_name == "yt-dlp"
                "This feature requires `yt-dlp` to be installed on the server."
              else
                "Audio conversion requires `ffmpeg` to be installed on the server."
              end
    raise ConversionError, message
  end

  def download_to_path(dir)
    output_template = File.join(dir, "download.%(ext)s")
    command = base_command(output_template)
    stdout, stderr, status = Open3.capture3(*command)

    unless status.success?
      raise ConversionError, normalize_download_error(stdout, stderr)
    end

    output_path = final_output_path(stdout, dir)
    return output_path if output_path.present? && File.exist?(output_path) && File.size(output_path).positive?

    raise ConversionError, "Expected output file was not generated."
  end

  def base_command(output_template)
    command = ["yt-dlp", "--no-playlist", "--print", "after_move:filepath", "-o", output_template]

    case download_type
    when "video"
      command + [video_url]
    when "mp3"
      command + ["-x", "--audio-format", "mp3", video_url]
    when "wav"
      command + ["-x", "--audio-format", "wav", video_url]
    else
      raise ValidationError, "Select a download option."
    end
  end

  def final_output_path(stdout, dir)
    printed_paths = stdout.to_s.lines.map(&:strip).reject(&:empty?)
    existing_path = printed_paths.find { |path| File.exist?(path) }
    return existing_path if existing_path.present?

    Dir[File.join(dir, "download.*")].find do |path|
      File.file?(path) && File.size(path).positive? && !path.end_with?(".part", ".ytdl")
    end
  end

  def normalize_download_error(stdout, stderr)
    message = [stdout, stderr].reject(&:empty?).join("\n")

    if message.include?("Signature extraction failed") ||
       message.include?("Precondition check failed") ||
       message.include?("Failed to extract any player response")
      return "Your installed `yt-dlp` is outdated for YouTube. Update `yt-dlp` to the latest release and try again."
    end

    if message.include?("Requested format is not available")
      return "The selected YouTube format could not be downloaded. Update `yt-dlp` and try again."
    end

    if message.include?("Failed to resolve") || message.include?("Temporary failure in name resolution")
      return "The server could not reach YouTube. Check this machine's internet or DNS configuration and try again."
    end

    message.presence || "YouTube download failed."
  end
end
