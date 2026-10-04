package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.card.FateGuidanceCardItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Iron 的法术与魔法书 (irons_spellbooks) 联动 —— 26.1.2 线**反射版**。
 *
 * <p><b>为什么用反射</b>:Iron 在 Modrinth 上最高只发布到 1.21.1，**没有 26.1.x 构建** ⇒
 * 本线无法声明 {@code compileOnly} 依赖。但按用户裁决(2026-10-04)「本线属 **NeoForge 端口**，
 * **联动必须保留**，即使该模组当前不存在」⇒ 改为**反射注册 + 反射读写**:
 * 模组未安装时 {@link #init()} 直接返回(零开销);将来发布 26.1.x 版后**无需改代码即自动生效**。
 *
 * <p>行为与 1.21.1 的编译期版逐条等价:**命运的指引激活期间，法术魔力消耗减半**
 * (仅处理消耗方向 —— 新魔力 < 旧魔力时把消耗量减半)。
 *
 * <p>注册范式与 {@link WaystoneWarpCompat} 完全一致(同样是「可选联动 + 反射注册」)。
 */
public final class IronSpellbooksCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronSpellbooksCompat.class);

    /** Iron 的魔力变更事件(编译期不可得 ⇒ 以字符串类名反射获取)。 */
    private static final String EVENT_CLASS = "io.redspace.ironsspellbooks.api.events.ChangeManaEvent";

    private static boolean registered = false;

    private IronSpellbooksCompat() {
    }

    /** 在 Iron 加载后调用(仅在 NeoForge 主类中调用)。 */
    public static void init() {
        if (registered || !ModList.get().isLoaded("irons_spellbooks")) {
            return;
        }
        try {
            Class<?> eventClass = Class.forName(EVENT_CLASS);
            Class<?> busClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
            Object bus = busClass.getField("EVENT_BUS").get(null);
            Method addListener = bus.getClass().getMethod("addListener", Class.class, Consumer.class);
            @SuppressWarnings("unchecked")
            Consumer<Object> handler = IronSpellbooksCompat::onChangeMana;
            addListener.invoke(bus, eventClass, handler);
            registered = true;
            LOGGER.info("[Astral Dice] Iron's Spells 'n Spellbooks integration enabled (reflective).");
        } catch (Throwable t) {
            LOGGER.warn("[Astral Dice] Failed to register Iron's Spellbooks integration: {}", t.toString());
        }
    }

    /** 命运的指引·魔力:激活期间法术魔力消耗减半(仅消耗方向)。 */
    private static void onChangeMana(Object event) {
        try {
            Object entity = invoke(event, "getEntity");
            if (!(entity instanceof Player player)) return;
            if (player.level().isClientSide()) return;
            if (!FateGuidanceCardItem.isFateGuidanceActive(player)) return;
            Object oldRaw = invoke(event, "getOldMana");
            Object newRaw = invoke(event, "getNewMana");
            if (!(oldRaw instanceof Number) || !(newRaw instanceof Number)) return;
            float oldMana = ((Number) oldRaw).floatValue();
            float newMana = ((Number) newRaw).floatValue();
            // 仅处理消耗方向(新魔力 < 旧魔力):将消耗量减半
            if (newMana < oldMana) {
                Method setter = event.getClass().getMethod("setNewMana", float.class);
                setter.invoke(event, newMana + (oldMana - newMana) / 2.0f);
            }
        } catch (ReflectiveOperationException ignored) {
            // 版本 API 不兼容时静默跳过,不影响正常游戏
        }
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }
}
