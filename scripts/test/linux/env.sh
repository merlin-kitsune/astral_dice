#!/usr/bin/env bash
# ============================================================================
# scripts/test/linux/env.sh — Astral Dice 测试台 · Linux 侧最小环境检查
#
# 背景：scripts/test/ 的完整测试台是 Windows-only 原生 shell 台（user32/强制结束进程/
#       cmd.exe/IME），Linux 下不可用（结论与证据见 scripts/test/linux/README.md）。
#       本目录是 Linux 宿主的最小可用测试路径：env.sh / launch.sh / stop.sh。
#
# 本脚本只读：不运行 gradle、不启动游戏、不写 run/ 下任何文件。
#
# 用法：
#   scripts/test/linux/env.sh                        # 检查全部四条线
#   scripts/test/linux/env.sh --version 1.21.1       # 只检查一条线
#   scripts/test/linux/env.sh --quiet                # 只打印 CHECK/结果行
#
# 退出码：0 = 无 FAIL（可能含 WARN）；1 = 有 FAIL；2 = 用法错误
# 输出：CHECK: <key>=OK|WARN|FAIL — <detail>（机器行，UTF-8）
#       最终 ENV_RESULT: OK|WARN|FAIL
# ============================================================================
set -uo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$TEST_DIR/../../.." && pwd)"
CONF="$REPO_ROOT/scripts/test/mt.conf"
LOG_DIR="${MT_LINUX_LOG_DIR:-/tmp/mt-linux}"

FILTER=""
QUIET=0
FAILS=0
WARNS=0
OKS=0
ALL_VERSIONS="1.21.1 1.20.1 26.1.2 fabric-1.20.1"

usage() {
    sed -n '14,20p' "$0" | sed -E 's/^# ?//'
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)  FILTER="${2:-}"; shift 2 ;;
        --version=*) FILTER="${1#*=}"; shift ;;
        --quiet|-q) QUIET=1; shift ;;
        -h|--help)  usage; exit 0 ;;
        *) echo "用法错误：未知参数 $1（-h 看用法）" >&2; exit 2 ;;
    esac
done

if [ -n "$FILTER" ]; then
    case " $ALL_VERSIONS " in
        *" $FILTER "*) VERSIONS="$FILTER" ;;
        *) echo "用法错误：--version 只接受 $ALL_VERSIONS" >&2; exit 2 ;;
    esac
else
    VERSIONS="$ALL_VERSIONS"
fi

# ── 线路元数据（与 gradle 子项目 / toolchain / run 目录一一对应）────────────
line_project() {
    case "$1" in
        1.21.1)        echo "neoforge-1.21.1" ;;
        1.20.1)        echo "forge-1.20.1" ;;
        26.1.2)        echo "neoforge-26.1.2" ;;
        fabric-1.20.1) echo "fabric-1.20.1" ;;
        *) return 1 ;;
    esac
}
line_jdkver() {
    case "$1" in
        1.21.1)        echo 21 ;;
        1.20.1)        echo 17 ;;
        26.1.2)        echo 25 ;;
        fabric-1.20.1) echo 17 ;;
        *) return 1 ;;
    esac
}
jdk_home_for() {
    local n="$1" d
    for d in "/usr/lib/jvm/java-${n}-openjdk-amd64" "/usr/lib/jvm/java-${n}"; do
        if [ -x "$d/bin/java" ]; then printf '%s' "$d"; return 0; fi
    done
    return 1
}

log_ok()   { OKS=$((OKS + 1));     printf 'CHECK: %s=OK — %s\n' "$1" "$2"; }
log_warn() { WARNS=$((WARNS + 1)); printf 'CHECK: %s=WARN — %s\n' "$1" "$2"; }
log_fail() { FAILS=$((FAILS + 1)); printf 'CHECK: %s=FAIL — %s\n' "$1" "$2" >&2; }
log_info() { [ "$QUIET" = 1 ] || printf 'INFO: %s — %s\n' "$1" "$2"; }

