# Multiloader 单仓结构 / Multiloader Repository Layout

> 2026-08-29 由原 `astra_dice` 仓库(分支承载版本)迁移而来。原仓库两个分支的**全部内容**
> (含 git 排除的 AGENTS.md/docs/temp/run 等)已完整迁入本仓库;原仓库保留未动。

## 结构总览

```
astral_dice_multiloader/
├── settings.gradle / build.gradle / gradle.properties   # 根:聚合入口(不编译)
├── gradle/wrapper + gradlew(.bat)                        # Gradle 9.2.1(阿里云镜像)
├── tools/check_lang_sync.ps1                              # lang 同步检查(对任一子项目 lang 目录运行)
├── docs/ deploy.ps1 scripts/ temp/         # 从原仓库迁移的配套内容
├── run/1.21.1/                                        # 1.21.1 开发测试环境(仓库根统一 run/,git 排除)
├── run/1.20.1/                                        # 1.20.1 开发测试环境(仓库根统一 run/,git 排除)
├── neoforge-1.21.1/   # 1.21.1 NeoForge 子项目(主线)
│   ├── build.gradle / gradle.properties                  # 沿用原 1.21.1-main 分支构建脚本(路径不变)
│   ├── src/                                              # 原 1.21.1-main 分支 src 全量(main + generated)
│   └── base-mod-compile-libs/ .oss-basemod-repo/         # Iron's 本地编译 jar / 本地 maven(1.21.1 专用)
└── forge-1.20.1/      # 1.20.1 Forge 子项目(移植线)
    ├── build.gradle / gradle.properties                  # 沿用原 1.20.1-forge 分支构建脚本(路径不变)
    └── src/                                              # 原 1.20.1-forge 分支 src 全量(main + generated)
```

## 与模板(Player005/multiloader-mod-template)的差异

模板面向"**同一 MC 版本、多加载器**"(common 共享源码 + fabric/neoforge 薄适配层)。本项目两版本
跨 MC 大版本(1.21.1 ↔ 1.20.1),API 差异大(数据组件/附件、事件、Curios 注册等,见
`compat-1.20.1-forge.md`),做 common 共享层需整体重构、风险不可控。因此保留模板的**骨架约定**
(根 settings/子项目包含/统一 wrapper/根 .gitignore/聚合 build),但:

- **不设 common 源码子项目**;每个"MC 版本+加载器"是自包含子项目;
- **不引入 fabric 子项目**(无 Fabric 移植计划);
- 子项目 build.gradle/gradle.properties **原样沿用各自分支的 Groovy 脚本**(ModDevGradle 2.0.x),
  不改写为模板的 Kotlin DSL,保证构建行为与原分支完全一致;
- 产物名保持 `astral_dice-<版本>.jar`(不用模板的 `<mod>-<loader>-<mc>` 命名),使原部署清理规则
  (`astral_dice-*+1.20.1.jar` 后缀匹配)继续有效。

## 常用命令(仓库根目录)

| 命令 | 作用 |
|---|---|
| `./gradlew build` | 构建全部子项目(两个版本) |
| `./gradlew :neoforge-1.21.1:build` | 仅构建 1.21.1 NeoForge |
| `./gradlew :forge-1.20.1:build` | 仅构建 1.20.1 Forge |
| `./gradlew :neoforge-1.21.1:runData` | 1.21.1 数据生成 |
| `python tools/check_lang_sync.ps1 --lang-dir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang` | lang 同步检查(forge 同理) |

## 迁移映射(内容来源)

| 本仓库路径 | 原仓库来源 |
|---|---|
| `neoforge-1.21.1/src` | `1.21.1-main` 分支 `src/` |
| `neoforge-1.21.1/build.gradle`、`gradle.properties` | `1.21.1-main` 分支同名文件(未改动) |
| `neoforge-1.21.1/base-mod-compile-libs/`、`.oss-basemod-repo/` | 原仓库同名目录(1.21.1 构建依赖) |
| `run/1.21.1/` | 原仓库 `run/1.21.1/`(git 排除内容;自 2026-09 起统一存放于仓库根 run/) |
| `forge-1.20.1/src` | `1.20.1-forge` 分支 `src/` |
| `forge-1.20.1/build.gradle`、`gradle.properties` | `1.20.1-forge` 分支同名文件(未改动) |
| `run/1.20.1/` | 原仓库 `run/1.20.1/`(git 排除内容;自 2026-09 起统一存放于仓库根 run/) |
| `tools/check_lang_sync.ps1` | `1.20.1-forge` 分支 `tools/check_lang_sync.ps1` |
| `docs/`、`scripts/`、`temp/`、`.zcode/`、`AGENTS.md`、`deploy.ps1`、`lib/` | 原仓库同名内容(git 排除内容);`AGENTS.md`/`deploy.ps1`/`docs/`/CI 已做 multiloader 适配 |
| `CHANGELOG_ZH.md`(中文)、`CHANGELOG.md`(英文)、`README.md`、`LICENSE`、`.gitattributes` | `1.21.1-main` 分支(README/CHANGELOG 已做 multiloader 适配;CHANGELOG 已按语言拆分为两文件,条目一一对应) |

**未迁移**(可再生的 IDE/构建产物):`.gradle/`、`build/`、`.idea/`、`.eclipse/`、`.vscode/`、`bin/`。

## git 跟踪策略

`.gitignore` 沿用原仓库的「项目级排除(用户要求)」:AGENTS.md、docs/、temp/、scripts/、run/、
deploy.ps1、.zcode/ 等仍在 git 排除之列(内容已迁移到本地,只是不入库)。如需入库,
删除 `.gitignore` 中「项目级排除」段落即可。

## 注意事项

- 两个版本的 `run/` 目录统一存放在**仓库根 `run/`** 下(`run/1.21.1` 与 `run/1.20.1`),按版本隔离且互不污染
  (mods/config/saves 独立);两子项目 build.gradle 的 `gameDirectory`/`pushToDevRun` 均指向
  `rootProject.file('run/<版本>')`。
- `gradle.properties` 的版本号**各子项目独立**;发布流程见 AGENTS.md「新版本发布流程」。
- 功能/修复默认**同步修改两个版本**(`neoforge-1.21.1` + `forge-1.20.1`);每次改动完成后自动本地提交、并随 `gradlew build` 自动部署到整合包,但**不自动 `git push`**(见 AGENTS.md「子项目修改默认规则」)。
- 两版本**均有帕秋莉手册**(1.20.1 已于 1.1.3 移植,含 Patchouli 1.20.1-85-forge 依赖),手册结构两版本一致。
- 更新日志按语言拆分为 `CHANGELOG_ZH.md`(中文)与 `CHANGELOG.md`(英文),两文件按版本号一一对应、条目数一致(见 AGENTS.md「更新日志约定」)。
