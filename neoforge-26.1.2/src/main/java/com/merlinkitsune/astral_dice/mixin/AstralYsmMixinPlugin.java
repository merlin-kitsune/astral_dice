package com.merlinkitsune.astral_dice.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * {@code astral_dice.ysm.mixins.json} 的加载闸门：只有 YSM 在场时才应用兼容层 mixin。
 *
 * <p>为什么需要闸门：本层注入的是**原版**接缝（{@code EntityRenderDispatcher}、
 * {@code SubmitNodeCollection}、{@code RenderTypes}），它们天天被命中。若 YSM 缺席还让它们挂上，
 * 就得完全依赖运行期窗口把关；闸门把它们整组摘掉更省心，也让「YSM 不在场 ⇒ 零影响」成为结构性事实。
 *
 * <p>本类**不得**引用任何客户端类（专用服务端也会实例化本插件）；判定只用 {@code ModList}
 * 与类存在性。判定不了时返回 true（失败开放）—— 真正的缺席会被运行期窗口兜住，
 * 而误判为缺席只会让兼容层静默失效。
 */
public final class AstralYsmMixinPlugin implements IMixinConfigPlugin {

    private static final String YSM_MODID = "yes_steve_model";
    private static final String YSM_MAIN_CLASS = "com.elfmcys.yesstevemodel.YesSteveModel";

    @Override
    public void onLoad(String mixinPackage) {
        // 无需初始化：判定在 shouldApplyMixin 里按需做（此时 ModList 已可用）
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ysmPresent();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 无跨配置依赖
    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需变换
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需变换
    }

    private static boolean ysmPresent() {
        try {
            ModList modList = ModList.get();
            if (modList != null) {
                return modList.isLoaded(YSM_MODID);
            }
        } catch (Throwable ignored) {
            // 退到类探测
        }
        try {
            Class.forName(YSM_MAIN_CLASS, false, AstralYsmMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException notFound) {
            return false;
        } catch (Throwable other) {
            return true;
        }
    }
}
