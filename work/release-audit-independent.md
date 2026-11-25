# Astral Dice 1.5.0 发布验收 · 独立只读复核报告

- 仓库：`D:/MCProject/astral_dice_multiloader`（分支 `multi-main`，HEAD `33fe2f5e7eadf4e9b78ce8504b140e8d5b60f7cc`）
- 复核时间：2026-11-02（系统时区 Asia/Hong_Kong）
- 复核方式：**只读**。未执行任何 `git add/commit/push/tag`、未运行 Gradle、未启动游戏、未修改任何文件（本文件是本次唯一写入）。
- 分支/版本/状态一律以实读命令为准；取不到证据的条目显式写「无法判定」。
- 命令均在仓库根执行；四线简称：n121 = `neoforge-1.21.1`、forge = `forge-1.20.1`、n26 = `neoforge-26.1.2`、fabric = `fabric-1.20.1`。

## 结论总表

| # | 判据 | 判定 | 一句话理由 |
|---|---|---|---|
| 1 | 版本号单一事实源 | **PASS** | 四线 `mod_version` 与四个 jar 内元数据均为 `1.5.0+<后缀>`；四 jar 全条目扫描 `1.5.0-rc/beta/alpha` 命中 0 |
| 2 | 改名「深渊试剂」 | **PASS** | 四线 `src/main` 与四 jar 内「试剂-深渊」命中 0；12/12 语言值正确；手册 name 键指向 `item.astral_dice.abyss_reagent_chip`（范围外文档/测试资产仍有残余，见下） |
| 3 | 发布文档一致性 | **PASS** | 三件均为 1.5.0 且四线并列、无「只提供两个构建」类过时表述；parity 门禁 exit 0（`sections=26 entries=787/787`）；README 矩阵与四线版本一致 |
| 4 | CI 发布链路 | **PASS（3 处软风险）** | 四线同一 Release / tag=1.5.0（由 `1.5.0` 去 `+后缀` 得出）/ 正式版（`--latest --prerelease=false`）；无「只发两线」有效分支，但有 3 处不会报错的缺口 |
| 5 | MAPPING | **FAIL** | 基准线与结构成立（§2 共 31 项 / §4.1 R-01..R-17 / §4.2 U1–U9），但抽样 3 条中 1 条与当前源码**对不上**（n26「缺两张战斗牌」已不成立），且 §1.5 自述的 HEAD/脏树计数与实测不符 |
| 6 | 仓库卫生 | **PASS** | 判据括号范围（四线 `src/`、`.github/`、`release/1.5.0/`、`tools/`）内未跟踪或修改文件 **0 条**；范围外的 `docs/porting/MAPPING-1.5.0.md` 等见下方专项说明 |

---

## 判据 1 · 版本号单一事实源 —— PASS

### 1a 四线 gradle.properties

```
$ grep -n '^mod_version=' neoforge-1.21.1/gradle.properties forge-1.20.1/gradle.properties neoforge-26.1.2/gradle.properties fabric-1.20.1/gradle.properties
neoforge-1.21.1/gradle.properties:37:mod_version=1.5.0+neoforge_1.21.1
forge-1.20.1/gradle.properties:41:mod_version=1.5.0+forge_1.20.1
neoforge-26.1.2/gradle.properties:54:mod_version=1.5.0+neoforge_26.1.2
fabric-1.20.1/gradle.properties:29:mod_version=1.5.0+fabric_1.20.1
```

四条均以 `1.5.0+` 开头，无 `rc/beta/alpha` 后缀。
备注：`neoforge-26.1.2/gradle.properties:52` 有一行**注释**提到历史版本 `1.2.1-beta+neoforge_26.1.2`（注释，非生效值，不影响判定）。

### 1b 四个 jar 内 mod 元数据

jar 均位于仓库根 `build/libs/`：

| jar | 元数据文件 | 关键行 |
|---|---|---|
| `astral_dice-1.5.0+neoforge_1.21.1.jar` | `META-INF/neoforge.mods.toml` | `:17 version="1.5.0+neoforge_1.21.1"` |
| `astral_dice-1.5.0+forge_1.20.1.jar` | `META-INF/mods.toml` | `:21 version="1.5.0+forge_1.20.1"` |
| `astral_dice-1.5.0+neoforge_26.1.2.jar` | `META-INF/neoforge.mods.toml` | `:17 version="1.5.0+neoforge_26.1.2"` |
| `astral_dice-1.5.0+fabric_1.20.1.jar` | `fabric.mod.json` | `"id": "astral_dice"` / `"version": "1.5.0+fabric_1.20.1"` |

