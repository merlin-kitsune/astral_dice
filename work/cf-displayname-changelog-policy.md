# CF 上传：站点显示名自动推导 + changelog 附带策略（2026-11-02）

- 仓库：F:/MCProject/astral_dice_multiloader
- 目标文件：tools/curseforge_upload.py（纯标准库；改后 1,579 行 / 78,077 B / 全 LF）
- 实际改动文件（共 2 个）：tools/curseforge_upload.py（保持 LF）、docs/agent/AGENTS-dsh.md（保持 CRLF）
- 依据：用户 2026-11-02 规则 —— ① CF 上传件显示名固定 `Astral Dice <版本> (<加载器> <MC>)`；② 小版本更新不在 CF 写 changelog
- 纪律：全程 dry-run；未上传、未 --apply、未 commit / push / tag、未碰 Release、未改 CI 与 tools/curseforge.json

## 0. 结论速览

| 维度 | 改前 | 改后 |
|---|---|---|
| 站点显示名 | jar 文件名 `astral_dice-1.5.0+fabric_1.20.1.jar` | 自动推导 `Astral Dice 1.5.0 (Fabric 1.20.1)` |
| 加载器写法 | 跟 jar 名走小写 | 规范为 Fabric / Forge / NeoForge；未知值首字母大写（`quilt` → `Quilt`） |
| changelog 默认 | `release/<版本>/PLAYER_CHANGELOG*.md` 存在就带（任何版本） | 只有主线版本 `X.Y.0` 自动带；小版本（补丁 / hotfix / 预发布）不写 |
| changelogType | 无条件写进 metadata | 只在真附带 changelog 时写；小版本连同 `changelog` 两个字段都不发 |
| 人工覆盖 | `--display-name` / `--changelog` | 均保留；新增 `--no-changelog`（与 `--changelog` 互斥） |
| dry-run 可核对 | 只有「更新日志 = …」一行 | 额外两行 `DISPLAY:` / `CHANGELOG:` |
| 规则文档 | — | docs/agent/AGENTS-dsh.md:167 新增 1 条 |

## 1. 改动点清单（文件:行号）

### 1.1 tools/curseforge_upload.py（LF；+150 −15）

| 行号 | 改动 |
|---|---|
| :99-129 | 模块 docstring：写入显示名 5 个样例 + 「changelog 只对主线版本默认附带」+ dry-run 两行样例；删掉旧的「--display-name 显式覆盖 / 发布说明默认取 release/…」两段 |
| :213-216 | 新增 `LOADER_DISPLAY_LABELS`（fabric/forge/neoforge → 规范大小写）与 `MAINLINE_VERSION_RE = ^\d+\.\d+\.0$` |
| :600-607 | 新增 `display_path(p)`：输出路径统一正斜杠（旧代码直接打 `rel_display()`，Windows 下会出现反斜杠） |
| :684-694 | 新增 `loader_display_label(loader)` |
| :697-710 | 新增 `default_display_name(ver, loader, mc)`（docstring 里就是 5 条验收样例） |
| :713-720 | 新增 `is_mainline_version(ver)` |
| :723-748 | 新增 `decide_changelog(ver, explicit, disabled)` → `(Path 或 None, 说明行)` |
| :1057-1089 | `build_jobs`：算出 `display`（--display-name → 库产物 → 自动推导三段判定）与 `changelog/changelog_line`；job dict 新增 `display` / `_changelog_line` |
| :1092-1104 | `print_jobs_plan`：每条 job 多打印 `DISPLAY: …` 与 CHANGELOG 决策行；「更新日志」行改用 `display_path` |
| :1107-1119 | `upload_metadata`：`displayName` 回落 `job.get("display")`；`changelogType` 与 `changelog` 一起移进 `if job["_changelog"]` |
| :1286-1296 | push 子命令 parser：新增 `--no-changelog`，并改 `--display-name` / `--changelog` 的 help 文案 |
| :1411-1421 | legacy（裸 --jar）parser：同样新增 `--no-changelog` 与 help 文案 |

未改动的关键点（刻意保留原语义）：真写唯一判定点 `write_enabled()` :946-952（`--dry-run` / `--apply` 语义原样）、P0/P1 闸门 `guard_p0_only`、relations 生成、上传与 `update-file` 的 HTTP 逻辑、tools/curseforge.json、CI。

### 1.2 docs/agent/AGENTS-dsh.md（CRLF；+1 行）

:167 在〈发布流程硬规则〉里追加 1 条（插在「发布前须取得用户许可（2026-11-02 补充）」之后）：


````
# docs/agent/AGENTS-dsh.md:167-167
  167  - **CF 站点显示名与 changelog 口径（2026-11-02）**：上传件的显示名一律 `Astral Dice <版本> (<加载器> <MC>)`（加载器写 `NeoForge`/`Forge`/`Fabric`）；小版本更新（补丁 / hotfix / 预发布）不在 CF 写 changelog，只有主线版本 `X.Y.0` 才附带。
````


未碰 `docs/agent/AGENTS-dsh-linux.md`（该文件在工作树里另有他人的既存改动，见 §8）。

## 2. 逐点前后对比

### 2.1 显示名：从「jar 文件名」到「自动推导」

旧（HEAD :989-995，`upload_metadata` 里硬编码回落到 jar 名）：


````
# git show HEAD:tools/curseforge_upload.py  :989-995
  989  def upload_metadata(args, job):
  990      metadata = {
  991          "displayName": args.display_name or job["jar"].name,
  992          "releaseType": job["releaseType"],
  993          "gameVersions": job["gameVersions"],
  994          "changelogType": args.changelog_type,
  995      }
````


新（:1107-1119）：


````
# tools/curseforge_upload.py:1107-1119
 1107  def upload_metadata(args, job):
 1108      metadata = {
 1109          # 显示名：--display-name 优先；否则用 build_jobs 里推导好的
 1110          # 「Astral Dice <版本> (<加载器> <MC>)」（2026-11-02 用户规则）。
 1111          "displayName": args.display_name or job.get("display") or job["jar"].name,
 1112          "releaseType": job["releaseType"],
 1113          "gameVersions": job["gameVersions"],
 1114      }
 1115      if job["_changelog"]:
 1116          # changelogType 只在**真附带** changelog 时出现：小版本不写 CF changelog
 1117          # ⇒ changelog 与 changelogType 两个字段都不发（2026-11-02 用户规则）。
 1118          metadata["changelogType"] = args.changelog_type
 1119          metadata["changelog"] = _read_text(job["_changelog"])
