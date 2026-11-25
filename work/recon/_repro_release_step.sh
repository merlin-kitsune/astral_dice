#!/usr/bin/env bash
# verbatim extraction of .github/workflows/build.yml lines 285-350 (release-step jar collection).
# ONLY change: jar globs read from $SANDBOX/<proj>/build/libs (no Gradle, no gh, no network).
# Required env: BASE=<version>, GITHUB_SHA=<commit>, SANDBOX=<fake build root>
set -euo pipefail
cd D:/MCProject/astral_dice_multiloader
LINES=(
  "neoforge-1.21.1|neoforge-1.21.1|P0"
  "forge-1.20.1|forge-1.20.1|P0"
  "neoforge-26.1.2|neoforge-26.1.2|P1"
  "fabric-1.20.1|fabric-1.20.1|P1"
)
# 历史兼容判据 ——「这个 ref 有没有该子项目」只看**提交对象**，不看工作区：
#   `git cat-file -e "$SHA:<子项目>/gradle.properties"` 读的是被检出那个提交的树。
#   为什么它在 checkout 之后仍然可靠（而 `[[ -d ]]` / `[[ -f ]]` 不可靠）：
#   ① 本 job 的 Checkout 用了 `fetch-depth: 0` ⇒ 完整历史已在本地对象库，任何提交的树都能
#      离线解析 —— 不依赖网络，也不依赖该 ref 上有没有 tag；
#   ② 它回答的是「**那个提交里**有没有」：build/libs 里的残留产物、被前序步骤创建或删除的目录、
#      `.gitignore` 掉的文件都改不了答案；而 `[[ -d ]]` 只能看见当前工作区，分不清
#      「此 ref 本就没有该子项目」与「目录在、但内容不属于该 ref」；
#   ③ 判定对象与构建对象是同一个提交 ⇒ 不会出现「拿 A 提交的树判定、拿 B 提交的产物上传」。
#   ⚠️ 之所以要这条：fabric-1.20.1（2026-10-01 才并入）与 neoforge-26.1.2（2026-09 才有）
#      在更早的 tag 上**目录根本不存在** —— 那些 ref 上不硬校验，只打 warning 跳过。
SHA="${GITHUB_SHA:-$(git rev-parse HEAD)}"
if ! git cat-file -e "${SHA}^{commit}" 2>/dev/null; then
  # 提交对象不在本地库里 ⇒ 子项目存在性无法判定。宁可红，也不要把「判不出来」当成「不存在」。
  echo "::error::commit $SHA is not in the local object database — cannot resolve subproject presence"
  exit 1
fi
JARS=()
MISSING=()
for entry in "${LINES[@]}"; do
  line="${entry%%|*}"
  rest="${entry#*|}"
  proj="${rest%%|*}"
  prio="${rest##*|}"
  props="$proj/gradle.properties"
  if ! git cat-file -e "${SHA}:${props}" 2>/dev/null; then
    echo "::warning::skipping $line (subproject not present at this ref)"
    continue
  fi
  # 产物名 = astral_dice-<mod_version>.jar，mod_version 形如 `1.5.0+neoforge_1.21.1`。
  # 用 BASE（已剥掉 +后缀）收紧 glob ⇒ 只命中**本版本**的 jar。⛔ 不能退回
  #   `astral_dice-*.jar`：build/libs 从不清理，实测 neoforge-26.1.2/build/libs 同时躺着
  #   1.2.1~1.5.0 共 14 个 jar，松散 glob 会把历次旧 jar 一并塞进本次 Release。
  # 未命中时 glob 保持字面量 ⇒ `[[ -f ]]` 为假 ⇒ 计 0。
  hits=()
  for j in "$SANDBOX/$proj"/build/libs/astral_dice-"$BASE"+*.jar; do
    if [[ -f "$j" ]]; then hits+=("$j"); fi
  done
  if [[ ${#hits[@]} -eq 0 ]]; then
    echo "::error::missing jar for $line: $proj/build/libs/astral_dice-$BASE+*.jar"
    # 顺带把该目录实际有什么打出来，便于一眼定位（目录不存在时由 || true 兜住）
    ls -1 "$SANDBOX/$proj/build/libs" 2>/dev/null | sed 's/^/    present: /' || true
    MISSING+=("$line")
  elif [[ ${#hits[@]} -gt 1 ]]; then
    # 同版本命中多个 = 产物目录不干净（例如混入 -sources.jar）：宁可报错，也不猜哪一个是交付件。
    echo "::error::ambiguous jars for $line (expected 1, got ${#hits[@]})"
    printf '    present: %s\n' "${hits[@]##*/}"
    MISSING+=("$line")
  else
    JARS+=("${hits[0]}")
    echo "jar ok ($prio) $line: ${hits[0]##*/}"
  fi
done
if [[ ${#MISSING[@]} -gt 0 ]]; then
  # 一次把**所有**缺件的线报全（而非撞第一条就退），CI 上跑一遍就能看全要补哪几件。
  echo "::error::aborting release: ${#MISSING[@]} line(s) without a usable jar (${MISSING[*]})"
  exit 1
fi
if [[ ${#JARS[@]} -eq 0 ]]; then
  echo "::error::no jars collected at all — refusing to publish an empty release"
  exit 1
fi
