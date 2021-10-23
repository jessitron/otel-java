package io.honeycomb.otel.fibonacci;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.Context;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

class IgnoreTracingOnForwardedRequest implements TextMapPropagator {

  public static ContextPropagators contextPropagators(ObjectProvider<List<TextMapPropagator>> propagators) {
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
  private final TextMapPropagator base;

  public IgnoreTracingOnForwardedRequest(TextMapPropagator base) {
    this.base = base;
  }
  public Collection<String> fields() {
    Collection<String> fields = base.fields();
    fields.add("user-agent");
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
    String ua = getter.get(carrier, "user-agent");
    if (ua == null || ua.startsWith("Java")) {
      System.out.println("THIS IS FROM ME");
      // looks like an internal call. Behave normally
      return base.extract(context, carrier, getter);
    } else {
      // initial request! Ignore tracing on headers.
      System.out.println("THIS IS FROM OUTSIDE");
      return context;
    }
  }
}
