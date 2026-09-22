package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.LivingPageImpact;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class LivingPageFlightScheduler {
    public static final double SPEED_BLOCKS_PER_TICK = 2.4D;

    private static final double SNAP_DISTANCE = 0.4D;

    private static final long MAX_AGE_TICKS = 400L;

    private static final int TRAIL_PARTICLES_PER_STEP = 2;

    private static final double TRAIL_SPACING = 0.45D;

    private static final int IMPACT_BURST_PARTICLES = 20;

    private static final double LAUNCH_FORWARD_OFFSET = 1.0D;

    private static final double EYE_CLEAR_RADIUS = 1.25D;

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingPageFlightScheduler.class);

    private static final List<Pending> PENDING = new ArrayList<>();

    private static double lastFlightMinEyeDistance = -1.0D;

    private static final class Pending {
        private final ServerLevel level;
        private final ServerPlayer caster;
        private final LivingEntity target;
        private final long startTick;

        private final int flightTicks;
        private Vec3 pos;
        private int elapsed;

        private Pending(ServerLevel level, ServerPlayer caster, LivingEntity target, Vec3 pos, int flightTicks) {
            this.level = level;
            this.caster = caster;
            this.target = target;
            this.pos = pos;
            this.flightTicks = flightTicks;
            this.startTick = level.getGameTime();
        }
    }

    private LivingPageFlightScheduler() {
    }

    public static boolean launch(ServerLevel level, ServerPlayer caster, LivingEntity target) {
        if (level == null || caster == null || target == null) return false;
        Vec3 start = launchOrigin(caster, target);
        lastFlightMinEyeDistance = -1.0D;
        double distance = start.distanceTo(target.getBoundingBox().getCenter());
        int flightTicks = (int) Math.max(1L, (long) Math.ceil(distance / SPEED_BLOCKS_PER_TICK));
        PENDING.add(new Pending(level, caster, target, start, flightTicks));
        return true;
    }

    private static Vec3 launchOrigin(ServerPlayer caster, LivingEntity target) {
        Vec3 eye = caster.getEyePosition();
        Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
        double distance = delta.length();
        if (distance <= 1.0E-4D) return eye;
        double offset = Math.min(LAUNCH_FORWARD_OFFSET, distance * 0.5D);
        return eye.add(delta.scale(offset / distance));
    }

    public static int pendingCount(ServerLevel level) {
        if (level == null) return 0;
        int count = 0;
        for (Pending p : PENDING) {
            if (p.level == level) count++;
        }
        return count;
    }

    public static long pendingRemainingTicks(ServerLevel level) {
        if (level == null) return -1L;
        long best = Long.MAX_VALUE;
        for (Pending p : PENDING) {
            if (p.level != level) continue;
            best = Math.min(best, Math.max(0L, (long) p.flightTicks - p.elapsed));
        }
        return best == Long.MAX_VALUE ? -1L : best;
    }

    public static double lastFlightMinEyeDistance() {
        return lastFlightMinEyeDistance;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) return;

        List<Pending> due = new ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            due.add(it.next());
            it.remove();
        }
        for (Pending p : due) {
            try {
                advance(p);
            } catch (Exception ex) {
                LOGGER.warn("[Astral Dice] 活体书页飞行结算失败,已丢弃本次飞行", ex);
            }
        }
    }

    private static void advance(Pending p) {
        if (!stillValid(p)) return;
        if (p.level.getGameTime() - p.startTick > MAX_AGE_TICKS) return;
        Vec3 dest = p.target.getBoundingBox().getCenter();
        Vec3 delta = dest.subtract(p.pos);
        double remaining = delta.length();
        double step = Math.min(SPEED_BLOCKS_PER_TICK, remaining);
        Vec3 prev = p.pos;
        if (remaining > 1.0E-4D) {
            p.pos = p.pos.add(delta.normalize().scale(step));
        }
        p.elapsed++;

        emitTrail(p, prev, p.pos);

        if (p.pos.distanceToSqr(dest) <= SNAP_DISTANCE * SNAP_DISTANCE || p.elapsed >= p.flightTicks) {
            p.pos = dest;
            impact(p, dest);
            return;
        }
        PENDING.add(p);
    }

    private static void emitTrail(Pending p, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / TRAIL_SPACING));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, (double) i / (double) steps);
            if (insideEyeClear(p.level, at)) continue;
            p.level.sendParticles(ParticleTypes.END_ROD,
                    at.x, at.y, at.z, TRAIL_PARTICLES_PER_STEP, 0.03D, 0.03D, 0.03D, 0.0D);
            trackEyeDistance(p, at);
        }
    }

    private static boolean insideEyeClear(ServerLevel level, Vec3 at) {
        double radiusSqr = EYE_CLEAR_RADIUS * EYE_CLEAR_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.getEyePosition().distanceToSqr(at) < radiusSqr) return true;
        }
        return false;
    }

    private static void trackEyeDistance(Pending p, Vec3 at) {
        double distance = at.distanceTo(p.caster.getEyePosition());
        if (lastFlightMinEyeDistance < 0.0D || distance < lastFlightMinEyeDistance) {
            lastFlightMinEyeDistance = distance;
        }
    }

    private static boolean stillValid(Pending p) {
        if (p.target == null || p.target.isRemoved() || !p.target.isAlive()) return false;
        if (p.target.level() != p.level) return false;
        if (p.caster == null || p.caster.isRemoved() || !p.caster.isAlive()) return false;
        return p.caster.level() == p.level;
    }

    private static void impact(Pending p, Vec3 center) {
        if (!insideEyeClear(p.level, center)) {
            p.level.sendParticles(ParticleTypes.END_ROD,
                    center.x, center.y, center.z, IMPACT_BURST_PARTICLES, 0.25D, 0.25D, 0.25D, 0.02D);
            p.level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF),
                    center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        LivingPageImpact.resolve(p.level, p.caster, p.target);
    }
}
