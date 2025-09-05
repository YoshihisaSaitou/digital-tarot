# Digital Tarot Android App — ユーザーガイド（日本語）

このアプリは、端末のカメラでタロットカードを撮影し、端末内で管理・閲覧・バックアップできるアプリです。名称編集、タグ付け、拡大表示、並べ替え・絞り込み、エクスポート/インポート（重複処理オプション、複数ファイル選択）に対応しています。

英語版のREADMEは README.en.md を参照してください。


## 機能一覧
- 撮影・保存: 端末のカメラを起動し、撮影画像をアプリ内ストレージ `files/cards/` に保存
- 一覧表示: `RecyclerView` のグリッドでカード一覧を表示、右下の `+` で追加
- 詳細表示/拡大: カードをタップで詳細画面へ、ピンチズーム/ダブルタップで拡大（PhotoView）
- 名称編集/タグ付け: 詳細画面のメニューから編集（名称+タグ（カンマ区切り））
- 削除: 一覧の長押し、または詳細画面メニューから削除（確認ダイアログ）
- 並べ替え: 名称/新しい順/古い順
- タグ絞り込み: 登録済みタグから単一タグで絞り込み
- バックアップ（エクスポート）: `cards.json` + 画像を ZIP にまとめて共有
- インポート: ZIPからの復元。複数ZIP選択可。重複時オプション（上書き/スキップ/複製）
- 多言語対応: 日本語/英語（端末の言語設定に追従）


## 使い方
- 追加（撮影）: 一覧画面右下の `+` → カメラで撮影 → 自動で保存&一覧更新
- 詳細/編集: 一覧アイテムをタップ → 詳細画面 → 右上メニューから「編集」
- 削除: 一覧アイテム長押し、または詳細画面のメニューから「削除」
- 並べ替え/絞り込み: 一覧画面右上メニューから選択
- エクスポート: 一覧画面右上メニュー → 「バックアップをエクスポート」 → 共有先を選択
- インポート: 一覧画面右上メニュー → 「バックアップをインポート」 → 重複時の処理を選択 → ZIPを複数選択可


## スクリーンショット / Screenshots
以下のパスにスクリーンショットを配置すると自動で表示されます。実機で撮影後、`docs/screenshots/` に配置してください。

| 画面 | パス |
| --- | --- |
| 一覧（ライト） | `docs/screenshots/list_light.png` |
| 一覧（ダーク） | `docs/screenshots/list_dark.png` |
| 詳細（拡大） | `docs/screenshots/detail_zoom.png` |
| 編集ダイアログ | `docs/screenshots/edit_dialog.png` |
| インポート設定 | `docs/screenshots/import_options.png` |

埋め込み例:

![List Light](docs/screenshots/list_light.png)
![Detail Zoom](docs/screenshots/detail_zoom.png)


## データ保存仕様
- 画像保存先: `Context.filesDir/cards/` に JPEG 等を保存
- メタ情報: `files/cards/cards.json`
  - スキーマ（配列）
    ```json
    [
      {
        "id": "UUID",
        "title": "カード名",
        "imageFileName": "保存した画像ファイル名",
        "createdAt": 1700000000000,
        "tags": ["tag1", "tag2"]
      }
    ]
    ```
- 互換性: 旧フォーマット（`title`/`imageFileName` のみ）も読み込み時に自動補完


## バックアップ/インポート仕様
- エクスポート: `cache/export/digital-tarot-backup.zip` を生成し共有
  - ZIP構成: ルートに `cards.json`、`images/` 配下に画像
- インポート:
  - `OpenMultipleDocuments` で複数ZIP選択可
  - 画像ファイル名が衝突する場合は `name (1).ext` のように自動リネーム
  - 重複（カードIDが一致）の処理オプション:
    - 上書き: 既存カードを置換
    - スキップ: 既存カードを維持（インポート側無視）
    - 複製: 新規IDで複製を追加
  - `cards.json` が無いZIPは、画像のみから新規カードを作成（タイトルはファイル名ベース）


## 権限とセキュリティ
- カメラ権限: 不要（`ACTION_IMAGE_CAPTURE` を使用）
- ファイル共有: `FileProvider` を使用（`res/xml/file_paths.xml`）
- 保存領域: アプリ内部ストレージ（他アプリからは直接参照不可）


## 主要依存関係
- AndroidX RecyclerView / CardView
- Material Components
- PhotoView（拡大表示）


## ビルド/実行
- Android Studio で開く → Gradle Sync → 実機/エミュレータで実行
- `compileSdk`: 36 / `targetSdk`: 36 / `minSdk`: 24


## 既知の制限/今後の拡張
- サムネイル生成は簡易実装（必要に応じて Glide などの導入を推奨）
- 絞り込みは単一タグのみ（複数タグ AND/OR は拡張可能）
- インポート時の進捗表示は簡易（プログレス表示の追加は容易）


---

# Digital Tarot Android App — User Guide (English)

This app lets you capture tarot card images with the camera and manage them locally on the device. It supports editing names, tagging, zooming, sorting/filtering, and backup/export/import (with duplicate handling and multi-file selection).

See README.en.md for an English-only version.

## Features
- Capture & save: Launch camera and save images under `files/cards/`
- List view: Grid list with `RecyclerView`; floating action button to add
- Detail/Zoom: Tap item to open detail, pinch/double-tap to zoom (PhotoView)
- Edit name/tags: From detail menu (name + comma-separated tags)
- Delete: Long-press on list item or from detail menu (with confirmation)
- Sorting: By title/newest/oldest
- Tag filter: Single tag filter from existing tags
- Backup (export): Package `cards.json` + images into a ZIP and share
- Import: Restore from ZIP. Supports selecting multiple ZIPs. Duplicate options (overwrite/skip/duplicate)
- Localization: Japanese/English (follows device language)

## Storage
- Images: `Context.filesDir/cards/`
- Metadata: `files/cards/cards.json` (array of objects)
- Backward compatible with older (minimal) JSON

## Backup/Import
- Export creates `digital-tarot-backup.zip` with `cards.json` and `images/`
- Import supports multiple ZIPs via Storage Access Framework
- Image filename conflicts resolved by `name (1).ext` pattern
- Duplicate handling (by card ID): Overwrite / Skip / Duplicate (new UUID)
- If no `cards.json`, images only are imported as new cards (title=filename)

## Permissions
- No camera permission required (uses `ACTION_IMAGE_CAPTURE`)
- `FileProvider` for safe file sharing

## Dependencies
- AndroidX RecyclerView / CardView, Material Components, PhotoView

## Build
- Open in Android Studio → Sync → Run on device/emulator

## Notes / Next steps
- Consider Glide/Picasso for thumbnails
- Multi-tag filters (AND/OR) and progress UI for import are possible enhancements

---

## 開発者向け（Developer Notes）
- 設計/責務の詳細は `docs/ARCHITECTURE.md` を参照してください。
- スクリーンショットは `docs/screenshots/` に配置してください。

