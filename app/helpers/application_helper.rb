module ApplicationHelper
  def nav_link_class(path)
    classes = ["nav-link"]
    classes << "is-active" if current_page?(path)
    classes.join(" ")
  end
end
