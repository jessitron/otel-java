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
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.Context;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

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
		TextMapPropagator base = TextMapPropagator.noop();
    List<TextMapPropagator> mapPropagators = propagators.getIfAvailable(Collections::emptyList);
    if (mapPropagators.isEmpty()) {
			System.err.println("NOOP FOR YOU");
    } else {
		System.err.println("THERE ARE " + mapPropagators.size() + " propagators");
      base = TextMapPropagator.composite(mapPropagators);
		}
		return ContextPropagators.create(new IgnoreTracingOnForwardedRequest(base));
  }

	class IgnoreTracingOnForwardedRequest implements TextMapPropagator {
		private final TextMapPropagator base;

		public IgnoreTracingOnForwardedRequest(TextMapPropagator base) {
			this.base = base;
		}
    public Collection<String> fields() {
			Collection<String> fields = base.fields();
			fields.add("x-forwarded-for"); // if that just gave us an immutable list, this won't work
			return fields;
    }

		public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
			base.inject(context, carrier, setter);
		}

		/**
		* Extracts data from upstream. For example, from incoming http headers. The returned Context
		* should contain the extracted data, if any, merged with the data from the passed-in Context.
		*
		*/
		public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
			String ff = getter.get(carrier, "x-forwarded-for");
			if (ff == null) {
				System.out.println("NOT FORWARDED");
				// no forwarding headers. Behave normally
		    return base.extract(context, carrier, getter);
			} else {
				// forwarded! Ignore tracing on headers.
				System.out.println("FORWARDED");
				return context;
			}
		}
	}

}
