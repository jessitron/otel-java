package io.honeycomb.otel.fibonacci;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

@SpringBootApplication
public class FibonacciApplication {

	public static void main(String[] args) {

		SpringApplication.run(FibonacciApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.build();
	}

	@Bean
  ContextPropagators contextPropagators(ObjectProvider<List<TextMapPropagator>> propagators) {
		return IgnoreTracingOnForwardedRequest.contextPropagators(propagators);
  }

}
