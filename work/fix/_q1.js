
var M = Java.loadClass("net.minecraft.client.Minecraft");
var mc = M.getInstance();
var FG = Java.loadClass("com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui");
var DCM = Java.loadClass("com.merlinkitsune.astral_dice.combat.DiceCombatModifiers");
var FT = Java.loadClass("net.minecraft.tags.FluidTags");
var p = mc.player;
JSON.stringify({
  where: AutoJS.where(),
  guiScaledHeight: mc.getWindow().getGuiScaledHeight(),
  guiScaledWidth: mc.getWindow().getGuiScaledWidth(),
  guiScale: mc.getWindow().getGuiScale(),
  rightHeight: FG.INSTANCE.rightHeight,
  leftHeight: FG.INSTANCE.leftHeight,
  attack: DCM.attackPowerDisplayOf(p),
  defense: DCM.defensePowerOf(p),
  airSupply: p.getAirSupply(),
  maxAir: p.getMaxAirSupply(),
  eyeInWater: p.isEyeInFluid(FT.WATER),
  inWater: p.isInWater(),
  onGround: p.onGround(),
  pos: "" + p.getX() + "," + p.getY() + "," + p.getZ(),
  vehicle: p.getVehicle() == null ? "none" : "" + p.getVehicle()
});
