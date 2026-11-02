#!/usr/bin/env bash
# ============================================================================
# scripts/test/win/env.sh — Astral Dice 测试台 · Windows(git bash) 环境体检
#
# 只读：不运行 gradle、不启动游戏、不写 run/ 下任何文件（唯一的写操作是"可写探测"，
#       探测文件用完即删）。
#
# 用法：
#   bash scripts/test/win/env.sh                     # 检查全部四条线
#   bash scripts/test/win/env.sh --version 1.21.1    # 只检查一条线
#   bash scripts/test/win/env.sh --quiet             # 不打 INFO 行
#
# 退出码：0 = 无 FAIL（可能含 WARN）；1 = 有 FAIL；2 = 用法错误
# 输出机器行：CHECK: <key>=OK|WARN|FAIL — <detail>、INFO: <key> — <detail>、
#             收尾 ENV_RESULT: OK|WARN|FAIL — <detail>
# ============================================================================
set -uo pipefail

WIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
. "$WIN_DIR/lib/common.sh"

MT_REPO_ROOT="$(mt_resolve_repo_root "$WIN_DIR")" || MT_REPO_ROOT=""
if [ -z "$MT_REPO_ROOT" ]; then
    echo "ENV_RESULT: FAIL — 无法定位仓库根（git rev-parse 与 settings.gradle 上溯都失败）" >&2
    exit 1
fi
export MT_REPO_ROOT
CONF="$(mt_conf_path)"
LOG_DIR="${MT_WIN_LOG_DIR:-$WIN_DIR/logs}"

FILTER=""
MT_QUIET=0
MT_OKS=0
MT_WARNS=0
MT_FAILS=0

usage() {
    cat <<'USAGE'
用法：
  bash scripts/test/win/env.sh [--version <ver>] [--quiet]

  --version <ver>  1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1（默认全部四条线）
  --quiet, -q      不打印 INFO 行（只留 CHECK 与 ENV_RESULT）
  -h, --help       本用法

退出码：0=无 FAIL；1=有 FAIL；2=用法错误
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version)   FILTER="${2:-}"; shift 2 ;;
        --version=*) FILTER="${1#*=}"; shift ;;
        --quiet|-q)  MT_QUIET=1; shift ;;
        -h|--help)   usage; exit 0 ;;
        *) echo "用法错误：未知参数 $1（-h 看用法）" >&2; exit 2 ;;
    esac
done

if [ -n "$FILTER" ]; then
    if ! mt_is_version "$FILTER"; then
        echo "用法错误：--version 只接受 $MT_ALL_VERSIONS" >&2
        exit 2
    fi
    VERSIONS="$FILTER"
else
    VERSIONS="$MT_ALL_VERSIONS"
fi

printf 'INFO: session — Windows 测试台 env.sh %s（git bash/MSYS2；repo=%s）\n' \
    "$MT_COMMON_VERSION" "$MT_REPO_ROOT"

# ── 1. 仓库与文件集 ───────────────────────────────────────────────────────
missing=""
for f in settings.gradle gradlew.bat gradle/wrapper/gradle-wrapper.jar \
         scripts/test/win/mt.sh scripts/test/win/env.sh scripts/test/win/lib/common.sh; do
    [ -f "$MT_REPO_ROOT/$f" ] || missing="$missing $f"
done
if [ -n "$missing" ]; then
    mt_fail repo "仓库根 $MT_REPO_ROOT 缺失:$missing"
else
    mt_ok repo "仓库根正常：$MT_REPO_ROOT（settings.gradle / gradlew.bat / wrapper jar / mt.sh 齐全）"
fi

git_top="$(git -C "$MT_REPO_ROOT" rev-parse --show-toplevel 2>/dev/null | head -n 1 | tr -d '\r')"
if [ -n "$git_top" ]; then
    mt_ok git_root "git rev-parse --show-toplevel = $git_top（本脚本解析为 $(mt_to_msys "$git_top" 2>/dev/null || printf '%s' "$git_top")）"
else
    mt_warn git_root "git rev-parse --show-toplevel 失败（非 git 工作树？仓库根已由 settings.gradle 上溯兜底）"
fi

