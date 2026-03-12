require "fileutils"
require "json"
require "open3"
require "securerandom"
require "tmpdir"
require "uri"

class YoutubeConverter
  class ValidationError < StandardError; end
  class ConversionError < StandardError; end

  DOWNLOAD_TYPES = %w[video mp3 wav].freeze
  MIME_TYPES = {
    "mp4" => "video/mp4",
    "webm" => "video/webm",
    "mkv" => "video/x-matroska",
    "mp3" => "audio/mpeg",
    "wav" => "audio/wav",
    "m4a" => "audio/mp4",
    "zip" => "application/zip"
  }.freeze

  attr_reader :video_url, :download_type, :job_id, :printed_paths

  def self.create_job!(video_url:, download_type:)
    converter = new(video_url: video_url, download_type: download_type)
    converter.validate_request!

    job_id = SecureRandom.uuid
    FileUtils.mkdir_p(downloads_dir(job_id))
    write_status(job_id, default_status(video_url: converter.video_url, download_type: converter.download_type))
    YoutubeDownloadJob.perform_later(job_id, converter.video_url, converter.download_type)
    job_id
  end

  def self.default_status(video_url:, download_type:)
    {
      "job_id" => nil,
      "state" => "queued",
      "progress" => 0,
      "eta" => nil,
      "message" => "Waiting to start download...",
      "video_url" => video_url,
      "download_type" => download_type,
      "current_item" => nil,
      "total_items" => nil,
      "filename" => nil,
      "download_ready" => false,
      "error" => nil
    }
  end

  def self.jobs_root
    Rails.root.join("tmp", "youtube_jobs")
  end

  def self.job_dir(job_id)
    jobs_root.join(job_id.to_s)
  end

  def self.downloads_dir(job_id)
    job_dir(job_id).join("downloads")
  end

  def self.status_path(job_id)
    job_dir(job_id).join("status.json")
  end

  def self.result_path(job_id)
    status = status_for(job_id)
    return nil unless status["filename"].present?

    job_dir(job_id).join(status["filename"])
  end

  def self.status_for(job_id)
    path = status_path(job_id)
    return missing_status(job_id) unless path.exist?

    JSON.parse(path.read)
  rescue JSON::ParserError
    missing_status(job_id)
  end

  def self.write_status(job_id, data)
    FileUtils.mkdir_p(job_dir(job_id))
    payload = data.merge("job_id" => job_id)
    status_path(job_id).write(JSON.pretty_generate(payload))
  end

  def self.update_status(job_id, attributes)
    current = status_for(job_id)
    write_status(job_id, current.merge(attributes))
  end

  def self.missing_status(job_id)
    {
      "job_id" => job_id,
      "state" => "missing",
      "progress" => 0,
      "eta" => nil,
      "message" => "Download job not found.",
      "video_url" => nil,
      "download_type" => nil,
      "current_item" => nil,
      "total_items" => nil,
      "filename" => nil,
      "download_ready" => false,
      "error" => "Download job not found."
    }
  end

  def initialize(video_url:, download_type:, job_id: nil)
    @video_url = normalize_url(video_url)
    @download_type = download_type.to_s
    @job_id = job_id
    @printed_paths = []
  end

  def call
    validate_request!
    raise ConversionError, "A job id is required to process this download." if job_id.blank?

    run_download!
  end

  def validate_request!
    raise ValidationError, "Please enter a YouTube video link." if video_url.blank?
    raise ValidationError, "Select a download option." unless DOWNLOAD_TYPES.include?(download_type)
    raise ValidationError, "Enter a valid YouTube link." unless youtube_url?

    ensure_tool!("yt-dlp")
    ensure_tool!("ffmpeg") if audio_download?
    ensure_tool!("zip")
  end

  private

  def run_download!
    YoutubeConverter.update_status(job_id, "state" => "downloading", "message" => "Starting download...", "progress" => 0)

    output_template = File.join(YoutubeConverter.downloads_dir(job_id), "%(title).120B [%(id)s].%(ext)s")
    raw_output = +""

    Open3.popen2e(*base_command(output_template)) do |_stdin, output, wait_thr|
      output.each_line do |line|
        raw_output << line
        handle_output_line(line)
      end

      unless wait_thr.value.success?
        raise ConversionError, normalize_download_error(raw_output)
      end
    end

    YoutubeConverter.update_status(job_id, "state" => "processing", "message" => "Packaging download...", "eta" => nil)
    finalize_result!
  rescue StandardError => e
    YoutubeConverter.update_status(
      job_id,
      "state" => "failed",
      "message" => e.message,
      "error" => e.message,
      "download_ready" => false
    )
    raise
  end

  def handle_output_line(line)
    stripped = line.to_s.strip
    return if stripped.empty?

    if stripped.start_with?("progress:")
      update_progress(stripped)
    elsif stripped.start_with?("item:")
      update_item_progress(stripped)
    elsif stripped.start_with?("after_move:")
      printed_paths << stripped.delete_prefix("after_move:")
    else
      update_progress_from_default_line(stripped)
    end
  end

  def update_progress(line)
    _prefix, percent_text, eta_text = line.split(":", 3)
    item_progress = percent_text.to_s.delete("%").strip.to_f
    progress = overall_progress(item_progress)
    YoutubeConverter.update_status(
      job_id,
      "state" => "downloading",
      "progress" => progress.round(1),
      "eta" => normalize_eta(eta_text),
      "message" => "Downloading..."
    )
  end

  def update_item_progress(line)
    _prefix, current_text, total_text = line.split(":", 3)
    YoutubeConverter.update_status(
      job_id,
      "current_item" => integer_or_nil(current_text),
      "total_items" => integer_or_nil(total_text)
    )
  end

  def update_progress_from_default_line(line)
    return unless line.include?("[download]") && line.match?(/(\d+(?:\.\d+)?)%/)

    percent_match = line.match(/(\d+(?:\.\d+)?)%/)
    eta_match = line.match(/ETA\s+([0-9:]+)/)
    item_progress = percent_match[1].to_f

    YoutubeConverter.update_status(
      job_id,
      "state" => "downloading",
      "progress" => overall_progress(item_progress).round(1),
      "eta" => normalize_eta(eta_match && eta_match[1]),
      "message" => "Downloading..."
    )
  end

  def finalize_result!
    output_files = printed_paths.filter_map do |path|
      clean_path = path.to_s.strip
      clean_path if clean_path.present? && File.exist?(clean_path) && File.size(clean_path).positive?
    end.uniq

    output_files = Dir[File.join(YoutubeConverter.downloads_dir(job_id), "*")].select do |path|
      File.file?(path) && File.size(path).positive?
    end if output_files.empty?

    raise ConversionError, "Expected output file was not generated." if output_files.empty?

    if output_files.one?
      finalize_single_file!(output_files.first)
    else
      finalize_archive!(output_files)
    end
  end

  def finalize_single_file!(source_path)
    extension = File.extname(source_path).delete(".").downcase
    filename = sanitize_filename(File.basename(source_path))
    target_path = YoutubeConverter.job_dir(job_id).join(filename)
    FileUtils.mv(source_path, target_path)

    YoutubeConverter.update_status(
      job_id,
      "state" => "completed",
      "progress" => 100.0,
      "eta" => nil,
      "message" => "Download ready.",
      "filename" => filename,
      "download_ready" => true
    )
  end

  def finalize_archive!(files)
    archive_name = "youtube_collection_#{download_type}.zip"
    archive_path = YoutubeConverter.job_dir(job_id).join(archive_name)

    stdout, stderr, status = Open3.capture3("zip", "-j", archive_path.to_s, *files)

    unless status.success? && archive_path.exist? && archive_path.size.positive?
      raise ConversionError, [stdout, stderr].reject(&:empty?).join("\n").presence || "Failed to package playlist download."
    end

    YoutubeConverter.update_status(
      job_id,
      "state" => "completed",
      "progress" => 100.0,
      "eta" => nil,
      "message" => "Album download ready.",
      "filename" => archive_name,
      "download_ready" => true
    )
  end

  def base_command(output_template)
    command = [
      "yt-dlp",
      "--newline",
      "--yes-playlist",
      "--print",
      "before_dl:item:%(playlist_index)s:%(n_entries)s",
      "--print",
      "after_move:%(filepath)s",
      "--progress-template",
      "download:progress:%(progress._percent_str)s:%(progress._eta_str)s",
      "-o",
      output_template
    ]

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

  def normalize_eta(raw_eta)
    eta = raw_eta.to_s.strip
    return nil if eta.blank? || eta == "NA"

    eta
  end

  def normalize_download_error(message)
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

    host == "youtu.be" || host.end_with?(".youtube.com") || host == "youtube.com" || host.end_with?(".music.youtube.com")
  rescue URI::InvalidURIError
    false
  end

  def audio_download?
    %w[mp3 wav].include?(download_type)
  end

  def ensure_tool!(tool_name)
    _, _, status = Open3.capture3("sh", "-c", "command -v #{tool_name}")
    return if status.success?

    message = case tool_name
              when "yt-dlp"
                "This feature requires `yt-dlp` to be installed on the server."
              when "ffmpeg"
                "Audio conversion requires `ffmpeg` to be installed on the server."
              else
                "This feature requires `#{tool_name}` to be installed on the server."
              end

    raise ConversionError, message
  end

  def integer_or_nil(value)
    text = value.to_s.strip
    return nil if text.blank? || text == "NA"

    Integer(text)
  rescue ArgumentError
    nil
  end

  def sanitize_filename(name)
    cleaned = name.to_s.gsub(/[^0-9A-Za-z._ -]+/, "_").strip
    cleaned = cleaned.gsub(/\A[._ -]+/, "")
    cleaned.present? ? cleaned : "youtube_download"
  end

  def overall_progress(item_progress)
    status = YoutubeConverter.status_for(job_id)
    current_item = status["current_item"].to_i
    total_items = status["total_items"].to_i

    return item_progress if current_item <= 0 || total_items <= 0

    completed_items = [current_item - 1, 0].max
    ((completed_items + (item_progress / 100.0)) / total_items) * 100.0
  end
end
