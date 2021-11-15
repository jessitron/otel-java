package io.honeycomb.otel.fibonacci;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.Context;

import io.opentelemetry.context.propagation.TextMapSetter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

public class Tracing {

  public static OpenTelemetry setUpTracing() {
    System.out.println("SET UP TRACING, YO");

    SpanExporter exporter = OtlpGrpcSpanExporter.builder()
        .addHeader("X-Honeycomb-Team", System.getenv("HONEYCOMB_API_KEY"))
        .addHeader("X-Honeycomb-Dataset", System.getenv("HONEYCOMB_DATASET"))
        .setEndpoint("https://api.honeycomb.io:443").build();

    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(exporter).build()).build();

    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).buildAndRegisterGlobal();

    Span boo = openTelemetry.getTracer("test-span").spanBuilder("test span").setAttribute("friend", "jessitron")
        .setNoParent().startSpan();
    boo.end();

    return openTelemetry;
  }
}
