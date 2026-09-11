package com.example.ai01.monitoring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceOperation {
    String spanName() default "";
    String serviceName() default "";
    boolean newSpan() default true;
    String spanKind() default "CHAIN";
}