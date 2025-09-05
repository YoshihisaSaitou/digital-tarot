# Digital Tarot — Architecture / 内部構成

本書は開発者向けドキュメントです。主要クラスの責務、データフロー、拡張ポイントを記載します。

## 構成概要
- アプリ層は単一モジュール（`app`）。永続化は軽量なJSONファイル、画像は内部ファイル領域。
- 画面は2枚：一覧（`MainActivity`）と詳細（`DetailActivity`）。

## 主要クラスと責務
- `MainActivity`
  - 一覧表示（`RecyclerView + GridLayout`）と `FloatingActionButton` による追加（撮影）
  - メニュー操作：並べ替え（名称/新しい順/古い順）、タグ絞り込み、エクスポート、インポート
  - カメラ起動（`ACTION_IMAGE_CAPTURE` + `FileProvider`）と撮影結果の保存
  - インポート（複数ZIP選択、重複オプション：上書き/スキップ/複製）
  - バックアップZIP作成（`cards.json` + `images/`）
  - フィルタ・ソート適用（`applyFilterAndSort`）
- `DetailActivity`
  - 画像のフル画面表示（`PhotoView` でピンチズーム）
  - メニューから名称・タグ編集、カード削除
  - 編集/削除結果を `setResult` で呼出元へ伝達
- `CardAdapter`
  - 一覧用アダプタ。画像の簡易サムネイル化（`BitmapFactory` の `inSampleSize`）
  - クリック（詳細へ）/長押し（削除確認）イベント伝播
- `CardItem`
  - ドメインモデル。`id`, `title`, `imageFileName`, `createdAt`, `tags`
- `CardStorage`
  - JSONファイル（`files/cards/cards.json`）への保存/読込
  - 画像ファイル削除、カードディレクトリの提供

## データ構造
- 保存先
  - 画像: `Context.filesDir/cards/`（`FileProvider` で公開パスをマップ）
  - メタ: `files/cards/cards.json`（配列）
- JSONスキーマ
  ```json
  [
    {
      "id": "UUID",
      "title": "Card",
      "imageFileName": "card-1700000.jpg",
      "createdAt": 1700000000000,
      "tags": ["tag1", "tag2"]
    }
  ]
  ```

## 主なフロー
- 撮影フロー
  1. `MainActivity` → `launchCamera()` でファイル用URIを発行
  2. カメラアプリで撮影 → 画像が指定ファイルに保存
  3. `onActivityResult` → `CardItem` 生成→ JSONへ保存→一覧更新
- 編集フロー
  1. 一覧タップ → `DetailActivity`
  2. メニュー「編集」→ ダイアログで名称/タグ更新 → JSON保存
  3. 結果コードで一覧へ反映
- 削除フロー
  - 一覧長押し、または詳細メニュー → 確認 → 画像/JSONを削除 → 一覧更新
- エクスポート
  - `cards.json` + 画像をZIP化 → `ACTION_SEND` で共有
- インポート
  - `OpenMultipleDocuments` で複数ZIP選択
  - Zip展開：`cards.json` と `images/` を読み込む
  - 画像名重複は `name (1).ext` で回避
  - カードID重複はポリシー（上書き/スキップ/複製）で処理

## 拡張ポイント
- 表示最適化
  - 画像ローディング: Glide/Picasso へ置換（メモリ効率とキャッシュ向上）
  - DiffUtil 導入で `notifyDataSetChanged()` を最小化
- 機能拡張
  - 複数タグのAND/OR絞り込み、タグ編集UIの改善（チップ、補完）
  - 画像編集（トリミング/回転）
  - 共有（単一カードの画像+メタを共有）
  - インポート進捗UI、重複時の都度選択ダイアログ
- 永続化
  - Room への移行（将来の拡張/検索性/マイグレーション）
  - バックアップの暗号化/署名

## 開発Tips
- 最低限API: `minSdk 24`。権限ハンドリングはカメラ不要（`ACTION_IMAGE_CAPTURE`）。
- `FileProvider` のパスは `res/xml/file_paths.xml` に定義。
- テスト: JSONの読み書き、ZIPの入出力はユニットテストで切り出し可能。
- UI: 端末言語により `values-ja/` と `values-en/` の文言が選択されます。

