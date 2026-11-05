#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/lib/fabric.sh — fabric 线（fabric-<mcver>）的 bash 侧实现
#
# 定位：库文件（不是可执行脚本）。由 lib/common.sh 末尾 source（见 common.sh
#   的 MT_FABRIC_LIB_LOADED 段），因此 mt.sh / env.sh / launch.sh / stop.sh /
#   lib/phases.sh 都会经由 common.sh 拿到下面这些函数。
#
# 为什么单独成文件：fabric 线与前三条线有三处结构性差异，集中放一处才不会
#   污染既有三线的代码路径：
#     1) run 目录是**双目录**：<fabric-子项目>/run/<client|server>，
#        而 P0 三线是 仓库根/run/<版本>（单目录）；
#     2) AutoJS 是**条件依赖**：各 fabric 线的 build.gradle（autojsTestMod 开关）只有在
#        -PautojsTestMod=1 时才 modRuntimeOnly 引入 AutoJS，运行目录默认没有它
#        ⇒ 没有 MCP，注入只能走 RCON（127.0.0.1:25575 / 口令 astralft，
#        见 scripts/test/fabric/ft_env.ps1:143-150）；
#     3) 旧 fabric 测试台（scripts/test/fabric/ft_*.ps1）的机器行前缀是
#        MT_FAB_*，本文件把这类输出归一成主测试台的 MT_*（只读兼容，不改旧脚本）。
#
# 依赖约定：本文件**不 source common.sh**（避免循环包含）。它只调用 common.sh
#   的 mt_ok / mt_warn / mt_fail / mt_info，因此必须由 common.sh 之后使用；
#   由 phases.sh 调用的 mt_fab_phase_* 额外依赖 mt_phase_begin / mt_phase_dry /
#   mt_phase_verdict / mt_phase_require_repo。
# ============================================================================
[ -n "${MT_FABRIC_LIB_LOADED:-}" ] && return 0
MT_FABRIC_LIB_LOADED=1
MT_FABRIC_LIB_VERSION="1.0.0-win"

# ── 常量（版本 / 子项目 / 侧 / RCON）────────────────────────────────────────
# 当前 fabric 线由 --version 现算（mt_fab_set_version）；这里的默认值只是「未给版本」时的兜底。
MT_FABRIC_VERSION="${MT_FABRIC_VERSION:-fabric-1.20.1}"
MT_FAB_SUBPROJECT="${MT_FAB_SUBPROJECT:-$MT_FABRIC_VERSION}"
MT_FAB_SIDES="client server"
MT_FAB_DEFAULT_SIDE="client"
MT_FAB_RCON_PORT="${MT_FAB_RCON_PORT:-25575}"
MT_FAB_RCON_PASSWORD="${MT_FAB_RCON_PASSWORD:-astralft}"
# 就绪判据与旧测试台 ft_launch.ps1:131 的 ready pattern 同源
MT_FAB_READY_SERVER='Done \([0-9.]\+s\)! For help'
MT_FAB_READY_CLIENT='Sound engine finished|Sound engine started'

# ── 版本 / 子项目 / 侧 ──────────────────────────────────────────────────────
# 前缀判定：fabric 线全线通用（"1.21.1" 是 "fabric-1.21.1" 的子串，故用前缀而不是子串）。
mt_is_fabric() {
    case "${1:-}" in
        fabric-*) return 0 ;;
        *) return 1 ;;
    esac
}

# 由 --version 现算当前 fabric 版本；非 fabric 版本返回 1 且不改动全局。
mt_fab_set_version() {
    local v="${1:-}"
    case "$v" in
        fabric-*) ;;
        *) return 1 ;;
    esac
    MT_FABRIC_VERSION="$v"
    MT_FAB_SUBPROJECT="$v"
    # 导出：stop.sh 等子进程脚本只读全局，由父进程带过去（单线调用即可命中本版本的 run 目录）
    export MT_FABRIC_VERSION MT_FAB_SUBPROJECT
    return 0
}

mt_fab_subproject() { printf '%s' "$MT_FAB_SUBPROJECT"; }

mt_fab_side_valid() {
    case "${1:-}" in
        client|server) return 0 ;;
        *) return 1 ;;
    esac
}

# 归一：空 ⇒ 默认 client；非法 ⇒ 返回 1（报错文案由调用方给）
mt_fab_side() {
    local side="${1:-}"
    [ -z "$side" ] && side="$MT_FAB_DEFAULT_SIDE"
    mt_fab_side_valid "$side" || return 1
    printf '%s' "$side"
}

# ── 双 run 根 ───────────────────────────────────────────────────────────────
mt_fab_run_root() {
    [ -n "${MT_REPO_ROOT:-}" ] || return 1
    printf '%s/%s/run' "$MT_REPO_ROOT" "$MT_FAB_SUBPROJECT"
}

mt_fab_side_dir() {
    local side root
    side="$(mt_fab_side "${1:-}")" || return 1
    root="$(mt_fab_run_root)" || return 1
    printf '%s/%s' "$root" "$side"
}

mt_fab_log_path() {
    local dir
    dir="$(mt_fab_side_dir "${1:-}")" || return 1
    printf '%s/logs/latest.log' "$dir"
}

mt_fab_mods_dir() {
    local dir
    dir="$(mt_fab_side_dir "${1:-}")" || return 1
    printf '%s/mods' "$dir"
}

mt_fab_jar_dir() {
    [ -n "${MT_REPO_ROOT:-}" ] || return 1
    printf '%s/%s/build/libs' "$MT_REPO_ROOT" "$MT_FAB_SUBPROJECT"
}

