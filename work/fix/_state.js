
var M = Java.loadClass("net.minecraft.client.Minecraft");
var mc = M.getInstance();
JSON.stringify({
  screen: mc.screen == null ? "none" : "" + mc.screen.getClass().getName(),
  health: mc.player.getHealth(),
  dead: mc.player.isDeadOrDying(),
  pos: "" + mc.player.getX() + "," + mc.player.getY() + "," + mc.player.getZ(),
  gamemode: "" + mc.gameMode.getPlayerMode()
});
