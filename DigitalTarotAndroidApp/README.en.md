# Digital Tarot Android App — README (English)

This repository contains an Android app to capture tarot cards with the device camera and manage them locally. It supports editing names, tagging, zooming, sorting/filtering, and backup/export/import with duplicate handling and multi-file selection.

For the Japanese guide, see README.md.

## Features
- Capture & save: Launch camera and save images in `files/cards/`
- List view: Grid `RecyclerView`; add with the floating action button
- Detail/Zoom: Tap → full-screen detail with pinch/double-tap zoom (PhotoView)
- Edit name/tags: From detail menu (name + comma-separated tags)
- Delete: Long-press in list or from detail menu (with confirmation)
- Sorting: Title / Newest / Oldest
- Tag filter: Single-tag filtering from existing tags
- Export backup: Create a ZIP with `cards.json` + images and share
- Import: Restore from ZIP, supports selecting multiple ZIPs
  - Duplicate handling by card ID: Overwrite / Skip / Duplicate
  - Image filename conflicts auto-resolved with `name (1).ext`
- Localization: Japanese and English (follows device language)

## Storage format
- Images: `Context.filesDir/cards/`
- Metadata file: `files/cards/cards.json` (array)
  ```json
  [
    {
      "id": "UUID",
      "title": "Card Name",
      "imageFileName": "stored-image-name.jpg",
      "createdAt": 1700000000000,
      "tags": ["tag1", "tag2"]
    }
  ]
  ```
- Backward compatible reading for older minimal JSON

## Backup/Import
- Export ZIP: `cards.json` at root + images under `images/`
- Import:
  - Pick multiple ZIP files via Storage Access Framework
  - Duplicate policy is selectable before import: Overwrite / Skip / Duplicate (new UUID)
  - ZIPs without `cards.json` import images as standalone cards (title from filename)

## Permissions / Security
- No camera permission required (`ACTION_IMAGE_CAPTURE`)
- Uses `FileProvider` to share files safely
- Data stored in app-internal storage

## Dependencies
- AndroidX RecyclerView / CardView
- Material Components
- PhotoView

## Build
- Open in Android Studio → Sync → Run on device/emulator
- Targets: compileSdk 36 / targetSdk 36 / minSdk 24

## Roadmap
- Use Glide/Picasso for thumbnails
- Multi-tag filtering (AND/OR)
- Import progress UI and conflict prompts per item

