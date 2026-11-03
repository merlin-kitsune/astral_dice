#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/lib/common.sh — Astral Dice 测试台 · Windows(git bash) 共享助手
#
# 定位：被 scripts/test/win/{env,launch,stop}.sh `source`，不单独执行。
# 蓝本：scripts/test/linux/{env,launch,stop}.sh（只读复用其契约，不修改它们）。
#
# 本机约束（2026-11-01 实测，MSYS2 UCRT64 / bash 5.3.15）：
#   * 可用：pwsh 7.6.9、curl 8.22、python3、taskkill、netstat、cmd.exe
#   * 不可用：jq / wget / setsid / findmnt / pgrep / wmic；`ps -o` 不支持
#   * /tmp 写入被沙箱拒绝 → 临时文件只走 Windows 真 TEMP 或仓库内（绝不落 /tmp）
#   * curl 一律 --noproxy '*'（本机 127.0.0.1 经代理会 000）
#   * pwsh 一律 -NoProfile -NonInteractive
#   * 进程识别一律 CIM（Get-CimInstance Win32_Process），不按进程名批量杀 java
#
# 机器行契约（docs/features/windows-channel-decoupling-spec.md §3）：
#   CHECK: <key>=OK|WARN|FAIL — <detail>
#   INFO:  <key> — <detail>
#   ENV_RESULT: OK|WARN|FAIL — <detail>
#   LAUNCH: … / WIN_STOP: … / PURGE: …
# ============================================================================

MT_COMMON_VERSION="1.0.0-win"

# ── 版本表（四线齐全；注意 Mt.Paths.psm1 的 $VERSIONS 至今只有三条线）───────
MT_ALL_VERSIONS="1.21.1 1.20.1 26.1.2 fabric-1.20.1"
MT_WORLD_NAME="testworld"

mt_all_versions() { printf '%s' "$MT_ALL_VERSIONS"; }

mt_is_version() {
    case " $MT_ALL_VERSIONS " in *" ${1:-} "*) return 0 ;; esac
    return 1
}

mt_subproject() {
    case "${1:-}" in
        1.21.1)        printf '%s' 'neoforge-1.21.1' ;;
        1.20.1)        printf '%s' 'forge-1.20.1' ;;
        26.1.2)        printf '%s' 'neoforge-26.1.2' ;;
        fabric-1.20.1) printf '%s' 'fabric-1.20.1' ;;
        *) return 1 ;;
    esac
}

mt_loader() {
    case "${1:-}" in
        1.21.1|26.1.2) printf '%s' 'neoforge' ;;
        1.20.1)        printf '%s' 'forge' ;;
        fabric-1.20.1) printf '%s' 'fabric' ;;
        *) return 1 ;;
    esac
}

mt_toolchain() {
    case "${1:-}" in
        1.21.1)        printf '%s' '21' ;;
        1.20.1)        printf '%s' '17' ;;
        26.1.2)        printf '%s' '25' ;;
        fabric-1.20.1) printf '%s' '17' ;;
        *) return 1 ;;
    esac
}

# AutoJS 只投放 1.21.1 与 26.1.2；1.20.1 的 runClient 没有 AutoJS mod。
# fabric-1.20.1 不同：它是**条件依赖**（fabric-1.20.1/build.gradle:182-190 只在
#   -PautojsTestMod=1 时才 modRuntimeOnly 引入 AutoJS），所以这里不写死 false：
#   交给 mt_fab_autojs_enabled 实测；未开启时**显式**说明回退 RCON（127.0.0.1:25575），
#   绝不静默返回 false（静默 false 会让上层把「没装 AutoJS」误判成「永远不可用」）。
mt_line_has_autojs() {
    case "${1:-}" in
        1.21.1|26.1.2) return 0 ;;
        fabric-1.20.1)
            if mt_fab_autojs_enabled "${MT_SIDE:-}"; then
                return 0
            fi
            printf 'MT_WARN: fabric-1.20.1 无 AutoJS（-PautojsTestMod 未开启，见 fabric-1.20.1/build.gradle:182-190）→ 回退 RCON（127.0.0.1:%s）\n' \
                "$(mt_fab_rcon_port)" >&2
            return 1
            ;;
        *) return 1 ;;
    esac
}