# ── 2. 文件系统（替代 findmnt：df -T）────────────────────────────────────
df_line="$(df -T "$MT_REPO_ROOT" 2>/dev/null | sed -n '2p')"
fs_type="$(printf '%s\n' "$df_line" | awk '{print $2}')"
fs_mnt="$(printf '%s\n' "$df_line" | awk '{print $NF}')"
case "$fs_type" in
    ntfs|NTFS)
        mt_ok fs "df -T 报文件系统=$fs_type（挂载点 $fs_mnt；Windows 同盘，大小写不敏感/CRLF 注意）" ;;
    cifs|smb*|nfs*|9p|fuse*)
        mt_warn fs "文件系统=$fs_type（网络/共享盘：Gradle 构建极慢，建议本地 NTFS）" ;;
    '')
        mt_warn fs "df -T 未给出 Type 列（$df_line）" ;;
    *)
        mt_info fs "文件系统=$fs_type（挂载点 $fs_mnt）" ;;
esac

# ── 3. Gradle Wrapper ────────────────────────────────────────────────────
if [ -f "$MT_REPO_ROOT/gradlew.bat" ]; then
    mt_ok gradlew "gradlew.bat 在位（Windows 侧入口；git bash 下调用 cmd //c gradlew.bat）"
    dist_url="$(grep -E '^distributionUrl=' "$MT_REPO_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | cut -d= -f2- | tr -d '\r')"
    mt_info gradle_wrapper "distributionUrl=$dist_url"
else
    mt_fail gradlew "缺失 gradlew.bat（$MT_REPO_ROOT/gradlew.bat）"
fi

# ── 4. mt.conf 关键键 ────────────────────────────────────────────────────
if [ -f "$CONF" ]; then
    miss_keys=""
    for k in MT_AUTOJS_ENDPOINT MT_AUTOJS_TIMEOUT_MS \
             MT_PACK_MODS_NEOFORGE MT_PACK_MODS_FORGE MT_PACK_MODS_NEOFORGE_26_1_2; do
        v="$(mt_conf_get "$k" '')"
        [ -n "$v" ] || miss_keys="$miss_keys $k"
    done
    if [ -n "$miss_keys" ]; then
        mt_warn conf "mt.conf 在位但缺键:$miss_keys"
    else
        mt_ok conf "mt.conf 关键键齐全（endpoint/timeout/三条 pack_mods）"
    fi
    mt_info conf_values "MT_AUTOJS_ENDPOINT=$(mt_conf_get MT_AUTOJS_ENDPOINT '<默认>') MT_AUTOJS_TIMEOUT_MS=$(mt_conf_get MT_AUTOJS_TIMEOUT_MS '<默认>')"
else
    mt_fail conf "mt.conf 不存在：$CONF（gitignored 的机器本地配置）"
fi

# ── 5. JDK（JAVA_HOME / where java / java -version）───────────────────────
if [ -n "${JAVA_HOME:-}" ]; then
    if [ -x "$(mt_to_msys "$JAVA_HOME")/bin/java.exe" ] || [ -x "$(mt_to_msys "$JAVA_HOME")/bin/java" ]; then
        jline="$(mt_java_line "$(mt_to_msys "$JAVA_HOME")/bin/java.exe" 2>/dev/null || mt_java_line "$(mt_to_msys "$JAVA_HOME")/bin/java" 2>/dev/null || true)"
        mt_ok java_home "JAVA_HOME=$JAVA_HOME（$jline）"
    else
        mt_warn java_home "JAVA_HOME=$JAVA_HOME 但该目录下找不到 bin/java.exe"
    fi
else
    mt_warn java_home "JAVA_HOME 未设置（launch.sh 会自行发现 daemon JVM ≥21）"
fi
where_java="$(cmd //c 'where java' 2>/dev/null | tr -d '\r' | tr '\n' ';' | sed 's/;$//')"
mt_info java_where "where java → ${where_java:-<未找到>}"
java_major="$(mt_java_major "$(command -v java 2>/dev/null || echo '')" 2>/dev/null || true)"
if [ -n "$java_major" ]; then
    mt_ok java_default "PATH 上的 java 主版本=$java_major（$(mt_java_line "$(command -v java)" 2>/dev/null || echo '?')）"
else
    mt_warn java_default "PATH 上找不到可执行的 java"
fi