````


### 2.2 changelog：从「有就带」到「只有主线带」

旧（HEAD :967-970，`build_jobs` 直接 `pick_changelog(ver, args.changelog)`，任何版本只要有文件就带；且 `changelogType` 无条件发）：


````
# git show HEAD:tools/curseforge_upload.py  :967-970
  967          jobs.append({"jar": jar, "ver": ver, "loader": loader, "mc": mc,
  968                       "releaseType": rtype, "gameVersions": ids, "_detail": detail,
  969                       "_line": (line or {}).get("key"), "_relations": rel,
  970                       "_changelog": pick_changelog(ver, args.changelog)})
````


新（:1072-1088）：


````
# tools/curseforge_upload.py:1072-1088
 1072          # 显示名（2026-11-02 用户规则）：--display-name 优先；否则按
 1073          # 「Astral Dice <版本> (<加载器> <MC>)」自动推导。库产物不套用这套口径
 1074          # （它推到库项目，命名与模组发布件不同），保留 jar 文件名。
 1075          if args.display_name:
 1076              display = args.display_name
 1077          elif is_lib_jar(jar.name):
 1078              display = jar.name
 1079          else:
 1080              display = default_display_name(ver, loader, mc)
 1081          # changelog 策略（2026-11-02 用户规则）：只有主线版本默认附带；小版本不写。
 1082          changelog, changelog_line = decide_changelog(
 1083              ver, args.changelog, getattr(args, "no_changelog", False))
 1084          jobs.append({"jar": jar, "ver": ver, "loader": loader, "mc": mc,
 1085                       "releaseType": rtype, "gameVersions": ids, "_detail": detail,
 1086                       "_line": (line or {}).get("key"), "_relations": rel,
 1087                       "display": display, "_changelog": changelog,
 1088                       "_changelog_line": changelog_line})
````


### 2.3 dry-run 输出：新增两行判定

旧（HEAD :974-986）：


````
# git show HEAD:tools/curseforge_upload.py  :974-986
  974  def print_jobs_plan(jobs):
  975      for j in jobs:
  976          cl = j["_changelog"]
  977          if cl:
  978              cl_desc = f"{rel_display(cl)}（{len(_read_text(cl))} 字符）"
  979          else:
  980              cl_desc = "（无，留空）"
  981          print(f"  {j['jar'].name}")
  982          print(f"    版本 {j['ver']} | {j['loader']} | MC {j['mc']} | 类型 {j['releaseType']}")
  983          print(f"    gameVersions = {j['gameVersions']}  {j['_detail']}")
  984          print(f"    更新日志 = {cl_desc}")
  985          print(f"    relations = {describe_relations(j['_relations'])}"
  986                + (f"  [{j['_line']}]" if j.get("_line") else "  [未匹配到该线]"))
````


新（:1092-1104）：


````
# tools/curseforge_upload.py:1092-1104
 1092  def print_jobs_plan(jobs):
 1093      for j in jobs:
 1094          cl = j["_changelog"]
 1095          print(f"  {j['jar'].name}")
 1096          print(f"    版本 {j['ver']} | {j['loader']} | MC {j['mc']} | 类型 {j['releaseType']}")
 1097          # 两条判定各打一行：dry-run 的验收依据（2026-11-02 用户规则）。
 1098          print(f"    DISPLAY: {j['display']}")
 1099          print(f"    {j['_changelog_line']}")
 1100          if cl:
 1101              print(f"    更新日志 = {display_path(cl)}（{len(_read_text(cl))} 字符）")
 1102          print(f"    gameVersions = {j['gameVersions']}  {j['_detail']}")
 1103          print(f"    relations = {describe_relations(j['_relations'])}"
 1104                + (f"  [{j['_line']}]" if j.get("_line") else "  [未匹配到该线]"))
````


### 2.4 CLI：新增 `--no-changelog`

push 子命令（:1286-1296）：


````
# tools/curseforge_upload.py:1286-1296
 1286                          "release/<版本>/PLAYER_CHANGELOG*.md，小版本（补丁/hotfix/预发布）默认不写")
 1287      p.add_argument("--changelog-type", default="markdown", choices=["markdown", "html", "text"])
 1288      p.add_argument("--no-changelog", action="store_true",
 1289                     help="强制不附带 changelog（覆盖主线版本的默认附带；与 --changelog 互斥）")
 1290      p.add_argument("--display-name",
 1291                     help="站点显示名（默认自动推导为 'Astral Dice <版本> (<加载器> <MC>)'，"
 1292                          "如 Astral Dice 1.5.0 (Fabric 1.20.1)）")
 1293      p.add_argument("--mc-version", help="覆盖从文件名解析出的 MC 版本")
 1294      p.add_argument("--loader", help="覆盖从文件名解析出的加载器（如 neoforge / forge）")
 1295      p.add_argument("--release-type", choices=["release", "beta", "alpha"],
 1296                     help="覆盖按版本号推断的发布类型")
````


legacy 裸 `--jar`（:1411-1421）：


````
# tools/curseforge_upload.py:1411-1421
 1411                           "release/<版本>/PLAYER_CHANGELOG*.md，小版本（补丁/hotfix/预发布）默认不写")
 1412      ap.add_argument("--changelog-type", default="markdown", choices=["markdown", "html", "text"])
 1413      ap.add_argument("--no-changelog", action="store_true",
 1414                      help="强制不附带 changelog（覆盖主线版本的默认附带；与 --changelog 互斥）")
 1415      ap.add_argument("--display-name",
 1416                      help="站点显示名（默认自动推导为 'Astral Dice <版本> (<加载器> <MC>)'，"
 1417                           "如 Astral Dice 1.5.0 (Fabric 1.20.1)）")
 1418      ap.add_argument("--mc-version", help="覆盖从文件名解析出的 MC 版本")
 1419      ap.add_argument("--loader", help="覆盖从文件名解析出的加载器（如 neoforge / forge）")
 1420      ap.add_argument("--release-type", choices=["release", "beta", "alpha"],
 1421                      help="覆盖按版本号推断的发布类型")
