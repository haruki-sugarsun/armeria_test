import com.codahale.metrics.MetricRegistry;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LoggingSpanCollector;
import com.google.common.net.MediaType;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.http.HttpClient;
import com.linecorp.armeria.client.logging.DropwizardMetricCollectingClient;
import com.linecorp.armeria.client.tracing.HttpTracingClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.http.DefaultHttpResponse;
import com.linecorp.armeria.common.http.HttpRequest;
import com.linecorp.armeria.common.http.HttpResponse;
import com.linecorp.armeria.common.http.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.http.HttpService;
import com.linecorp.armeria.server.logging.DropwizardMetricCollectingService;

public class Main {
    public static void main(String[] args) {
        MetricRegistry metricRegistry = new MetricRegistry();

        Service<HttpRequest, HttpResponse> service = new HttpService() {
            @Override
            public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) {
                DefaultHttpResponse res = new DefaultHttpResponse();
                res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Hello");
                return res;
            }
        };
        new ServerBuilder().port(8080, SessionProtocol.HTTP)
                .serviceAt("/hello",
                        service.decorate(DropwizardMetricCollectingService.newDecorator(
                                metricRegistry, "httpService")))
                .build().start();

        HttpClient client = new ClientBuilder("none+h2c://localhost:8080/")
                .decorator(HttpRequest.class, HttpResponse.class,
                        HttpTracingClient.newDecorator(newBrave("httpClient")))
                .decorator(HttpRequest.class, HttpResponse.class,
                        DropwizardMetricCollectingClient.newDecorator(metricRegistry, "httpClient"))
                .build(HttpClient.class);
        System.out.println(client.get("/hello").aggregate().join().content().toStringAscii());
        System.out.println(metricRegistry.getMeters());
        System.out.println(metricRegistry.getMeters().get("httpService.GET.successes").getCount());
        System.out.println(metricRegistry.getMeters().get("httpClient.GET.successes").getCount());
    }

    private static Brave newBrave(String name) {
        return new Brave.Builder(name)
                .spanCollector(new LoggingSpanCollector())
                .build();

    }
}