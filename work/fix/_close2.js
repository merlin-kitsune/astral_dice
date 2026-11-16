
var M = Java.loadClass("net.minecraft.client.Minecraft");
var mc = M.getInstance();
mc.options.pauseOnLostFocus = false;
mc.setScreen(null);
mc.gui.getChat().clearMessages(true);
JSON.stringify({ screen: mc.screen == null ? "none" : "" + mc.screen.getClass().getName(), where: AutoJS.where(), pauseOnLostFocus: mc.options.pauseOnLostFocus });
