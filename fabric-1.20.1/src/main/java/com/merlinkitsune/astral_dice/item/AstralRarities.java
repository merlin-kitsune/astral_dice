package com.merlinkitsune.astral_dice.item;

import net.minecraft.ChatFormatting;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * 本模组稀有度等级的**平台接线**(Fabric 1.20.1) —— 把 {@link Rarity} 里的 5 个等级
 * 落进原版 {@code net.minecraft.world.item.Rarity},并提供运行期访问器。
 *
 * <h2>等级名 / 颜色 / 序列化名都在 {@link Rarity}</h2>
 * <p>本类**不含任何等级定义**,只做两件事:① 造出 5 个 identity 互不相同的原版 {@code Rarity}
 * 常量并把它们追加进 {@code Rarity.$VALUES};② 把结果交回消费方,并提供 {@link #tierOf} 反查。
 *
 * <h2>为什么用 Unsafe 合成,而不是 Forge 的 {@code Rarity.create}</h2>
 * <p>Forge 1.20.1 的机制是原版 {@code Rarity} implements {@code IExtensibleEnum},其补丁加了
 * {@code Rarity.create(String, UnaryOperator<Style>)} 静态工厂 + {@code styleModifier} 字段。
 * **Fabric 侧的 {@code Rarity} 是纯原版**(javap 实证):
 * <pre>
 *   public final class Rarity extends Enum&lt;Rarity&gt; {
 *       public static final Rarity COMMON, UNCOMMON, RARE, EPIC;
 *       public final ChatFormatting color;      // ← 唯一的颜色通道（⚠️ 生产 intermediary 下叫 field_8908，
 *                                               //    故下方一律**按类型**取字段，绝不按名字，见 lookupColorField）
 *       private Rarity(ChatFormatting);         // ← private 构造器,枚举
 *   }
 * </pre>
 * 三条硬约束:
 * <ul>
 *   <li>{@code final} 类 ⇒ 不能继承;</li>
 *   <li>枚举 ⇒ javac **禁止**在源码里写 {@code new Rarity(...)}(JLS 15.9.1);Mixin 也帮不上忙
 *       —— 注入进 {@code <clinit>} 的代码同样要先过 javac;</li>
 *   <li>{@code Constructor.newInstance} 对枚举被 JDK 硬拒
 *       ({@code IllegalArgumentException: Cannot reflectively create enum objects})。</li>
 * </ul>
 * ⇒ 唯一可行且**不需要 {@code --add-opens}** 的路径是 {@link Unsafe#allocateInstance(Class)} 分配实例,
 * 再用 {@link Unsafe} 的字段偏移直接写入 {@code java.lang.Enum} 的 {@code name}/{@code ordinal}
 * 与 {@code Rarity.color}(用字段偏移写入绕开了 {@code setAccessible} 对 {@code java.base} 的模块限制)。
 *
 * <h2>颜色口径</h2>
 * <p>原版 1.20.1 的物品名着色链路是 {@code ItemStack#getTooltipLines} → {@code rarity.color}
 * ({@code ChatFormatting}),**没有** Forge 的 {@code styleModifier} 通道,故只能用 16 色
 * {@code ChatFormatting} 近似库 {@link Rarity#rgb()} 的精确色值:
 * <table border="1">
 *   <caption>档位配色对照</caption>
 *   <tr><th>档位</th><th>库 rgb</th><th>本类 ChatFormatting</th></tr>
 *   <tr><td>稀有</td><td>#55FFFF</td><td>AQUA(#55FFFF) —— 精确一致</td></tr>
 *   <tr><td>史诗</td><td>#FF55FF</td><td>LIGHT_PURPLE(#FF55FF) —— 精确一致</td></tr>
 *   <tr><td>传奇</td><td>#FFC24B</td><td>GOLD(#FFAA00) —— 近似(原版无 #FFC24B)</td></tr>
 *   <tr><td>巅峰</td><td>#FF4D4D</td><td>RED(#FF5555) —— 近似</td></tr>
 *   <tr><td>奇特</td><td>#FF4D4D</td><td>RED(#FF5555) —— 与巅峰同色(与 Forge 侧口径一致:
 *       「奇特文字色 = 亮红(与巅峰同色),流动彩虹只在边框上」)</td></tr>
 * </table>
 * <p>⇒ 5 档的**文字色**为 4 种(奇特与巅峰同色,与 Forge 侧行为一致),而 {@code Rarity} 的
 * **identity 仍是 5 个互不相同** ⇒ {@code tierOf} 与提示框边框(RarityTooltipFrame)的全部 5 档判定不受影响。
 *
 * <p>⚠️ 与 Forge 侧的可观测差异(已登记):传奇/巅峰的名色是近似值,不是精确 RGB。
 * 精确 RGB 需要客户端另加一条「按 {@code tierOf} 覆写名字行 Style」的渲染路径,属渲染层改动,
 * 不在本次平台接线范围内。
 *
 * <p>⚠️ 同包内的 {@code Rarity} 是本模组枚举 ⇒ 原版类型在本文件里**一律写全限定名**(照 Forge 侧同类注释)。
 */
public final class AstralRarities {

    private static final Unsafe UNSAFE;
    private static final long ENUM_NAME_OFFSET;
    private static final long ENUM_ORDINAL_OFFSET;
    private static final long RARITY_COLOR_OFFSET;
    private static final long RARITY_VALUES_OFFSET;
    private static final Object RARITY_VALUES_BASE;

    /** 稀有(水蓝)。 */
    public static final net.minecraft.world.item.Rarity RARE;
    /** 史诗(粉紫)。 */
    public static final net.minecraft.world.item.Rarity EPIC;
    /** 传奇(金)。 */
    public static final net.minecraft.world.item.Rarity LEGENDARY;
    /** 巅峰(亮红)。 */
    public static final net.minecraft.world.item.Rarity PINNACLE;
    /** 奇特(文字亮红 / 边框流动彩虹)。 */
    public static final net.minecraft.world.item.Rarity BIZARRE;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
            ENUM_NAME_OFFSET = UNSAFE.objectFieldOffset(Enum.class.getDeclaredField("name"));
            ENUM_ORDINAL_OFFSET = UNSAFE.objectFieldOffset(Enum.class.getDeclaredField("ordinal"));
            RARITY_COLOR_OFFSET = UNSAFE.objectFieldOffset(lookupColorField(net.minecraft.world.item.Rarity.class));
            Field values = lookupValuesField(net.minecraft.world.item.Rarity.class);
            RARITY_VALUES_OFFSET = UNSAFE.staticFieldOffset(values);
            RARITY_VALUES_BASE = UNSAFE.staticFieldBase(values);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
        // 必须在任何物品构造之前触发(消费方在 ModItems 注册期调用下列访问器,天然满足)。
        // 先读一个原版常量 ⇒ 保证 Rarity.<clinit> 已跑完($VALUES 已建好),再往后面追加。
        net.minecraft.world.item.Rarity anchor = net.minecraft.world.item.Rarity.COMMON;
        if (anchor == null) {
            throw new ExceptionInInitializerError("vanilla Rarity not initialised");
        }
        RARE = synthesize(Rarity.RARE.constantName(), ChatFormatting.AQUA);
        EPIC = synthesize(Rarity.EPIC.constantName(), ChatFormatting.LIGHT_PURPLE);
        LEGENDARY = synthesize(Rarity.LEGENDARY.constantName(), ChatFormatting.GOLD);
        PINNACLE = synthesize(Rarity.PINNACLE.constantName(), ChatFormatting.RED);
        BIZARRE = synthesize(Rarity.BIZARRE.constantName(), ChatFormatting.RED);
    }

    /**
     * 定位原版 {@code Rarity} 的颜色字段：**按类型匹配，不按名字**。
     *
     * <p>⚠️ 硬约束（2026-09-29 生产事故 KI-F13，**勿回退为按名反射**）：
     * Fabric 的 **dev 与生产是两套映射** —— dev（Loom named）里
     * {@code net.minecraft.world.item.Rarity} 的字段就叫 {@code color}，
     * 而生产（intermediary）里同一个类是 {@code net.minecraft.class_1814}、
     * 该字段名被重映射成 {@code field_8908}。⇒ 任何**按字符串名**反射原版成员的写法
     * 都在 dev 全绿、在整合包里 100% 崩（{@code NoSuchFieldException: color}
     * → {@code ExceptionInInitializerError} → 入口点失败 → 游戏进不去）。
     * 按**类型**匹配对两套映射同时成立。
     */
    private static Field lookupColorField(Class<?> rarityClass) {
        for (Field field : rarityClass.getDeclaredFields()) {
            if (field.getType() == ChatFormatting.class) {
                return field;
            }
        }
        throw new IllegalStateException(
                "vanilla Rarity has no ChatFormatting-typed field: " + rarityClass.getName());
    }

    /**
     * 定位枚举的 {@code $VALUES} 后备数组：**按修饰符 + 组件类型匹配，不按名字**
     * （理由同 {@link #lookupColorField}；intermediary 下该字段名是 {@code field_8905}）。
     */
    private static Field lookupValuesField(Class<?> rarityClass) {
        for (Field field : rarityClass.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && field.getType().isArray()
                    && field.getType().getComponentType() == rarityClass) {
                return field;
            }
        }
        throw new IllegalStateException(
                "vanilla Rarity has no Rarity[] static field: " + rarityClass.getName());
    }

    private AstralRarities() {
    }

    /** 稀有(水蓝 = 原版 RARE 配色)。 */
    public static net.minecraft.world.item.Rarity rare() {
        return RARE;
    }

    /** 史诗(粉紫 = 原版 EPIC 配色)。 */
    public static net.minecraft.world.item.Rarity epic() {
        return EPIC;
    }

    /** 传奇(金)。 */
    public static net.minecraft.world.item.Rarity legendary() {
        return LEGENDARY;
    }

    /** 巅峰(亮红)。 */
    public static net.minecraft.world.item.Rarity pinnacle() {
        return PINNACLE;
    }

    /** 奇特(彩虹/流动;文字色与巅峰同为亮红,彩虹只体现在提示框边框)。 */
    public static net.minecraft.world.item.Rarity bizarre() {
        return BIZARRE;
    }

    /**
     * **反查**:把一个(可能已被本库合成过的)原版 {@code Rarity} 常量还原成本库档位;
     * **不是本模组的档位时返回 {@code null}**(含原版自带的 COMMON / UNCOMMON / RARE / EPIC)。
     *
     * <p>用途:客户端渲染「按本模组档位生效」的表现(提示框边框染色)时,用它判断该物品归哪一档。
     * 消费方**不要**自己写 {@code ==} 链 —— 那等于把档位映射抄到了库外。
     */
    public static Rarity tierOf(net.minecraft.world.item.Rarity rarity) {
        if (rarity == null) {
            return null;
        }
        if (rarity == RARE) {
            return Rarity.RARE;
        }
        if (rarity == EPIC) {
            return Rarity.EPIC;
        }
        if (rarity == LEGENDARY) {
            return Rarity.LEGENDARY;
        }
        if (rarity == PINNACLE) {
            return Rarity.PINNACLE;
        }
        if (rarity == BIZARRE) {
            return Rarity.BIZARRE;
        }
        return null;
    }

    private static net.minecraft.world.item.Rarity synthesize(String name, ChatFormatting color) {
        try {
            net.minecraft.world.item.Rarity constant =
                    (net.minecraft.world.item.Rarity) UNSAFE.allocateInstance(net.minecraft.world.item.Rarity.class);
            net.minecraft.world.item.Rarity[] values =
                    (net.minecraft.world.item.Rarity[]) UNSAFE.getObject(RARITY_VALUES_BASE, RARITY_VALUES_OFFSET);
            UNSAFE.putObject(constant, ENUM_NAME_OFFSET, name);
            UNSAFE.putInt(constant, ENUM_ORDINAL_OFFSET, values.length);
            UNSAFE.putObject(constant, RARITY_COLOR_OFFSET, color);
            net.minecraft.world.item.Rarity[] grown =
                    Arrays.copyOf(values, values.length + 1);
            grown[values.length] = constant;
            UNSAFE.putObject(RARITY_VALUES_BASE, RARITY_VALUES_OFFSET, grown);
            return constant;
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }
}
