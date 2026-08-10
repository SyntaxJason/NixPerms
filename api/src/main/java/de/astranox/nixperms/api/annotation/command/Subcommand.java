package de.astranox.nixperms.api.annotation.command;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Subcommand {
    String label();
    String permission() default "nixperms.admin";
    String[] aliases() default {};
}
