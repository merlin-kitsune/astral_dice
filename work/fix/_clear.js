
var M = Java.loadClass("net.minecraft.client.Minecraft");
var mc = M.getInstance();
mc.gui.getChat().clearMessages(true);
mc.gui.getChat().clearMessages(false);
"cleared";
