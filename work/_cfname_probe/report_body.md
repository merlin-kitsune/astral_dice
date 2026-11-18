# CF 上传：站点显示名自动推导 + changelog 附带策略（2026-11-02）

- 仓库：F:/MCProject/astral_dice_multiloader
- 目标文件：tools/curseforge_upload.py（纯标准库；改后 1,579 行 / 78,077 B / 全 LF）
- 实际改动文件（共 2 个）：tools/curseforge_upload.py（保持 LF）、docs/agent/AGENTS-dsh.md（保持 CRLF）
- 依据：用户 2026-11-02 规则 —— ① CF 上传件显示名固定 ⟦Astral Dice <版本> (<加载器> <MC>)⟧；② 小版本更新不在 CF 写 changelog
- 纪律：全程 dry-run；未上传、未 --apply、未 commit / push / tag、未碰 Release、未改 CI 与 tools/curseforge.json

## 0. 结论速览

| 维度 | 改前 | 改后 |
|---|---|---|
| 站点显示名 | jar 文件名 ⟦astral_dice-1.5.0+fabric_1.20.1.jar⟧ | 自动推导 ⟦Astral Dice 1.5.0 (Fabric 1.20.1)⟧ |
| 加载器写法 | 跟 jar 名走小写 | 规范为 Fabric / Forge / NeoForge；未知值首字母大写（⟦quilt⟧ → ⟦Quilt⟧） |
| changelog 默认 | ⟦release/<版本>/PLAYER_CHANGELOG*.md⟧ 存在就带（任何版本） | 只有主线版本 ⟦X.Y.0⟧ 自动带；小版本（补丁 / hotfix / 预发布）不写 |
| changelogType | 无条件写进 metadata | 只在真附带 changelog 时写；小版本连同 ⟦changelog⟧ 两个字段都不发 |
| 人工覆盖 | ⟦--display-name⟧ / ⟦--changelog⟧ | 均保留；新增 ⟦--no-changelog⟧（与 ⟦--changelog⟧ 互斥） |
| dry-run 可核对 | 只有「更新日志 = …」一行 | 额外两行 ⟦DISPLAY:⟧ / ⟦CHANGELOG:⟧ |
| 规则文档 | — | docs/agent/AGENTS-dsh.md:167 新增 1 条 |

## 1. 改动点清单（文件:行号）

### 1.1 tools/curseforge_upload.py（LF；+150 −15）

| 行号 | 改动 |
|---|---|
| :99-129 | 模块 docstring：写入显示名 5 个样例 + 「changelog 只对主线版本默认附带」+ dry-run 两行样例；删掉旧的「--display-name 显式覆盖 / 发布说明默认取 release/…」两段 |
| :213-216 | 新增 ⟦LOADER_DISPLAY_LABELS⟧（fabric/forge/neoforge → 规范大小写）与 ⟦MAINLINE_VERSION_RE = ^\d+\.\d+\.0$⟧ |
| :600-607 | 新增 ⟦display_path(p)⟧：输出路径统一正斜杠（旧代码直接打 ⟦rel_display()⟧，Windows 下会出现反斜杠） |
| :684-694 | 新增 ⟦loader_display_label(loader)⟧ |
| :697-710 | 新增 ⟦default_display_name(ver, loader, mc)⟧（docstring 里就是 5 条验收样例） |
| :713-720 | 新增 ⟦is_mainline_version(ver)⟧ |
| :723-748 | 新增 ⟦decide_changelog(ver, explicit, disabled)⟧ → ⟦(Path 或 None, 说明行)⟧ |
| :1057-1089 | ⟦build_jobs⟧：算出 ⟦display⟧（--display-name → 库产物 → 自动推导三段判定）与 ⟦changelog/changelog_line⟧；job dict 新增 ⟦display⟧ / ⟦_changelog_line⟧ |
| :1092-1104 | ⟦print_jobs_plan⟧：每条 job 多打印 ⟦DISPLAY: …⟧ 与 CHANGELOG 决策行；「更新日志」行改用 ⟦display_path⟧ |
| :1107-1119 | ⟦upload_metadata⟧：⟦displayName⟧ 回落 ⟦job.get("display")⟧；⟦changelogType⟧ 与 ⟦changelog⟧ 一起移进 ⟦if job["_changelog"]⟧ |
| :1286-1296 | push 子命令 parser：新增 ⟦--no-changelog⟧，并改 ⟦--display-name⟧ / ⟦--changelog⟧ 的 help 文案 |
| :1411-1421 | legacy（裸 --jar）parser：同样新增 ⟦--no-changelog⟧ 与 help 文案 |

未改动的关键点（刻意保留原语义）：真写唯一判定点 ⟦write_enabled()⟧ :946-952（⟦--dry-run⟧ / ⟦--apply⟧ 语义原样）、P0/P1 闸门 ⟦guard_p0_only⟧、relations 生成、上传与 ⟦update-file⟧ 的 HTTP 逻辑、tools/curseforge.json、CI。

