
var M = Java.loadClass("net.minecraft.client.Minecraft");
var S = Java.loadClass("net.minecraft.client.Screenshot");
var FT = Java.loadClass("net.minecraft.tags.FluidTags");
var mc = M.getInstance();
var p = mc.player;
mc.gui.getChat().clearMessages(true);
var st = {
  air: p.getAirSupply(), maxAir: p.getMaxAirSupply(),
  eyeInWater: p.isEyeInFluid(FT.WATER),
  health: p.getHealth(), food: p.getFoodData().getFoodLevel(),
  guiH: mc.getWindow().getGuiScaledHeight()
};
S.grab(mc.gameDirectory, "autojs_water_02.png", mc.getMainRenderTarget(), function(c) { console.info("shot-done: " + c.getString()); });
JSON.stringify(st);
