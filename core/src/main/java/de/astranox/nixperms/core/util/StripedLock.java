package de.astranox.nixperms.core.util;

import java.util.concurrent.locks.ReentrantLock;

public final class StripedLock {

    private final ReentrantLock[] stripes;

    public StripedLock(int stripeCount) {
        if (stripeCount < 1) throw new IllegalArgumentException("stripeCount must be positive");
        this.stripes = new ReentrantLock[stripeCount];
        for (int index = 0; index < stripeCount; index++) {
            stripes[index] = new ReentrantLock();
        }
    }

    public ReentrantLock forKey(Object key) {
        int hash = key == null ? 0 : spread(key.hashCode());
        return stripes[(hash & Integer.MAX_VALUE) % stripes.length];
    }

    private int spread(int hash) {
        return hash ^ (hash >>> 16);
    }
}
