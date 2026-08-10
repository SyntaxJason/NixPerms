package de.astranox.nixperms.core.permission;

import de.astranox.nixperms.api.permission.PermissionDecision;

import java.util.Arrays;
import java.util.Map;

/**
 * Immutable character trie used by permission snapshots.
 *
 * <p>The common lookup path does not create wildcard strings or substrings. A wildcard rule is
 * stored at the node directly before its trailing {@code *}, while the complete permission remains
 * available as an exact rule as well.</p>
 */
final class PermissionNodeIndex {

    private static final byte UNSET = 0;
    private static final byte ALLOW = 1;
    private static final byte DENY = 2;

    private final Node root;

    PermissionNodeIndex(Map<String, PermissionDecision> values) {
        MutableNode mutableRoot = new MutableNode();
        values.forEach((node, decision) -> insert(mutableRoot, node, decision));
        this.root = freeze(mutableRoot);
    }

    PermissionDecision decision(String node) {
        Node current = root;
        byte wildcard = current.wildcard;

        for (int index = 0; index < node.length(); index++) {
            current = current.child(node.charAt(index));
            if (current == null) return decode(wildcard);
            if (current.wildcard != UNSET) wildcard = current.wildcard;
        }

        if (current.exact != UNSET) return decode(current.exact);
        return decode(wildcard);
    }

    private static void insert(MutableNode root, String node, PermissionDecision decision) {
        byte encoded = encode(decision);
        MutableNode current = root;

        for (int index = 0; index < node.length(); index++) {
            current = current.child(node.charAt(index));
        }
        current.exact = encoded;

        if (!node.endsWith("*")) return;
        MutableNode wildcardOwner = root;
        for (int index = 0; index < node.length() - 1; index++) {
            wildcardOwner = wildcardOwner.child(node.charAt(index));
        }
        wildcardOwner.wildcard = encoded;
    }

    private static Node freeze(MutableNode mutable) {
        int size = mutable.size;
        char[] labels = Arrays.copyOf(mutable.labels, size);
        Node[] children = new Node[size];

        sort(labels, mutable.children, size);
        for (int index = 0; index < size; index++) {
            children[index] = freeze(mutable.children[index]);
        }
        return new Node(labels, children, mutable.exact, mutable.wildcard);
    }

    private static void sort(char[] labels, MutableNode[] children, int size) {
        for (int index = 1; index < size; index++) {
            char label = labels[index];
            MutableNode child = children[index];
            int position = index - 1;
            while (position >= 0 && labels[position] > label) {
                labels[position + 1] = labels[position];
                children[position + 1] = children[position];
                position--;
            }
            labels[position + 1] = label;
            children[position + 1] = child;
        }
    }

    private static byte encode(PermissionDecision decision) {
        return decision == PermissionDecision.ALLOW ? ALLOW : DENY;
    }

    private static PermissionDecision decode(byte decision) {
        return switch (decision) {
            case ALLOW -> PermissionDecision.ALLOW;
            case DENY -> PermissionDecision.DENY;
            default -> PermissionDecision.UNSET;
        };
    }

    private static final class MutableNode {
        private char[] labels = new char[4];
        private MutableNode[] children = new MutableNode[4];
        private int size;
        private byte exact;
        private byte wildcard;

        private MutableNode child(char label) {
            for (int index = 0; index < size; index++) {
                if (labels[index] == label) return children[index];
            }
            ensureCapacity();
            MutableNode child = new MutableNode();
            labels[size] = label;
            children[size] = child;
            size++;
            return child;
        }

        private void ensureCapacity() {
            if (size < labels.length) return;
            int capacity = labels.length << 1;
            labels = Arrays.copyOf(labels, capacity);
            children = Arrays.copyOf(children, capacity);
        }
    }

    private record Node(char[] labels, Node[] children, byte exact, byte wildcard) {
        private Node child(char label) {
            int low = 0;
            int high = labels.length - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                char current = labels[middle];
                if (current < label) {
                    low = middle + 1;
                    continue;
                }
                if (current > label) {
                    high = middle - 1;
                    continue;
                }
                return children[middle];
            }
            return null;
        }
    }
}
