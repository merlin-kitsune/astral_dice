import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import com.merlinkitsune.astral_dice.fixes.DamageStackSanitizer;

/**
 * DamageStackSanitizer 的纯 Java 语义验证（不依赖 Gradle / Minecraft / NeoForge / Mixin / slf4j）。
 *
 * <p>本测试直接编译真实的 {@code DamageStackSanitizer.java}（它只依赖 JDK）并调用它，不复制任何逻辑。
 *
 * <p>独立可复现的运行命令（在仓库根 F:\MCProject\astral_dice_multiloader 执行）：
 * <pre>
 * pwsh -NoProfile -File tools\run_mixin_stack_sanitizer_test.ps1
 * </pre>
 * 等价手工命令（javac/java 取 JAVA_HOME 下的 21；本机亦可用
 * {@code C:\Program Files\Zulu\zulu-21\bin}）：
 * <pre>
 * $out = "$env:TEMP\mss-out"
 * Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
 * New-Item -ItemType Directory -Force -Path $out | Out-Null
 * & javac -encoding UTF-8 -d $out `
 *     'neoforge-1.21.1\src\main\java\com\merlinkitsune\astral_dice\fixes\DamageStackSanitizer.java' `
 *     'tools\mixin-stack-sanitizer-test\DamageStackSanitizerTest.java'
 * & java -cp $out DamageStackSanitizerTest
 * </pre>
 *
 * <p>覆盖（用例编号与交付报告一致）：0 = 一次性存活日志（首次 restore，popped=0 也要打印）；
 * A = 未修复路径补 1 次 pop；B = 已 backport 路径 0 次 pop 且不抛 EmptyStackException；
 * C = 嵌套 hurt 逐层配对；D = null / 哨兵 / null 栈边界 no-op；E = 方法结尾出口 0 次 pop；
 * F = 无敌帧出口 0 次 pop；G = 结构性保证（绝不弹到低于进入深度、不重建）；H = 配对失同步 no-op；
 * I = 一次性（不刷屏）。
 *
 * <p>输出刻意使用 ASCII 标签，避免 Windows 控制台编码干扰证据采集。
 */
public final class DamageStackSanitizerTest {

    private static int passed = 0;
    private static int failed = 0;

    /** 捕获一次性存活日志（测试用注入 sink，替代默认的 System.out）。 */
    private static final List<String> CAPTURED_LIVENESS_LOG = new ArrayList<>();

    /** 计数 Stack：统计真实 pop 次数（Stack.pop 的调用次数），用于断言「零次 pop」。 */
    private static final class CountingStack<E> extends Stack<E> {
        int pops;

        @Override
        public synchronized E pop() {
            pops++;
            return super.pop();
        }
    }

    private interface Body {
        void run() throws Throwable;
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        System.out.println("       " + detail);
    }