````


## 3. 判定函数原文（带行号）


````
# :600-607  display_path（显示路径统一正斜杠）
  600  def display_path(p):
  601      """展示用路径：统一成正斜杠。
  602  
  603      rel_display 在 Windows 上按 os.sep 渲染成反斜杠（str(Path) 的默认行为），而文档与
  604      dry-run 样本一律写正斜杠 —— 同一个路径，这里只为输出口径一致、跨宿主可比。
  605      """
  606      return str(rel_display(p)).replace("\\", "/")
  607  

# :684-694  loader_display_label
  684  def loader_display_label(loader):
  685      """加载器显示名：fabric→Fabric / forge→Forge / neoforge→NeoForge；未知值回落为「首字母大写」。
  686  
  687      ⚠️ 只对已知三个做规范映射；未知值走 ``key[:1].upper() + key[1:]``（保留其余原样），
  688      不用 ``str.capitalize()`` —— 后者会把 ``neoForge`` 之类的中间大小写也抹平，
  689      而未知加载器本来就没有权威写法，抹平只会更失真。
  690      """
  691      key = str(loader or "").strip()
  692      if key.lower() in LOADER_DISPLAY_LABELS:
  693          return LOADER_DISPLAY_LABELS[key.lower()]
  694      return key[:1].upper() + key[1:]

# :697-710  default_display_name
  697  def default_display_name(ver, loader, mc):
  698      """推导 CF 站点显示名：``Astral Dice <版本> (<加载器> <MC>)``（2026-11-02 用户规则）。
  699  
  700      口径正本（样例即验收标准）：
  701          astral_dice-1.5.0+fabric_1.20.1.jar        ⇒ Astral Dice 1.5.0 (Fabric 1.20.1)
  702          astral_dice-1.5.0+neoforge_1.21.1.jar      ⇒ Astral Dice 1.5.0 (NeoForge 1.21.1)
  703          astral_dice-1.5.0+forge_1.20.1.jar         ⇒ Astral Dice 1.5.0 (Forge 1.20.1)
  704          astral_dice-1.5.0+neoforge_26.1.2.jar      ⇒ Astral Dice 1.5.0 (NeoForge 26.1.2)
  705          astral_dice-1.5.0-hotfix+fabric_1.20.1.jar ⇒ Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
  706  
  707      ``ver`` 用 parse_jar 解析出的版本（**不含** ``+<加载器>_<MC>`` 后缀）。
  708      ``--display-name`` 显式给了就以它为准（见 build_jobs / upload_metadata）。
  709      """
  710      return "Astral Dice %s (%s %s)" % (ver, loader_display_label(loader), mc)

# :713-720  is_mainline_version
  713  def is_mainline_version(ver):
  714      """主线版本判定（2026-11-02 用户规则）：``X.Y.0`` 且不含 ``-`` 后缀 ⇒ 默认附带 changelog。
  715  
  716      其它一律算小版本（补丁 / hotfix / 预发布）：
  717          ``1.5.1``、``1.6.2``、``1.5.0-hotfix``、``1.3.7-beta.2``、``1.3.7-rc1`` ……
  718      ⇒ 默认**不**在 CF 写 changelog。
  719      """
  720      return bool(MAINLINE_VERSION_RE.match(str(ver or "").strip()))

# :723-748  decide_changelog
  723  def decide_changelog(ver, explicit, disabled=False):
  724      """决定本次是否附带 changelog，返回 ``(Path 或 None, 打印用说明行)``。
  725  
  726      规则（2026-11-02 用户规则，优先级从上到下）：
  727        1. ``--no-changelog`` ⇒ 强制不附（与 ``--changelog`` 互斥，同时给直接报错）；
  728        2. ``--changelog <文件>`` ⇒ 用户手动指定，任何版本都附（文件不存在仍报错）；
  729        3. 主线版本（见 is_mainline_version）⇒ 自动取
  730           ``release/<基础版本>/PLAYER_CHANGELOG*.md``；
  731        4. 其它（小版本：补丁 / hotfix / 预发布）⇒ **不附** —— metadata 里
  732           ``changelog`` 与 ``changelogType`` 两个字段都不出现。
  733      """
  734      if disabled and explicit:
  735          raise SystemExit("[ERR] --no-changelog 与 --changelog 冲突：二者只能选一个")
  736      if disabled:
  737          return None, "CHANGELOG: skip (--no-changelog 强制不附 changelog)"
  738      if explicit:
  739          p = pick_changelog(ver, explicit)  # 内含「文件不存在就 SystemExit」
  740          return p, "CHANGELOG: attach %s (显式 --changelog)" % display_path(p)
  741      if is_mainline_version(ver):
  742          p = pick_changelog(ver, None)
  743          if p is not None:
  744              return p, "CHANGELOG: attach %s (主线版本)" % display_path(p)
  745          return None, ("CHANGELOG: skip (主线版本，但没有 release/%s/PLAYER_CHANGELOG*.md)"
  746                        % str(ver).split("-")[0])
  747      return None, ("CHANGELOG: skip (小版本更新不写 CF changelog: ver=%s, 用户 2026-11-02 规则)"
  748                    % ver)
````


## 4. 四组 dry-run 原始输出

说明：全部为 `python tools/curseforge_upload.py …` 的完整 stdout+stderr（未加 `--apply`），由 shell 重定向落盘后逐字粘贴。

### 4.1 前置：用户给的原命令（未加 `--allow-p1`）被 P1 闸门拦住

命令：

    python tools/curseforge_upload.py --jar fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar --proxy http://127.0.0.1:7897

原始输出（exit code 1）：


````
[BLOCKED] astral_dice-1.5.0-hotfix+fabric_1.20.1.jar 属于 P1 线（fabric / MC 1.20.1），按 2026-10-25 用户裁决默认禁止上传：
          P1 两线（neoforge-26.1.2 / fabric-1.20.1）从未获授权移植，其编译产物一律视为不可用。
          允许上传的只有 P0 两线：neoforge-1.21.1 与 forge-1.20.1。
          若本次任务确由用户点名要求发布 P1，请显式加 --allow-p1。
````


