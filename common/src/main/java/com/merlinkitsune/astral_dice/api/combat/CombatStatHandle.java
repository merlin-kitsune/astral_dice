package com.merlinkitsune.astral_dice.api.combat;

import java.util.concurrent.atomic.AtomicBoolean;

/** 属性钩子的注册句柄，可幂等注销。 */
public final class CombatStatHandle {

    private final Runnable unregister;
    private final AtomicBoolean active = new AtomicBoolean(true);

    CombatStatHandle(Runnable unregister) {
        this.unregister = unregister;
    }

    /** 注销该钩子；重复调用无副作用。 */
    public void unregister() {
        if (active.compareAndSet(true, false)) {
            unregister.run();
        }
    }

    /** 该钩子当前是否仍生效。 */
    public boolean isActive() {
        return active.get();
    }
}
