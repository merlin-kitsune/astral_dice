package com.merlinkitsune.astral_dice.compat.accessories;

import java.util.Map;

import com.merlinkitsune.astral_dice.AstralDiceMod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * <b>饰品栏槽位图标自检(客户端一次性)</b> —— 把「哪个槽位的图标取不到」直接打进日志。
 *
 * <h2>⚠️ 判据是「图集成员资格」,不是「文件存在」(2026-09-29 实测踩坑)</h2>
 * <p>Accessories 的槽位图标**不是直接绑贴图**,而是从**原版 {@code minecraft:blocks} 图集**里取 sprite ——
 * 它在自己的 jar 里往该图集加了一条 sprite source:
 * <pre>{ "type": "directory", "source": "gui/slot", "prefix": "gui/slot/" }</pre>
 * ⇒ 槽位图标**必须**放在 {@code assets/<ns>/textures/gui/slot/<name>.png},并把 {@code icon} 写成
 * {@code <ns>:gui/slot/<name>}。放在别处(哪怕文件真实存在、路径也自洽)会**取不到 sprite**,
 * 界面画成洋红/黑的缺失贴图,而且**不会**留下 {@code Failed to load texture} 之类的告警
 * (missingno 兜底路径不打 warning)、也不影响游戏运行 ⇒ 极难从日志发现。
 *
 * <p>本自检因此比对**两条**判据,缺一不可:
 * <ol>
 *   <li>{@code fileExists} —— 资源管理器里有没有这个 png;</li>
 *   <li>{@code inAtlas} —— 它有没有被收进 {@code minecraft:blocks} 图集(判据 = 取到的 sprite 不是
 *       {@code minecraft:missingno})。**这一条才是渲染会不会变紫黑格的真正判据**。</li>
 * </ol>
 * 历史教训:只查文件存在的那一版自检对本次缺陷**误报通过**(15/15 exists=true,画面却有 3 格紫黑格)。
 *
 * <p>⚠️ 只在**客户端**、且仅在**进入过世界之后**跑一次(之前图集尚未 stitch 完)。
 * ⚠️ 本类只被 {@code AstralDiceClient} 在「Accessories 在场」时加载 ⇒ 服务端与无 Accessories 的环境零影响。
 */
public final class AccessoriesClientIconCheck {

    /** 图集里代表「缺失」的 sprite(取不到目标 sprite 时会回退到它)。 */
    private static final ResourceLocation MISSINGNO = new ResourceLocation("minecraft", "missingno");

    private static boolean ran = false;

    private AccessoriesClientIconCheck() {
    }

    /** 注册一次性自检(幂等;必须在客户端初始化期调用)。 */
    public static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ran || client.level == null) {
                return; // 等资源加载/图集 stitch 完成并真正进过世界
            }
            ran = true;
            try {
                run(client);
            } catch (Throwable t) {
                AstralDiceMod.LOGGER.warn("[Astral Dice][Accessories] 槽位图标自检失败(忽略): {}", t.toString());
            }
        });
    }

    private static void run(Minecraft client) {
        // ⚠️ 1.20.1 的 `Minecraft#getTextureAtlas` 返回的是 `Function<ResourceLocation, TextureAtlasSprite>`
        //    (不是 TextureAtlas 本体)—— 用 apply(icon) 直接取 sprite 即可。
        java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas =
                client.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        Map<String, ?> slots = io.wispforest.accessories.data.SlotTypeLoader.INSTANCE.getSlotTypes(true);
        StringBuilder report = new StringBuilder();
        int noFile = 0;
        int noAtlas = 0;
        for (Map.Entry<String, ?> entry : slots.entrySet()) {
            io.wispforest.accessories.api.slot.SlotType slot =
                    (io.wispforest.accessories.api.slot.SlotType) entry.getValue();
            ResourceLocation icon = slot.icon();
            ResourceLocation texture = new ResourceLocation(icon.getNamespace(), "textures/" + icon.getPath() + ".png");
            boolean fileExists = client.getResourceManager().getResource(texture).isPresent();

            TextureAtlasSprite sprite = atlas.apply(icon);
            SpriteContents contents = sprite == null ? null : sprite.contents();
            boolean inAtlas = contents != null && !MISSINGNO.equals(contents.name());

            if (!fileExists) {
                noFile++;
            }
            if (!inAtlas) {
                noAtlas++;
            }
            report.append('\n').append("  ").append(String.format("%-12s", entry.getKey()))
                    .append(" icon=").append(String.format("%-44s", icon))
                    .append(" file=").append(fileExists ? "yes" : "**NO**")
                    .append(" inBlocksAtlas=").append(inAtlas ? "yes" : "**NO(会画成紫黑格)**");
        }
        AstralDiceMod.LOGGER.info("AP_FAB_SLOT_ICON: 槽位图标自检 槽位={} 文件缺失={} 图集未收录={}",
                slots.size(), noFile, noAtlas);
        AstralDiceMod.LOGGER.info("AP_FAB_SLOT_ICON_DETAIL:{}", report);
    }
}
