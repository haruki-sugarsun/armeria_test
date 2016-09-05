import com.google.common.net.MediaType;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.http.HttpClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.http.*;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.http.HttpService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.linecorp.armeria.common.util.Functions.voidFunction;

public class Main {

    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private static HttpClient client;

    public static void main(String[] args) {

        // Setup
        client = Clients.newClient("none+h2c://localhost:8081/", HttpClient.class);

        ServerBuilder sb = new ServerBuilder()
                .port(8080, SessionProtocol.HTTP)
                .port(8081, SessionProtocol.HTTP)
                .serviceAt("/hello", new HttpService() {
                    @Override
                    public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) {
                        DefaultHttpResponse res = new DefaultHttpResponse();
                        res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Hello");
                        return res;
                    }
                }).serviceUnder("/fib", new HttpService() {
                    /*
                       /fib/N returns a Fibonacci number,
                       This recursivelly calls the server itself
                       /fib/0 == 0
                       /fib/1 == 1
                       /fib/2 == 1
                       ...
                       /fib/N == /fib/N-2 + /fib/N-1
                     */
                    @Override
                    public HttpResponse serve(ServiceRequestContext ctx,
                                              HttpRequest req) {
                        // Parse the path.
                        String path = req.path();
                        int n = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));

                        if (n <= 1) {
                            // Base cases. Return immediately.
                            return text(n == 1 ? "1" : "0");
                        }

                        // Recursive cases. Call the dependencies recursively,
                        HttpResponse ppReq = callRecursive(n - 2);
                        HttpResponse pReq = callRecursive(n - 1);
                        DefaultHttpResponse res = new DefaultHttpResponse();

                        // and return concurrently. Note that HttpResponse implements HttpResponseWriter,
                        // so the object works in concurrent ways.
                        ppReq.aggregate().handle(voidFunction((ppMes, throwable) -> {
                            pReq.aggregate().handle(voidFunction((pMes, throwable1) -> {
                                try {
                                    int ppVal = Integer.parseInt(
                                            ppMes.content().toStringAscii().replaceAll("[ \\n]", ""));
                                    int pVal = Integer.parseInt(
                                            pMes.content().toStringAscii().replaceAll("[ \\n]", ""));
                                    res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8,
                                            (ppVal + pVal) + "\n");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8,
                                            -1 + "\n");
                                }
                            }));
                        }));
                        return res;
                    }
                });

        Server server = sb.build();
        server.start();

        // Test requests to the server.
        IntStream.range(0, 20).forEach(i -> {
                    try {
                        AggregatedHttpMessage response = null;
                        response = client.execute(
                                HttpHeaders.of(HttpMethod.GET, "/fib/" + i)
                                        .set(HttpHeaderNames.ACCEPT, "utf-8")).aggregate().get();
                        System.out.println(String.format("fib(%d) = %s", i, response.content().toStringAscii()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
    }

    private static HttpResponse callRecursive(int fibArg) {
        return client.get("/fib/" + fibArg);
    }

    private static HttpResponse text(int i) {
        return text(String.valueOf(i));
    }

    private static HttpResponse text(String s) {
        DefaultHttpResponse res = new DefaultHttpResponse();
        res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, s + "\n");
        return res;
    }
}