# 1.5.0 正式版发布计划（multi-main 四线）

生成时间：2026-11-02；Lead 会话；依据 work/recon/01..03 取证。

## 0. 已定基线（取自实读取证，非文档快照）
- HEAD=fd990c15，分支 multi-main，领先 origin/multi-main 24 提交、落后 0。
- 四线均能构建，jar 与源码同步；rc.7 jar 字节：n21 2611872 / fg 2660152 / n26 2875210 / fb 3027461。
- 版本号单点源＝各线 gradle.properties 的 mod_version；mod 元数据全用占位符（无写死）。
- 改名影响面＝四线 zh_cn.json 各 1 行（item.astral_dice.abyss_reagent_chip）；tooltip/手册条目走 lang 键与 id，更改值即生效；源码内 30 处命中全为注释。
- CI = .github/workflows/build.yml（动态读版本，rc 消失后 Release 自动变正式版）。
- release/ 无 1.5.0/ 目录 ⇒ 必须新建，否则 Release 正文退化。

## 1. 决策（Lead 裁定，写入交付报告）
1. mod_version 改为 1.5.0+<loader>_<mc>（保留 + 后缀，去掉 -rc.7）：符合本仓「版本自带加载器后缀」硬规则，四线产物名不冲突。
2. 改名同步 en/ja：en_us 的 Reagent - Abyss 改为 Abyss Reagent；ja_jp 的 試薬 - 深淵 改为 深淵試薬（同一改名在各语言的一致表达；物品 id 不动）。
3. Java 注释内的「试剂-深渊」一并更新（保持代码与物品名一致）；docs/CHANGELOG/.superpowers/porting 历史不改。
4. 四线全部发正式版（用户明确点名，覆盖「默认只发 P0」红线）；tag=1.5.0，四线 jar 同放一个 Release。
5. fabric 线的 C-3（SignHookDispatcher 死代码）与 P-5 未裁决项，按「不得侥幸/降级」处理：要么修实现，要么在报告登记为 REFUTED 并给出证据。

## 2. 阶段与责任（写作用域互不重叠）
| 阶段 | 内容 | 写作用域 | 执行者 | 验收判据 |
|---|---|---|---|---|
| P1 | 版本号 + 改名（四线） | 4 gradle.properties、4x3 lang、四线 src/main/java 注释 | 一次性 subagent | grep 0 残留 + 4 处新值 + 编译过 |
| P2 | 四线 build，出 jar | 各线 build/** | 同上（同批） | 4 个 astral_dice-1.5.0+*.jar |
| P3 | 按序实机测试（n21→fg→n26→fb） | run/**、scripts/test/cases/**、work/test/** | tester（串行，唯一实例所有者） | 每线 MT 报告 + 截图 + 玩家数据快照 |
| P4 | 审核→自动修复→复核→补测 | 失败线 src/** | fix 子代理（按线派） | dsh_code_review + 补测通过 |
| P5 | mapping 整理（1.21.1 基准） | docs/porting/MAPPING-1.5.0.md | docs 子代理 | 逐项功能差异 + 证据 |
| P6 | README/CHANGELOG/玩家日志/MC百科 + release/1.5.0/** | README*、CHANGELOG*、release/1.5.0/** | docs 子代理 | 三份玩家侧说明齐全、口径一致 |
| P7 | 本地确认→提交→tag 1.5.0→push→CurseForge | .git、发布配置 | release 子代理 | 远端 tag + CF 8 文件 + Release 附件 |
| P8 | 清理旧 jar/临时资产 | 仓库根 + 各线 build/libs + run/*/mods | release 子代理 | 旧 jar 0 残留、临时文件移出 |

## 3. 纪律
- 每阶段结束必须留证据（命令原文 + 文件名 + 大小/哈希）。
- 实机：每轮结束 mt.ps1 --version <线> --phase stop --force --purge-saves；不得让客户端无人观察驻留。
- 测试靶子必须非亡灵（pillager/cow），禁僵尸。
- 复核一律 dsh_code_review；功能审查另派 subagent。
- 所有 git 远端操作经 http://127.0.0.1:7897。