这是 2026-10-25 既有裁决（P1 两条线默认禁止上传）在起作用，与本次改动无关 —— 它在「算显示名」之前就抛 `SystemExit`。为了复现用户要求看到的判定输出，下面 A 组加上 `--allow-p1`（仍是 dry-run，未发任何请求）。

### 4.2 A 组：小版本 hotfix + fabric（命令加 `--allow-p1`）

命令：

    python tools/curseforge_upload.py --jar fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar --proxy http://127.0.0.1:7897 --allow-p1

原始输出：


````
[WARN] P1 产物上传已放行（--allow-p1）：astral_dice-1.5.0-hotfix+fabric_1.20.1.jar | fabric | MC 1.20.1 —— 仅限用户逐次点名下达的任务
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 离线缓存 .curseforge\versions.json（5.2 天前；dry-run 不联网）
写请求         = 否（未给 --apply/--yes ⇒ 默认 dry-run（不发任何写请求））
------------------------------------------------------------------------
  astral_dice-1.5.0-hotfix+fabric_1.20.1.jar
    版本 1.5.0-hotfix | fabric | MC 1.20.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
    CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)
    gameVersions = [9638, 9639, 9990, 7499]  {'Client': 9638, 'Server': 9639, '1.20.1': 9990, 'fabric': 7499}
    relations = fabric-api(306612)=requiredDependency, puzzles-lib(495476)=requiredDependency, trinkets(341284)=requiredDependency, accessories(938917)=optionalDependency, starengine(1755415)=requiredDependency  [fabric_1.20.1]
------------------------------------------------------------------------
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
````


断言：`DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)` ✔；`CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)` ✔；无「更新日志 = …」行 ✔（小版本确实不附）。

### 4.3 B 组：主线版本 neoforge 1.21.1（副本，原文件未动）

命令（jar 是从 `neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar` **复制**到 `work/_cfname_probe/` 的副本）：

    python tools/curseforge_upload.py --jar work/_cfname_probe/astral_dice-1.5.0+neoforge_1.21.1.jar --proxy http://127.0.0.1:7897

原始输出：


````
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 离线缓存 .curseforge\versions.json（5.2 天前；dry-run 不联网）
写请求         = 否（未给 --apply/--yes ⇒ 默认 dry-run（不发任何写请求））
------------------------------------------------------------------------
  astral_dice-1.5.0+neoforge_1.21.1.jar
    版本 1.5.0 | neoforge | MC 1.21.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0 (NeoForge 1.21.1)
    CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)
    更新日志 = release/1.5.0/PLAYER_CHANGELOG.md（56234 字符）
    gameVersions = [9638, 9639, 11779, 10150]  {'Client': 9638, 'Server': 9639, '1.21.1': 11779, 'neoforge': 10150}
    relations = curios(309927)=requiredDependency, starengine(1755415)=requiredDependency  [neoforge_1.21.1]
------------------------------------------------------------------------
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
````


断言：`DISPLAY: Astral Dice 1.5.0 (NeoForge 1.21.1)` ✔；`CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)` ✔。

### 4.4 C 组：`--no-changelog` 覆盖主线默认

命令：

    python tools/curseforge_upload.py --jar work/_cfname_probe/astral_dice-1.5.0+neoforge_1.21.1.jar --proxy http://127.0.0.1:7897 --no-changelog

原始输出：


````
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 离线缓存 .curseforge\versions.json（5.2 天前；dry-run 不联网）
写请求         = 否（未给 --apply/--yes ⇒ 默认 dry-run（不发任何写请求））
------------------------------------------------------------------------
  astral_dice-1.5.0+neoforge_1.21.1.jar
    版本 1.5.0 | neoforge | MC 1.21.1 | 类型 release
    DISPLAY: Astral Dice 1.5.0 (NeoForge 1.21.1)
    CHANGELOG: skip (--no-changelog 强制不附 changelog)
    gameVersions = [9638, 9639, 11779, 10150]  {'Client': 9638, 'Server': 9639, '1.21.1': 11779, 'neoforge': 10150}
    relations = curios(309927)=requiredDependency, starengine(1755415)=requiredDependency  [neoforge_1.21.1]
------------------------------------------------------------------------
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
````


断言：`CHANGELOG: skip (--no-changelog 强制不附 changelog)` ✔（主线版本也被关掉）。

### 4.5 D 组：`--display-name 'X'` 覆盖推导

命令：

    python tools/curseforge_upload.py --jar work/_cfname_probe/astral_dice-1.5.0+neoforge_1.21.1.jar --proxy http://127.0.0.1:7897 --display-name 'X'

原始输出：


````
项目 projectId = 1662159（slug: astral-dice，来源 tools/curseforge.json）
凭据           = token: .curseforge\token ；api-key: 回退：用旧版 X-Api-Token 充当 x-api-key（未验证可过）
代理           = http://127.0.0.1:7897
版本表         = 离线缓存 .curseforge\versions.json（5.2 天前；dry-run 不联网）
写请求         = 否（未给 --apply/--yes ⇒ 默认 dry-run（不发任何写请求））
------------------------------------------------------------------------
  astral_dice-1.5.0+neoforge_1.21.1.jar
    版本 1.5.0 | neoforge | MC 1.21.1 | 类型 release
    DISPLAY: X
    CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)
    更新日志 = release/1.5.0/PLAYER_CHANGELOG.md（56234 字符）
    gameVersions = [9638, 9639, 11779, 10150]  {'Client': 9638, 'Server': 9639, '1.21.1': 11779, 'neoforge': 10150}
    relations = curios(309927)=requiredDependency, starengine(1755415)=requiredDependency  [neoforge_1.21.1]
------------------------------------------------------------------------
DRY-RUN：未发送任何请求（默认行为；真上传需 --apply）。
````


断言：`DISPLAY: X` ✔（显式覆盖优先于推导；changelog 决策不受影响，仍按主线附带）。

## 5. 其它验证

### 5.1 语法与整洁性

    python -c "import ast;ast.parse(open('tools/curseforge_upload.py',encoding='utf-8').read())"   →  AST OK（无 SyntaxWarning）
    python -m py_compile tools/curseforge_upload.py                                                 →  COMPILE_OK
    git diff --check                                                                                →  DIFFCHECK_OK（无空白错误）
    python -c "…'endswith_nl True / endswith_2nl False'"                                            →  bytes 78077，文件以单个换行结尾

