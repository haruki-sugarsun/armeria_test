import com.google.common.net.MediaType;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.http.HttpClient;
import com.linecorp.armeria.client.routing.EndpointGroupRegistry;
import com.linecorp.armeria.client.routing.EndpointSelectionStrategy;
import com.linecorp.armeria.client.routing.StaticEndpointGroup;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.http.DefaultHttpResponse;
import com.linecorp.armeria.common.http.HttpRequest;
import com.linecorp.armeria.common.http.HttpResponse;
import com.linecorp.armeria.common.http.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.Service;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.http.HttpService;

import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) {
        new ServerBuilder().port(8080, SessionProtocol.HTTP).serviceAt("/hello", createNewService(1)).build().start();
        new ServerBuilder().port(8081, SessionProtocol.HTTP).serviceAt("/hello", createNewService(2)).build().start();
        new ServerBuilder().port(8082, SessionProtocol.HTTP).serviceAt("/hello", createNewService(3)).build().start();

        StaticEndpointGroup serverGroup = new StaticEndpointGroup(
                Endpoint.of("localhost", 8080, 1),
                Endpoint.of("localhost", 8081, 2),
                Endpoint.of("localhost", 8082, 3));

        EndpointGroupRegistry.register("localhosts", serverGroup, EndpointSelectionStrategy.WEIGHTED_ROUND_ROBIN);

        HttpClient client = Clients.newClient("none+h2c://group:localhosts/", HttpClient.class);
        for (int i = 0; i < 600; i++) {
            System.out.println(client.get("/hello").aggregate().join().content().toStringAscii());
        }
    }

    private static Service createNewService(int i) {
        return new HttpService() {
            AtomicInteger counter = new AtomicInteger(0);

            @Override
            public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) {
                DefaultHttpResponse res = new DefaultHttpResponse();
                res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8,
                        String.format("Hello from server %d. Counter: %d.", i, counter.incrementAndGet()));
                return res;
            }
        };
    }
}