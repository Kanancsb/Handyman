require "test_helper"

class YoutubeConverterControllerTest < ActionDispatch::IntegrationTest
  test "shows youtube converter form" do
    get youtube_converter_url

    assert_response :success
    assert_includes @response.body, "Youtube_Converter"
    assert_includes @response.body, "Download a YouTube video, playlist, or audio file"
    assert_includes @response.body, "Start Download"
    assert_includes @response.body, "Playlist and album-style links are packaged into a ZIP"
  end

  test "creates youtube download job" do
    YoutubeConverter.stub(:create_job!, "job-123") do
      post youtube_converter_url,
           params: {
             video_url: "https://www.youtube.com/watch?v=abc123",
             download_type: "mp3"
           },
           headers: { "Accept" => "application/json" }
    end

    assert_response :success
    body = JSON.parse(@response.body)
    assert_equal "job-123", body["job_id"]
    assert_match(/youtube-converter\/jobs\/job-123/, body["status_url"])
    assert_match(/youtube-converter\/download\/job-123/, body["download_url"])
  end

  test "returns error json when youtube job validation fails" do
    YoutubeConverter.stub(:create_job!, ->(**_kwargs) { raise YoutubeConverter::ValidationError, "Please enter a YouTube video link." }) do
      post youtube_converter_url,
           params: { download_type: "mp3" },
           headers: { "Accept" => "application/json" }
    end

    assert_response :unprocessable_entity
    body = JSON.parse(@response.body)
    assert_equal "Please enter a YouTube video link.", body["error"]
  end

  test "shows youtube job status" do
    YoutubeConverter.stub(:status_for, {
      "job_id" => "job-123",
      "state" => "downloading",
      "progress" => 42.5,
      "eta" => "00:12",
      "message" => "Downloading...",
      "current_item" => 2,
      "total_items" => 8,
      "download_ready" => false,
      "filename" => nil
    }) do
      get youtube_converter_job_url(job_id: "job-123"), headers: { "Accept" => "application/json" }
    end

    assert_response :success
    body = JSON.parse(@response.body)
    assert_equal "downloading", body["state"]
    assert_equal 42.5, body["progress"]
    assert_nil body["download_url"]
  end

  test "downloads finished youtube file" do
    tmp_file = Rails.root.join("tmp", "youtube-controller-test.mp3")
    File.binwrite(tmp_file, "audio-binary")

    YoutubeConverter.stub(:status_for, {
      "job_id" => "job-123",
      "download_ready" => true,
      "filename" => "youtube_download.mp3"
    }) do
      YoutubeConverter.stub(:result_path, tmp_file) do
        get youtube_converter_download_url(job_id: "job-123")
      end
    end

    assert_response :success
    assert_equal "audio-binary", @response.body
    assert_equal "audio/mpeg", @response.media_type
  ensure
    File.delete(tmp_file) if File.exist?(tmp_file)
  end

  test "redirects if youtube file is not ready" do
    YoutubeConverter.stub(:status_for, { "download_ready" => false }) do
      get youtube_converter_download_url(job_id: "job-123")
    end

    assert_redirected_to youtube_converter_url
  end
end