# ── 路径变换（cygpath 优先，兜底手写；只在无 cygpath 时使用兜底）────────────
mt_to_msys() {
    local p="${1:-}" drive rest
    [ -n "$p" ] || return 1
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -u "$p" 2>/dev/null && return 0
    fi
    case "$p" in
        [A-Za-z]:[\\/]*)
            drive="${p:0:1}"
            drive="$(printf '%s' "$drive" | tr 'A-Z' 'a-z')"
            rest="${p:2}"
            rest="${rest//\\//}"
            printf '/%s%s\n' "$drive" "$rest"
            ;;
        *) printf '%s\n' "$p" ;;
    esac
}

mt_to_win() {
    local p="${1:-}"
    [ -n "$p" ] || return 1
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$p" 2>/dev/null && return 0
    fi
    printf '%s\n' "$p"
}

# 仓库根：git 优先（--show-toplevel 给 F:/… 风格），失败则从起点向上找 settings.gradle。
mt_resolve_repo_root() {
    local start="${1:-$PWD}" top d
    if command -v git >/dev/null 2>&1; then
        top="$(git -C "$start" rev-parse --show-toplevel 2>/dev/null | head -n 1)"
        if [ -n "$top" ]; then
            mt_to_msys "$top"
            return 0
        fi
    fi
    d="$(cd "$start" 2>/dev/null && pwd)" || d=""
    [ -n "$d" ] || return 1
    while [ -n "$d" ] && [ "$d" != "/" ]; do
        if [ -f "$d/settings.gradle" ] || [ -f "$d/settings.gradle.kts" ]; then
            printf '%s\n' "$d"
            return 0
        fi
        d="$(dirname "$d")"
    done
    return 1
}

mt_conf_path() { printf '%s/scripts/test/mt.conf' "${MT_REPO_ROOT:-$PWD}"; }

# 从 gitignored 的 mt.conf 读值（CRLF 安全；不 source，避免值里的空格/中文/引号出问题）。
mt_conf_get() {
    local key="${1:-}" def="${2:-}" conf v=""
    conf="$(mt_conf_path)"
    if [ -f "$conf" ]; then
        v="$(grep -E "^[[:space:]]*${key}=" "$conf" 2>/dev/null | tail -n 1 \
             | sed -E 's/^[^=]*=//' | tr -d '\r' | sed -E 's/^"//; s/"$//')"
    fi
    if [ -n "$v" ]; then printf '%s' "$v"; else printf '%s' "$def"; fi
}

# ── 机器行 ────────────────────────────────────────────────────────────────
mt_ok()   { MT_OKS=$(( ${MT_OKS:-0} + 1 ));         printf 'CHECK: %s=OK — %s\n' "$1" "$2"; }
mt_warn() { MT_WARNS=$(( ${MT_WARNS:-0} + 1 ));     printf 'CHECK: %s=WARN — %s\n' "$1" "$2"; }
mt_fail() { MT_FAILS=$(( ${MT_FAILS:-0} + 1 ));     printf 'CHECK: %s=FAIL — %s\n' "$1" "$2" >&2; }
mt_info() { [ "${MT_QUIET:-0}" = 1 ] || printf 'INFO: %s — %s\n' "$1" "$2"; }

mt_launch()  { printf 'LAUNCH: %s\n' "$*"; }
mt_launch_err() { printf 'LAUNCH: %s\n' "$*" >&2; }
mt_winstop() { printf 'WIN_STOP: %s\n' "$*"; }
mt_winstop_err() { printf 'WIN_STOP: %s\n' "$*" >&2; }
mt_purge()   { printf 'PURGE: %s\n' "$*"; }
mt_detail()  { printf '  %s\n' "$*"; }

# ── PowerShell 内核 / 外部工具 ────────────────────────────────────────────
# 用途仅限「宿主能力」：CIM(Win32_Process) 取进程命令行做按线判线（bash 无等价物）。
# ⚠️ 这不是测试资产——scripts/ 下 .ps1 已全部废弃，这里调的是 PowerShell **解释器**：
#   ① pwsh 7（若装了） ② 系统自带 Windows PowerShell 5.1（Win10/11 恒有）。
# MT_PWSH_EXE 可直接钉死解释器（反向判据「没有解释器必须 ERROR」用它自证）。
mt_pwsh_exe() {
    local exe="${MT_PWSH_EXE:-}"
    if [ -n "$exe" ]; then printf '%s' "$exe"; return 0; fi
    if command -v pwsh >/dev/null 2>&1; then exe="$(command -v pwsh)"
    elif [ -x "/c/Program Files/PowerShell/7/pwsh.exe" ]; then exe="/c/Program Files/PowerShell/7/pwsh.exe"
    elif [ -x "/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe" ]; then exe="/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"
    else return 127
    fi
    MT_PWSH_EXE="$exe"
    printf '%s' "$exe"
}