即 `version` 已展开为 `1.5.0+<后缀>`，与 gradle.properties 一致。

### 1c jar 内无 rc 残留

对四个 jar 的**全部条目**做字节扫描（不限 .json/.toml，含 class 常量池）搜 `1.5.0-rc` / `1.5.0-beta` / `1.5.0-alpha` / `1.5.0_beta`：

```
astral_dice-1.5.0+neoforge_1.21.1.jar    entries=2016  hits=0
astral_dice-1.5.0+forge_1.20.1.jar      entries=2032  hits=0
astral_dice-1.5.0+neoforge_26.1.2.jar   entries=2452  hits=0
astral_dice-1.5.0+fabric_1.20.1.jar     entries=2308  hits=0
```

⇒ 命中 0，因此**不需要**启用「mod 元数据 / lang 例外」这条豁免。

### 1d 「仓根 build/libs 的四个 jar 就是四线产物」的交叉验证

```
$ md5sum build/libs/*.jar neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar \
         forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar \
         neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar \
         fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar
811752022a4c887e7b0e8148bc89295c  build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar
ba200387359649d85d12243b2b8992ea  build/libs/astral_dice-1.5.0+forge_1.20.1.jar
7a7d6b5c5e5c6f330a2304e5d210bc09  build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar
bcb3ce3da986b0aa812e9160684a354e  build/libs/astral_dice-1.5.0+fabric_1.20.1.jar
811752022a4c887e7b0e8148bc89295c  neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar
ba200387359649d85d12243b2b8992ea  forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar
7a7d6b5c5e5c6f330a2304e5d210bc09  neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar
bcb3ce3da986b0aa812e9160684a354e  fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar
```

逐字节相同 ⇒ 根目录四 jar 确为四线最新产物，不是旧件拼装。

### 1e 本机目录里的旧件（不影响判据，但需知悉）

子项目 `build/libs/` 仍留有 pre-release 旧 jar，例如：

```
neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar
neoforge-1.21.1/build/libs/astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar
forge-1.20.1/build/libs/astral_dice-1.5.0-rc.7+forge_1.20.1.jar
neoforge-26.1.2/build/libs/astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar  （另有 1.2.1-beta ~ 1.3.7-beta 共 12 个）
```

这些是**本机残留**，CI 在清洁检出上构建不受影响；但只要上传/发布是从本机手工拿文件，就必须只取根 `build/libs/` 的四个（md5 见上）。

**判定：PASS**（version 单一事实源成立，四 jar 无 rc 残留）。

---

## 判据 2 · 改名「深渊试剂」 —— PASS（判据范围内），范围外有残余

### 2a 源码与 jar 内无旧名

```
$ grep -rn '试剂-深渊' neoforge-1.21.1/src/main forge-1.20.1/src/main neoforge-26.1.2/src/main fabric-1.20.1/src/main | wc -l
0
```

四个 jar 内 `.json/.toml/.txt/.md/.html/.js` 全条目解包扫描 `试剂-深渊`：**0 命中**。

### 2b 三语语言值（python json 解析 + 码点校验，四线 × 三语 = 12/12 全对）

| 线 | zh_cn（`item.astral_dice.abyss_reagent_chip`） | en_us | ja_jp |
|---|---|---|---|
| n121 | 深渊试剂 `zh_cn.json:379` | Abyss Reagent `en_us.json:379` | 深淵試薬 `ja_jp.json:378` |
| forge | 深渊试剂 `:385` | Abyss Reagent `:385` | 深淵試薬 `:384` |
| n26 | 深渊试剂 `:960` | Abyss Reagent `:960` | 深淵試薬 `:960` |
| fabric | 深渊试剂 `:995` | Abyss Reagent `:995` | 深淵試薬 `:995` |