### 1.2 docs/agent/AGENTS-dsh.md（CRLF；+1 行）

:167 在〈发布流程硬规则〉里追加 1 条（插在「发布前须取得用户许可（2026-11-02 补充）」之后）：

{{MD:167}}

未碰 ⟦docs/agent/AGENTS-dsh-linux.md⟧（该文件在工作树里另有他人的既存改动，见 §8）。

## 2. 逐点前后对比

### 2.1 显示名：从「jar 文件名」到「自动推导」

旧（HEAD :989-995，⟦upload_metadata⟧ 里硬编码回落到 jar 名）：

{{OLD:989-995}}

新（:1107-1119）：

{{SRC:1107-1119}}

### 2.2 changelog：从「有就带」到「只有主线带」

旧（HEAD :967-970，⟦build_jobs⟧ 直接 ⟦pick_changelog(ver, args.changelog)⟧，任何版本只要有文件就带；且 ⟦changelogType⟧ 无条件发）：

{{OLD:967-970}}

新（:1072-1088）：

{{SRC:1072-1088}}

### 2.3 dry-run 输出：新增两行判定

旧（HEAD :974-986）：

{{OLD:974-986}}

新（:1092-1104）：

{{SRC:1092-1104}}

### 2.4 CLI：新增 ⟦--no-changelog⟧

push 子命令（:1286-1296）：

{{SRC:1286-1296}}

legacy 裸 ⟦--jar⟧（:1411-1421）：

{{SRC:1411-1421}}

## 3. 判定函数原文（带行号）

{{FILE:work/_cfname_probe/report_data/funcs.txt}}

## 4. 四组 dry-run 原始输出

说明：全部为 ⟦python tools/curseforge_upload.py …⟧ 的完整 stdout+stderr（未加 ⟦--apply⟧），由 shell 重定向落盘后逐字粘贴。

### 4.1 前置：用户给的原命令（未加 ⟦--allow-p1⟧）被 P1 闸门拦住

命令：

    python tools/curseforge_upload.py --jar fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar --proxy http://127.0.0.1:7897

原始输出（exit code 1）：

{{OUT:A0_blocked}}

这是 2026-10-25 既有裁决（P1 两条线默认禁止上传）在起作用，与本次改动无关 —— 它在「算显示名」之前就抛 ⟦SystemExit⟧。为了复现用户要求看到的判定输出，下面 A 组加上 ⟦--allow-p1⟧（仍是 dry-run，未发任何请求）。

### 4.2 A 组：小版本 hotfix + fabric（命令加 ⟦--allow-p1⟧）

命令：

    python tools/curseforge_upload.py --jar fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar --proxy http://127.0.0.1:7897 --allow-p1

原始输出：

{{OUT:A_hotfix_fabric}}

断言：⟦DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)⟧ ✔；⟦CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)⟧ ✔；无「更新日志 = …」行 ✔（小版本确实不附）。

### 4.3 B 组：主线版本 neoforge 1.21.1（副本，原文件未动）

命令（jar 是从 ⟦neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar⟧ **复制**到 ⟦work/_cfname_probe/⟧ 的副本）：

    python tools/curseforge_upload.py --jar work/_cfname_probe/astral_dice-1.5.0+neoforge_1.21.1.jar --proxy http://127.0.0.1:7897

原始输出：

{{OUT:B_mainline_probe}}

断言：⟦DISPLAY: Astral Dice 1.5.0 (NeoForge 1.21.1)⟧ ✔；⟦CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)⟧ ✔。

### 4.4 C 组：⟦--no-changelog⟧ 覆盖主线默认

命令：

    python tools/curseforge_upload.py --jar work/_cfname_probe/astral_dice-1.5.0+neoforge_1.21.1.jar --proxy http://127.0.0.1:7897 --no-changelog

原始输出：

{{OUT:C_no_changelog}}

断言：⟦CHANGELOG: skip (--no-changelog 强制不附 changelog)⟧ ✔（主线版本也被关掉）。

### 4.5 D 组：⟦--display-name 'X'⟧ 覆盖推导

命令：

    python tools/curseforge_upload.py --jar work/_cfname_probe/astral_dice-1.5.0+neoforge_1.21.1.jar --proxy http://127.0.0.1:7897 --display-name 'X'

原始输出：

{{OUT:D_display_name}}

断言：⟦DISPLAY: X⟧ ✔（显式覆盖优先于推导；changelog 决策不受影响，仍按主线附带）。

## 5. 其它验证

### 5.1 语法与整洁性

    python -c "import ast;ast.parse(open('tools/curseforge_upload.py',encoding='utf-8').read())"   →  AST OK（无 SyntaxWarning）
    python -m py_compile tools/curseforge_upload.py                                                 →  COMPILE_OK
    git diff --check                                                                                →  DIFFCHECK_OK（无空白错误）
    python -c "…'endswith_nl True / endswith_2nl False'"                                            →  bytes 78077，文件以单个换行结尾

### 5.2 离线策略自检（不联网、不依赖 jar，直接调模块函数）

