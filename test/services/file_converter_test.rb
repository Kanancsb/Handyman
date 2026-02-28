require "test_helper"
require "minitest/mock"
require "tempfile"

class FileConverterTest < ActiveSupport::TestCase
  UploadedFile = Struct.new(:original_filename, :tempfile)

  test "auto detects source format from file extension when source_format is blank" do
    with_converter(filename: "photo.jpeg", source_format: "", target_format: "pdf") do |converter|
      converter.stub(:convert, lambda { |_input_path, dir|
        output_path = File.join(dir, "converted.pdf")
        File.binwrite(output_path, "pdf-output")
        output_path
      }) do
        result = converter.call

        assert_equal "photo.pdf", result.filename
        assert_equal "application/pdf", result.mime_type
        assert_equal "pdf-output", result.content
      end
    end
  end

  test "raises validation error when source format cannot be auto detected" do
    with_converter(filename: "archive.zip", source_format: "", target_format: "pdf") do |converter|
      error = assert_raises(FileConverter::ValidationError) { converter.call }
      assert_includes error.message, "Could not auto-detect source type"
    end
  end

  test "raises validation error when source format and extension do not match" do
    with_converter(filename: "photo.jpg", source_format: "pdf", target_format: "png") do |converter|
      error = assert_raises(FileConverter::ValidationError) { converter.call }
      assert_includes error.message, "does not match uploaded file extension"
    end
  end

  test "raises unsupported conversion error for unsupported format combination" do
    with_converter(filename: "document.pdf", source_format: "pdf", target_format: "ico") do |converter|
      error = assert_raises(FileConverter::UnsupportedConversionError) { converter.call }
      assert_includes error.message, "pdf -> ico"
    end
  end

  test "sanitizes output file name when building conversion result" do
    with_converter(filename: "my file.png", source_format: "png", target_format: "jpg") do |converter|
      converter.stub(:convert, lambda { |_input_path, dir|
        output_path = File.join(dir, "converted.jpg")
        File.binwrite(output_path, "jpg-output")
        output_path
      }) do
        result = converter.call

        assert_equal "my_file.jpg", result.filename
        assert_equal "image/jpeg", result.mime_type
      end
    end
  end

  test "convert_from_word returns generated pdf when target format is pdf" do
    with_converter(filename: "report.docx", source_format: "word", target_format: "pdf") do |converter|
      converter.stub(:convert_word_to_pdf, "/tmp/input.pdf") do
        result = converter.send(:convert_from_word, "/tmp/input.docx", "/tmp")
        assert_equal "/tmp/input.pdf", result
      end
    end
  end

  test "convert_from_word converts generated pdf to image when target is png or jpg" do
    with_converter(filename: "report.docx", source_format: "word", target_format: "png") do |converter|
      calls = {}

      converter.stub(:convert_word_to_pdf, "/tmp/input.pdf") do
        converter.stub(:convert_pdf_to_image, lambda { |pdf_path, dir, format|
          calls[:pdf_path] = pdf_path
          calls[:dir] = dir
          calls[:format] = format
          "/tmp/converted.png"
        }) do
          result = converter.send(:convert_from_word, "/tmp/input.docx", "/tmp")
          assert_equal "/tmp/converted.png", result
        end
      end

      assert_equal "/tmp/input.pdf", calls[:pdf_path]
      assert_equal "/tmp", calls[:dir]
      assert_equal "png", calls[:format]
    end
  end

  test "convert_from_pdf returns input file directly when target is pdf" do
    with_converter(filename: "guide.pdf", source_format: "pdf", target_format: "pdf") do |converter|
      result = converter.send(:convert_from_pdf, "/tmp/input.pdf", "/tmp")
      assert_equal "/tmp/input.pdf", result
    end
  end

  test "convert_from_image uses ico conversion mode when target format is ico" do
    with_converter(filename: "icon.png", source_format: "png", target_format: "ico") do |converter|
      calls = {}

      converter.stub(:run_python_image_script, lambda { |input_path, output_path, mode|
        calls[:input_path] = input_path
        calls[:output_path] = output_path
        calls[:mode] = mode
      }) do
        converter.stub(:ensure_file_exists!, ->(path) { path }) do
          result = converter.send(:convert_from_image, "/tmp/icon.png", "/tmp")
          assert_equal "/tmp/converted.ico", result
        end
      end

      assert_equal "/tmp/icon.png", calls[:input_path]
      assert_equal "/tmp/converted.ico", calls[:output_path]
      assert_equal "to_ico", calls[:mode]
    end
  end

  private

  def with_converter(filename:, source_format:, target_format:, content: "input-content")
    tempfile = Tempfile.new(["upload", File.extname(filename)])
    tempfile.binmode
    tempfile.write(content)
    tempfile.rewind

    uploaded_file = UploadedFile.new(filename, tempfile)
    converter = FileConverter.new(
      uploaded_file: uploaded_file,
      source_format: source_format,
      target_format: target_format
    )

    yield converter
  ensure
    tempfile&.close!
  end
end
