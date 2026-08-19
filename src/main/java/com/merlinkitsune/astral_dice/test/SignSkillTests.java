package com.merlinkitsune.astral_dice.test;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 主动技能触发自动化测试:
 * 1. 装备立牌后触发,等待状态应正确设置;
 * 2. 等待期间重复触发按键不得破坏等待状态;
 * 3. 待命效果应施加于玩家。
 */
@GameTestHolder("astral_dice")
public class SignSkillTests {

    @GameTest(template = "empty")
    public static void testAstrologerSkillReady(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // 装备占星师立牌到第一个立牌槽位
        var curios = CuriosApi.getCuriosInventory(player);
        helper.assertTrue(curios.isPresent(), "无法获取模拟玩家的 Curios 容器");
        var handlerOpt = curios.get().getStacksHandler("stand");
        helper.assertTrue(handlerOpt.isPresent(), "无法获取 stand 栏");
        handlerOpt.get().getStacks().setStackInSlot(0, new ItemStack(ModItems.HAIQING_SIGN.get()));

        // 触发主动技能(模拟按下 J)
        BaseSignItem.performSkillForCurio(player);

        // 验证等待状态与待命效果
        helper.assertTrue(ModAttachments.getSignReadyType(player) == HaiqingSignItem.READY_TYPE,
                "占星师等待状态类型未设置(应为1)");
        helper.assertTrue(ModAttachments.getSignReadyExpire(player) > 0,
                "占星师等待到期时刻未设置");
        helper.assertTrue(player.hasEffect(ModEffects.HAIQING_READY),
                "占星师待命效果未施加");

        // 重复触发(模拟重复按 J):等待期间按键无效,不得破坏等待状态
        BaseSignItem.performSkillForCurio(player);
        helper.assertTrue(ModAttachments.getSignReadyType(player) == HaiqingSignItem.READY_TYPE,
                "重复触发破坏了占星师等待状态");
        helper.assertTrue(player.hasEffect(ModEffects.HAIQING_READY),
                "重复触发移除了占星师待命效果");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void testSecretDetectiveSkillReady(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var curios = CuriosApi.getCuriosInventory(player);
        helper.assertTrue(curios.isPresent(), "无法获取模拟玩家的 Curios 容器");
        var handlerOpt = curios.get().getStacksHandler("stand");
        helper.assertTrue(handlerOpt.isPresent(), "无法获取 stand 栏");
        handlerOpt.get().getStacks().setStackInSlot(0, new ItemStack(ModItems.BONNIE_SIGN.get()));

        BaseSignItem.performSkillForCurio(player);

        helper.assertTrue(ModAttachments.getSignReadyType(player) == BonnieSignItem.READY_TYPE,
                "秘密侦探等待状态类型未设置(应为2)");
        helper.assertTrue(ModAttachments.getSignReadyExpire(player) > 0,
                "秘密侦探等待到期时刻未设置");
        helper.assertTrue(player.hasEffect(ModEffects.BONNIE_READY),
                "秘密侦探待命效果未施加");

        // 重复触发:不得破坏等待状态
        BaseSignItem.performSkillForCurio(player);
        helper.assertTrue(ModAttachments.getSignReadyType(player) == BonnieSignItem.READY_TYPE,
                "重复触发破坏了秘密侦探等待状态");
        helper.assertTrue(player.hasEffect(ModEffects.BONNIE_READY),
                "重复触发移除了秘密侦探待命效果");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void testNoSignNoSkill(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // 未装备立牌:触发应无效,不得设置任何状态
        BaseSignItem.performSkillForCurio(player);
        helper.assertTrue(ModAttachments.getSignReadyType(player) == 0,
                "未装备立牌时不应进入等待状态");
        helper.succeed();
    }
}
