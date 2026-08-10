package de.astranox.nixperms.api.annotation.http;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Endpoint {
    String value();
}
