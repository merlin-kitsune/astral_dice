"""精准滞后判据：forge 的「实质语句行」在 fabric 中是否存在。
已规范化平台标识符、剔除 import/注解/纯注释。
未命中者 = forge 独有语义 ⇒ 候选滞后（需人工确认是否平台特有 API）。
"""
import os, re, glob
import sys
# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
# 背景：Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk，
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError
# 退出码 1（“门会红但红的原因是崩溃”）。此块使脚本不依赖调用者环境与宿主编码。
# ⚠ 块内引用 sys ⇒ 必须位于**所有 import 之后**；也**不得**加 except-pass（会静默失效）。
def _astral_heal_stdio() -> None:  # pragma: no cover - 环境适配
    for _stream_name in ("stdout", "stderr"):
        _stream = getattr(sys, _stream_name, None)
        _reconf = getattr(_stream, "reconfigure", None)
        if _reconf is None:
            continue
        try:
            _reconf(encoding="utf-8", errors="replace")
        except Exception as _exc:  # noqa: BLE001 - 明确告警，绝不静默
            sys.stderr.write(
                "[astral-gate] WARN: %s.reconfigure(utf-8) failed: %r\n"
                % (_stream_name, _exc)
            )


_astral_heal_stdio()

ROOT = r'F:\MCProject\astral_dice_multiloader'
PKG = 'src/main/java/com/merlinkitsune/astral_dice'

PLAT_SUBS = [
    (r'import [^;]+;', ''),
    (r'@Mod\.EventBusSubscriber\([^)]*\)', ''),
    (r'@EventBusSubscriber\([^)]*\)', ''),
    (r'\b(?:top\.theillusivec4\.curios\.api\.|com\.merlinkitsune\.astral_dice\.compat\.curios\.|com\.merlinkitsune\.astral_dice\.platform\.event\.|net\.minecraftforge\.event\.|net\.neoforged\.event\.)', ''),
    (r'\b(?:net\.minecraftforge\.|net\.neoforged\.)', ''),
    (r'CuriosCompat\.getCuriosInventory', 'CuriosApi.getCuriosInventory'),
    (r'com\.merlinkitsune\.starenginelib\.item\.CuriosCompat', 'CuriosCompat'),
]


def canon(s):
    for pat, rep in PLAT_SUBS:
        s = re.sub(pat, rep, s)
    s = re.sub(r'\s+', ' ', s).strip()
    return s


def content_lines(p):
    t = open(p, 'rb').read().decode('utf-8').replace('\r\n', '\n')
    out = []
    for ln in t.split('\n'):
        s = ln.strip()
        if not s:
            continue
        if s.startswith(('package ', 'import ', '@Mod.', '@EventBusSubscriber')):
            continue
        if s.startswith(('//', '*', '/*', '*/')):
            continue
        out.append(canon(ln))
    return out


rows = []
for fp in sorted(glob.glob(os.path.join(ROOT, 'forge-1.20.1', PKG, '**', '*.java'), recursive=True)):
    rel = os.path.relpath(fp, os.path.join(ROOT, 'forge-1.20.1', PKG))
    bp = os.path.join(ROOT, 'fabric-1.20.1', PKG, rel)
    if not os.path.exists(bp):
        continue
    fa = content_lines(fp)
    fb = set(content_lines(bp))
    miss = [x for x in fa if x not in fb]
    if miss:
        rows.append((rel.replace('\\', '/'), miss))

print(f'=== forge 独有语义行命中文件 {len(rows)} 个 ===')
tot = sum(len(m) for _, m in rows)
print(f'合计未命中行 {tot}\n')
for rel, miss in sorted(rows, key=lambda x: -len(x[1])):
    print(f'--- {rel}  ({len(miss)} 行)')
    for m in miss[:14]:
        print('    ', m[:150])
    if len(miss) > 14:
        print(f'     … 另 {len(miss) - 14} 行')
