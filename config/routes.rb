Rails.application.routes.draw do
  root "home#index"

  get "transform-files", to: "transform_files#new", as: :transform_files
  post "transform-files", to: "transform_files#create"
end
