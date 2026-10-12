#!/usr/bin/env bash
# ============================================================================
# mt_build.sh - Gradle 构建守护（WSL/Linux 侧，bash 原生）
# ----------------------------------------------------------------------------
# 用途：
#   在 WSL/Linux 下构建本仓四条线之一（默认 :<子项目>:classes），并在判定「构建
#   实际已完成」后**强制终止** gradlew 进程组，规避 gradlew 的已知驻留缺陷。
#   本脚本是 Windows 侧 scripts/test/mt_build.ps1 的 Linux/WSL 对等件；两者职能
#   相同（构建 + 日志/产物双重验证 + 超时强杀），但本脚本为纯 bash，不调用任何
#   Windows 侧脚本或解释器。
#
# 已知缺陷（用户裁决，2026-10-13）：
#   gradlew 在**产出构建产物之后进程仍不自行退出**、长时间驻留（白占 CPU/内存），
#   效率极低。故本脚本不「等 gradlew 自然退出」，而是**按窗口轮询**判定完成：
#     - 单窗最长 60s（用户硬上限）——日志标记 + 产物双重验证；
#     - 一旦「成功标记（BUILD SUCCESSFUL）」或「产物正确（较 START 更新）」命中，
#       立即强杀 gradlew 进程组并继续后续流程；
#     - 未判成则再等一个 60s 窗口，直到 --windows 个窗口耗尽，判超时失败。
#
# 四类判据（任一命中即出轮询；出轮询后一律强杀进程组）：
#   1) ok_log_success  日志含 `BUILD SUCCESSFUL`
#   2) fail_log        日志含 `BUILD FAILED` 或 `FAILURE: Build failed`
#   3) ok_artifact     产物判据（task=jar     -> <子项目>/build/libs/*.jar
#                                  task=classes -> <子项目>/build/classes/java/main/**/*.class
#                      且 mtime 晚于本次运行起始 epoch START）
#   4) process_exited  launcher 进程已退出（kill -0 失败）-> wait 取退出码，
#                      再按日志标记定 OK/FAIL
#
# WSL/Linux 环境前提：
#   - JDK（Gradle 启动器）：/usr/lib/jvm/java-{17,21,25}-openjdk-amd64（各线映射见下 case）。
#   - ~/.gradle/gradle.properties 已配置代理 127.0.0.1:7897（http/https.proxyHost/Port）。
#   - ~/.m2/repository 内已放置 starengine_lib 等本地工件（Gradle 本地仓库依赖）。
#   - 依赖 `setsid`（建新会话/进程组以便整组强杀）与 GNU `find -newermt`。
#
# 与 mt_build.ps1 的对应关系：
#   强杀进程组（kill -TERM/-KILL -- -PGID）= Windows 侧 taskkill /T /F；
#   日志/产物双重验证口径一致，但本脚本按「窗口轮询 + 完成即杀」实现。
#   注意：Windows 侧 mt_build.ps1 另有 pushToGame/pushToDevRun 部署（写死 D:\.minecraft\...），
#   本脚本**不**承担该部署职能（见 --task 白名单）。
#
# 退出码：0 = OK；1 = FAIL（日志/进程/超时）；2 = 用法/参数错误（含缺 JDK、禁用 task）。
# ============================================================================

usage() {
    cat <<'EOF'
用法: scripts/test/mt_build.sh --version <1.21.1|1.20.1|26.1.2|fabric-1.20.1>
                               [--task classes|jar] [--timeout <秒/窗口>] [--windows <窗口数>]
                               [-h|--help]

  --version   构建线（子项目）：1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1
  --task      Gradle 任务：classes（默认）| jar。仅此两项；build 在 WSL 侧禁用。
  --timeout   单个窗口最长秒数（默认 60；硬上限 60，超出压回 60）
  --windows   窗口数（默认 40；窗口耗尽即判超时失败）
  -h,--help   显示本帮助并以退出码 0 结束
EOF
}

# ---------------------------------------------------------------------------
# 参数解析
# ---------------------------------------------------------------------------
VERSION=""
TASK="classes"
TIMEOUT=60
WINDOWS=40

need_val() {
    # $1 = 参数名；缺值则报错退出
    if [ -z "$2" ]; then
        echo "MT_BUILD_SH: ERROR 缺少 $1 的值" >&2
        exit 2
    fi
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)
            need_val "--version" "$2"
            VERSION="$2"; shift 2 ;;
        --task)
            need_val "--task" "$2"
            TASK="$2"; shift 2 ;;
        --timeout)
            need_val "--timeout" "$2"
            TIMEOUT="$2"; shift 2 ;;
        --windows)
            need_val "--windows" "$2"
            WINDOWS="$2"; shift 2 ;;
        -h|--help)
            usage; exit 0 ;;
        *)
            echo "MT_BUILD_SH: ERROR 未知参数 $1" >&2
            usage >&2
            exit 2 ;;
    esac
done

if [ -z "$VERSION" ]; then
    echo "MT_BUILD_SH: ERROR 必须指定 --version <1.21.1|1.20.1|26.1.2|fabric-1.20.1>" >&2
    usage >&2
    exit 2
