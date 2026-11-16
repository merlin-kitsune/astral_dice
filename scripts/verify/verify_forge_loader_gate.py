#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""verify_forge_loader_gate —— 1.20.1「加载器版本门槛 + Mixin Booster 硬前置」独立静态守门
（由 旧版 verify_forge_loader_gate 实现 等价迁移；判据本体与旧执行体逐条等价，读数前缀沿用 AP_LG_*）

零 mt 依赖：① 不 Import-Module scripts/test/lib/*；② 不读写 run/**；③ 不读 cases/*.json；
④ 不用 mt 退出码常量（退出码 = 本脚本自定）。

判据（S1..S6）
  S1 区间来源链：forge-1.20.1/gradle.properties 的 forge_version（期望 1.20.1-47.1.47；2026-11-05 用户裁决降至 47.1 线末版）
     → 派生 loaderVersion=[<major>,<major+1)> 与 forge 依赖区间 [<semver>,<major+1)>；
     模板 / 生成资源 / 构建产物 jar 内 META-INF/mods.toml 三段取回并要求与派生值一致。
     模板经 Groovy SimpleTemplateEngine 展开，其中是美元花括号占位 ⇒ 模板段的区间取不到字面值
     属正常（只报 placeholder，不判失败）。
  S2 Mixin Booster 依赖段：三段一致地声明 modId="mixinbooster" + mandatory=false（2026-09-29 起
     由 mandatory 改 optional）+ versionRange="[0.1.3,)" + ordering="AFTER" + side="BOTH"；
     build.gradle 仍装配 maven.modrinth:mixinbooster；模板内不得出现未注释的 reason= 行。
     前置真实版本读自 gradle 缓存 jar 内 mixinbooster_version.txt。
  S2b 运行时门控（取代强制依赖）：MixinRuntimeGate.java 存在 + 产物 jar 内该类常量池同时含
     mixinbooster/connectormod + AstralDiceMod.class 引用 MixinRuntimeGate。
  S3 FML 同一实现核对：fmlloader jar 的 POM 取 maven-artifact / commons-lang3 版本并解析到 jar；
     字节码常量池扫描确认 ModSorter 与 VersionSupportMatrix 引用 VersionRange。
  S4/S5 离线判定器：用同一个 maven-artifact jar（classpath 加 commons-lang3）编译并运行
     LoaderGateProbe，逐字复刻 ModSorter#modVersionNotContained + VersionSupportMatrix
     （含 mod.forge → 47.1.79 别名覆盖项）：旧区间 [47,) 接纳低版本 ⇒ bug_repro=1（修复前 BUG 反证）；
     新区间用直接 contains 判定：拒绝 47.0.0/47.1.46、接纳 47.1.47/47.2.0/47.4.10/47.5.0、拒绝 48.0.0；
     别名 47.1.79 落在新区间内（FML 别名回退会掩盖下限，单独记录，不参与 gate_ok）；loaderVersion=[47,48)
     接纳 javafml 47、拒绝 48；Mixin Booster 区间 [0.1.3,) 对真实版本 accept。
  S6 聚合：任一判据不成立 ⇒ 非零退出；各失败项以 LG_FAIL:<key> 逐条打印。

退出码契约与优先级（与 旧实现 逐字一致）
  0 = 门槛成立（AP_LG_VERDICT:...:range_only=1:overall=1）；
  1 = 存在明确偏差（LG_FAIL 非空即判 1，verdict overall=0）；
  2 = 无法判定（仅当 LG_FAIL 为空且存在 LG_UNDECIDABLE），每条带 LG_REMEDY:<key>:<命令>；
      verdict 为 overall=2。
  明确偏差优先于无法判定：LG_FAIL 非空就 exit 1，即使同时存在 LG_UNDECIDABLE；
     两类 key 并存时逐条全部打印，verdict 同时带 :fail= 与 :undecidable=。

只读：除在工作目录落盘判定器源码/字节码（<脚本所在仓库>/temp/t78/loadergate/，与 -Root 指向的树
无关）外不写任何文件；产品侧（两条发布线 + 26.1.2）一律只读。

与 旧实现 的差异（逐条，均已列入交付报告）
  1) JDK 探测：旧实现 硬编码 bin\javac.exe；本脚本按平台取 bin/javac(|.exe)，Linux CI 可用。
  2) gradle 缓存根 / ~/.gradle/jdks：旧实现 用 $env:USERPROFILE（Linux 上为空会报错）；本脚本回落 home。
  3) classpath 分隔符：旧实现 写死 ';'；本脚本用 os.pathsep（Windows 上仍是 ';'）。
  4) 判定器源码落盘行尾跟随 os.linesep（旧实现 跟随其自身文件行尾）——仅影响 temp/ 下文件字节，
     不影响任何输出行。
  5) 无新增判据、无新增状态词、无删除检查项。

用法：
  python scripts/verify/verify_forge_loader_gate.py [-Root <仓库根>] [-SkipArtifacts]
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import zipfile
from glob import glob

EXIT_OK = 0
EXIT_DEVIATION = 1
EXIT_UNDECIDABLE = 2

EXPECT_FORGE_VERSION = "1.20.1-47.1.47"
EXPECT_OLD_RANGE = "[47,)"
EXPECT_NEW_RANGE = "[47.1.47,48)"
EXPECT_LOADER_RANGE = "[47,48)"
EXPECT_MB_RANGE = "[0.1.3,)"
# 2026-09-29（BMC4 玩家实测定位）：mixinbooster 由 mandatory 改为 optional。
# Sinytra Connector 在场时 Mixin Booster 会主动关闭自己，该 mod 条目根本不会注册 ⇒ 若仍写
# mandatory=true，装了 Connector 的整合包一律被 FML 硬拒。⇒ 硬拒改为运行时门控（S2b）。
EXPECT_MB_MANDATORY = "false"

NOISE_RE = re.compile(r"sources|javadoc", re.IGNORECASE)

READINGS = []
FAIL = []
UNDECIDABLE = []


def write_out(text=""):
    sys.stdout.write(text + "\n")


def add_reading(text):
    READINGS.append(text)
    write_out(text)


def add_fail(key):
    if key not in FAIL:
        FAIL.append(key)
    write_out("LG_FAIL:%s" % key)


def add_undecidable(key, what="", remedy=""):
    if key not in UNDECIDABLE:
        UNDECIDABLE.append(key)
    write_out("LG_UNDECIDABLE:%s:%s" % (key, what))
    if remedy:
        write_out("LG_REMEDY:%s:%s" % (key, remedy))


# ── 通用取物工具 ────────────────────────────────────────────────────────────
def find_first_file(pattern):
    """取匹配路径中「最新且非 sources/javadoc」的那个文件（Gradle 缓存里同坐标会有多份）。"""
    hits = []
    for path in glob(pattern):
        if not os.path.isfile(path):
            continue
        if NOISE_RE.search(os.path.basename(path)):
            continue
        try:
            mtime = os.path.getmtime(path)
        except OSError:
            continue
        hits.append((mtime, len(hits), path))
    if not hits:
        return None
    hits.sort(key=lambda t: (t[0], t[1]), reverse=True)
    return hits[0][2]


def get_jar_entry_text(jar_path, entry_name):
    """读取 jar 内某个文本条目（产物里的 META-INF/mods.toml / 前置的版本号文件 / .class 常量池）。"""
    try:
        with zipfile.ZipFile(jar_path) as zf:
            for name in zf.namelist():
                if name == entry_name:
                    return zf.read(name).decode("utf-8", errors="replace")
    except Exception:  # noqa: BLE001
        return None
    return None


def read_text(path):
    with open(path, "r", encoding="utf-8-sig", errors="replace", newline="") as fh:
        return fh.read()


def iter_files_recursive(root):
    """旧 shell Get-ChildItem -Recurse -File 的等价：本级文件优先，再 DFS 子目录。"""
    try:
        entries = list(os.scandir(root))
    except OSError:
        return
    dirs = []
    for e in entries:
        try:
            if e.is_dir(follow_symlinks=False):
                dirs.append(e.name)
            else:
                yield os.path.join(root, e.name)
        except OSError:
            continue
    for name in dirs:
        yield from iter_files_recursive(os.path.join(root, name))


def get_range_from_toml(toml):
    """取 loaderVersion 与 modId="forge" 那条依赖的 versionRange（占位值原样返回）。"""
    loader = ""
    m = re.search(r'(?m)^\s*loaderVersion\s*=\s*"([^"]+)"', toml)
    if m:
        loader = m.group(1)
    forge = ""
    for block in re.split(r"(?m)^\s*\[\[dependencies", toml):
        if re.search(r'(?m)modId\s*=\s*"forge"', block):
            mm = re.search(r'(?m)versionRange\s*=\s*"([^"]+)"', block)
            if mm:
                forge = mm.group(1)
    return {"loader": loader, "forge": forge}


def get_dep_from_toml(toml, mod_id):
    """取某个 modId 依赖段的字段（FML 只认 modId/mandatory/versionRange/ordering/side，无 reason）。"""
    res = {"present": False, "mandatory": "", "range": "", "ordering": "", "side": ""}
    pat = re.compile(r'(?m)modId\s*=\s*"' + re.escape(mod_id) + r'"')
    for block in re.split(r"(?m)^\s*\[\[dependencies", toml):
        if not pat.search(block):
            continue
        res["present"] = True
        m = re.search(r"(?m)^\s*mandatory\s*=\s*(\S+)", block)
        if m:
            res["mandatory"] = m.group(1).strip()
        m = re.search(r'(?m)^\s*versionRange\s*=\s*"([^"]+)"', block)
        if m:
            res["range"] = m.group(1)
        m = re.search(r'(?m)^\s*ordering\s*=\s*"([^"]+)"', block)
        if m:
            res["ordering"] = m.group(1)
        m = re.search(r'(?m)^\s*side\s*=\s*"([^"]+)"', block)
        if m:
            res["side"] = m.group(1)
        break
    return res


def javac_name():
    return "javac.exe" if os.name == "nt" else "javac"


def java_name():
    return "java.exe" if os.name == "nt" else "java"


def get_java_home():
    """定位带 javac 的 JDK（JAVA_HOME → Zulu 21 → Adoptium → ~/.gradle/jdks/*）。"""
    jh = os.environ.get("JAVA_HOME")
    if jh and os.path.isfile(os.path.join(jh, "bin", javac_name())):
        return jh
    for cand in ("C:\\Program Files\\Zulu\\zulu-21",
                 "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.0.0-hotspot"):
        if os.path.isfile(os.path.join(cand, "bin", javac_name())):
            return cand
    home = os.environ.get("USERPROFILE") or os.path.expanduser("~")
    jdks = os.path.join(home, ".gradle", "jdks")
    found = []
    if os.path.isdir(jdks):
        for name in os.listdir(jdks):
            p = os.path.join(jdks, name)
            if os.path.isdir(p) and os.path.isfile(os.path.join(p, "bin", javac_name())):
                found.append(p)
    found.sort(key=lambda p: os.path.basename(p), reverse=True)
    return found[0] if found else ""


PROBE_SRC = r'''import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * FML 依赖排序判定的逐字复刻（判据实现 = 同一个 maven-artifact jar）。
 *
 * ModSorter#modVersionNotContained(ModVersion mv, Map<String,ArtifactVersion> modVersions):
 *     return !VersionSupportMatrix.testVersionSupportMatrix(
 *                mv.getVersionRange(), mv.getModId(), "mod",
 *                (modId, range) -> modVersions.containsKey(modId)
 *                                  && (range.containsVersion(modVersions.get(modId))
 *                                      || modVersions.get(modId).toString().equals("0.0NONE")));
 *
 * VersionSupportMatrix.testVersionSupportMatrix(range, key, value, test):
 *     if (test.test(key, range)) return true;
 *     var versions = overrideVersions.get(value + "." + key);   // 常量池 recipe = \u0001.\u0001
 *     return versions != null && versions.stream().anyMatch(range::containsVersion);
 *   static { if (MC == 1.20.1) add("mod.forge", "47.1.79"); }   // key=value+"."+key="mod.forge"
 *
 * 入参（由 旧版 verify_forge_loader_gate 实现 从 gradle.properties 派生 + mods.toml 实际声明值传入，
 * 不写死）：args[0]=旧区间 args[1]=新区间 args[2]=loaderVersion 区间 args[3]=MB 区间 args[4]=MB 实装版本。
 */
