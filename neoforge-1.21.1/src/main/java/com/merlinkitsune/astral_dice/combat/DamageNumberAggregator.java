package com.merlinkitsune.astral_dice.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.network.DamageNumberPayload;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 伤害数字聚合器(服务端,2026-09-26 用户裁决)。
 *
 * <h2>它解决什么问题</h2>
 * 改造前,跳数字由 8 处调用点各自 {@code DamageNumberPayload.send(...)} 发送,而客户端一侧
 * (库 {@code starenginelib} 的 {@code ClientDamageNumbers})是按 <b>entityId 单键</b>存储、
 * <b>后到覆盖先到</b>:
 * <ul>
 *   <li>一次骰战命中会先发「主伤害」、再发「额外加伤」⇒ 前者被后者覆盖,玩家只看到一段;</li>
 *   <li>活体书页命中先发基础值、再由 {@code DamageEffectCardHandler} 发「基础+法伤加成」
 *       ⇒ 只能靠「谁先谁后」的脆弱约定保证显示正确(见 {@code LivingPageImpact} 的注释);</li>
 *   <li>各调用点发的是**结算前**的数值(主伤害发 {@code finalDmg}、活体书页发 {@code base}),
 *       与实际扣血量在「吸收(黄心)」这一环上对不上。</li>
 * </ul>
 *
 * <h2>现在的口径</h2>
 * <ol>
 *   <li><b>取值</b> = 伤害管线终值。本类挂 {@link LivingDamageEvent.Post} —— 该事件在
 *       {@code LivingEntity#actuallyHurt} 里于 {@code setHealth(getHealth() - f1)}
 *       <b>之后</b>触发(1.21.1 源码 {@code LivingEntity.java:1805},
 *       {@code f1 = damageContainers.peek().getNewDamage()}),故 {@code getNewDamage()}
 *       就是「这一次实际扣掉的生命值」(官方 javadoc:「the amount of health this entity lost
 *       during this sequence」)。</li>
 *   <li><b>聚合</b> = 同一 tick 内同一目标的同一组别累加,{@link ServerTickEvent.Post} 时
 *       每组只发**一条**包 ⇒ 一个数字显示该伤害类型的**总值**。</li>
 *   <li><b>组别</b>:攻击力 / 伤害加成类 = {@link Group#ATTACK}(红);
 *       法伤 / 技能伤害类 = {@link Group#SPELL}(绿)。S/AOE/反击一并归入。</li>
 * </ol>
 *
 * <h2>怎么判定组别</h2>
 * 优先读 {@link #tag} 显式登记的标记,其次按伤害类型回落;两者都不命中 ⇒ 不发数字(与改造前
 * 一致:飞星 / 火卡 / 电磁炮雷击 / 绯红骰子自伤等本来就不显示)。
 *
 * <p>⚠️ **为什么必须有显式标记**:骰战主伤害用的是**原版** {@code player_attack} 伤害源
 * (本模组只在 {@code LivingDamageEvent.Pre} 里 {@code setNewDamage} 覆盖数值,不改类型),
 * 且 {@code astral_dice:true_damage} 被多路复用(大当家溅射=物理红 / 效果牌范围波及与法伤加成=法伤绿)
 * ⇒ **只看伤害类型无法区分**。
 *
 * <p>⚠️ 标记按 {@code (entityId, DamageSource 实例身份)} 索引,故**嵌套伤害不会串味**:
 * 大当家溅射(真伤)嵌在骰战主伤害(原版近战)的事件内部,两者的 {@code DamageSource} 不是同一实例,
 * 后者不会把前者登记的标记消费掉。标记与累计值都在每 tick 的 {@link #flush()} 里整体清空,
 * 即使某次 {@code hurt} 被无敌帧/免疫丢弃而没有对应的 {@code Post}(标记漏消费),最多存活一个 tick。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class DamageNumberAggregator {

    /** 攻击力 / 伤害加成类(红)。 */
    public static final int ATTACK_COLOR = 0xFF5555;
    /** 法伤 / 技能伤害类(绿)。 */
    public static final int SPELL_COLOR = 0x7CFC00;

    /** 跳数字的两个组别。颜色即客户端的分槽键(见 {@code client/DamageNumberStore})。 */
    public enum Group {
        ATTACK(ATTACK_COLOR),
        SPELL(SPELL_COLOR);

        private final int color;

        Group(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    /** (entityId, DamageSource 实例身份) → 该次伤害应归入的组别。 */
    private static final Map<Long, Group> TAGS = new HashMap<>();
    /** (entityId, 组别) → 本 tick 的累计值。 */
    private static final Map<Long, Accumulator> PENDING = new HashMap<>();

    private DamageNumberAggregator() {
    }

    /**
     * 在调用 {@code hurt(...)} **之前**声明「接下来这一次伤害归入哪个组别」。
     *
     * @param target 受击者
     * @param source **即将**传给 {@code hurt} 的那个伤害源实例(必须同一实例,见类头)
     * @param group  组别
     */
    public static void tag(LivingEntity target, DamageSource source, Group group) {
        if (target == null || source == null || group == null) return;
        if (target.level().isClientSide()) return;
        TAGS.put(tagKey(target.getId(), source), group);
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        record(target, event.getSource(), event.getNewDamage());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        flush();
    }

    /** 取本次伤害的组别并累计。{@code finalDamage} = 本 tick 该次伤害实际扣掉的生命值。 */
    private static void record(LivingEntity target, DamageSource source, float finalDamage) {
        if (finalDamage <= 0.0f) return;
        Group group = classify(target, source);
        if (group == null) return;

        Accumulator acc = PENDING.get(accumKey(target.getId(), group));
        if (acc == null) {
            // 世界坐标在**首次累计时**冻结:客户端据此锚定,数字不再跟随目标跳动。
            Vec3 anchor = target.getEyePosition().add(0.0, -0.5, 0.0);
            acc = new Accumulator(target, group, anchor.x, anchor.y, anchor.z);
            PENDING.put(accumKey(target.getId(), group), acc);
        }
        acc.total += finalDamage;
    }

    /** 组别判定:显式标记优先,其次按伤害类型回落,都不命中则返回 {@code null}(不显示)。 */
    private static Group classify(LivingEntity target, DamageSource source) {
        Group tagged = TAGS.remove(tagKey(target.getId(), source));
        if (tagged != null) return tagged;

        // 回落只认「本模组自定义、且由玩家造成」的伤害类型;原版类型一律不显示。
        boolean byPlayer = source.getEntity() instanceof Player && source.getEntity() != target;
        if (!byPlayer) return null;
        if (source.is(ModDamageTypes.DICE_DAMAGE) || source.is(ModDamageTypes.EXTRA_DAMAGE)) {
            return Group.ATTACK;
        }
        if (source.is(ModDamageTypes.CARD_SPELL) || source.is(ModDamageTypes.SKILL_DAMAGE)) {
            return Group.SPELL;
        }
        // astral_dice:true_damage 多路复用(溅射红 / 范围波及与法伤加成绿)⇒ 无标记者不显示。
        return null;
    }

    /** 每 server tick 末尾结算:每 (目标, 组别) 只发一条包,取整后为该类型的总值。 */
    private static void flush() {
        TAGS.clear();
        if (PENDING.isEmpty()) return;
        var snapshot = new ArrayList<Accumulator>(PENDING.values());
        PENDING.clear();
        for (Accumulator acc : snapshot) {
            int total = Math.round(acc.total);
            if (total <= 0) continue;
            DamageNumberPayload.send(acc.target, total, acc.group.color(), acc.x, acc.y, acc.z);
        }
    }

    private static long tagKey(int entityId, DamageSource source) {
        return ((long) entityId << 32) | (System.identityHashCode(source) & 0xFFFFFFFFL);
    }

    private static long accumKey(int entityId, Group group) {
        return ((long) entityId << 32) | group.ordinal();
    }

    private static final class Accumulator {
        final LivingEntity target;
        final Group group;
        final double x;
        final double y;
        final double z;
        float total;

        Accumulator(LivingEntity target, Group group, double x, double y, double z) {
            this.target = target;
            this.group = group;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
