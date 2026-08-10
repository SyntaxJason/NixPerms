package de.astranox.nixperms.api.annotation.command;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Usage {
    String value();
}
