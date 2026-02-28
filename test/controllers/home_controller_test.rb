require "test_helper"

class HomeControllerTest < ActionDispatch::IntegrationTest
  test "shows landing page" do
    get root_url

    assert_response :success
    assert_includes @response.body, "Welcome to your daily file toolbox"
    assert_includes @response.body, "Open File Transformer"
  end
end
