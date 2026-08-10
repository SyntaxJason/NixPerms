package de.astranox.nixperms.api.annotation.message;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MessagePrefix {
    String value();
    boolean global() default false;
}
