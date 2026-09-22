#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
StarEngine-Lib 源码迁移(默认干跑,--apply 才落盘)

分类依据: tools/recompute_island_strict.py 的严格孤岛(35 个单元)

  COMMON(30)        共享源码 → <lib>/common/src/main/java
                    28 个字节相同 + 2 个 shim 化(SignActiveTriggeredEvent / BossEntityUtil)
  PLATFORM_BOTH(2)  同名两实现 → <lib>/{neoforge-1.21.1,forge-1.20.1}/src/main/java
                    ActionBarManager(DeltaTracker vs float) / ModEffectRemoval(Holder<MobEffect> vs MobEffect)
  FORGE_ONLY(2)     仅 Forge → <lib>/forge-1.20.1/src/main/java
                    ItemDataKey / CuriosCompat

不迁移(已证有注册依赖,搬了必编译失败):
  combat/AttackPowerModifier..  → DiceCombatContext → ModItems
  item/card/*                  → RandomCardHandler  → ModItems
  item/chip/*                  → ModItems
  client/TargetSelectOverlay   → ModAttachments
  component/WeaponEnhancement  → ModDataComponents
  component/AstralData         → ModCapabilities
  config/ModCommonConfig       → 消费方自身的游戏规则配置,且改了会重命名配置文件
  effect/HealingEffect 等      → (已放行, 其 HealingManager 引用仅存在于 javadoc)

用法:
  python tools/migrate_to_starengine_lib.py            # 干跑
  python tools/migrate_to_starengine_lib.py --apply    # 落盘
"""
import re
import shutil
import sys
from pathlib import Path

DRY = '--apply' not in sys.argv

ROOT = Path(__file__).resolve().parent.parent
PKG = 'com/merlinkitsune/astral_dice'
NEO_PKG = ROOT / 'neoforge-1.21.1' / 'src' / 'main' / 'java' / PKG
FORGE_PKG = ROOT / 'forge-1.20.1' / 'src' / 'main' / 'java' / PKG

LIB = Path('F:/MCProject/starengine_lib')
LIB_NEW_PKG = 'com/merlinkitsune/starengine'
LIB_COMMON = LIB / 'common' / 'src' / 'main' / 'java' / LIB_NEW_PKG
LIB_NEO = LIB / 'neoforge-1.21.1' / 'src' / 'main' / 'java' / LIB_NEW_PKG
LIB_FORGE = LIB / 'forge-1.20.1' / 'src' / 'main' / 'java' / LIB_NEW_PKG

OLD = 'com.merlinkitsune.astral_dice.'
NEW = 'com.merlinkitsune.starengine.'
OLD_FQN = re.compile(r'com\.merlinkitsune\.astral_dice\.([A-Za-z0-9_.$]+)')

# ---------------- 分类清单 ----------------
COMMON = """
client/ClientDamageNumbers
component/GameplayConstants
effect/BerserkEffect
effect/CounterEffect
effect/CutterReadyEffect
effect/DiceBlessingEffect
effect/FateGuidanceEffect
effect/HealingEffect
effect/InvestigationBonusEffect
effect/KingPowerEffect
effect/LivingPageEffect
effect/MarkedEffect
effect/MisakiBurstEffect
effect/MosesBrokenEffect
effect/NancyLuHackEffect
effect/PandamanTauntEffect
effect/PaparaBiteEffect
effect/RevengeHalberdEffect
effect/UndercoverInvestigationEffect
effect/WeakMarkEffect
event/AmethystDiceHandler
event/AstralEventType
event/EventContext
event/EventEffect
event/EventTargetCollector
event/SignActiveTriggeredEvent
item/BossEntityUtil
target/TargetSelectionAction
target/TargetSelectionRegistry
target/TargetType
""".split()

PLATFORM_BOTH = ['client/ActionBarManager', 'event/ModEffectRemoval']
FORGE_ONLY = ['component/ItemDataKey', 'item/CuriosCompat']

MOVED = sorted(set(COMMON) | set(PLATFORM_BOTH) | set(FORGE_ONLY))
MOVED_FQN = {OLD + r.replace('/', '.') for r in MOVED}
MOVED_SIMPLE = {r.rpartition('/')[2] for r in MOVED}
MOVED_PKG = {r.rpartition('/')[0] for r in MOVED}

# 需要搬走时从两侧删除的文件
DELETE_BOTH = COMMON + PLATFORM_BOTH + FORGE_ONLY

# 新文件(库自身)
EXTRA_LIB_FILES = {}


def new_files():
    return {
        LIB_COMMON / 'component' / 'GameplayConfigValues.java': GAMEPLAY_CONFIG_VALUES,
        LIB_NEO / 'StarEngineLib.java': MOD_CLASS_NEO,
        LIB_FORGE / 'StarEngineLib.java': MOD_CLASS_FORGE,
        LIB_NEO / 'platform' / 'LoaderEvent.java': LOADER_EVENT_NEO,
        LIB_NEO / 'platform' / 'LoaderTags.java': LOADER_TAGS_NEO,
        LIB_FORGE / 'platform' / 'LoaderEvent.java': LOADER_EVENT_FORGE,
        LIB_FORGE / 'platform' / 'LoaderTags.java': LOADER_TAGS_FORGE,
        ROOT / 'neoforge-1.21.1' / 'src' / 'main' / 'java' / PKG / 'config' / 'GameplayConfigBinder.java':
            GAMEPLAY_CONFIG_BINDER_NEO,
        ROOT / 'forge-1.20.1' / 'src' / 'main' / 'java' / PKG / 'config' / 'GameplayConfigBinder.java':
            GAMEPLAY_CONFIG_BINDER_FORGE,
    }


# ---------------- 源码文本 ----------------
GAMEPLAY_CONFIG_VALUES = '''package com.merlinkitsune.starengine.component;

/**
 * 玩法配置值快照:由消费方 mod 从自己的配置系统读取后传入
 * {@link GameplayConstants#applyConfig(GameplayConfigValues)}。
 *
 * <p>存在的意义:1.20.1 用 {@code ForgeConfigSpec}、1.21.1 用 {@code ModConfigSpec},
 * 两侧配置类型不通用,无法放进共享源码;共享的是「值」而不是「配置对象」,
 * 因此把可配置项收拢成本 record,配置读取留在各平台侧。
 */
