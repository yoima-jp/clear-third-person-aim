#!/usr/bin/env bash
# 全バージョン × ローダー × 互換構成のクライアントテストを順に実行し、結果を build/test-screenshots/summary.txt にまとめる。
# 使い方: bash testing/run-all-client-tests.sh [フィルタ (例: 26.3)]
# 各テストは実際のゲームウィンドウを開くので、ディスプレイのある環境で実行すること (Linux の CI では xvfb-run を前に付ける)。
set -u
cd "$(dirname "$0")/.."
filter="${1:-}"
logdir="build/test-logs"
mkdir -p "$logdir" build/test-screenshots
summary="build/test-screenshots/summary.txt"
: > "$summary"

# Fabric 1.21.4 以降は公式のクライアントゲームテスト、それ以外は自作ドライバ (runSelfTest)。
task_for() {
  local version="$1" loader="$2"
  if [ "$loader" = "fabric" ] && [ "$version" != "1.21.1" ]; then echo runClientGameTest; else echo runSelfTest; fi
}

# 構成: "" = MOD 単体, sodium, iris (シェーダーパック無し), iris-shader (テスト用最小シェーダーパック)
# Iris の NeoForge 版が存在しないバージョン (26.3) では iris 系を飛ばす。
combos=(
  "26.3 fabric ''" "26.3 fabric sodium" "26.3 fabric iris" "26.3 fabric iris-shader"
  "26.3 neoforge ''" "26.3 neoforge sodium"
  "26.2 fabric ''" "26.2 fabric sodium" "26.2 fabric iris" "26.2 fabric iris-shader"
  "26.2 neoforge ''" "26.2 neoforge sodium" "26.2 neoforge iris" "26.2 neoforge iris-shader"
  "26.1.2 fabric ''" "26.1.2 fabric sodium" "26.1.2 fabric iris" "26.1.2 fabric iris-shader"
  "26.1.2 neoforge ''" "26.1.2 neoforge sodium" "26.1.2 neoforge iris" "26.1.2 neoforge iris-shader"
  "1.21.11 fabric ''" "1.21.11 fabric sodium" "1.21.11 fabric iris" "1.21.11 fabric iris-shader"
  "1.21.11 neoforge ''" "1.21.11 neoforge sodium" "1.21.11 neoforge iris" "1.21.11 neoforge iris-shader"
  "1.21.1 fabric ''" "1.21.1 fabric sodium" "1.21.1 fabric iris" "1.21.1 fabric iris-shader"
  "1.21.1 neoforge ''" "1.21.1 neoforge sodium" "1.21.1 neoforge iris" "1.21.1 neoforge iris-shader"
)

for combo in "${combos[@]}"; do
  eval "set -- $combo"
  version="$1"; loader="$2"; compat="$3"
  if [ -n "$filter" ] && [[ "$version" != "$filter" ]]; then continue; fi
  task="$(task_for "$version" "$loader")"
  name="$version-$loader${compat:+-$compat}"
  args=(":$version-$loader:$task")
  [ -n "$compat" ] && args+=("-Pcta.compat=$compat")
  echo "=== $name"
  if ./gradlew "${args[@]}" > "$logdir/$name.log" 2>&1; then status=PASS; else status=FAIL; fi
  result="$(grep -a -o 'RESULT: [A-Z]*.*' "$logdir/$name.log" | tail -1)"
  echo "$name: $status ${result:-(no result line; see $logdir/$name.log)}" | tee -a "$summary"
done
