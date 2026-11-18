var MCQ = Java.loadClass("net.minecraft.client.Minecraft");
var mc = MCQ.getInstance();
var srv = mc.getSingleplayerServer();
var ss = srv.createCommandSourceStack();
var cmd = srv.getCommands();
var out = [];
out.push("air-hollow=" + cmd.performPrefixedCommand(ss, "fill ~-1 ~ ~-1 ~1 ~3 ~1 minecraft:air"));
out.push("box-air=" + cmd.performPrefixedCommand(ss, "fill ~-2 ~-1 ~-2 ~2 ~4 ~2 minecraft:air"));
JSON.stringify(out);