# ── 6. 每条线：toolchain JDK / run 目录 / mods ───────────────────────────
for v in $VERSIONS; do
    proj="$(mt_subproject "$v")" || { mt_fail "version_$v" "未知版本"; continue; }
    toolchain_ver="$(mt_toolchain "$v")"
    run_dir="$MT_REPO_ROOT/run/$v"
    # fabric-1.20.1 是双 run 目录（<子项目>/run/<client|server>）；其它三线仍是单目录。
    # check_dirs 是「本条线要检查的 run 目录」列表，下面所有检查都按它循环。
    check_dirs="$run_dir"
    if mt_is_fabric "$v"; then
        check_dirs="$MT_REPO_ROOT/$v/run/client $MT_REPO_ROOT/$v/run/server"
        run_dir="$(mt_fab_side_dir client)"
        mt_info "run_layout_$v" "fabric 双 run 目录：$(mt_fab_run_label "$v" client) | $(mt_fab_run_label "$v" server)（--side 切换；RCON 127.0.0.1:$(mt_fab_rcon_port)）"
    fi

    # 先按 toolchain 精确匹配（JAVA_HOME 可能指向别的版本，不能直接采信）；
    # 不精确时退回"任意候选"并降级为 WARN：Gradle 会尝试 auto-provision，失败则报 toolchain 不匹配。
    jdk_home="$(mt_find_jdk_exact "$toolchain_ver" 2>/dev/null || true)"
    exact=1
    if [ -z "$jdk_home" ]; then
        exact=0
        jdk_home="$(mt_find_jdk "$toolchain_ver" 2>/dev/null || true)"
    fi
    if [ -z "$jdk_home" ]; then
        mt_fail "jdk_$v" "本线 toolchain=$toolchain_ver（:$proj），本机未发现该版本 JDK（找过 C:/Program Files/Zulu/zulu-$toolchain_ver、C:/Program Files/Java/jdk-$toolchain_ver、Program Files/Eclipse Adoptium、Amazon Corretto、~/.gradle/jdks/*$toolchain_ver*、~/.jdks/*$toolchain_ver*、JAVA_HOME）"
    else
        jline="$(mt_java_line "$jdk_home/bin/java.exe" 2>/dev/null || mt_java_line "$jdk_home/bin/java" 2>/dev/null || true)"
        major="$(mt_java_major "$jdk_home/bin/java.exe" 2>/dev/null || mt_java_major "$jdk_home/bin/java" 2>/dev/null || true)"
        if [ "$exact" = 1 ] && [ "$major" = "$toolchain_ver" ]; then
            mt_ok "jdk_$v" "toolchain=$toolchain_ver 精确在位：$jdk_home（$jline）"
        else
            mt_warn "jdk_$v" "toolchain=$toolchain_ver 无精确 JDK，退到主版本=${major:-?}：$jdk_home（$jline）；Gradle 可能 auto-provision 或报 toolchain 不匹配"
        fi
    fi

    any_run=0
    for rd in $check_dirs; do
        if [ ! -d "$rd" ]; then
            mt_warn "run_dir_$v" "${rd#$MT_REPO_ROOT/} 不存在（首次 :$proj:$([ "$rd" = "$run_dir" ] && printf runClient || printf runServer) 后由 Gradle 创建）"
            continue
        fi
        any_run=1
        mt_ok "run_dir_$v" "${rd#$MT_REPO_ROOT/} 存在"

        if [ -d "$rd/mods" ]; then
            stats="$(mt_mods_stats "$rd" 2>/dev/null || true)"
            total="$(printf '%s' "$stats" | awk '{print $1}')"
            aj="$(printf '%s' "$stats" | awk '{print $2}')"
            rh="$(printf '%s' "$stats" | awk '{print $3}')"
            names="$(printf '%s' "$stats" | cut -d' ' -f4-)"
            mt_info "mods_$v" "${rd#$MT_REPO_ROOT/}/mods 共 ${total:-0} 个条目；autojs=${aj:-0}；rhino=${rh:-0}${names:+；aj_jar:${names}}"
            # 2026-11-01 用户裁决：AutoJS 就绪判据放宽至所有版本 —— 不再按版本白名单，
            # 只看本线 run/<版本>/mods 里 AutoJS jar 是否在位（四线实测都在位）。
            # fabric 例外：它是**条件依赖**（-PautojsTestMod=1 才装），没有 jar 属正常，
            # 因此这里额外说明「注入走 RCON」，不把它当成缺口。
            if [ "${aj:-0}" -gt 0 ]; then
                mt_ok "autojs_jar_$v" "AutoJS jar 在位（$aj 个）${names}——该线纳入 AutoJS 就绪判据（launch.sh 主判据 health=200 + status；marker 仅兜底）"
            elif mt_is_fabric "$v"; then
                mt_info "autojs_jar_$v" "${rd#$MT_REPO_ROOT/}/mods 无 AutoJS*.jar（fabric 条件依赖：-PautojsTestMod 未开启）→ 注入/断言回退 RCON 127.0.0.1:$(mt_fab_rcon_port)"
            else
                mt_warn "autojs_jar_$v" "${rd#$MT_REPO_ROOT/}/mods 无 AutoJS*.jar（该线不做 AutoJS 就绪判定，走日志 marker 兜底；单线缺 jar 不阻塞整体 env）"
            fi
        else
            mt_warn "mods_$v" "${rd#$MT_REPO_ROOT/}/mods 不存在（env 装配尚未执行）"
        fi
    done
    [ "$any_run" = 1 ] || continue

    if [ -f "$MT_REPO_ROOT/run/start-$v.bat" ]; then
        mt_info "start_bat_$v" "run/start-$v.bat 在位（launch.sh 只参考其命令行，不直接调用：它结尾有 pause）"
    elif mt_is_fabric "$v"; then
        mt_info "start_bat_$v" "fabric 线没有 run/start-$v.bat（统一走 win/launch.sh --side）"
    else
        mt_info "start_bat_$v" "run/start-$v.bat 不存在（launch.sh 直接走 cmd //c gradlew.bat）"
    fi
