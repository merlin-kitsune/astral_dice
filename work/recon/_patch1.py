import io
p="work/recon/ci-failure.md"
s=open(p,encoding="utf-8").read()
old1="- **完整 step 日志（每条命令的 stdout/stderr）未取得**：日志下载 `GET /repos/{o}/{r}/actions/jobs/{id}/logs` 在配额耗尽时返回 403；即便配额恢复，匿名下载 job 日志对私有化路径要求更高权限。因此对 #144 只能定位到「第 21 个 step、退出码 1」，**不能**给出它具体是在 `gh release delete-asset` 还是 `gh release upload` 上挂的。"
new1="""- **完整 step 日志（每条命令的 stdout/stderr）未取得 —— 已实测确认，不是推测**。配额重置后（2026-11-02T11:10:20Z 实测）再取 `GET /repos/merlin-kitsune/astral_dice/actions/jobs/125106569899/logs`：

```
HTTP=403  size=176
{"message":"Must have admin rights to Repository.",
 "documentation_url":"https://docs.github.com/rest/actions/workflow-jobs#download-job-logs-for-a-workflow-run",
 "status":403}
```

  ⇒ 匿名身份下**没有**可取 job 日志的路径。可用替代面是 `GET /actions/runs/<id>/jobs`（匿名 200，返回**每个 step 的 name/status/conclusion**）：本报告第 3 节据此把三次红全部定位到 **step 22**，但命令级 stdout/stderr 仍不可得。"""
assert old1 in s, "anchor1 missing"
s=s.replace(old1,new1)
old2="check run id `125225791294`，失败 step 名（annotation 前缀即 step 名）："
new2="job id `125225791294`（`GET /actions/runs/41429965923/jobs`，started `2026-11-02T10:35:06Z` / completed `2026-11-02T10:37:52Z`）：**step 1–21 全部 success**（含 `Build with Gradle (all targets)`、`Read base version`、`Create Git tag`），**唯一 non-success = step 22**。失败 step 名（annotation 前缀即 step 名）："
assert old2 in s, "anchor2 missing"
s=s.replace(old2,new2)
old3="check run id `125227038930`，annotation 原文："
new3="job id `125227038930`（started `2026-11-02T10:37:51Z` / completed `2026-11-02T10:39:47Z`）：同样**只有 step 22** 为 failure，其余 28 个 step（含 post）success。annotation 原文："
assert old3 in s, "anchor3 missing"
s=s.replace(old3,new3)
open(p,"w",encoding="utf-8",newline="").write(s)
print("A/B applied; len", len(s))