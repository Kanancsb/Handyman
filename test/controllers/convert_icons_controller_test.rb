require "test_helper"

class ConvertIconsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @sample_file_path = Rails.root.join("test/fixtures/files/sample.jpg")
  end

  test "shows convert icons form" do
    get convert_icons_url

    assert_response :success
    assert_includes @response.body, "Create profile-ready icons"
    assert_includes @response.body, "Instagram Profile PNG (320x320)"
    assert_includes @response.body, "Convert Icon and Download"
  end

  test "downloads converted icon when conversion succeeds" do
    uploaded_file = Rack::Test::UploadedFile.new(@sample_file_path, "image/jpeg")
    result = IconConverter::Result.new(
      content: "icon-binary",
      filename: "avatar_icon.ico",
      mime_type: "image/x-icon"
    )

    fake_converter = Struct.new(:result) do
      def call
        result
      end
    end.new(result)

    IconConverter.stub(:new, ->(**_kwargs) { fake_converter }) do
      post convert_icons_url, params: {
        file: uploaded_file,
        target_preset: "ico"
      }
    end

    assert_response :success
    assert_equal "icon-binary", @response.body
    assert_equal "image/x-icon", @response.media_type
    assert_match(/attachment; filename=\"avatar_icon.ico\"/, @response.headers["Content-Disposition"])
  end

  test "redirects back with alert when icon conversion validation fails" do
    post convert_icons_url, params: { target_preset: "ico" }

    assert_redirected_to convert_icons_url
    follow_redirect!
    assert_response :success
    assert_includes @response.body, "Please upload an image file."
  end
end
