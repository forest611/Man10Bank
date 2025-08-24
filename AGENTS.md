# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java` or `src/main/kotlin`: Plugin source (Kotlin preferred). Current entry: `red.man10.man10bank.Man10Bank`.
- `src/main/resources`: Plugin assets (`plugin.yml`, `config.yml`).
- `src/main/sql`: SQL schema/migrations (e.g., `db.sql`).
- `libs`: Optional external jars (compileOnly).
- `build/`: Gradle outputs; shaded jar via Shadow.
- `images/`: Screenshots used in `README.md`.

## Build, Test, and Development Commands
- `java -version`: Ensure JDK 17.
- `gradle clean build`: Compile + run resource processing.
- `gradle shadowJar`: Produce shaded plugin jar in `build/libs/` (no classifier).
- Local run: copy `build/libs/Man10Bank-<version>.jar` to your Paper server `plugins/`, start server, verify load.
- Config: edit `src/main/resources/config.yml` (or server-generated `plugins/Man10Bank/config.yml`).
 - 配布補助: ローカル専用のビルド+コピー用スクリプト `local-build-deploy.sh` を用意（Git管理外）。
   - 実行例: `sh ./local-build-deploy.sh`
   - 生成Jarを `/Volumes/M2_1TB/Documents/minecraft/Paper_1_20_4/plugins` に上書きコピー（既存の `Man10Bank-*.jar` を削除後）。
   - コピー先は環境変数 `DEPLOY_DIR` で上書き可能。
 - Gradle連携: `gradle deploy` でビルド＋コピーを一括実行できます。
   - `DEPLOY_DIR` もしくは `-PdeployDir=/path/to/plugins` でコピー先を指定可能。
   - 既存の `Man10Bank-*.jar` を削除してから新Jarをコピーします。

## Coding Style & Naming Conventions
- **Language**: Kotlin (preferred) targeting JVM 17; follow Official Kotlin Coding Conventions.
- **Indentation**: 4 spaces; UTF‑8 files; Unix line endings.
- **Names**: `UpperCamelCase` for classes, `lowerCamelCase` for functions/props, `UPPER_SNAKE_CASE` for constants.
- **Packages**: `red.man10.man10bank.*`.
- **Resources**: Keep `plugin.yml` commands/permissions in sync with code.
- Linting/formatting: if using ktlint/Spotless locally, run before pushing.

## Testing Guidelines
- Framework: JUnit 5 with MockBukkit is recommended for new tests.
- Location: `src/test/kotlin/**` mirroring package paths.
- Naming: `ClassNameTest.kt`, methods `fun doesThing_whenX_returnsY()`.
- Run: `gradle test`. Aim to cover business logic off the main thread.

## Commit & Pull Request Guidelines
- Commits: small, focused, imperative mood. Prefer Conventional Commits (e.g., `feat: add cheque memo parsing`).
- PRs: include purpose, scope, screenshots for GUI/UX, and reproduction/testing steps. Link issues, call out config/DB changes and migration notes (`src/main/sql`).
- CI/readiness: ensure builds pass and the plugin loads on Paper 1.20.4 with Vault present.

## Security & Configuration Tips
- Do not commit real database credentials; use placeholders in `config.yml`.
- Avoid blocking the main server thread for DB operations; use async tasks.
- Maintain DB compatibility and provide safe migrations in `src/main/sql`.

## Language Policy / 言語ポリシー
- Codexの回答、PR/Issueでの議論、ソースコード内コメントは日本語で記述してください。
- 本プロジェクトの開発者およびプロダクト利用者はいずれも日本人であることを前提とします。必要に応じて外部公開向けに英語を併記してください。
- 命名は英語（変数・メソッド・クラス）、コメントとユーザー向け文言は自然な日本語を推奨します。

## Git Workflow / Git運用
- Codexは編集を行った後、必ずGitにコミットを作成してください（小さな論理単位で分割）。
- 軽微な修正（例: タイポ、コメント、空白調整など）は直前のコミットを`git commit --amend`で更新してください。メッセージを変えない場合は`git commit --amend --no-edit`を使用します。
- Commitメッセージは必ず日本語で記述してください。
- すでにリモートへ公開した履歴の書き換えは原則避けてください。やむを得ず`--amend`後にプッシュする場合は`git push --force-with-lease`を用い、PR上で明記してください。
- コミットメッセージはConventional Commitsに準拠し、変更意図を明確に記述してください。
- コミット前に必ずビルドを実行し、コンパイルエラーがないことを確認してください。例: `gradle clean build`（必要に応じて `gradle shadowJar`）。エラーがある場合は修正してからコミットします。