# 通用 run 根（三线 ⇒ <repo>/run/<version>；fabric ⇒ <repo>/<fabric-子项目>/run/<side>）
mt_fabric_run_root() {
    local v side
    v="${1:-}"
    side="${2:-$MT_FAB_DEFAULT_SIDE}"
    if mt_is_fabric "$v"; then
        mt_fab_side_dir "$side"
    else
        printf '%s/run/%s' "$MT_REPO_ROOT" "$v"
    fi
}

# 人类可读标签（三线仍是 "run/<版本>"，与既有输出逐字一致）
mt_fab_run_label() {
    local v side
    v="${1:-}"
    side="${2:-$MT_FAB_DEFAULT_SIDE}"
    if mt_is_fabric "$v"; then
        printf '%s/run/%s' "$MT_FAB_SUBPROJECT" "$(mt_fab_side "$side")"
    else
        printf 'run/%s' "$v"
    fi
}

mt_fab_gradle_task() {
    local side task
    side="$(mt_fab_side "${1:-}")" || return 1
    case "$side" in
        server) task="runServer" ;;
        *)      task="runClient" ;;
    esac
    # 只返回裸任务名：调用方各自拼 ":<子项目>:<任务>"，避免出现 ":proj::proj:runServer"。
    printf '%s' "$task"
}

# ── AutoJS：条件依赖（-PautojsTestMod=1）────────────────────────────────────
# 返回 0 = AutoJS 可用；1 = 不可用（调用方必须显式说明「回退 RCON」，不得静默 false）
mt_fab_autojs_enabled() {
    local side s
    side="${1:-}"
    case "$(printf '%s' "${MT_FAB_AUTOJS:-}" | tr 'A-Z' 'a-z')" in
        1|true|yes|on) return 0 ;;
        0|false|no|off) return 1 ;;
    esac
    case "${MT_GRADLE_AUTOJS_ARGS:-}" in
        *-PautojsTestMod=1*) return 0 ;;
    esac
    if [ -n "$side" ]; then
        ls "$(mt_fab_mods_dir "$side")" 2>/dev/null | grep -qi '^autojs' && return 0
        return 1
    fi
    for s in $MT_FAB_SIDES; do
        ls "$(mt_fab_mods_dir "$s")" 2>/dev/null | grep -qi '^autojs' && return 0
    done
    return 1
}

mt_fab_autojs_mode() {
    if mt_fab_autojs_enabled "${1:-}"; then printf 'on'; else printf 'off'; fi
}

# 仅在显式开启时才带 -PautojsTestMod=1（默认不带）
mt_fab_gradle_autojs_arg() {
    if mt_fab_autojs_enabled "${1:-}"; then printf '%s' '-PautojsTestMod=1'; fi
}

mt_fab_autojs_note() {
    printf '%s AutoJS=%s（开关 -PautojsTestMod=1；未开启时注入/断言回退 RCON 127.0.0.1:%s）' \
        "$MT_FABRIC_VERSION" "$(mt_fab_autojs_mode "${1:-}")" "$MT_FAB_RCON_PORT"
}

# ── AutoJS 注入状态文件（launch.sh 写 / stop.sh 删 / PowerShell 通道判定读）────
# 存在理由：fabric 的 AutoJS 是**条件依赖**，由 -PautojsTestMod=1 以 modRuntimeOnly 注入运行期
# classpath，**永远不进 run/<side>/mods**；而「mods 里有没有 AutoJS-*.jar」是既有就绪判据
# （scripts/test/win/launch.sh:191-210、scripts/test/lib/Mt.Channel.psm1:225-242）。制品是
# intermediary 命名空间，手放进 mods 会 NoSuchMethodError（TESTING-RULES-OVERVIEW.md:191 规则 6），
# 所以判据只能改成「本次启动是否真的注入了」——落这个文件。
mt_fab_autojs_state_path() {
    local side
    side="$(mt_fab_side "$1")" || return 1
    printf '%s/.mt_autojs_injected' "$(mt_fab_side_dir "$side")"
}

# 真启动前调用：无条件写（autojs=0 也写），保证不会读到上一轮的陈旧「已注入」
mt_fab_autojs_state_write() {
    local side path arg
    side="$(mt_fab_side "$1")" || return 1
    path="$(mt_fab_autojs_state_path "$side")" || return 1
    [ -d "$(dirname "$path")" ] || return 1
    arg="$(mt_fab_gradle_autojs_arg "$side")"
    {
        if [ -n "$arg" ]; then printf 'autojs=1\n'; else printf 'autojs=0\n'; fi
        printf 'gradle_arg=%s\n' "${arg:-<none>}"
        printf 'artifact=AutoJS-%s-1.0.0.jar\n' "$MT_FABRIC_VERSION"
        printf 'source=mavenLocal:cc.sighs.autojs/AutoJS-%s/1.0.0\n' "$MT_FABRIC_VERSION"
        printf 'side=%s\n' "$side"
        printf 'stamp=%s\n' "$(date +%Y%m%d-%H%M%S)"
    } > "$path" 2>/dev/null || return 1
    return 0
}

mt_fab_autojs_state_clear() {
    local side path
    side="$(mt_fab_side "$1")" || return 1
    path="$(mt_fab_autojs_state_path "$side")" || return 1
    rm -f "$path" 2>/dev/null || true
    return 0
}

