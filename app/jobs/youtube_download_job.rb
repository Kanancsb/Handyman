class YoutubeDownloadJob < ApplicationJob
  queue_as :default

  def perform(job_id, video_url, download_type)
    YoutubeConverter.new(
      video_url: video_url,
      download_type: download_type,
      job_id: job_id
    ).call
  end
end
