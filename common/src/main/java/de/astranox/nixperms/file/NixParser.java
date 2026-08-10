package de.astranox.nixperms.file;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

public final class NixParser {
    private final NixLexer lexer;
    private NixLexer.Tok token;

    public NixParser(InputStream inputStream) {
        this.lexer = new NixLexer(inputStream);
    }

    public static NixObject parseUtf8File(java.nio.file.Path path) throws IOException {
        try (InputStream inputStream = java.nio.file.Files.newInputStream(path)) {
            return new NixParser(inputStream).parse();
        }
    }

    private NixLexer.Tok take() throws IOException {
        NixLexer.Tok current = token;
        token = lexer.nextTok();
        return current;
    }

    private void expect(NixLexer.T kind) throws IOException {
        if (token.type() != kind) {
            throw new IOException("Expected " + kind + " but got " + token.type());
        }
    }

    public NixObject parse() throws IOException {
        token = lexer.nextTok();
        NixObject root = new NixObject();
        while (token.type() != NixLexer.T.EOF) {
            statement(root);
        }
        return root;
    }

    private void statement(NixObject scope) throws IOException {
        if (token.type() != NixLexer.T.IDENT) {
            throw new IOException("Expected identifier at statement start");
        }

        String name = take().string();
        if (token.type() == NixLexer.T.EQ) {
            take();
            NixNode value = value();
            scope.put(name, value);
            return;
        }

        if (token.type() == NixLexer.T.LBRACE) {
            take();
            NixObject child = new NixObject();
            while (token.type() != NixLexer.T.RBRACE) {
                statement(child);
            }
            take();
            scope.put(name, child);
            return;
        }

        throw new IOException("Expected '=' or '{' after identifier");
    }

    private NixNode value() throws IOException {
        return switch (token.type()) {
            case STRING -> {
                String string = take().string();
                yield new NixString(string);
            }
            case TRUE -> {
                take();
                yield new NixBool(true);
            }
            case FALSE -> {
                take();
                yield new NixBool(false);
            }
            case NUMBER -> {
                String number = take().string();
                yield new NixNumber(new BigDecimal(number));
            }
            case LBRACK -> array();
            case LBRACE -> map();
            case IDENT -> {
                String string = take().string();
                yield new NixString(string);
            }
            default -> throw new IOException("Unexpected token in value: " + token.type());
        };
    }

    private NixNode array() throws IOException {
        take(); // [
        NixArray array = new NixArray();
        if (token.type() == NixLexer.T.RBRACK) {
            take();
            return array;
        }

        while (true) {
            array.add(value());
            if (token.type() == NixLexer.T.COMMA) {
                take();
                if (token.type() == NixLexer.T.RBRACK) {
                    take();
                    break;
                }
                continue;
            }
            expect(NixLexer.T.RBRACK);
            take();
            break;
        }
        return array;
    }

    private NixNode map() throws IOException {
        take();
        NixObject object = new NixObject();
        if (token.type() == NixLexer.T.RBRACE) {
            take();
            return object;
        }

        while (true) {
            String key;
            if (token.type() == NixLexer.T.STRING || token.type() == NixLexer.T.IDENT) {
                key = take().string();
            } else {
                throw new IOException("Expected map key");
            }

            expect(NixLexer.T.COLON);
            take();
            object.put(key, value());
            if (token.type() == NixLexer.T.COMMA) {
                take();
                if (token.type() == NixLexer.T.RBRACE) {
                    take();
                    break;
                }
                continue;
            }
            expect(NixLexer.T.RBRACE);
            take();
            break;
        }
        return object;
    }
}
