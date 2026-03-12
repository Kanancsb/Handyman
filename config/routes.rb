Rails.application.routes.draw do
  root "home#index"

  get "convert-files", to: "convert_files#new", as: :convert_files
  post "convert-files", to: "convert_files#create"

  get "convert-icons", to: "convert_icons#new", as: :convert_icons
  post "convert-icons", to: "convert_icons#create"

  get "youtube-converter", to: "youtube_converter#new", as: :youtube_converter
  post "youtube-converter", to: "youtube_converter#create"
end
