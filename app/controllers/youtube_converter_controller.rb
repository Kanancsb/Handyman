class YoutubeConverterController < ApplicationController
  DOWNLOAD_OPTIONS = [
    ["Download Video", "video"],
    ["Download Mp3", "mp3"],
    ["Download .Wav", "wav"]
  ].freeze

  def new; end

  def create
    result = YoutubeConverter.new(
      video_url: params[:video_url],
      download_type: params[:download_type]
    ).call

    send_data(
      result.content,
      filename: result.filename,
      type: result.mime_type,
      disposition: "attachment"
    )
  rescue YoutubeConverter::ValidationError => e
    redirect_to youtube_converter_path, alert: e.message
  rescue YoutubeConverter::ConversionError => e
    Rails.logger.error("YouTube conversion failed: #{e.message}")
    redirect_to youtube_converter_path, alert: e.message
  end
end
