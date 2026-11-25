import io,re
p=r'D:/MCProject/astral_dice_multiloader/docs/agent/AGENTS-dsh.md'
f=io.open(p,'r',encoding='utf-8',newline='');s=f.read();f.close()
anchor='- **违规处置**：若发现已在测试结论齐全之前发布 ⇒ 立即下架/归档该件并登记在案，不得用「后续补测通过」掩盖已发生的越序发布。'
print('anchor', s.count(anchor))
add=anchor+chr(13)+chr(10)+'- **发布前须取得用户许可（2026-11-02 补充）**：测试与实机取证完成后，先向用户汇报证据，**由用户明确点头后**才执行 push / tag / Release / CurseForge；用户未许可前不得对外发布，用户已撤回的发布件不得自行重新上传。'
s=s.replace(anchor, add, 1)
f=io.open(p,'w',encoding='utf-8',newline='');f.write(s);f.close()
print('ok lines', s.count(chr(10)))