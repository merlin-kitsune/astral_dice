package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.RepulsionItem;
import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 强化拒止实体（效果牌「强化拒止」的可见部分，2026-10-18 新牌；属「怪力乱神」立牌；
 * 2026-10-18 移植到 forge-1.20.1；同日按用户追加规则补〈吸附资格〉与〈苦力怕〉两段，与 neo 线逐字对等）。
 *
 * <p><b>形态</b>：只有实体，**没有任何碰撞方块**（实体本身仍有 1.6×2.4 的拾取 / 攻击判定框，
 * 但 {@link #hurt} 只认**玩家来源** ⇒ 怪物 / 环境攻击无效、玩家可以主动打掉，见〈可被玩家拆除〉）；
 * {@code noPhysics = true}
 * ⇒ 不参与任何方块碰撞 / 推动。{@code extends LivingEntity}（**不**用 Mob AI）只为拿到生命值
 * 同步通道：耐久直接用 {@link #getHealth()} 承载（{@code MAX_HEALTH = 50}，由
 * {@code event/ModEntityEvents} 在 {@code EntityAttributeCreationEvent} 注册）。
 *
 * <p><b>吸附（用户需求原文）</b>：「强化拒止会自动吸附8格范围内的敌对目标，并对其持续施加
 * 缓慢和虚弱效果。强化拒止每10秒消耗吸附目标数量的耐久度（单次最大消耗5点）」
 * ＋「会以1秒为频率将半径8格范围敌对目标向强化拒止的位置逐渐缓慢拉扯（但目标无法逃离
 * 强化拒止影响范围）」＋「强化拒止不会吸引仇恨，但会持续为怪物施加 缓慢 (0:10)和虚弱 (0:10)」。
 *
 * <p><b>三层节拍</b>：
 * <ol>
 *   <li><b>每 tick</b>（{@link #tickTrackedTargets()} → {@link #controlTarget(UUID, Mob)}）：维护
 *       「被吸附目标」集合 —— 死亡 / 移除 / 距离 &gt; 32 格 / **失去吸附资格**
 *       （{@link #isAttachable(Mob)}）的目标剪掉并释放状态；对剩下的受控目标每 tick 做
 *       ① 飞行目标关重力（原值记录、脱控恢复）② 瞬移 / 闪现拦截 ③ 出 11.5 格硬钳制
 *       ④ **每 tick 向心牵引**（指数收敛，见 {@link #pullTowardAnchor(Mob)}）
 *       ⑤ 纵向钳制（遁地）；</li>
 *   <li><b>每 20 tick（1 秒）</b>（{@link #scanAndApplyEffects()}）：扫描 12 格内**有吸附资格**、
 *       存活、{@code distanceToSqr <= 144} 的目标（资格 = 敌对生物 / 中立生物 + 当前有仇恨目标
 *       或被激怒 + 非已驯服宠物，见〈吸附资格〉），加入集合并施加 缓慢 (0:10) / 虚弱 (0:10)
 *       （{@code amplifier = 0}）；**新**目标随之给放置者 +1 充能（见 {@link #awardCharge(UUID)}）；</li>
 *   <li><b>每 200 tick（10 秒）</b>（{@link #consumeDurability()}）：耐久
 *       {@code -= min(当前 12 格内被吸附目标数, 5)}；扣到 &lt;= 0 ⇒ 碎裂粒子 + 玻璃破碎音 +
 *       {@link #discard()}。</li>
 * </ol>
 *
 * <p><b>吸附资格（2026-10-18 用户追加规则）</b>：候选 = <b>敌对生物</b>（{@code instanceof Enemy}）
 * <b>或</b> <b>中立生物</b>（{@code instanceof NeutralMob}）；且<b>必须有仇恨 / 激怒状态</b>
 * （{@code mob.getTarget() != null} 或 中立生物 {@code NeutralMob#isAngry()}）；且<b>不是已驯服的宠物</b>
 * （{@code TamableAnimal#isTame()}，即狼 / 猫 / 鹦鹉）。⇒「没有仇恨目标的敌对生物」（未激怒的末影人、
 * 无目标游荡的僵尸）与玩家宠物**都不会**被新增吸附。判据落在 {@link #isAttachable(Mob)}。
 * <ul>
 *   <li>⚠️ 这是**有意的口径收窄**（用户原话「排除没有仇恨目标的敌对（如末影人）」），**不**调用模组
 *       统一入口 {@code starengine} 的 {@code HostileTargets}：统一入口对中立生物一律放行且不看
 *       anger，与本需求相反。同类收窄的既有先例 = {@code combat/ShootingStarManager#isStarTarget}
 *       （飞星只认已激怒的中立生物）。<b>不得</b>以「敌对判定必须走统一入口」为由把本判据改宽。</li>
 *   <li>资格在**扫描时**（每 20 tick，决定「是否新增吸附」）与**每 tick 剪枝**（决定「是否继续吸附」）
 *       两处都判定 ⇒ <b>平静即释放</b>：被激怒而吸附的中立生物一旦平静、以及失去仇恨目标的敌对生物，
 *       会在**下一个 tick** 被移出集合 —— 不再施加 缓慢 / 虚弱、不再被拉扯 / 钳制，也不再计入耐久消耗；
 *       已施加的 缓慢 / 虚弱 按原时长自然结束（**不主动清除**）。</li>
 * </ul>
 *
 * <p><b>苦力怕（2026-10-18 用户追加规则）</b>：被吸附的 {@code Creeper} **不允许自爆**，两条路径叠加：
 * <ol>
 *   <li><b>自然膨胀</b>（{@link #suppressTrackedCreeperFuses()}，**每 tick**）：被吸附集合内的苦力怕
 *       {@code getSwellDir() > 0} ⇒ {@code setSwellDir(-1)}。原版 {@code Creeper#tick} 是
 *       {@code swell += swellDir}（先读方向、再累加、之后才跑 AI），写入 -1 后下一次累加变成 -1，
 *       两种 tick 顺序下 swell 要么回落到 0、要么停在当前值 ⇒ 涨不到引爆阈值；</li>
 *   <li><b>打火石点燃</b>（{@code event/RepulsionCreeperGuard}，订阅 {@code ExplosionEvent.Start}）：
 *       {@code isIgnited()} 会让 {@code Creeper#tick} **每 tick 无条件** {@code setSwellDir(1)}（且在读值
 *       之前）⇒ 第 1 条压不住，引信仍会烧到底。故必须再叠「爆炸开始事件」取消：命中即
 *       {@code setCanceled(true)} + {@code setSwellDir(-1)} + {@code FIRE_EXTINGUISH}。
 *       取消点在 {@code Level#explode} 的 {@code explosion.explode()} 之前 ⇒ 不炸方块、不造成伤害、
 *       无爆炸粒子 / 音效；但 {@code explodeCreeper()} 里 {@code dead = true} 与随后的 {@code discard()}
 *       仍会执行 ⇒ 表现是苦力怕**无声消失**（既定代价，见该类 javadoc）。</li>
 * </ol>
 * 只对**被吸附**的苦力怕生效（事件层用 {@link #isTracking(UUID)} 判定）；TNT、其他爆炸、未被吸附的
 * 苦力怕一律不受影响。
 *
 * <p><b>拉力机制（2026-10-21 重做：旧「每秒一次 0.22 冲量」实测只有 0.2–0.5 格/秒，用户判「太弱」）</b>：
 * <ul>
 *   <li>改为**每 tick 直接改位置**（{@link #pullTowardAnchor(Mob)}），不再走 {@code setDeltaMovement}：
 *       后者每 tick 会被 {@code aiStep} 的移动输入与 {@code travel} 的摩擦重算，既拉不动，
 *       又存在「逐 tick 加速 ⇒ 弹射」的隐患；</li>
 *   <li>步长按剩余距离**指数收敛**并封顶：{@code min(0.85, max(0.08, (d - 1.2) × 0.35))} 格/tick
 *       ⇒ 12 格内约 15 tick（0.75 秒）贴到中心 1.2 格；越近步长越小，**不会过冲 / 抖动**；</li>
 *   <li>落点先做 {@code Level#noCollision} 检查：可用则 {@code setPos}，被占则退回原版
 *       {@code move(MoverType.SELF, …)} 由碰撞裁剪 ⇒ **不穿模**；</li>
 *   <li>每次位移后统一 {@code setDeltaMovement(ZERO)} + {@code hurtMarked = true} ⇒ 速度从不累积，
 *       客户端靠原版位置 / 速度同步跟上，**不发自定义包**。</li>
 * </ul>
 *
 * <p><b>不吸引仇恨</b>：全程**不调用任何 {@code setTarget}**，也不清除目标原有仇恨 ——
 * 与「路障」（{@code BarricadeEntity}，主动改写仇恨）是**相反**的语义，两者不得混用判据。
 *
 * <p><b>可被玩家回收（2026-10-25 用户裁决，取代 2026-10-22 的「玩家可拆除」）</b>：
 * {@link #hurt} 只认**玩家本人的近战 / 扫击**（{@code source.getDirectEntity() instanceof Player}
 * ⇒ 玩家射出的箭 / 三叉戟的 directEntity 是弹射物本身，自动排除）：命中**不再扣耐久**，只累加
 * {@link #meleeHits}；累计 {@link #MAX_MELEE_HITS} 次 ⇒ {@link #recoverIntoItem(Player)} 把装置
 * 回收成**物品形式**（{@code astral_dice:repulsion}，带走耐久 / 剩余寿命 / 已发放充能），
 * 再次放置由 {@link #restoreState(float, long, int)} 恢复。怪物 / 环境伤害**仍然无效**
 * ⇒「不吸引仇恨」与「玩家能把装置收走」两个口径同时成立。
 * <p>{@code /kill} 这类「显式判死」走 {@code Entity#kill()}，本类已覆写 {@link #kill()} 直接接到
 * {@link #shatter()}（2026-10-22 用户反馈「/kill 打不掉」）⇒
 * {@code /kill @e[type=astral_dice:repulsion,distance=..64]} 可用；管理员兜底
 * {@code /astralparty killrepulsion [radius|all]}（2026-10-22 新增，一次清一片，并额外释放
 * 被控目标的脱控状态）同样可用。
 *
 * <p><b>持久化</b>：被吸附目标集合以 {@code Set<UUID>} 存字段，NBT 落盘为
 * {@code long[]}（每目标 2 个 long = most/least significant bits，避免依赖
 * {@code NbtUtils} 的 UUID 包装类型）。已发放的充能点数
 * （{@code RepulsionChargeGiven}，{@code int}，0..{@link #MAX_CHARGE_STACKS}）同样落盘
 * ⇒ 重启后不会重复发满 5 点；「换过充能的目标」与瞬移参照点、重力原值都是纯内存态。
 * ⚠️ 落盘的只是 UUID 集合：{@link ServerLevel#getEntity(UUID)} 在**区块未加载 / 跨维度**时返回
 * {@code null} ⇒ 该目标会被剪枝（**不保证跨卸载保留**；卸载再加载后要等下一次 12 格扫描才会
 * 重新吸附，期间不施加 缓慢 / 虚弱、也不计耐久）。
 *
 * <p><b>1.20.1 平台差异</b>（相对 neoforge-1.21.1 蓝本，均按本线源码核实；**代码逐字相同**）：
 * <ul>
 *   <li>{@code MobEffects.MOVEMENT_SLOWDOWN} / {@code MobEffects.WEAKNESS} 在 1.20.1 是裸
 *       {@code MobEffect}（1.21.1 是 {@code Holder<MobEffect>}），而
 *       {@code new MobEffectInstance(MobEffect, int, int, boolean, boolean, boolean)} 两线都存在
 *       ⇒ 源码字面一致，**不需要** 1.21.1 侧的 {@code .get()}（本类原文本就只写常量名）；</li>
 *   <li>{@code addAdditionalSaveData} / {@code readAdditionalSaveData}：{@code Entity} 里是
 *       {@code protected abstract}，**{@code LivingEntity} 已放宽为 {@code public}**
 *       （1.20.1 源码 {@code LivingEntity.java:680 / :712}）⇒ 覆写体写 {@code public}，与蓝本同形；</li>
 *   <li>{@code shouldDropExperience()} / {@code shouldDropLoot()} 在 1.20.1 的
 *       {@code LivingEntity} 里同样可覆写（{@code LivingEntity.java:561} 为 {@code public boolean}）；</li>
 *   <li>2026-10-18 追加规则新增的成员两线同签名（已从本机 {@code forge-1.20.1-47.4.10-sources.jar} 逐条核对：
 *       {@code NeutralMob#isAngry()}（默认方法）{@code NeutralMob.java:91}、
 *       {@code TamableAnimal#isTame()} 公有无参 {@code TamableAnimal.java:109}、
 *       {@code Creeper#getSwellDir()} / {@code #setSwellDir(int)} {@code Creeper.java:198,205}）；</li>
 *   <li>其余用到的成员（{@code ServerLevel#getEntity(UUID)} / {@code #sendParticles} /
 *       {@code Level#playSound} / {@code Entity#teleportTo(double,double,double)} /
 *       {@code Level#noCollision(Entity, AABB)} / {@code setDeltaMovement} / {@code push} /
 *       {@code isPushable} / {@code discard} / {@code CompoundTag#putLongArray} /
 *       {@code getEntitiesOfClass}）两线同签名。</li>
 * </ul>
 *
 * <p><b>⚠️ 实机未验证项</b>（本轮按纪律不启动游戏）：
 * <ul>
 *   <li><b>拉怪手感</b>：新步长公式（0.08–0.85 格/tick、指数收敛、停止圈 1.2 格）是按数学推演定的，
 *       未实机读数；过快 / 过慢只调 {@link #PULL_RATE} 与 {@link #PULL_MAX_STEP} 两个量；</li>
 *   <li><b>末影人是否还闪</b>：本线靠「单 tick 位移 &gt; 2 格 ⇒ 拉回」兜底（1.20.1 另有
 *       {@code EntityTeleportEvent.EnderEntity} 事件取消），若实体在力场 tick **之后**才闪现，
 *       最多可见 1 tick 的闪动 —— 需实机确认观感；</li>
 *   <li><b>飞行 / 遁地</b>：飞行目标 {@code setNoGravity(true)}、脚底硬钳在锚点 −0.5 格以内，
 *       是否「压得住」且脱控后能正常恢复，需实机确认（尤其幻翼／恼鬼的脱控恢复）；</li>
 *   <li>模型朝向 / 贴图面序（见 {@code client/RepulsionModel} 的说明）。</li>
 *   <li>苦力怕「禁止自爆」：逻辑按两线源码推演（{@code Creeper#tick} 的 {@code swell += swellDir}
 *       与 {@code Level#explode} 里 {@code explosion.explode()} 的先后），未实机；尤其「打火石点燃
 *       路径」取消爆炸后苦力怕会**无声消失**（{@code dead = true} 先于 explode 设置）的观感需实机确认；</li>
 *   <li>吸附资格：未激怒的末影人 / 无目标僵尸应当**不被**吸附，驯服狼 / 猫应当**不被**吸附 ——
 *       判据按源码写死（{@code NeutralMob#isAngry()} / {@code TamableAnimal#isTame()}），未实机核对。</li>
 * </ul>
 *
 * <p><b>已登记风险</b>：{@code hurt} 只认玩家 ⇒ 若被**第三方 goal** 选为目标，该 goal 会永远
 * 打不死本实体（原版敌对 goal 只选 {@code Player} / {@code Villager} / {@code Monster}，通常
 * 不命中本实体；第三方若点名 {@code LivingEntity} 才会出现，每 tick 的视线 / 距离判定开销极小，
 * 但如实登记在此）。
 */
public class RepulsionEntity extends LivingEntity {

    /** 吸附 / 影响半径（格，球形；用户需求「半径12格」，2026-10-21 由 8 提升）。 */
    public static final double AFFECT_RADIUS = 12.0D;

    /** 越界目标的「拉回」半径（格，**3D** 距离）：比 {@link #AFFECT_RADIUS} 略小，
     * 保证拉回后 3D 距离 11.5 ≤ 12，不会每 tick 反复瞬移。 */
    public static final double CLAMP_RADIUS = 11.5D;

    /** 遗忘半径（格）：超出即从被吸附集合里剪掉（用户需求「无法逃离影响范围」仍以 12 格为准，
     * 32 格只是「跑太远就彻底不管」的清理阈值）。 */
    public static final double FORGET_RADIUS = 32.0D;

    /*
     * ── 拉怪强度（2026-10-21 用户裁决「拉怪强度太低」后重做）────────────────────────
     * 旧机制：每 20 tick 用 setDeltaMovement 写一次 0.22 的水平冲量（实测约 0.2–0.5 格/秒）。
     * 新机制：**每 tick 直接改位置**（不经 delta ⇒ 不会被 AI 每 tick 重算抹掉、也绝不会累积成弹射），
     * 位移量按「剩余距离的固定比例」指数收敛，并对单 tick 位移封顶：
     *   步长 = clamp((距离 - PULL_STOP_RADIUS) × PULL_RATE, PULL_MIN_STEP, PULL_MAX_STEP)
     * 指数收敛 ⇒ 越近越慢、永不越过停止圈（无过冲 ⇒ 无抖动、无甩飞）。12 格 → 1.2 格约 15 tick。
     */

    /** 牵引停止半径（格，3D）：目标与本实体距离缩到该值即「已贴脸」，不再施加位移。 */
    private static final double PULL_STOP_RADIUS = 1.2D;

    /** 每 tick 收敛比例（剩余距离的 35%）：指数收敛，越近越慢，天然不过冲。 */
    private static final double PULL_RATE = 0.35D;

    /** 单 tick 最大位移（格）：封顶避免「一帧甩飞」；0.85 已是 17 格/秒。 */
    private static final double PULL_MAX_STEP = 0.85D;

    /** 单 tick 最小位移（格）：贴近停止圈时仍缓慢贴合，不被目标自身 AI 抵消成僵持。 */
    private static final double PULL_MIN_STEP = 0.08D;

    /** 纵向硬钳制余量（格）：目标脚底低于本实体 Y 超过该值 ⇒ 直接抬回（治「遁地」）。 */
    private static final double VERTICAL_CLAMP = 0.5D;

    /** 瞬移判定阈值（格 / 单 tick）：超过它且不是自由落体 ⇒ 判为瞬移 / 闪现，拉回上一位置。 */
    private static final double TELEPORT_JUMP = 2.0D;

    /** 扫描 / 施加 缓慢 / 虚弱 的周期（tick；用户需求「以1秒为频率」）。牵引已改为每 tick。 */
    private static final int SCAN_INTERVAL = 20;

    /** 耐久结算周期（tick；用户需求「每10秒消耗」）。 */
    private static final int DURABILITY_INTERVAL = 200;

    /** 缓慢 / 虚弱 的时长（tick；用户需求「缓慢 (0:10)和虚弱 (0:10)」）。 */
    private static final int EFFECT_DURATION = 200;

    /** 单次耐久结算的最大消耗（点；用户需求「单次最大消耗5点」）。 */
    private static final int MAX_DURABILITY_COST = 5;

    /**
     * 近战回收所需命中次数（2026-10-25 用户裁决：玩家**不再能把强化拒止打掉**，改为「对其近战
     * 5 次 ⇒ 重新掉落<b>物品形式</b>的强化拒止（不是效果牌）」）。
     *
     * <p>为什么改成「次数」而不是「伤害」：同一批改动把耐久改成**由物品承载的关联血量**（回收时随
     * 物品带走、再次放置时恢复）。若命中仍扣耐久，玩家一次近战就会同时推动两条进度（回收计数 +
     * 血量），语义互相污染；改成纯计数后，「打 5 次」与「耐久随时间被吸附目标消耗」是两条独立进度。
     */
    public static final int MAX_MELEE_HITS = 5;

    /** NBT 键：已累计的玩家近战命中次数（{@code int}；上限 {@link #MAX_MELEE_HITS}）。 */
    private static final String TAG_MELEE_HITS = "RepulsionMeleeHits";

    /** 碎裂粒子数量（用户需求「耐久耗尽会有碎裂粒子效果」；设计要求 CRIT 至少 16 个）。 */
    private static final int SHATTER_CRIT_COUNT = 32;
    private static final int SHATTER_POOF_COUNT = 8;

    /** NBT 键：被吸附目标（long[]，每目标 2 个 long）。 */
    private static final String TAG_TARGETS = "RepulsionTargets";

    /** 被吸附目标（服务端权威）。⚠️ 落盘的是 UUID 集合；{@code ServerLevel#getEntity} 在未加载 /
     * 跨维度时返回 null ⇒ 该目标会被剪枝（**不保证跨卸载保留**）。 */
    private final Set<UUID> trackedTargets = new HashSet<>();

    /** NBT 键：放置者 UUID（long[2]；2026-10-18 随怪力乱神立牌 lingling 被动新增，规格 §4.6）。 */
    private static final String TAG_OWNER = "RepulsionOwner";

    /**
     * 单个强化拒止可提供的充能层上限（用户裁决 2026-10-20：按**每个力场**各 5 点，
     * <b>不是</b>全局 5 点 ⇒ 多座力场各自封顶后相加）。
     */
    public static final int MAX_CHARGE_STACKS = 5;

    /** NBT 键：本力场**已实际发放**给放置者的充能点数（{@code int}；上限 {@link #MAX_CHARGE_STACKS}）。 */
    private static final String TAG_CHARGE_GIVEN = "RepulsionChargeGiven";

    /** NBT 键：创建时刻（{@code Level#getGameTime()} 绝对值；**不是** tickCount —— 后者重载会归零）。 */
    private static final String TAG_CREATED_AT = "RepulsionCreatedAt";

    /** 一生寿命（tick）：20:00 = 1200 秒 = 24000 tick，到点 {@link #discard()}。 */
    private static final long LIFETIME_TICKS = 24000L;

    /** {@link #createdAt} 的「尚未记录」哨兵（合法的 gameTime 不会是 Long.MIN_VALUE）。 */
    private static final long CREATED_AT_UNSET = Long.MIN_VALUE;

    /** 创建时刻（{@code level().getGameTime()}）；{@link #CREATED_AT_UNSET} = 首 tick 时补记。 */
    private long createdAt = CREATED_AT_UNSET;

    /**
     * 已**实际发放**给放置者的充能点数（落盘）。2026-10-21 用户裁决：吸附到一个新目标就立刻给放置者
     * +1 真实充能，**永久到账** —— 玩家走远 / 目标脱离 / 本力场碎裂或被拆一律**不回收**。
     * 单座力场最多 {@link #MAX_CHARGE_STACKS} 点，给满后不再累加。
     */
    private int chargeGiven;

    /** 已累计的玩家近战命中次数（落盘）；达到 {@link #MAX_MELEE_HITS} 即 {@link #recoverIntoItem(Player)}。 */
    private int meleeHits;

    /**
     * 本生命周期内「已经换过充能」的目标 UUID（**纯内存，不落盘**）。
     * 只做去重：同一目标反复进出 12 格不重复发充能；只在**发放成功**时写入，
     * 因此大小天然有界（≤ {@link #MAX_CHARGE_STACKS}）。
     */
    private final Set<UUID> chargedTargets = new HashSet<>();

    /** 受控目标上一 tick 的有效位置（**纯内存**）：瞬移 / 闪现检测的参照点，脱控即清除。 */
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();

    /** 受控目标的 {@code isNoGravity()} 原值（**纯内存**）：只对飞行目标写入，脱控 / 销毁时恢复。 */
    private final Map<UUID, Boolean> originalNoGravity = new HashMap<>();

    /**
     * 放置者（**持久化**）。1.21.1 侧 {@code RepulsionEntity} 本来就有 {@code ownerUUID}
     * （{@code item/card/RepulsionCardItem#applyEffect} 放置时写入），1.20.1 侧此前**没有** ——
     * 怪力乱神立牌被动要求「只算自己放置的拒止吸附了多少目标」与「攻击被吸附目标 +5」，
     * 无 owner 无从判定，故本次补齐（语义与 1.21.1 逐位一致）。
     *
     * <p>null = 旧存档 / 命令生成 / 无主的拒止 —— 两处被动都**不计入**（宁可少算，不误算他人）。
     */
    private UUID ownerUUID;

    public RepulsionEntity(EntityType<? extends RepulsionEntity> entityType, Level level) {
        super(entityType, level);
        // 强化拒止是「钉在地上的装置」：不参与物理、不受重力、不会被生物 / 爆炸 / 水流推动
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 主手侧（{@link LivingEntity} 的抽象方法）。强化拒止没有手、也不持有物品，
     * 这里只是为了让类可实例化。
     */
    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // ── 装备槽（{@link LivingEntity} 的三个抽象方法）：强化拒止没有装备 ─────────────

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // 强化拒止不持有任何物品
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.createdAt != CREATED_AT_UNSET) {
            tag.putLong(TAG_CREATED_AT, this.createdAt);
        }
        long[] ids = new long[this.trackedTargets.size() * 2];
        int i = 0;
        for (UUID id : this.trackedTargets) {
            ids[i++] = id.getMostSignificantBits();
            ids[i++] = id.getLeastSignificantBits();
        }
        tag.putLongArray(TAG_TARGETS, ids);
        if (this.chargeGiven > 0) {
            tag.putInt(TAG_CHARGE_GIVEN, this.chargeGiven);
        }
        if (this.meleeHits > 0) {
            tag.putInt(TAG_MELEE_HITS, this.meleeHits);
        }
        if (this.ownerUUID != null) {
            tag.putLongArray(TAG_OWNER, new long[] {this.ownerUUID.getMostSignificantBits(),
                    this.ownerUUID.getLeastSignificantBits()});
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_CREATED_AT)) {
            this.createdAt = tag.getLong(TAG_CREATED_AT);
        } else {
            // 老存档没有寿命字段：回填「当前 gameTime」⇒ 从加载这一刻重新计 20:00，不会被立刻销毁。
            this.createdAt = this.level().getGameTime();
        }
        this.trackedTargets.clear();
        long[] ids = tag.getLongArray(TAG_TARGETS);
        for (int i = 0; i + 1 < ids.length; i += 2) {
            this.trackedTargets.add(new UUID(ids[i], ids[i + 1]));
        }
        // 老存档没有该键 ⇒ 0（从 0 起算，已计过的目标会重新计，最多仍只发 5 点）
        this.chargeGiven = Math.max(0, Math.min(MAX_CHARGE_STACKS, tag.getInt(TAG_CHARGE_GIVEN)));
        // 老存档没有该键 ⇒ 0（回收进度从 0 起算）
        this.meleeHits = Math.max(0, Math.min(MAX_MELEE_HITS, tag.getInt(TAG_MELEE_HITS)));
        this.chargedTargets.clear();
        long[] owner = tag.getLongArray(TAG_OWNER);
        this.ownerUUID = owner.length >= 2 ? new UUID(owner[0], owner[1]) : null;
    }

    // ── 对外查询：是否正在吸附某个目标 ─────────────────────────────────────────

    /**
     * 某个目标是否正在被本实体吸附（**只读查询**）。供 {@code event/RepulsionCreeperGuard} 判定
     * 「这颗苦力怕的爆炸该不该被取消」—— 事件层不能去读私有集合，故开这个单一入口。
     *
     * <p>服务端权威：集合只在服务端被读写（{@link #tick()} 首行即分流客户端），客户端调用只会读到
     * 空集合 / NBT 同步来的旧值；爆炸开始事件本身也只在服务端发出，故实际只会在服务端被调用。
     */
    public boolean isTracking(UUID targetId) {
        return targetId != null && this.trackedTargets.contains(targetId);
    }

    /** 放置者 UUID（可能为 null = 旧存档 / 无主）。§4.6「只算自己放置的」判据入口。 */
    @javax.annotation.Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    /** 放置时写入放置者（仅服务端；见 {@code RepulsionCardItem#applyEffect}）。 */
    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    /**
     * 被吸附目标的**只读**快照视图（§4.6 由立牌按「自己放置的」逐个统计用）。
     *
     * <p>返回的是**活视图的不可变包装**（与 1.21.1 侧 {@code getTrackedTargets()} 同口径）；
     * 调用方需要迭代时自行防御性复制，本实体在 tick 中会修改底层集合。
     */
    public Set<UUID> getTrackedTargets() {
        return Collections.unmodifiableSet(this.trackedTargets);
    }

    /** 本力场已发放给放置者的充能点数（只读；0 .. {@link #MAX_CHARGE_STACKS}）。 */
    public int chargeGiven() {
        return this.chargeGiven;
    }

    /**
     * 吸附到一个**新**目标时立刻给放置者 +1 真实充能（2026-10-21 用户裁决）。
     *
     * <p>口径：
     * <ul>
     *   <li>只认<b>在线</b>的放置者：{@code ownerUUID} 对应玩家已离线 ⇒ <b>跳过</b>（不做离线记账，
     *       也不占用本力场的 5 点配额 —— 下次吸附新目标会再试一次）；</li>
     *   <li>走 {@link ChargeManager#addStacks(Player, int)}：与其它充能来源同一条链路
     *       （HUD / 消耗 / 死亡保留全部自动生效）；</li>
     *   <li><b>只在真实增加层数时计数</b>：玩家已到
     *       {@link com.merlinkitsune.starengine.component.GameplayConstants#CHARGE_MAX_STACKS}
     *       上限时本次不加、也不占配额；</li>
     *   <li><b>永久到账</b>：玩家走远、目标脱离 / 阵亡、本力场碎裂或被拆 —— 一律**不回收**。</li>
     * </ul>
     */
    private void awardCharge(UUID targetId) {
        if (targetId == null || this.ownerUUID == null) return;
        if (this.chargeGiven >= MAX_CHARGE_STACKS) return;
        if (this.chargedTargets.contains(targetId)) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        Player owner = serverLevel.getServer().getPlayerList().getPlayer(this.ownerUUID);
        if (owner == null) return; // 离线 ⇒ 跳过（无离线记账）
        int before = ChargeManager.getStacks(owner);
        ChargeManager.addStacks(owner, 1);
        if (ChargeManager.getStacks(owner) <= before) return; // 已满层 ⇒ 不占配额
        this.chargeGiven++;
        this.chargedTargets.add(targetId);
    }

    // ── 每 tick：剪枝 + 钳制（不施力） ─────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (this.isRemoved() || !this.isAlive()) return;

        // 寿命结算（20:00 = 24000 tick）：用 gameTime 差值，不用 tickCount（重载会归零）。
        // 首 tick（牌放置 / 裸 /summon / 老存档回填失败）先把创建时刻记下来，本 tick 不判生命周期。
        if (this.createdAt == CREATED_AT_UNSET) {
            this.createdAt = this.level().getGameTime();
        } else if (this.level().getGameTime() - this.createdAt >= LIFETIME_TICKS) {
            // 到点强制销毁（与耐久耗尽同一入口语义：不走 die，直接移除实体）
            this.discard();
            return;
        }

        // 立牌依附安全网（2026-10-27 用户裁决 ③）：放置者主动卸下「怪力乱神」立牌后，
        // 已放置的强化拒止必须立即销毁。即时路径在 LinglingSignManager#onSignLost；
        // 这里兜住「卸载时玩家不在线 / 命令移除立牌 / 老存档」等漏网情形（每 10t 一次）。
        if (this.ownerUUID != null && this.tickCount % 10 == 0
                && this.level() instanceof ServerLevel ownerLevel) {
            Player owner = ownerLevel.getServer().getPlayerList().getPlayer(this.ownerUUID);
            if (owner != null && owner.tickCount > 20 && !owner.isDeadOrDying()
                    && !LinglingSignManager.hasSign(owner)) {
                this.shatter();
                return;
            }
        }

        // 钉死（2026-10-21 用户裁决：放置后不得再被移动）：
        // ① 清掉一切外力写入的速度（爆炸 / 击退 / 其它模组的 setDeltaMovement）；
        // ② 位置若偏离落位锚点（活塞 / 水流 / 直接 setPos），拉回原坐标。
        if (this.getDeltaMovement().lengthSqr() > 1.0E-6) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        if (!this.pinned) {
            this.pinX = this.getX();
            this.pinY = this.getY();
            this.pinZ = this.getZ();
            this.pinned = true;
        } else if (Math.abs(this.getX() - this.pinX) > 1.0E-4
                || Math.abs(this.getY() - this.pinY) > 1.0E-4
                || Math.abs(this.getZ() - this.pinZ) > 1.0E-4) {
            this.setPos(this.pinX, this.pinY, this.pinZ);
        }

        // 剪枝 + 钳制 + 牵引 + 状态接管：每 tick 做（「目标无法逃离影响范围」需要即时生效）
        this.tickTrackedTargets();
        // 苦力怕引信压制：每 tick 做（自然膨胀路径；打火石点燃路径由 ExplosionEvent.Start 兜底）
        this.suppressTrackedCreeperFuses();
        if (this.tickCount % SCAN_INTERVAL == 0) {
            // 每秒一次：刷新吸附集合 + 施加 缓慢 / 虚弱（牵引已改为每 tick，见 tickTrackedTargets）
            this.scanAndApplyEffects();
        }
        if (this.tickCount % DURABILITY_INTERVAL == 0) {
            this.consumeDurability();
        }
    }

    /**
     * 维护被吸附目标集合：剪枝（死亡 / 移除 / &gt; 32 格 / 失去吸附资格）＋ 越界钳制。
     *
     * <p><b>平静即释放（2026-10-18 用户追加规则）</b>：对仍存在且存活的 {@link Mob} 追加同一条
     * {@link #isAttachable(Mob)} 判定 ⇒ 被激怒而吸附的中立生物一旦平静、以及失去仇恨目标的敌对生物
     * 在**下一个 tick** 被移出集合（不再施加 缓慢 / 虚弱、不再被拉扯 / 钳制、不再计入耐久消耗）。
     * 已施加的 缓慢 / 虚弱 按原时长自然结束，本方法**不主动清除**。
     *
     * <p>判定顺序固定为「alive / removed → &gt; 32 格 → isAttachable → 越界钳制」：32 格是「跑太远就
     * 彻底不管」的清理阈值，先于资格判定；钳制只对仍具备吸附资格的目标生效。
     *
     * <p>⚠️ 本方法**不施力**：清理（剪枝 / 释放状态）之后把每个受控目标交给
     * {@link #controlTarget(UUID, Mob)} —— 那里才是每 tick 的瞬移拦截 / 硬钳制 / 牵引 / 纵向钳制。
     *
     * <p>⚠️ 用 {@link ServerLevel#getEntity(UUID)} 取回实体（跨维度 / 未加载返回 null ⇒ 剪枝）。
     */
    private void tickTrackedTargets() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        Iterator<UUID> it = this.trackedTargets.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity entity = serverLevel.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
                this.releaseControl(id);
                it.remove();
                continue;
            }
            double distanceSqr = mob.distanceToSqr(this);
            if (distanceSqr > FORGET_RADIUS * FORGET_RADIUS) {
                this.releaseControl(id);
                it.remove();
                continue;
            }
            if (!this.isAttachable(mob)) {
                // 平静即释放（2026-10-18 用户追加规则）：不再施加 缓慢 / 虚弱、不再拉扯 / 钳制、不计耐久
                this.releaseControl(id);
                it.remove();
                continue;
            }
            // 受控目标：状态接管 + 瞬移拦截 + 硬钳制 + 每 tick 向心牵引 + 纵向钳制
            this.controlTarget(id, mob);
        }
    }

    /**
     * 苦力怕引信压制（2026-10-18 用户追加规则）：被吸附集合内的苦力怕**不允许自爆**。
     *
     * <p>每 tick 对集合内每颗 {@code Creeper} 做 {@code if (getSwellDir() > 0) setSwellDir(-1)}：
     * 原版 {@code Creeper#tick} 是 {@code swell += swellDir}（<b>先读方向、再累加</b>，之后才跑 AI），
     * 写入 -1 后下一次累加变成 -1；AI 的 {@code CreeperSwellGoal} 即使在本 tick 之后又把方向写成 1，
     * 下一 tick 仍会被这里改回 -1 ⇒ 两种 tick 顺序下 swell 分别是「逐 tick 回落到 0」与「停在当前值」，
     * 都涨不到引爆阈值（{@code maxSwell}）。
     *
     * <p>⚠️ 只压得住**自然膨胀**：打火石 / 火焰点燃时 {@code isIgnited()} 为真，原版 {@code Creeper#tick}
     * 会**每 tick 无条件** {@code setSwellDir(1)}（而且在读值之前），单靠本方法必然被覆盖 ⇒ 引信照样烧到
     * 引爆。那条路径由 {@code event/RepulsionCreeperGuard} 取消 {@code ExplosionEvent.Start} 兜底
     * （见类 javadoc〈苦力怕〉）。
     *
     * <p>只遍历已吸附集合（不改动它，无需拷贝）：剪枝已在 {@link #tickTrackedTargets()} 完成；
     * 跨维度 / 区块未加载时 {@code getEntity} 返回 null ⇒ 跳过。
     */
    private void suppressTrackedCreeperFuses() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        for (UUID id : this.trackedTargets) {
            if (!(serverLevel.getEntity(id) instanceof Creeper creeper) || creeper.isRemoved()) continue;
            if (creeper.getSwellDir() > 0) {
                creeper.setSwellDir(-1);
            }
        }
    }

    /**
     * 把越界目标沿**3D 方向**拉回 {@link #CLAMP_RADIUS} 处并清零速度。
     *
     * <p>目标点 = 本实体位置 + 归一化 3D 向量 × 11.5（不再只钳水平、y 保持不变）。
     * 落点若不可用（{@code level.noCollision} 为假 ⇒ 该处有方块碰撞 / 其他实体碰撞 / 出世界边界），
     * 依次把 y 上抬 0.5 / 1.0 / 1.5 找无碰撞落点；仍失败则**回退**为「只拉水平到 11.5 格、y 保持」。
     *
     * <p>为什么 3D 钳制能消除反复瞬移：钳制后目标与本实体的 **3D** 距离正好 11.5 ≤ 12，下一 tick
     * 不会再被判为越界；此前只钳水平时，竖直偏移大的目标会被每 tick 反复瞬移。
     * 回退路径（只钳水平）本身**不做**无碰撞校验，若回退点也嵌在方块里由原版碰撞推挤兜底
     * （宁可留竖直偏移，也不主动塞墙）。
     */
    private void clampInside(Mob mob) {
        Vec3 dir = this.position().subtract(mob.position());
        if (dir.lengthSqr() < 1.0E-6D) {
            // 正上方 / 正下方重合：给一个确定方向，避免除零
            dir = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 target = this.position().add(dir.normalize().scale(CLAMP_RADIUS));

        Vec3 landing = this.findFreeLanding(mob, target);
        if (landing == null) {
            // 回退：只钳水平到 11.5 格，y 保持
            double dx = mob.getX() - this.getX();
            double dz = mob.getZ() - this.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < 1.0E-4D) {
                dx = 1.0D;
                dz = 0.0D;
                horizontal = 1.0D;
            }
            landing = new Vec3(this.getX() + dx / horizontal * CLAMP_RADIUS, mob.getY(),
                    this.getZ() + dz / horizontal * CLAMP_RADIUS);
        }
        this.hardPlace(mob, landing);
    }

    /**
     * 依次尝试 {@code target} / {@code target.y+0.5} / {@code +1.0} / {@code +1.5}，返回第一个
     * 「把包围盒挪过去不会碰撞」的落点；四者都不可用时返回 {@code null}。
     *
     * <p>判据用 {@code Level#noCollision(Entity, AABB)}（{@code CollisionGetter} 的默认实现，两线同签名）：
     * 它会同时检查**方块碰撞 + 其他实体碰撞 + 世界边界**（{@code getBlockCollisions} /
     * {@code getEntityCollisions} / {@code borderCollision} 三者都为空才返回 true）。
     * ⇒ 目标身边挤满其他生物时，3D 落点常被判不可用而走水平回退，这是**有意**的保守取舍。
     */
    private Vec3 findFreeLanding(Mob mob, Vec3 target) {
        Vec3 origin = mob.position();
        for (int i = 0; i < 4; i++) {
            Vec3 candidate = target.add(0.0D, 0.5D * i, 0.0D);
            if (this.level().noCollision(mob, mob.getBoundingBox().move(candidate.subtract(origin)))) {
                return candidate;
            }
        }
        return null;
    }

    // ── 每秒：扫描 + 施加 缓慢 / 虚弱 ──────────────────────────────────────────

    /**
     * 吸附资格（2026-10-18 用户追加规则；口径与缘由见类 javadoc〈吸附资格〉）。
     *
     * <ol>
     *   <li>已驯服的玩家宠物一律跳过（{@code TamableAnimal#isTame()} ⇒ 狼 / 猫 / 鹦鹉）；</li>
     *   <li>候选类型 = 敌对生物（{@code Enemy}）**或** 中立生物（{@code NeutralMob}），其余一律跳过
     *       （两者可同时实现：末影人 / 僵尸猪灵 / 猪灵 等）；</li>
     *   <li>必须处于仇恨 / 激怒状态：{@code mob.getTarget() != null}，或中立生物 {@code NeutralMob#isAngry()}。</li>
     * </ol>
     *
     * <p>⚠️ 本判据**有意**比模组统一入口 {@code HostileTargets} 更窄（用户原话「排除没有仇恨目标的敌对
     * （如末影人）」）：统一入口对中立生物一律放行、且不看 anger，本牌则要求「有仇恨目标 / 被激怒」。
     * 先例 = {@code combat/ShootingStarManager#isStarTarget}（飞星同样只认已激怒的中立生物）。
     * 勿以「敌对判定必须走统一入口」为由改回宽口径。
     */
    private static boolean isAttachable(Mob mob) {
        // ① 玩家宠物除外（狼 / 猫 / 鹦鹉）：避免把玩家自己的宠物拉走
        if (mob instanceof TamableAnimal tameable && tameable.isTame()) {
            return false;
        }
        // ② 候选类型：敌对生物 或 中立生物（Enemy 与 NeutralMob 可同时实现，如末影人 / 僵尸猪灵）
        boolean enemy = mob instanceof Enemy;
        boolean neutral = mob instanceof NeutralMob;
        if (!enemy && !neutral) {
            return false;
        }
        // ③ 必须有仇恨 / 激怒状态：无仇恨目标的敌对生物（未激怒的末影人、无目标游荡的僵尸）不吸附
        if (mob.getTarget() != null) {
            return true;
        }
        return mob instanceof NeutralMob neutralMob && neutralMob.isAngry();
    }

    /**
     * 半径 12 格（球形）内**有吸附资格**的目标：加入被吸附集合并持续施加 缓慢 / 虚弱。
     *
     * <p>资格判据 = {@link #isAttachable(Mob)}（2026-10-18 用户追加规则：敌对生物 / 中立生物 ＋ 当前
     * 有仇恨目标或被激怒 ＋ 非已驯服宠物；口径与缘由见类 javadoc〈吸附资格〉）。
     * 刻意**不**排除精英 / Boss —— 需求未要求排除，且本牌不拉仇恨，不会把 Boss 的仇恨从玩家身上抢走。
     */
    private void scanAndApplyEffects() {
        AABB area = this.getBoundingBox().inflate(AFFECT_RADIUS);
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, area)) {
            if (!isAttachable(mob)) continue;
            if (!mob.isAlive() || mob.isRemoved()) continue;
            if (mob.distanceToSqr(this) > AFFECT_RADIUS * AFFECT_RADIUS) continue;
            this.trackedTargets.add(mob.getUUID());
            // 2026-10-21 用户裁决：吸附到一个**新**目标 ⇒ 立刻给放置者 +1 真实充能
            //（永久到账、单座封顶 5、同一目标不重复计数、放置者离线则跳过；见 #awardCharge）
            this.awardCharge(mob.getUUID());
            // 0:10 = 200 tick、I 级（amplifier 0）；ambient=false、visible=true、showIcon=true
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    EFFECT_DURATION, 0, false, true, true));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    EFFECT_DURATION, 0, false, true, true));
        }
    }

    // ── 每 tick：受控目标的硬钳制 / 瞬移拦截 / 牵引 / 状态接管 ────────────────────

    /**
     * 单个受控目标每 tick 的完整控制流程（**只在服务端**，由 {@link #tickTrackedTargets()} 调用）：
     * <ol>
     *   <li><b>状态接管</b>：飞行目标 {@code setNoGravity(true)}，原值记进 {@link #originalNoGravity}
     *       （只记第一次）—— 脱控 / 力场销毁时恢复，绝不永久改状态；</li>
     *   <li><b>瞬移拦截</b>（末影人 / 紫颂果 / 任何 {@code teleportTo}）：与上一 tick 位置相比单 tick
     *       位移 &gt; {@link #TELEPORT_JUMP} 格且不是自由落体 ⇒ 判为闪现，立刻拉回上一位置；</li>
     *   <li><b>通用硬钳制</b>：出 {@link #CLAMP_RADIUS} 球即拉回球面 + 清速度 + {@code hurtMarked}
     *       强制同步（每 tick 判定，不再依赖 20t 扫描）；</li>
     *   <li><b>每 tick 向心牵引</b>：直接改位置（指数收敛 + 单 tick 封顶），见
     *       {@link #pullTowardAnchor(Mob)}；</li>
     *   <li><b>纵向钳制</b>：脚底低于本实体 Y 超过 {@link #VERTICAL_CLAMP} ⇒ 抬回（治「遁地」）。</li>
     * </ol>
     * 全程<b>不动 {@code NoAi}</b>、不发自定义包（同步靠 {@code hurtMarked} + 原版位置同步）。
     */
    private void controlTarget(UUID id, Mob mob) {
        // ① 状态接管：飞行目标关重力（原始值只记一次）
        if (isFlying(mob)) {
            this.originalNoGravity.putIfAbsent(id, mob.isNoGravity());
            if (!mob.isNoGravity()) {
                mob.setNoGravity(true);
            }
        }

        // ② 瞬移拦截：单 tick 位置突变即视为闪现，拉回上一有效位置
        Vec3 last = this.lastPositions.get(id);
        if (last != null && isTeleportJump(mob, last)) {
            this.hardPlace(mob, last);
        }

        // ③ 通用硬钳制：每 tick 判定，出球即拉回球面
        if (mob.distanceToSqr(this) > CLAMP_RADIUS * CLAMP_RADIUS) {
            this.clampInside(mob);
        }

        // ④ 每 tick 向心牵引
        this.pullTowardAnchor(mob);

        // ⑤ 纵向钳制（遁地）：脚底最多低于锚点 VERTICAL_CLAMP 格
        double floorY = this.getY() - VERTICAL_CLAMP;
        if (mob.getY() < floorY - 1.0E-4D) {
            this.hardPlace(mob, new Vec3(mob.getX(), floorY, mob.getZ()));
        }

        // ⑥ 记录本 tick 的有效位置：下一 tick 的瞬移判定拿它做参照
        this.lastPositions.put(id, mob.position());
    }

    /**
     * 是否判为「瞬移 / 闪现」：与上一 tick 位置相比单 tick 位移 &gt; {@link #TELEPORT_JUMP} 格。
     *
     * <p>⚠️ <b>自由落体例外</b>：重力加速下生物竖直速度可达约 3.9 格/tick（终端速度），正常下落会误判
     * ⇒ 只有「竖直速度 &gt; -1.0（不是高速下落）」时才按瞬移处理。末影人的横向 / 斜向闪现一律命中；
     * 向下闪现被漏掉的代价只是「下一 tick 被牵引拉回」，不再有脱离。
     */
    private static boolean isTeleportJump(Mob mob, Vec3 last) {
        if (last.distanceToSqr(mob.position()) <= TELEPORT_JUMP * TELEPORT_JUMP) return false;
        return mob.getDeltaMovement().y > -1.0D;
    }

    /** 飞行类目标：原版 {@link FlyingMob}（幻翼 / 恶魂等）＋ 恼鬼。 */
    private static boolean isFlying(Mob mob) {
        return mob instanceof FlyingMob || mob instanceof Vex;
    }

    /**
     * 把目标**硬放到**指定点：无碰撞则直接 {@code setPos}（不经物理 ⇒ 不会被 AI / 重力改写），
     * 有碰撞则退回原版 {@code move}（由碰撞裁剪，**不塞墙**）。两种情况都清速度并标记强制同步。
     *
     * <p>⚠️ 这是「直接改位置」而不是 {@code setDeltaMovement}：后者每 tick 被 {@code aiStep} /
     * {@code travel} 重算，既拉不动又可能累积成弹射；直接改位置才是「稳定牵到中心」的实现。
     */
    private void hardPlace(Mob mob, Vec3 dest) {
        Vec3 delta = dest.subtract(mob.position());
        if (delta.lengthSqr() > 1.0E-10D) {
            if (this.level().noCollision(mob, mob.getBoundingBox().move(delta))) {
                mob.setPos(dest.x, dest.y, dest.z);
            } else {
                // 目标点被方块 / 实体占住：交给原版碰撞裁剪，宁可卡在边缘也不塞进墙里
                mob.move(MoverType.SELF, delta);
            }
        }
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hurtMarked = true;
    }

    /**
     * 每 tick 向心牵引（3D）：步长 = clamp(剩余距离 × {@link #PULL_RATE},
     * {@link #PULL_MIN_STEP}, {@link #PULL_MAX_STEP})，再夹一次「不许越过停止圈」。
     *
     * <p>三个反「抖动 / 甩飞」的硬保证：
     * <ol>
     *   <li><b>指数收敛</b>：步长 ∝ 剩余距离 ⇒ 越近越慢，理论不过冲；</li>
     *   <li><b>不越过停止圈</b>：{@code step ≤ 距离 - STOP/2} ⇒ 到位后不会再被推动；</li>
     *   <li><b>不累积速度</b>：每次 {@code setDeltaMovement(ZERO)}（见 {@link #hardPlace}）⇒
     *       不存在「逐 tick 加速、最后一帧弹射」的路径。</li>
     * </ol>
     */
    private void pullTowardAnchor(Mob mob) {
        Vec3 delta = this.position().subtract(mob.position());
        double distance = delta.length();
        if (distance <= PULL_STOP_RADIUS) return;
        double step = Math.min(PULL_MAX_STEP,
                Math.max(PULL_MIN_STEP, (distance - PULL_STOP_RADIUS) * PULL_RATE));
        step = Math.min(step, distance - PULL_STOP_RADIUS * 0.5D);
        if (step <= 1.0E-4D) return;
        this.hardPlace(mob, mob.position().add(delta.scale(step / distance)));
    }

    /**
     * 目标脱控（死亡 / 移除 / 跑出 32 格 / 失去吸附资格 / 力场销毁）：恢复被抓走前的
     * {@code isNoGravity()}，并清掉瞬移参照点。
     */
    private void releaseControl(UUID id) {
        this.lastPositions.remove(id);
        Boolean original = this.originalNoGravity.remove(id);
        if (original == null) return;
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getEntity(id) instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
            mob.setNoGravity(original);
        }
    }

    /** 力场销毁（碎裂 / 移除 / 寿命到期）：把所有受控目标的重力状态还原。 */
    private void releaseAllControls() {
        for (UUID id : new ArrayList<>(this.originalNoGravity.keySet())) {
            this.releaseControl(id);
        }
        this.lastPositions.clear();
    }

    // ── 每 10 秒：耐久结算 ────────────────────────────────────────────────────

    /**
     * 每 10 秒按「当前 12 格内被吸附目标数」扣耐久（单次最多 {@link #MAX_DURABILITY_COST} 点）。
     * 目标数为 0 时不扣（没有吸附对象就没有消耗）。
     */
    private void consumeDurability() {
        int attached = this.countTrackedWithinAura();
        if (attached <= 0) return;
        float cost = Math.min(attached, MAX_DURABILITY_COST);
        float next = this.getHealth() - cost;
        if (next <= 0.0F) {
            this.shatter();
        } else {
            this.setHealth(next);
        }
    }

    /** 当前仍在 12 格内的被吸附目标数量（耐久结算口径）。 */
    private int countTrackedWithinAura() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return 0;
        int count = 0;
        for (UUID id : new ArrayList<>(this.trackedTargets)) {
            Entity entity = serverLevel.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()
                    && mob.distanceToSqr(this) <= AFFECT_RADIUS * AFFECT_RADIUS) {
                count++;
            }
        }
        return count;
    }

    /**
     * 耐久耗尽：碎裂粒子（{@code CRIT} + 少量 {@code POOF}）＋ 玻璃破碎音 ⇒ 直接 {@link #discard()}。
     *
     * <p>不走 {@code die}：本实体免疫一切伤害，{@code die} 只会在耐久耗尽时被这条路径手工触发；
     * 用 {@code discard} 可以跳过死亡动画 / 死亡音 / 战利品结算。
     */
    private void shatter() {
        double cx = this.getX();
        double cy = this.getY() + this.getBbHeight() * 0.5D;
        double cz = this.getZ();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT, cx, cy, cz,
                    SHATTER_CRIT_COUNT, 0.6D, 0.9D, 0.6D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.POOF, cx, cy, cz,
                    SHATTER_POOF_COUNT, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        this.level().playSound(null, cx, cy, cz, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
        this.setHealth(0.0F);
        this.releaseAllControls();
        this.trackedTargets.clear();
        this.discard();
    }

    /**
     * 外部（{@code LinglingSignManager#destroyOwnedFields}）触发的「所有者失去立牌」销毁。
     *
     * <p>语义与耐久耗尽的 {@link #shatter()} 一致（碎裂观感 + 直接移除，不掉落物品）；
     * 客户端 / 已移除实体直接忽略，幂等。
     */
    public void destroyByOwnerLoss() {
        if (this.level().isClientSide) return;
        if (this.isRemoved()) return;
        this.shatter();
    }

    // ── 免疫 / 钉地 ───────────────────────────────────────────────────────────

    /**
     * 实体被移除（碎裂 / 寿命到期 / 区块卸载以外的任何移除路径）：先把受控目标的重力状态还原，
     * 再走原版移除 —— 保证「飞行怪被关掉的重力」不会残留在世界上。
     */
    @Override
    public void remove(RemovalReason reason) {
        this.releaseAllControls();
        super.remove(reason);
    }

    /**
     * 受伤入口（**服务端唯一判定**；2026-10-25 用户裁决改写）。
     *
     * <p>口径：
     * <ul>
     *   <li>来源判定 = {@code source.getDirectEntity() instanceof Player} ⇒ **只认玩家本人的近战 /
     *       扫击**。与旧版（{@code getEntity()}）的关键差别：玩家射出的箭 / 三叉戟的
     *       {@code getDirectEntity()} 是弹射物本身 ⇒ 自动排除；怪物、其它生物、环境伤害
     *       （坠落 / 火焰 / 冻结 / 挤压）与 {@code /kill} 的 {@code genericKill}（没有 directEntity）
     *       一律无效 ⇒「不吸引仇恨」的语义不变；</li>
     *   <li><b>只认放置者本人</b>（2026-10-25 用户裁决：玩家只能破坏自己放置的强化拒止）：
     *       {@code ownerUUID} 非空且不是该玩家 ⇒ 动作栏提示「这个强化拒止不属于你」并 {@code return false}，
     *       既不计 {@link #meleeHits}、也不播受击红闪 / 受击音；{@code ownerUUID} 为空（管理命令 / 老存档）
     *       时对所有人开放；</li>
     *   <li><b>命中不再扣耐久</b>：耐久（= {@link #getHealth()}）只由 {@link #consumeDurability()}
     *       按吸附目标数消耗、或由放置时从物品恢复的状态决定；命中只累加 {@link #meleeHits}；</li>
     *   <li>命中 {@link #MAX_MELEE_HITS} 次 ⇒ {@link #recoverIntoItem(Player)}：掉落**物品形式**的
     *       强化拒止（耐久 / 剩余寿命 / 已发放充能随物品带走），本实体 {@code discard}；</li>
     *   <li>客户端红闪 + 受击音走 {@code broadcastDamageEvent}（{@code ClientboundDamageEventPacket}
     *       → {@code handleDamageEvent} 置 {@code hurtTime}），受击音由 {@link #getHurtSound} 决定，
     *       这里不重复 {@code playSound}。</li>
     * </ul>
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (this.isRemoved() || this.isDeadOrDying()) return false;
        // 只认玩家本人（近战 / 扫击）；弹射物的 directEntity 是箭 / 三叉戟 / 子弹 ⇒ 不计
        if (!(source.getDirectEntity() instanceof Player player)) return false;
        // 只允许放置者本人回收（2026-10-25 用户裁决）：别人的强化拒止打不动、不计命中
        if (this.ownerUUID != null && !this.ownerUUID.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(RepulsionItem.MSG_NOT_OWNER)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        this.markHurt();
        this.level().broadcastDamageEvent(this, source);

        this.meleeHits++;
        int remaining = MAX_MELEE_HITS - this.meleeHits;
        if (remaining <= 0) {
            this.recoverIntoItem(player);
            return true;
        }
        player.displayClientMessage(Component.translatable(RepulsionItem.MSG_HIT, remaining)
                .withStyle(ChatFormatting.YELLOW), true);
        return true;
    }

    /**
     * 回收为物品形式（2026-10-25 用户裁决）：把「耐久 / 已存活 tick / 已发放充能」三份状态写进
     * {@code astral_dice:repulsion} 物品并掉在原地（带默认拾取延迟），本实体走 {@code discard}。
     *
     * <p>为什么不复用 {@link #shatter()}：碎裂是「耐久耗尽」的语义（CRIT 碎裂粒子 + 玻璃破碎音 +
     * 什么都不掉）；回收是「玩家把装置收起来了」，用较轻的 {@code POOF} + 物品拾取音，两件事的观感
     * 必须能分开。owner 一并写进物品：再次放置时仍是原主的装置（专属语义与效果牌一致）。
     */
    private void recoverIntoItem(Player player) {
        long elapsed = this.createdAt == CREATED_AT_UNSET
                ? 0L
                : Math.max(0L, this.level().getGameTime() - this.createdAt);
        UUID ownerId = this.ownerUUID != null ? this.ownerUUID : player.getUUID();
        ItemStack recovered = RepulsionItem.createRecovered(this.getHealth(), elapsed, this.chargeGiven,
                ownerId, ownerNameOf(ownerId, player));

        double cx = this.getX();
        double cy = this.getY();
        double cz = this.getZ();
        ItemEntity drop = new ItemEntity(this.level(), cx, cy, cz, recovered);
        drop.setDefaultPickUpDelay();
        this.level().addFreshEntity(drop);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, cx, cy + this.getBbHeight() * 0.5D, cz,
                    SHATTER_POOF_COUNT, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        this.level().playSound(null, cx, cy, cz, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.9F, 1.3F);

        player.displayClientMessage(Component.translatable(RepulsionItem.MSG_RECOVERED,
                RepulsionItem.getDurability(recovered), RepulsionItem.MAX_DURABILITY)
                .withStyle(ChatFormatting.GREEN), true);

        this.releaseAllControls();
        this.trackedTargets.clear();
        this.discard();
    }

    /** 取所有者显示名（本人优先自查；否则查在线玩家表；查不到返回空串 ⇒ tooltip 显示「未知」）。 */
    private static String ownerNameOf(UUID ownerId, Player fallback) {
        if (ownerId == null) {
            return fallback != null ? fallback.getGameProfile().getName() : "";
        }
        if (fallback != null && ownerId.equals(fallback.getUUID())) {
            return fallback.getGameProfile().getName();
        }
        var server = fallback != null ? fallback.getServer() : null;
        if (server != null) {
            var online = server.getPlayerList().getPlayer(ownerId);
            if (online != null) return online.getGameProfile().getName();
        }
        return "";
    }

    /**
     * 由物品形态恢复状态（{@code item/RepulsionItem#place} 调用，**仅服务端**）：
     * 血量 / 剩余寿命 / 已发放充能。
     *
     * <p>剩余寿命的算法：{@code createdAt = now - elapsedTicks} ⇒ {@code LIFETIME_TICKS} 到点的判据
     * （{@code gameTime - createdAt >= LIFETIME_TICKS}）自动变成「只消耗掉已存活的那部分」；不做特判。
     *
     * <p>{@code chargedTargets} 与 {@code meleeHits} 归零：前者是「本次生命周期内已换过充能的目标」
     * 去重表（纯内存），后者是回收进度；两者都不该跨放置继承。
     */
    public void restoreState(float health, long elapsedTicks, int chargeGiven) {
        this.setHealth(Math.max(1.0F, Math.min(this.getMaxHealth(), health)));
        long elapsed = Math.max(0L, Math.min(LIFETIME_TICKS, elapsedTicks));
        this.createdAt = this.level().getGameTime() - elapsed;
        this.chargeGiven = Math.max(0, Math.min(MAX_CHARGE_STACKS, chargeGiven));
        this.chargedTargets.clear();
        this.meleeHits = 0;
    }

    /** 受击音：玻璃磕碰（客户端由伤害事件包播放；碎裂音是另一条路径，见 {@link #shatter()}）。 */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    /**
     * 死亡入口（**无生物式死亡动画**，2026-10-22 用户裁决）：一律折叠成 {@link #shatter()}。
     *
     * <p>为什么这样就「不动画」：原版 {@code LivingEntity#tickDeath()} 要等 {@code deathTime >= 20}
     * 才移除实体，这 20 tick 就是客户端看到的躺倒 / 变红动画（由 {@code super.die} 里的
     * {@code setPose(Pose.DYING)} 与实体事件 3 驱动）。本覆写把「判死」直接换成「碎裂粒子 + 玻璃
     * 破碎音 + {@code discard}」⇒ 服务端同一 tick 内移除，客户端只收到音效 / 粒子 + 移除包，
     * 观感与「船 / 盔甲架」一致。客户端侧不做任何本地处理。
     *
     * <p>目前没有路径会主动调它（{@link #consumeDurability()} 直接走 {@link #shatter()}，
     * {@link #hurt} 走 {@link #recoverIntoItem(Player)}）；保留是为了「万一有第三方或未来的伤害路径
     * 把本实体判死」时行为不退化成生物式死亡。
     */
    @Override
    public void die(DamageSource source) {
        if (this.level().isClientSide) return;
        this.shatter();
    }
    /**
     * 「显式判死」入口（2026-10-22 用户反馈「遗留力场拿不到 id，/kill 也杀不掉」）。
     * 原版 {@code /kill} 走 {@code Entity#kill()} → {@code hurt(genericKill, MAX)}，而本实体的
     * {@link #hurt} 只认玩家来源 ⇒ 原版 /kill 打不掉它。这里把 {@code kill()} 直接接到唯一的
     * 消亡路径 {@link #shatter()}：{@code /kill @e[type=astral_dice:repulsion,distance=..64]}
     * 与 {@code /astralparty killrepulsion [radius|all]} 等价可用。
     *
     * <p>只影响「显式判死」这条路径；怪物 / 环境伤害仍走 {@link #hurt} 被拒 ⇒ 免疫口径不变。
     */
    @Override
    public void kill() {
        if (this.level().isClientSide) return;
        this.shatter();
    }

    /** 药水（喷溅 / 滞留 / 瞬时）一律不生效（2026-10-21 用户裁决）。 */
    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 水流 / 岩浆流也不得推移（{@code Entity} 默认返回 true）。 */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** 落位锚点：首次 tick 记录，之后每 tick 比对（见 {@link #tick()}）。 */
    private double pinX;
    private double pinY;
    private double pinZ;
    private boolean pinned;

    /** 钉在地上：不吃爆炸 / 活塞 / 水流等一切外力推移。 */
    @Override
    public void push(double x, double y, double z) {
        // 故意空实现
    }

    /** 强化拒止不是战利品也不是经验来源（耐久耗尽即碎裂，不掉物品）。 */
    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }
}
