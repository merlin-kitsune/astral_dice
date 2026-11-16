var MCQ = Java.loadClass("net.minecraft.client.Minecraft");
var mc = MCQ.getInstance();
var FG = Java.loadClass("com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui");
var DCM = Java.loadClass("com.merlinkitsune.astral_dice.combat.DiceCombatModifiers");
var Mth = Java.loadClass("net.minecraft.util.Mth");
var A = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
var FT = Java.loadClass("net.minecraft.tags.FluidTags");
var p = mc.player;
var H = mc.getWindow().getGuiScaledHeight();
var h = Mth.ceil(p.getHealth());
var ab = p.getAbsorptionAmount();
var seed = FG.leftSeed(p.getAttributeValue(A.MAX_HEALTH), h, h, ab);
var lh = FG.INSTANCE.leftHeight;
var rh = FG.INSTANCE.rightHeight;
JSON.stringify({
  H: H, guiScale: mc.getWindow().getGuiScale(),
  leftHeight_end: lh, rightHeight_end: rh, seed: seed,
  defenseY: H - (lh - 10), attackY: H - (rh - 10),
  health: p.getHealth(), attrMax: p.getAttributeValue(A.MAX_HEALTH), absorption: ab,
  defense: DCM.defensePowerOf(p), attack: DCM.attackPowerDisplayOf(p), ROW: 10,
  air: p.getAirSupply(), maxAir: p.getMaxAirSupply(), eyeInWater: p.isEyeInFluid(FT.WATER),
  vehicle: p.getVehicle() == null ? "null" : String(p.getVehicle().getType())
});