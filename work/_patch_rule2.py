import io
def rd(p):
    f=io.open(p,'r',encoding='utf-8',newline='');s=f.read();f.close();return s
def wr(p,s):
    f=io.open(p,'w',encoding='utf-8',newline='');f.write(s);f.close()
CR=chr(13);LF=chr(10)
p1=r'D:/MCProject/astral_dice_multiloader/docs/agent/AGENTS-dsh.md'
s=rd(p1)
nl = CR+LF if (CR+LF in s) else LF
anchor='项目基线:'
print('anchor1', s.count(nl+anchor))
sec=[]
sec.append('## 发布流程硬规则（2026-11-02 用户裁决，强制）')
sec.append('')
sec.append('- **没有详细测试就没有发布版本**：构建成功不等于可发布。任何版本在对外分发前必须完成本地详细测试与**实机冒烟**取证（进世界、mod 清单与版本号、版本门行、关键功能日志/截图），证据缺失即视为未测试。')
sec.append('- **发布动作必须是最后一步**：固定顺序 = 构建 → 本地详细测试 → 实机冒烟取证 → 审核项全过 → 才允许 push / tag / GitHub Release / CurseForge 上传。禁止「先上传再补测」「边测边发」。')
sec.append('- **覆盖全部渠道**：GitHub Release、CurseForge、任何对外分发点一视同仁；构建产物本地生成不算发布。')
sec.append('- **违规处置**：若发现已在测试结论齐全之前发布 ⇒ 立即下架/归档该件并登记在案，不得用「后续补测通过」掩盖已发生的越序发布。')
block = nl.join(sec) + nl + nl
s = s.replace(nl+anchor, nl+block+anchor, 1)
wr(p1,s)
print('p1 ok lines', s.count(LF))
p2=r'D:/MCProject/astral_dice_multiloader/AGENTS.md'
t=rd(p2)
nl2 = CR+LF if (CR+LF in t) else LF
a2='6. **不代改他人职责**：模组源码 / 资源 / 版本号 / 依赖 / 发布配置的改动，按所属宿主的规则文件走。'
print('anchor2', t.count(a2))
b2 = a2 + nl2 + '7. **没有详细测试就没有发布版本**：未完成本地详细测试与实机冒烟取证的版本一律不得作为发布版本；生产版本的 push / tag / Release / CurseForge 上传必须是整条流程的最后一步（2026-11-02 用户裁决）。'
t = t.replace(a2, b2, 1)
wr(p2,t)
print('p2 ok lines', t.count(LF))