done

# ── 7. AutoJS 端点（是否纳入判据按 run/<版本>/mods 里 AutoJS jar 是否在位判定）
endpoint="$(mt_aj_endpoint)"
base_url="${endpoint%/mcp}"
port="${base_url##*:}"
port="${port%%/*}"
mt_info autojs_endpoint "endpoint=$endpoint base=$base_url port=$port timeout_ms=$(mt_conf_get MT_AUTOJS_TIMEOUT_MS '<默认>')"

checked_autojs=0
for v in $VERSIONS; do
    aj_n_v="$(mt_autojs_jar_count "$MT_REPO_ROOT/run/$v" 2>/dev/null || true)"
    [ -n "$aj_n_v" ] || aj_n_v=0
    if [ "$aj_n_v" -gt 0 ]; then
        checked_autojs=1
        break
    fi
done

if [ "$checked_autojs" = 1 ]; then
    hcode="$(mt_aj_health_code 3)"
    case "$hcode" in
        200)
            mt_ok autojs_health "GET $base_url/health = 200（端点在线；游戏已启动且 AutoJS 就绪）" ;;
        000)
            mt_warn autojs_health "GET $base_url/health = 000（端点未运行：游戏未启动属预期；launch.sh 启动后会复检）" ;;
        *)
            mt_fail autojs_health "GET $base_url/health = $hcode（有响应但非 200，AutoJS 通道异常）" ;;
    esac
    if mt_have netstat; then
        lst="$(netstat -ano 2>/dev/null | grep -E "TCP[^:]*:[0-9]+[^:]*:[0-9]+\s+:$port\s" | head -n 3 | tr -d '\r')"
        if [ -n "$lst" ]; then
            mt_info autojs_port "netstat -ano：TCP :$port 已监听 → $(printf '%s' "$lst" | tr '\n' ' ')"
        else
            mt_info autojs_port "netstat -ano：TCP :$port 未监听"
        fi
    else
        mt_info autojs_port "netstat 不可用，跳过端口探测"
    fi
else
    mt_info autojs_health "本次检查的线（$VERSIONS）的 run/<版本>/mods 里都没有 AutoJS jar，跳过 /health"
fi

