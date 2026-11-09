#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 AutoJS 实机测试库两个产物按 Maven 布局发布到 mavenLocal（jar + 手写最小 pom.xml）。

本脚本是旧编排器发布脚本的等价 Python 实现
（该资产已随本分支的 bash/python 收敛而废弃删除）。

用法:
    python scripts/test/tools/publish_autojs_maven.py [--force] [--m2-repo <dir>] [--root <dir>]

发布坐标（Gradle 侧由 -PautojsTestMod=1 条件启用）:
    cc.sighs.autojs:AutoJS-forge-1.20.1:1.0.0
    cc.sighs.autojs:AutoJS-fabric-1.20.1:1.0.0
    cc.sighs.autojs:AutoJS-fabric-1.21.1:1.0.0

目标目录:
    <m2>/cc/sighs/autojs/<artifactId>/1.0.0/<artifactId>-1.0.0.{jar,pom}

源 jar（优先用仓库内 vendor 副本；缺失时从构建产物复制，绝不重建）:
    scripts/test/resources/mods/autojs/AutoJS-<loader>-1.20.1-1.0.0.jar
    <- temp/AutoJS-src/targets/<loader>-1.20.1/build/libs/AutoJS-<loader>-1.20.1-1.0.0.jar

幂等判据:
  1) vendor 副本已存在且 SHA256 == 构建产物 => 不复制（不触碰时间戳、不覆盖）；
     两处 SHA256 不一致 => FAIL（不静默覆盖别人的产物；确认以构建产物为准时加 --force）。
  2) 目标 jar SHA256 == 源 jar 且 pom 文本逐字节相同 => SKIP（不重写）；
     同一内容重复运行 => 第二次必须全部 SKIP。

输出（显式 LF，UTF-8）：
    MT_AUTOJS_MAVEN: OK   — published <coord> jar=… sha256=… pom=…
    MT_AUTOJS_MAVEN: SKIP — <coord> already current (sha256=…)
    MT_AUTOJS_MAVEN: FAIL — <coord> <原因>
    MT_AUTOJS_MAVEN: PROVENANCE — APPENDED|CREATED|SKIP …
    MT_AUTOJS_MAVEN_RESULT: OK|FAIL published=<n> skipped=<n> failed=<n>

退出码：0 = 全部 OK/SKIP；1 = 有 FAIL。
"""
import argparse
import hashlib
import os
import shutil
import sys

GROUP = "cc.sighs.autojs"
VERSION = "1.0.0"
UPSTREAM = "https://github.com/MerrySnow404/AutoJS"
VENDOR_REL = "scripts/test/resources/mods/autojs"
PROVENANCE_REL = "scripts/test/resources/mods/autojs/PROVENANCE.md"
MARKER = "### AutoJS Maven 分发记录（publish_autojs_maven.py）"

ARTIFACTS = [
    {
        "id": "AutoJS-forge-1.20.1",
        "loader": "forge",
        "jar": "AutoJS-forge-1.20.1-1.0.0.jar",
        "build": ["temp/AutoJS-src/targets/forge-1.20.1/build/libs/AutoJS-forge-1.20.1-1.0.0.jar",
                  "../AutoJS/targets/forge-1.20.1/build/libs/AutoJS-forge-1.20.1-1.0.0.jar"],
        "mc": "1.20.1",
        "ns": "srg",
        "remap": "ModDevGradle legacyforge: modRuntimeOnly 解析期 SRG->named",
    },
    {
        "id": "AutoJS-fabric-1.20.1",
        "loader": "fabric",
        "jar": "AutoJS-fabric-1.20.1-1.0.0.jar",
        "build": ["temp/AutoJS-src/targets/fabric-1.20.1/build/libs/AutoJS-fabric-1.20.1-1.0.0.jar",
                  "../AutoJS/targets/fabric-1.20.1/build/libs/AutoJS-fabric-1.20.1-1.0.0.jar"],
        "mc": "1.20.1",
        "ns": "intermediary",
        "remap": "Fabric Loom: modRuntimeOnly 解析期 intermediary->named",
    },
    {
        "id": "AutoJS-fabric-1.21.1",
        "loader": "fabric",
        "jar": "AutoJS-fabric-1.21.1-1.0.0.jar",
        "build": ["../AutoJS/targets/fabric-1.21.1/build/libs/AutoJS-fabric-1.21.1-1.0.0.jar",
                  "temp/AutoJS-1211-probe/targets/fabric-1.21.1/build/libs/AutoJS-fabric-1.21.1-1.0.0.jar"],
        "mc": "1.21.1",
        "ns": "intermediary",
        "remap": "Fabric Loom: modRuntimeOnly 解析期 intermediary->named（依赖 rhino 2101 多加载器单 jar）",
    },
]

POM_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<!--
  AutoJS 实机测试库 —— 本地 mavenLocal 分发件（不是 Curse/Modrinth 发布件）。
  由 scripts/test/tools/publish_autojs_maven.py 生成；仅供 astral_dice 的 dev 运行期使用
  （forge-1.20.1 / fabric-1.20.1 的 build.gradle 内 -PautojsTestMod=1 条件 modRuntimeOnly 依赖）。
  上游项目: https://github.com/MerrySnow404/AutoJS
  许可证: GPL-3.0
-->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>cc.sighs.autojs</groupId>
  <artifactId>@ID@</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>

  <name>AutoJS (@LOADER@-@MC@)</name>
  <description>AutoJS 实机测试库（@LOADER@ / Minecraft @MC@）。本工件由 scripts/test/tools/publish_autojs_maven.py 从本地构建产物发布到 mavenLocal，不是 Curse/Modrinth 发布件。</description>
  <url>@UPSTREAM@</url>

  <licenses>
    <license>
      <name>GNU General Public License v3.0</name>
      <url>https://www.gnu.org/licenses/gpl-3.0.html</url>
      <distribution>repo</distribution>
      <comments>SPDX: GPL-3.0 —— 与 jar 内 mods.toml / fabric.mod.json 的 license 字段一致。</comments>
    </license>
  </licenses>

  <scm>
    <connection>scm:git:@UPSTREAM@.git</connection>
    <developerConnection>scm:git:@UPSTREAM@.git</developerConnection>
    <url>@UPSTREAM@</url>
  </scm>

  <properties>
    <autojs.distribution>local-maven-only</autojs.distribution>
    <autojs.release-channel>not-curse-or-modrinth</autojs.release-channel>
    <autojs.minecraft.version>@MC@</autojs.minecraft.version>
    <autojs.loader>@LOADER@</autojs.loader>
    <autojs.bytecode.namespace>@NS@</autojs.bytecode.namespace>
    <autojs.remap-at-resolve>@REMAP@</autojs.remap-at-resolve>
  </properties>
</project>
"""


