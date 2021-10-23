package io.honeycomb.otel.fibonacci;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
		System.err.println("JESS WAS HERE");
    List<TextMapPropagator> mapPropagators = propagators.getIfAvailable(Collections::emptyList);
    if (mapPropagators.isEmpty()) {
			System.err.println("NOOP FOR YOU");
      return ContextPropagators.noop();
    }
		System.err.println("THERE ARE " + mapPropagators.size() + " propagators");
    return ContextPropagators.create(TextMapPropagator.composite(mapPropagators));
  }

}