# 读回来（1/0）；文件缺失或无 autojs= 行 ⇒ 非零退出（调用方按「未注入」处理）
mt_fab_autojs_state_read() {
    local side path v
    side="$(mt_fab_side "$1")" || return 1
    path="$(mt_fab_autojs_state_path "$side")" || return 1
    [ -f "$path" ] || return 1
    v="$(tr -d '\r' < "$path" 2>/dev/null | sed -n 's/^autojs=//p' | head -n 1)"
    [ -n "$v" ] || return 1
    printf '%s' "$v"
}

# ── 旧 fabric 测试台状态文件（scripts/test/fabric/.ft_launch_state.json）────
mt_fab_state_file() {
    [ -n "${MT_REPO_ROOT:-}" ] || return 1
    printf '%s/scripts/test/fabric/.ft_launch_state.json' "$MT_REPO_ROOT"
}

## mt_fab_state_get <key>：取一个 JSON 标量（只用 grep/sed，不依赖 jq）
mt_fab_state_get() {
    local key file line
    key="${1:-}"
    file="$(mt_fab_state_file)" || return 1
    [ -f "$file" ] || return 1
    line="$(tr -d '\r' < "$file" 2>/dev/null | grep -o "${key}[[:space:]]*:[[:space:]]*\(\"[^\"]*\"\|[0-9][0-9]*\|true\|false\)" | head -n 1)"
    [ -n "$line" ] || return 1
    line="${line#*:}"
    line="$(printf '%s' "$line" | sed 's/^[[:space:]]*//; s/^"//; s/"$//')"
    printf '%s' "$line"
}

## mt_fab_state_pid [side]：读 PID；给了 side 且状态文件 side 不匹配 ⇒ 不返回
mt_fab_state_pid() {
    local want got pid
    want="${1:-}"
    got="$(mt_fab_state_get side 2>/dev/null || true)"
    if [ -n "$want" ] && [ -n "$got" ] && [ "$got" != "$want" ]; then
        return 1
    fi
    pid="$(mt_fab_state_get pid 2>/dev/null || true)"
    case "$pid" in ''|*[!0-9]*) return 1 ;; esac
    printf '%s' "$pid"
}

# ── RCON（无 AutoJS 时的唯一注入通道）──────────────────────────────────────
mt_fab_server_property() {
    local key file val
    key="${1:-}"
    file="$(mt_fab_side_dir server)/server.properties"
    [ -f "$file" ] || return 1
    val="$(tr -d '\r' < "$file" 2>/dev/null | grep -E "^[[:space:]]*$key[[:space:]]*=" | tail -n 1)"
    [ -n "$val" ] || return 1
    val="${val#*=}"
    printf '%s' "$(printf '%s' "$val" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
}

mt_fab_rcon_port() {
    local p
    p="$(mt_fab_server_property rcon.port 2>/dev/null || true)"
    case "$p" in ''|*[!0-9]*) p="$MT_FAB_RCON_PORT" ;; esac
    printf '%s' "$p"
}

mt_fab_rcon_password() {
    local p
    p="$(mt_fab_server_property rcon.password 2>/dev/null || true)"
    [ -n "$p" ] || p="$MT_FAB_RCON_PASSWORD"
    printf '%s' "$p"
}

mt_fab_python() {
    command -v python3 2>/dev/null || command -v python 2>/dev/null || return 1
}

## mt_rcon_exec <命令> [端口] [口令]：成功时把服务端应答打到 stdout，返回 0
##   缺 python / 端口不可达 / 认证失败 ⇒ 打 MT_ERROR 并返回 2（不静默成功）
mt_rcon_exec() {
    local cmd port pw py
    cmd="${1:-}"
    port="${2:-$(mt_fab_rcon_port)}"
    pw="${3:-$(mt_fab_rcon_password)}"
    if [ -z "$cmd" ]; then
        printf 'MT_ERROR: mt_rcon_exec 需要一条 RCON 命令\n' >&2
        return 2
    fi
    py="$(mt_fab_python)" || {
        printf 'MT_ERROR: 找不到 python3/python —— 无法连 RCON（fabric 未开 AutoJS 时的唯一注入通道）\n' >&2
        return 2
    }
    "$py" - "$port" "$pw" "$cmd" <<'PY'
import socket, struct, sys

port, pw, cmd = int(sys.argv[1]), sys.argv[2], sys.argv[3]

def packet(rid, typ, body):
    data = body.encode('utf-8')
    payload = struct.pack('<ii', rid, typ) + data + b'\x00\x00'
    return struct.pack('<i', len(payload)) + payload

def recv_exact(sock, n):
    buf = b''
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise EOFError('连接被对端关闭')
        buf += chunk
    return buf

def read_packet(sock):
    (length,) = struct.unpack('<i', recv_exact(sock, 4))
    data = recv_exact(sock, length)
    rid, typ = struct.unpack('<ii', data[:8])
    return rid, typ, data[8:-2].decode('utf-8', 'replace')

try:
    sock = socket.create_connection(('127.0.0.1', port), timeout=5)
except OSError as exc:
    sys.stderr.write('MT_ERROR: 无法连接 RCON 127.0.0.1:%d（%s）—— fabric 服务端未启动，或 enable-rcon=false\n' % (port, exc))
    sys.exit(2)

sock.settimeout(10)
try:
    sock.sendall(packet(1, 3, pw))
    rid, _typ, _body = read_packet(sock)
    if rid == -1:
        sys.stderr.write('MT_ERROR: RCON 认证失败（server.properties 的 rcon.password 与脚本口令不符）\n')
        sys.exit(2)
    sock.sendall(packet(2, 2, cmd))
    _rid, _typ, body = read_packet(sock)
finally:
    try:
        sock.close()
    except OSError:
        pass

sys.stdout.write(body if body.endswith('\n') else body + '\n')
sys.exit(0)
PY
}

