package com.example.ai01.monitoring;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

@Aspect
@Component
public class OpenTelemetryTracingAspect {
    private static final String DEFAULT_INSTRUMENTATION_NAME = "com.example.ai01";
    private final Tracer tracer;

    public OpenTelemetryTracingAspect(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(DEFAULT_INSTRUMENTATION_NAME);
    }

    @Around("@annotation(com.example.ai01.monitoring.TraceOperation) " +
            "|| @within(com.example.ai01.monitoring.TraceOperation)")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {

        TraceMetadata traceMetadata = resolveTraceMetadata(joinPoint);

        /*
         * اگر newSpan=false باشد،
         * Child Span جدید ایجاد نمی‌کنیم.
         */
        if (!traceMetadata.newSpan()) {
            return executeAsEvent(joinPoint, traceMetadata);
        }
        return executeAsSpan(joinPoint, traceMetadata);
    }


    // =========================================================
    // New Span
    // =========================================================

    private Object executeAsSpan(ProceedingJoinPoint joinPoint, TraceMetadata metadata) throws Throwable {


        /*
         * اگر Span فعلی وجود داشته باشد
         * به صورت Parent استفاده می‌شود.
         */
        Span span = tracer.spanBuilder(metadata.spanName())
                .setParent(Context.current()).startSpan();

        setCommonAttributes(span, joinPoint, metadata);

        /*
         * Context شامل Span جدید است.
         */
        Context spanContext = Context.current().with(span);

        try (Scope ignored = spanContext.makeCurrent()) {

            Object result = joinPoint.proceed();

            /*
             * بسیار مهم:
             *
             * اگر متد Async باشد نباید همینجا span.end()
             * شود.
             *
             * باید تا پایان Future باز بماند.
             */
            if (result instanceof CompletionStage<?> stage) {
                return traceAsyncResult(stage, span, spanContext);
            }


            span.setStatus(
                    StatusCode.OK
            );


            span.end();

            return result;

        } catch (Throwable throwable) {

            recordError(
                    span,
                    throwable
            );

            span.end();

            throw throwable;
        }
    }


    // =========================================================
    // Async result
    // =========================================================

    private CompletionStage<?> traceAsyncResult(
            CompletionStage<?> stage,
            Span span,
            Context spanContext
    ) {

        return stage.whenComplete(
                (result, throwable) -> {

                    try (Scope ignored =
                                 spanContext.makeCurrent()) {

                        if (throwable != null) {

                            Throwable error =
                                    unwrap(
                                            throwable
                                    );


                            recordError(
                                    span,
                                    error
                            );

                        } else {

                            span.setStatus(
                                    StatusCode.OK
                            );
                        }

                    } finally {

                        span.end();
                    }
                }
        );
    }


    // =========================================================
    // Event mode
    // =========================================================

    private Object executeAsEvent(
            ProceedingJoinPoint joinPoint,
            TraceMetadata metadata
    ) throws Throwable {

        Span currentSpan =
                Span.current();


        long start =
                System.nanoTime();


        currentSpan.addEvent(
                metadata.spanName()
                        + ".started",
                Attributes.builder()
                        .put(
                                AttributeKey.stringKey(
                                        "app.service.name"
                                ),
                                metadata.serviceName()
                        )
                        .build()
        );


        try {

            Object result =
                    joinPoint.proceed();


            /*
             * Async Event
             */
            if (result instanceof CompletionStage<?> stage) {

                return stage.whenComplete(
                        (value, throwable) -> {

                            double durationMs =
                                    elapsedMilliseconds(
                                            start
                                    );


                            if (throwable != null) {

                                currentSpan.addEvent(
                                        metadata.spanName()
                                                + ".failed",
                                        Attributes.builder()
                                                .put(
                                                        AttributeKey.doubleKey(
                                                                "app.operation.duration_ms"
                                                        ),
                                                        durationMs
                                                )
                                                .build()
                                );

                            } else {

                                currentSpan.addEvent(
                                        metadata.spanName()
                                                + ".completed",
                                        Attributes.builder()
                                                .put(
                                                        AttributeKey.doubleKey(
                                                                "app.operation.duration_ms"
                                                        ),
                                                        durationMs
                                                )
                                                .build()
                                );
                            }
                        }
                );
            }


            currentSpan.addEvent(
                    metadata.spanName()
                            + ".completed",
                    Attributes.builder()
                            .put(
                                    AttributeKey.doubleKey(
                                            "app.operation.duration_ms"
                                    ),
                                    elapsedMilliseconds(
                                            start
                                    )
                            )
                            .build()
            );


            return result;

        } catch (Throwable throwable) {

            currentSpan.addEvent(
                    metadata.spanName()
                            + ".failed",
                    Attributes.builder()
                            .put(
                                    AttributeKey.doubleKey(
                                            "app.operation.duration_ms"
                                    ),
                                    elapsedMilliseconds(
                                            start
                                    )
                            )
                            .put(
                                    AttributeKey.stringKey(
                                            "exception.type"
                                    ),
                                    throwable
                                            .getClass()
                                            .getName()
                            )
                            .build()
            );


            throw throwable;
        }
    }


