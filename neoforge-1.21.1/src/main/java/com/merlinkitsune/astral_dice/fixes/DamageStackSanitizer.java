package com.merlinkitsune.astral_dice.fixes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * NeoForge 21.1.x {@code LivingEntity#hurt} 伤害容器栈泄漏补丁的核心逻辑（纯逻辑，可独立单测）。
 *
 * <p>背景：NeoForge 21.1.235 的 {@code LivingEntity#hurt} 在方法开头把新建的
 * {@code DamageContainer} 压入 {@code damageContainers}，随后调用
 * {@code CommonHooks.onEntityIncomingDamage(...)}；该调用返回 {@code true}（事件被取消）时方法直接
 * {@code return false}，**没有 pop**，容器就永久残留在栈上。上游修复（NeoForge PR #3101）就是给这个
 * 取消分支补一次 {@code pop()}；1.21.x 全线（含 21.1.250）至今未修。
 *
 * <p>本补丁不采用「@Redirect 钩子后盲目 pop」——那种写法在上游 backport 之后会重复弹栈
 * （弹掉外层容器，或对空栈抛 {@link java.util.EmptyStackException}）。这里改用「恢复进入深度」：
 * 进入 {@code hurt} 时记录当时的栈深，返回时把栈截断回该深度。于是：
 * <ul>
 *   <li>未修复字节码：取消分支少了一次 pop → 本补丁补 1 次；</li>
 *   <li>已 backport 字节码：上游自己已经 pop 到位 → 本补丁 0 次 pop（自动退化为 no-op）。</li>
 * </ul>
 * <b>永远不会弹到低于进入时的深度</b>，这是「backport 后不重复弹栈、永不 EmptyStackException」的结构性保证。
 *
 * <p>本类刻意只依赖 JDK（Minecraft / NeoForge / Mixin / slf4j 一律不引用），因此可以被纯 Java 测试
 * 直接编译运行（见 {@code neoforge-1.21.1/tools/mixin-stack-sanitizer-test/DamageStackSanitizerTest.java}）。
 * 平台侧（slf4j → {@code logs/latest.log}）由 {@link NeoForgeFixesLog} 经 {@link #setLogSink(Consumer)} 接入。
 */
public final class DamageStackSanitizer {

    /**
     * 注入目标方法描述符：{@code LivingEntity#hurt(DamageSource, float)}。
     *
     * <p>用完整描述符（而不是裸方法名 {@code "hurt"}）定位目标，避免未来新增同名重载时选错方法；
     * 描述符不匹配时注入器按 {@code require = 0} 静默跳过（不抛错、不会让游戏启动失败）。
     */
    public static final String HURT_DESCRIPTOR = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z";

    /** 补丁 / mixin 配置名，出现在一次性存活日志里，便于跑完游戏内用例后 grep server.log / latest.log。 */
    public static final String PATCH_ID = "astral_dice/neoforge_fixes";

    /** 哨兵：表示「本层不处理」（进入 {@code hurt} 时 {@code damageContainers} 为 null）。 */
    public static final int NO_TARGET_DEPTH = -1;

    /** push/pop 配对失同步（例如 hurt 抛异常逃逸）时的保守上限；超过即放弃记录，退化为 no-op。 */
    private static final int MAX_NESTING = 64;

    /** 一次性存活日志开关：每 JVM（每个类加载器）只输出一次。 */
    private static final AtomicBoolean FIRST_RESTORE_LOGGED = new AtomicBoolean(false);

    /** 每线程一个「进入 hurt 时的栈深」记录栈，支持 hurt 嵌套调用逐层配对。 */
    private static final ThreadLocal<Deque<Integer>> DEPTHS = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 存活日志落点。默认 JDK-only 的 {@code System.out}（纯 Java 单测可直接看到、也可被测试捕获）；
     * 平台侧由 {@link NeoForgeFixesLog#install()} 换成 slf4j，使该行进入 {@code logs/latest.log}。
     */
    private static volatile Consumer<String> logSink = System.out::println;

    private DamageStackSanitizer() {
    }

    /**
     * 替换存活日志落点（幂等且廉价：已是同一个 sink 时零成本返回）。
     *
     * @param sink 新落点；{@code null} 表示恢复 {@code System.out} 默认落点
     */
    public static void setLogSink(Consumer<String> sink) {
        final Consumer<String> target = (sink == null) ? System.out::println : sink;
        if (logSink != target) {
            logSink = target;
        }
    }

    /** 进入 {@code hurt}（{@code @At("HEAD")}）：记录进入时的栈深（字段为 null 时记哨兵）。 */
    public static void recordEntry(Stack<?> damageContainers) {
        final Deque<Integer> depths = DEPTHS.get();
        if (depths.size() >= MAX_NESTING) {
            // 说明存在未配对的记录（异常逃逸）；清空比继续错配更安全（后续 restore 退化为 no-op）。
            depths.clear();
        }
        depths.push(damageContainers == null ? NO_TARGET_DEPTH : damageContainers.size());
    }

    /**
     * 离开 {@code hurt}（{@code @At("RETURN")}）：弹出本层记录，把栈截断回进入时的深度。
     *
     * <p>本方法**首次被调用**时输出一行一次性存活日志（无论本次 pop 次数是 0 还是 1），
     * 用于区分「补丁活着」与「补丁静默失效（上游重构 / 字段删除）」；之后不再输出，不刷屏。
     *
     * @return 实际执行的 {@code pop} 次数（未修复路径为 1；已 backport / 正常路径为 0）
     */
    public static int restoreEntry(Stack<?> damageContainers) {
        final Deque<Integer> depths = DEPTHS.get();
        if (depths.isEmpty()) {
            // 无本层记录（配对失同步）：保守 no-op，绝不猜测深度。
            return 0;
        }
        final int targetDepth = depths.pop();
        if (depths.isEmpty()) {
            DEPTHS.remove(); // 不在工作线程上长期保留空容器
        }
        final int beforeRestore = (damageContainers == null) ? NO_TARGET_DEPTH : damageContainers.size();
        final int popped = sanitize(damageContainers, targetDepth);
        if (FIRST_RESTORE_LOGGED.compareAndSet(false, true)) {
            emitLog("[" + PATCH_ID + "] DamageContainer leak patch active (first hurt: entryDepth=" + targetDepth
                    + ", beforeRestore=" + beforeRestore + ", popped=" + popped + ")");
        }
        return popped;
    }

    /**
     * 纯函数：把 {@code damageContainers} 截断回 {@code targetDepth}。
     *
     * <p>{@code targetDepth < 0}（哨兵）或栈为 null 时直接返回 0；只做「比目标更深才 pop」，
     * 因此目标深度大于当前深度时同样是 no-op。
     *
     * @return 实际执行的 {@code pop} 次数
     */
    public static int sanitize(Stack<?> damageContainers, int targetDepth) {
        if (damageContainers == null || targetDepth < 0) {
            return 0;
        }
        int popped = 0;
        while (damageContainers.size() > targetDepth) {
            damageContainers.pop();
            popped++;
        }
        return popped;
    }

    /** 诊断用：当前线程尚未消费的进入记录数（正常情况下应为 0）。 */
    public static int pendingRecords() {
        return DEPTHS.get().size();
    }

    /** 诊断用：一次性存活日志是否已输出过。 */
    public static boolean livenessLogged() {
        return FIRST_RESTORE_LOGGED.get();
    }

    /** 落点异常绝不能影响伤害管线：日志失败一律吞掉并退回 System.out。 */
    private static void emitLog(String message) {
        try {
            logSink.accept(message);
        } catch (Throwable ignored) {
            System.out.println(message);
        }
    }
}
