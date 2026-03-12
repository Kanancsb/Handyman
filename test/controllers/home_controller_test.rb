require "test_helper"

class HomeControllerTest < ActionDispatch::IntegrationTest
  test "shows landing page" do
    get root_url

    assert_response :success
    assert_includes @response.body, "Welcome to your daily file toolbox"
    assert_includes @response.body, "Open File Converter"
    assert_includes @response.body, "Open Icon Converter"
    assert_includes @response.body, "Open Youtube Converter"
    assert_includes @response.body, "Go to Convert Files"
    assert_includes @response.body, "Go to Convert Icons"
    assert_includes @response.body, "Go to Youtube Converter"
  end
end
