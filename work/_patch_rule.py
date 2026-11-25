import io
def rd(p):
    f=io.open(p,'r',encoding='utf-8',newline='');s=f.read();f.close();return s
def wr(p,s):
    f=io.open(p,'w',encoding='utf-8',newline='');f.write(s);f.close()
CR=chr(13);LF=chr(10)
p1=r'D:/MCProject/astral_dice_multiloader/docs/agent/AGENTS-dsh.md'
s=rd(p1)
print('dsh crlf', CR+LF in s, 'lines', s.count(LF))
anchor='带 `-rc` / `-pre` / `-alpha` / `-beta` 的版本一律标 prerelease、不占 latest。'
print('anchor count', s.count(anchor))
add='**③ 发布时序红线（2026-11-02 用户裁决，强制）**：**未经详细测试（含实机冒烟）的版本一律不得作为发布版本**；**生产版本的发布动作必须是整条流程的最后一步** —— 固定顺序为「构建 → 本地详细测试 / 实机冒烟取证 → 审核项全过 → 才允许 push / tag / Release / CurseForge 上传」。在测试结论齐全之前执行的任何对外发布动作都算违规，发现即回滚或下架并记录。'
s=s.replace(anchor, anchor+add, 1)
wr(p1,s)
print('p1 new len', len(s))