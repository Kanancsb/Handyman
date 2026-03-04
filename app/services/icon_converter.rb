require "open3"
require "tmpdir"

class IconConverter
  class ValidationError < StandardError; end
  class ConversionError < StandardError; end

  Result = Struct.new(:content, :filename, :mime_type, keyword_init: true)

  JPG_EXTENSIONS = %w[jpg jpeg].freeze
  SOURCE_EXTENSIONS = (JPG_EXTENSIONS + %w[png]).freeze

  PRESETS = {
    "ico" => {
      extension: "ico",
      mime_type: "image/x-icon",
      mode: "ico",
      size: nil,
      format: nil,
      suffix: "icon"
    },
    "instagram_png" => {
      extension: "png",
      mime_type: "image/png",
      mode: "square",
      size: 320,
      format: "PNG",
      suffix: "instagram_profile"
    },
    "instagram_jpg" => {
      extension: "jpg",
      mime_type: "image/jpeg",
      mode: "square",
      size: 320,
      format: "JPEG",
      suffix: "instagram_profile"
    },
    "twitter_png" => {
      extension: "png",
      mime_type: "image/png",
      mode: "square",
      size: 400,
      format: "PNG",
      suffix: "twitter_profile"
    },
    "twitter_jpg" => {
      extension: "jpg",
      mime_type: "image/jpeg",
      mode: "square",
      size: 400,
      format: "JPEG",
      suffix: "twitter_profile"
    }
  }.freeze

  def initialize(uploaded_file:, target_preset:)
    @uploaded_file = uploaded_file
    @target_preset = target_preset.to_s
  end

  def call
    validate_request!
    preset = PRESETS.fetch(target_preset)

    Dir.mktmpdir("icon-convert-") do |dir|
      input_path = persist_upload(dir)
      output_path = File.join(dir, "converted.#{preset[:extension]}")

      convert_image(input_path, output_path, preset)

      Result.new(
        content: File.binread(output_path),
        filename: "#{output_basename}_#{preset[:suffix]}.#{preset[:extension]}",
        mime_type: preset[:mime_type]
      )
    end
  end

  private

  attr_reader :uploaded_file, :target_preset

  def validate_request!
    raise ValidationError, "Please upload an image file." unless uploaded_file&.respond_to?(:tempfile)
    raise ValidationError, "Uploaded file has no extension." if input_extension.empty?

    unless SOURCE_EXTENSIONS.include?(input_extension)
      raise ValidationError, "Only JPG and PNG files are supported for icon conversion."
    end

    return if PRESETS.key?(target_preset)

    raise ValidationError, "Select a target preset."
  end

  def persist_upload(dir)
    path = File.join(dir, "input.#{input_extension}")
    tempfile = uploaded_file.tempfile
    tempfile.rewind if tempfile.respond_to?(:rewind)
    File.binwrite(path, tempfile.read)
    tempfile.rewind if tempfile.respond_to?(:rewind)
    path
  end

  def convert_image(input_path, output_path, preset)
    run_python_image_script(
      input_path,
      output_path,
      preset[:mode],
      preset[:size],
      preset[:format]
    )
    ensure_file_exists!(output_path)
  end

  def run_python_image_script(input_path, output_path, mode, size, output_format)
    script = <<~PYTHON
      from PIL import Image, ImageOps
      import sys

      input_path, output_path, mode, size_arg, output_format = sys.argv[1:6]

      try:
          resample = Image.Resampling.LANCZOS
      except AttributeError:
          resample = Image.LANCZOS

      with Image.open(input_path) as img:
          if mode == "ico":
              fitted = ImageOps.fit(img.convert("RGBA"), (512, 512), method=resample)
              fitted.save(
                  output_path,
                  format="ICO",
                  sizes=[(32, 32), (48, 48), (64, 64), (128, 128), (180, 180), (256, 256), (512, 512)],
              )
          elif mode == "square":
              size = int(size_arg)
              fitted = ImageOps.fit(img, (size, size), method=resample)

              if output_format == "JPEG":
                  if fitted.mode not in ("RGB", "L"):
                      if "A" in fitted.mode:
                          rgba = fitted.convert("RGBA")
                          background = Image.new("RGB", rgba.size, (255, 255, 255))
                          background.paste(rgba, mask=rgba.split()[-1])
                          fitted = background
                      else:
                          fitted = fitted.convert("RGB")

                  fitted.save(output_path, "JPEG", quality=95)
              else:
                  fitted.save(output_path, "PNG")
          else:
              raise ValueError("Unsupported mode")
    PYTHON

    run_command!(
      "python3",
      "-c",
      script,
      input_path,
      output_path,
      mode,
      size.to_s,
      output_format.to_s
    )
  end

  def run_command!(*command)
    stdout, stderr, status = Open3.capture3(*command)
    return if status.success?

    message = [stdout, stderr].reject(&:empty?).join("\n")
    raise ConversionError, message.presence || "Command failed: #{command.join(' ')}"
  rescue Errno::ENOENT
    raise ConversionError, "Required tool is missing: #{command.first}"
  end

  def ensure_file_exists!(path)
    return path if File.exist?(path) && File.size(path).positive?

    raise ConversionError, "Expected output file was not generated."
  end

  def output_basename
    base = File.basename(uploaded_file.original_filename.to_s, ".*")
    cleaned = base.gsub(/[^0-9A-Za-z._-]+/, "_").gsub(/\A[._-]+/, "")
    cleaned.present? ? cleaned : "converted_icon"
  end

  def input_extension
    @input_extension ||= File.extname(uploaded_file.original_filename.to_s).delete(".").downcase
  end
end