码点核对：中文 `U+6DF1 U+6E0A U+8BD5 U+5242`（「深 渊 试 剂」，简体正确，非「試劑」）；日文 `U+6DF1 U+6DF5 U+8A66 U+85AC`（「深 淵 試 薬」，日本新字体正确）。
jar 内 `assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json` 同值。

### 2c Patchouli 手册引用键

四线均存在：

```
<线>/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/entries/chips_healing/abyss_reagent_chip.json
```

四线文件**逐字节一致**，关键字段：

```json
"name": "item.astral_dice.abyss_reagent_chip",
"category": "astral_dice:chips_healing",
"icon": "astral_dice:abyss_reagent_chip",
"spotlight": true, "sortnum": 9
```

⇒ name 仍指向该 item 键（`item.astral_dice.abyss_reagent_chip`），未漂移成 `...chip.abyss_reagent` 之类的其它键。
贴图文件名亦已同步为 `abyss_reagent_chip.png`（`textures/item/` 与 `textures/gui/chip_shop/chips/`，四线都有）。

### 2d 残余（**不在**判据 2 的 `src/main` 范围内，但发布前值得清）

`git grep -a 试剂-深渊` 仍命中：

- `docs/agent/AGENTS-full.md:1201`、`:1324`
- `docs/chip-recipe-plan.md:75,79`、`docs/chip-shop-logic-plan.md:55`
- `scripts/test/cases/PMFIX-TIP-1.20.1.json` 与 `PMFIX-TIP-1.21.1.json`（`:3/:13/:111/:271`）
- `scripts/test/resources/kubejs/1.20.1|1.21.1/client_scripts/astral_pmfix_client.js:4`
- 历史发布目录 `release/1.5.0-rc.1` … `release/1.5.0-rc.6` 的 `PLAYER_CHANGELOG_ZH.md:195/198`、`PLAYER_CHANGELOG_MCMOD.html:157/159/291/296`（**历史文档，不应改**）
- `AGENTS.md.bak-*`（备份，不应入库）

⇒ 上述均不进 jar、不进 `release/1.5.0/`，**不构成判据 2 的失败**；但 `docs/chip-*` 与测试用例属活文档，建议后续顺手订正。

**判定：PASS**（按判据 2 的口径：四线 `src/main` 与 jar 内无旧名、三语值正确、手册键指向正确）。

---

## 判据 3 · 发布文档一致性 —— PASS

### 3a `release/1.5.0/` 三件

```
$ ls -la release/1.5.0/
PLAYER_CHANGELOG_ZH.md     54256 B   Nov  2 05:56
PLAYER_CHANGELOG.md        56359 B   Nov  2 05:56
PLAYER_CHANGELOG_MCMOD.html 56817 B  Nov  2 05:56
$ git ls-files release/1.5.0/     # 三件均已入库
release/1.5.0/PLAYER_CHANGELOG.md
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html
release/1.5.0/PLAYER_CHANGELOG_ZH.md
```

三件均写 1.5.0 且四线并列：

- `PLAYER_CHANGELOG_ZH.md:3`「已发布：1.5.0（正式版）……四个 jar 同属一个 Release」
- `PLAYER_CHANGELOG.md:3`「Released: 1.5.0 (stable)」
- `PLAYER_CHANGELOG_MCMOD.html:1` `<h1>星之骰戏 (Astral Dice) 1.5.0 玩家更新日志</h1>`；`:3` 列四条平台线；`:14`「本版同时发布四个构建」+ 四个 jar 名 + 「tag 为 1.5.0」

### 3b 过时表述排查

```
$ grep -nE '两个构建|两个版本|只提供|双线|两条线|two builds|only two|两个加载器' release/1.5.0/PLAYER_CHANGELOG_ZH.md release/1.5.0/PLAYER_CHANGELOG_MCMOD.html
ZH:20  「补齐两条线各自的零星缺口」          ← 指 fabric + 26.1.2 两条新线，非「只发两线」
ZH:373 「1.20.1 两条线用铁砧代替重锤」        ← 指 forge + fabric 的 1.20.1 行为
HTML:17 / :351 对应同样两处
```

除这两处语义无关表述外，**不存在**「只提供两个构建」这类过时表述。

### 3c parity 门禁

