package de.astranox.nixperms.api.annotation.message;

import de.astranox.nixperms.api.platform.Platform;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Message {
    String value();
    boolean prefix() default true;
    Platform[] platforms() default {};
}