脚本 ⟦work/_cfname_probe/check_policy.py⟧ 覆盖 8 个显示名样例 + 主线判定 + metadata 字段集：

{{FILE:work/_cfname_probe/report_data/policy.txt}}

结论：⟦1.5.1⟧ / ⟦1.6.2⟧ / ⟦1.3.7-beta.2⟧ 均判为小版本（不附 changelog），四个 ⟦1.5.0⟧ 样例判为主线；未知加载器回落首字母大写；小版本 metadata 里 ⟦changelog⟧ 与 ⟦changelogType⟧ 两个键都不存在。（自检用的是最小版本目录桩，所以 ⟦gameVersions⟧ 只列了两个 id，与真实 dry-run 的 4 个 id 不是同一来源。）

### 5.3 既有 changelog 一致性检查未被影响

    python tools/check_changelog_parity.py  →  CHANGELOG_PARITY: OK sections=27 entries=791/791（exit 0）

## 6. 行尾统计（实测）

{{FILE:work/_cfname_probe/report_data/eol.txt}}

- tools/curseforge_upload.py：裸 LF 1,579、CRLF 0、裸 CR 0 ⇒ 原 LF 口径保持，单换行结尾。
- docs/agent/AGENTS-dsh.md：CRLF 316、裸 LF 0、裸 CR 0 ⇒ 原 CRLF 口径保持（HEAD 里是 LF 存储 50,467 B，工作区 CRLF 化后 51,078 B，+1 行后与实测一致）。

## 7. 附录

### 附录 A：tools/curseforge_upload.py 完整 diff

{{FILE:work/_cfname_probe/report_data/py.diff}}

### 附录 B：docs/agent/AGENTS-dsh.md 完整 diff

{{FILE:work/_cfname_probe/report_data/md.diff}}

## 8. 未做 / 风险项

未做（按纪律）：

- 未上传任何 jar、未 ⟦--apply⟧、未 commit / push / tag、未碰 GitHub Release、未改 CI 与 ⟦tools/curseforge.json⟧。
- 未跑游戏内验证（本次只改发布工具的元数据口径，不涉及模组运行时行为）。

风险与注意：

1. **库产物（⟦starengine-*.jar⟧）不套用新显示名**，仍用 jar 文件名 —— 它推到 CurseForge 的**库项目**，命名口径与模组发布件不同。这是主动偏离，已在 :1072-1078 的代码注释与 :99-129 docstring 里写明；若以后要求库项目也用 ⟦Astral Dice …⟧ 格式，只需删掉 ⟦is_lib_jar⟧ 那一支。
2. **主线判定只看版本号字符串**（⟦^d+.d+.0$⟧）：⟦1.5.0⟧ 算主线、⟦1.5.1⟧ 不算。少数项目把 ⟦X.Y.1⟧ 当「新功能版」发布时，需要显式 ⟦--changelog⟧ 才写 CF changelog —— 这是规则的字面实现，不是 bug。
3. **P1 闸门在前**：fabric / 26.1.2 两条线的产物在算显示名之前就被 ⟦guard_p0_only⟧ 拒掉，要看到它们的 DISPLAY 必须显式 ⟦--allow-p1⟧（本次验证即如此，且仍是 dry-run）。
4. **⟦--no-changelog⟧ 与 ⟦--changelog⟧ 同给会直接报错**（⟦SystemExit⟧，:734-735），不静默取其一。
5. **工作树里有他人/其它会话的既存未提交改动**（⟦docs/agent/AGENTS-dsh-linux.md⟧、⟦docs/agent/AGENTS-full.md⟧、⟦docs/archive/card-recipes-2026-10-09/README.md⟧、fabric-1.20.1 下三个 java 文件），以及 ⟦work/⟧、⟦.tmp/⟧ 大量未跟踪文件 —— 一律未碰，按红线只改了自己那两个文件。
6. 本报告里的 dry-run 全部走了 ⟦--proxy http://127.0.0.1:7897⟧；dry-run 本身不联网（版本表走本地缓存），联网路径未被本次验证覆盖。
7. 生成报告用的脚本与原始输出留在 ⟦work/_cfname_probe/⟧（⟦gather.py⟧ / ⟦splice.py⟧ / ⟦report_body.md⟧ / ⟦out/*.txt⟧ / ⟦report_data/*⟧），均为未跟踪文件，可随时删除。

## 9. 结论

默认显示名现在按 jar 解析值自动推导为 ⟦Astral Dice <版本> (<加载器> <MC>)⟧（加载器规范成 Fabric / Forge / NeoForge，⟦--display-name⟧ 可覆盖），changelog 只在主线版本 ⟦X.Y.0⟧ 自动附带、小版本（补丁 / hotfix / 预发布）默认不写且 metadata 里连 ⟦changelogType⟧ 都不发，⟦--changelog⟧ 显式指定仍生效、⟦--no-changelog⟧ 可强制关闭，两处决策都在 dry-run 里各打一行供核对。
