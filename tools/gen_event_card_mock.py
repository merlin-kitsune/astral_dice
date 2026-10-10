# -*- coding: utf-8 -*-
"""效果卡 v5：完全照商店卡牌口径 —— 卡框 44x67 + 事件图 64x64 等比缩到 38x38 贴 (3,3)（合成一张图）+
MC 字体(9px) 居中两行。商店参照：screen/CardShopScreen.java FRAME_W=44 FRAME_H=61 FACE_SIZE=38 FACE_X_OFF=3
FACE_Y_OFF=3 NAME_Y_OFF=47; fanny_skill_event.png 是同一卡框的加高版(44x67)。"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EV = os.path.join(ROOT, "neoforge-1.21.1/src/main/resources/assets/astral_dice/textures/gui/event")
FRAME = os.path.join(ROOT, "images/shop/fanny_skill_event.png")
OUT = os.path.join(ROOT, "images/mocks/event_card_mock_v5.png")
GREEN, YELLOW, RED = (85, 255, 85), (255, 255, 85), (255, 85, 85)
FW, FH, FACE, FX, FY, NAME_Y, TIME_Y = 44, 67, 38, 3, 3, 47, 56


def _f(size):
    for q in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"):
        if os.path.exists(q):
            return ImageFont.truetype(q, size)
    return ImageFont.load_default()


F, FL = _f(9), _f(13)
frame = Image.open(FRAME).convert("RGBA")
print("frame", frame.size)


def composed_card(eid):
    """合成卡：卡框 + 事件图（贴 (3,3)，38x38）—— 一张图，整卡缩放。"""
    card = frame.copy()
    icon = Image.open(os.path.join(EV, eid + ".png")).convert("RGBA").resize((FACE, FACE), Image.NEAREST)
    card.alpha_composite(icon, (FX, FY))
    return card


def ctext(d, cx, y, s, fnt, color):
    w = d.textlength(s, font=fnt)
    d.text((cx - w / 2, y), s, font=fnt, fill=color)


def draw(canvas, cx, cy, scale, alpha, eid, name, color, timer):
    cw, ch = max(1, int(FW * scale)), max(1, int(FH * scale))
    card = composed_card(eid).resize((cw, ch), Image.LANCZOS)
    d = ImageDraw.Draw(card)
    ctext(d, cw / 2, int(NAME_Y * scale), name, F, color + (255,))
    if timer:
        ctext(d, cw / 2, int(TIME_Y * scale), timer, F, color + (255,))
    if alpha < 255:
        card.putalpha(card.getchannel("A").point(lambda v: int(v * alpha / 255)))
    canvas.alpha_composite(card, (int(cx - cw / 2), int(cy - ch / 2)))


CELL_W, CELL_H = 120, 150
FRAMES = [
    (0.775, 128, "red_heat_warning", "红温警告", GREEN, "3:00", "t=0.25s 进场"),
    (1.000, 255, "red_heat_warning", "红温警告", GREEN, "3:00", "t=0.75s 展示"),
    (1.075, 128, "crowded_passage", "拥挤通道", YELLOW, None, "t=1.25s 收起"),
    (1.000, 255, "world_collapse", "天崩地裂", RED, None, "t=2.00s 已消失"),
]
row = Image.new("RGBA", (CELL_W * 4, CELL_H), (24, 26, 32, 255))
d = ImageDraw.Draw(row)
for i, (sc, al, eid, nm, col, tm, cap) in enumerate(FRAMES):
    cx = CELL_W * i + CELL_W / 2
    ctext(d, cx, 6, cap, FL, (175, 185, 205, 255))
    if sc == 1.0 and al == 255 and eid == "world_collapse":
        ctext(d, cx, 60, "（卡已消失）", FL, (205, 205, 215, 255))
        ctext(d, cx, 76, "结算 + 音效", FL, (255, 215, 120, 255))
    else:
        draw(row, cx, 82, sc, al, eid, nm, col, tm)
zoom = 3
big = row.resize((row.width * zoom, row.height * zoom), Image.NEAREST)
# 合成步骤对照（1:1 三格）
steps = Image.new("RGBA", (CELL_W * 3, CELL_H), (24, 26, 32, 255))
sd = ImageDraw.Draw(steps)
sd.text((8, 6), "① 卡框", font=FL, fill=(175, 185, 205, 255))
steps.alpha_composite(frame.copy(), (int(CELL_W / 2 - FW / 2), 30))
sd.text((CELL_W + 8, 6), "② 贴事件图 (3,3) 38x38", font=FL, fill=(175, 185, 205, 255))
steps.alpha_composite(composed_card("red_heat_warning"), (int(CELL_W * 1.5 - FW / 2), 30))
sd.text((CELL_W * 2 + 8, 6), "③ + MC 字体两行", font=FL, fill=(175, 185, 205, 255))
tmp = composed_card("red_heat_warning")
td = ImageDraw.Draw(tmp)
ctext(td, FW / 2, NAME_Y, "红温警告", F, GREEN + (255,))
ctext(td, FW / 2, TIME_Y, "3:00", F, GREEN + (255,))
steps.alpha_composite(tmp, (int(CELL_W * 2.5 - FW / 2), 30))
bigsteps = steps.resize((steps.width * zoom, steps.height * zoom), Image.NEAREST)

W = big.width
img = Image.new("RGB", (W, big.height + bigsteps.height + 74), (28, 30, 36))
dd = ImageDraw.Draw(img)
dd.text((14, 8), "效果卡 v5 · 照商店卡牌口径：卡框 44x67 + 事件图 38x38 贴(3,3) 合并为一张图", font=_f(17), fill=(240, 240, 245))
dd.text((14, 32), "MC 标准字体 9px · 文本居中 · 名称 y=47 / 计时器 y=56 | 0-0.5s 进场 0.5-1.0s 展示 1.0-1.5s 收起 2.0s(40t) 结算+音效", font=_f(13), fill=(150, 200, 255))
dd.text((14, 54), "下排 3× 放大；末行 = 合成步骤", font=_f(13), fill=(175, 185, 205))
img.paste(big, (0, 74), big)
img.paste(bigsteps, (0, 74 + big.height), bigsteps)
img.save(OUT)
print("WROTE", OUT, img.size)
