// ft_cmd_watcher.js — fabric 测试台「手动命令注入」的观察者（KubeJS **服务端**脚本）。
//
// 由 `ft_env.ps1 --side server --install-watcher` 安装到
//   fabric-1.20.1/run/server/kubejs/server_scripts/ft_cmd_watcher.js
// 队列文件（模板里的 __FT_QUEUE_PATH__ 会被替换成绝对路径，斜杠统一为正斜杠）：
//   fabric-1.20.1/run/server/kubejs/.ft_cmd_queue.txt
//
// 工作方式：ft_inject.ps1 --channel kubejs 只往队列文件**追加一行**；
//   本脚本每 20 tick（1s）读走全部内容 → 逐行 server.runCommand() → 把队列清空。
//
// ⚠️ 生效时机：KubeJS 只在**冷启动**或 `/kubejs reload server_scripts` 后加载脚本。
//    装完本脚本必须冷启（或先经 rcon 通道发一次 `/kubejs reload server_scripts`）才会开始轮询。
//
// ⚠️ 本脚本不依赖任何「按名字猜」的 API。逐条依据 = `javap` 实查 KubeJS 工件
//    （build/loom-cache/remapped_working/remapped.maven.modrinth-kubejs-*.jar）：
//      · server.runCommand(String) -> int
//          dev.latvian.mods.kubejs.core.MinecraftServerKJS:
//            public default int kjs$runCommand(java.lang.String);
//            public default int kjs$runCommandSilent(java.lang.String);
//          （KubeJS 在 JS 侧去掉 `kjs$` 前缀 ⇒ `server.runCommand(...)`）
//      · Java.loadClass(String) -> Object
//          dev.latvian.mods.kubejs.bindings.JavaWrapper#loadClass(java.lang.String)
//      · JsonIO.readString(java.nio.file.Path) -> String
//          dev.latvian.mods.kubejs.util.JsonIO#readString(java.nio.file.Path)
//      · java.nio.file.Paths.get(String) / java.nio.file.Files.writeString(Path, CharSequence)
//          JDK 标准 API（游戏跑 Java 17，writeString 自 Java 11 起可用）
//      · `ServerEvents.tick(function (event) { event.server ... })` 与 `console.info/error`
//          与同目录 event_bridge_probe.js 完全同形；该探针已实测在
//          run/server/logs/latest.log:222-229 打出 `[event-probe] …` 行（证明事件与服务端句柄可用）。
//
// ⚠️ **未在实机冒烟**（见 README「实测 vs 仅静态校验」）：上面三条 Java 互操作在 KubeJS 的
//    ClassFilter 下是否被放行尚未实测；若被拒，只会打一行 `[ft-inject] ERR:`，不影响服务端运行。
//    需要一条「已验证」的注入通道时请用 ft_inject 的默认通道 `rcon`。
//
// ⚠️ 全部用 `var` 而非 `const`/`let`：回调每 tick 都会执行一次，KubeJS 用的 Rhino 会在第二次
//    执行时报 `redeclaration of var X`（实测 2026-09-29，见 event_bridge_probe.js 注释）。

var FT_QUEUE = '__FT_QUEUE_PATH__';
var FT_POLL_TICKS = 20; // 每 20 tick（约 1 秒）轮询一次
var FT_COUNTER = 0;
var FT_JsonIO = null;
var FT_Paths = null;
var FT_Files = null;

ServerEvents.tick(function (event) {
  var server = event.server;
  if (!server) {
    return;
  }
  FT_COUNTER++;
  if (FT_COUNTER % FT_POLL_TICKS !== 0) {
    return;
  }

  var raw;
  try {
    if (FT_JsonIO === null) {
      FT_JsonIO = Java.loadClass('dev.latvian.mods.kubejs.util.JsonIO');
      FT_Paths = Java.loadClass('java.nio.file.Paths');
      FT_Files = Java.loadClass('java.nio.file.Files');
    }
    raw = FT_JsonIO.readString(FT_Paths.get(FT_QUEUE));
  } catch (err) {
    console.error('[ft-inject] ERR 读队列失败: ' + err);
    return;
  }

  if (raw === null || raw === undefined) {
    return;
  }
  var text = '' + raw;
  if (text.trim() === '') {
    return;
  }

  // 先执行、后清空；执行中途异常也不吞掉后续命令。
  var lines = text.split('\n');
  for (var i = 0; i < lines.length; i++) {
    var line = '' + lines[i];
    var cmd = line.replace(/^\s*\/?/, '').trim(); // 去掉行首空白与可选的 '/'
    if (cmd === '') {
      continue;
    }
    try {
      var rc = server.runCommand(cmd);
      console.info('[ft-inject] ran rc=' + rc + ' :: ' + cmd);
    } catch (err) {
      console.error('[ft-inject] ERR 执行失败 :: ' + cmd + ' :: ' + err);
    }
  }

  try {
    FT_Files.writeString(FT_Paths.get(FT_QUEUE), '');
    console.info('[ft-inject] queue drained');
  } catch (err) {
    console.error('[ft-inject] ERR 清空队列失败: ' + err);
  }
});