# 从 gitignored 的 mt.conf 读值（CRLF 安全；不 source，避免值里的空格/中文出问题）。
conf_get() {
    local key="$1" def="${2:-}" v=""
    if [ -f "$CONF" ]; then
        v="$(grep -E "^[[:space:]]*${key}=" "$CONF" 2>/dev/null | tail -n 1 \
             | sed -E 's/^[^=]*=//' | tr -d '\r' | sed -E 's/^"//; s/"$//')"
    fi
    if [ -n "$v" ]; then printf '%s' "$v"; else printf '%s' "$def"; fi
}

# ── 1. 仓库与挂载 ───────────────────────────────────────────────────────────
missing=""
for f in settings.gradle gradlew gradle/wrapper/gradle-wrapper.jar scripts/test/win/mt.sh; do
    [ -f "$REPO_ROOT/$f" ] || missing="$missing $f"
done
if [ -n "$missing" ]; then
    log_fail repo "仓库根 $REPO_ROOT 缺失:$missing"
else
    log_ok repo "仓库根正常：$REPO_ROOT"
fi

# 挂载类型：共享盘/网络盘上 stat -f 会误报，优先用 findmnt 取真实 FSTYPE/SOURCE。
# 2026-10-31 原生 Linux 真机：仓库在 /mnt/f（ntfs3，Windows F: 同盘）；无 hgfs / 9p / fuse 共享盘。
fs_type=""
fs_src=""
if command -v findmnt >/dev/null 2>&1; then
    fs_type="$(findmnt -n -o FSTYPE -T "$REPO_ROOT" 2>/dev/null | head -n 1)"
    fs_src="$(findmnt -n -o SOURCE -T "$REPO_ROOT" 2>/dev/null | head -n 1)"
fi
if [ -z "$fs_type" ]; then
    fs_type="$(stat -f -c %T "$REPO_ROOT" 2>/dev/null || echo unknown)"
    fs_src="$(stat -f -c %S "$REPO_ROOT" 2>/dev/null || echo '?')"
fi
case "$fs_type" in
    *vmhgfs*|vmhgfs|hgfs|fuse*|9p*)
        log_warn mount "文件系统=$fs_type source=$fs_src（共享/网络盘，I/O 慢；Gradle 构建建议在本地盘）" ;;
    ntfs3|ntfs)
        log_info mount "文件系统=$fs_type source=$fs_src（Windows 同盘；符号链接/硬链接实测可用，注意 Windows 侧大小写不敏感）" ;;
    *)  log_info mount "文件系统=$fs_type source=$fs_src" ;;
esac

if [ -f "$REPO_ROOT/gradlew" ]; then
    if [ -x "$REPO_ROOT/gradlew" ]; then
        log_ok gradlew "gradlew 可执行"
    else
        log_warn gradlew "gradlew 无 +x（NTFS/FUSE 挂载常见）→ 请用 bash ./gradlew"
    fi
    dist_url="$(grep -E '^distributionUrl=' "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | cut -d= -f2- | tr -d '\r')"
    log_info gradle_wrapper "distributionUrl=$dist_url"
fi