mt_pwsh() {
    local exe
    exe="$(mt_pwsh_exe 2>/dev/null)" || return 127
    "$exe" -NoProfile -NonInteractive -Command "$1"
}

mt_have() { command -v "${1:-}" >/dev/null 2>&1; }

# Windows 真 TEMP（git bash 的 $TEMP 指 /tmp，写入被拒；这里问 cmd 拿真值）。
mt_win_temp() {
    local t=""
    if [ -n "${MT_WIN_TEMP:-}" ]; then printf '%s' "$MT_WIN_TEMP"; return 0; fi
    if mt_have cmd; then
        t="$(cmd //c 'echo %TEMP%' 2>/dev/null | tr -d '\r\n')"
    fi
    case "$t" in
        [A-Za-z]:*) t="$(mt_to_msys "$t")" ;;
        *) t="" ;;
    esac
    [ -n "$t" ] || t="${USERPROFILE:-$HOME}/AppData/Local/Temp"
    MT_WIN_TEMP="$t"
    printf '%s' "$t"
}

# 临时目录：$TEMP → run/.tmp → 都不行则失败。/tmp 永不使用。
mt_tmp_dir() {
    local d probe
    for d in "${MT_WIN_TMP_DIR:-}" "$(mt_win_temp)" "${MT_REPO_ROOT:-$PWD}/run/.mt-tmp"; do
        [ -n "$d" ] || continue
        [ -d "$d" ] || mkdir -p "$d" 2>/dev/null
        [ -d "$d" ] || continue
        probe="$d/.mt-probe.$$"
        if ( : > "$probe" ) 2>/dev/null; then
            rm -f "$probe" 2>/dev/null
            printf '%s' "$d"
            return 0
        fi
    done
    return 1
}

mt_tmpfile() {
    local d
    d="$(mt_tmp_dir)" || return 1
    mktemp "$d/${1:-mt}.XXXXXX" 2>/dev/null
}

# 可写探测：$1 = 目录，返回 0 表示可写。
mt_dir_writable() {
    local d="${1:-}" probe
    [ -n "$d" ] || return 1
    [ -d "$d" ] || return 1
    probe="$d/.mt-wprobe.$$"
    if ( : > "$probe" ) 2>/dev/null; then
        rm -f "$probe" 2>/dev/null
        return 0
    fi
    return 1
}

# ── CIM：java 进程 (pid, CommandLine) ─────────────────────────────────────
# 输出每行 "<pid>\t<CommandLine>"；CIM 失败时 stderr 打 CIM_ERROR … 且返回非 0。
mt_cim_java_rows() {
    local ps
    ps="$(cat <<'PS'
$ErrorActionPreference = 'Stop'
try {
    $p = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction Stop)
} catch {
    [Console]::Error.WriteLine('CIM_ERROR ' + $_.Exception.Message)
    exit 3
}
foreach ($x in $p) { "{0}`t{1}" -f [int]$x.ProcessId, ([string]$x.CommandLine) }
PS
)"
    mt_pwsh "$ps"
}

