require "test_helper"
require "minitest/mock"
require "tempfile"

class IconConverterTest < ActiveSupport::TestCase
  UploadedFile = Struct.new(:original_filename, :tempfile)

  test "raises validation error for unsupported source extension" do
    with_converter(filename: "document.pdf", target_preset: "ico") do |converter|
      error = assert_raises(IconConverter::ValidationError) { converter.call }
      assert_includes error.message, "Only JPG and PNG files are supported"
    end
  end

  test "raises validation error when target preset is missing" do
    with_converter(filename: "avatar.jpg", target_preset: "") do |converter|
      error = assert_raises(IconConverter::ValidationError) { converter.call }
      assert_includes error.message, "Select a target preset."
    end
  end

  test "returns converted icon result with preset suffix and mime type" do
    with_converter(filename: "avatar.jpg", target_preset: "twitter_png") do |converter|
      converter.stub(:convert_image, lambda { |_input_path, output_path, _preset|
        File.binwrite(output_path, "png-output")
      }) do
        result = converter.call

        assert_equal "avatar_twitter_profile.png", result.filename
        assert_equal "image/png", result.mime_type
        assert_equal "png-output", result.content
      end
    end
  end

  test "sanitizes output base name for icon downloads" do
    with_converter(filename: "my profile image.png", target_preset: "ico") do |converter|
      converter.stub(:convert_image, lambda { |_input_path, output_path, _preset|
        File.binwrite(output_path, "ico-output")
      }) do
        result = converter.call
        assert_equal "my_profile_image_icon.ico", result.filename
      end
    end
  end

  test "convert image calls python script with preset details" do
    with_converter(filename: "avatar.png", target_preset: "instagram_jpg") do |converter|
      calls = {}
      preset = IconConverter::PRESETS.fetch("instagram_jpg")

      converter.stub(:run_python_image_script, lambda { |input_path, output_path, mode, size, format|
        calls[:input_path] = input_path
        calls[:output_path] = output_path
        calls[:mode] = mode
        calls[:size] = size
        calls[:format] = format
      }) do
        converter.stub(:ensure_file_exists!, true) do
          converter.send(:convert_image, "/tmp/input.png", "/tmp/output.jpg", preset)
        end
      end

      assert_equal "/tmp/input.png", calls[:input_path]
      assert_equal "/tmp/output.jpg", calls[:output_path]
      assert_equal "square", calls[:mode]
      assert_equal 320, calls[:size]
      assert_equal "JPEG", calls[:format]
    end
  end

  private

  def with_converter(filename:, target_preset:, content: "image-content")
    tempfile = Tempfile.new(["icon-upload", File.extname(filename)])
    tempfile.binmode
    tempfile.write(content)
    tempfile.rewind

    uploaded_file = UploadedFile.new(filename, tempfile)
    converter = IconConverter.new(
      uploaded_file: uploaded_file,
      target_preset: target_preset
    )

    yield converter
  ensure
    tempfile&.close!
  end
end
