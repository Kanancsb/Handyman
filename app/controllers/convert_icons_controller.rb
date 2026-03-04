class ConvertIconsController < ApplicationController
  TARGET_OPTIONS = [
    ["ICO (.ico)", "ico"],
    ["Instagram Profile PNG (320x320)", "instagram_png"],
    ["Instagram Profile JPG (320x320)", "instagram_jpg"],
    ["Twitter/X Profile PNG (400x400)", "twitter_png"],
    ["Twitter/X Profile JPG (400x400)", "twitter_jpg"]
  ].freeze

  def new; end

  def create
    result = IconConverter.new(
      uploaded_file: params[:file],
      target_preset: params[:target_preset]
    ).call

    send_data(
      result.content,
      filename: result.filename,
      type: result.mime_type,
      disposition: "attachment"
    )
  rescue IconConverter::ValidationError => e
    redirect_to convert_icons_path, alert: e.message
  rescue IconConverter::ConversionError => e
    Rails.logger.error("Icon conversion failed: #{e.message}")
    redirect_to convert_icons_path,
                alert: "The icon conversion failed. Try another image or preset."
  end
end
