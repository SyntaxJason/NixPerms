package de.astranox.nixperms.config.lib;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NixConfiguration {
    String file();

    boolean autoReload() default true;

    long debounceMs() default 300;
}
