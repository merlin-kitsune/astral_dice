# 嵌套松散引用“消失”事件 — 调查结论（2026-09-11）

> 本地技术备忘，`docs/` 已被 `.gitignore` 排除，不入库。权威摘要见 AGENTS.md 同名章节。

## 1. 原始问题

`git` 刚写完 `.git/refs/heads/<含斜杠分支名>/<引用>` 后，该引用文件**连同父目录**查不到；
`HEAD` 看起来无法解析、分支像“消失”。首见于提交 `04e8aeb`、`4d952c5`。

早期定性（**现已作废**）：外部删除 / 内核态文件系统过滤驱动 / 卡巴斯基实时防护。
早期“证据”：只有嵌套引用会消失；reflog 存活；文件确被创建过随后数毫秒消失；删除瞬间无新进程；
“只有 `%TEMP%` 豁免”；引用文件出现在回收站。

## 2. 本次复现（用户已暂停卡巴斯基实时防护，且不调用自愈钩子）

| 位置 | 嵌套引用 `refs/heads/nested/deep` | 普通嵌套目录 |
|---|---|---|
| `F:\MCProject\`（仓库外） | 存活 | 存活 |
| 仓库内 `temp/` | 存活 | 存活 |
| `C:\Users\xmace\AppData\Local\Temp\` | 存活 | 存活 |
| `D:\` 根 | 存活 | 存活 |
| `C:\` 根 | 存活 | 存活 |

**结果：全部 5/5 存活**；`git show-ref`、`git rev-parse` 均可解析。
沙箱模式与被绕过模式（`⚠️ Sandbox bypassed (escalation-approved)`）**同样存活**。
`git init` + `git update-ref` 与纯 shell `printf` 写入两种方式**都存活**。

→ **无法复现任何删除。卡巴斯基、Defender 均已排除**（Defender 本就 `Not running`）。

### 复现过程中发现的探针自身缺陷（说明旧观测不可信）

1. 旧检查用了 `refs/heads/...` 而实际路径是 `.git/refs/heads/...` → 一律“GONE”。
2. `git update-ref <ref> 0000…0001` 因对象不存在直接失败 → 被误读为“引用被删”。
3. 更早还有一处 `cd` 写在 `$( )` 子 shell 里，导致 C: 盘测试实际跑在 F: 目录（已修正）。

## 3. 三个“伪删除”来源（已实测证实）

### 3.1 `git pack-refs` 会删除松散引用文件并移除其**空父目录**

`git gc --auto` 会自动调用 `pack-refs`。引用改由 `.git/packed-refs` 承载，**仍然有效**。

本机实测：

```
after update-ref : dir=EXISTS  file=EXISTS
after pack-refs  : dir=MISSING file=MISSING
in packed-refs?  : 1
still resolvable : d4480cf37f60          ← 引用没丢！
```

→ **裸 `ls .git/refs/heads/<a>/` 报 “No such file or directory” 不代表引用丢失。**

### 3.2 父目录不存在时写入直接失败

`echo "$sha" > .git/refs/heads/<a>/<b>` 在父目录缺失时报 `No such file or directory`——
**是写入失败，不是写入后被删**。2026-09-10 23:09 沙箱日志原文即为此：

```
ls: cannot access '.git/refs/heads/multi-1.20.1/': No such file or directory
cat:    .git/refs/heads/multi-1.20.1/1.21.1: No such file or directory
```

### 3.3 “只有 `%TEMP%` 豁免” = WorkBuddy 沙箱白名单

`C:\Program Files\WorkBuddy\resources\app.asar.unpacked\cli\vendor\sandbox\5.5.5\tsbx_rules.json`：

```json
"default_action": "deny_write",
"recyclebin_backup": true,
"file_rules": [
  { "path": "%LOCALAPPDATA%\\Temp\\**", "type": "inherit_user" },
  { "path": "**\\$RECYCLE.BIN\\**",     "type": "inherit_user" },
  ...（.cache/.local/.npm/.gradle/.m2 等开发缓存目录同样 inherit_user）
]
```

`%LOCALAPPDATA%\Temp\**` 正在白名单里——旧观测“只 `%TEMP%` 生存、其它全灭”**逐条吻合**，
说明那是沙箱写入策略，**不是**外部清理程序。

## 4. 回收站里为什么会有引用文件

WorkBuddy 沙箱自带的 `modify_backup`（文件改动跟踪/可回滚）：

- 沙箱日志：`EnableModifyBackup enabled=true, fileBackupMaxSizeMB=3000`；
- 全局规则：`add_file_rule: original_path=** → type=modify_backup`；
- 每条命令创建/修改/删除文件都会记 `ModifyBackup {reason: a|m|d, targetPath}`；
- 快照落在 `~/.workbuddy/workspace/sessions/<sid>/modify_backup/`（确见 `7.a.*.gitrepro2.sh` 等）；
- 配置项 `recyclebin_backup: true` → 被删原文件经**回收站**保留。

→ 解释了 `$RECYCLE.BIN` 现象，与杀毒软件无关。

## 5. 驱动层排除

| 驱动 | 实际归属 |
|---|---|
| `UnionFS.sys` | **Microsoft** — Union Filesystem Driver |
| `bfs.sys`（“中转文件系统”） | **Microsoft** — Bfs 筛选器驱动程序 |
| `VirtualFileSystem2.sys` | **Dokan Project 2.1.0.1001**（用户态文件系统框架） |
| `NeSigVerify.sys` | NetEase 签名驱动 |

均与引用消失无因果关系。

## 6. 正确姿势与缓解

**判定引用是否真的丢了**（禁止再用 `ls` 松散文件）：

```bash
git rev-parse --verify HEAD
git show-ref
git branch
```

只要 `git rev-parse` 能解析，引用就是好的（哪怕松散文件已被 `pack-refs` 收走）。

**已保留的廉价保险**（真正丢失时才生效）：

- `scripts/maintenance/repair-loose-refs.ps1` — 以**幸存 reflog** 为准补回；仍可解析者跳过；
  git 有意删分支时 reflog 一并删除，故不会误复活。
- `.git/hooks/{post-commit,post-checkout,post-merge,post-rewrite}` — 自动调用（已端到端验证）。

**可选缓解**：长期工作分支名去掉 `/`（如 `multi-1.20.1-1.21.1`），
减少“空目录被清理”造成的误判。

**不需要**再调整卡巴斯基（可恢复实时防护）。
