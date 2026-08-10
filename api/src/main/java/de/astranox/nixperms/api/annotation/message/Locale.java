package de.astranox.nixperms.api.annotation.message;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Locale {
    String value();
}