# 命令行是否属于某线（与 Mt.Paths.psm1:141-156 Get-MtProcessMarkers 同款标记串）：
#   ":<子项目>:"（gradle 任务选择器）、"run\<ver>"、"run/<ver>"
# 刻意不用裸子项目名：那同时是仓库目录名，会误命中 IDE / 索引进程。
mt_cmdline_matches_version() {
    local v="${1:-}" cmd="${2:-}" sub lc needle
    sub="$(mt_subproject "$v")" || return 1
    lc="$(printf '%s' "$cmd" | tr 'A-Z' 'a-z')"
    needle=":${sub}:"
    case "$lc" in *"$needle"*) return 0 ;; esac
    case "$lc" in *"run/$v"*) return 0 ;; esac
    case "$lc" in *"run\\$v"*) return 0 ;; esac
    # fabric-1.20.1 是双 run 目录 fabric-1.20.1/run/<client|server>，路径里没有版本号，
    # 所以上面的 run/<版本> needle 命中不了；改为「子项目名 + run + client|server」兜底匹配，
    # 避免在双引号内写反斜杠字面量（\ 在 double-quote 里是转义，曾把行尾引号吃掉导致解析错）。
    case "$lc" in *"$MT_FAB_SUBPROJECT"*run*client*) return 0 ;; esac
    case "$lc" in *"$MT_FAB_SUBPROJECT"*run*server*) return 0 ;; esac
    case "$lc" in
        *"$MT_FAB_SUBPROJECT"*)
            case "$lc" in *runclient*|*runserver*) return 0 ;; esac
            ;;
    esac

    return 1
}

mt_rows_for_version() {
    local v="${1:-}" rows pid cmd
    rows="$(mt_cim_java_rows 2>/dev/null)" || return 1
    [ -n "$rows" ] || return 0
    while IFS=$'\t' read -r pid cmd; do
        [ -n "$pid" ] || continue
        if mt_cmdline_matches_version "$v" "$cmd"; then
            printf '%s\t%s\n' "$pid" "$cmd"
        fi
    done <<< "$rows"
}

# 所有线的残留（--all 用）；输出 "<pid>\t<ver>\t<cmdline>"。
mt_rows_for_any_version() {
    local rows pid cmd v hit
    rows="$(mt_cim_java_rows 2>/dev/null)" || return 1
    [ -n "$rows" ] || return 0
    while IFS=$'\t' read -r pid cmd; do
        [ -n "$pid" ] || continue
        hit=""
        for v in $MT_ALL_VERSIONS; do
            if mt_cmdline_matches_version "$v" "$cmd"; then hit="$v"; break; fi
        done
        [ -n "$hit" ] && printf '%s\t%s\t%s\n' "$pid" "$hit" "$cmd"
    done <<< "$rows"
}

mt_pid_alive() {
    local pid="${1:-}" rows
    case "$pid" in ''|*[!0-9]*) return 1 ;; esac
    rows="$(mt_cim_java_rows 2>/dev/null)" || return 1
    printf '%s\n' "$rows" | while IFS=$'\t' read -r p _c; do
        [ "$p" = "$pid" ] && { echo alive; break; }
    done | grep -q alive
}

# 强杀进程树。git bash 会把 /PID 当路径，故用 //PID（MSYS 还原成 /PID）。
# rc 不可靠（无此进程时 taskkill 仍可能 rc=0），调用方必须用 CIM 复核。
mt_taskkill_tree() {
    local pid="${1:-}"
    case "$pid" in ''|*[!0-9]*) return 2 ;; esac
    if mt_have taskkill; then
        taskkill //PID "$pid" //T //F >/dev/null 2>&1 || true
    else
        cmd //c "taskkill /PID $pid /T /F" >/dev/null 2>&1 || true
    fi
    return 0
}

mt_gradle_daemon_rows() {
    local rows pid cmd
    rows="$(mt_cim_java_rows 2>/dev/null)" || return 1
    [ -n "$rows" ] || return 0
    while IFS=$'\t' read -r pid cmd; do
        [ -n "$pid" ] || continue
        case "$cmd" in *GradleDaemon*) printf '%s\t%s\n' "$pid" "$cmd" ;; esac
    done <<< "$rows"
}

# ── JDK 发现（Windows 布局：Zulu / Java / Adoptium / Corretto / Gradle 自动下载）──
mt_jdk_candidates() {
    local n="${1:-}" c
    [ -n "$n" ] || return 1
    for c in \
        "/c/Program Files/Zulu/zulu-$n" \
        "/c/Program Files/Java/jdk-$n"* \
        "/c/Program Files/Eclipse Adoptium/jdk-$n"* \
        "/c/Program Files/Amazon Corretto/jdk$n"* \
        "$HOME/.gradle/jdks/"*"$n"* \
        "$HOME/.jdks/"*"$n"* \
        "${JAVA_HOME:-}"
    do
        [ -n "$c" ] || continue
        [ -d "$c" ] || continue
        if [ -x "$c/bin/java.exe" ] || [ -x "$c/bin/java" ]; then
            printf '%s\n' "$c"
        fi
    done
}

