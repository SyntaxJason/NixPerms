package de.astranox.nixperms.file;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class NixPrinter implements NixVisitor {

    public static final class Options {
        public final String indent;
        public final boolean sortKeys;
        public final boolean trailingCommas;

        public Options(String indent, boolean sortKeys, boolean trailingCommas) {
            this.indent = indent;
            this.sortKeys = sortKeys;
            this.trailingCommas = trailingCommas;
        }

        public static Options defaults() {
            return new Options("  ", true, true);
        }
    }

    private final Options options;
    private Writer writer;
    private int level;

    public NixPrinter() {
        this(Options.defaults());
    }

    public NixPrinter(Options options) {
        this.options = options;
    }

    public void writeDocument(NixObject root, OutputStream outputStream) throws IOException {
        try (Writer writerWrapper = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            this.writer = writerWrapper;
            this.level = 0;
            writeStatements(root);
            this.writer = null;
        }
    }

    private void writeStatements(NixObject object) throws IOException {
        var entries = order(object.asMap());
        for (var entry : entries) {
            var key = entry.getKey();
            var value = entry.getValue();
            if (value instanceof NixObject child) {
                pad();
                writer.write(key + " {\n");
                level += 1;
                writeStatements(child);
                level -= 1;
                pad();
                writer.write("}\n");
                continue;
            }

            pad();
            writer.write(key + " = ");
            writeInline(value);
            writer.write("\n");
        }
    }

    private void writeInline(NixNode value) throws IOException {
        value.accept(this);
    }

    @Override
    public void visitString(NixString value) throws IOException {
        writeString(value.value);
    }

    @Override
    public void visitBool(NixBool value) throws IOException {
        writer.write(value.value ? "true" : "false");
    }

    @Override
    public void visitNumber(NixNumber value) throws IOException {
        writer.write(value.value.toPlainString());
    }

    @Override
    public void visitArray(NixArray value) throws IOException {
        var list = value.asList();
        int size = list.size();
        if (size == 0) {
            writer.write("[]");
            return;
        }

        writer.write("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) writer.write(", ");
            writeInline(list.get(i));
        }
        writer.write(options.trailingCommas ? ",]" : "]");
    }

    @Override
    public void visitObject(NixObject value) throws IOException {
        List<Map.Entry<String, NixNode>> mapEntries = order(value.asMap());
        if (mapEntries.isEmpty()) {
            writer.write("{}");
            return;
        }

        writer.write("{ ");
        int index = 0;
        for (var entry : mapEntries) {
            if (index++ > 0) writer.write(", ");
            writeKey(entry.getKey());
            writer.write(": ");
            writeInline(entry.getValue());
        }
        writer.write(options.trailingCommas ? ", }" : " }");
    }

    private void writeKey(String key) throws IOException {
        if (key.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
            writer.write(key);
            return;
        }
        writeString(key);
    }

    private void writeString(String string) throws IOException {
        writer.write('"');
        for (int i = 0; i < string.length(); i++) {
            char character = string.charAt(i);
            switch (character) {
                case '\\' -> writer.write("\\\\");
                case '"' -> writer.write("\\\"");
                case '\n' -> writer.write("\\n");
                case '\r' -> writer.write("\\r");
                case '\t' -> writer.write("\\t");
                default -> writer.write(character);
            }
        }
        writer.write('"');
    }

    private void pad() throws IOException {
        for (int i = 0; i < level; i++) {
            writer.write(options.indent);
        }
    }

    private List<Map.Entry<String, NixNode>> order(Map<String, NixNode> map) {
        if (!options.sortKeys) {
            return new ArrayList<>(map.entrySet());
        }

        var keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        var output = new ArrayList<Map.Entry<String, NixNode>>(keys.size());
        for (var key : keys) {
            output.add(Map.entry(key, map.get(key)));
        }
        return output;
    }

    public static void writeUtf8File(java.nio.file.Path path, NixObject root, Options options) throws IOException {
        java.nio.file.Files.createDirectories(path.getParent());
        try (OutputStream outputStream = java.nio.file.Files.newOutputStream(path)) {
            new NixPrinter(options).writeDocument(root, outputStream);
        }
    }

    public static void writeUtf8File(java.nio.file.Path path, NixObject root) throws IOException {
        writeUtf8File(path, root, Options.defaults());
    }
}
