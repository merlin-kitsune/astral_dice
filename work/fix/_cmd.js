var MCQ = Java.loadClass("net.minecraft.client.Minecraft");
var mc = MCQ.getInstance();
mc.player.connection.sendCommand("effect clear @a");
mc.player.connection.sendCommand("attribute @a minecraft:generic.max_health base set 4");
"sent";