mt_find_jdk() {
    local n="${1:-}" d
    [ -n "$n" ] || return 1
    while IFS= read -r d; do
        [ -n "$d" ] || continue
        printf '%s' "$d"
        return 0
    done <<< "$(mt_jdk_candidates "$n")"
    return 1
}

# 精确 major 匹配：把全部候选都跑一遍 -version，返回第一个主版本 == n 的 JDK。
# 用于 env.sh 的逐线 toolchain 检查（JAVA_HOME 可能指向别的版本，不能直接采信）。
mt_find_jdk_exact() {
    local n="${1:-}" c major
    [ -n "$n" ] || return 1
    # ⚠ 必须用 read 循环而不是 `for c in $(...)`：Windows 路径含空格（Program Files），
    # 词分割会把 "/c/Program Files/Zulu/zulu-21" 拆成两段。
    while IFS= read -r c; do
        [ -n "$c" ] || continue
        major="$(mt_java_major "$c/bin/java.exe" 2>/dev/null || mt_java_major "$c/bin/java" 2>/dev/null || true)"
        [ "$major" = "$n" ] || continue
        printf '%s' "$c"
        return 0
    done <<< "$(mt_jdk_candidates "$n")"
    return 1
}

mt_java_major() {
    local java_exe="${1:-$JAVA_HOME/bin/java}" out
    [ -x "$java_exe" ] || java_exe="$(command -v java 2>/dev/null || true)"
    [ -n "$java_exe" ] || return 1
    out="$("$java_exe" -version 2>&1 | head -n 1 | tr -d '\r')"
    printf '%s\n' "$out" | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n 1
}

mt_java_line() {
    local java_exe="${1:-}"
    [ -n "$java_exe" ] || return 1
    [ -x "$java_exe" ] || return 1
    "$java_exe" -version 2>&1 | head -n 1 | tr -d '\r'
}

# Gradle daemon JVM 必须 ≥21（实测：JAVA_HOME=17 时配置期直接崩）。
# 返回第一个 major ≥21 的 JDK；失败返回 1。
mt_find_daemon_jdk() {
    local c major
    if [ -n "${MT_GRADLE_DAEMON_JAVA_HOME:-}" ]; then
        if [ -x "$MT_GRADLE_DAEMON_JAVA_HOME/bin/java.exe" ] || [ -x "$MT_GRADLE_DAEMON_JAVA_HOME/bin/java" ]; then
            printf '%s' "$MT_GRADLE_DAEMON_JAVA_HOME"
            return 0
        fi
    fi
    # 同样必须 read 循环：路径含空格（"Program Files"）时 for-in-$(...) 会被词分割。
    local n
    for n in 21 25 22 23 24; do
        while IFS= read -r c; do
            [ -n "$c" ] || continue
            major="$(mt_java_major "$c/bin/java.exe" 2>/dev/null || mt_java_major "$c/bin/java" 2>/dev/null || true)"
            case "$major" in
                ''|*[!0-9]*) continue ;;
            esac
            if [ "$major" -ge 21 ]; then
                printf '%s' "$c"
                return 0
            fi
        done <<< "$(mt_jdk_candidates "$n")"
    done
    return 1
}

# ── AutoJS（端点只做 GET /health 与 POST /mcp 的 initialize + tools/call）────
mt_aj_endpoint() {
    local ep="${MT_AUTOJS_ENDPOINT:-}"
    [ -n "$ep" ] || ep="$(mt_conf_get MT_AUTOJS_ENDPOINT 'http://127.0.0.1:25585/mcp')"
    printf '%s' "$ep"
}

mt_aj_base() {
    local ep
    ep="$(mt_aj_endpoint)"
    printf '%s' "${ep%/mcp}"
}

mt_aj_health_code() {
    local base c
    base="$(mt_aj_base)"
    c="$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' -m "${1:-3}" "$base/health" 2>/dev/null)"
    [ -n "$c" ] || c="000"
    printf '%s' "$c"
}

mt_aj_health_body() {
    local base
    base="$(mt_aj_base)"
    curl -fsS --noproxy '*' -m "${1:-3}" "$base/health" 2>/dev/null | tr -d '\r'
}