fi

# 数值校验
case "$TIMEOUT" in ''|*[!0-9]*) echo "MT_BUILD_SH: ERROR --timeout 必须为正整数（秒）" >&2; exit 2 ;; esac
case "$WINDOWS" in ''|*[!0-9]*) echo "MT_BUILD_SH: ERROR --windows 必须为正整数（窗口数）" >&2; exit 2 ;; esac
if [ "$TIMEOUT" -lt 1 ]; then echo "MT_BUILD_SH: ERROR --timeout 必须 >= 1（秒）" >&2; exit 2; fi
if [ "$WINDOWS" -lt 1 ]; then echo "MT_BUILD_SH: ERROR --windows 必须 >= 1" >&2; exit 2; fi

# 用户硬上限：单窗最长 60s
if [ "$TIMEOUT" -gt 60 ]; then
    echo "MT_BUILD_SH: 提示 --timeout $TIMEOUT 超过硬上限 60s，已压回 60"
    TIMEOUT=60
fi

# --task 白名单：仅 classes / jar
case "$TASK" in
    classes|jar) : ;;
    build)
        echo "MT_BUILD_SH: ERROR 禁止 --task build：build 会触发写死 Windows 路径（D:\.minecraft\...）的部署任务（pushToGame/pushToDevRun），WSL 侧禁用；需整包请回 Windows mt_build.ps1。" >&2
        exit 2 ;;
    *)
        echo "MT_BUILD_SH: ERROR 不支持的 --task '$TASK'（白名单：classes / jar）" >&2
        exit 2 ;;
esac

# ---------------------------------------------------------------------------
# 版本 -> 子项目 / 启动器 JDK 映射
#   - 1.21.1        : NeoForge 21.1.x，Java 21
#   - 1.20.1        : Forge 47.4.x；启动器必须 >= 21，因为 :fabric-1.20.1 子项目的
#                     fabric-loom 1.14 在**配置期**要求 JVM 21，而 settings 会评估
#                     全部子项目；Forge 自身的 Java 17 编译由 Gradle toolchain 在
#                     /usr/lib/jvm 自动探测（无需启动器为 17）。
#   - 26.1.2        : NeoForge 26.1.2.x，Java 25
#   - fabric-1.20.1 : Fabric Loom 1.14，Java 21
# ---------------------------------------------------------------------------
SUB=""
JH=""
case "$VERSION" in
    1.21.1)         SUB="neoforge-1.21.1"; JH="/usr/lib/jvm/java-21-openjdk-amd64" ;;
    1.20.1)         SUB="forge-1.20.1";    JH="/usr/lib/jvm/java-21-openjdk-amd64" ;;
    26.1.2)         SUB="neoforge-26.1.2"; JH="/usr/lib/jvm/java-25-openjdk-amd64" ;;
    fabric-1.20.1)  SUB="fabric-1.20.1";   JH="/usr/lib/jvm/java-21-openjdk-amd64" ;;
    *)
        echo "MT_BUILD_SH: ERROR 未知 --version '$VERSION'（应为 1.21.1|1.20.1|26.1.2|fabric-1.20.1）" >&2
        exit 2 ;;
esac

# 启动前校验启动器 JDK 存在（用户规则：缺工具先向用户请示）
if [ ! -x "$JH/bin/java" ]; then
    echo "MT_BUILD_SH: ERROR 缺少 JDK $JH（用户规则：缺工具先向用户请示）" >&2
    exit 2
fi

# ---------------------------------------------------------------------------
# 定位仓库根：脚本自身路径上两级（scripts/test/mt_build.sh -> 仓根）
# ---------------------------------------------------------------------------
SCRIPT_PATH="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
SCRIPT_DIR="$(dirname "$SCRIPT_PATH")"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT" || { echo "MT_BUILD_SH: ERROR 无法进入仓根 $ROOT" >&2; exit 2; }
echo "MT_BUILD_SH_ROOT: $ROOT"

# ---------------------------------------------------------------------------
# 准备日志 + 起始 epoch
# ---------------------------------------------------------------------------
LOG="$(mktemp "/tmp/mt_build_sh.${VERSION}.XXXX.log")"
START="$(date +%s)"

# ---------------------------------------------------------------------------
# 产物判据（mtime 晚于 START）
# ---------------------------------------------------------------------------
artifact_fresh() {
    local dir found
    if [ "$TASK" = "jar" ]; then
        dir="$SUB/build/libs"
        [ -d "$dir" ] || return 1
        found="$(find "$dir" -name '*.jar' -newermt "@$START" 2>/dev/null | head -n 1)"
    else
        dir="$SUB/build/classes/java/main"
        [ -d "$dir" ] || return 1
        found="$(find "$dir" -name '*.class' -newermt "@$START" 2>/dev/null | head -n 1)"
    fi
    [ -n "$found" ]
}

