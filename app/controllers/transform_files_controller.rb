class TransformFilesController < ApplicationController
  SOURCE_OPTIONS = [
    ["Word (.doc/.docx/.odt/.rtf)", "word"],
    ["PDF (.pdf)", "pdf"],
    ["JPG (.jpg/.jpeg)", "jpg"],
    ["PNG (.png)", "png"]
  ].freeze

  TARGET_OPTIONS = [
    ["PDF (.pdf)", "pdf"],
    ["PNG (.png)", "png"],
    ["JPG (.jpg)", "jpg"],
    ["ICO (.ico)", "ico"]
  ].freeze

  def new; end

  def create
    result = FileTransformer.new(
      uploaded_file: params[:file],
      source_format: params[:source_format],
      target_format: params[:target_format]
    ).call

    send_data(
      result.content,
      filename: result.filename,
      type: result.mime_type,
      disposition: "attachment"
    )
  rescue FileTransformer::ValidationError, FileTransformer::UnsupportedConversionError => e
    redirect_to transform_files_path, alert: e.message
  rescue FileTransformer::ConversionError => e
    Rails.logger.error("File conversion failed: #{e.message}")
    redirect_to transform_files_path,
                alert: "The conversion failed. Try another file or format combination."
  end
end
