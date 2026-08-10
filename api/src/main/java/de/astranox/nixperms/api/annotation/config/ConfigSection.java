package de.astranox.nixperms.api.annotation.config;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigSection {
    String value();
}