for v in $VERSIONS; do
    aj_exists=0
    aj_where=""
    scan_dirs="$MT_REPO_ROOT/run/$v"
    if mt_is_fabric "$v"; then
        scan_dirs="$MT_REPO_ROOT/$v/run/client $MT_REPO_ROOT/$v/run/server"
    fi
    for sd in $scan_dirs; do
        n="$(mt_autojs_jar_count "$sd" 2>/dev/null || true)"
        [ -n "$n" ] || n=0
        if [ "$n" -gt 0 ]; then
            aj_exists=$((aj_exists + n))
            aj_where="$aj_where ${sd#$MT_REPO_ROOT/}"
        fi
    done
    if [ "$aj_exists" -gt 0 ]; then
        mt_info "autojs_line_$v" "该线纳入 AutoJS 就绪判据（launch.sh 主判据 health=200 + status(client available=true)，marker 仅兜底）；${aj_where# }/mods 里 AutoJS jar=$aj_exists"
    elif mt_is_fabric "$v"; then
        mt_info "autojs_line_$v" "fabric 双侧 mods 里都无 AutoJS jar（=0，-PautojsTestMod 未开启属正常），注入与断言改走 RCON 127.0.0.1:$(mt_fab_rcon_port)"
    else
        mt_info "autojs_line_$v" "run/$v/mods 里无 AutoJS jar（=0），该线改走日志 marker 兜底"
    fi
done

# ── 7b. fabric-1.20.1 的 RCON 通道（AutoJS 是条件依赖；未开启时注入/断言全靠它）──
case " $VERSIONS " in
    *" fabric-1.20.1 "*)
        fab_srv_dir="$(mt_fab_side_dir server)"
        fab_props="$fab_srv_dir/server.properties"
        if [ ! -d "$fab_srv_dir" ]; then
            mt_warn fab_run_server "${fab_srv_dir#$MT_REPO_ROOT/} 不存在（首次 :fabric-1.20.1:runServer 后由 Gradle 创建；RCON 配置也落在这个目录）"
        elif [ ! -f "$fab_props" ]; then
            mt_warn fab_server_properties "${fab_props#$MT_REPO_ROOT/} 不存在（先完整启动一次服务端才会生成）"
        else
            rcon_enable="$(mt_fab_server_property enable-rcon 2>/dev/null || true)"
            rcon_port="$(mt_fab_rcon_port)"
            rcon_pw="$(mt_fab_rcon_password)"
            rcon_mode="$(mt_fab_autojs_mode "${MT_SIDE:-}")"
            case "$rcon_enable" in
                true)
                    mt_ok fab_rcon "enable-rcon=true rcon.port=$rcon_port rcon.password=已设置（${#rcon_pw} 字符，不打印明文）；注入通道 127.0.0.1:$rcon_port" ;;
                '')
                    mt_warn fab_rcon "server.properties 缺 enable-rcon（等价默认 false）→ RCON 不可用；需要时补 enable-rcon=true rcon.port=$rcon_port rcon.password=astralft" ;;
                *)
                    mt_warn fab_rcon "enable-rcon=$rcon_enable（需要 true 才有 RCON 通道）" ;;
            esac
            mt_info fab_channel "注入通道判定：autojs=$rcon_mode（fabric 的 AutoJS 是条件依赖，见 fabric-1.20.1/build.gradle:182-190，不开 -PautojsTestMod 就只有 RCON）"
            if mt_have netstat; then
                if mt_rcon_probe "$rcon_port"; then
                    mt_ok fab_rcon_port "TCP 127.0.0.1:$rcon_port 可达（服务端在跑且 RCON 已监听）"
                else
                    mt_info fab_rcon_port "TCP 127.0.0.1:$rcon_port 不可达（服务端未启动属预期；launch --side server 后由 cases 阶段复检）"
                fi
            else
                mt_info fab_rcon_port "netstat 不可用，跳过 RCON 端口探测"
            fi
        fi
        ;;
esac
# ── 8. java 进程残留（CIM 判线，绝不按进程名批量处理）────────────────────
if ! rows="$(mt_cim_java_rows 2>/dev/null)"; then
    mt_fail residue "CIM 查询 Win32_Process 失败（pwsh -NoProfile Get-CimInstance 不可用）"
elif [ -z "$rows" ]; then
    mt_ok residue "无 java 进程残留（CIM Win32_Process 查询为空）"
