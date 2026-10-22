# 交接 — 1.5.0-pre.3 已推送到两个 P0 整合包（2026-10-22）

> 本文是「本仓 → 整合包会话」的交接单。本仓（`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`）这边的活已收口，
> 余下在整合包会话继续；下面每条都带可复算的命令与读数。

## 0 一句话

`1.5.0-pre.3` 已推送两个 P0 整合包并逐项核验（字节 / sha256 / jar 内依赖区间 / jarjar 计数 / 关键类）；
本仓本批的实机验证（1.20.1 创造模式出牌扣卡）已于同日 **PASS**。余下工作见 §5。

## 1 投放结果（`Get-Item` + `Get-FileHash` 实测，2026-10-22 16:2x）

| 整合包 | 文件 | 字节 | sha256 | 对 `build/libs` |
|---|---|---|---|---|
| `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods` | `astral_dice-1.5.0-pre.3+neoforge_1.21.1.jar` | 2,555,654 | `f063102b53f171b6…04c2ed` | **MATCH** |
| 同上 | `starengine-neoforge-1.21.1-1.1.0-pre.2.jar` | 98,733 | `c0fc596c…` | 成对库在位 |
| `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods` | `astral_dice-1.5.0-pre.3+forge_1.20.1.jar` | 2,601,208 | `7bf787dff9e64985…c167d1c` | **MATCH** |
| 同上 | `starengine-forge-1.20.1-1.1.0-pre.2.jar` | 103,604 | `f395939a…` | 成对库在位 |
| 同上 | `AutoJS-forge-1.20.1-1.0.0.jar` | 167,358 | `39cdc693…` | 整合包用 **reobf**（非 `-dev` named） |

- 两个包各自**只有一行** `astral_dice-*` 与一行 `starengine-*`（同名覆盖即完成升级，无历史版本残留）。
- 缺库即启动失败：`Missing or unsupported mandatory dependencies`（库区间 `[1.1.0-pre.2,2.0.0)`，**不能**写成 `[1.1.0,2.0.0)`）。

## 2 jar 内自检（`tar -xOf` / `tar -tf`）

- neo：`version="1.5.0-pre.3+neoforge_1.21.1"`、`modId="starengine"` 配 `versionRange="[1.1.0-pre.2,2.0.0)"`、
  `META-INF/jarjar/` 计数 **0**、`item/card/BaseEffectCardItem.class` = 1、`item/sign/BonnieSignItem$1.class` = 1（立牌冷却补丁在匿名内部类里）。
- forge：`version="1.5.0-pre.3+forge_1.20.1"`、`loaderVersion="[47,48)"`、同一 starengine 区间、jarjar **0**、关键类同上。
  ⚠️ forge 侧 `loaderVersion="[47.4.10,48)"` 会被 FML 判 `fml.language.missingversion` 拒载 ⇒ 只能是 `[47,48)`。
- 库 jar 自称版本：`1.1.0-pre.2+neoforge_1.21.1` / `1.1.0-pre.2+forge_1.20.1`。

## 3 三个别踩的坑（本仓踩过）

1. **dev 与整合包的 jar 不可互换**：forge dev 必须 `build/devlibs` 的 named jar（reobf 的 SRG 调用在 dev 抛 `NoSuchMethodError`），
   整合包必须 reobf jar；两者**不可同目录**。
2. **每次重建 jar 后必须重新投放并核 sha256** —— 只看 `ls` 到同名就当成新构建，曾导致一整轮实机读数作废。
3. **P1 两条线（26.1.2 / fabric）1.5.0 正式版前不验证、不移植**，整合包侧不要顺手升级它们（26.1.2 产物仍停在 `1.3.7-beta.2`）。

## 4 本仓侧状态（供对账）

- 分支 `multi-main`，HEAD `b57abbd7`（本批实机用例 + 交接刷新）；未 push、无 `1.5.0-pre.3` tag（默认不动，需用户放行）。
- 守门 8 跑 7 绿，唯一红 = `verify_probe_class_refs` 剩 1 处（`neoforge-26.1.2/build/libs` 仍是 1.3.7-beta.2 陈旧产物，P1 待重建）。
- 实机：1.20.1 本批 6/6 PASS、1.21.1 49/49 PASS、**1.20.1 创造模式出牌扣卡 PASS**（创造扣 0、生存对照扣 1）。
- 用例：`scripts/test/cases/CARD-NOCOST-CREATIVE-1.20.1.json`（46 steps / 10 asserts，`channel=kubejs`）。

## 5 交给整合包会话的事（建议顺序）

1. 两个实例各冷启动一次，确认日志无 `Missing or unsupported mandatory dependencies`、无 mixin/registry 报错。
2. 重点看 pre.3 与 **AutoJS / kubejs** 的共存（forge 包内 `AutoJS-forge-1.20.1-1.0.0.jar` 为 reobf 版）。
3. 若要验创造扣卡行为：需要在**装骰子**的前提下右键出牌（未佩骰子时选择确认会静默不执行 action）。
4. 报问题请附：`logs/latest.log` 片段 + mods 目录里两行 jar 的 sha256，便于与本文件 §1 逐字对账。