public record GameplayConfigValues(
        int maxStarlight,
        int maxMarker,
        int effectCardCooldownSeconds,
        int maxEffectStacks,
        boolean giveGuideBookOnFirstJoin,
        int eventRange,
        boolean eventApplyMcTeam,
        boolean eventApplyFtbTeam,
        boolean eventApplyOpac,
        boolean eventApplyMaid,
        int handFanBigRange,
        int actionbarDurationTicks,
        int actionbarFadeTicks) {
}
'''

MOD_CLASS_NEO = '''package com.merlinkitsune.starengine;

import net.neoforged.fml.common.Mod;

/**
 * StarEngine Lib / NeoForge 1.21.1 入口。
 *
 * <p>本库**不注册任何注册表条目**(物品/效果/附件/数据组件/能力全部留在消费方 mod),
 * 因此搬迁不会改变任何 {@code ResourceLocation} 归属、不破坏存档与数据包。
 * 入口存在只为:1) 让库成为独立可加载的 mod(FML 需要 mods.toml 对应的容器);
 * 2) 为将来库自身的配置/日志留锚点。
 */
@Mod(StarEngineLib.MODID)
public final class StarEngineLib {
    public static final String MODID = "starengine_lib";

    public StarEngineLib() {
    }
}
'''

MOD_CLASS_FORGE = '''package com.merlinkitsune.starengine;

import net.minecraftforge.fml.common.Mod;

/**
 * StarEngine Lib / Forge 1.20.1 入口。
 *
 * <p>本库**不注册任何注册表条目**(物品/效果/附件/数据组件/能力全部留在消费方 mod),
 * 因此搬迁不会改变任何 {@code ResourceLocation} 归属、不破坏存档与数据包。
 * 入口存在只为:1) 让库成为独立可加载的 mod(FML 需要 mods.toml 对应的容器);
 * 2) 为将来库自身的配置/日志留锚点。
 */
@Mod(StarEngineLib.MODID)
public final class StarEngineLib {
    public static final String MODID = "starengine_lib";

    public StarEngineLib() {
    }
}
'''

LOADER_EVENT_NEO = '''package com.merlinkitsune.starengine.platform;

