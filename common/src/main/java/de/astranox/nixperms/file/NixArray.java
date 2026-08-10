package de.astranox.nixperms.file;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NixArray implements NixNode {
    private final ArrayList<NixNode> list = new ArrayList<>();

    public NixArray add(NixNode value) {
        list.add(value);
        return this;
    }

    public List<NixNode> asList() {
        return list;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NixArray otherArray)) {
            return false;
        }
        return Objects.equals(this.list, otherArray.list);
    }

    @Override
    public int hashCode() {
        return list.hashCode();
    }

    @Override
    public void accept(NixVisitor visitor) throws java.io.IOException {
        visitor.visitArray(this);
    }
}
