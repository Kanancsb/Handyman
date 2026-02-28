require "test_helper"

class TransformFilesControllerTest < ActionDispatch::IntegrationTest
  setup do
    @sample_file_path = Rails.root.join("test/fixtures/files/sample.jpg")
  end

  test "shows transform form" do
    get transform_files_url

    assert_response :success
    assert_includes @response.body, "Convert and download your file"
    assert_includes @response.body, "Transform and Download"
  end

  test "downloads transformed file when conversion succeeds" do
    uploaded_file = Rack::Test::UploadedFile.new(@sample_file_path, "image/jpeg")
    result = FileTransformer::Result.new(
      content: "binary-output",
      filename: "converted.pdf",
      mime_type: "application/pdf"
    )

    fake_transformer = Struct.new(:result) do
      def call
        result
      end
    end.new(result)

    FileTransformer.stub(:new, fake_transformer) do
      post transform_files_url, params: {
        file: uploaded_file,
        source_format: "jpg",
        target_format: "pdf"
      }
    end

    assert_response :success
    assert_equal "binary-output", @response.body
    assert_equal "application/pdf", @response.media_type
    assert_match(/attachment; filename=\"converted.pdf\"/, @response.headers["Content-Disposition"])
  end

  test "redirects back with alert when validation fails" do
    post transform_files_url, params: { source_format: "jpg", target_format: "pdf" }

    assert_redirected_to transform_files_url
    follow_redirect!
    assert_response :success
    assert_includes @response.body, "Please upload a file."
  end
end
