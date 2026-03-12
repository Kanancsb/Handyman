require "test_helper"

class YoutubeConverterControllerTest < ActionDispatch::IntegrationTest
  test "shows youtube converter form" do
    get youtube_converter_url

    assert_response :success
    assert_includes @response.body, "Youtube_Converter"
    assert_includes @response.body, "Download Video"
    assert_includes @response.body, "Download Mp3"
    assert_includes @response.body, "Download .Wav"
  end

  test "downloads converted youtube content when conversion succeeds" do
    result = YoutubeConverter::Result.new(
      content: "video-binary",
      filename: "youtube_download.mp3",
      mime_type: "audio/mpeg"
    )

    fake_converter = Struct.new(:result) do
      def call
        result
      end
    end.new(result)

    YoutubeConverter.stub(:new, ->(**_kwargs) { fake_converter }) do
      post youtube_converter_url, params: {
        video_url: "https://www.youtube.com/watch?v=abc123",
        download_type: "mp3"
      }
    end

    assert_response :success
    assert_equal "video-binary", @response.body
    assert_equal "audio/mpeg", @response.media_type
    assert_match(/attachment; filename=\"youtube_download.mp3\"/, @response.headers["Content-Disposition"])
  end

  test "redirects back with alert when youtube conversion validation fails" do
    post youtube_converter_url, params: { download_type: "mp3" }

    assert_redirected_to youtube_converter_url
    follow_redirect!
    assert_response :success
    assert_includes @response.body, "Please enter a YouTube video link."
  end

  test "redirects back with alert when youtube conversion command fails" do
    fake_converter = Struct.new(:message) do
      def call
        raise YoutubeConverter::ConversionError, message
      end
    end.new("Your installed `yt-dlp` is outdated for YouTube. Update `yt-dlp` to the latest release and try again.")

    YoutubeConverter.stub(:new, ->(**_kwargs) { fake_converter }) do
      post youtube_converter_url, params: {
        video_url: "https://www.youtube.com/watch?v=abc123",
        download_type: "mp3"
      }
    end

    assert_redirected_to youtube_converter_url
    follow_redirect!
    assert_response :success
    assert_includes @response.body, "Your installed `yt-dlp` is outdated for YouTube."
  end

  test "redirects back with alert when youtube network fails" do
    fake_converter = Struct.new(:message) do
      def call
        raise YoutubeConverter::ConversionError, message
      end
    end.new("The server could not reach YouTube. Check this machine's internet or DNS configuration and try again.")

    YoutubeConverter.stub(:new, ->(**_kwargs) { fake_converter }) do
      post youtube_converter_url, params: {
        video_url: "https://www.youtube.com/watch?v=abc123",
        download_type: "video"
      }
    end

    assert_redirected_to youtube_converter_url
    follow_redirect!
    assert_response :success
    assert_includes @response.body, "The server could not reach YouTube."
  end
end
