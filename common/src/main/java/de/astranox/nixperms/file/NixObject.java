package de.astranox.nixperms.file;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

public final class NixObject implements NixNode {

    private final LinkedHashMap<String, NixNode> map = new LinkedHashMap<>();

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public NixNode get(String key) {
        return map.get(key);
    }

    public NixObject put(String key, NixNode value) {
        map.put(key, value);
        return this;
    }

    public Set<String> keys() {
        return map.keySet();
    }

    public Map<String, NixNode> asMap() {
        return map;
    }

    @Override
    public String toString() {
        return map.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NixObject otherObject)) {
            return false;
        }
        return Objects.equals(this.map, otherObject.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public void accept(NixVisitor visitor) throws java.io.IOException {
        visitor.visitObject(this);
    }
}
