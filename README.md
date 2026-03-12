# HandymanApp

HandymanApp is a Ruby on Rails project with a small collection of daily conversion tools.

## What the project can do

### 1. Home page
- Shows the main website introduction.
- Links users to the available tools.

### 2. Convert Files
- Upload and convert files between supported formats.
- Auto-detects the source type from the uploaded file.
- Current supported flows:
  - Word files (`.doc`, `.docx`, `.odt`, `.rtf`) to `PDF`, `PNG`, or `JPG`
  - `PDF` to `PNG` or `JPG`
  - `JPG` and `PNG` to `PDF`, `PNG`, `JPG`, or `ICO`

### 3. Convert Icons
- Upload a `JPG` or `PNG` and generate:
  - `.ico`
  - Instagram profile image in `PNG`
  - Instagram profile image in `JPG`
  - Twitter/X profile image in `PNG`
  - Twitter/X profile image in `JPG`

### 4. Youtube_Converter
- Download a YouTube video from a link.
- Convert a YouTube video to `MP3`.
- Convert a YouTube video to `WAV`.
- Accepts playlist and album-style YouTube links.
- Shows progress, current item, and ETA while downloading.
- Packages multi-file playlist/album downloads as a `.zip`.

## Main routes

- `/` - Home page
- `/convert-files` - File conversion page
- `/convert-icons` - Icon and social image conversion page
- `/youtube-converter` - YouTube video/audio download page

## Application requirements

### Ruby and Rails
- Ruby `3.2.3`
- Rails `6.1.7.x`
- Bundler

### Database
- SQLite3

### JavaScript runtime
- Node.js

This project includes `webpacker` and a `package.json`. The current pages do not depend on a compiled front-end bundle to render, but Node.js is still recommended for a normal Rails setup.

### System tools required by the converters

#### For file conversion
- `libreoffice` or another package that provides `soffice`
  - Used for Word to PDF conversion
- `poppler-utils`
  - Provides `pdftoppm`
  - Used for PDF to PNG/JPG conversion
- `python3`
- Pillow for Python
  - Usually installed with `python3-pil`
  - Used for image and icon conversion

#### For YouTube downloads
- `yt-dlp`
- `ffmpeg`
  - Required for MP3 and WAV extraction
- `zip`
  - Used to package playlist and album downloads into a single archive

## Recommended installation on Ubuntu / Zorin OS

Install the base packages:

```bash
sudo apt-get update
sudo apt-get install -y build-essential libsqlite3-dev sqlite3 nodejs ffmpeg zip unzip libreoffice poppler-utils python3 python3-pil
```

Install Ruby dependencies:

```bash
bundle install
```

Install a current `yt-dlp` release.

Important: distro packages for `yt-dlp` are often outdated and may fail against YouTube with errors like `Signature extraction failed` or `Precondition check failed`. Prefer the latest official binary:

```bash
sudo rm -f /usr/local/bin/yt-dlp
sudo wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp
sudo chmod a+rx /usr/local/bin/yt-dlp
hash -r
yt-dlp --version
```

Make sure `which yt-dlp` returns `/usr/local/bin/yt-dlp` if an older `/usr/bin/yt-dlp` is also installed.

## Project setup

### 1. Install gems

```bash
bundle install
```

### 2. Prepare the database

```bash
bin/rails db:prepare
```

### 3. Start the server

```bash
bin/rails s
```

Then open:

```text
http://127.0.0.1:3000
```

## Optional checks

Run the Rails test suite:

```bash
bin/rails test
```

Check the routes:

```bash
bin/rails routes
```

## Notes about how the tools work

### Convert Files
- Source type is auto-detected from the uploaded file extension.
- Word conversion depends on `soffice` being available in the server environment.
- PDF image export depends on `pdftoppm`.
- Image conversion depends on Python Pillow.

### Convert Icons
- Supports only `JPG`, `JPEG`, and `PNG` uploads.
- Produces square social profile images sized for Instagram and Twitter/X presets.

### Youtube_Converter
- Requires outbound internet access from the machine running Rails.
- Requires working DNS resolution.
- Playlist and album links can generate multiple files; those are returned as a `.zip`.
- Audio downloads require both `yt-dlp` and `ffmpeg`.
- Progress and ETA depend on the output reported by `yt-dlp`.

## Branding asset

The site uses the project icon from:

- `app/assets/images/Icons/handymanIco.png`

It is used in the header and as the page favicon.
