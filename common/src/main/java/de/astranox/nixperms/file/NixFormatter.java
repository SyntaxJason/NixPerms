package de.astranox.nixperms.file;

import java.io.IOException;
import java.nio.file.Path;

public final class NixFormatter {
    private NixFormatter() {}

    public static void reformatFile(Path path, NixPrinter.Options options) throws IOException {
        var root = NixParser.parseUtf8File(path);
        NixPrinter.writeUtf8File(path, root, options);
    }

    public static void reformatFile(Path path) throws IOException {
        reformatFile(path, NixPrinter.Options.defaults());
    }
}