else
    found=0
    while IFS=$'\t' read -r pid cmd; do
        [ -n "$pid" ] || continue
        hit=""
        for v in $MT_ALL_VERSIONS; do
            if mt_cmdline_matches_version "$v" "$cmd"; then hit="$v"; break; fi
        done
        if [ -n "$hit" ]; then
            found=1
            mt_warn residue "PID $pid 属 $hit 线的客户端/构建进程仍在运行 → 先跑 bash scripts/test/win/stop.sh --version $hit"
        fi
    done <<< "$rows"
    if [ "$found" = 0 ]; then
        n=$(printf '%s\n' "$rows" | grep -c .)
        mt_ok residue "有 $n 个 java 进程但都不属于本仓库四线（Gradle daemon / IDE / 其它项目）"
    fi
fi

# ── 9. 工作目录与可写性 ──────────────────────────────────────────────────
cwd_now="$(pwd -P 2>/dev/null || echo '?')"
if [ "$cwd_now" = "$MT_REPO_ROOT" ]; then
    mt_ok cwd "当前 cwd = 仓库根"
else
    mt_warn cwd "当前 cwd=$cwd_now（建议在仓库根执行；脚本内部全用绝对路径，不受影响）"
fi

if mt_dir_writable "$MT_REPO_ROOT"; then
    mt_ok cwd_writable "仓库根可写：$MT_REPO_ROOT"
else
    mt_fail cwd_writable "仓库根不可写：$MT_REPO_ROOT（检查 NTFS 权限 / 沙箱策略）"
fi

wt_bad=""
wt_ok=""
for d in "$LOG_DIR" "$MT_REPO_ROOT/run/1.21.1"; do
    if mt_dir_writable "$d"; then
        wt_ok="$wt_ok $(printf '%s' "$d" | sed "s|$MT_REPO_ROOT/||")"
    else
        wt_bad="$wt_bad $(printf '%s' "$d" | sed "s|$MT_REPO_ROOT/||")"
    fi
done
if [ -z "$wt_bad" ]; then
    mt_ok write_targets "launch 写目标可写：日志目录与 run/<ver>/（pidfile）；OK 项:$wt_ok"
else
    mt_warn write_targets "launch 写目标不可写:$wt_bad（日志/pidfile 会失败；沙箱/ACL 拒绝时属环境问题，正常交互 shell 下应可写）"
fi

# ── 10. git 换行口径 ─────────────────────────────────────────────────────
autocrlf="$(git -C "$MT_REPO_ROOT" config --get core.autocrlf 2>/dev/null | tr -d '\r')"
case "$autocrlf" in
    true)  mt_ok git_autocrlf "core.autocrlf=true（Windows 侧按 CRLF 落盘；本目录脚本以 LF 提交，.gitattributes 未强制）" ;;
    '')    mt_warn git_autocrlf "core.autocrlf 未设置（脚本为 LF；Windows 检出为 CRLF 时 bash 仍可执行，但 diff 会整文件变动）" ;;
    input) mt_warn git_autocrlf "core.autocrlf=input（检出为 LF，与 Windows 侧其它测试资产口径不同）" ;;
    *)     mt_warn git_autocrlf "core.autocrlf=$autocrlf（期望 true）" ;;
esac

# ── 11. 互斥锁目录 ───────────────────────────────────────────────────────
lock_dir="$(mt_to_msys "${MT_LOCK_DIR:-F:/MCProject/.agent-locks}")"
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
        case "$b" in dsh.lock) continue ;; esac
        others="$others $b"
    done
    if [ -n "$others" ]; then
        mt_warn locks_other "其它 agent 锁活跃：$others（写文件/启动 Gradle 前先确认）"
    else
        mt_ok locks_other "无其它 agent 活跃锁（dsh.lock 属本宿主）"
    fi
    [ -n "$stale" ] && mt_warn locks_stale "陈旧锁（持有进程已退出，不自行删除，报用户裁决）：$stale"
    mt_info locks "锁目录 $lock_dir：$(ls -1 "$lock_dir" 2>/dev/null | tr '\n' ' ')"
else
    mt_info locks "锁目录 $lock_dir 不存在（无跨宿主互斥时属正常）"
fi