```
$ PYTHONIOENCODING=utf-8 python3 tools/check_changelog_parity.py
CHANGELOG_PARITY: OK sections=26 entries=787/787
WARN P2 CHANGELOG 1.3.0 / 2.0.0-SNAPSHOT.5: bold-start count differs (historical style difference; not a failure)
$ echo $?
0
```

exit code = **0**；另有 2 条 WARN，脚本自注为历史样式差异、不计失败。

### 3d README 支持矩阵

`README.md` / `README_ZH.md` `L74-77` 四行与四线 `gradle.properties` 一致：

| 线 | MC | 加载器 | JDK | 版本 |
|---|---|---|---|---|
| neoforge-1.21.1 | 1.21.1 | NeoForge 21.1.235 | 21 | 1.5.0 |
| forge-1.20.1 | 1.20.1 | Forge 47.4.10 | 17 | 1.5.0 |
| neoforge-26.1.2 | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.5.0 |
| fabric-1.20.1 | 1.20.1 | Fabric Loader 0.19.5 | 17 | 1.5.0 |

`L79`「四条线都是正式发布线……tag 用不带后缀的基础版本号（本版 1.5.0）……发布类型是正式版」；`L116/117` 的 tag 规则与 CurseForge 口径一致。

附带核对（StarEngine 内嵌口径）：README / 发布说明称「26.1.2 与 fabric 仍内置」。实物：`n121 = 0`、`forge = 0`、`n26 = 3`（`META-INF/jarjar/metadata.json` + `starengine-neoforge-26.1.2-1.1.0.jar`）、`fabric = 1`（`META-INF/jars/starengine-fabric-1.20.1-1.1.0.jar`，Loom 形态）。

> 说明（非失败项）：文档统一用「jarjar 内嵌」一词，对 fabric 并不精确（走 Loom 的 `META-INF/jars/`）。语义结论「仍内置」成立。

**判定：PASS**。

---

## 判据 4 · CI 发布链路 —— PASS（含 3 处软风险）

`.github/workflows/build.yml` 共 351 行。

### 4a tag 规则

```yaml
5:    branches:  'multi-main' / 'multi-dev-next' / 'multi-26.1.2-neoforge'
19:    tags:
20-22     # 发布规范：tag 只按 mod 版本创建（形如 1.5.0 / 1.5.0-rc.1 / 1.5.0-pre.6），
          #   禁止平台前缀、禁止 v 前缀、禁止 +加载器 后缀；同一版本多平台 jar 放进同一个 Release
23:      - '[0-9]*'
```

`Read base version`（`:199-`）：

```
VERSION=$(grep '^mod_version=' neoforge-1.21.1/gradle.properties | cut -d'=' -f2)   # 1.5.0+neoforge_1.21.1
BASE=${VERSION%%+*}                                                                 # 1.5.0
# BASE 含 '-' ⇒ is_prerelease=1，否则 0        （:206-211）
# 三段点号版本正则（可选 -pre 段）⇒ skip_tag   （:212-218）
```

⇒ 1.5.0 走 `BASE=1.5.0` → `is_prerelease=0` → `skip_tag=0` → tag 名 `1.5.0`（`:225-` 的 `Create Git tag`，仅 `refs/heads/multi-main` 且 `skip_tag=='0'` 时执行）。

### 4b 四线 jar 收集

```yaml
178: uses: actions/upload-artifact@v4
180:   name: astral_dice-jars
181:   path: neoforge-1.21.1/build/libs/astral_dice-*.jar
          forge-1.20.1/build/libs/astral_dice-*.jar
          neoforge-26.1.2/build/libs/astral_dice-*.jar
190: uses: actions/upload-artifact@v4
191:   if: hashFiles('fabric-1.20.1/gradle.properties') != ''
192:   name: astral_dice-jars-fabric
193:   path: fabric-1.20.1/build/libs/astral_dice-*.jar
```

### 4c Release 步骤

