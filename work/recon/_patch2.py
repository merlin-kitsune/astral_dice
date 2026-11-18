p="work/recon/ci-failure.md"
s=open(p,encoding="utf-8").read()
old="- **该 step 的具体命令级日志未取得**（§1.3）；因此「是 `delete-asset` 还是 `upload` 挂的」= **未核验**。"
new="""### 3.3.1 #144 的 step 级台账（`GET /actions/runs/41392445400/jobs`，匿名 200）

job `125106569899`，started `2026-11-02T05:10:24Z` / completed `2026-11-02T05:13:02Z`，`conclusion: failure`：

| step | 名称 | 结果 |
|---|---|---|
| 1–8 | Set up job / Checkout / Setup JDK 21·17·25 / Setup Gradle / Checkout StarEngine / Publish StarEngine | success |
| 9–12 | Check lang sync（四线） | success |
| 13–16 | Setup Python / CHANGELOG parity / chip acquisition / event-choice & combat gates | success |
| 17 | `Build with Gradle (all targets)` | **success** |
| 18–19 | Upload artifact（三线 / fabric） | success |
| 20 | `Read base version` | success |
| 21 | `Create Git tag (release branch push only)` | success |
| **22** | **`Create/Update GitHub Release (four JARs, notes from release/<tag>/)`** | **failure** |
| 38–45 | post 步骤 / Complete job | success（`Post Setup Python` 为 skipped） |

读数：
- 构建、版本读取、打 tag **全部成功** ⇒ #144 的失败**不**在 jar 收集（`build.yml:326`）也不在版本树——与 #146/#147 的机理**不同**；
- 失败必定发生在该 step 的 `gh` 调用段（`build.yml:396`–`build.yml:432`），而这正是 `[republish-assets]` 通道（annotation 已确认该 run 进入了该通道）；
- **该 step 的具体命令级日志未取得**（§1.3，`logs` 端点实测 403 `Must have admin rights to Repository.`）；因此「是 `delete-asset` 还是 `upload` 挂的」= **未核验**。"""
assert old in s
s=s.replace(old,new)
# V7 row
oldv6="| V6 | 这两次红**没有破坏 Release**：`exit 1` 在任何 `gh` 调用之前 | `build.yml:344-347` 位置；Release `1.5.0` 现仍完整持有四个 `1.5.0+…` 附件 |"
newv6=oldv6+"\n| V7 | 三次红的失败点被逐 step 台账确认：**#146 / #147 / #144 都是且只是第 22 个 workflow step（Release step）失败**，其余 step 全 success | `GET /actions/runs/{41429965923,41430344936,41392445400}/jobs`：`#146` job 125225791294 `success 28 / failure 仅 step 22`；`#147` job 125227038930 同为 28 success / 仅 step 22 failure；`#144` job 125106569899 step 1–21 全 success、step 22 failure |"
assert oldv6 in s
s=s.replace(oldv6,newv6)
open(p,"w",encoding="utf-8",newline="").write(s)
print("ok", len(s))