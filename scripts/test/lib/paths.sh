#!/usr/bin/env bash
# mt — 路径与版本映射（bash 侧唯一路径来源）。
# 与 python 侧 lib/mt_paths.py 共用同一张版本表，新增版本必须两侧同步。
# 由各 mt_*.sh 通过 source 引入。

# 不在此处 set -e：由调用方决定错误策略（source 后设置会污染调用方语义）。
MT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MT_TEST_DIR="$(cd "$MT_LIB_DIR/.." && pwd)"
MT_ROOT="$(cd "$MT_TEST_DIR/../.." && pwd)"

# 路径转写（关键）：bash 内部用 POSIX 形式（/f/...），但一旦把路径当参数交给
# 原生 Windows 程序（python / gradle），MSYS 会把它转写成 F:\f\...，于是出现
# 「ModuleNotFoundError: mt_paths」「can't open file 'F:\f\...'」这类假故障。
# 因此凡「交给原生程序」的路径一律先经 mt_native 转成 F:/... 形式。
mt_native() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  else
    printf '%s' "$1"
  fi
}
MT_LIB_DIR_WIN="$(mt_native "$MT_LIB_DIR")"
MT_TEST_DIR_WIN="$(mt_native "$MT_TEST_DIR")"
MT_ROOT_WIN="$(mt_native "$MT_ROOT")"

# 版本顺序 = 测试顺序：1.21.1 优先，通过后才执行 1.20.1。
MT_VERSIONS=("1.21.1" "1.20.1")
MT_PUBLISH_PORT=25565
MT_WORLD_NAME="testworld"

# 机器本地配置（整合包 mods 源等）
# 不直接 `. mt.conf`：配置值可能含空格 / 中文，shell source 会误解析；
# 且 source 等于执行文件内任意 shell。这里逐行解析 KEY=VALUE，并剥掉成对引号。
# 支持 KEY=VALUE / KEY="VALUE" / KEY='VALUE'，忽略注释与空行 —— 两侧（bash/python）行为一致。
mt_load_conf() {
  local conf="$MT_TEST_DIR/mt.conf"
  [ -f "$conf" ] || return 0
  local line key val
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line#"${line%%[![:space:]]*}"}"     # 去前导空白
    case "$line" in ''|'#'*) continue ;; esac
    case "$line" in *=*) ;; *) continue ;; esac
    key="${line%%=*}"; val="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"        # key 尾空白
    key="${key#"${key%%[![:space:]]*}"}"        # key 前空白
    val="${val#"${val%%[![:space:]]*}"}"        # val 前空白
    val="${val%"${val##*[![:space:]]}"}"        # val 尾空白
    case "$val" in                              # 剥成对外层引号
      \"*\") val="${val#\"}"; val="${val%\"}" ;;
      \'*\') val="${val#\'}"; val="${val%\'}" ;;
    esac
    [ -n "$key" ] && printf -v "$key" '%s' "$val"
  done < "$conf"
}
mt_load_conf

: "${MT_PACK_MODS_NEOFORGE:=D:/.minecraft/versions/狐の航空学 Voxy Edition/mods}"
: "${MT_PACK_MODS_FORGE:=D:/.minecraft/versions/1.20.1 模组测试/mods}"
: "${MT_PYTHON:=python}"

# mt_resolve <version> — 导出该版本的全部路径与 Gradle 任务名到 MT_* 变量。
# 返回 2 = 未知版本。调用方应在每次切换版本时重新调用。
mt_resolve() {
  local v="$1"
  case "$v" in
    1.21.1) MT_LOADER="neoforge"; MT_SUBPROJECT="neoforge-1.21.1"; MT_PACK_MODS="$MT_PACK_MODS_NEOFORGE" ;;
    1.20.1) MT_LOADER="forge";    MT_SUBPROJECT="forge-1.20.1";    MT_PACK_MODS="$MT_PACK_MODS_FORGE" ;;
    *) printf 'MT_ERROR: 未知版本 %s（可选：%s）\n' "$v" "${MT_VERSIONS[*]}" >&2; return 2 ;;
  esac

  MT_RUN_DIR="$MT_ROOT/run/$v"
  MT_MODS_DIR="$MT_RUN_DIR/mods"
  MT_SAVES_DIR="$MT_RUN_DIR/saves"
  MT_LOGS_DIR="$MT_RUN_DIR/logs"
  MT_LATEST_LOG="$MT_LOGS_DIR/latest.log"
  MT_DEBUG_LOG="$MT_LOGS_DIR/debug.log"
  MT_KUBEJS_LOG="$MT_LOGS_DIR/kubejs/server.log"
  MT_CRASH_DIR="$MT_RUN_DIR/crash-reports"
  MT_SHOT_DIR="$MT_RUN_DIR/screenshots"
  MT_CLIENT_WORLD="$MT_SAVES_DIR/$MT_WORLD_NAME"
  MT_SERVER_WORLD="$MT_RUN_DIR/$MT_WORLD_NAME"

  MT_TASK_BUILD=":$MT_SUBPROJECT:build"
  MT_TASK_CLIENT=":$MT_SUBPROJECT:runClient"
  MT_TASK_SERVER=":$MT_SUBPROJECT:runServer"
  export MT_VERSION="$v"
}

# mt_latest_shot <version> — 输出当前世代最后一张截图路径（无则返回 1）。
mt_latest_shot() {
  local v="$1"
  "$MT_PYTHON" "$MT_TEST_DIR_WIN/mt_capture.py" --version "$v" --list-latest
}
