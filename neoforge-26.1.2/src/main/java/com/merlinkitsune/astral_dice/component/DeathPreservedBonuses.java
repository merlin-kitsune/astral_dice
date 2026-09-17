package com.merlinkitsune.astral_dice.component;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死亡保留的两个立牌累计值(2026-09-15 用户裁决):调查员 {@code rin_pages} 与忍者
 * {@code komachi_damage_bonus}。
 *
 * <p><b>为什么需要暂存:</b>死亡掉落在默认 gamerule({@code keepInventory=false})下会把立牌从饰品槽丢出,
 * Curios 的 tick 轮询随即判定"已离身"并回调 {@code ICurio#onUnequip} →
 * {@code BaseSignItem#clearSignData} → 两个立牌各自的 {@code clearSignData} 把这两个键清零;
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

    private static final Map<UUID, int[]> PRESERVED = new ConcurrentHashMap<>();

    private DeathPreservedBonuses() {
    }

    /** 死亡清理阶段调用({@code LivingDeathEvent},旧实体仍在、值尚未被"掉落导致的卸下"清零)。 */
    public static void preserveOnDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        PRESERVED.put(player.getUUID(), new int[] {
                ModAttachments.getRinPages(player),
                ModAttachments.getKomachiDamageBonus(player)
        });
    }

    /** 重生后调用(克隆与重生事件都调用:先到者回写、后到者因表项已被取走而空操作)。 */
    public static void restoreAfterDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int[] preserved = PRESERVED.remove(player.getUUID());
        if (preserved == null) return;
        // 取较大值:兼容"克隆已经带回原值"的路径,并保证重复调用幂等
        if (preserved[RIN_PAGES] > ModAttachments.getRinPages(player)) {
            ModAttachments.setRinPages(player, preserved[RIN_PAGES]);
        }
        if (preserved[KOMACHI_DAMAGE_BONUS] > ModAttachments.getKomachiDamageBonus(player)) {
            ModAttachments.setKomachiDamageBonus(player, preserved[KOMACHI_DAMAGE_BONUS]);
        }
    }
}