def out(text=""):
    sys.stdout.write(text + "\n")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def make_pom(a):
    return (
        POM_TEMPLATE
        .replace("@ID@", a["id"])
        .replace("@LOADER@", a["loader"])
        .replace("@MC@", a["mc"])
        .replace("@NS@", a["ns"])
        .replace("@REMAP@", a["remap"])
        .replace("@UPSTREAM@", UPSTREAM)
    )


def add_provenance_block(root, block, marker):
    path = os.path.join(root, PROVENANCE_REL)
    d = os.path.dirname(path)
    if d and not os.path.isdir(d):
        os.makedirs(d, exist_ok=True)
    if os.path.isfile(path):
        with open(path, "r", encoding="utf-8", newline="") as f:
            existing = f.read()
        if marker in existing:
            return "SKIP (already recorded)"
        nl = "\r\n" if "\r\n" in existing else "\n"
        body = block.replace("\r\n", "\n")
        if nl != "\n":
            body = body.replace("\n", nl)
        prefix = ""
        if existing and not existing.endswith("\n"):
            prefix = nl
        with open(path, "a", encoding="utf-8", newline="") as f:
            f.write(prefix + nl + body)
        return "APPENDED"
    body = block.replace("\r\n", "\n").replace("\n", "\r\n")
    header = ("# AutoJS 测试库来源记录（PROVENANCE）" + "\r\n" + "\r\n"
              + "本文件记录测试库类 jar 的来源与本地分发坐标；各投放步骤只**追加**自己的记录，不覆盖他人内容。" + "\r\n" + "\r\n")
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(header + body)
    return "CREATED"