# ── 2. 每条线的 toolchain JDK / run 目录 / mods ────────────────────────────
for v in $VERSIONS; do
    proj="$(line_project "$v")" || { log_fail "version_$v" "未知版本"; continue; }
    toolchain_ver="$(line_jdkver "$v")"
    run_dir="$REPO_ROOT/run/$v"

    jdk_home="$(jdk_home_for "$toolchain_ver" || true)"
    if [ -z "$jdk_home" ]; then
        log_fail "jdk_$v" "本线 toolchain=$toolchain_ver，但本机缺 JDK $toolchain_ver（期望 /usr/lib/jvm/java-$toolchain_ver-openjdk-amd64）"
    else
        java_line="$("$jdk_home/bin/java" -version 2>&1 | head -n 1 | tr -d '\r')"
        log_ok "jdk_$v" "本线 toolchain=$toolchain_ver 且本机在位：JAVA_HOME=$jdk_home（$java_line）"
    fi

    if [ ! -d "$run_dir" ]; then
        log_warn "run_dir_$v" "run/$v 不存在（首次 runClient 后由 Gradle 创建）"
    else
        log_ok "run_dir_$v" "run/$v 存在"
        if [ -d "$run_dir/mods" ]; then
            mods_n="$(ls -1 "$run_dir/mods" 2>/dev/null | wc -l | tr -d ' ')"
            # 大小写不敏感匹配 AutoJS/autojs/AutoJs…（实际产物是 AutoJS-<loader>-<ver>-<v>.jar）；
            # 只认以 .jar 结尾的文件，排除 *.jar.bak-* / *.jar.reobf-* 之类的备份。
            aj_n=0
            aj_names=""
            rh_n=0
            for jar in "$run_dir/mods"/*.jar; do
                [ -e "$jar" ] || continue
                b="$(basename "$jar")"
                case "$b" in
                    *[Aa][Uu][Tt][Oo][Jj][Ss]*.jar) aj_n=$((aj_n + 1)); aj_names="$aj_names $b" ;;
                    *[Rr][Hh][Ii][Nn][Oo]*.jar)     rh_n=$((rh_n + 1)) ;;
                esac
            done
            log_info "mods_$v" "run/$v/mods 共 $mods_n 个条目；autojs=$aj_n；rhino=$rh_n"
            if [ "$aj_n" -gt 0 ]; then
                log_ok "autojs_jar_$v" "AutoJS jar 在位（$aj_n 个）：$aj_names"
            else
                log_warn "autojs_jar_$v" "run/$v/mods 无 AutoJS*.jar（AutoJS 通道会 BLOCKED；1.20.1/fabric 也可由 build.gradle modRuntimeOnly 提供）"
            fi
        else
            log_warn "mods_$v" "run/$v/mods 不存在（env 装配尚未执行；Linux 侧暂无自动装配）"
        fi
    fi
done

# ── 3. AutoJS 端点（mt.conf / 环境变量；游戏未启动时属预期不可用）──────────
endpoint="$(conf_get MT_AUTOJS_ENDPOINT "${MT_AUTOJS_ENDPOINT:-http://127.0.0.1:25585/mcp}")"
timeout_ms="$(conf_get MT_AUTOJS_TIMEOUT_MS "${MT_AUTOJS_TIMEOUT_MS:-15000}")"
case "$endpoint" in
    http://*|https://*) ;;
    *) log_fail autojs_config "MT_AUTOJS_ENDPOINT 不是合法 http(s) URL：$endpoint" ;;
esac
base_url="${endpoint%/mcp}"
port="${base_url##*:}"
port="${port%%/*}"
log_info autojs_config "endpoint=$endpoint timeout_ms=$timeout_ms port=$port"
health_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$base_url/health" 2>/dev/null)"
health_code="${health_code:-000}"
if [ "$health_code" = "200" ]; then
    log_ok autojs_health "GET $base_url/health = 200（端点在线：三态=ONLINE）"
elif [ "$health_code" = "000" ]; then
    log_warn autojs_health "GET $base_url/health = 000（未运行：游戏未启动或 AutoJS 未就绪；launch.sh 启动后会复检）"
else
    log_fail autojs_health "GET $base_url/health = $health_code（端点有响应但非 200，AutoJS 通道异常；三态=HTTP_ERROR）"
fi
if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":$port "; then
    log_info autojs_port "TCP $port 已监听"
else
    log_info autojs_port "TCP $port 未监听"
fi

# ── 4. 游戏客户端残留（按 /proc/<pid>/cwd 落在仓库 run/ 下判定）───────────
scan_clients() {
    local p pid cwd
    for p in /proc/[0-9]*; do
        pid="${p#/proc/}"
        [ "$pid" = "$$" ] && continue
        [ -r "$p/comm" ] || continue
        [ "$(cat "$p/comm" 2>/dev/null || true)" = "java" ] || continue
        cwd="$(readlink "$p/cwd" 2>/dev/null || true)"
        case "$cwd" in
            "$REPO_ROOT"/run/*) printf '%s|%s\n' "$pid" "$cwd" ;;
        esac
    done
}
residue="$(scan_clients || true)"
if [ -n "$residue" ]; then
    while IFS='|' read -r pid cwd; do
        log_warn residue "PID $pid 客户端仍在运行（cwd=$cwd）→ 先跑 scripts/test/linux/stop.sh --version <ver>"
    done <<< "$residue"
else
    log_ok residue "无本流程游戏客户端残留（/proc 扫描）"
fi

# ── 5. Windows-only 配置值（mt.conf 机器本地配置在 Linux 上无效）──────────
win_vals=""
for key in MT_PACK_MODS_NEOFORGE MT_PACK_MODS_FORGE MT_PACK_MODS_NEOFORGE_26_1_2 MT_MCP_BIN; do
    val="$(conf_get "$key" "")"
    [ -n "$val" ] || continue
    if printf '%s' "$val" | grep -qE '^[A-Za-z]:[/\\]'; then
        win_vals="$win_vals $key=$val"
    fi
done
if [ -n "$win_vals" ]; then
    log_info conf_windows "mt.conf 含 Windows 专用路径（Windows 测试台的机器配置，Linux 侧忽略且不修改）：$win_vals"
else
    log_ok conf_windows "mt.conf 无 Windows 专用路径"
fi

# ── 6. 复用 run/start-<ver>.sh（gradle-launcher 产出后优先复用）──────────
for v in $VERSIONS; do
    if [ -f "$REPO_ROOT/run/start-$v.sh" ]; then
        log_info "start_sh_$v" "run/start-$v.sh 已存在（launch.sh 将优先复用它）"
    else
        log_info "start_sh_$v" "run/start-$v.sh 不存在（launch.sh 回退到 bash ./gradlew :<线>:runClient）"
    fi
done

# ── 7. cwd / 共享盘可写 / 四 JDK / git 换行 / 锁 / Gradle ──────────────────
cwd_now="$(pwd -P 2>/dev/null || echo '?')"
if [ "$cwd_now" = "$REPO_ROOT" ]; then
    log_ok cwd "当前 cwd = 仓库根"
else
    log_warn cwd "当前 cwd=$cwd_now（建议在仓库根执行；本脚本内部全用绝对路径，不受影响）"
fi

fs_type="$(findmnt -n -o FSTYPE -T "$REPO_ROOT" 2>/dev/null || true)"
if [ ! -w "$REPO_ROOT" ]; then
    log_fail repo_writable "仓库根不可写（检查挂载/权限）：$REPO_ROOT"
else
    case "$fs_type" in
        ntfs3|ntfs|ext4|ext3|xfs|btrfs)
            log_ok repo_writable "仓库根可写，文件系统=${fs_type:-unknown}（ntfs3 上 ln -s / 硬链接已实测可用）" ;;
        *vmhgfs*|hgfs|fuse*|9p*)
            log_warn repo_writable "仓库根在 $fs_type 上（共享/网络盘：I/O 慢、符号链接/硬链接可能不可用）" ;;
        *)
            log_ok repo_writable "仓库根可写，文件系统=${fs_type:-unknown}" ;;
    esac
fi

jdk_missing=""
for n in 17 21 25; do
    [ -x "/usr/lib/jvm/java-${n}-openjdk-amd64/bin/java" ] || jdk_missing="$jdk_missing $n"
done
if [ -n "$jdk_missing" ]; then
    log_fail jdk_paths "缺少 JDK:$jdk_missing（期望 /usr/lib/jvm/java-<17|21|25>-openjdk-amd64）"
else
    log_ok jdk_paths "toolchain JDK 17/21/25 均在位（本机可发现；daemon JVM 统一用 21）"
fi

daemon_jdk="/usr/lib/jvm/java-21-openjdk-amd64"
if [ -x "$daemon_jdk/bin/java" ]; then
    log_ok gradle_daemon_jvm "Gradle daemon JVM 需 ≥21：$daemon_jdk 在位（四线统一；toolchain 17/21/25 由 Gradle 自动发现）"
else
    log_fail gradle_daemon_jvm "缺少 Gradle daemon JVM ≥21：$daemon_jdk（用 JAVA_HOME=17 会在配置期崩：Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.）"
fi

autocrlf="$(git -C "$REPO_ROOT" config --get core.autocrlf 2>/dev/null || true)"
case "$autocrlf" in
    true)  log_ok git_autocrlf "core.autocrlf=true" ;;
    '')    log_warn git_autocrlf "core.autocrlf 未设置（Windows 侧测试台按 CRLF 假设；Linux 建议按 docs/agent/AGENTS-dsh-linux.md 处理）" ;;
    *)     log_warn git_autocrlf "core.autocrlf=$autocrlf（期望 true）" ;;
esac

mc_home="${MC_HOME:-/mnt/d/.minecraft}"
if [ ! -d "$mc_home" ]; then
    log_warn pack_root "整合包根目录不存在：$mc_home（设 MC_HOME，或确认 /mnt/d 已挂载）"
else
    missing=""
    for p in "狐の航空学 Voxy Edition" "1.20.1-Forge 模组测试" "26.1.2-NeoForge 模组测试" "1.20.1-Fabric 模组测试"; do
        [ -d "$mc_home/versions/$p/mods" ] || missing="$missing $p"
    done
    if [ -n "$missing" ]; then
        log_warn pack_dirs "整合包根=$mc_home，缺 mods 目录:$missing"
    else
        log_ok pack_dirs "整合包根=$mc_home，四线目标 mods 目录齐全"
    fi
fi

lock_dir="/mnt/f/MCProject/.agent-locks"
if [ -d "$lock_dir" ]; then
    others=""
    stale=""
    for f in "$lock_dir"/*.lock; do
        [ -e "$f" ] || continue
        b="$(basename "$f")"
        lpid="$(sed -n 's/^PID=\([0-9][0-9]*\).*/\1/p' "$f" 2>/dev/null | head -n 1)"
        if [ -n "$lpid" ] && ! kill -0 "$lpid" 2>/dev/null; then
            stale="$stale $b(PID=$lpid 已退出)"
            continue
        fi
        [ "$b" = "dsh.lock" ] && continue
        others="$others $b"
    done
    if [ -n "$others" ]; then
        log_warn locks_other "其它 agent 锁活跃：$others（写文件/启动 Gradle 前先确认）"
    else
        log_ok locks_other "无其它 agent 活跃锁（dsh.lock 属本宿主）"
    fi
    if [ -n "$stale" ]; then
        log_warn locks_stale "陈旧锁（持有进程已退出，不自行删除，报用户裁决）：$stale"
    fi
    log_info locks "锁目录 $lock_dir：$(ls -1 "$lock_dir" 2>/dev/null | tr '\n' ' ')"
else
    log_info locks "锁目录 $lock_dir 不存在"
fi

daemon_n="$(pgrep -c -f 'GradleDaemon' 2>/dev/null || true)"
wrapper_n="$(pgrep -c -f 'gradle-wrapper|gradlew' 2>/dev/null || true)"
log_info gradle "GradleDaemon=${daemon_n:-0} gradlew-wrapper=${wrapper_n:-0}（全队只有一个 Gradle 并发位；启动前确认没有别的 agent 在构建）"

# ── 汇总 ─────────────────────────────────────────────────────────────────────
printf '\n'
if [ "$FAILS" -gt 0 ]; then
    echo "ENV_RESULT: FAIL — $FAILS 项不满足，$WARNS 项提示"
    exit 1
elif [ "$WARNS" -gt 0 ]; then
    echo "ENV_RESULT: WARN — 0 项阻塞，$WARNS 项提示（多为游戏未启动/首次运行前的预期状态）"
    exit 0
else
    echo "ENV_RESULT: OK — 全部检查通过"
    exit 0
fi
