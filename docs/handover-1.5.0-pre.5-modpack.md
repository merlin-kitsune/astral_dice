# 交接 — 1.5.0-pre.5 已推送到两个 P0 整合包（2026-10-22）

> 本文是「本仓 → 整合包会话」的交接单。本仓（`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`）这边的活已收口，
> 余下在整合包会话继续；下面每条都带可复算的命令与读数。

## 0 一句话

`1.5.0-pre.5` 已推送两个 P0 整合包并逐项核验（字节 / sha256 / jar 内依赖区间 / jarjar 计数 / 关键类 / 效果图标 md5 / 诊断探针 0 残留）；
本仓 pre.4 的全量实机（P0 双线各 9 条机器判据 + 1 条截图取证）已于同日 **PASS**；**pre.5 本批（指示器改动）尚未实机**（见 §5-3）。余下工作见 §5。

## 1 投放结果（`Get-Item` + `Get-FileHash` 实测，2026-10-22 22:33，含 pre.5 指示器批）

| 整合包 | 文件 | 字节 | sha256 | 对 `build/libs` |
|---|---|---|---|---|
| `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods` | `astral_dice-1.5.0-pre.5+neoforge_1.21.1.jar` | 2,559,837 | `f25546b638892527…76c2138fa` | **MATCH** |
| 同上 | `starengine-neoforge-1.21.1-1.1.0-pre.2.jar` | 98,733 | `c0fc596c…` | 成对库在位 |
| `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods` | `astral_dice-1.5.0-pre.5+forge_1.20.1.jar` | 2,605,382 | `e30980b4e8dfa6dd…45de6fab79` | **MATCH** |
| 同上 | `starengine-forge-1.20.1-1.1.0-pre.2.jar` | 103,604 | `f395939a…` | 成对库在位 |
| 同上 | `AutoJS-forge-1.20.1-1.0.0.jar` | 167,358 | `39cdc693…` | 整合包用 **reobf**（非 `-dev` named） |

- 两个包各自**只有一行** `astral_dice-*` 与一行 `starengine-*`（同名覆盖即完成升级；四个投放目标里的旧 `astral_dice-1.5.0-pre.*` 已先删除，无历史版本残留）。
- 缺库即启动失败：`Missing or unsupported mandatory dependencies`（库区间 `[1.1.0-pre.2,2.0.0)`，**不能**写成 `[1.1.0,2.0.0)`）。
- 本仓 `run/1.20.1/mods` 放的是 `build/devlibs` 的 named jar（2,575,899 B `86c6350b…`），**不要**拿它当整合包来源（§3-1）。
- 整合包里本次诊断注入的测试资产已清除干净：`kubejs/server_scripts/` 下的 `astral_bugfix_probe.js` / `astral_fixbatch_probe.js` / `zz_lpfx_drive2.js` 已删，世界副本 `saves/lp-fx-test` 已删，用户原世界 `saves/新しい世界` 未动。

## 2 jar 内自检（`tar -xOf` / `tar -tf`）

- neo：`version="1.5.0-pre.5+neoforge_1.21.1"`、`modId="starengine"` 配 `versionRange="[1.1.0-pre.2,2.0.0)"`、
  `META-INF/jarjar/` 计数 **0**、`item/card/BaseEffectCardItem.class` = 1、`item/sign/BonnieSignItem$1.class` = 1（立牌冷却补丁在匿名内部类里）、
  `command/AstralPartyCommand.class` 含 `killbarricade` / `killrepulsion` / `AFTERM`（pre.4 新增）。
  `item/sign/LuluSignItem` 的 `HURT_COOLDOWN_TICKS` 常量 = **300**（15 秒；本批由 20 改来，`javap -p -c -constants` 可取）。
- forge：`version="1.5.0-pre.5+forge_1.20.1"`、`loaderVersion="[47,48)"`、同一 starengine 区间、jarjar **0**、关键类同上。
  ⚠️ forge 侧 `loaderVersion="[47.4.10,48)"` 会被 FML 判 `fml.language.missingversion` 拒载 ⇒ 只能是 `[47,48)`。
