package de.astranox.nixperms.api.message;

public interface ReplyBuilder {
    ReplyBuilder with(String placeholder, Object value);
    void send();
}