/**
 * 加载器事件基类 shim(NeoForge 1.21.1)。
 *
 * <p>共享源码里的事件类不能直接 {@code extends Event}——NeoForge 是
 * {@code net.neoforged.bus.api.Event},Forge 是 {@code net.minecraftforge.eventbus.api.Event},
 * 两者包名不同。共享源码统一 {@code extends LoaderEvent},由本平台类接上真正的事件基类。
 *
 * <p>声明为 abstract 是刻意的:无论平台侧 Event 是抽象类还是具体类,继承都合法。
 */
public abstract class LoaderEvent extends net.neoforged.bus.api.Event {
}
'''

LOADER_EVENT_FORGE = '''package com.merlinkitsune.starengine.platform;

/**
 * 加载器事件基类 shim(Forge 1.20.1)。
 *
 * <p>共享源码里的事件类不能直接 {@code extends Event}——NeoForge 是
 * {@code net.neoforged.bus.api.Event},Forge 是 {@code net.minecraftforge.eventbus.api.Event},
 * 两者包名不同。共享源码统一 {@code extends LoaderEvent},由本平台类接上真正的事件基类。
 *
 * <p>声明为 abstract 是刻意的:无论平台侧 Event 是抽象类还是具体类,继承都合法。
 */
public abstract class LoaderEvent extends net.minecraftforge.eventbus.api.Event {
}
'''

LOADER_TAGS_NEO = '''package com.merlinkitsune.starengine.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 加载器通用标签 shim(NeoForge 1.21.1)。
 *
 * <p>{@code c:bosses} 等 Common Tags 在 NeoForge 位于
 * {@code net.neoforged.neoforge.common.Tags},在 Forge 位于
 * {@code net.minecraftforge.common.Tags};共享源码统一引用本类。
 */
public final class LoaderTags {
    /** 通用 tag 命名空间(Common Tags,以 {@code c} 为命名空间) */
    public static final String COMMON_NAMESPACE = "c";

    /** {@code c:bosses} —— 覆盖末影龙/凋灵/监守者及灾变等模组的 boss */
    public static final TagKey<EntityType<?>> BOSSES =
            net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES;

    /** 备用:直接按 {@code c:bosses} 构造(当平台 Tags 未提供该常量时使用) */
    public static final TagKey<EntityType<?>> BOSSES_RAW =
            TagKey.create(Registries.ENTITY_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(COMMON_NAMESPACE, "bosses"));

    private LoaderTags() {
    }
}
'''

LOADER_TAGS_FORGE = '''package com.merlinkitsune.starengine.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 加载器通用标签 shim(Forge 1.20.1)。
 *
 * <p>{@code c:bosses} 等 Common Tags 在 NeoForge 位于
 * {@code net.neoforged.neoforge.common.Tags},在 Forge 位于
 * {@code net.minecraftforge.common.Tags};共享源码统一引用本类。
 */
public final class LoaderTags {
    /** 通用 tag 命名空间(Common Tags,以 {@code c} 为命名空间) */
    public static final String COMMON_NAMESPACE = "c";

    /** {@code c:bosses} —— 覆盖末影龙/凋灵/监守者及灾变等模组的 boss */
    public static final TagKey<EntityType<?>> BOSSES =
            net.minecraftforge.common.Tags.EntityTypes.BOSSES;

    /** 备用:直接按 {@code c:bosses} 构造(当平台 Tags 未提供该常量时使用) */
    public static final TagKey<EntityType<?>> BOSSES_RAW =
            TagKey.create(Registries.ENTITY_TYPE, new net.minecraft.resources.ResourceLocation(COMMON_NAMESPACE, "bosses"));

    private LoaderTags() {
    }
}
'''

GAMEPLAY_CONFIG_BINDER_NEO = '''package com.merlinkitsune.astral_dice.config;

import com.merlinkitsune.starengine.component.GameplayConfigValues;
import com.merlinkitsune.starengine.component.GameplayConstants;

/**
 * 配置 → 共享常量的绑定器(1.21.1 侧)。
 *
 * <p>共享库的 {@link GameplayConstants} 不能直接读配置——1.20.1 用
 * {@code ForgeConfigSpec}、1.21.1 用 {@code ModConfigSpec},类型不通用。
 * 因此由各平台侧把本 mod 配置系统的值读出来,包成平台无关的
 * {@link GameplayConfigValues} 交给共享库。
 */
