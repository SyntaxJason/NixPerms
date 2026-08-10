package de.astranox.nixperms.core.group;

import de.astranox.nixperms.api.group.IPermissionGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NixGroupChainCache {

    private static final int MAX_DEPTH = 32;
    private final ConcurrentHashMap<String, List<IPermissionGroup>> cache = new ConcurrentHashMap<>();

    public List<IPermissionGroup> get(IPermissionGroup root) {
        return cache.computeIfAbsent(root.name(), ignored -> resolve(root));
    }

    public void invalidate(String groupName) {
        cache.entrySet().removeIf(entry -> contains(entry.getValue(), groupName));
    }

    public void clear() {
        cache.clear();
    }

    private List<IPermissionGroup> resolve(IPermissionGroup root) {
        List<IPermissionGroup> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        IPermissionGroup current = root;
        while (current != null && chain.size() < MAX_DEPTH && visited.add(current.name())) {
            chain.add(current);
            current = current.parent().orElse(null);
        }
        Collections.reverse(chain);
        return List.copyOf(chain);
    }

    private boolean contains(List<IPermissionGroup> chain, String groupName) {
        for (IPermissionGroup group : chain) {
            if (group.name().equals(groupName)) return true;
        }
        return false;
    }
}
