p='work/recon/ci-failure.md'
s=open(p,encoding='utf-8').read()
oldU1='''| U1 | #144 具体挂在哪条 `gh` 命令上 | 该 job 的**完整日志**（`#step:22` 的命令级 stdout/stderr）。当前匿名下载日志 403（配额耗尽 + 日志下载需鉴权），只能到「退出码 1」 |'''
newU1='''| U1 | #144 具体挂在哪条 `gh` 命令上 | 该 step（`#step:22`）的**命令级 stdout/stderr**。配额恢复后实测 `GET /actions/jobs/125106569899/logs` → 403，body = `Must have admin rights to Repository.`；匿名身份无此面，只能到「step 22 + 退出码 1」 |'''
assert oldU1 in s, 'U1 missing'
s=s.replace(oldU1,newU1)
oldU2='''| U2 | #144 的失败是否确为**并发竞态**（而非 gh 版本/网络抖动） | 同样缺 U1 的日志。间接证据充分：三个 run（#142 05:08:47–05:11:50、#144 05:10:20–05:13:03、#145 05:10:32–05:12:50）时间窗完全重叠，操作同一个 tag `1.5.0`，且 `[republish-assets]` 通道会**先删后传**；全文件无 `concurrency:` 组 |'''
newU2='''| U2 | #144 的失败是否确为**并发竞态**（而非 gh 版本/网络抖动、API 抖动） | 同样缺 U1 的日志。间接证据已相当强：① 同 commit 的 tag run #145（05:10:32–05:12:50）**绿**而分支 run #144（05:10:20–05:13:02）**红**，两者同版本树、同 `[republish-assets]` 通道；② 三个 run（#142 05:08:47–05:11:50、#144 05:10:20–05:13:03、#145 05:10:32–05:12:50）时间窗完全重叠，操作同一个 tag `1.5.0`；③ `[republish-assets]` 通道先逐个 `delete-asset`（无容错）再 `upload`（无 `--clobber`）；④ 全文件无 `concurrency:` 组。**但**「哪条命令先挂」仍无逐字证据 |'''
assert oldU2 in s, 'U2 missing'
s=s.replace(oldU2,newU2)
oldC='缺的那一项证据就是 #144 该 step 的命令级日志。'
newC='缺的那一项证据就是 #144 该 step 的命令级日志（配额恢复后实测该端点仍返回 403 `Must have admin rights to Repository.`，匿名身份取不到）。'
assert oldC in s, 'final missing'
s=s.replace(oldC,newC)
open(p,'w',encoding='utf-8',newline='').write(s)
print('ok',len(s))