public final class GameplayConfigBinder {

    /** 在配置加载完成后(FMLCommonSetup)调用,把配置值写入共享常量。 */
    public static void refresh() {
        GameplayConstants.applyConfig(new GameplayConfigValues(
                ModCommonConfig.MAX_STARLIGHT.get(),
                ModCommonConfig.MAX_MARKER.get(),
                ModCommonConfig.EFFECT_CARD_COOLDOWN_SECONDS.get(),
                ModCommonConfig.MAX_EFFECT_STACKS.get(),
                ModCommonConfig.GIVE_GUIDE_BOOK_ON_FIRST_JOIN.get(),
                ModCommonConfig.EVENT_RANGE.get(),
                ModCommonConfig.EVENT_APPLY_MC_TEAM.get(),
                ModCommonConfig.EVENT_APPLY_FTB_TEAM.get(),
                ModCommonConfig.EVENT_APPLY_OPAC.get(),
                ModCommonConfig.EVENT_APPLY_MAID.get(),
                ModCommonConfig.HAND_FAN_BIG_RANGE.get(),
                ModCommonConfig.ACTIONBAR_DURATION_TICKS.get(),
                ModCommonConfig.ACTIONBAR_FADE_TICKS.get()));
    }

    private GameplayConfigBinder() {
    }
}
'''

GAMEPLAY_CONFIG_BINDER_FORGE = GAMEPLAY_CONFIG_BINDER_NEO.replace(
    '(1.21.1 侧)', '(1.20.1 侧)')


# ---------------- 工具函数 ----------------
def read(p: Path) -> str:
    return p.read_text(encoding='utf-8')


def rewrite_moved(text: str) -> str:
    """只把「被搬迁类」的 FQN 换成新包;未搬迁类的 FQN 保持不动(它们仍在 astral_dice)。"""
    def sub(m):
        fqn = 'com.merlinkitsune.astral_dice.' + m.group(1)
        # 逐个前缀试探:可能带内部类后缀($ 或 .Nested)
        parts = m.group(1).split('.')
        for i in range(len(parts), 0, -1):
            cand = 'com.merlinkitsune.astral_dice.' + '.'.join(parts[:i])
            if cand in MOVED_FQN:
                return NEW + '.'.join(parts[:i]) + ('.' + '.'.join(parts[i:]) if i < len(parts) else '')
        return fqn
    return OLD_FQN.sub(sub, text)


def strip_comments(src: str) -> str:
    out, i, n, state = [], 0, len(src), 'code'
    while i < n:
        c = src[i]
        nx = src[i + 1] if i + 1 < n else ''
        if state == 'code':
            if c == '/' and nx == '/':
                state = 'line'; i += 2; continue
            if c == '/' and nx == '*':
                state = 'block'; i += 2; continue
            if c in '"\'':
                state = 'str' if c == '"' else 'chr'; out.append(c); i += 1; continue
            out.append(c); i += 1; continue
        if state == 'line':
            if c == '\n':
                state = 'code'; out.append(c)
            i += 1; continue
        if state == 'block':
            if c == '*' and nx == '/':
                state = 'code'; i += 2; continue
            if c == '\n':
                out.append('\n')
            i += 1; continue
        out.append(c)
        if c == '\\':
            if i + 1 < n:
                out.append(src[i + 1])
            i += 2; continue
        if (state == 'str' and c == '"') or (state == 'chr' and c == "'"):
            state = 'code'
        i += 1; continue
    return ''.join(out)


def apply_special(rel: str, text: str, side: str) -> str:
    """针对具体文件的额外改写。"""
    if rel == 'event/SignActiveTriggeredEvent':
        text = text.replace('import net.neoforged.bus.api.Event;',
                            'import com.merlinkitsune.starengine.platform.LoaderEvent;')
        text = text.replace('import net.minecraftforge.eventbus.api.Event;',
                            'import com.merlinkitsune.starengine.platform.LoaderEvent;')
        text = re.sub(r'extends\s+Event\b', 'extends LoaderEvent', text)
        text = text.replace('共享源码', '共享源码')
    elif rel == 'item/BossEntityUtil':
        text = text.replace('import net.neoforged.neoforge.common.Tags;',
                            'import com.merlinkitsune.starengine.platform.LoaderTags;')
        text = text.replace('import net.minecraftforge.common.Tags;',
                            'import com.merlinkitsune.starengine.platform.LoaderTags;')
        text = text.replace('Tags.EntityTypes.BOSSES', 'LoaderTags.BOSSES')
    return text


REFRESH_BLOCK = re.compile(
    r'\n\s*//\s*从配置文件刷新仍保留的可配置项.*?\n\s*public static void refresh\(\)\s*\{.*?\n\s*\}\n',
    re.DOTALL)

NEW_APPLY = '''
    // 应用配置值快照;其余玩法数值固定为上方默认常量。
    // 注意:由各平台侧的配置绑定器调用(见消费方 mod 的 GameplayConfigBinder),
    // 本类不依赖任何加载器的配置 API。
    public static void applyConfig(GameplayConfigValues config) {
        MAX_STARLIGHT = config.maxStarlight();
        MAX_MARKER = config.maxMarker();
        EFFECT_CARD_COOLDOWN_SECONDS = config.effectCardCooldownSeconds();
        MAX_EFFECT_STACKS = config.maxEffectStacks();
        GIVE_GUIDE_BOOK_ON_FIRST_JOIN = config.giveGuideBookOnFirstJoin();

        EVENT_RANGE = config.eventRange();
        EVENT_APPLY_MC_TEAM = config.eventApplyMcTeam();
        EVENT_APPLY_FTB_TEAM = config.eventApplyFtbTeam();
        EVENT_APPLY_OPAC = config.eventApplyOpac();
        EVENT_APPLY_MAID = config.eventApplyMaid();
        HAND_FAN_BIG_RANGE = config.handFanBigRange();

        ACTIONBAR_DURATION_TICKS = config.actionbarDurationTicks();
        ACTIONBAR_FADE_TICKS = config.actionbarFadeTicks();

        // 以下为固定常量对应的派生 tick 值
        SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
        DICE_BLESSING_DURATION_TICKS = DICE_BLESSING_DURATION_SECONDS * 20;
    }