    private static void guard(String name, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            check(name + " (unexpected throwable)", false, t.getClass().getName() + ": " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        // 捕获存活日志（默认落点是 System.out；此处换成内存 sink 以便断言内容）
        DamageStackSanitizer.setLogSink(CAPTURED_LIVENESS_LOG::add);

        // 0. 首次 restore 必须输出一次性存活日志——本次 popped == 0 也要打印
        //    （证明「上游 backport 之后零 pop」时注入器仍然活着）。
        guard("0 liveness-log-on-first-restore(popped=0)", () -> {
            CountingStack<String> stack = new CountingStack<>();
            stack.push("OUTER_CONTAINER");
            DamageStackSanitizer.recordEntry(stack);              // 进入深度 = 1
            stack.push("CANCEL_CONTAINER");
            stack.pop();                                          // 上游已 pop（等价于 backport 后的取消分支）
            int popped = DamageStackSanitizer.restoreEntry(stack); // 首次 restore → popped = 0
            boolean ok = popped == 0 && CAPTURED_LIVENESS_LOG.size() == 1
                    && CAPTURED_LIVENESS_LOG.get(0).contains("neoforge_fixes")
                    && CAPTURED_LIVENESS_LOG.get(0).contains("entryDepth=1")
                    && CAPTURED_LIVENESS_LOG.get(0).contains("beforeRestore=1")
                    && CAPTURED_LIVENESS_LOG.get(0).contains("popped=0")
                    && DamageStackSanitizer.livenessLogged();
            check("0 liveness-log-on-first-restore(popped=0)", ok,
                    "popped=" + popped + " livenessLogged=" + DamageStackSanitizer.livenessLogged()
                            + " logCount=" + CAPTURED_LIVENESS_LOG.size()
                            + " line=\"" + (CAPTURED_LIVENESS_LOG.isEmpty() ? "<none>" : CAPTURED_LIVENESS_LOG.get(0)) + "\"");
        });

        // A. 未修复路径：push 后走取消分支（上游不 pop）→ sanitize 必须补 1 次 pop，恢复到进入深度。
        guard("A unfixed-cancel-path", () -> {
            CountingStack<String> stack = new CountingStack<>();
            stack.push("OUTER_CONTAINER");
            DamageStackSanitizer.recordEntry(stack);              // @At("HEAD"): 进入深度 = 1
            stack.push("PEARL_IMMUNE_CONTAINER");                 // NeoForge: damageContainers.push(...)
            int popped = DamageStackSanitizer.restoreEntry(stack); // @At("RETURN"): 取消分支(上游未 pop)
            check("A unfixed-cancel-path",
                    popped == 1 && stack.pops == 1 && stack.size() == 1 && "OUTER_CONTAINER".equals(stack.peek()),
                    "popped=" + popped + " realStackPops=" + stack.pops + " size=" + stack.size()
                            + " top=" + stack.peek() + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        // B. 已 backport 路径：上游自己 pop（取消分支已修）→ 本补丁必须 0 次 pop，且不抛 EmptyStackException。
        guard("B backported-upstream-pop", () -> {
            CountingStack<String> stack = new CountingStack<>();
            stack.push("OUTER_CONTAINER");
            DamageStackSanitizer.recordEntry(stack);              // 进入深度 = 1
            stack.push("PEARL_IMMUNE_CONTAINER");
            stack.pop();                                          // 上游 backport 的 pop（新增那一行）
            int popsBefore = stack.pops;
            int popped = DamageStackSanitizer.restoreEntry(stack);
            check("B backported-upstream-pop",
                    popped == 0 && stack.pops == popsBefore && stack.size() == 1 && "OUTER_CONTAINER".equals(stack.peek()),
                    "popped=" + popped + " realStackPops=" + stack.pops + " (before=" + popsBefore + ") size=" + stack.size()
                            + " top=" + stack.peek() + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        // C. 嵌套 hurt：外层 push → 内层 hurt(进入深度 1) 正常 pop → 外层取消不 pop，逐层 sanitize 后深度必须为 0。
        guard("C nested-hurt-frames", () -> {
            CountingStack<String> stack = new CountingStack<>();
            DamageStackSanitizer.recordEntry(stack);              // 外层 HEAD: 深度 0
            stack.push("OUTER_CONTAINER");                        // 外层 push
            DamageStackSanitizer.recordEntry(stack);              // 内层 HEAD: 深度 1
            stack.push("INNER_CONTAINER");                        // 内层 push
            stack.pop();                                          // 内层正常路径: 上游 pop
            int innerPopped = DamageStackSanitizer.restoreEntry(stack); // 内层 RETURN → target 1 → 0 次
            int outerPopped = DamageStackSanitizer.restoreEntry(stack); // 外层 RETURN(取消,上游未 pop) → target 0 → 1 次
            check("C nested-hurt-frames",
                    innerPopped == 0 && outerPopped == 1 && stack.size() == 0 && DamageStackSanitizer.pendingRecords() == 0,
                    "innerPopped=" + innerPopped + " outerPopped=" + outerPopped + " finalSize=" + stack.size()
                            + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        // D. 边界：damageContainers == null、target < 0(哨兵)、栈为 null 三者都必须 no-op。
        guard("D null-and-sentinel-noop", () -> {
            DamageStackSanitizer.recordEntry(null);                       // 字段为 null → 记哨兵
            int nullFieldPopped = DamageStackSanitizer.restoreEntry(null);
            Stack<String> stack = new Stack<>();
            stack.push("X");
            int sentinelPopped = DamageStackSanitizer.sanitize(stack, DamageStackSanitizer.NO_TARGET_DEPTH);
            int anyNegativePopped = DamageStackSanitizer.sanitize(stack, -7);
            int nullStackPopped = DamageStackSanitizer.sanitize(null, 0);
            check("D null-and-sentinel-noop",
                    nullFieldPopped == 0 && sentinelPopped == 0 && anyNegativePopped == 0 && nullStackPopped == 0
                            && stack.size() == 1 && "X".equals(stack.peek()) && DamageStackSanitizer.pendingRecords() == 0,
                    "nullFieldPopped=" + nullFieldPopped + " sentinelPopped=" + sentinelPopped
                            + " anyNegativePopped=" + anyNegativePopped + " nullStackPopped=" + nullStackPopped
                            + " size=" + stack.size() + " top=" + stack.peek()
                            + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        // E. 正常路径（方法结尾出口）：上游 pop 已执行 → 0 次 pop。
        guard("E normal-method-end-path", () -> {
            CountingStack<String> stack = new CountingStack<>();
            DamageStackSanitizer.recordEntry(stack);              // 进入深度 = 0
            stack.push("NORMAL_CONTAINER");
            stack.pop();                                          // 上游方法结尾的 pop
            int popsBefore = stack.pops;
            int popped = DamageStackSanitizer.restoreEntry(stack);
            check("E normal-method-end-path",
                    popped == 0 && stack.pops == popsBefore && stack.size() == 0,
                    "popped=" + popped + " realStackPops=" + stack.pops + " (before=" + popsBefore + ") size=" + stack.size()
                            + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        // F. 正常路径（无敌帧提前返回出口）：同样 pop 后 return → 0 次 pop，且外层容器不受影响。
        guard("F normal-invulnerability-path", () -> {
            CountingStack<String> stack = new CountingStack<>();
            stack.push("OUTER_CONTAINER");
            DamageStackSanitizer.recordEntry(stack);              // 进入深度 = 1
            stack.push("INVULN_CONTAINER");
            stack.pop();                                          // 上游无敌帧分支的 pop
            int popsBefore = stack.pops;
            int popped = DamageStackSanitizer.restoreEntry(stack);
            check("F normal-invulnerability-path",
                    popped == 0 && stack.pops == popsBefore && stack.size() == 1 && "OUTER_CONTAINER".equals(stack.peek()),
                    "popped=" + popped + " realStackPops=" + stack.pops + " (before=" + popsBefore + ") size=" + stack.size()
                            + " top=" + stack.peek() + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        // G. 结构性保证：上游若多 pop（栈已浅于进入深度），本补丁不重建、不抛异常、绝不继续弹。
        guard("G never-pop-below-entry-depth", () -> {
            CountingStack<String> stack = new CountingStack<>();
            stack.push("OUTER_A");
            stack.push("OUTER_B");
            DamageStackSanitizer.recordEntry(stack);              // 进入深度 = 2
            stack.push("CANCEL_CONTAINER");
            stack.pop();
            stack.pop();                                          // 上游多弹了一层(异常/未来重构)
            int popsBefore = stack.pops;
            int popped = DamageStackSanitizer.restoreEntry(stack);
            check("G never-pop-below-entry-depth",
                    popped == 0 && stack.pops == popsBefore && stack.size() == 1,
                    "popped=" + popped + " realStackPops=" + stack.pops + " (before=" + popsBefore + ") size=" + stack.size()
                            + " (no rebuild, no throw, no extra pop)");
        });

        // H. 配对失同步（异常逃逸导致 RETURN 未执行）：下一次 restore 无记录时必须 no-op。
        guard("H desynced-missing-record-noop", () -> {
            CountingStack<String> stack = new CountingStack<>();
            stack.push("OUTER_CONTAINER");
            int popped = DamageStackSanitizer.restoreEntry(stack); // 无 HEAD 记录
            check("H desynced-missing-record-noop",
                    popped == 0 && stack.size() == 1 && DamageStackSanitizer.pendingRecords() == 0,
                    "popped=" + popped + " size=" + stack.size()
                            + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        // I. 一次性（不刷屏）：上面 9 次 restore 之后存活日志仍然只有 1 行。
        guard("I liveness-log-once-per-jvm", () -> {
            CountingStack<String> stack = new CountingStack<>();
            for (int i = 0; i < 50; i++) {                        // 再跑 50 次完整 hurt 往返
                DamageStackSanitizer.recordEntry(stack);
                stack.push("CONTAINER_" + i);
                DamageStackSanitizer.restoreEntry(stack);         // 正常路径: 上游已 pop? 否 —— 由 sanitize 补齐
            }
            check("I liveness-log-once-per-jvm",
                    CAPTURED_LIVENESS_LOG.size() == 1 && stack.size() == 0,
                    "logCount=" + CAPTURED_LIVENESS_LOG.size() + " (expected 1) finalSize=" + stack.size()
                            + " pendingRecords=" + DamageStackSanitizer.pendingRecords());
        });

        System.out.println();
        System.out.println("LIVENESS-LOG(captured)=" + CAPTURED_LIVENESS_LOG);
        System.out.println("SUMMARY passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }
}
