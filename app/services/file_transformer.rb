require "open3"
require "tmpdir"
require "fileutils"

class FileTransformer
  class ValidationError < StandardError; end
  class UnsupportedConversionError < StandardError; end
  class ConversionError < StandardError; end

  Result = Struct.new(:content, :filename, :mime_type, keyword_init: true)

  SOURCE_FORMATS = %w[word pdf jpg png].freeze
  TARGET_FORMATS = %w[pdf png jpg ico].freeze
  WORD_EXTENSIONS = %w[doc docx odt rtf].freeze
  MIME_TYPES = {
    "pdf" => "application/pdf",
    "png" => "image/png",
    "jpg" => "image/jpeg",
    "ico" => "image/x-icon"
  }.freeze

  def initialize(uploaded_file:, source_format:, target_format:)
    @uploaded_file = uploaded_file
    @source_format = source_format.to_s.downcase
    @target_format = target_format.to_s.downcase
  end

  def call
    validate_request!

    Dir.mktmpdir("file-transform-") do |dir|
      input_path = persist_upload(dir)
      output_path = transform(input_path, dir)
      output_extension = File.extname(output_path).delete(".").downcase

      Result.new(
        content: File.binread(output_path),
        filename: "#{output_basename}.#{output_extension}",
        mime_type: MIME_TYPES.fetch(output_extension, "application/octet-stream")
      )
    end
  end

  private

  attr_reader :uploaded_file, :source_format, :target_format

  def validate_request!
    raise ValidationError, "Please upload a file." unless uploaded_file&.respond_to?(:tempfile)
    raise ValidationError, "Select a source format." unless SOURCE_FORMATS.include?(source_format)
    raise ValidationError, "Select a target format." unless TARGET_FORMATS.include?(target_format)
    raise ValidationError, "Uploaded file has no extension." if input_extension.empty?

    unless extension_matches_source?
      raise ValidationError,
            "Selected source format does not match uploaded file extension (.#{input_extension})."
    end

    return if conversion_supported?

    raise UnsupportedConversionError,
          "This conversion is not supported yet: #{source_format} -> #{target_format}."
  end

  def persist_upload(dir)
    path = File.join(dir, "input.#{input_extension}")
    tempfile = uploaded_file.tempfile
    tempfile.rewind if tempfile.respond_to?(:rewind)
    File.binwrite(path, tempfile.read)
    tempfile.rewind if tempfile.respond_to?(:rewind)
    path
  end

  def transform(input_path, dir)
    case source_format
    when "word"
      transform_from_word(input_path, dir)
    when "pdf"
      transform_from_pdf(input_path, dir)
    when "jpg", "png"
      transform_from_image(input_path, dir)
    else
      raise UnsupportedConversionError,
            "This conversion is not supported yet: #{source_format} -> #{target_format}."
    end
  end

  def transform_from_word(input_path, dir)
    pdf_path = convert_word_to_pdf(input_path, dir)
    return pdf_path if target_format == "pdf"

    convert_pdf_to_image(pdf_path, dir, target_format)
  end

  def transform_from_pdf(input_path, dir)
    return input_path if target_format == "pdf"

    convert_pdf_to_image(input_path, dir, target_format)
  end

  def transform_from_image(input_path, dir)
    output_path = File.join(dir, "converted.#{target_format}")

    case target_format
    when "pdf"
      run_python_image_script(input_path, output_path, "to_pdf")
    when "jpg"
      run_python_image_script(input_path, output_path, "to_jpg")
    when "png"
      run_python_image_script(input_path, output_path, "to_png")
    when "ico"
      run_python_image_script(input_path, output_path, "to_ico")
    else
      raise UnsupportedConversionError,
            "This conversion is not supported yet: #{source_format} -> #{target_format}."
    end

    ensure_file_exists!(output_path)
  end

  def convert_word_to_pdf(input_path, dir)
    runtime_dir = File.join(dir, "runtime")
    home_dir = File.join(dir, "home")
    FileUtils.mkdir_p([runtime_dir, home_dir])
    FileUtils.chmod(0o700, runtime_dir)

    run_command!(
      "soffice",
      "--headless",
      "--convert-to",
      "pdf",
      "--outdir",
      dir,
      input_path,
      env: {
        "HOME" => home_dir,
        "XDG_RUNTIME_DIR" => runtime_dir
      }
    )

    pdf_path = File.join(dir, "input.pdf")
    ensure_file_exists!(pdf_path)
  end

  def convert_pdf_to_image(input_path, dir, format)
    raise UnsupportedConversionError, "PDF can only be converted to PNG or JPG." unless %w[png jpg].include?(format)

    output_base = File.join(dir, "converted")
    command = if format == "png"
                ["pdftoppm", "-f", "1", "-singlefile", "-png", input_path, output_base]
              else
                ["pdftoppm", "-f", "1", "-singlefile", "-jpeg", input_path, output_base]
              end

    run_command!(*command)

    output_path = File.join(dir, "converted.#{format}")
    ensure_file_exists!(output_path)
  end

  def run_python_image_script(input_path, output_path, mode)
    script = <<~PYTHON
      from PIL import Image
      import sys

      input_path, output_path, mode = sys.argv[1], sys.argv[2], sys.argv[3]

      with Image.open(input_path) as img:
          if mode == "to_pdf":
              if img.mode not in ("RGB", "L"):
                  if "A" in img.mode:
                      rgba = img.convert("RGBA")
                      background = Image.new("RGB", rgba.size, (255, 255, 255))
                      background.paste(rgba, mask=rgba.split()[-1])
                      img = background
                  else:
                      img = img.convert("RGB")
              img.save(output_path, "PDF", resolution=300.0)
          elif mode == "to_jpg":
              if img.mode not in ("RGB", "L"):
                  if "A" in img.mode:
                      rgba = img.convert("RGBA")
                      background = Image.new("RGB", rgba.size, (255, 255, 255))
                      background.paste(rgba, mask=rgba.split()[-1])
                      img = background
                  else:
                      img = img.convert("RGB")
              img.save(output_path, "JPEG", quality=95)
          elif mode == "to_png":
              img.save(output_path, "PNG")
          elif mode == "to_ico":
              img = img.convert("RGBA")
              img.save(output_path, format="ICO", sizes=[(32, 32), (48, 48), (64, 64), (128, 128), (180, 180), (512, 512)])
          else:
              raise ValueError("Unsupported mode")
    PYTHON

    run_command!("python3", "-c", script, input_path, output_path, mode)
  end

  def run_command!(*command, env: {})
    stdout, stderr, status = Open3.capture3(env, *command)
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

  def conversion_supported?
    return true if source_format == "word" && %w[pdf png jpg].include?(target_format)
    return true if source_format == "pdf" && %w[pdf png jpg].include?(target_format)
    return true if %w[jpg png].include?(source_format) && %w[pdf png jpg ico].include?(target_format)

    false
  end

  def extension_matches_source?
    case source_format
    when "word"
      WORD_EXTENSIONS.include?(input_extension)
    when "pdf"
      input_extension == "pdf"
    when "jpg"
      %w[jpg jpeg].include?(input_extension)
    when "png"
      input_extension == "png"
    else
      false
    end
  end

  def output_basename
    base = File.basename(uploaded_file.original_filename.to_s, ".*")
    cleaned = base.gsub(/[^0-9A-Za-z._-]+/, "_").gsub(/\A[._-]+/, "")
    cleaned.present? ? cleaned : "converted_file"
  end

  def input_extension
    @input_extension ||= File.extname(uploaded_file.original_filename.to_s).delete(".").downcase
  end
end