def main():
    ap = argparse.ArgumentParser(add_help=True, description="AutoJS -> mavenLocal 发布")
    ap.add_argument("-Force", "--force", "-f", action="store_true", dest="force")
    ap.add_argument("-M2Repo", "--m2-repo", dest="m2_repo",
                    default=os.path.join(os.path.expanduser("~"), ".m2", "repository"))
    ap.add_argument("-Root", "--root", dest="root", default=None)
    # 兼容单破折号长选项（-Root / -M2Repo 已覆盖）
    a = ap.parse_args()

    root = a.root or os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
    m2 = a.m2_repo

    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace", newline="\n")
    except Exception:
        pass

    out("AutoJS -> mavenLocal 发布（python scripts/test/tools/publish_autojs_maven.py）")
    out("ROOT: " + root)
    out("M2  : " + m2)
    out()

    published = skipped = failed = 0
    records = []

    for art in ARTIFACTS:
        coord = GROUP + ":" + art["id"] + ":" + VERSION
        vendor_path = os.path.join(root, VENDOR_REL, art["jar"])
        candidates = art["build"] if isinstance(art["build"], list) else [art["build"]]
        build_path = None
        for rel in candidates:
            cand = rel if os.path.isabs(rel) else os.path.abspath(os.path.join(root, rel))
            if os.path.isfile(cand):
                build_path = cand
                break
        if build_path is None:
            first = candidates[0]
            build_path = first if os.path.isabs(first) else os.path.abspath(os.path.join(root, first))
        target_dir = os.path.join(m2, "cc", "sighs", "autojs", art["id"], VERSION)
        target_jar = os.path.join(target_dir, art["id"] + "-" + VERSION + ".jar")
        target_pom = os.path.join(target_dir, art["id"] + "-" + VERSION + ".pom")

        # 1) vendor 副本落地
        try:
            if os.path.isfile(vendor_path):
                vendor_hash = sha256(vendor_path)
                if os.path.isfile(build_path):
                    build_hash = sha256(build_path)
                    if vendor_hash != build_hash:
                        if a.force:
                            shutil.copyfile(build_path, vendor_path)
                            if sha256(vendor_path) != build_hash:
                                raise RuntimeError("重复制后 SHA256 不一致：" + vendor_path)
                            vendor_state = "RECOPIED(-Force)"
                        else:
                            raise RuntimeError(
                                "vendor 副本与构建产物 SHA256 不一致（vendor=" + vendor_hash
                                + " build=" + build_hash + "）；确认以构建产物为准请加 -Force")
                    else:
                        vendor_state = "KEPT(identical)"
                else:
                    vendor_state = "KEPT(build output absent)"
            else:
                if not os.path.isfile(build_path):
                    raise RuntimeError(
                        "缺少源 jar：vendor 副本与构建产物都不存在（"
                        + VENDOR_REL + "/" + art["jar"] + " / " + " | ".join(candidates) + "）")
                vdir = os.path.dirname(vendor_path)
                if vdir and not os.path.isdir(vdir):
                    os.makedirs(vdir, exist_ok=True)
                shutil.copyfile(build_path, vendor_path)
                if sha256(vendor_path) != sha256(build_path):
                    raise RuntimeError("复制后 SHA256 不一致：" + vendor_path)
                vendor_state = "COPIED"
        except Exception as e:
            failed += 1
            out("MT_AUTOJS_MAVEN: FAIL — " + coord + " vendor: " + str(e))
            continue

        # 2) 发布到 mavenLocal
        try:
            src_hash = sha256(vendor_path)
            pom = make_pom(art)
            jar_ok = os.path.isfile(target_jar) and sha256(target_jar) == src_hash
            pom_ok = False
            if os.path.isfile(target_pom):
                with open(target_pom, "r", encoding="utf-8", newline="") as f:
                    pom_ok = f.read() == pom
            if jar_ok and pom_ok:
                skipped += 1
                out("MT_AUTOJS_MAVEN: SKIP — " + coord + " already current (sha256="
                    + src_hash[:16] + "…) dir=" + target_dir + " vendor=" + vendor_state + ")")
            else:
                if not os.path.isdir(target_dir):
                    os.makedirs(target_dir, exist_ok=True)
                shutil.copyfile(vendor_path, target_jar)
                with open(target_pom, "w", encoding="utf-8", newline="") as f:
                    f.write(pom)
                if sha256(target_jar) != src_hash:
                    raise RuntimeError("发布后 jar SHA256 不一致：" + target_jar)
                published += 1
                out("MT_AUTOJS_MAVEN: OK — published " + coord + " jar=" + target_jar
                    + " sha256=" + src_hash + " pom=" + target_pom + " vendor=" + vendor_state)
            records.append({
                "coord": coord,
                "vendor": VENDOR_REL + "/" + art["jar"],
                "build": (os.path.relpath(build_path, root).replace(os.sep, "/")
                          if os.path.abspath(build_path).startswith(os.path.abspath(root))
                          else build_path),
                "hash": src_hash,
                "remap": art["remap"],
            })
        except Exception as e:
            failed += 1
            out("MT_AUTOJS_MAVEN: FAIL — " + coord + " " + str(e))

    # 3) PROVENANCE.md
    if records:
        rows = [MARKER, "",
                "由 scripts/test/tools/publish_autojs_maven.py 写入（本地 mavenLocal 分发；**不是** Curse/Modrinth 发布件）。",
                ""]
        for r in records:
            rows.append("- `" + r["coord"] + "`")
            rows.append("  - jar: `" + r["vendor"] + "`（SHA256 `" + r["hash"] + "`）")
            rows.append("  - 构建来源: `" + r["build"] + "`")
            rows.append("  - 装载方式: " + r["remap"])
        rows.append("")
        block = "\n".join(rows) + "\n"
        try:
            pv = add_provenance_block(root, block, MARKER)
            out()
            out("MT_AUTOJS_MAVEN: PROVENANCE — " + pv + " file=" + PROVENANCE_REL)
        except Exception as e:
            out()
            out("MT_AUTOJS_MAVEN: PROVENANCE — FAIL " + str(e))

    out()
    verdict = "OK" if failed == 0 else "FAIL"
    out("MT_AUTOJS_MAVEN_RESULT: " + verdict + " published=" + str(published)
        + " skipped=" + str(skipped) + " failed=" + str(failed))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
