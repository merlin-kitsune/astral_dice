var MCQ = Java.loadClass("net.minecraft.client.Minecraft");
var mc = MCQ.getInstance();
var chat = mc.gui.getChat();
var out = [];
for (var i = 0; i < chat.allMessages.size(); i++) {
  var l = chat.allMessages.get(i);
  out.push(String(l.content().getString()));
}
JSON.stringify(out.slice(-8));