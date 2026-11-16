
var B = Java.loadClass("net.minecraft.world.level.block.Blocks");
var BP = Java.loadClass("net.minecraft.core.BlockPos");
var p = server.getPlayerList().getPlayers().get(0);
var lvl = p.level();
var bx = Math.floor(p.getX()), by = Math.floor(p.getY()), bz = Math.floor(p.getZ());
var n = 0;
for (var dy = -1; dy <= 5; dy++) {
  for (var dx = -2; dx <= 2; dx++) {
    for (var dz = -2; dz <= 2; dz++) {
      lvl.setBlock(new BP(bx+dx, by+dy, bz+dz), B.WATER.defaultBlockState(), 3); n++;
    }
  }
}
JSON.stringify({bx:bx, by:by, bz:bz, blocks:n, eyeY: p.getEyeY(), feetY: p.getY()});
