# -*- coding: utf-8 -*-
"""把 mod 依赖内嵌的**纯库** JarJar 解到 dev 运行目录。

## 为什么需要这一步

Fabric Loom 在**开发环境**把 mod 依赖放在 classpath 上，但**不会**处理它们
`META-INF/jars/*.jar` 里内嵌的库（Fabric Loader 的 JarJar 只对 `mods/` 目录下的 jar 生效）。
生产环境由 Loader 正常展开，所以这是**纯 dev 缺口**。

实测（2026-09-29，Fabric 1.20.1 线）：
  * `accessories` 内嵌 `io.wispforest:endec 0.1.8` / `gson 0.1.5` / `netty 0.1.4`
    ⇒ 缺失时 `AccessoriesFabric.<clinit>` 抛
      `NoClassDefFoundError: io/wispforest/endec/util/MapCarrier`
  * `patchouli` 内嵌 `fiber-0.23.0-2`
    ⇒ 缺失时 `vazkii.patchouli.fabric.common.FabricModInitializer.onInitialize` 抛
      `ClassNotFoundException: io.github.fablabsmc...ConfigType`

两者都在 entrypoint 初始化阶段硬崩，服务端/客户端根本起不来。

## ⚠️ 为什么只处理白名单里的 mod，而不是"全部内嵌 jar"

从**生产 jar** 里解出来的内嵌 jar 是 **intermediary** 命名的。对**纯 Java 库**
（endec / fiber / gson / netty —— 不引用任何 Minecraft 类）这没有问题；
但对内嵌的**MC mod**（例如 Trinkets 内嵌的 `cardinal-components-*`、
Sodium 内嵌的 `fabric-api-*` 模块）就是灾难：
dev 环境用的是 named（Mojang 映射）类名，intermediary 版根本加载不了，
而且还会与 classpath 上已有的同名 mod 撞成重复条目。
⇒ 这类依赖必须在 build.gradle 里用 `modRuntimeOnly` 显式声明（Loom 会 remap 成 named）。

## 用法

    python scripts/devtools/unpack_nested_mod_jars.py            # 解包
    python scripts/devtools/unpack_nested_mod_jars.py --check    # 只报告
    python scripts/devtools/unpack_nested_mod_jars.py --clean    # 先清空目标目录再解包

依赖清单**自动**从 `fabric-1.20.1/build.gradle` 的 `maven.modrinth:<slug>:<version|${prop}>`
行解析（${prop} 会去 gradle.properties 展开），改依赖后无需同步本脚本。
解出来的 jar 落在 `run/<server|client>/mods/`，该目录已在 .gitignore 中。
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import sys
import zipfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BUILD_GRADLE = os.path.join(REPO_ROOT, 'fabric-1.20.1', 'build.gradle')
PROPS_FILE = os.path.join(REPO_ROOT, 'fabric-1.20.1', 'gradle.properties')
GRADLE_MOD_CACHE = os.path.join(
    os.path.expanduser('~'), '.gradle', 'caches', 'modules-2', 'files-2.1', 'maven.modrinth')
TARGET_DIRS = [
    # ⚠️ Loom 的 `runDir 'run/server'` 是相对**子项目目录**解析的
    #    (实测 2026-09-29:跑到一半发现 jar 没被加载,真实目录是 fabric-1.20.1/run/…)。
    #    注意与 build.gradle 的 pushToDevRun 区分:那个是 **rootProject** 下的分发目录
    #    (run/fabric-1.20.1/mods),给整合包/手工测试用,不是 Loom 的 runDir。
    os.path.join(REPO_ROOT, 'fabric-1.20.1', 'run', 'server', 'mods'),
    os.path.join(REPO_ROOT, 'fabric-1.20.1', 'run', 'client', 'mods'),
]
NESTED_PREFIX = 'META-INF/jars/'

# 只处理这几个 mod 的内嵌库（见模块 docstring 的说明）
MOD_WHITELIST = ('accessories', 'patchouli', 'puzzles-lib', 'forge-config-api-port')

MODRINTH_RE = re.compile(r'maven\.modrinth:([A-Za-z0-9._\-]+):([A-Za-z0-9._\-${}]+)')


def load_props() -> dict[str, str]:
    props: dict[str, str] = {}
    if os.path.exists(PROPS_FILE):
        with open(PROPS_FILE, 'r', encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    k, v = line.split('=', 1)
                    props[k.strip()] = v.strip()
    return props


def resolve_mod_jars() -> list[tuple[str, str, str]]:
    """从 build.gradle 解析 (slug, versionId) 并定位 Gradle 缓存里的 jar。"""
    with open(BUILD_GRADLE, 'r', encoding='utf-8') as fh:
        text = fh.read()
    props = load_props()

    resolved: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for slug, version in MODRINTH_RE.findall(text):
        if slug not in MOD_WHITELIST:
            continue
        if version.startswith('${') and version.endswith('}'):
            version = props.get(version[2:-1], '')
        if not version or version.startswith('$'):
            print('!! 无法解析 %s 的版本号，跳过' % slug)
            continue
        if slug in seen:
            continue
        seen.add(slug)
        jars = [p for p in glob.glob(os.path.join(GRADLE_MOD_CACHE, slug, version, '*', '*.jar'))
                if not p.endswith('-sources.jar')]
        if jars:
            resolved.append((slug, version, jars[0]))
        else:
            print('!! 缓存里找不到 %s:%s 的 jar（先跑一次 gradle 构建）' % (slug, version))
    return resolved


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='只报告，不写文件')
    parser.add_argument('--clean', action='store_true', help='先清空目标目录里的 jar 再解包')
    args = parser.parse_args()

    mods = resolve_mod_jars()
    if not mods:
        print('未解析到任何待处理依赖，检查: %s' % BUILD_GRADLE)
        return 1

    if args.clean and not args.check:
        for d in TARGET_DIRS:
            if not os.path.isdir(d):
                continue
            for name in os.listdir(d):
                if name.endswith('.jar'):
                    os.remove(os.path.join(d, name))
                    print('[clean] %s' % os.path.join(d, name))

    if not args.check:
        for d in TARGET_DIRS:
            os.makedirs(d, exist_ok=True)

    total = 0
    for slug, version, jar in sorted(mods):
        with zipfile.ZipFile(jar) as zf:
            nested = [n for n in zf.namelist()
                      if n.startswith(NESTED_PREFIX) and n.endswith('.jar')]
            if not nested:
                print('%-12s %-24s (无内嵌 jar)' % (slug, version))
                continue
            print('%-12s %-24s -> %d 个内嵌 jar' % (slug, version, len(nested)))
            for name in nested:
                data = zf.read(name)
                target_name = name[len(NESTED_PREFIX):]
                for d in TARGET_DIRS:
                    out = os.path.join(d, target_name)
                    if os.path.exists(out) and os.path.getsize(out) == len(data):
                        continue
                    if not args.check:
                        with open(out, 'wb') as fh:
                            fh.write(data)
                    print('    %s %s' % ('[check]' if args.check else '[write]', out))
                    total += 1

    print('完成：处理 %d 个文件。' % total)
    return 0


if __name__ == '__main__':
    sys.exit(main())
