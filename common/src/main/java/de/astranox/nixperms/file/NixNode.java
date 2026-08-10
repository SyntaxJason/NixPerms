package de.astranox.nixperms.file;

import java.math.BigDecimal;
import java.util.Objects;

public interface NixNode {
    default boolean isObject() { return this instanceof NixObject; }
    default boolean isArray() { return this instanceof NixArray; }
    default boolean isBool() { return this instanceof NixBool; }
    default boolean isNumber() { return this instanceof NixNumber; }
    default boolean isString() { return this instanceof NixString; }

    default NixObject asObject() { return (NixObject) this; }
    default NixArray asArray() { return (NixArray) this; }
    default boolean asBool() { return ((NixBool) this).value; }
    default BigDecimal asBig() { return ((NixNumber) this).value; }
    default long asLong() { return ((NixNumber) this).value.longValue(); }
    default int asInt() { return ((NixNumber) this).value.intValue(); }
    default double asDouble() { return ((NixNumber) this).value.doubleValue(); }
    default String asString() { return ((NixString) this).value; }

    void accept(NixVisitor visitor) throws java.io.IOException;

    static NixNode of(String string) { return new NixString(string); }
    static NixNode of(boolean bool) { return new NixBool(bool); }
    static NixNode of(long number) { return new NixNumber(new BigDecimal(number)); }
    static NixNode of(double number) { return new NixNumber(BigDecimal.valueOf(number)); }
}

final class NixString implements NixNode {
    public final String value;

    public NixString(String value) {
        this.value = value;
    }

    @Override
    public void accept(NixVisitor visitor) throws java.io.IOException {
        visitor.visitString(this);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NixString otherString)) return false;
        return Objects.equals(value, otherString.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}

final class NixBool implements NixNode {
    public final boolean value;

    public NixBool(boolean value) {
        this.value = value;
    }

    @Override
    public void accept(NixVisitor visitor) throws java.io.IOException {
        visitor.visitBool(this);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NixBool otherBool)) return false;
        return value == otherBool.value;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }
}

final class NixNumber implements NixNode {
    public final BigDecimal value;

    public NixNumber(BigDecimal value) {
        this.value = value;
    }

    @Override
    public void accept(NixVisitor visitor) throws java.io.IOException {
        visitor.visitNumber(this);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NixNumber otherNumber)) return false;
        return Objects.equals(value, otherNumber.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