# ── 12. Gradle 守护进程数（CIM 命令行含 GradleDaemon）────────────────────
if drows="$(mt_gradle_daemon_rows 2>/dev/null)"; then
    dn=$(printf '%s\n' "$drows" | grep -c . || true)
    wn=$(printf '%s\n' "${rows:-}" | grep -ci 'gradle-wrapper\|gradlew' || true)
    mt_info gradle "CIM：GradleDaemon=${dn:-0} gradlew-wrapper=${wn:-0}（全队只有一个 Gradle 并发位，启动前确认无别的 agent 在构建）"
    if [ "${dn:-0}" -gt 0 ]; then
        while IFS=$'\t' read -r dpid dcmd; do
            [ -n "$dpid" ] || continue
            dhome="$(printf '%s' "$dcmd" | sed -nE 's/.*java\.home=([^ ]+).*/\1/p')"
            mt_info gradle_daemon "PID $dpid java.home=${dhome:-?}"
        done <<< "$drows"
    fi
else
    mt_warn gradle "CIM 查询 Gradle daemon 失败"
fi

# ── 13. 临时目录口径（/tmp 不可写）与 pwsh ───────────────────────────────
# 事实：git bash 的 $TEMP 就是 /tmp，而 /tmp 是 C:\Users\<user>\AppData\Local\Temp 的
# MSYS 挂载点（mount 可见 usertemp 标记）——所以"不用 /tmp"等于"不用 Windows 默认 TEMP"。
# 本会话 ACL/沙箱拒写该目录；AutoJS 探测已改为纯管道（curl -D -），不再依赖临时文件。
tmpd="$(mt_tmp_dir 2>/dev/null || true)"
if [ -n "$tmpd" ]; then
    mt_ok tmp "临时目录=$tmpd（\$TEMP=${TEMP:-<空>} 是 AppData\\Local\\Temp 的挂载点，本机可写）"
else
    mt_warn tmp "\$TEMP(=${TEMP:-<空>}，AppData\\Local\\Temp 的挂载点) 与 run/.mt-tmp 均不可写；AutoJS 探测不受影响（纯管道解析），仅影响将来需要落盘临时文件的扩展"
fi

# PowerShell **解释器**（不是测试资产）：pwsh 7 或系统自带 5.1，只用于 CIM 进程命令行判线。
if pwsh_ver="$(mt_pwsh '$PSVersionTable.PSVersion.ToString()' 2>/dev/null | tr -d '\r' | head -n 1)" && [ -n "$pwsh_ver" ]; then
    mt_ok pwsh "PowerShell 内核 $pwsh_ver（$(mt_pwsh_exe)）；CIM 判线依赖它（非测试资产，.ps1 已全部废弃）"
else
    mt_fail pwsh "找不到 PowerShell 解释器（pwsh 与 %SystemRoot%/System32/WindowsPowerShell/v1.0/powershell.exe 都不可用）—— 无法做 CIM 进程判线；设 MT_PWSH_EXE 指定解释器"
fi

tool_list=""
for t in curl taskkill netstat cmd git python3; do
    if mt_have "$t"; then tool_list="$tool_list $t"; else tool_list="$tool_list ${t}(缺)"; fi
done
mt_info tools "外部工具：$tool_list"

pack_bad=""
pack_ok=""
for k in MT_PACK_MODS_NEOFORGE MT_PACK_MODS_FORGE MT_PACK_MODS_NEOFORGE_26_1_2; do
    v="$(mt_conf_get "$k" '')"
    [ -n "$v" ] || continue
    d="$(mt_to_msys "$v")"
    if [ -d "$d" ]; then pack_ok="$pack_ok $d"; else pack_bad="$pack_bad $k=$v"; fi
done
if [ -n "$pack_bad" ]; then
    mt_warn pack_dirs "整合包 mods 目录不存在:$pack_bad（推包前需挂载对应盘）"
elif [ -n "$pack_ok" ]; then
    mt_ok pack_dirs "mt.conf 指向的三个整合包 mods 目录都在位：$pack_ok"
fi

# ── 汇总 ─────────────────────────────────────────────────────────────────
printf '\n'
if [ "$MT_FAILS" -gt 0 ]; then
    echo "ENV_RESULT: FAIL — $MT_FAILS 项不满足，$MT_WARNS 项提示（$MT_OKS 项通过）"
    exit 1
elif [ "$MT_WARNS" -gt 0 ]; then
    echo "ENV_RESULT: WARN — 0 项阻塞，$MT_WARNS 项提示（$MT_OKS 项通过；多为游戏未启动/首次运行前的预期状态）"
    exit 0
else
    echo "ENV_RESULT: OK — 全部检查通过（$MT_OKS 项）"
    exit 0
fi