## mt_rcon_probe [端口]：只测可达性（不发命令），0=可达 1=不可达
mt_rcon_probe() {
    local port py
    port="${1:-$(mt_fab_rcon_port)}"
    py="$(mt_fab_python)" || return 1
    "$py" - "$port" <<'PY'
import socket, sys
try:
    s = socket.create_connection(('127.0.0.1', int(sys.argv[1])), timeout=2)
    s.close()
except OSError:
    sys.exit(1)
sys.exit(0)
PY
}

# ── 旧机器行归一：MT_FAB_* → MT_*（只用于读旧 pwsh 测试台输出）──────────────
mt_fab_normalize_stream() {
    sed -u -e 's/^MT_FAB_INFO:[[:space:]]*/MT_INFO: /' \
           -e 's/^MT_FAB_WARN:[[:space:]]*/MT_WARN: /' \
           -e 's/^MT_FAB_ERROR:[[:space:]]*/MT_ERROR: /' \
           -e 's/^MT_FAB_\([A-Z0-9_]*\):[[:space:]]*/MT_\1: /'
}

# ── 就绪判据（按侧区分，与三线不同）────────────────────────────────────────
mt_fab_marker_pattern() {
    local side
    side="$(mt_fab_side "${1:-}")" || return 1
    case "$side" in
        server) printf '%s' "$MT_FAB_READY_SERVER" ;;
        *)      printf '%s' "$MT_FAB_READY_CLIENT" ;;
    esac
}

mt_fab_marker_name() {
    local side
    side="$(mt_fab_side "${1:-}")" || return 1
    case "$side" in
        server) printf '%s' 'Done (<N>s)! For help' ;;
        *)      printf '%s' 'Sound engine started|finished' ;;
    esac
}

## mt_fab_marker_hit <side> [日志路径]：在 fabric 侧 run 目录 latest.log 里找就绪行
mt_fab_marker_hit() {
    local side log pat
    side="${1:-}"
    pat="$(mt_fab_marker_pattern "$side")" || return 1
    log="${2:-$(mt_fab_log_path "$side")}"
    [ -f "$log" ] || return 1
    grep -qE "$pat" "$log" 2>/dev/null
}

# ── launch：委托 win/launch.sh（bash 原生，不碰 pwsh）───────────────────────
mt_fab_phase_launch() {
    local v side rc tsk ajarg other
    v="${1:-$MT_FABRIC_VERSION}"
    side="$(mt_fab_side "${MT_SIDE:-}")" || side="$MT_FAB_DEFAULT_SIDE"
    tsk="$(mt_fab_gradle_task "$side")"
    ajarg="$(mt_fab_gradle_autojs_arg "$side")"
    if [ "$side" = client ]; then other=server; else other=client; fi

    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "cd ${MT_REPO_ROOT} && bash scripts/test/win/launch.sh --version ${v} --side ${side}" \
            "子项目 :${MT_FAB_SUBPROJECT}（toolchain $(mt_toolchain "$v")，gradle 任务 :${MT_FAB_SUBPROJECT}:${tsk}）" \
            "run 目录：$(mt_fab_run_label "$v" "$side")（fabric 双目录，另侧 ${other}）" \
            "$(mt_fab_autojs_note "$side")" \
            "判据：就绪 marker 按侧区分（server: Done (<N>s)! For help；client: Sound engine started）；BLOCKED(11)/TIMEOUT(12)/ERROR(2) 原样透传"
        return 0
    fi

    ( cd "$MT_REPO_ROOT" && bash "$MT_PHASES_DIR/../launch.sh" --version "$v" --side "$side" )
    rc=$?
    mt_phase_verdict LAUNCH "$rc"
    return "$rc"
}

# ── cases：bash 原生解析 scripts/test/cases/*-<当前 fabric 版本>.json ───────
mt_fab_cases_dir() {
    [ -n "${MT_REPO_ROOT:-}" ] || return 1
    printf '%s/scripts/test/cases' "$MT_REPO_ROOT"
}

