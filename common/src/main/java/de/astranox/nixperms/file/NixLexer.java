package de.astranox.nixperms.file;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class NixLexer {

    private final Reader reader;
    private int character = -2;
    private int line = 1;
    private int column = 0;

    public NixLexer(InputStream inputStream) {
        this.reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    }

    private int next() throws IOException {
        int currentChar = reader.read();
        column++;
        if (currentChar == '\n') {
            line++;
            column = 0;
        }
        return currentChar;
    }

    private int peek() throws IOException {
        if (character == -2) {
            character = next();
        }
        return character;
    }

    private int take() throws IOException {
        int peekedChar = peek();
        character = -2;
        return peekedChar;
    }

    Tok nextTok() throws IOException {
        while (true) {
            int peekedChar = peek();
            if (peekedChar == -1) {
                return new Tok(T.EOF, "");
            }

            if (Character.isWhitespace(peekedChar)) {
                take();
                continue;
            }

            if (peekedChar == '#') {
                while (peekedChar != -1 && peekedChar != '\n') {
                    take();
                    peekedChar = peek();
                }
                continue;
            }

            break;
        }

        int currentChar = take();
        return switch (currentChar) {
            case '{' -> new Tok(T.LBRACE, "{");
            case '}' -> new Tok(T.RBRACE, "}");
            case '[' -> new Tok(T.LBRACK, "[");
            case ']' -> new Tok(T.RBRACK, "]");
            case ',' -> new Tok(T.COMMA, ",");
            case ':' -> new Tok(T.COLON, ":");
            case '=' -> new Tok(T.EQ, "=");
            case '"', '\'' -> string((char) currentChar);
            default -> {
                if (isIdentStart(currentChar)) {
                    yield ident((char) currentChar);
                }
                if (isNumStart(currentChar)) {
                    yield number((char) currentChar);
                }
                throw new IOException("Unexpected char: " + (char) currentChar + " at " + line + ":" + column);
            }
        };
    }

    private boolean isIdentStart(int character) {
        return character == '_' || character == '.' || character == '-' || Character.isLetter(character);
    }

    private boolean isIdentPart(int character) {
        return isIdentStart(character) || Character.isDigit(character);
    }

    private Tok ident(char first) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(first);
        while (true) {
            int peekedChar = peek();
            if (peekedChar == -1 || !isIdentPart(peekedChar)) {
                break;
            }
            stringBuilder.append((char) take());
        }

        String result = stringBuilder.toString();
        if (result.equals("true")) {
            return new Tok(T.TRUE, result);
        }
        if (result.equals("false")) {
            return new Tok(T.FALSE, result);
        }
        return new Tok(T.IDENT, result);
    }

    private boolean isNumStart(int character) {
        return character == '-' || Character.isDigit(character);
    }

    private Tok number(char first) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(first);
        boolean hasDot = false;
        while (true) {
            int peekedChar = peek();
            if (peekedChar == '.' && !hasDot) {
                hasDot = true;
                stringBuilder.append((char) take());
                continue;
            }
            if (peekedChar == 'e' || peekedChar == 'E' || peekedChar == '+' || peekedChar == '-') {
                stringBuilder.append((char) take());
                continue;
            }
            if (peekedChar == -1 || (!Character.isDigit(peekedChar) && peekedChar != '.')) {
                break;
            }
            stringBuilder.append((char) take());
        }
        return new Tok(T.NUMBER, stringBuilder.toString());
    }

    private Tok string(char quote) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        while (true) {
            int currentChar = take();
            if (currentChar == -1) {
                throw new EOFException("Unterminated string");
            }
            if (currentChar == quote) {
                break;
            }
            if (currentChar == '\\') {
                int escapeChar = take();
                switch (escapeChar) {
                    case 'n' -> stringBuilder.append('\n');
                    case 'r' -> stringBuilder.append('\r');
                    case 't' -> stringBuilder.append('\t');
                    case '\\' -> stringBuilder.append('\\');
                    case '"' -> stringBuilder.append('"');
                    case '\'' -> stringBuilder.append('\'');
                    default -> stringBuilder.append((char) escapeChar);
                }
                continue;
            }
            stringBuilder.append((char) currentChar);
        }
        return new Tok(T.STRING, stringBuilder.toString());
    }

    enum T {
        LBRACE, RBRACE, LBRACK, RBRACK, COMMA, COLON, EQ,
        IDENT, STRING, NUMBER, TRUE, FALSE, EOF
    }

    record Tok(T type, String string) {}
}
