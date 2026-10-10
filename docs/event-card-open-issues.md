# 事件效果卡 · 现有问题记录（2026-10-10，等待用户下一步指示）

> 状态：**未提交**（HEAD = `6d52df32`，工作区 ~58 项改动）；两线均可编译（`.63`）。
> 客户端：PID **29044** 运行中（20:04:42 启动，装的是**含旧打点**的 jar）；`run/1.21.1/saves/testworld` 保留；`.mt_keep_alive` 已落。

## A. 待实机确认的修复（已改代码、未验证）

1. **[已修·待验证] 卡框与事件图不随 alpha 淡入淡出**
   - 现象（逐 tick 连拍量测）：缩放 ✓ 与文字 alpha ✓ 都严格按源码，但**卡框/事件图 blit 恒为不透明**（源 α 0.15→1.00，实测 α_eff 恒 1.000；t=1370 时文字只剩 19% 亮度而卡面仍是纯色）。观感 = 不透明弹出/消失 + 只缩小 + 只文字渐隐。
   - 根因：`GuiGraphics` 是**批处理**渲染，shader 颜色在 **flush** 时生效；卡框/事件图 blit 之后紧跟 `drawString`，那一批被用文字颜色（不透明）刷出。
   - 修法（两线已落）：卡框 + 事件图 blit 之后**显式 `g.flush()`**，再用 `RenderSystem.setShaderColor` 复位后画文字（`EventCardOverlay.java:111/114/117/127`）。
   - 验证方式：**必须重启客户端**（现运行 jar 为修复前字节码），重拍相位帧看卡框与事件图是否随之变淡。

## B. 已知但未处理的项

2. **[未处理·可选] 垂直半像素偏移**：卡心 (959.5, 541.5) vs 屏幕中心 (960, 540) ⇒ 偏差 (−0.5, +**1.5**) 设备 px = (−0.125, +0.375) GUI px @guiScale=4。
   成因：`FRAME_H = 67`（奇数）+ `int y = -FRAME_H/2` ⇒ 上下留白 33/34 不对称。修法：奇数高时整体上移 0.5 GUI px（1 行）。
3. **[无法判定] 音效可闻性**：该实例 `options.txt` 主音量 `soundCategory_master:0.0`、`showSubtitles:false`，NeoForge 不记录单次 sound play ⇒ 听不见也测不到。
   静态链已核：弹卡瞬间**无条件**播 `ModSounds.EVENT_FADE_IN`；`sounds.json` 齐备；8 个 `event_*.ogg` 合法非空。
   另：`red_heat_warning` **按设计无结算音**（不在 `playTriggerSounds` 的 switch 里）⇒ 要验 `event_heal`/`event_damage` 需换 `learn_from_others` / `world_collapse`。
4. **[口径说明] 结算时机**：`exec_at = 弹卡 gt + 38`（`EventCardService.DELAY_TICKS = 38` = 动画 1.4s + 0.5s），但 `EventEffects#onPlayerTick` 每 **10t** 才 drain 一次 ⇒ 实际落在 **38–48t**；20 TPS 下墙钟实测 **1999 / 2000 / 2001 / 2290 / 2498 ms**（tick 域口径，不是恒定 2.0s）。
5. **[未覆盖] 覆盖缺口**：`world_collapse`（无计时器事件）**无截图**；`storm_approaching` / `problem_delivery` 只有静态哈希、无屏上核验。

## C. 收尾欠账（等指示后统一处理）

6. **临时件清理**：
   - 产品源码打点：**已删净**（两线 `src` 内 `AP_EC_*` / `TEMP-INSTRUMENTATION` 计数 = 0）；
   - 测试资产：`scripts/test/_tmp_ec_collect*.ps1`、`scripts/test/resources/kubejs/1.21.1/server_scripts/{astral_event_card_probe.js,zz_ec_trigger.js}`、同目录 `client_scripts/{astral_event_card_shot.js,zz_ec_burst3.js}`；
   - `run/1.21.1/kubejs/**` 探针（含每 600t 连拍那个，会持续产 `mine_b*.png`）；
   - `temp/card_shots*/**`、`temp/ec_verify/**`、`temp/t156..t160`；`run/1.21.1/.mt_keep_alive` 与 `scripts/test/cases/.mt_keep_alive`。
7. **现场收停**：`pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force --purge-saves`。
8. **提交**：清理后跑两线构建 + 全部守门（`verify_event_registry.ps1` / `check_lang_sync.ps1` / `verify_effect_icons.py` / `verify_resource_integrity.ps1`）+ `code_review` → 提交 **`.64`**。

## D. 已核实为正确的项（实机像素级证据）

- 居中：偏差 < 1 GUI px（水平 0 / 垂直 0.375）；事件图按**内容 bbox** 居中铺进实测图片格（40×39 @2,2）✓；
- 指示器图标：三对 SHA256 与 `images/<中文名>.png` **逐字节一致**，屏上效果 HUD 18×18 GUI 匹配 91.9% ✓；
- 极性染色：12 条事件类型已按用户清单逐条订正（红温警告=正面/绿）✓；
- 动画相位：0.2s 进（scale 0.864/α 0.395）→ 1.0s 停（1.000/1.000）→ 0.2s **缩小淡出**（0.920/α 0.600，末帧 0.805/0.025）✓（**文字** alpha 正确；卡面淡出见 A.1）。
