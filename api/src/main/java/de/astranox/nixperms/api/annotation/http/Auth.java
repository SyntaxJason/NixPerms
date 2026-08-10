package de.astranox.nixperms.api.annotation.http;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auth {
    AuthStrategy strategy() default AuthStrategy.SESSION_TOKEN;
}
