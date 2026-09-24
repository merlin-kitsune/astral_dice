package com.merlinkitsune.astral_dice.audio;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组自定义音效的**注册表**（8 条）。
 *
 * <p>每条音效由两部分组成，缺一不可：
 * <ol>
 *   <li><b>资源</b>：{@code assets/astral_dice/sounds/<id>.ogg}（单声道 Ogg Vorbis）＋
 *       {@code assets/astral_dice/sounds.json} 里同名的声明条目；</li>
 *   <li><b>注册</b>：本类里的 {@link SoundEvent}（id 与上面完全一致）。</li>
 * </ol>
 * 资源声明缺失时 {@code SoundEvent} 仍能注册成功，但播放时静默无声，
 * 故新增音效必须**三处同时**落地（ogg / sounds.json / 本类）。
 *
 * <p>播放一律走 {@link SoundPlayback}（区分「只发给本人」与「世界中广播」两种语义），
 * 不要在调用点直接写 {@code level.playSound}。
 *
 * <p><b>源素材对照</b>（仓库根 {@code sound/} 目录，wav 转 ogg 的脚本见 {@code temp/t124/}）：
 * <pre>
 *   wallet.wav                    -> wallet_deposit
 *   coin.wav                      -> coin_withdraw
 *   coin1.wav                     -> coin_bag_withdraw
 *   damage_effect_card_hit.wav    -> damage_effect_card_hit
 *   damage_effect_card_bighit.wav -> damage_effect_card_bighit
 *   boost_effect_card_use.wav     -> boost_effect_card_use
 *   effect_card_use1.wav          -> effect_card_use_target
 *   effect_card_use2.wav          -> effect_card_use_self
 * </pre>
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AstralDiceMod.MODID);

    /** 存钱：星币钱包按钮（存入成功时）。源素材 {@code wallet.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> WALLET_DEPOSIT = register("wallet_deposit");

    /** 取钱：取出**星币**（单枚或全部）。源素材 {@code coin.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> COIN_WITHDRAW = register("coin_withdraw");

    /** 取钱：取出**星币袋**（单个或全部）。源素材 {@code coin1.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> COIN_BAG_WITHDRAW = register("coin_bag_withdraw");

    /** 活体书页命中目标，本次伤害 **&lt; 8**。源素材 {@code damage_effect_card_hit.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> DAMAGE_EFFECT_CARD_HIT = register("damage_effect_card_hit");

    /** 活体书页命中目标，本次伤害 **&gt;= 8**。源素材 {@code damage_effect_card_bighit.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> DAMAGE_EFFECT_CARD_BIGHIT =
            register("damage_effect_card_bighit");

    /** 使用了狂暴 / 王之力 / 岿然不动。源素材 {@code boost_effect_card_use.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOST_EFFECT_CARD_USE = register("boost_effect_card_use");

    /** 对**其他人或目标**使用效果牌。源素材 {@code effect_card_use1.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EFFECT_CARD_USE_TARGET = register("effect_card_use_target");

    /** 对**自身**使用效果牌。源素材 {@code effect_card_use2.wav}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> EFFECT_CARD_USE_SELF = register("effect_card_use_self");

    private ModSounds() {
    }

    /** 统一用可变范围（{@code createVariableRangeEvent}）：音量决定可听半径，与其它模组音效口径一致。 */
    private static DeferredHolder<SoundEvent, SoundEvent> register(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, id)));
    }
}
