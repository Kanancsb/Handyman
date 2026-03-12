class YoutubeConverterController < ApplicationController
  DOWNLOAD_OPTIONS = [
    ["Download Video", "video"],
    ["Download Mp3", "mp3"],
    ["Download .Wav", "wav"]
  ].freeze

  def new; end

  def create
    job_id = YoutubeConverter.create_job!(
      video_url: params[:video_url],
      download_type: params[:download_type]
    )

    render json: {
      job_id: job_id,
      status_url: youtube_converter_job_path(job_id: job_id),
      download_url: youtube_converter_download_path(job_id: job_id)
    }
  rescue YoutubeConverter::ValidationError, YoutubeConverter::ConversionError => e
    render json: { error: e.message }, status: :unprocessable_entity
  end

  def status
    status_data = YoutubeConverter.status_for(params[:job_id])

    render json: status_data.merge(
      download_url: status_data["download_ready"] ? youtube_converter_download_path(job_id: params[:job_id]) : nil
    )
  end

  def download
    status_data = YoutubeConverter.status_for(params[:job_id])

    unless status_data["download_ready"]
      redirect_to youtube_converter_path, alert: "This YouTube download is not ready yet."
      return
    end

    result_path = YoutubeConverter.result_path(params[:job_id])

    unless result_path&.exist?
      redirect_to youtube_converter_path, alert: "The downloaded file could not be found."
      return
    end

    send_file(
      result_path,
      filename: status_data["filename"],
      type: YoutubeConverter::MIME_TYPES.fetch(result_path.extname.delete(".").downcase, "application/octet-stream"),
      disposition: "attachment"
    )
  rescue YoutubeConverter::ConversionError => e
    redirect_to youtube_converter_path, alert: e.message
  end
end
