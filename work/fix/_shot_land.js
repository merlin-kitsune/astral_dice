
var M = Java.loadClass("net.minecraft.client.Minecraft");
var S = Java.loadClass("net.minecraft.client.Screenshot");
var mc = M.getInstance();
S.grab(mc.gameDirectory, "autojs_land_01.png", mc.getMainRenderTarget(), function(c) { console.info("shot-done: " + c.getString()); });
"grab-requested";