mt_fab_case_files() {
    local dir
    dir="$(mt_fab_cases_dir)" || return 1
    [ -d "$dir" ] || return 1
    ls "$dir"/*-"$MT_FABRIC_VERSION".json 2>/dev/null
}

mt_fab_state_dir() {
    [ -n "${MT_REPO_ROOT:-}" ] || return 1
    printf '%s/scripts/test/win/logs/fabric' "$MT_REPO_ROOT"
}

mt_fab_report_file() {
    local v dir
    v="${1:-$MT_FABRIC_VERSION}"
    dir="$(mt_fab_state_dir)" || return 1
    printf '%s/cases-%s.jsonl' "$dir" "$v"
}

mt_fab_report_summary_file() {
    local v dir
    v="${1:-$MT_FABRIC_VERSION}"
    dir="$(mt_fab_state_dir)" || return 1
    printf '%s/summary-%s.md' "$dir" "$v"
}

## mt_fab_cases_run <version> [单条目路径]：跑 fabric 条目，返回 0/1/2/11
mt_fab_cases_run() {
    local v case_sel side py dir
    v="${1:-$MT_FABRIC_VERSION}"
    case_sel="${2:-}"
    side="$(mt_fab_side "${MT_SIDE:-}")" || side="$MT_FAB_DEFAULT_SIDE"
    py="$(mt_fab_python)" || {
        mt_fail fabric_cases_python "找不到 python3/python —— fabric 的 cases 阶段在 bash 侧用 python 解析条目 JSON（不依赖 jq）"
        return 2
    }
    dir="$(mt_fab_state_dir)" || return 1
    mkdir -p "$dir" 2>/dev/null || true
    "$py" - "$MT_REPO_ROOT" "$v" "$case_sel" "$side" "$(mt_fab_autojs_mode "$side")" \
        "$(mt_fab_rcon_port)" "$(mt_fab_rcon_password)" "$(mt_fab_report_file "$v")" \
        "$(mt_fab_jar_dir)" <<'PY'
import glob, json, os, re, socket, struct, sys, time, zipfile

repo, version, case_sel, side, autojs, rport, rpw, result_file, jar_dir = sys.argv[1:10]
rport = int(rport)


def out(line):
    sys.stdout.write(line + '\n')
    sys.stdout.flush()


def err(line):
    sys.stderr.write(line + '\n')
    sys.stderr.flush()


def rcon(cmd):
    def packet(rid, typ, body):
        data = body.encode('utf-8')
        payload = struct.pack('<ii', rid, typ) + data + b'\x00\x00'
        return struct.pack('<i', len(payload)) + payload

    def recv_exact(sock, n):
        buf = b''
        while len(buf) < n:
            chunk = sock.recv(n - len(buf))
            if not chunk:
                raise EOFError('连接被对端关闭')
            buf += chunk
        return buf

    def read_packet(sock):
        (length,) = struct.unpack('<i', recv_exact(sock, 4))
        data = recv_exact(sock, length)
        rid, typ = struct.unpack('<ii', data[:8])
        return rid, typ, data[8:-2].decode('utf-8', 'replace')

    sock = socket.create_connection(('127.0.0.1', rport), timeout=5)
    sock.settimeout(10)
    try:
        sock.sendall(packet(1, 3, rpw))
        rid, _t, _b = read_packet(sock)
        if rid == -1:
            raise RuntimeError('RCON 认证失败（口令不符）')
        sock.sendall(packet(2, 2, cmd))
        _r, _t, body = read_packet(sock)
        return body
    finally:
        try:
            sock.close()
        except OSError:
            pass


def newest_jar():
    if not os.path.isdir(jar_dir):
        return None
    cands = [p for p in glob.glob(os.path.join(jar_dir, 'astral_dice-*.jar'))
             if not re.search(r'-(sources|dev|slim)\.jar$', p)]
    if not cands:
        return None
    return max(cands, key=os.path.getmtime)


def jar_names():
    p = newest_jar()
    if not p:
        return None, []
    with zipfile.ZipFile(p) as zf:
        return p, zf.namelist()


def jar_stats():
    p, names = jar_names()
    if p is None:
        raise RuntimeError('找不到产物 jar（先跑 --phase build）：%s' % jar_dir)

    def count(prefix, suffix=''):
        return len([n for n in names if n.startswith(prefix) and n.endswith(suffix)])

    return {
        'jar': os.path.basename(p),
        'entries': len(names),
        'models': count('assets/astral_dice/models/item/', '.json'),
        'recipes': count('data/astral_dice/recipes/', '.json'),
        'trinkets': count('data/trinkets/', '.json'),
        'bountiful': count('data/bountiful/', '.json'),
        'embed': count('META-INF/jars/', '.jar'),
        'signs': len([n for n in names if re.search(r'item/sign/[A-Za-z]+SignItem\.class$', n)]),
    }


LOG = os.path.join(repo, version, 'run', side, 'logs', 'latest.log')


def log_text():
    if not os.path.isfile(LOG):
        return ''
    with open(LOG, 'r', encoding='utf-8', errors='replace') as fh:
        return fh.read()


def log_has(pattern, text=None):
    text = log_text() if text is None else text
    try:
        return re.search(pattern, text) is not None
    except re.error:
        return False


CRASH_MARKERS = r'A fatal error has been detected|crash-reports/|Exception in thread "main"|#@!@# Game crashed'
KUBEJS_ERR = r'\[KubeJS\]\s*(Error|ERROR)'
MIXIN_ERR = r'Mixin apply failed|InjectionError|MixinTransformerError'


def eval_assert(a, state):
    typ = a.get('type', '')
    if typ == 'log':
        pat = a.get('pattern', '')
        return (log_has(pat), 'log:%s' % pat)
    if typ == 'absent':
        pat = a.get('pattern', '')
        return (not log_has(pat), 'absent:%s' % pat)
    if typ == 'crash':
        return (not log_has(CRASH_MARKERS), 'crash-markers')
    if typ == 'kubejs':
        return (not log_has(KUBEJS_ERR), 'kubejs-errors')
    if typ == 'mixin':
        return (not log_has(MIXIN_ERR), 'mixin-errors')
    if typ == 'state':
        key = a.get('from', '')
        if key not in state:
            return (False, 'state-missing:%s' % key)
        got = state[key]
        expect = a.get('expect', {})
        numeric = {'gte': (lambda v, b: v >= b, '>='),
                   'min': (lambda v, b: v >= b, '>='),
                   'gt': (lambda v, b: v > b, '>'),
                   'lte': (lambda v, b: v <= b, '<='),
                   'max': (lambda v, b: v <= b, '<='),
                   'lt': (lambda v, b: v < b, '<')}
        for k, want in expect.items():
            if k in numeric:
                continue
            if got.get(k) != want:
                return (False, 'state:%s.%s=%r!=%r' % (key, k, got.get(k), want))
        for op in numeric:
            if op in expect:
                cmpfn, label = numeric[op]
                for k, bound in expect[op].items():
                    val = got.get(k)
                    if not isinstance(val, (int, float)):
                        return (False, 'state:%s.%s 非数值' % (key, k))
                    if not cmpfn(val, bound):
                        return (False, 'state:%s.%s=%s 不满足 %s %s' % (key, k, val, label, bound))
        return (True, 'state:%s' % key)
    if typ == 'eval':
        return (None, 'eval 需要 AutoJS（当前 AutoJS=%s）' % autojs)
    if typ == 'vision':
        return (None, 'vision 需要截图通道（bash 侧未实现）')
    if typ == 'jar':
        key = a.get('from', 'jar')
        if key not in state:
            return (False, 'jar-state-missing（条目里需先有 op=jar_stats 步骤）')
        got = state[key]
        for field, value in a.items():
            if field in ('type', 'from'):
                continue
            if field in ('models_min', 'recipes_min', 'trinkets_min', 'entries_min', 'signs_min'):
                real = field[:-4]
                if got.get(real, 0) < value:
                    return (False, 'jar:%s=%s < %s' % (real, got.get(real), value))
            elif field in ('bountiful_max', 'embed_max'):
                real = field[:-4]
                if got.get(real, 0) > value:
                    return (False, 'jar:%s=%s > %s' % (real, got.get(real), value))
            elif field == 'embed_lib':
                if bool(got.get('embed', 0)) != bool(value):
                    return (False, 'jar:embed=%s != %s' % (got.get('embed'), value))
            else:
                return (None, 'jar 断言字段未实现：%r' % field)
        return (True, 'jar:%s' % os.path.basename(str(got.get('jar', '?'))))
    if typ in ('bytecode', 'bytecode_absent'):
        pat = a.get('pattern', '')
        path, names = jar_names()
        if path is None:
            return (False, 'bytecode:no-jar（先跑 --phase build）')
        hit = [n for n in names if re.search(pat, n)]
        if typ == 'bytecode':
            return (bool(hit), 'bytecode:%s(%d)' % (pat, len(hit)))
        return (not hit, 'bytecode_absent:%s(%d)' % (pat, len(hit)))
    return (None, '未知断言类型 %r（按 BLOCKED 处理，不静默通过）' % typ)


if case_sel:
    cases = [case_sel if os.path.isabs(case_sel) else os.path.join(repo, case_sel)]
else:
    cases = sorted(glob.glob(os.path.join(repo, 'scripts', 'test', 'cases', '*-%s.json' % version)))
if not cases:
    err('MT_ERROR: 没有找到 fabric 条目（scripts/test/cases/*-%s.json）' % version)
    sys.exit(11)

# 结果文件按“本次运行”重置：否则 report 会把历次运行累加（例如 2 次 × 9 条读成 18 条）。
# 只截断本宿主自己的运行产物 scripts/test/win/logs/fabric/cases-<ver>.jsonl，不动仓库任何文件。
try:
    os.makedirs(os.path.dirname(result_file), exist_ok=True)
    with open(result_file, 'w', encoding='utf-8'):
        pass
except OSError as exc:
    err('MT_ERROR: 无法重置结果文件 %s：%s' % (result_file, exc))
    sys.exit(12)

total = ok_n = fail_n = blocked_n = 0
records = []

for path in cases:
    try:
        with open(path, 'r', encoding='utf-8') as fh:
            case = json.load(fh)
    except (OSError, ValueError) as exc:
        total += 1
        fail_n += 1
        err('MT_CASE: %s = FAIL — 读取/解析条目失败：%s' % (os.path.basename(path), exc))
        records.append({'case_id': os.path.basename(path), 'verdict': 'FAIL', 'why': str(exc)})
        continue

    cid = str(case.get('case_id', os.path.basename(path)))
    total += 1
    missing = [k for k in ('case_id', 'title', 'version', 'steps') if k not in case]
    if missing:
        fail_n += 1
        err('MT_CASE: %s = FAIL — 缺必填字段 %s' % (cid, ','.join(missing)))
        records.append({'case_id': cid, 'verdict': 'FAIL', 'why': 'missing:' + ','.join(missing)})
        continue
    if str(case.get('version')) != version:
        fail_n += 1
        err('MT_CASE: %s = FAIL — version=%s 与本轮 %s 不符' % (cid, case.get('version'), version))
        records.append({'case_id': cid, 'verdict': 'FAIL', 'why': 'version'})
        continue

    steps = case.get('steps') or []
    # 主测试台 schema 用 "assert"；早期草案写的是 "asserts" —— 两者都收，避免条目写法差异被静默忽略。
    asserts = list(case.get('assert') or case.get('asserts') or [])
    state = {}
    verdict = 'OK'
    why = ''

    out('MT_CASE_BEGIN: %s side=%s steps=%d asserts=%d' % (cid, side, len(steps), len(asserts)))

    for idx, step in enumerate(steps):
        op = str(step.get('op', ''))
        if op == 'note':
            continue
        if op == 'wait':
            ms = int(step.get('ms', 0) or 0)
            if 0 < ms <= 120000:
                time.sleep(ms / 1000.0)
            continue
        if op in ('inject_command', 'rcon'):
            if side != 'server':
                verdict, why = 'BLOCKED', '%s 需要 server 侧（当前 side=%s）' % (op, side)
                break
            cmd = str(step.get('command') or step.get('cmd') or '')
            try:
                body = rcon(cmd)
                first = body.strip().splitlines()[0][:160] if body.strip() else '<空>'
                out('MT_CASE_RCON: %s cmd=%s reply=%s' % (cid, cmd, first))
                time.sleep(0.2)
            except (OSError, RuntimeError, EOFError) as exc:
                verdict, why = 'BLOCKED', 'RCON 不可用：%s' % exc
                break
            continue
        if op == 'jar_stats':
            try:
                state[str(step.get('save_as', 'jar'))] = jar_stats()
            except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
                verdict, why = 'BLOCKED', '读产物 jar 失败：%s' % exc
                break
            continue
        if op in ('eval_js', 'await_state', 'mcp_status'):
            verdict, why = 'BLOCKED', '%s 需要 AutoJS（当前 AutoJS=%s；fabric 的 AutoJS 是 -PautojsTestMod=1 条件依赖）' % (op, autojs)
            break
        if op in ('screenshot', 'kubejs_reload'):
            verdict, why = 'BLOCKED', '%s 在 bash/python 侧未实现（旧 pwsh 通道 ft_inject.ps1 已废弃删除；能力缺口登记见 docs/features/devnext-bash-testbed-decisions.md）' % op
            break
        if op == 'assert':
            res, detail = eval_assert(step, state)
            if res is None:
                verdict, why = 'BLOCKED', detail
                break
            if res is False:
                verdict, why = 'FAIL', 'steps[%d] 内联断言 %s' % (idx, detail)
                break
            continue
        verdict, why = 'BLOCKED', 'steps[%d] 未知 op %r（不静默通过）' % (idx, op)
        break

    if verdict == 'OK':
        for a in asserts:
            res, detail = eval_assert(a, state)
            if res is None:
                verdict, why = 'BLOCKED', detail
                break
            if res is False:
                verdict, why = 'FAIL', detail
                break

    if verdict == 'OK':
        ok_n += 1
        out('MT_CASE: %s = OK' % cid)
    elif verdict == 'FAIL':
        fail_n += 1
        err('MT_CASE: %s = FAIL — %s' % (cid, why))
        if os.path.isfile(LOG):
            err('MT_CASE_LOG: %s 日志=%s（末 5 行）' % (cid, LOG))
            with open(LOG, 'r', encoding='utf-8', errors='replace') as fh:
                tail = [ln.rstrip() for ln in fh.readlines()[-5:]]
            for ln in tail:
                err('MT_CASE_LOG:   ' + ln[:200])
        else:
            err('MT_CASE_LOG: %s 日志不存在=%s' % (cid, LOG))
    else:
        blocked_n += 1
        err('MT_CASE: %s = BLOCKED — %s' % (cid, why))
    records.append({'case_id': cid, 'verdict': verdict, 'why': why})

os.makedirs(os.path.dirname(result_file), exist_ok=True)
with open(result_file, 'a', encoding='utf-8') as fh:
    for rec in records:
        rec['at'] = time.strftime('%Y-%m-%dT%H:%M:%S')
        rec['side'] = side
        fh.write(json.dumps(rec, ensure_ascii=False) + '\n')

out('MT_CASES: total=%d ok=%d fail=%d blocked=%d（日志 %s；结果 %s）' % (
    total, ok_n, fail_n, blocked_n, LOG, result_file))
if fail_n:
    sys.exit(1)
if blocked_n:
    sys.exit(11)
sys.exit(0)
PY
}

mt_fab_phase_cases() {
    local v case_sel rc count
    v="${1:-$MT_FABRIC_VERSION}"
    case_sel="${2:-}"
    mt_phase_require_repo || return $?
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        count="$(mt_fab_case_files 2>/dev/null | wc -l | tr -d ' ')"
        mt_phase_dry "bash（原生）解析 scripts/test/cases/*-${MT_FABRIC_VERSION}.json —— 条目 ${count} 个" \
            "side=$(mt_fab_side "${MT_SIDE:-}")；$(mt_fab_autojs_note "${MT_SIDE:-}")" \
            "判据：全部 OK → PASS(0)；任一 FAIL → FAIL(1)；无 FAIL 但有 BLOCKED → BLOCKED(11)" \
            "结果落 $(mt_fab_report_file "$v")"
        return 0
    fi
    mt_fab_cases_run "$v" "$case_sel"
    rc=$?
    mt_phase_verdict CASES "$rc"
    return "$rc"
}

# ── report：bash 原生汇总 cases-<version>.jsonl ─────────────────────────────
mt_fab_phase_report() {
    local v verdict rc
    v="${1:-$MT_FABRIC_VERSION}"
    verdict="${2:-}"
    mt_phase_require_repo || return $?
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry "bash（原生）汇总 $(mt_fab_report_file "$v")" \
            '判据：无 FAIL 记录 → rc=0；有 FAIL → rc=1'
        return 0
    fi
    mt_fab_report_summary "$v"
    rc=$?
    [ -n "$verdict" ] && printf 'MT_REPORT_VERDICT: %s = %s\n' "$v" "$verdict"
    return "$rc"
}

## mt_fab_report_mark <version> <phase> <result>：与 pwsh 的 report mark 同语义
##   机器行按主测试台口径单写 MT_INFO:（mt_info 打的是 "INFO: key — detail"，此处不用它）
mt_fab_report_mark() {
    local v phase result dir
    v="${1:-$MT_FABRIC_VERSION}"
    phase="${2:-}"
    result="${3:-}"
    dir="$(mt_fab_state_dir)" || return 1
    mkdir -p "$dir" 2>/dev/null || true
    printf '%s\t%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$v" "$phase" "$result" \
        >> "$dir/marks-$v.tsv" 2>/dev/null || true
    printf 'MT_INFO: report_mark %s %s = %s\n' "$v" "$phase" "$result"
    return 0
}

## mt_fab_report_summary [version]：读 jsonl 出 SUMMARY.md + MT_SUMMARY 机器行
mt_fab_report_summary() {
    local v file sum py rc
    v="${1:-$MT_FABRIC_VERSION}"
    file="$(mt_fab_report_file "$v")" || return 1
    sum="$(mt_fab_report_summary_file "$v")" || return 1
    py="$(mt_fab_python)" || { mt_warn fabric_report_python '找不到 python3/python，跳过 fabric 报告汇总'; return 0; }
    "$py" - "$file" "$sum" "$v" <<'PY'
import json, os, sys
from collections import Counter

src, dst, version = sys.argv[1:4]
recs = []
if os.path.isfile(src):
    with open(src, 'r', encoding='utf-8') as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                recs.append(json.loads(line))
            except ValueError:
                pass
cnt = Counter(r.get('verdict', '?') for r in recs)
lines = ['# %s 报告（%s）' % (version, version), '', '条目数：%d' % len(recs), '']
lines.append('| 结论 | 条数 |')
lines.append('| --- | --- |')
for k in ('OK', 'FAIL', 'BLOCKED'):
    lines.append('| %s | %d |' % (k, cnt.get(k, 0)))
lines.append('')
if recs:
    lines.append('| case_id | 结论 | 说明 |')
    lines.append('| --- | --- | --- |')
    for r in recs:
        lines.append('| %s | %s | %s |' % (r.get('case_id', '-'), r.get('verdict', '-'),
                                           (r.get('why') or '').replace('|', '/')[:160]))
os.makedirs(os.path.dirname(dst), exist_ok=True)
with open(dst, 'w', encoding='utf-8') as fh:
    fh.write('\n'.join(lines) + '\n')
print('MT_SUMMARY: %s total=%d ok=%d fail=%d blocked=%d（%s）' % (
    version, len(recs), cnt.get('OK', 0), cnt.get('FAIL', 0), cnt.get('BLOCKED', 0), dst))
sys.exit(1 if cnt.get('FAIL', 0) else 0)
PY
    rc=$?
    return "$rc"
}

# ── preflight：fabric 前置检查（bash 原生，不委托 pwsh）──────────────────────
mt_fab_phase_preflight() {
    local v problems side s
    v="${1:-$MT_FABRIC_VERSION}"
    problems=0
    mt_phase_require_repo || return $?
    side="$(mt_fab_side "${MT_SIDE:-}")" || side="$MT_FAB_DEFAULT_SIDE"
    if [ "${MT_DRY_RUN:-0}" = 1 ]; then
        mt_phase_dry 'bash（原生）fabric 前置检查：子项目目录 / 双 run 目录 / gradlew.bat / python / RCON 配置' \
            '判据：全部 OK → rc=0；关键项缺失 → BLOCKED(11)'
        return 0
    fi
    if [ ! -d "${MT_REPO_ROOT}/$MT_FAB_SUBPROJECT" ]; then
        mt_fail fabric_subproject "子项目目录不存在：$MT_FAB_SUBPROJECT"
        problems=$((problems + 1))
    else
        mt_ok fabric_subproject "子项目 $MT_FAB_SUBPROJECT 在位（toolchain $(mt_toolchain "$v")）"
    fi
    for s in $MT_FAB_SIDES; do
        if [ -d "$(mt_fab_side_dir "$s")" ]; then
            mt_ok "fabric_run_$s" "$(mt_fab_run_label "$v" "$s") 在位"
        else
            mt_warn "fabric_run_$s" "$(mt_fab_run_label "$v" "$s") 不存在（首次 run 后由 Loom 创建）"
        fi
    done
    if [ -f "${MT_REPO_ROOT}/gradlew.bat" ]; then
        mt_ok fabric_gradlew 'gradlew.bat 在位'
    else
        mt_fail fabric_gradlew 'gradlew.bat 缺失'
        problems=$((problems + 1))
    fi
    if [ -n "$(mt_fab_python)" ]; then
        mt_ok fabric_python 'python 可用于条目解析与 RCON'
    else
        mt_fail fabric_python '找不到 python3/python（cases 与 RCON 都依赖它）'
        problems=$((problems + 1))
    fi
    printf 'INFO: fabric_autojs — %s\n' "$(mt_fab_autojs_note "$side")"
    if mt_rcon_probe "$(mt_fab_rcon_port)"; then
        mt_ok fabric_rcon "RCON 127.0.0.1:$(mt_fab_rcon_port) 可达"
    else
        mt_warn fabric_rcon "RCON 127.0.0.1:$(mt_fab_rcon_port) 不可达（服务端未启动属预期；注入类条目会判 BLOCKED）"
    fi
    if [ "$problems" -gt 0 ]; then
        printf 'MT_PREFLIGHT: BLOCKED — %s 前置欠缺 %s 项\n' "$MT_FAB_SUBPROJECT" "$problems" >&2
        return 11
    fi
    printf 'MT_PREFLIGHT: OK\n'
    return 0
}
