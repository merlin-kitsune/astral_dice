package com.merlinkitsune.astral_dice.test;

import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 立牌主动技能(目标选择器类)自动化测试:
 * 1. 装备立牌后触发,应进入目标选择会话(TargetSelectionManager.isSelecting);
 * 2. 选择会话进行中重复触发按键不得破坏会话(服务端守卫忽略);
 * 3. 未装备立牌时触发不得进入选择会话。
 */
@GameTestHolder("astral_dice")
public class SignSkillTests {

        @GameTest(template = "empty")
        public static void testAstrologerSkillSelection(GameTestHelper helper) {
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                // 装备占星师立牌到第一个立牌槽位
                var curios = CuriosApi.getCuriosInventory(player);
                helper.assertTrue(curios.isPresent(), "无法获取模拟玩家的 Curios 容器");
                var handlerOpt = curios.get().getStacksHandler("stand");
                helper.assertTrue(handlerOpt.isPresent(), "无法获取 stand 栏");
                handlerOpt.get().getStacks().setStackInSlot(0, new ItemStack(ModItems.HAIQING_SIGN.get()));

                // 触发主动技能(模拟按下 J):应进入目标选择会话
                BaseSignItem.performSkillForCurio(player);
                helper.assertTrue(TargetSelectionManager.isSelecting(player),
                                "占星师触发后未进入目标选择会话");

                // 重复触发(模拟重复按 J):选择会话进行中按键无效,不得破坏会话
                BaseSignItem.performSkillForCurio(player);
                helper.assertTrue(TargetSelectionManager.isSelecting(player),
                                "重复触发破坏了占星师目标选择会话");

                // 清理:取消会话(直接由管理器清理,避免影响其他测试)
                TargetSelectionManager.cancelSessionForTests(player);
                helper.succeed();
        }

        @GameTest(template = "empty")
        public static void testSecretDetectiveSkillSelection(GameTestHelper helper) {
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                var curios = CuriosApi.getCuriosInventory(player);
                helper.assertTrue(curios.isPresent(), "无法获取模拟玩家的 Curios 容器");
                var handlerOpt = curios.get().getStacksHandler("stand");
                helper.assertTrue(handlerOpt.isPresent(), "无法获取 stand 栏");
                handlerOpt.get().getStacks().setStackInSlot(0, new ItemStack(ModItems.BONNIE_SIGN.get()));

                BaseSignItem.performSkillForCurio(player);
                helper.assertTrue(TargetSelectionManager.isSelecting(player),
                                "秘密侦探触发后未进入目标选择会话");

                BaseSignItem.performSkillForCurio(player);
                helper.assertTrue(TargetSelectionManager.isSelecting(player),
                                "重复触发破坏了秘密侦探目标选择会话");

                TargetSelectionManager.cancelSessionForTests(player);
                helper.succeed();
        }

        @GameTest(template = "empty")
        public static void testNoSignNoSkill(GameTestHelper helper) {
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                // 未装备立牌:触发应无效,不得进入目标选择会话
                BaseSignItem.performSkillForCurio(player);
                helper.assertTrue(!TargetSelectionManager.isSelecting(player),
                                "未装备立牌时不应进入目标选择会话");
                helper.succeed();
        }
}
