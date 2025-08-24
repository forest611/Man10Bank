#!/usr/bin/env bash
set -euo pipefail

# ==============================
# Man10Bank: Build & Deploy JAR
# ==============================
# - .env を自動で読み込み（存在する場合）
# - 環境変数 DEPLOY_DIR で配布先 plugins ディレクトリを指定
# - 既存の Man10Bank-*.jar を削除してからコピー
# - 引数に --no-build を渡すとビルドをスキップ

# .env 読み込み（エクスポート付き）
if [[ -f ./.env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

DEST_DIR=${DEPLOY_DIR:-}
if [[ -z "${DEST_DIR}" ]]; then
  echo "[Error] DEPLOY_DIR が設定されていません。.env か環境変数で設定してください。" >&2
  exit 1
fi
if [[ ! -d "${DEST_DIR}" ]]; then
  echo "[Error] コピー先ディレクトリが存在しません: ${DEST_DIR}" >&2
  exit 1
fi

if [[ "${1:-}" != "--no-build" ]]; then
  echo "[Build] gradle clean build を実行します"
  if command -v gradle >/dev/null 2>&1; then
    gradle clean build -x test
  else
    echo "gradle コマンドが見つかりません。PATH を確認してください。" >&2
    exit 1
  fi
fi

echo "[Find] 生成されたJARを探索します"
if [[ ! -d build/libs ]]; then
  echo "build/libs ディレクトリが見つかりません。ビルドに失敗しています。" >&2
  exit 1
fi
JAR_FILE=$(ls -t build/libs/*.jar 2>/dev/null | head -n 1 || true)
if [[ -z "${JAR_FILE}" || ! -f "${JAR_FILE}" ]]; then
  echo "JARが見つかりませんでした。build/libs/*.jar を確認してください。" >&2
  exit 1
fi

echo "[Clean] 既存の Man10Bank-*.jar を削除 (${DEST_DIR})"
find "${DEST_DIR}" -maxdepth 1 -type f -name 'Man10Bank-*.jar' -print -delete || true

echo "[Copy] ${JAR_FILE} -> ${DEST_DIR}/"
cp -f "${JAR_FILE}" "${DEST_DIR}/"
echo "[Done] コピー完了: ${DEST_DIR}/$(basename "${JAR_FILE}")"

