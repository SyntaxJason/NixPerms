package de.astranox.nixperms.file;

import java.io.IOException;

public interface NixVisitor {
    void visitString(NixString value) throws IOException;
    void visitBool(NixBool value) throws IOException;
    void visitNumber(NixNumber value) throws IOException;
    void visitArray(NixArray value) throws IOException;
    void visitObject(NixObject value) throws IOException;
}
