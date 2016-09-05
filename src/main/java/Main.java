import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.common.SerializationFormat;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.thrift.THttpService;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;

public class Main {
    public static void main(String[] args) {
        new ServerBuilder().port(8080, SessionProtocol.HTTP).serviceAt("/echo", THttpService.of(
                new EchoService.AsyncIface() {
                    @Override
                    public void echo(String message, AsyncMethodCallback resultHandler) throws TException {
                        resultHandler.onComplete(message);
                    }
                }, SerializationFormat.THRIFT_BINARY))
                .build().start();

        try {
            // Sync
            EchoService.Iface echoSyncClient = Clients.newClient(
                    "tbinary+h2c://localhost:8080/echo",
                    EchoService.Iface.class);
            System.err.println(echoSyncClient.echo("Hello, Armeria Sync!"));

            // Async
            EchoService.AsyncIface echoAsyncClient = Clients.newClient(
                    "tbinary+h2c://localhost:8080/echo",
                    EchoService.AsyncIface.class);
            echoAsyncClient.echo("Hello, Armeria Async!", new AsyncMethodCallback<String>() {
                @Override
                public void onComplete(String response) {
                    System.err.println(response);
                }

                @Override
                public void onError(Exception exception) {
                    exception.printStackTrace();
                }
            });
        } catch (TException e) {
            e.printStackTrace();
        }
    }
}