### 5.2 离线策略自检（不联网、不依赖 jar，直接调模块函数）

脚本 `work/_cfname_probe/check_policy.py` 覆盖 8 个显示名样例 + 主线判定 + metadata 字段集：


````
== 显示名 + 主线判定 ==
astral_dice-1.5.0+fabric_1.20.1.jar            -> Astral Dice 1.5.0 (Fabric 1.20.1)          mainline=True
astral_dice-1.5.0+neoforge_1.21.1.jar          -> Astral Dice 1.5.0 (NeoForge 1.21.1)        mainline=True
astral_dice-1.5.0+forge_1.20.1.jar             -> Astral Dice 1.5.0 (Forge 1.20.1)           mainline=True
astral_dice-1.5.0+neoforge_26.1.2.jar          -> Astral Dice 1.5.0 (NeoForge 26.1.2)        mainline=True
astral_dice-1.5.0-hotfix+fabric_1.20.1.jar     -> Astral Dice 1.5.0-hotfix (Fabric 1.20.1)   mainline=False
astral_dice-1.5.1+forge_1.20.1.jar             -> Astral Dice 1.5.1 (Forge 1.20.1)           mainline=False
astral_dice-1.6.2+fabric_1.20.1.jar            -> Astral Dice 1.6.2 (Fabric 1.20.1)          mainline=False
astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar   -> Astral Dice 1.3.7-beta.2 (NeoForge 26.1.2) mainline=False

== 未知加载器回落 ==
default_display_name('1.5.0','quilt','1.20.1') = Astral Dice 1.5.0 (Quilt 1.20.1)

== 真实将 POST 的 metadata（小版本：不应出现 changelog/changelogType） ==
astral_dice-1.5.0-hotfix+fabric_1.20.1.jar
     CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)
     keys = ['displayName', 'gameVersions', 'releaseType']
     json = {"displayName": "Astral Dice 1.5.0-hotfix (Fabric 1.20.1)", "releaseType": "release", "gameVersions": [9638, 9639]}
astral_dice-1.5.0+neoforge_1.21.1.jar
     CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)
     keys = ['changelog', 'changelogType', 'displayName', 'gameVersions', 'releaseType']
     json = {"displayName": "Astral Dice 1.5.0 (NeoForge 1.21.1)", "releaseType": "release", "gameVersions": [9638, 9639], "changelogType": "markdown", "changelog": "<56234 字符>"}
````


结论：`1.5.1` / `1.6.2` / `1.3.7-beta.2` 均判为小版本（不附 changelog），四个 `1.5.0` 样例判为主线；未知加载器回落首字母大写；小版本 metadata 里 `changelog` 与 `changelogType` 两个键都不存在。（自检用的是最小版本目录桩，所以 `gameVersions` 只列了两个 id，与真实 dry-run 的 4 个 id 不是同一来源。）

### 5.3 既有 changelog 一致性检查未被影响

    python tools/check_changelog_parity.py  →  CHANGELOG_PARITY: OK sections=27 entries=791/791（exit 0）

## 6. 行尾统计（实测）


````
tools/curseforge_upload.py  CRLF=0  bareLF=1579  bareCR=0  bytes=78077
docs/agent/AGENTS-dsh.md  CRLF=316  bareLF=0  bareCR=0  bytes=51078
````


- tools/curseforge_upload.py：裸 LF 1,579、CRLF 0、裸 CR 0 ⇒ 原 LF 口径保持，单换行结尾。
- docs/agent/AGENTS-dsh.md：CRLF 316、裸 LF 0、裸 CR 0 ⇒ 原 CRLF 口径保持（HEAD 里是 LF 存储 50,467 B，工作区 CRLF 化后 51,078 B，+1 行后与实测一致）。

## 7. 附录

### 附录 A：tools/curseforge_upload.py 完整 diff


````
diff --git a/tools/curseforge_upload.py b/tools/curseforge_upload.py
index 6647f8bf..aecef8df 100644
--- a/tools/curseforge_upload.py
+++ b/tools/curseforge_upload.py
@@ -98,10 +98,35 @@
 
 版本号 / 加载器 / MC 版本一律**从 jar 文件名解析**（``astral_dice-<版本>+<加载器>_<MC版本>.jar``），
 release 类型由版本号里的 ``-alpha`` / ``-beta`` / ``-rc`` 后缀决定（无后缀 ⇒ release）。
-可用 ``--mc-version`` / ``--loader`` / ``--release-type`` / ``--display-name`` 显式覆盖。
+可用 ``--mc-version`` / ``--loader`` / ``--release-type`` 显式覆盖。
 
-发布说明默认取 ``release/<基础版本>/PLAYER_CHANGELOG.md``（英文，面向 CurseForge 国际社区）；
-不存在时退回 ``PLAYER_CHANGELOG_ZH.md``；也可用 ``--changelog <文件>`` 显式指定。
+站点显示名（**2026-11-02 用户规则**）默认**自动推导**为
+``Astral Dice <版本> (<加载器> <MC>)``：版本号不带 ``+<加载器>_<MC>`` 后缀，加载器写
+``Fabric`` / ``Forge`` / ``NeoForge``（不是 jar 名里的小写 ``fabric`` / ``forge`` / ``neoforge``）：
+
+    astral_dice-1.5.0+fabric_1.20.1.jar        ⇒ Astral Dice 1.5.0 (Fabric 1.20.1)
+    astral_dice-1.5.0+neoforge_1.21.1.jar      ⇒ Astral Dice 1.5.0 (NeoForge 1.21.1)
+    astral_dice-1.5.0+forge_1.20.1.jar         ⇒ Astral Dice 1.5.0 (Forge 1.20.1)
+    astral_dice-1.5.0+neoforge_26.1.2.jar      ⇒ Astral Dice 1.5.0 (NeoForge 26.1.2)
+    astral_dice-1.5.0-hotfix+fabric_1.20.1.jar ⇒ Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
+
+``--display-name`` 仍可显式覆盖（给了就用给的，作用于本次全部 jar）。
+库产物（``starengine-<平台>-<版本>.jar``，推到库项目）不套用这套命名，仍用 jar 文件名。
+
+发布说明（**2026-11-02 用户规则**）**只对主线版本默认附带**：版本号形如 ``X.Y.0`` 且不含
+``-`` 后缀（即不是补丁 / hotfix / 预发布）时，自动取
+``release/<基础版本>/PLAYER_CHANGELOG.md``（英文，面向 CurseForge 国际社区），
+不存在时退回 ``PLAYER_CHANGELOG_ZH.md``。**小版本更新（``1.5.1`` / ``1.5.0-hotfix`` /
+``-rc`` / ``-beta`` / ``-alpha`` 等）默认不在 CF 写 changelog** —— metadata 里
+``changelog`` 与 ``changelogType`` 两个字段都不出现。覆盖方式：``--changelog <文件>``
+手动指定（任何版本都附）；``--no-changelog`` 强制不附（与 ``--changelog`` 互斥）。
+
+dry-run 会把这两条判定各打一行，便于核对：
+
+    DISPLAY: Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
+    CHANGELOG: skip (小版本更新不写 CF changelog: ver=1.5.0-hotfix, 用户 2026-11-02 规则)
+    # 或
+    CHANGELOG: attach release/1.5.0/PLAYER_CHANGELOG.md (主线版本)
 
 =====================================================================
 CurseForge 上传 API 要点（2026-09-30 实测）