```yaml
241: - name: Create/Update GitHub Release (four JARs, notes from release/<tag>/)
     if: startsWith(github.ref,'refs/tags/') || (push && multi-main && steps.tag.outputs.tag != '')
267:   # 缺任一即 ::error:: + exit 1（宁可不发布，也不发缺件/错件）
271-272 echo "::error::required P0 jar not found: $p" ; exit 1      ← 只硬校验 P0 两条线
276-285 for p in 四线 glob ; if [[ -f "$p" ]]; then JARS+=("$p")   ← 四线收集，未命中即跳过
286: NOTES_ZH="release/$TAG/PLAYER_CHANGELOG_ZH.md"
287: NOTES_EN="release/$TAG/PLAYER_CHANGELOG.md"
312: else ::warning:: no release/$TAG/PLAYER_CHANGELOG*.md found — fallback to attachment list
315-319 if is_prerelease==1 ⇒ REL_KIND=(--prerelease --latest=false)
                     else    ⇒ REL_KIND=(--latest --prerelease=false)
325-341 gh release view → edit，否则 create；附件 grep -Fxq 判重后只补传缺失（无 --clobber）
```

⇒ 与「四线同一 Release / tag=1.5.0 / 正式版（非 prerelease）」**一致**。workflow 注释里 2026-10-25「只发 P0 两线」裁决已显式标注被 2026-11-02 新指令取代，文件内**没有**仍会生效的「只发两线」分支。

### 4d 软风险（不构成本条判据失败，但发布前应知）

1. **26.1.2 / fabric 缺件不会报错**：`:271` 只对 n121 与 forge 做硬校验；`:276-285` 的 glob 未命中时那两条线**静默缺席**，Release 仍会成功创建。若某次 26.1.2 或 fabric 构建产物命名变化（例如加了 `-sources` 分类器或改了 `archivesBaseName`），会出现「发出去只有两个 jar 却一切正常」。
2. **`[0-9]*` 同时匹配 rc/pre tag**：设计上允许（同一 Release 规则），但意味着 `1.5.0` 与 `1.5.0-rc.n` 完全共用一条流水线，二者只靠「BASE 是否含 `-`」区分 prerelease。若将来出现 `1.5.0+build.2` 之类，`BASE=${VERSION%%+*}` 会把它归为正式版。
3. **`PLAYER_CHANGELOG_MCMOD.html` 未被 CI 引用**：Release 正文只取 ZH + EN 两份 md；MCMod 用的 HTML 需手工上传。若这一版要求「三件齐发」，CI 侧缺口在 MCMOD HTML 上。

**判定：PASS**（链路与目标口径一致；3 处软风险如上）。

---

## 判据 5 · MAPPING —— FAIL

### 5a 文件与入库状态

```
$ wc -l docs/porting/MAPPING-1.5.0.md ; md5sum …
332 docs/porting/MAPPING-1.5.0.md      62024 B   Nov  2 05:58   8f36bc979026648837d392e9b4344569
$ git ls-files docs/porting/ | wc -l
0                                     ← 未入库
$ md5sum porting/fabric-1.20.1/MAPPING-1.5.0.md
18f81e16085152a378189277dc0e61bc      117802 B  Nov  1 17:19   ← 另一份、内容不同、同样未入库
```

### 5b 基准线与结构（成立）

