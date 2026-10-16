package com.merlinkitsune.astral_dice.component;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死亡保留的立牌累计值与诅咒之剑筹码加成(2026-09-15 用户裁决 + 2026-09-27 蛟龙立牌 + 2026-10-01 诅咒之剑
 * + 2026-10-16 超天酱/地雷女):调查员 {@code rin_pages}、忍者 {@code komachi_damage_bonus}、蛟龙
 * {@code mamushi_awakening}(第 3 槽位)与诅咒之剑 {@code cursed_sword_bonus}(第 4 槽位)四个 {@code int} 槽位;
 * 2026-10-16 追加**三个** {@code int} 槽位 —— 超天酱 {@code kangel_fan_count}、地雷女
 * {@code ame_love_layers} 与 {@code ame_love_cap} —— 以及一个字符串槽位 {@code kangel_fan_targets}
 * (已登记目标 UUID 的逗号分隔集,放不进 {@code int[]} ⇒ 用并行 {@code Map<UUID, String>} 暂存)。
 *
 * <p><b>为什么需要暂存:</b>死亡掉落在默认 gamerule({@code keepInventory=false})下会把立牌从饰品槽丢出,
 * Curios 的 tick 轮询随即判定"已离身"并回调 {@code ICurio#onUnequip} →
 * {@code BaseSignItem#clearSignData} → 各立牌各自的 {@code clearSignData} 把它们负责的键清零;
 * 而该回调**早于**玩家克隆(1.21.1 附件 {@code .copyOnDeath()};1.20.1 见
 * {@code AstralData#onPlayerClone} 的死亡分支),于是"死亡保留"会被抢先抹掉。故在
 * {@code LivingDeathEvent}(此刻值仍在)暂存,待克隆/重生后回写。
 *
 * <p><b>为什么用静态表:</b>与 {@code ChargeManager} 的充能死亡保留同一模式(按 UUID 暂存、取走即删)。
 * 局限:本表只在**同一 JVM 会话**内有效——死亡后未重生就退出世界/关服则不再保留
 * (单人模式"退出到标题"不会卸载类,表项会跨世界存活;因重生事件只由真正的重生流程触发、
 * 且任一新世界的死亡都会先覆盖该表项,故不会把旧世界的值写进新世界)。
 *
 * <p><b>与本类无关的部分:</b>本类只负责"值不丢"。值是否作为伤害加成**生效**另由佩戴判定,
 * 见 {@code SpellDamageRegistry#effectCardDamageBonus}/{@code #livingPageBonusPages}(不戴立牌不加成)。
 */
public final class DeathPreservedBonuses {

    private static final int RIN_PAGES = 0;
    private static final int KOMACHI_DAMAGE_BONUS = 1;
    /**
     * 蛟龙立牌(mamushi)觉醒层数(第 3 个槽位,2026-09-27)。
     *
     * <p>为什么必须在这里也存一份:该附件本身已是 {@code .copyOnDeath()},但**死亡时立牌先掉出饰品槽**,
     * Curios 的 tick 轮询会先判定"已离身"并回调 {@code onUnequip → clearSignData},
     * 而 {@code MamushiSignItem#clearSignData} 按 D4「只有卸下立牌才归零」把觉醒清零 —— 该回调
     * **早于**玩家克隆的附件复制 ⇒ 单靠 {@code .copyOnDeath()} 会被抢先抹掉。
     * 与 {@code rin_pages} 的既有做法逐字相同(两层齐备:附件标记 + 本表兜底)。
     */
    private static final int MAMUSHI_AWAKENING = 2;

    /** 第 4 槽位:诅咒之剑筹码({@code cursed_sword_bonus})累计攻击力加成(2026-10-01) */
    private static final int CURSED_SWORD_BONUS = 3;

    /**
     * 第 5 槽位:超天酱立牌(kangel)「小天使请安」粉丝数({@code kangel_fan_count},2026-10-16)。
     *
     * <p>该附件本身已是 {@code .copyOnDeath()},但死亡时立牌先掉出饰品槽 ⇒ Curios 的 tick 轮询先判定
     * "已离身"并回调 {@code onUnequip → clearSignData}({@code KangelSignItem#clearSignData} 把粉丝数
     * 归零)、**早于**玩家克隆的附件复制 ⇒ 单靠 {@code .copyOnDeath()} 会被抢先抹掉,须本表兜底
     * (与 {@code rin_pages} 的两层齐备写法一致)。
     */
    private static final int KANGEL_FAN_COUNT = 4;

    /**
     * 第 6 槽位:地雷女立牌(ame)「爱」层数({@code ame_love_layers},2026-10-16)。
     * {@code AmeSignItem#clearSignData} 在卸下(含死亡掉落导致的卸下)时把层数清零 ⇒ 同前,需本表兜底。
     */
    private static final int AME_LOVE_LAYERS = 5;

    /**
     * 第 7 槽位:地雷女立牌(ame)「爱」层数上限({@code ame_love_cap},2026-10-16)。
     * {@code AmeSignItem#clearSignData} 卸下时把它复位为默认值({@code AmeSignItem#AME_LOVE_CAP_DEFAULT})
     * ⇒ 与层数同寿命,须一并暂存。回写口径同前 6 槽(取较大值):上限只随主动「感情过量摄入」自然到期而
     * 增长,故取大即"死亡不掉"。
     */
    private static final int AME_LOVE_CAP = 6;

    /** 第 1..7 槽位共用的暂存表(按 UUID 暂存、取走即删)。 */
    private static final Map<UUID, int[]> PRESERVED = new ConcurrentHashMap<>();

    /**
     * 字符串槽位暂存表:超天酱已登记目标集({@code kangel_fan_targets},逗号分隔 UUID)。
     *
     * <p>为什么单独一张表:{@link #PRESERVED} 的载荷是定长 {@code int[]},放不下字符串。与 int 槽位**同寿命**
     * (用户原话「粉丝数永久有效…死亡后不会清空」⇒ 目标集必须与计数同进同退,否则重生后计数保留而目标集
     * 清空,同一目标会被重复计成新粉丝)。同样按 UUID 暂存、取走即删。
     */
    private static final Map<UUID, String> PRESERVED_TARGETS = new ConcurrentHashMap<>();

    private DeathPreservedBonuses() {
    }

    /** 死亡清理阶段调用({@code LivingDeathEvent},旧实体仍在、值尚未被"掉落导致的卸下"清零)。 */
    public static void preserveOnDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        PRESERVED.put(player.getUUID(), new int[] {
                ModAttachments.getRinPages(player),
                ModAttachments.getKomachiDamageBonus(player),
                ModAttachments.getMamushiAwakening(player),
                ModAttachments.getCursedSwordBonus(player),
                // 2026-10-16 超天酱/地雷女:粉丝数 / 爱层数 / 爱层数上限(卸下即清 ⇒ 同需兜底)
                ModAttachments.getKangelFanCount(player),
                ModAttachments.getAmeLoveLayers(player),
                ModAttachments.getAmeLoveCap(player)
        });
        // 字符串槽位(超天酱已登记目标集)放不进 int[] ⇒ 并行 Map,同样取走即删
        PRESERVED_TARGETS.put(player.getUUID(), ModAttachments.getKangelFanTargets(player));
    }

    /** 重生后调用(克隆与重生事件都调用:先到者回写、后到者因表项已被取走而空操作)。 */
    public static void restoreAfterDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int[] preserved = PRESERVED.remove(player.getUUID());
        String preservedTargets = PRESERVED_TARGETS.remove(player.getUUID());
        if (preserved != null) {
            // 取较大值:兼容"克隆已经带回原值"的路径,并保证重复调用幂等
            if (preserved[RIN_PAGES] > ModAttachments.getRinPages(player)) {
                ModAttachments.setRinPages(player, preserved[RIN_PAGES]);
            }
            if (preserved[KOMACHI_DAMAGE_BONUS] > ModAttachments.getKomachiDamageBonus(player)) {
                ModAttachments.setKomachiDamageBonus(player, preserved[KOMACHI_DAMAGE_BONUS]);
            }
            // 蛟龙立牌:觉醒层数同样取较大值(死亡不掉层;重生后由立牌 tick 依据层数自动补回真龙形态效果)
            if (preserved[MAMUSHI_AWAKENING] > ModAttachments.getMamushiAwakening(player)) {
                ModAttachments.setMamushiAwakening(player, preserved[MAMUSHI_AWAKENING]);
            }
            // 诅咒之剑筹码:累计攻击力加成死亡不清(只有真正卸下筹码才归零)⇒ 取较大值幂等
            if (preserved[CURSED_SWORD_BONUS] > ModAttachments.getCursedSwordBonus(player)) {
                ModAttachments.setCursedSwordBonus(player, preserved[CURSED_SWORD_BONUS]);
            }
            // 超天酱立牌:粉丝数死亡不清(只有真正卸下立牌才归零)⇒ 取较大值幂等
            if (preserved[KANGEL_FAN_COUNT] > ModAttachments.getKangelFanCount(player)) {
                ModAttachments.setKangelFanCount(player, preserved[KANGEL_FAN_COUNT]);
            }
            // 地雷女立牌:爱层数与层数上限同寿命,取较大值(重生后由立牌 tick 依据层数重挂镜像效果)
            if (preserved[AME_LOVE_LAYERS] > ModAttachments.getAmeLoveLayers(player)) {
                ModAttachments.setAmeLoveLayers(player, preserved[AME_LOVE_LAYERS]);
            }
            if (preserved[AME_LOVE_CAP] > ModAttachments.getAmeLoveCap(player)) {
                ModAttachments.setAmeLoveCap(player, preserved[AME_LOVE_CAP]);
            }
        }
        // 超天酱立牌:已登记目标集(字符串)只有"当前为空串且暂存非空"才回写 ⇒ 幂等;
        // 附件本身已是 copyOnDeath,克隆已带回时(非空)不覆盖,避免抹掉克隆之后新登记的目标。
        if (preservedTargets != null && !preservedTargets.isEmpty()
                && ModAttachments.getKangelFanTargets(player).isEmpty()) {
            ModAttachments.setKangelFanTargets(player, preservedTargets);
        }
    }
}
