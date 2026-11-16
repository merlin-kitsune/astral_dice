
var M = Java.loadClass("net.minecraft.client.Minecraft");
var mc = M.getInstance();
mc.setScreen(null);
mc.gui.getChat().clearMessages(true);
"closed";
