package com.merlinkitsune.astral_dice.client;

import java.util.Iterator;
import java.util.Map;

import com.google.common.collect.Maps;

public class ClientDamageNumbers {
    private static final Map<Integer, FloatingNumber> activeNumbers = Maps.newHashMap();
    private static final int DURATION = 40;

    public static void add(int entityId, int damage, int color) {
        activeNumbers.put(entityId, new FloatingNumber(damage, color, DURATION));
    }

    public static Map<Integer, FloatingNumber> getActiveNumbers() {
        return activeNumbers;
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, FloatingNumber>> it = activeNumbers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, FloatingNumber> entry = it.next();
            entry.getValue().tick();
            if (entry.getValue().remaining <= 0) {
                it.remove();
            }
        }
    }

    public static class FloatingNumber {
        public final int damage;
        public final int color;
        public int remaining;

        public FloatingNumber(int damage, int color, int remaining) {
            this.damage = damage;
            this.color = color;
            this.remaining = remaining;
        }

        public void tick() {
            remaining--;
        }
    }
}
