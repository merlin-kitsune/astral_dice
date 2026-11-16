
var M = Java.loadClass("net.minecraft.client.Minecraft");
var S = Java.loadClass("net.minecraft.client.Screenshot");
var FT = Java.loadClass("net.minecraft.tags.FluidTags");
var mc = M.getInstance();
var p = mc.player;
mc.gui.getChat().clearMessages(true);
var st = {
  air: p.getAirSupply(), maxAir: p.getMaxAirSupply(),
  eyeInWater: p.isEyeInFluid(FT.WATER), inWater: p.isInWater(),
  guiH: mc.getWindow().getGuiScaledHeight(), guiW: mc.getWindow().getGuiScaledWidth(),
  rightHeight: null, leftHeight: null,
  health: p.getHealth(), food: p.getFoodData().getFoodLevel()
};
var FG = Java.loadClass("com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui");
st.rightHeight = FG.INSTANCE.rightHeight;
st.leftHeight = FG.INSTANCE.leftHeight;
S.grab(mc.gameDirectory, "autojs_water_03.png", mc.getMainRenderTarget(), function(c) { console.info("shot-done: " + c.getString()); });
JSON.stringify(st);