# ---------------------------------------------------------------------------
# 启动 gradlew（新会话 -> 独立进程组，便于整组强杀）
# ---------------------------------------------------------------------------
echo "MT_BUILD_SH: RUN :$SUB:$TASK（JAVA_HOME=$JH，窗口 ${TIMEOUT}s x ${WINDOWS}，日志 $LOG）"
setsid env JAVA_HOME="$JH" ./gradlew ":$SUB:$TASK" --console=plain > "$LOG" 2>&1 &
PID=$!
# [2026-10-13 code_review 修复] 进程组一致性守卫（D1/D2）：
#   D1：ps 若在子进程完成 setsid(2) 前读到它，PID 仍属脚本自身进程组，捕获 PGID=脚本组，kill -- -$PGID 会误杀脚本与宿主。
#   D2：作业控制（set -m / 交互）下 setsid 会 fork，$! 立即退出、PGID 取空，旧守卫 [ -n "$PGID" ] 静默跳过，驻留进程永不被杀。
#   修法：轮询确认 PGID 稳定 == PID 后再断言；仅「PGID==PID 且非空且 != 脚本组」时 KILL_OK=1，否则走 WARN 分支。
PGID=""
for _i in 1 2 3 4 5 6 7 8 9 10; do
  PGID="$(ps -o pgid= -p "$PID" 2>/dev/null | tr -d ' ')"
  [ "$PGID" = "$PID" ] && break
  sleep 0.2
done
MINE="$(ps -o pgid= -p $$ | tr -d ' ')"
KILL_OK=0
if [ "$PGID" = "$PID" ] && [ -n "$PGID" ] && [ "$PGID" != "$MINE" ]; then
  KILL_OK=1
fi

# ---------------------------------------------------------------------------
# 轮询：每 5s 小检查，每 60s（一个窗口）打印一次心跳
# ---------------------------------------------------------------------------
CRIT=""
w=0
while [ "$w" -lt "$WINDOWS" ]; do
    w=$((w + 1))
    wstart="$(date +%s)"
    while : ; do
        # 1) 日志成功标记
        if grep -qF 'BUILD SUCCESSFUL' "$LOG" 2>/dev/null; then CRIT="ok_log_success"; break; fi
        # 2) 日志失败标记
        if grep -qE 'BUILD FAILED|FAILURE: Build failed' "$LOG" 2>/dev/null; then CRIT="fail_log"; break; fi
        # 3) 产物判据
        if artifact_fresh; then CRIT="ok_artifact"; break; fi
        # 4) launcher 进程退出
        if ! kill -0 "$PID" 2>/dev/null; then CRIT="process_exited"; break; fi
        # 窗口时间到则结束本窗，进入下一窗
        now="$(date +%s)"
        if [ $((now - wstart)) -ge "$TIMEOUT" ]; then break; fi
        sleep 5
    done
    [ -n "$CRIT" ] && break
    size="$(wc -c < "$LOG" 2>/dev/null | tr -d ' ')"
    echo "MT_BUILD_SH: WAIT ${w}/${WINDOWS}（${TIMEOUT}s 窗口，日志 ${size} 字节）仍无完成判据..."
done

# ---------------------------------------------------------------------------
# 出轮询后强杀进程组：仅当进程组一致性已确认（KILL_OK=1）时才执行
#   kill -TERM/-KILL -- -PGID  = Windows 侧 taskkill /T /F；
#   对应 gradlew「产物已出但进程不退出」的已知驻留缺陷：判成即杀，不等自然退出。
#   [2026-10-13 code_review 修复 D1/D2] PGID 一致性未确认时按裁决不杀其它组、打印
#   WARN 一行（含 setsid fork 致 PGID 取空的情形），把旧的静默失效改为响亮报警。
# ---------------------------------------------------------------------------
if [ "$KILL_OK" = "1" ]; then
    kill -TERM -- "-$PGID" 2>/dev/null
    sleep 3
    kill -KILL -- "-$PGID" 2>/dev/null
else
    echo "MT_BUILD_SH: WARN 进程组一致性未确认（PGID=$PGID, PID=$PID）——按裁决不杀其它组，驻留进程需人工检查"
fi

# ---------------------------------------------------------------------------
# 输出与退出码
# ---------------------------------------------------------------------------
print_ok() {
    echo "MT_BUILD_SH: OK ($1) :$SUB:$TASK（窗口 $w，日志 $LOG）"
    tail -3 "$LOG"
}

case "$CRIT" in
    ok_log_success|ok_artifact)
        print_ok "$CRIT"
        exit 0 ;;

    fail_log)
        echo "MT_BUILD_SH: FAIL（日志判据）"
        grep -B2 -A12 'What went wrong' "$LOG"
        exit 1 ;;

    process_exited)
        wait "$PID" 2>/dev/null
        rc=$?
        if grep -qF 'BUILD SUCCESSFUL' "$LOG" 2>/dev/null; then
            print_ok "process_exited"
            exit 0
        fi
        echo "MT_BUILD_SH: FAIL（进程退出 rc=${rc}）"
        tail -15 "$LOG"
        exit 1 ;;

    *)
        # 窗口耗尽：CRIT 为空
        echo "MT_BUILD_SH: FAIL（超时 ${WINDOWS} 窗口无完成判据）"
        tail -5 "$LOG"
        exit 1 ;;
esac