- 实体拆除口径（pre.4 的核心改动，字节码取证）：neo `entity/RepulsionEntity` 含 `hurt(DamageSource,float)` / `getHurtSound(DamageSource)` / `die(DamageSource)` / **`kill()`**，
  `entity/BarricadeEntity` 含 `die` / **`kill()`** / `remove(RemovalReason)`；forge reobf 对应 SRG = `m_6469_` / `m_7975_` / `m_6667_` / **`m_6074_`**（kill）/ `m_142687_`。
- 11 张 `assets/astral_dice/textures/mob_effect/*.png` 与 `images/shop/unbord_chips/` 源文件 **md5 全等**（两线各 11，mismatch 0）；pre.5 新增的 `whetstone_guard_cd.png`(410 B) / `airbag_guard_cd.png`(483 B) 两线俱在，`railgun_ready.png` 已随「删电磁炮就位指示器」删除（0 个）。
- **诊断探针 0 残留**：`client/ClientFrameProbe.class` 0 个、字节码含 `AP_FP_` 的 class 0 个（pre.5 是剔除探针后的干净重建，22:32）。
- 库 jar 自称版本：`1.1.0-pre.2+neoforge_1.21.1` / `1.1.0-pre.2+forge_1.20.1`。

## 3 三个别踩的坑（本仓踩过）

1. **dev 与整合包的 jar 不可互换**：forge dev 必须 `build/devlibs` 的 named jar（reobf 的 SRG 调用在 dev 抛 `NoSuchMethodError`），
   整合包必须 reobf jar；两者**不可同目录**。
2. **每次重建 jar 后必须重新投放并核 sha256** —— 只看 `ls` 到同名就当成新构建，曾导致一整轮实机读数作废。
3. **P1 两条线（26.1.2 / fabric）1.5.0 正式版前不验证、不移植**，整合包侧不要顺手升级它们（26.1.2 产物仍停在 `1.3.7-beta.2`）。
   本批的效果图标也只换了 P0 两线，P1 两线仍是带边框版（本仓已登记欠账）。

## 4 本仓侧状态（供对账）

- 分支 `multi-main`；pre.4 已 push（`origin/multi-main` = `353cfed4`），**pre.5 本批改动随本文件一起提交**，提交后未推送 1 个；无 tag（默认不动，需用户放行）。
- 守门 8 跑 7 绿，唯一红 = `verify_probe_class_refs` 剩 1 处（`neoforge-26.1.2/build/libs` 仍是 1.3.7-beta.2 陈旧产物，P1 待重建）。
- 实机：pre.4 全量实机 P0 双线**各 9 条机器判据 PASS + 1 条截图取证**（2026-10-22 19:00–19:53，用例 `PRE4-*`）；pre.5 指示器批**尚未实机**；整合包内激光卡顿取证结论 = 非光束渲染（有/无光束帧时长分布一致）。
- 用例：`scripts/test/cases/CARD-NOCOST-CREATIVE-1.20.1.json`（46 steps / 10 asserts，`channel=kubejs`）。

## 5 交给整合包会话的事（建议顺序）

1. 两个实例各冷启动一次，确认日志无 `Missing or unsupported mandatory dependencies`、无 mixin/registry 报错。
2. 重点看 pre.4 与 **AutoJS / kubejs** 的共存（forge 包内 `AutoJS-forge-1.20.1-1.0.0.jar` 为 reobf 版）。
3. **验 pre.4 的实体拆除口径（本仓未实机）**：先 `/astralparty killrepulsion all` 清掉旧世界里可能残留的强化拒止 →
   重新放一个强化拒止 → 用任意武器砍一刀（应按实际伤害扣耐久，至少 1 点，且怪物/环境伤害对它无效）→ 砍到归零（应在**同一 tick 消失**，
   没有生物式躺倒 / 变红动画）。路障同理：`/astralparty killbarricade all`。遗留实体杀不掉时优先用这两条命令（原版 `/kill` 在老版本里对它们无效，
   pre.4 已覆写 `kill()` 才生效）。
4. 若要验创造扣卡行为：需要在**装骰子**的前提下右键出牌（未佩骰子时选择确认会静默不执行 action）。
5. 报问题请附：`logs/latest.log` 片段 + mods 目录里两行 jar 的 sha256，便于与本文件 §1 逐字对账。