# 解析 tools/call status 的响应：成功打印 detail 并返回 0。
# 入参：文件路径，或 "-" 表示从 stdin 读（默认 "-"；本机可能无处可写临时文件）。
mt_aj_parse_status() {
    local src="${1:--}" txt pys
    [ -n "$src" ] || src="-"
    if [ "$src" = "-" ]; then
        txt="$(cat 2>/dev/null || true)"
    else
        [ -f "$src" ] || return 1
        txt="$(cat "$src" 2>/dev/null || true)"
    fi
    [ -n "$txt" ] || return 1
    if mt_have python3; then
        # 脚本用 -c 传参，stdin 留给响应正文（heredoc 会占住 stdin，不能用 `python3 - <<PY`）。
        pys="$(cat <<'PY'
import json, sys

txt = sys.stdin.read()
try:
    obj = json.loads(txt)
except Exception:
    obj = None
    for line in txt.splitlines():
        s = line.strip()
        if s.startswith('data:'):
            try:
                obj = json.loads(s[5:].strip())
            except Exception:
                continue
if not isinstance(obj, dict):
    sys.exit(1)
res = obj.get('result') or {}
text = ''
for c in (res.get('content') or []):
    if isinstance(c, dict) and c.get('type') == 'text':
        text = c.get('text') or ''
        break
if not text:
    sys.exit(1)
try:
    data = json.loads(text)
except Exception:
    sys.exit(1)
client = None
for s in (data.get('sides') or []):
    if isinstance(s, dict) and s.get('side') == 'client':
        client = s
        break
if not client or client.get('available') is not True:
    sys.exit(1)
print('state="%s" registered=%s available=true'
      % (client.get('state', ''), str(client.get('registered')).lower()))
PY
)"
        printf '%s' "$txt" | python3 -c "$pys"
        return $?
    fi
    # 无 python3：退化为文本匹配（只判 available 布尔，够 launch 收停判据用）
    txt="$(printf '%s' "$txt" | sed 's/\\"/"/g')"
    case "$txt" in *'"side":"client"'*) ;; *) return 1 ;; esac
    case "$txt" in *'"available":true'*) ;; *) return 1 ;; esac
    printf '%s' 'state="?" registered=? available=true'
    return 0
}

# 去掉 HTTP 响应头块（curl -D - 的产物），原样输出 body。
mt_strip_headers() {
    awk 'BEGIN { h = 1 } h && /^\r?$/ { h = 0; next } !h { print }'
}

# 完整 AutoJS 客户端侧探测：health==ok 且 status(client available=true)。
# 成功 stdout 打印 'health=ok state=… registered=… available=true'；失败返回 1。
# ⚠ 全程不落临时文件（本机 /tmp、$TEMP、run/ 都可能被 ACL/沙箱拒写）：
#   -D - 把响应头并进 stdout，-w 用哨兵串取 HTTP 码，body 用变量+管道解析。
mt_aj_probe() {
    local ep base tok resp code sid hb detail body
    local -a auth=() sid_args=()
    ep="$(mt_aj_endpoint)"
    base="${ep%/mcp}"
    tok="$(mt_conf_get MT_AUTOJS_TOKEN '')"
    hb="$(mt_aj_health_body 2)" || hb=""
    [ "$hb" = "ok" ] || return 1
    [ -n "$tok" ] && auth=(-H "Authorization: Bearer $tok")

    resp="$(curl -sS --noproxy '*' -m 6 -D - -w '\n__MT_CODE__%{http_code}' -X POST "$ep" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json, text/event-stream' \
        "${auth[@]}" \
        --data '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"win-launch-sh","version":"1.0.0"}}}' \
        2>/dev/null || true)"
    code="$(printf '%s' "$resp" | sed -n 's/.*__MT_CODE__\([0-9][0-9][0-9]\).*/\1/p' | tail -n 1)"
    if [ "${code:-000}" != "200" ]; then
        return 1
    fi
    sid="$(printf '%s' "$resp" | awk -F': ' 'tolower($1)=="mcp-session-id"{v=$2} END{if(v!="")print v}' 2>/dev/null | tr -d '\r' | head -n 1)"
    [ -n "$sid" ] && sid_args=(-H "Mcp-Session-Id: $sid")

    curl -sS --noproxy '*' -m 6 -o /dev/null -X POST "$ep" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json, text/event-stream' \
        "${auth[@]}" "${sid_args[@]}" \
        --data '{"jsonrpc":"2.0","method":"notifications/initialized"}' >/dev/null 2>&1 || true

    resp="$(curl -sS --noproxy '*' -m 8 -D - -w '\n__MT_CODE__%{http_code}' -X POST "$ep" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json, text/event-stream' \
        "${auth[@]}" "${sid_args[@]}" \
        --data '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"status","arguments":{}}}' \
        2>/dev/null || true)"
    code="$(printf '%s' "$resp" | sed -n 's/.*__MT_CODE__\([0-9][0-9][0-9]\).*/\1/p' | tail -n 1)"
    [ "${code:-000}" = "200" ] || return 1
    # curl 的 -w 尾巴会跟在响应正文后面（"__MT_CODE__200"），mt_strip_headers 只去
    # HTTP 头不去它 ⇒ 不剥掉的话 json.loads 必定失败，probe 恒 return 1（2026-11-02 实测：
    # launch 就绪判据永远拿不到 AutoJS，一直等到 600s 超时 exit 12）。
    body="$(printf '%s' "$resp" | mt_strip_headers | sed '/^__MT_CODE__[0-9][0-9][0-9]\r*$/d')"
    if detail="$(printf '%s' "$body" | mt_aj_parse_status -)"; then
        printf 'health=%s %s\n' "$hb" "$detail"
        return 0
    fi
    return 1
}