§1.1 表格首行明确写「**基准线** `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | Java 21」，其余三线标「对照 ①/②/③」⇒ **以 neoforge-1.21.1 为基准**成立。

清单结构与数量（实测行数）：

| 节 | 内容 | 数量 |
|---|---|---|
| §1 | 口径与范围（1.1 四线与版本事实 / 1.2「同功能」三项判定口径 / 1.3 差异性质五词：等价映射·平台替代·裁剪·缺失·待验证 / 1.4 取证边界 / 1.5 工作树状态） | 5 小节 |
| §2 | 差异总表（功能域 / 基准线落点 / forge / n26 / fabric / 差异性质） | **31 项**（`| 1 |` … `| 31 |`） |
| §2.1 | 需要额外说明的四条（饰品三种数据契约 / fabric 换注入点 / n26 两处结构换代 / fabric shim 层） | 4 条 |
| §3 | 等价映射速查表 | **27 项** |
| §4.1 | 风险条目 R-01 … R-17 | **17 条** |
| §4.2 | fabric 未收口项：C-3（已收口）/ P-5（已定位未裁决）/ U1–U9 | 3 + 9 条 |
| §5 | 数据与资源差异（5.1 三条不齐 / 5.2 / 5.3） | 3 小节 |
| §6 | 未验证项（静态无法判定，须实机）+ 6.1 26.1.2 受测模组集 | 9 条 U + 1 记录 |
| 附录 A | 关键文件清单 | 19 行 |

⇒ 「同功能实现差异项清单」在**数量与结构**上成立，且差异性质严格限定为五个词。

### 5c 抽样 3 条到源码核对

#### 抽样 A —— §2 第 21 行 / §4.1 R-08「forge 与 fabric 缺 Jade `ShopCooldownServerDataProvider`」→ **对上**

```
$ for d in n121 forge n26 fabric; do ls $d/src/main/java/com/merlinkitsune/astral_dice/compat/jade/; done
n121 : AstralDiceJadePlugin / EventDrawerCostProvider / ShopCooldownClientProvider / ShopCooldownServerDataProvider   (4)
forge: AstralDiceJadePlugin / EventDrawerCostProvider / ShopCooldownClientProvider                                     (3)
n26  : AstralDiceJadePlugin / EventDrawerCostProvider / ShopCooldownClientProvider / ShopCooldownServerDataProvider   (4)
fabric: AstralDiceJadePlugin / EventDrawerCostProvider / ShopCooldownClientProvider                                    (3)
```

与文档「n121/n26 各 4 个、forge/fabric 各 3 个（无该 provider）」完全一致。
附带发现（文档未记）：`astral_dice.jade.mixins.json` 的 client 数组，n121/n26 为 `DisplayHelperArmorIconMixin`，而 **forge 与 fabric 同为** `ArmorElementIconMixin`。R-04 只把该差异归因于 fabric，forge 侧同样不同名——属文档**欠记**，不影响结论方向。

#### 抽样 B —— §2 第 29 行 / §4.1 R-16「基准线与 n26 有 `META-INF/enumextensions.json`，forge 与 fabric 无」→ **对上**

```
neoforge-1.21.1: neoforge-1.21.1/src/main/resources/META-INF/enumextensions.json      （存在）
forge-1.20.1:    ls: cannot access '…/META-INF/enumextensions.json': No such file    （不存在）
neoforge-26.1.2: neoforge-26.1.2/src/main/resources/META-INF/enumextensions.json      （存在）
fabric-1.20.1:   ls: cannot access '…/META-INF/enumextensions.json': No such file     （不存在）
```

fabric 走库内枚举的旁证：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/ChipShopClientState.java:4` = `import com.merlinkitsune.starengine.item.Rarity;`，与文档表述一致。

#### 抽样 C —— §2 第 2 行 / §4.1 R-05 / §4.2 P-5-①「n26 缺 2 个战斗牌常量」（文档给 `grep 计数 10/10/9/10`、`9/9/8/9`，并称 n26 `ModCreativeTabs.java:66`（`FULL_POWER`）之后**直接是** `:67` `// 防御牌`）→ **对不上**

```
$ for d in n121 forge n26 fabric; do echo -n "$d "; grep -rn 'ATTACK_CARD_BITE' $d/src/main/java | wc -l; done
neoforge-1.21.1 10   forge-1.20.1 10   neoforge-26.1.2 10   fabric-1.20.1 10        ← 文档称 n26 = 9
$ for d in n121 forge n26 fabric; do grep -rn 'ATTACK_CARD_DRAGON_ROAR' $d/src/main/java | wc -l; done
neoforge-1.21.1 9    forge-1.20.1 9    neoforge-26.1.2 9    fabric-1.20.1 9         ← 文档称 n26 = 8

$ grep -n 'FULL_POWER|ATTACK_CARD_BITE|DRAGON_ROAR|防御牌' neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/init/ModCreativeTabs.java
66:  output.accept(ModItems.ATTACK_CARD_FULL_POWER.get());
67:  // 蛟龙立牌(mamushi)专属战斗牌(2026-09-27):撕咬 / 龙之咆哮
68:  output.accept(ModItems.ATTACK_CARD_BITE.get());
69:  output.accept(ModItems.ATTACK_CARD_DRAGON_ROAR.get());
70:  // 防御牌
（n121 同文件行号完全相同：66/67/68/69/70）

$ grep -n 'ATTACK_CARD_BITE|ATTACK_CARD_DRAGON_ROAR' neoforge-26.1.2/…/item/ModItems.java
333: public static final DeferredItem<Item> ATTACK_CARD_BITE = registerItem("attack_card_bite", …
342: public static final DeferredItem<Item> ATTACK_CARD_DRAGON_ROAR = registerItem("attack_card_dragon_roar", …
```

