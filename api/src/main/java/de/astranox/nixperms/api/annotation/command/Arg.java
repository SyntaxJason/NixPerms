package de.astranox.nixperms.api.annotation.command;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Arg {
    String value();
    String def() default "";
    boolean required() default true;
    ArgType type() default ArgType.AUTO;
}
