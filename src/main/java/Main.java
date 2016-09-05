import com.google.common.net.MediaType;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.http.HttpClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.http.DefaultHttpResponse;
import com.linecorp.armeria.common.http.HttpRequest;
import com.linecorp.armeria.common.http.HttpResponse;
import com.linecorp.armeria.common.http.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.http.HttpService;

public class Main {
    public static void main(String[] args) {
        new ServerBuilder().port(8080, SessionProtocol.HTTP).serviceAt("/hello", new HttpService() {
            @Override
            public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) {
                DefaultHttpResponse res = new DefaultHttpResponse();
                res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Hello");
                return res;
            }
        }).build().start();

        HttpClient client = Clients.newClient("none+h2c://localhost:8080/", HttpClient.class);
        System.out.println(client.get("/hello").aggregate().join().content().toStringAscii());
    }
}