@@ -185,6 +210,10 @@ UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
 JAR_RE = re.compile(r"^astral_dice-(?P<ver>[^+]+)\+(?P<loader>[a-z]+)_(?P<mc>.+)\.jar$")
 # StarEngine 库产物: starengine-<平台>-<版本>.jar（平台含 MC 版本，如 neoforge-1.21.1）
 LIB_JAR_RE = re.compile(r"^starengine-(?P<loader>neoforge|forge|fabric)-(?P<mc>\d+\.\d+(?:\.\d+)?)-(?P<ver>.+)\.jar$")
+# 站点显示名里的加载器写法（2026-11-02 用户规则）：jar 名是小写，站点显示名要规范大小写。
+LOADER_DISPLAY_LABELS = {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}
+# 主线版本判定（2026-11-02 用户规则）：X.Y.0 且不含 "-" 后缀（补丁 / hotfix / 预发布都算小版本）。
+MAINLINE_VERSION_RE = re.compile(r"^\d+\.\d+\.0$")
 CACHE_DAYS = 7
 # CurseForge 官方文档给出的 relation type 全集（没有 include 这一项）。
 RELATION_TYPES = ("requiredDependency", "optionalDependency", "embeddedLibrary", "incompatible", "tool")
@@ -568,6 +597,15 @@ def rel_display(p):
         return p
 
 
+def display_path(p):
+    """展示用路径：统一成正斜杠。
+
+    rel_display 在 Windows 上按 os.sep 渲染成反斜杠（str(Path) 的默认行为），而文档与
+    dry-run 样本一律写正斜杠 —— 同一个路径，这里只为输出口径一致、跨宿主可比。
+    """
+    return str(rel_display(p)).replace("\\", "/")
+
+
 def find_line(binding, key=None, loader=None, mc=None):
     """在 `tools/curseforge.json` 的 lines 里定位一条线：优先用 `key`，否则按 loader+mc 唯一匹配。"""
     lines = binding.get("lines") or []
@@ -643,6 +681,73 @@ def pick_changelog(ver, explicit):
     return None
 
 
+def loader_display_label(loader):
+    """加载器显示名：fabric→Fabric / forge→Forge / neoforge→NeoForge；未知值回落为「首字母大写」。
+
+    ⚠️ 只对已知三个做规范映射；未知值走 ``key[:1].upper() + key[1:]``（保留其余原样），
+    不用 ``str.capitalize()`` —— 后者会把 ``neoForge`` 之类的中间大小写也抹平，
+    而未知加载器本来就没有权威写法，抹平只会更失真。
+    """
+    key = str(loader or "").strip()
+    if key.lower() in LOADER_DISPLAY_LABELS:
+        return LOADER_DISPLAY_LABELS[key.lower()]
+    return key[:1].upper() + key[1:]
+
+
+def default_display_name(ver, loader, mc):
+    """推导 CF 站点显示名：``Astral Dice <版本> (<加载器> <MC>)``（2026-11-02 用户规则）。
+
+    口径正本（样例即验收标准）：
+        astral_dice-1.5.0+fabric_1.20.1.jar        ⇒ Astral Dice 1.5.0 (Fabric 1.20.1)
+        astral_dice-1.5.0+neoforge_1.21.1.jar      ⇒ Astral Dice 1.5.0 (NeoForge 1.21.1)
+        astral_dice-1.5.0+forge_1.20.1.jar         ⇒ Astral Dice 1.5.0 (Forge 1.20.1)
+        astral_dice-1.5.0+neoforge_26.1.2.jar      ⇒ Astral Dice 1.5.0 (NeoForge 26.1.2)
+        astral_dice-1.5.0-hotfix+fabric_1.20.1.jar ⇒ Astral Dice 1.5.0-hotfix (Fabric 1.20.1)
+
+    ``ver`` 用 parse_jar 解析出的版本（**不含** ``+<加载器>_<MC>`` 后缀）。
+    ``--display-name`` 显式给了就以它为准（见 build_jobs / upload_metadata）。
+    """
+    return "Astral Dice %s (%s %s)" % (ver, loader_display_label(loader), mc)
+
+
+def is_mainline_version(ver):
+    """主线版本判定（2026-11-02 用户规则）：``X.Y.0`` 且不含 ``-`` 后缀 ⇒ 默认附带 changelog。
+
+    其它一律算小版本（补丁 / hotfix / 预发布）：
+        ``1.5.1``、``1.6.2``、``1.5.0-hotfix``、``1.3.7-beta.2``、``1.3.7-rc1`` ……
+    ⇒ 默认**不**在 CF 写 changelog。
+    """
+    return bool(MAINLINE_VERSION_RE.match(str(ver or "").strip()))
+
+
+def decide_changelog(ver, explicit, disabled=False):
+    """决定本次是否附带 changelog，返回 ``(Path 或 None, 打印用说明行)``。
+
+    规则（2026-11-02 用户规则，优先级从上到下）：
+      1. ``--no-changelog`` ⇒ 强制不附（与 ``--changelog`` 互斥，同时给直接报错）；
+      2. ``--changelog <文件>`` ⇒ 用户手动指定，任何版本都附（文件不存在仍报错）；
+      3. 主线版本（见 is_mainline_version）⇒ 自动取
+         ``release/<基础版本>/PLAYER_CHANGELOG*.md``；
+      4. 其它（小版本：补丁 / hotfix / 预发布）⇒ **不附** —— metadata 里
+         ``changelog`` 与 ``changelogType`` 两个字段都不出现。
+    """
+    if disabled and explicit:
+        raise SystemExit("[ERR] --no-changelog 与 --changelog 冲突：二者只能选一个")
+    if disabled:
+        return None, "CHANGELOG: skip (--no-changelog 强制不附 changelog)"
+    if explicit:
+        p = pick_changelog(ver, explicit)  # 内含「文件不存在就 SystemExit」
+        return p, "CHANGELOG: attach %s (显式 --changelog)" % display_path(p)
+    if is_mainline_version(ver):
+        p = pick_changelog(ver, None)
+        if p is not None:
+            return p, "CHANGELOG: attach %s (主线版本)" % display_path(p)
+        return None, ("CHANGELOG: skip (主线版本，但没有 release/%s/PLAYER_CHANGELOG*.md)"
+                      % str(ver).split("-")[0])
+    return None, ("CHANGELOG: skip (小版本更新不写 CF changelog: ver=%s, 用户 2026-11-02 规则)"
+                  % ver)
+
+
 # ---------------------------------------------------------------- 版本表（离线规划）
 def load_catalog_offline():
     """dry-run 专用：只读本地缓存，**绝不联网**；没有缓存则返回 (None, 说明)。"""
