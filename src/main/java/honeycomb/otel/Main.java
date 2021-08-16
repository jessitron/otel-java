package honeycomb.otel;

import io.github.cdimascio.dotenv.Dotenv;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.*;

public class Main {

    private static OpenTelemetrySdk openTelemetry;
    private static Tracer tracer;
  
    private final TextMapGetter<HttpExchange> getter =
        new TextMapGetter<HttpExchange>() {
            @Override
            public String get(HttpExchange carrier, String key) {
                if (carrier.getRequestHeaders().containsKey(key)) {
                return carrier.getRequestHeaders().get(key).get(0);
                }
                return null;
            }
        
            @Override
            public Iterable<String> keys(HttpExchange carrier) {
                return carrier.getRequestHeaders().keySet();
            } 
        };

    private static final TextMapSetter<HttpURLConnection> setter = URLConnection::setRequestProperty;

    private class HelloHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Span span = tracer
                .spanBuilder("GET /")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

            try {
                span.setAttribute("component", "http");
                span.setAttribute("http.method", "GET");
                span.setAttribute("http.scheme", "http");
                span.setAttribute("http.host", "localhost:" + port);
                span.setAttribute("http.target", "/");

                // Process the request
                String response = "hello from java\n this is the end of the journey... for today";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes(Charset.defaultCharset()));
                os.close();
                System.out.println("Served Client: " + exchange.getRemoteAddress());
            } finally {
                span.end();
            }            
        }
    }
  
    private class FibonacciHandler implements HttpHandler {

        private boolean _extractContext = false;

        public FibonacciHandler(boolean extractContext) {
            _extractContext = extractContext;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Context context = Context.current();
            if (_extractContext) {
                context = openTelemetry.getPropagators().getTextMapPropagator().extract(context, exchange, getter);
            }

            try (Scope scope = context.makeCurrent()) {
                Span span = tracer
                    .spanBuilder("GET /fib")
                    .setParent(context)
                    .setSpanKind(SpanKind.SERVER)
                    .startSpan();
                span.setAttribute("component", "http");
                span.setAttribute("http.method", "GET");
                span.setAttribute("http.scheme", "http");
                span.setAttribute("http.host", "localhost:" + port);
                span.setAttribute("http.target", "/fib");

                System.out.println(span.getSpanContext().getTraceId());

                try {
                    Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getQuery());
                    Integer initialValue = Integer.valueOf(queryParams.get("i"));
                    span.setAttribute("parameter", initialValue);
                    System.out.println("iv: " + initialValue);
                    Integer returnValue = 0;
                    if ((initialValue == 0) || (initialValue == 1)) {
                        returnValue = 0;
                    } else if (initialValue == 2) {
                        returnValue = 1;
                    } else {
                        Integer minusOneResult = makeRequest(String.format("http://localhost:3000/fibinternal?i=%s", Integer.toString(initialValue - 1)));
                        System.out.println("m1r for " + initialValue + " was " + minusOneResult);
                        Integer minusTwoResult = makeRequest(String.format("http://localhost:3000/fibinternal?i=%s", Integer.toString(initialValue - 2)));
                        System.out.println("m2r for " + initialValue + " was " + minusTwoResult);
                        returnValue = minusOneResult + minusTwoResult;
                    }
                    System.out.println("out of fh ifelse for " + initialValue);
                    String resultString = Integer.toString(returnValue);
                    exchange.sendResponseHeaders(200, resultString.length());
                    System.out.println("sent rh for " + initialValue);
                    OutputStream os = exchange.getResponseBody();
                    os.write(resultString.getBytes(Charset.defaultCharset()));
                    os.close();
                    System.out.println("Fib: " + exchange.getRemoteAddress());
                } catch (URISyntaxException e) { } finally {
                    span.end();
                }
            }
        }
    }
  
    private int makeRequest(String urlPath) throws IOException, URISyntaxException {
        URL url = new URL(urlPath);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
    
        int status = 0;
        StringBuilder content = new StringBuilder();
    
        // Name convention for the Span is not yet defined.
        // See: https://github.com/open-telemetry/opentelemetry-specification/issues/270
        Span span = tracer.spanBuilder("/").setSpanKind(SpanKind.CLIENT).startSpan();
        try (Scope scope = span.makeCurrent()) {
          span.setAttribute("http.method", "GET");
          span.setAttribute("component", "http");
          /*
           Only one of the following is required
             - http.url
             - http.scheme, http.host, http.target
             - http.scheme, peer.hostname, peer.port, http.target
             - http.scheme, peer.ip, peer.port, http.target
          */
          URI uri = url.toURI();
          url =
              new URI(
                      uri.getScheme(),
                      null,
                      uri.getHost(),
                      uri.getPort(),
                      uri.getPath(),
                      uri.getQuery(),
                      uri.getFragment())
                  .toURL();
    
          span.setAttribute("http.url", url.toString());
    
          // Inject the request with the current Context/Span.
          openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), con, setter);
    
          try {
            // Process the request
            // Process the request
            con.setRequestMethod("GET");
            status = con.getResponseCode();
            BufferedReader in =
                new BufferedReader(
                    new InputStreamReader(con.getInputStream(), Charset.defaultCharset()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
              content.append(inputLine);
            }
            in.close();
          } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, "HTTP Code: " + status);
          }
        } finally {
          span.end();
        }
    
        // Output the result of the request
        System.out.println("Response Code: " + status);
        System.out.println("Response Msg: " + content);
        return Integer.valueOf(content.toString());
      }
  
    private Map<String, String> parseQuery(String query) {
        Map<String, String> res = new HashMap<>();
        for (String parameter : query.split("&")) {
            String[] entry = parameter.split("=");
            if (entry.length > 1) {
                res.put(entry[0], entry[1]);
            } else {
                res.put(entry[0], "");
            }
        }
        return res;
    }

    private com.sun.net.httpserver.HttpServer server;
    private static int port;
    private Main() throws IOException {
        Dotenv dotenv = Dotenv.load();
        port = Integer.parseInt(dotenv.get("SERVER_PORT", "3000"));
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 100);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        // Test urls
        server.createContext("/", new HelloHandler());
        server.createContext("/fib", new FibonacciHandler(false));
        server.createContext("/fibinternal", new FibonacciHandler(true));
        server.start();

        configureOpenTelemetry(dotenv);
        
        System.out.println("Server ready on http://localhost:" + port);
    }

    private void stop() {
        server.stop(0);
    }

    private void configureOpenTelemetry(Dotenv dotenv) {
        // console exporter
        LoggingSpanExporter loggingExporter = new LoggingSpanExporter();

        // honeycomb exporter
        OtlpGrpcSpanExporter hnyExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("https://api.honeycomb.io")
            .addHeader("x-honeycomb-team", dotenv.get("HONEYCOMB_API_KEY",""))
            .addHeader("x-honeycomb-dataset", dotenv.get("HONEYCOMB_DATASET",""))
            .build();

//        // lightstep exporter
//        OtlpGrpcSpanExporter lsExporter = OtlpGrpcSpanExporter.builder()
//            .setEndpoint("https://ingest.lightstep.com")
//            .addHeader("lightstep-access-token", dotenv.get("LS_KEY", ""))
//            .build();
        
        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(loggingExporter))
            .addSpanProcessor(BatchSpanProcessor.builder(hnyExporter).build())
//          .addSpanProcessor(BatchSpanProcessor.builder(lsExporter).build())
            .build();

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

        tracer = openTelemetry.getTracer("otel-java");
    }

    /**
     * Main method to run the example.
     *
     * @param args It is not required.
     * @throws Exception Something might go wrong.
     */
    public static void main(String[] args) throws Exception {
        final Main s = new Main();
        // Gracefully close the server
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread() {
                            @Override
                            public void run() {
                                s.stop();
                            }
                        });
    }
}