    // =========================================================
    // Common attributes
    // =========================================================

    private void setCommonAttributes(
            Span span,
            ProceedingJoinPoint joinPoint,
            TraceMetadata metadata
    ) {

        MethodSignature signature =
                (MethodSignature)
                        joinPoint.getSignature();


        span.setAttribute(
                "app.service.name",
                metadata.serviceName()
        );


        span.setAttribute(
                "code.class",
                joinPoint
                        .getTarget()
                        .getClass()
                        .getSimpleName()
        );


        span.setAttribute(
                "code.method",
                signature.getName()
        );


        span.setAttribute(
                "app.operation",
                metadata.spanName()
        );
    }


    // =========================================================
    // Error handling
    // =========================================================

    private void recordError(
            Span span,
            Throwable throwable
    ) {

        span.recordException(
                throwable
        );


        span.setStatus(
                StatusCode.ERROR,
                throwable.getMessage() == null
                        ? throwable.getClass().getSimpleName()
                        : throwable.getMessage()
        );
    }


    private Throwable unwrap(
            Throwable throwable
    ) {

        if (throwable instanceof CompletionException
                && throwable.getCause() != null) {

            return throwable.getCause();
        }


        return throwable;
    }


    // =========================================================
    // Resolve Annotation
    // =========================================================

    private TraceMetadata resolveTraceMetadata(
            ProceedingJoinPoint joinPoint
    ) {

        MethodSignature signature =
                (MethodSignature)
                        joinPoint.getSignature();


        Method interfaceMethod =
                signature.getMethod();


        Method targetMethod =
                AopUtils.getMostSpecificMethod(
                        interfaceMethod,
                        joinPoint
                                .getTarget()
                                .getClass()
                );


        /*
         * ابتدا Annotation روی Method بررسی می‌شود.
         */
        TraceOperation methodAnnotation =
                AnnotatedElementUtils
                        .findMergedAnnotation(
                                targetMethod,
                                TraceOperation.class
                        );


        /*
         * سپس Annotation روی Class.
         */
        TraceOperation classAnnotation =
                AnnotatedElementUtils
                        .findMergedAnnotation(
                                joinPoint
                                        .getTarget()
                                        .getClass(),
                                TraceOperation.class
                        );


        String defaultServiceName =
                joinPoint
                        .getTarget()
                        .getClass()
                        .getSimpleName();


        String defaultSpanName =
                defaultServiceName
                        + "."
                        + signature.getName();


        String serviceName =
                resolveServiceName(
                        methodAnnotation,
                        classAnnotation,
                        defaultServiceName
                );


        String spanName =
                resolveSpanName(
                        methodAnnotation,
                        classAnnotation,
                        defaultSpanName
                );


        boolean newSpan =
                resolveNewSpan(
                        methodAnnotation,
                        classAnnotation
                );


        return new TraceMetadata(
                serviceName,
                spanName,
                newSpan
        );
    }


    private String resolveServiceName(
            TraceOperation methodAnnotation,
            TraceOperation classAnnotation,
            String defaultValue
    ) {

        if (methodAnnotation != null
                && !methodAnnotation
                .serviceName()
                .isBlank()) {

            return methodAnnotation
                    .serviceName();
        }


        if (classAnnotation != null
                && !classAnnotation
                .serviceName()
                .isBlank()) {

            return classAnnotation
                    .serviceName();
        }


        return defaultValue;
    }


    private String resolveSpanName(
            TraceOperation methodAnnotation,
            TraceOperation classAnnotation,
            String defaultValue
    ) {

        if (methodAnnotation != null
                && !methodAnnotation
                .spanName()
                .isBlank()) {

            return methodAnnotation
                    .spanName();
        }


        /*
         * اگر Annotation فقط روی Class باشد،
         * اسم متد را به عنوان Span نگه می‌داریم.
         */
        return defaultValue;
    }


    private boolean resolveNewSpan(
            TraceOperation methodAnnotation,
            TraceOperation classAnnotation
    ) {

        if (methodAnnotation != null) {

            return methodAnnotation
                    .newSpan();
        }


        if (classAnnotation != null) {

            return classAnnotation
                    .newSpan();
        }


        return true;
    }


    private double elapsedMilliseconds(
            long start
    ) {

        return (System.nanoTime() - start)
                / 1_000_000.0;
    }


    // =========================================================
    // Internal metadata
    // =========================================================

    private record TraceMetadata(
            String serviceName,
            String spanName,
            boolean newSpan
    ) {
    }
}