并且该结论在 **HEAD 上已不成立**（不是「工作树脏」造成的差异）：

```
$ git show HEAD:neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/init/ModCreativeTabs.java | grep -n 'BITE|DRAGON_ROAR|防御牌'
68:  output.accept(ModItems.ATTACK_CARD_BITE.get());
69:  output.accept(ModItems.ATTACK_CARD_DRAGON_ROAR.get());
70:  // 防御牌
$ git log --oneline -S'ATTACK_CARD_BITE' -- neoforge-26.1.2/…/init/ModCreativeTabs.java
12de194e fix(26.1.2): declare event sounds, register waystone events through Balm, restore two dragon cards
```

`12de194e` 是 HEAD 的**祖先**（9 个 commit 之一）。⇒ R-05 / P-5-① / §2-第 2 行这三处**在文档写作时点即已过时**（或因取证的是更早的 `fd990c15` 快照而残留），当前源码里 n26 **已经有**这两张牌，四线计数为 `10/10/10/10` 与 `9/9/9/9`。
连带影响：P-5 把「n26 缺两张牌」列为**待裁决的内容面差额**，这一半其实已由 `12de194e` 关闭，文档的「待裁决」清单已失真。

### 5d 文档自述的树状态失真

§1.5 写「工作树当前是脏的（`git status`：45 删除 / 262 修改 / 373 未跟踪，HEAD = `fd990c15` 2026-11-01 14:42）」。实测：

```
$ git rev-parse --short HEAD        → 33fe2f5e
$ git status --porcelain | grep -c '^ M'   → 3
$ git status --porcelain -uall | grep -c '^??' → 409
```

⇒ 文档写的 HEAD 与脏树计数均与仓库现状不符（且 45 个「删除」在现状中为 0）。这既影响 §1.5 表格里的「工作树 / HEAD 双口径」计数（配方数、立牌数、生成资源数等），也说明该文档至少有一部分是按更早快照写的。

**判定：FAIL**。理由：基准线选择、清单数量与结构成立（31 项差异 / 17 条风险 / 9 条待实机），但 3 条抽样里有 1 条与源码**直接相反**，且是「结论级」错误（判 n26 缺内容、条件已修复而文档未跟进）；同时文档自述的 HEAD 与脏树计数已与仓库不符。修复方向是**改文档**（不是改代码），并把 §2/§4.1/§4.2 的 n26 条目重新取证一遍。

---

## 判据 6 · 仓库卫生 —— PASS（判据括号范围内），范围外另有必须入库项

### 6a 判据括号范围（四线 `src/`、`.github/`、`release/1.5.0/`、`tools/`）

```
$ git status --porcelain -uall -- neoforge-1.21.1/src forge-1.20.1/src neoforge-26.1.2/src fabric-1.20.1/src .github release/1.5.0 tools
（无输出）
```

⇒ 判据指定范围内**没有任何**未跟踪或已修改的产品文件。

### 6b 四线目录下（非 `src/`）的未跟踪文件 —— 均非产品文件

```
?? fabric-1.20.1/.tmp-case-20261102-055012.log
?? fabric-1.20.1/.tmp-case-20261102-055130.log
?? fabric-1.20.1/.tmp-case-20261102-060044.log
?? fabric-1.20.1/.tmp-case-20261102-060223.log
?? fabric-1.20.1/.tmp-case-hoverfix2.log
?? fabric-1.20.1/.tmp-case-hoverfix3.log
?? fabric-1.20.1/.tmp-runclient.log
```

全部为测试台（`scripts/test`）运行产生的 `.tmp-*` 日志，运行期产物 ⇒ **可以不入库**（建议加进 `.gitignore` 或清理，避免 `git status` 噪音）。

### 6c 全局状态

```
$ git status --porcelain | grep -c '^ M'          → 3
$ git status --porcelain -uall | grep -c '^??'    → 409
$ git status --porcelain | grep '^ M'
 M docs/agent/AGENTS-dsh-linux.md
 M docs/agent/AGENTS-full.md
 M docs/archive/card-recipes-2026-10-09/README.md
```