# ── 每线 run 目录资产（AutoJS jar 计数；大小写不敏感，只认 .jar 结尾）───────
# 输出 "<总条目数> <autojs jar 数> <rhino jar 数> <autojs jar 名列表>"
mt_mods_stats() {
    local run_dir="${1:-}" total=0 aj=0 rh=0 names="" jar b
    [ -d "$run_dir/mods" ] || return 1
    total="$(ls -1 "$run_dir/mods" 2>/dev/null | wc -l | tr -d ' ')"
    for jar in "$run_dir/mods"/*.jar; do
        [ -e "$jar" ] || continue
        b="$(basename "$jar")"
        case "$b" in
            *[Aa][Uu][Tt][Oo][Jj][Ss]*.jar) aj=$((aj + 1)); names="$names $b" ;;
            *[Rr][Hh][Ii][Nn][Oo]*.jar)     rh=$((rh + 1)) ;;
        esac
    done
    printf '%s %s %s %s\n' "${total:-0}" "$aj" "$rh" "$names"
}

mt_autojs_jar_count() {
    local run_dir="${1:-}" jar b n=0
    for jar in "$run_dir/mods"/*.jar; do
        [ -e "$jar" ] || continue
        b="$(basename "$jar")"
        case "$b" in *[Aa][Uu][Tt][Oo][Jj][Ss]*.jar) n=$((n + 1)) ;; esac
    done
    printf '%s' "$n"
}

# 世界 marker（与 linux/launch.sh 同款；Windows 走 quickplay，默认应命中）
mt_log_marker_hit() {
    local log="${1:-}" v="${2:-}"
    [ -f "$log" ] || return 1
    case "$v" in
        26.1.2)
            grep -q 'logged in with entity id' "$log" 2>/dev/null \
                && grep -qE 'Loaded [0-9]+ advancements' "$log" 2>/dev/null
            ;;
        *)
            grep -q 'Total time to load game and open world was' "$log" 2>/dev/null
            ;;
    esac
}

mt_log_marker_name() {
    case "${1:-}" in
        26.1.2) printf '%s' '"logged in with entity id" + "Loaded <N> advancements"' ;;
        *)      printf '%s' '"Total time to load game and open world was"' ;;
    esac
}

# ── fabric-1.20.1 库（双 run 目录 / 条件 AutoJS / RCON）─────────────────────
# 反向包含：fabric.sh 只调用本文件里的函数，自己不 source 本文件，所以不会循环；
# 由本文件末尾统一 source，mt.sh / env.sh / launch.sh / stop.sh / phases.sh 都能拿到。
if [ -z "${MT_FABRIC_LIB_LOADED:-}" ]; then
    _mt_common_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"
    if [ -n "$_mt_common_dir" ] && [ -f "$_mt_common_dir/fabric.sh" ]; then
        . "$_mt_common_dir/fabric.sh"
    fi
    unset _mt_common_dir
fi
