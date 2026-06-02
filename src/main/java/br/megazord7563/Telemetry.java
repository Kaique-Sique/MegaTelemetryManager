package br.megazord7563;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;


@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Telemetry {
    Priority priority() default Priority.MEDIUM;
    String basePath() default "";  // ← adiciona isso
    String key() default "";
}