3 个已修改文件全是 `docs/`（规则/归档文档），**不属于**判据 6 的括号范围，也不进 jar。

### 6d 范围外但**性质上必须入库**的文件（单独列出，供发布决策）

| 文件 | 未跟踪 | 为何属「必须入库」 |
|---|---|---|
| `docs/porting/MAPPING-1.5.0.md` | ✅ | 1.5.0 的权威 mapping 唯一产物（文档自述「权威 mapping（四线对照）／ 唯一产物」），判据 5 的被审对象本身；不入库则该审计对象在发布版本里不存在、无法追溯 |
| `porting/fabric-1.20.1/MAPPING-1.5.0.md` | ✅ | 同主题的另一份（117 KB），内容与上一份不同；至少要让读者能判断哪份是正本 |

其余未跟踪项（`-unormal` 折叠）：`work/` 136、`porting/fabric-1.20.1/` 64、`scripts/test/cases/` 15、`scripts/test/lib/` 7、`scripts/test/` 4、`docs/features/` 4、`docs/` 3；仓根散件 `nul`、`javac.20261101_*.args`（14 个）、`_b20a_jarinfo.py`、`_fabric_remap.log`、`_sched.bat`、`_tmp_b11_*.py`、`acl-backup-20261101-153946.txt`、`tmptekih3ja.sh`、`zz_screen_probe.py`。

- `work/` 与上述散件属**取证/调试草稿**，可以不入库；但 `work/` **未被 .gitignore 忽略**（`git check-ignore work/...` 无输出），导致 `git status` 长期挂 136 条噪音——建议加入 `.gitignore`。
- `scripts/test/cases|lib` 属测试台资产，是否入库取决于工程口径（本报告不裁决）；注意 `PMFIX-TIP-*.json` 里仍带旧名「试剂-深渊」（见判据 2d）。
- 异常项：仓根存在字面目录 `%SystemDrive%/ProgramData/Microsoft/Windows/Caches/`（内含 4 个未跟踪 `.db`），疑似某脚本环境变量未展开误建，建议确认后删除，**不要入库**。
- `build/` 已被 `.gitignore:60` 忽略（因此 `build/libs` 的四 jar 不入库，属正常发布口径）。

**判定：PASS**（判据括号范围内 0 条必须入库而未提交的产品文件）；范围外的 MAPPING 两份见 6d。

---

## 附：需要决策 / 建议处理的清单（按优先级）

1. **【高】`docs/porting/MAPPING-1.5.0.md` 与 `porting/fabric-1.20.1/MAPPING-1.5.0.md` 未入库**，且前者含已过时结论（n26 战斗牌、HEAD 与脏树计数）。入库前应先订正，或至少裁掉过时段落。
2. **【中】CI 对 26.1.2 / fabric 缺件静默**（`build.yml:271` 只硬校验 P0 两线）。若要「四线同发」成为硬约束，建议把 P0 校验扩成四线（或至少把 26.1.2 并入硬校验）。
3. **【中】`PLAYER_CHANGELOG_MCMOD.html` 不在 CI 取用范围**，需在发布流程里单列一步（手工上传）。
4. **【低】判据 2 的残余旧名**在 `docs/chip-*`、`scripts/test/cases/PMFIX-TIP-*.json`、`scripts/test/resources/kubejs/**/astral_pmfix_client.js`。
5. **【低】仓库卫生**：`work/` 未忽略、仓根 `%SystemDrive%/` 误建目录、`nul` 文件、`_tmp_*` 草稿；`fabric-1.20.1/.tmp-*.log` 建议忽略。
6. **【信息】本机 `build/libs` 含 pre-release 旧 jar**：手工上传时务必只取根 `build/libs/` 的四个（md5 见判据 1d）。

## 实机依赖声明

本报告 6 条判据**均可用静态证据判定**，未出现只能靠实机才能下的结论。唯一与实机有关的是 MAPPING §6 的 U1–U9（观感 / 同步时序 / 槽位 resize / 跨线协议等 9 项）——它们不属于本次 6 条验收判据，报告不对其作判定。