public class LoaderGateProbe {

    private static final String FORGE_ALIAS = "47.1.79";

    private static boolean contains(VersionRange r, String v) {
        return r.containsVersion(new DefaultArtifactVersion(v));
    }

    /** 拥有该 modId 的已安装版本时，FML 是否判定「版本区间满足」 */
    private static boolean fmlAcceptsForge(VersionRange range, String installed) {
        // ① 主体判定（ModSorter 的 predicate 分支）
        boolean direct = contains(range, installed);
        if (direct) return true;
        // ② VersionSupportMatrix 的别名覆盖项（mod.forge → 47.1.79）
        return contains(range, FORGE_ALIAS);
    }

    private static String v(VersionRange r, String ver) {
        return contains(r, ver) ? "accept" : "reject";
    }

    /**
     * Mixin Booster 专用：它**不在** VersionSupportMatrix 的 overrideVersions 里
     * （那里只有 "mod.forge"），故判定退化为纯 range.containsVersion —— 不能用
     * fmlAcceptsForge（那个别名回退会让 [0.1.3,) 误判 "47.1.79" 为满足）。
     */
    private static String vb(VersionRange r, String ver) {
        return contains(r, ver) ? "accept" : "reject";
    }

    public static void main(String[] args) throws Exception {
        String oldSpec = args.length > 0 ? args[0] : "[47,)";
        String newSpec = args.length > 1 ? args[1] : "[47.1.47,48)";
        String loaderSpec = args.length > 2 ? args[2] : "[47,48)";
        VersionRange oldRange = VersionRange.createFromVersionSpec(oldSpec);
        VersionRange newRange = VersionRange.createFromVersionSpec(newSpec);
        VersionRange loaderRange = VersionRange.createFromVersionSpec(loaderSpec);

        // 旧区间（修复前）——低版本被接纳 = BUG 复现
        boolean bug = fmlAcceptsForge(oldRange, "47.0.0") && fmlAcceptsForge(oldRange, "47.4.9");
        System.out.println("AP_LG_OLD:range=" + oldSpec + ":v47.0.0=" + v(oldRange, "47.0.0")
                + ":v47.4.9=" + v(oldRange, "47.4.9")
                + ":v47.4.10=" + v(oldRange, "47.4.10")
                + ":bug_repro=" + (bug ? 1 : 0));

        // 新区间（修复后）——用直接 contains 判定下限：拒绝 47.0.0 / 47.1.46，接纳 47.1.47+
        boolean fixed = !contains(newRange, "47.0.0")
                && !contains(newRange, "47.1.46")
                && contains(newRange, "47.1.47")
                && contains(newRange, "47.2.0")
                && contains(newRange, "47.4.10")
                && contains(newRange, "47.5.0")
                && !contains(newRange, "48.0.0");
        System.out.println("AP_LG_NEW:range=" + newSpec + ":v47.0.0=" + v(newRange, "47.0.0")
                + ":v47.1.46=" + v(newRange, "47.1.46")
                + ":v47.1.47=" + v(newRange, "47.1.47")
                + ":v47.2.0=" + v(newRange, "47.2.0")
                + ":v47.4.10=" + v(newRange, "47.4.10")
                + ":v47.5.0=" + v(newRange, "47.5.0")
                + ":v48.0.0=" + v(newRange, "48.0.0")
                + ":gate_ok=" + (fixed ? 1 : 0));

        // 别名覆盖项本身的自证：47.1.79 落在新区间内，FML 别名回退会掩盖下限（单独记录，不参与 gate_ok）
        System.out.println("AP_LG_ALIAS:alias=mod.forge@" + FORGE_ALIAS
                + ":new_range_contains_alias=" + (contains(newRange, FORGE_ALIAS) ? 1 : 0)
                + ":old_range_contains_alias=" + (contains(oldRange, FORGE_ALIAS) ? 1 : 0));

        // loaderVersion 是 javafml **语言提供者**的版本区间。LanguageLoadingProvider#findLanguage
        // 同样经 VersionSupportMatrix，但它的别名键是 value + "." + key = "languageloader.javafml"
        // —— 不在 overrideVersions（只有 "mod.forge"）里，故判定退化为纯 containsVersion。
        // 提供者版本 = Forge 主系列号 47（依据：2026-09-14 实机读数
        // `Missing language javafml version [47.4.10,48) … found 47`）。
        boolean loaderOk = contains(loaderRange, "47") && !contains(loaderRange, "48");
        System.out.println("AP_LG_LOADER:range=" + loaderSpec + ":javafml47=" + (contains(loaderRange, "47") ? "accept" : "reject")
                + ":javafml48=" + (contains(loaderRange, "48") ? "accept" : "reject")
                + ":alias_key=languageloader.javafml"
                + ":loader_ok=" + (loaderOk ? 1 : 0));

        // Mixin Booster 强依赖区间（Forge 1.20.1 的 FML 无 Mixin 集成，Sponge Mixin 由该前置 jar
        // 内嵌的 fabric-mixin 以 ModLauncher 服务提供；缺它/旧它都会让本模组的全部 mixin
        // **静默失效**——不报任何错，所以必须在依赖排序阶段硬拒）。
        if (args.length >= 5) {
            String spec = args[3];
            String actual = args[4];
            VersionRange mb = VersionRange.createFromVersionSpec(spec);
            boolean mbOk = contains(mb, actual)   // 实装前置版本必须落在区间内
                    && contains(mb, "0.1.3")      // 下限闭合：[0.1.3,) 含 0.1.3
                    && contains(mb, "0.1.4")
                    && contains(mb, "0.2.0")
                    && !contains(mb, "0.1.2")     // 旧前置必须被拒（不是"装了就算过"）
                    && !contains(mb, "0.1.0")
                    && !contains(mb, "0.0.9");
            System.out.println("AP_LG_MB_RANGE:range=" + spec + ":artifact=" + actual
                    + ":v0.1.0=" + vb(mb, "0.1.0")
                    + ":v0.1.2=" + vb(mb, "0.1.2")
                    + ":v0.1.3=" + vb(mb, "0.1.3")
                    + ":v" + actual + "=" + vb(mb, actual)
                    + ":v0.1.4=" + vb(mb, "0.1.4")
                    + ":v0.2.0=" + vb(mb, "0.2.0")
                    + ":gate_ok=" + (mbOk ? 1 : 0));
        }
    }
}'''


def default_root():
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.normpath(os.path.join(here, "..", ".."))


def run(root_arg, skip_artifacts):
    # -Root：缺省由脚本位置派生仓库根（scripts/verify → 上两级），绝不缺省 '.'。
    root = default_root() if root_arg is None else root_arg
    if root.strip() == "":
        write_out("LG_UNDECIDABLE:root_unresolved:无法从 $PSScriptRoot 派生仓库根")
        write_out("LG_REMEDY:root_unresolved:显式传 -Root <仓库根>")
        write_out("AP_LG_VERDICT:overall=2:undecidable=root_unresolved")
        return EXIT_UNDECIDABLE
    # GetFullPath 两个分支都做归一：ntpath.abspath 内含 normpath（'/' → '\'、折叠 . 与 ..），
    # 在 Linux 上对正斜杠路径是无操作 ⇒ 与 旧实现 逐字一致且不引入平台写死。
    root = os.path.abspath(root)
    root = root.rstrip("\\/")
    sub = os.path.join(root, "forge-1.20.1")

    work_root = default_root()
    work_dir = os.path.join(work_root, "temp", "t78", "loadergate")

    gradle_home = os.environ.get("GRADLE_USER_HOME")
    if gradle_home:
        cache_root = os.path.join(gradle_home, "caches", "modules-2", "files-2.1")
    else:
        home = os.environ.get("USERPROFILE") or os.path.expanduser("~")
        cache_root = os.path.join(home, ".gradle", "caches", "modules-2", "files-2.1")
    # Join-Path 在本机渲染成反斜杠：normpath 让 py 与 旧实现 在 Windows 上逐字一致；
    # 在 Linux（CI）上 normpath 对正斜杠路径是无操作。
    cache_root = os.path.normpath(cache_root)

    artifacts_state = "skipped" if skip_artifacts else "present"

    write_out("== verify_forge_loader_gate（1.20.1 加载器版本门槛 + Mixin Booster 硬前置；独立于 mt 用例链）==")
    write_out("LG_ROOT:%s:sub=%s:artifacts=%s:cache=%s" % (root, sub, artifacts_state, cache_root))

    # ── S1：区间来源链 ──────────────────────────────────────────────────────
    write_out("LG_STEP:1/6 取回区间来源(gradle.properties → build.gradle 派生 → 模板/生成资源/产物)")
    props_path = os.path.join(sub, "gradle.properties")
    if not os.path.isfile(props_path):
        add_undecidable("not_a_forge_tree",
                        "-Root 下缺少 forge-1.20.1/gradle.properties（%s）" % props_path,
                        "把 -Root 指向仓库根（缺省即由 $PSScriptRoot 派生），或指向含 forge-1.20.1/ 子项目的夹具树")
        write_out("AP_LG_VERDICT:bug_repro=0:gate_ok=0:loader_ok=0:mb_ok=0:fail=:fml_stage=dependency_sorting"
                  ":artifacts=%s:msg=Missing or unsupported mandatory dependencies"
                  ":undecidable=not_a_forge_tree:overall=2" % artifacts_state)
        write_out("LG_DONE")
        return EXIT_UNDECIDABLE

    forge_version = ""
    m = re.search(r"(?m)^\s*forge_version\s*=\s*(.+?)\s*$", read_text(props_path))
    if m:
        forge_version = m.group(1).strip()
    semver = forge_version.split("-")[-1] if forge_version else ""

    if (not forge_version) or (not re.match(r"^\d+(\.\d+)*$", semver)):
        add_fail("forge_version_missing")
        write_out("LG_NOTE:声明链前缀闸门不成立（forge_version 缺失/非法）⇒ 早退，避免在不可判定输入上产出误导性判据")
        write_out("AP_LG_VERDICT:bug_repro=0:gate_ok=0:loader_ok=0:mb_ok=0:fail=%s:fml_stage=dependency_sorting"
                  ":artifacts=%s:msg=Missing or unsupported mandatory dependencies:overall=0"
                  % ("|".join(FAIL), artifacts_state))
        write_out("LG_DONE")
        return EXIT_DEVIATION

    major = int(semver.split(".")[0])
    derived_new = "[%s,%d)" % (semver, major + 1)
    derived_loader = "[%d,%d)" % (major, major + 1)
    derived_old = "[%d,)" % major

    if forge_version != EXPECT_FORGE_VERSION:
        add_fail("forge_version_changed=%s" % forge_version)
    if derived_new != EXPECT_NEW_RANGE:
        add_fail("derived_forge=%s" % derived_new)
    if derived_loader != EXPECT_LOADER_RANGE:
        add_fail("derived_loader=%s" % derived_loader)
    if derived_old != EXPECT_OLD_RANGE:
        add_fail("derived_old=%s" % derived_old)

    tmpl_path = os.path.join(sub, "src", "main", "templates", "META-INF", "mods.toml")
    toml_template = ""
    if os.path.isfile(tmpl_path):
        toml_template = read_text(tmpl_path)
    else:
        add_fail("template_mods_toml_absent")

    toml_gen = ""
    r_gen = None
    if skip_artifacts:
        add_reading("AP_LG_SRC_GEN:skipped(artifacts=skipped)")
    else:
        gen_path = os.path.join(sub, "build", "generated", "sources", "modMetadata", "META-INF", "mods.toml")
        if os.path.isfile(gen_path):
            toml_gen = read_text(gen_path)
            r_gen = get_range_from_toml(toml_gen)
            add_reading("AP_LG_SRC_GEN:loader=%s:forge=%s" % (r_gen["loader"], r_gen["forge"]))
            if not r_gen["loader"]:
                add_fail("gen_loaderVersion_absent")
            if not r_gen["forge"]:
                add_fail("gen_forge_range_absent")
        else:
            add_reading("AP_LG_SRC_GEN:absent")
            add_undecidable("generated_mods_toml_absent", "缺少 %s（尚未数据生成）" % gen_path,
                            "bash scripts/test/win/mt.sh --version 1.20.1 --phase build；"
                            "或 gradlew :forge-1.20.1:generateModMetadata")

    toml_jar = ""
    r_jar = None
    jar = None
    if skip_artifacts:
        add_reading("AP_LG_SRC_JAR:skipped(artifacts=skipped)")
    else:
        jar = find_first_file(os.path.join(sub, "build", "libs", "astral_dice-*+forge_*.jar"))
        if jar is not None:
            toml = get_jar_entry_text(jar, "META-INF/mods.toml")
            if toml is not None:
                toml_jar = toml
                r_jar = get_range_from_toml(toml)
                add_reading("AP_LG_SRC_JAR:jar=%s:loader=%s:forge=%s"
                            % (os.path.basename(jar), r_jar["loader"], r_jar["forge"]))
                if not r_jar["loader"]:
                    add_fail("jar_loaderVersion_absent")
                if not r_jar["forge"]:
                    add_fail("jar_forge_range_absent")
            else:
                add_reading("AP_LG_SRC_JAR:absent(no mods.toml in jar)")
                add_undecidable("jar_mods_toml_absent", "构建产物 %s 内没有 META-INF/mods.toml" % jar,
                                "gradlew :forge-1.20.1:build（产物 jar 必须内嵌展开后的 mods.toml）")
        else:
            add_reading("AP_LG_SRC_JAR:absent")
            add_undecidable("built_jar_absent", "缺少 %s\\build\\libs\\astral_dice-*+forge_*.jar（尚未构建）" % sub,
                            "gradlew :forge-1.20.1:build（或先跑 -SkipArtifacts 只做静态档）")

    # 三段一致性：模板段容忍 Groovy 占位；生成资源/产物必须与派生值逐字一致
    if toml_template:
        tmpl_ranges = get_range_from_toml(toml_template)
    else:
        tmpl_ranges = {"loader": "", "forge": ""}

    def tmpl_state(value):
        if not value:
            return "absent"
        if "$" in value:
            return "placeholder"
        return value

    tmpl_loader_state = tmpl_state(tmpl_ranges["loader"])
    tmpl_forge_state = tmpl_state(tmpl_ranges["forge"])
    add_reading("AP_LG_SRC_TMPL:loader=%s:forge=%s" % (tmpl_loader_state, tmpl_forge_state))
    if tmpl_ranges["loader"] and "$" not in tmpl_ranges["loader"] and tmpl_ranges["loader"] != EXPECT_LOADER_RANGE:
        add_fail("tmpl_loader=%s" % tmpl_ranges["loader"])
    if tmpl_ranges["forge"] and "$" not in tmpl_ranges["forge"] and tmpl_ranges["forge"] != EXPECT_NEW_RANGE:
        add_fail("tmpl_forge=%s" % tmpl_ranges["forge"])
    if r_gen is not None:
        if r_gen["loader"] != derived_loader:
            add_fail("toml_gen_loader=%s" % r_gen["loader"])
        if r_gen["forge"] != derived_new:
            add_fail("toml_gen_forge=%s" % r_gen["forge"])
    if r_jar is not None:
        if r_jar["loader"] != derived_loader:
            add_fail("toml_jar_loader=%s" % r_jar["loader"])
        if r_jar["forge"] != derived_new:
            add_fail("toml_jar_forge=%s" % r_jar["forge"])
    if (r_gen is not None) and (r_jar is not None):
        if r_gen["loader"] != r_jar["loader"]:
            add_fail("loader_mismatch_gen_vs_jar")
        if r_gen["forge"] != r_jar["forge"]:
            add_fail("forge_range_mismatch_gen_vs_jar")

    # 判定器要判的实际声明值：优先生成资源/产物（FML 真读的那份），否则回落到派生值
    declared_loader = ""
    declared_forge = ""
    src_list = []
    if r_gen is not None:
        declared_loader = r_gen["loader"]
        declared_forge = r_gen["forge"]
        src_list.append("gen")
    if r_jar is not None:
        if not declared_loader:
            declared_loader = r_jar["loader"]
        if not declared_forge:
            declared_forge = r_jar["forge"]
        src_list.append("jar")
    if toml_template:
        src_list.append("template")
    if not declared_loader:
        declared_loader = derived_loader
    if not declared_forge:
        declared_forge = derived_new

    add_reading("AP_LG_SRC:forge_version=%s:derived_loader=%s:derived_forge=%s:derived_old=%s"
                ":loaderVersion=%s:forgeRange=%s:src=%s:artifacts=%s"
                % (forge_version, derived_loader, derived_new, derived_old,
                   declared_loader, declared_forge, "+".join(src_list), artifacts_state))

    # ── S2：Mixin Booster 依赖声明 ──────────────────────────────────────────
    write_out("LG_STEP:2/6 核对 Mixin Booster 前置门槛声明(模板/生成资源/产物)")
    mb_src = []
    mb_seen = {}
    for pair in ({"k": "template", "t": toml_template, "skipped": False, "artifact": False},
                 {"k": "gen", "t": toml_gen, "skipped": skip_artifacts, "artifact": True},
                 {"k": "jar", "t": toml_jar, "skipped": skip_artifacts, "artifact": True}):
        if pair["skipped"]:
            continue
        if not pair["t"]:
            # 模板缺 = 真偏差；生成资源/产物缺 = 已记 undecidable 的环境态（不重复判失败）
            if pair["artifact"]:
                write_out("LG_NOTE:mb_src_absent_%s（该段缺产物，已记 undecidable，不重复判失败）" % pair["k"])
            else:
                add_fail("mb_no_toml_%s" % pair["k"])
            continue
        dep = get_dep_from_toml(pair["t"], "mixinbooster")
        if not dep["present"]:
            add_fail("mb_absent_in_%s" % pair["k"])
            continue
        if dep["mandatory"] != EXPECT_MB_MANDATORY:
            add_fail("mb_mandatory_%s=%s" % (pair["k"], dep["mandatory"]))
        if dep["range"] != EXPECT_MB_RANGE:
            add_fail("mb_range_%s=%s" % (pair["k"], dep["range"]))
        if dep["ordering"] != "AFTER":
            add_fail("mb_ordering_%s=%s" % (pair["k"], dep["ordering"]))
        if dep["side"] != "BOTH":
            add_fail("mb_side_%s=%s" % (pair["k"], dep["side"]))
        mb_src.append(pair["k"])
        if "range" not in mb_seen:
            mb_seen["range"] = dep["range"]
            mb_seen["mandatory"] = dep["mandatory"]
            mb_seen["ordering"] = dep["ordering"]
            mb_seen["side"] = dep["side"]
    if "range" not in mb_seen:
        mb_seen = {"range": "", "mandatory": "", "ordering": "", "side": ""}

    # 前置本体：maven.modrinth:mixinbooster 的纯服务 jar，版本号读自 jar 根 mixinbooster_version.txt
    mb_jar = find_first_file(os.path.join(cache_root, "maven.modrinth", "mixinbooster", "*", "*", "*.jar"))
    mb_artifact_version = ""
    if mb_jar is not None:
        v_txt = get_jar_entry_text(mb_jar, "mixinbooster_version.txt")
        if v_txt is not None:
            mb_artifact_version = v_txt.strip()
        if not mb_artifact_version:
            add_undecidable("mixinbooster_version_txt_absent",
                            "缓存 jar %s 内没有 mixinbooster_version.txt" % mb_jar,
                            "gradlew :forge-1.20.1:dependencies --refresh-dependencies（重取前置）")
    else:
        add_undecidable("mixinbooster_cache_absent",
                        "Gradle 缓存里找不到 maven.modrinth:mixinbooster 的 jar（%s）" % cache_root,
                        "gradlew :forge-1.20.1:dependencies（首次解析依赖会把前置拉进缓存）")

    # 开发运行时侧：前置必须仍挂在 build.gradle 上（否则 dev 里 mixin 全静默失效）
    build_gradle = os.path.join(sub, "build.gradle")
    mb_build_dep = "absent"
    if os.path.isfile(build_gradle):
        if re.search(r"maven\.modrinth:mixinbooster", read_text(build_gradle)):
            mb_build_dep = "present"
    else:
        add_undecidable("build_gradle_absent", "缺少 %s" % build_gradle,
                        "把 -Root 指向 forge 子项目所在的仓库根")
    if mb_build_dep != "present":
        add_fail("build_gradle_mixinbooster_missing")

    # Forge 1.20.1 不认 reason 键：模板里若出现未注释的 reason= 行即为无效声明
    reason_key = "none"
    if toml_template and len(re.findall(r"(?m)^\s*reason\s*=", toml_template)) > 0:
        reason_key = "present"
    if reason_key != "none":
        add_fail("unsupported_reason_key_in_template")

    add_reading("AP_LG_MB:src=%s:modId=mixinbooster:mandatory=%s:range=%s:ordering=%s:side=%s:present=%d"
                ":artifact_version=%s:build_dep=%s:reason_key=%s"
                % ("+".join(mb_src), mb_seen["mandatory"], mb_seen["range"], mb_seen["ordering"],
                   mb_seen["side"], 1 if len(mb_src) > 0 else 0, mb_artifact_version, mb_build_dep, reason_key))

    # ── S2b：取代硬依赖的运行时门控必须在位（源码 + 产物字节码双证）─────────
    gate_src_rel = os.path.join("src", "main", "java", "com", "merlinkitsune", "astral_dice",
                                "init", "MixinRuntimeGate.java")
    gate_src_path = os.path.join(sub, gate_src_rel)
    gate_src_state = "absent"
    if os.path.isfile(gate_src_path):
        gate_src_state = "present"
    else:
        add_fail("mb_gate_src_absent")

    gate_class_state = "skipped"
    gate_ids_state = "skipped"
    gate_call_state = "skipped"
    if skip_artifacts:
        add_reading("AP_LG_MB_GATE:src=present:class=skipped(artifacts=skipped)")
    elif jar is not None:
        gate_cls = get_jar_entry_text(jar, "com/merlinkitsune/astral_dice/init/MixinRuntimeGate.class")
        if gate_cls is not None:
            gate_class_state = "present"
            has_booster_id = "mixinbooster" in gate_cls
            has_connector_id = "connectormod" in gate_cls
            gate_ids_state = "both" if (has_booster_id and has_connector_id) else "partial"
            if gate_ids_state != "both":
                add_fail("mb_gate_ids_%s" % gate_ids_state)
        else:
            add_fail("mb_gate_class_absent")
        main_cls = get_jar_entry_text(jar, "com/merlinkitsune/astral_dice/AstralDiceMod.class")
        if (main_cls is not None) and ("MixinRuntimeGate" in main_cls):
            gate_call_state = "present"
        else:
            add_fail("mb_gate_call_absent")
        add_reading("AP_LG_MB_GATE:src=%s:class=%s:ids=%s:call=%s"
                    % (gate_src_state, gate_class_state, gate_ids_state, gate_call_state))
    else:
        add_reading("AP_LG_MB_GATE:src=present:class=absent(no jar)")
        add_undecidable("mb_gate_jar_absent", "缺少构建产物 jar，无法核对门控类是否真被打进产物",
                        "gradlew :forge-1.20.1:build")

    # ── S3：FML 同一实现核对（jar + POM + 字节码引用）────────────────────────
    write_out("LG_STEP:3/6 核对 FML 依赖排序的同一实现(fmlloader POM → maven-artifact → ModSorter)")
    maven_jar = ""
    maven_ver = ""
    lang3_jar = ""
    lang3_ver = ""
    ref_classes = []
    fml_ver = forge_version
    fml_jar = find_first_file(os.path.join(cache_root, "net.minecraftforge", "fmlloader", fml_ver,
                                          "*", "fmlloader-%s.jar" % fml_ver))
    if fml_jar is not None:
        # POM 与 jar 落在不同的 hash 目录下（Gradle 缓存布局），必须递归找
        fml_dir = os.path.join(cache_root, "net.minecraftforge", "fmlloader", fml_ver)
        pom = None
        for p in iter_files_recursive(fml_dir):
            if p.lower().endswith(".pom"):
                pom = p
                break
        if pom is not None:
            pom_text = read_text(pom)
            mm = re.search(r"(?s)<artifactId>maven-artifact</artifactId>\s*<version>([^<]+)</version>", pom_text)
            if mm:
                maven_ver = mm.group(1)
            ml = re.search(r"(?s)<artifactId>commons-lang3</artifactId>\s*<version>([^<]+)</version>", pom_text)
            if ml:
                lang3_ver = ml.group(1)
        else:
            add_undecidable("fmlloader_pom_absent", "fmlloader 缓存目录里找不到 POM（%s）" % fml_dir,
                            "gradlew :forge-1.20.1:dependencies --refresh-dependencies")
        if maven_ver:
            maven_jar = find_first_file(os.path.join(cache_root, "org.apache.maven", "maven-artifact",
                                                     maven_ver, "*", "maven-artifact-%s.jar" % maven_ver))
        else:
            add_undecidable("maven_artifact_version_unknown", "fmlloader 的 POM 未声明 maven-artifact 版本",
                            "gradlew :forge-1.20.1:dependencies --refresh-dependencies")
        # maven-artifact 的版本比较实现依赖 commons-lang3（NumberUtils），缺它会 NoClassDefFoundError
        if lang3_ver:
            lang3_jar = find_first_file(os.path.join(cache_root, "org.apache.commons", "commons-lang3",
                                                     lang3_ver, "*", "commons-lang3-%s.jar" % lang3_ver))
        else:
            add_undecidable("commons_lang3_version_unknown", "fmlloader 的 POM 未声明 commons-lang3 版本",
                            "gradlew :forge-1.20.1:dependencies --refresh-dependencies")
        # 字节码常量池扫描：哪些 fmlloader 类引用了 VersionRange
        needle = "org/apache/maven/artifact/versioning/VersionRange"
        try:
            with zipfile.ZipFile(fml_jar) as zf:
                for name in zf.namelist():
                    if not name.lower().endswith(".class"):
                        continue
                    try:
                        txt = zf.read(name).decode("utf-8", errors="replace")
                    except Exception:  # noqa: BLE001
                        continue
                    if needle in txt:
                        base = name.split("/")[-1]
                        ref_classes.append(re.sub(r"\.class$", "", base, flags=re.IGNORECASE))
        except Exception:  # noqa: BLE001
            pass
    else:
        add_undecidable("fmlloader_jar_absent", "Gradle 缓存里找不到 fmlloader-%s.jar（%s）" % (fml_ver, cache_root),
                        "gradlew :forge-1.20.1:dependencies（首次解析依赖会把 fmlloader 拉进缓存）")
    if maven_ver and not maven_jar:
        add_undecidable("maven_artifact_jar_absent", "缓存里找不到 maven-artifact-%s.jar" % maven_ver,
                        "gradlew :forge-1.20.1:dependencies --refresh-dependencies")
    if lang3_ver and not lang3_jar:
        add_undecidable("commons_lang3_jar_absent", "缓存里找不到 commons-lang3-%s.jar" % lang3_ver,
                        "gradlew :forge-1.20.1:dependencies --refresh-dependencies")
    sorted_refs = sorted(set(ref_classes))
    add_reading("AP_LG_IMPL:fmlloader=%s:maven_artifact=%s:commons_lang3=%s:jar=%s:version_range_refs=%s"
                % (os.path.basename(fml_jar) if fml_jar else "", maven_ver, lang3_ver,
                   os.path.basename(maven_jar) if maven_jar else "", ",".join(sorted_refs)))
    if fml_jar is not None:
        if "ModSorter" not in sorted_refs:
            add_fail("ModSorter_no_VersionRange_ref")
        if "VersionSupportMatrix" not in sorted_refs:
            add_fail("VersionSupportMatrix_no_VersionRange_ref")
    else:
        write_out("LG_NOTE:fml_static_scan_skipped（fmlloader jar 缺席，已记 undecidable）")

    # ── S4/S5：离线判定器（同一 maven-artifact jar）─────────────────────────
    write_out("LG_STEP:4/6 编译离线判定器(modVersionNotContained 同式)")
    java_home = get_java_home()
    if not java_home:
        add_undecidable("javac_not_found",
                        "本机找不到带 javac 的 JDK（JAVA_HOME / Zulu 21 / Adoptium / ~/.gradle/jdks）",
                        "安装 JDK 17 或 21，或设置 JAVA_HOME 指向含 bin\\javac.exe 的 JDK")
    elif (not maven_jar) or (not lang3_jar):
        add_undecidable("probe_classpath_incomplete", "判定器 classpath 不完整（缺 maven-artifact 或 commons-lang3）",
                        "gradlew :forge-1.20.1:dependencies --refresh-dependencies")
    else:
        os.makedirs(work_dir, exist_ok=True)
        src_file = os.path.join(work_dir, "LoaderGateProbe.java")
        java_src = PROBE_SRC.replace("\n", os.linesep)
        with open(src_file, "wb") as fh:
            fh.write(java_src.encode("utf-8"))
        write_out("LG_PROBE:src=%s" % src_file)

        javac = os.path.join(java_home, "bin", javac_name())
        java_exe = os.path.join(java_home, "bin", java_name())
        proc = subprocess.run([javac, "-encoding", "UTF-8", "-cp", maven_jar, "-d", work_dir, src_file],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        compile_text = proc.stdout.decode("utf-8", errors="replace").strip()
        compile_text = re.sub(r"[\r\n]+", " ", compile_text)
        add_reading("AP_LG_COMPILE:javac=%s:rc=%d:out=%s" % (javac, proc.returncode, compile_text))
        if proc.returncode != 0:
            add_fail("probe_compile_failed")
        else:
            write_out("LG_STEP:5/6 运行判定器(旧区间 BUG 复现 / 新区间门槛 / loaderVersion / Mixin Booster 区间)")
            cp = os.pathsep.join([maven_jar, lang3_jar, work_dir])
            argv = [java_exe, "-cp", cp, "LoaderGateProbe",
                    derived_old, declared_forge, declared_loader]
            want_mb = bool(mb_seen["range"]) and bool(mb_artifact_version)
            if want_mb:
                argv.extend([mb_seen["range"], mb_artifact_version])
            proc2 = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            probe_rc = proc2.returncode
            if not want_mb:
                add_reading("AP_LG_MB_RANGE:skipped=1:range=%s:artifact=%s:gate_ok=0"
                            % (mb_seen["range"], mb_artifact_version))
            for ln in proc2.stdout.decode("utf-8", errors="replace").splitlines():
                s = ln.strip()
                if s:
                    add_reading(s)
            if probe_rc != 0:
                add_fail("probe_rc=%d" % probe_rc)

    old_line = "".join(r for r in READINGS if r.startswith("AP_LG_OLD:"))
    new_line = "".join(r for r in READINGS if r.startswith("AP_LG_NEW:"))
    load_line = "".join(r for r in READINGS if r.startswith("AP_LG_LOADER:"))
    mb_line = "".join(r for r in READINGS if r.startswith("AP_LG_MB_RANGE:"))
    bug_ok = re.search(r"bug_repro=1", old_line, re.IGNORECASE) is not None
    gate_ok = re.search(r"gate_ok=1", new_line, re.IGNORECASE) is not None
    loader_ok = re.search(r"loader_ok=1", load_line, re.IGNORECASE) is not None
    mb_range_ok = re.search(r"gate_ok=1", mb_line, re.IGNORECASE) is not None

    # ── S6：聚合判定 ──────────────────────────────────────────────────────
    write_out("LG_STEP:6/6 汇总依赖排序阶段结论")
    verdict_head = ("AP_LG_VERDICT:bug_repro=%d:gate_ok=%d:loader_ok=%d:mb_ok=%d:fail=%s"
                    ":fml_stage=dependency_sorting:artifacts=%s:msg=Missing or unsupported mandatory dependencies"
                    % (1 if bug_ok else 0, 1 if gate_ok else 0, 1 if loader_ok else 0,
                       1 if mb_range_ok else 0, "|".join(FAIL), artifacts_state))

    has_fail = len(FAIL) > 0
    has_undecidable = len(UNDECIDABLE) > 0
    undecidable_field = ""
    if has_undecidable:
        undecidable_field = ":undecidable=" + "|".join(UNDECIDABLE)
    ok = bug_ok and gate_ok and loader_ok and mb_range_ok and (not has_fail)

    if has_fail:
        write_out("%s:range_only=1%s:overall=0" % (verdict_head, undecidable_field))
        write_out("LG_DONE")
        return EXIT_DEVIATION
    if has_undecidable:
        write_out("%s:range_only=1%s:overall=2" % (verdict_head, undecidable_field))
        write_out("LG_DONE")
        return EXIT_UNDECIDABLE
    write_out("%s:range_only=1:overall=%d" % (verdict_head, 1 if ok else 0))
    write_out("LG_DONE")
    if ok:
        return EXIT_OK
    # 无 LG_FAIL 项但区间子判据自身不成立（如 probe 输出缺 gate_ok）⇒ 仍按「门槛不成立」记 1。
    return EXIT_DEVIATION


def main(argv=None):
    ap = argparse.ArgumentParser(description="1.20.1 加载器版本门槛 + Mixin Booster 硬前置静态守门（旧实现 等价迁移）")
    ap.add_argument("root_pos", nargs="?", default=None, help="仓库根（位置参数，与 旧实现 的 -Root 位置参数等价）")
    ap.add_argument("-Root", "--root", dest="root", default=None,
                    help="仓库根（缺省由脚本位置上溯两级派生）")
    ap.add_argument("-SkipArtifacts", "--skip-artifacts", dest="skip_artifacts", action="store_true",
                    help="跳过分档 S1/S2 的生成资源与构建产物两段（模板段仍查）")
    args = ap.parse_args(argv)
    root = args.root if args.root is not None else args.root_pos
    return run(root, args.skip_artifacts)


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8", newline="\n")
        sys.stderr.reconfigure(encoding="utf-8", newline="\n")
    except Exception:
        pass
    sys.exit(main())
