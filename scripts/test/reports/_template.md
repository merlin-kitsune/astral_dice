# Astral Dice 目标选择器自动化测试报告模板

- **分支**: 1.21.1-targetselector
- **日期**: {{DATE}}
- **环境**: neoforge-1.21.1/run/1.21.1(dev 本体, quickplay=testworld)
- **兼容模组**: Sodium sodium-neoforge-0.8.13+mc1.21.1.jar / Iris iris-neoforge-1.8.14-beta.1+mc1.21.1.jar
- **本地 MCP**: computer-control-mcp(AB498 v0.3.13, stdio, custom-computer_control)

## 执行步骤结果

| 步骤 | 内容 | 结果 |
|---|---|---|
| 1 | 构建 + pushToDevRun + Sodium/Iris 安装 |  |
| 2 | prepare_world(超平坦/创造/允许命令) |  |
| 3 | runClient quickplay=testworld 进入世界 |  |
| 4 | MCP 自检(status/tools) |  |
| 5~17 | TC1~TC13(见下) |  |
| 18 | 日志/截图收集 + 关闭游戏 |  |

## TC 结果

| TC | 场景 | 断言 | 结果 |
|---|---|---|---|
| TC1 | 触发+HUD | /astral_dice targetselect enemy → 日志 start + 截图 OCR「目标选择中」 |  |
| TC2 | 敌对高亮 | 召唤 NoAI 掠夺者 → 截图红色描边 |  |
| TC3 | 友方/中立高亮 | 驯服狼/村民(living) → 绿/黄描边 |  |
| TC4 | 右键确认 | rclick → confirm SUCCESS + ActionBar 回显 |  |
| TC5 | Enter 确认 | enter → 同上 |  |
| TC6 | 无效目标 | enemy 模式指村民 → 无高亮 + 提示 |  |
| TC7 | Esc 取消 | escape → HUD 消失、无暂停界面 |  |
| TC8 | J 取消 | j → HUD 消失 |  |
| TC9 | 输入锁定+移动 | e/t/f3 被拦截图 + W 移动坐标变化 |  |
| TC10 | 半径 | 10 格可/20 格不可;改 32;写 40 回落 32 |  |
| TC11 | Sodium+Iris 兼容 | 全流程高亮/HUD 正常、无 crash、无 Iris 异常 |  |
| TC12 | 回归 | 退出选择后左键攻击正常 |  |
| TC13 | MCP 健康 | mcp_connector_status OK + 截屏/OCR 可用 |  |

## 关键日志标记

```
(粘贴 latest.log 中 [Astral Dice][TargetSelection*] 关键行)
```

## 崩溃/异常

- crash-reports: 无
- latest.log ERROR 堆栈: 无
- kubejs server.log: 0 errors

## 结论