'''


def transform_gameplay_constants(text: str) -> str:
    if 'ModCommonConfig' not in text:
        return text
    text = re.sub(r'^import com\.merlinkitsune\.astral_dice\.config\.ModCommonConfig;\n',
                  '', text, flags=re.MULTILINE)
    text = text.replace('少量仍从配置文件读取', '少量仍可从配置文件调整')
    if not REFRESH_BLOCK.search(text):
        raise SystemExit('!! GameplayConstants: 未能匹配 refresh() 块, 请人工处理')
    text = REFRESH_BLOCK.sub(NEW_APPLY, text)
    return text


# ---------------- 主流程 ----------------
def main():
    print('=' * 100)
    print('StarEngine-Lib 源码迁移' + ('  [干跑]' if DRY else '  [落盘]'))
    print('=' * 100)
    print(f'COMMON={len(COMMON)}  PLATFORM_BOTH={len(PLATFORM_BOTH)}  FORGE_ONLY={len(FORGE_ONLY)}'
          f'  合计={len(MOVED)}')

    plan = []
    writes = []

    # 1) common
    for rel in COMMON:
        src = NEO_PKG / (rel + '.java')
        if not src.exists():
            raise SystemExit(f'!! 缺少源文件: {src}')
        text = rewrite_moved(read(src))
        text = apply_special(rel, text, 'neo')
        if rel == 'component/GameplayConstants':
            text = transform_gameplay_constants(text)
        writes.append((src, LIB_COMMON / (rel + '.java'), text, 'COMMON'))
        plan.append(f'  COMMON        {rel}  -> lib/common')

    # 2) 平台双份
    for rel in PLATFORM_BOTH:
        n = NEO_PKG / (rel + '.java')
        f = FORGE_PKG / (rel + '.java')
        for src, dst, tag in ((n, LIB_NEO / (rel + '.java'), 'lib/neoforge'),
                              (f, LIB_FORGE / (rel + '.java'), 'lib/forge')):
            if not src.exists():
                raise SystemExit(f'!! 缺少源文件: {src}')
            writes.append((src, dst, rewrite_moved(read(src)), 'PLATFORM'))
            plan.append(f'  PLATFORM      {rel}  -> {tag}')

    # 3) 仅 Forge
    for rel in FORGE_ONLY:
        src = FORGE_PKG / (rel + '.java')
        if not src.exists():
            raise SystemExit(f'!! 缺少源文件: {src}')
        writes.append((src, LIB_FORGE / (rel + '.java'), rewrite_moved(read(src)), 'FORGE_ONLY'))
        plan.append(f'  FORGE_ONLY    {rel}  -> lib/forge')

    for line in plan:
        print(line)

    print(f'\n--- 新增文件({len(new_files())}) ---')
    for p in new_files():
        print(f'  NEW {p}')

    # 4) 消费方剩余文件的改写(两侧)
    consumer_edits = {}
    for label, tree in (('neo', NEO_PKG), ('forge', FORGE_PKG)):
        for p in sorted(tree.rglob('*.java')):
            rel = p.relative_to(tree).as_posix()[:-len('.java')]
            if rel in DELETE_BOTH:
                continue
            raw = read(p)
            code = strip_comments(raw)
            new_text = rewrite_moved(raw)
            # 同包引用:文件所在包内有类被搬走,而文件用简单名直接引用 → 需补 import
            pkg = rel.rpartition('/')[0]
            missing = []
            if pkg in MOVED_PKG:
                for r in MOVED:
                    if r.rpartition('/')[0] != pkg:
                        continue
                    s = r.rpartition('/')[2]
                    if re.search(r'\b' + re.escape(s) + r'\b', code) and \
                       not re.search(r'class\s+' + re.escape(s) + r'\b', code) and \
                       not re.search(r'^\s*import\s+static\s+.*\b' + re.escape(s) + r'\b',
                                     new_text, re.MULTILINE):
                        if NEW + r.replace('/', '.') not in new_text:
                            missing.append(r)
            if missing:
                ins = '\n'.join('import ' + NEW + r.replace('/', '.') + ';' for r in sorted(missing))
                m = list(re.finditer(r'^import\s+(?:static\s+)?[^;]+;\s*$', new_text, re.MULTILINE))
                if m:
                    last = m[-1]
                    new_text = new_text[:last.end()] + '\n' + ins + new_text[last.end():]
                else:
                    m2 = re.search(r'^package\s+[^;]+;\s*$', new_text, re.MULTILINE)
                    new_text = new_text[:m2.end()] + '\n\n' + ins + new_text[m2.end():]
            # GameplayConstants.refresh() 已迁至库并改为 applyConfig(值快照),
            # 消费方改调本 mod 的配置绑定器。
            if 'GameplayConstants.refresh()' in new_text:
                new_text = new_text.replace('GameplayConstants.refresh()', 'GameplayConfigBinder.refresh()')
                imp = 'import com.merlinkitsune.astral_dice.config.GameplayConfigBinder;'
                if imp not in new_text:
                    m = list(re.finditer(r'^import\s+(?:static\s+)?[^;]+;\s*$', new_text, re.MULTILINE))
                    new_text = new_text[:m[-1].end()] + '\n' + imp + new_text[m[-1].end():]
            if new_text != raw:
                consumer_edits[label, rel] = (p, new_text, len(missing))

    print(f'\n--- 消费方待改写文件 ---')
    for (label, rel), (p, t, mk) in sorted(consumer_edits.items()):
        print(f'  [{label}] {rel}' + (f'   (+{mk} import)' if mk else ''))

    print(f'\n--- 消费方待删除文件({len(DELETE_BOTH)} x 2 侧) ---')
    for rel in DELETE_BOTH:
        print(f'  DEL neo+forge  {rel}')

    if DRY:
        print('\n[干跑] 未写入任何文件。加 --apply 落盘。')
        return

    # ---------------- 落盘 ----------------
    for src, dst, text, tag in writes:
        dst.parent.mkdir(parents=True, exist_ok=True)
        dst.write_text(text, encoding='utf-8')
    for p, text in new_files().items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding='utf-8')
    for (label, rel), (p, text, mk) in consumer_edits.items():
        p.write_text(text, encoding='utf-8')

    deleted = 0
    for rel in DELETE_BOTH:
        for tree in (NEO_PKG, FORGE_PKG):
            f = tree / (rel + '.java')
            if f.exists():
                f.unlink()
                deleted += 1
    print(f'\n已写入 {len(writes) + len(new_files())} 个文件, 删除 {deleted} 个源文件。')


if __name__ == '__main__':
    main()