@@ -964,36 +1069,53 @@ def build_jobs(args, binding, cat, offline):
         ids, detail = plan_ids(cat, mc, loader, offline)
         line = find_line(binding, key=args.line, loader=loader, mc=mc)
         rel = relations_payload(line, disabled=args.no_relations)
+        # 显示名（2026-11-02 用户规则）：--display-name 优先；否则按
+        # 「Astral Dice <版本> (<加载器> <MC>)」自动推导。库产物不套用这套口径
+        # （它推到库项目，命名与模组发布件不同），保留 jar 文件名。
+        if args.display_name:
+            display = args.display_name
+        elif is_lib_jar(jar.name):
+            display = jar.name
+        else:
+            display = default_display_name(ver, loader, mc)
+        # changelog 策略（2026-11-02 用户规则）：只有主线版本默认附带；小版本不写。
+        changelog, changelog_line = decide_changelog(
+            ver, args.changelog, getattr(args, "no_changelog", False))
         jobs.append({"jar": jar, "ver": ver, "loader": loader, "mc": mc,
                      "releaseType": rtype, "gameVersions": ids, "_detail": detail,
                      "_line": (line or {}).get("key"), "_relations": rel,
-                     "_changelog": pick_changelog(ver, args.changelog)})
+                     "display": display, "_changelog": changelog,
+                     "_changelog_line": changelog_line})
     return jobs
 
 
 def print_jobs_plan(jobs):
     for j in jobs:
         cl = j["_changelog"]
-        if cl:
-            cl_desc = f"{rel_display(cl)}（{len(_read_text(cl))} 字符）"
-        else:
-            cl_desc = "（无，留空）"
         print(f"  {j['jar'].name}")
         print(f"    版本 {j['ver']} | {j['loader']} | MC {j['mc']} | 类型 {j['releaseType']}")
+        # 两条判定各打一行：dry-run 的验收依据（2026-11-02 用户规则）。
+        print(f"    DISPLAY: {j['display']}")
+        print(f"    {j['_changelog_line']}")
+        if cl:
+            print(f"    更新日志 = {display_path(cl)}（{len(_read_text(cl))} 字符）")
         print(f"    gameVersions = {j['gameVersions']}  {j['_detail']}")
-        print(f"    更新日志 = {cl_desc}")
         print(f"    relations = {describe_relations(j['_relations'])}"
               + (f"  [{j['_line']}]" if j.get("_line") else "  [未匹配到该线]"))
 
 
 def upload_metadata(args, job):
     metadata = {
-        "displayName": args.display_name or job["jar"].name,
+        # 显示名：--display-name 优先；否则用 build_jobs 里推导好的
+        # 「Astral Dice <版本> (<加载器> <MC>)」（2026-11-02 用户规则）。
+        "displayName": args.display_name or job.get("display") or job["jar"].name,
         "releaseType": job["releaseType"],
         "gameVersions": job["gameVersions"],
-        "changelogType": args.changelog_type,
     }
     if job["_changelog"]:
+        # changelogType 只在**真附带** changelog 时出现：小版本不写 CF changelog
+        # ⇒ changelog 与 changelogType 两个字段都不发（2026-11-02 用户规则）。
+        metadata["changelogType"] = args.changelog_type
         metadata["changelog"] = _read_text(job["_changelog"])
     if job.get("_relations"):
         metadata["relations"] = job["_relations"]
@@ -1159,9 +1281,15 @@ def build_push_parser():
         epilog=PUSH_EPILOG)
     p.add_argument("--jar", action="append", metavar="PATH", required=True,
                    help="要上传的 jar（可重复；相对路径按当前目录解析）")
-    p.add_argument("--changelog", help="更新日志文件（默认自动取 release/<版本>/PLAYER_CHANGELOG*.md）")
+    p.add_argument("--changelog",
+                   help="更新日志文件（给了就任何版本都附）；默认只对主线版本 X.Y.0 自动取 "
+                        "release/<版本>/PLAYER_CHANGELOG*.md，小版本（补丁/hotfix/预发布）默认不写")
     p.add_argument("--changelog-type", default="markdown", choices=["markdown", "html", "text"])
-    p.add_argument("--display-name", help="站点显示名（默认 = jar 文件名）")
+    p.add_argument("--no-changelog", action="store_true",
+                   help="强制不附带 changelog（覆盖主线版本的默认附带；与 --changelog 互斥）")
+    p.add_argument("--display-name",
+                   help="站点显示名（默认自动推导为 'Astral Dice <版本> (<加载器> <MC>)'，"
+                        "如 Astral Dice 1.5.0 (Fabric 1.20.1)）")
     p.add_argument("--mc-version", help="覆盖从文件名解析出的 MC 版本")
     p.add_argument("--loader", help="覆盖从文件名解析出的加载器（如 neoforge / forge）")
     p.add_argument("--release-type", choices=["release", "beta", "alpha"],
@@ -1278,9 +1406,15 @@ def build_legacy_parser():
         epilog=LEGACY_EPILOG)
     ap.add_argument("--jar", action="append", metavar="PATH",
                     help="要上传的 jar（可重复；相对路径按当前目录解析）。--update-file 模式下可省略")
-    ap.add_argument("--changelog", help="更新日志文件（默认自动取 release/<版本>/PLAYER_CHANGELOG*.md）")
+    ap.add_argument("--changelog",
+                    help="更新日志文件（给了就任何版本都附）；默认只对主线版本 X.Y.0 自动取 "
+                         "release/<版本>/PLAYER_CHANGELOG*.md，小版本（补丁/hotfix/预发布）默认不写")
     ap.add_argument("--changelog-type", default="markdown", choices=["markdown", "html", "text"])
-    ap.add_argument("--display-name", help="站点显示名（默认 = jar 文件名）")
+    ap.add_argument("--no-changelog", action="store_true",
+                    help="强制不附带 changelog（覆盖主线版本的默认附带；与 --changelog 互斥）")
+    ap.add_argument("--display-name",
+                    help="站点显示名（默认自动推导为 'Astral Dice <版本> (<加载器> <MC>)'，"
+                         "如 Astral Dice 1.5.0 (Fabric 1.20.1)）")
     ap.add_argument("--mc-version", help="覆盖从文件名解析出的 MC 版本")
     ap.add_argument("--loader", help="覆盖从文件名解析出的加载器（如 neoforge / forge）")
     ap.add_argument("--release-type", choices=["release", "beta", "alpha"],
````


### 附录 B：docs/agent/AGENTS-dsh.md 完整 diff


````
diff --git a/docs/agent/AGENTS-dsh.md b/docs/agent/AGENTS-dsh.md
index 6a033b24..09aab2f6 100644
--- a/docs/agent/AGENTS-dsh.md
+++ b/docs/agent/AGENTS-dsh.md
@@ -164,6 +164,7 @@
 - **覆盖全部渠道**：GitHub Release、CurseForge、任何对外分发点一视同仁；构建产物本地生成不算发布。
 - **违规处置**：若发现已在测试结论齐全之前发布 ⇒ 立即下架/归档该件并登记在案，不得用「后续补测通过」掩盖已发生的越序发布。
 - **发布前须取得用户许可（2026-11-02 补充）**：测试与实机取证完成后，先向用户汇报证据，**由用户明确点头后**才执行 push / tag / Release / CurseForge；用户未许可前不得对外发布，用户已撤回的发布件不得自行重新上传。
+- **CF 站点显示名与 changelog 口径（2026-11-02）**：上传件的显示名一律 `Astral Dice <版本> (<加载器> <MC>)`（加载器写 `NeoForge`/`Forge`/`Fabric`）；小版本更新（补丁 / hotfix / 预发布）不在 CF 写 changelog，只有主线版本 `X.Y.0` 才附带。
 
 项目基线:
 - 主线子项目 `neoforge-1.21.1`:MC 1.21.1 / NeoForge 21.1.235 / Java 21 / ModDevGradle(`net.neoforged.moddev` 2.0.141)
````


## 8. 未做 / 风险项

未做（按纪律）：

- 未上传任何 jar、未 `--apply`、未 commit / push / tag、未碰 GitHub Release、未改 CI 与 `tools/curseforge.json`。
- 未跑游戏内验证（本次只改发布工具的元数据口径，不涉及模组运行时行为）。

风险与注意：

1. **库产物（`starengine-*.jar`）不套用新显示名**，仍用 jar 文件名 —— 它推到 CurseForge 的**库项目**，命名口径与模组发布件不同。这是主动偏离，已在 :1072-1078 的代码注释与 :99-129 docstring 里写明；若以后要求库项目也用 `Astral Dice …` 格式，只需删掉 `is_lib_jar` 那一支。
2. **主线判定只看版本号字符串**（`^d+.d+.0$`）：`1.5.0` 算主线、`1.5.1` 不算。少数项目把 `X.Y.1` 当「新功能版」发布时，需要显式 `--changelog` 才写 CF changelog —— 这是规则的字面实现，不是 bug。
3. **P1 闸门在前**：fabric / 26.1.2 两条线的产物在算显示名之前就被 `guard_p0_only` 拒掉，要看到它们的 DISPLAY 必须显式 `--allow-p1`（本次验证即如此，且仍是 dry-run）。
4. **`--no-changelog` 与 `--changelog` 同给会直接报错**（`SystemExit`，:734-735），不静默取其一。
5. **工作树里有他人/其它会话的既存未提交改动**（`docs/agent/AGENTS-dsh-linux.md`、`docs/agent/AGENTS-full.md`、`docs/archive/card-recipes-2026-10-09/README.md`、fabric-1.20.1 下三个 java 文件），以及 `work/`、`.tmp/` 大量未跟踪文件 —— 一律未碰，按红线只改了自己那两个文件。
6. 本报告里的 dry-run 全部走了 `--proxy http://127.0.0.1:7897`；dry-run 本身不联网（版本表走本地缓存），联网路径未被本次验证覆盖。
7. 生成报告用的脚本与原始输出留在 `work/_cfname_probe/`（`gather.py` / `splice.py` / `report_body.md` / `out/*.txt` / `report_data/*`），均为未跟踪文件，可随时删除。

## 9. 结论

默认显示名现在按 jar 解析值自动推导为 `Astral Dice <版本> (<加载器> <MC>)`（加载器规范成 Fabric / Forge / NeoForge，`--display-name` 可覆盖），changelog 只在主线版本 `X.Y.0` 自动附带、小版本（补丁 / hotfix / 预发布）默认不写且 metadata 里连 `changelogType` 都不发，`--changelog` 显式指定仍生效、`--no-changelog` 可强制关闭，两处决策都在 dry-run 里各打一行供核对。
