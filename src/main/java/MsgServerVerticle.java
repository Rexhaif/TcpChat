import io.netty.util.internal.ConcurrentSet;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class MsgServerVerticle extends AbstractVerticle {

    private Logger L;

    private String path;
    private int port;
    private String eBusTag;
    private String backwardTag;

    private HttpServer server;

    private EventBus eBus;

    private Set<ServerWebSocket> conns;

    public MsgServerVerticle(int port, String eBusTag, String backwardTag) {
        this.port = port;
        this.eBusTag = eBusTag;
        this.backwardTag = backwardTag;

        conns = new ConcurrentSet<>();
        path = eBusTag;

        L = LoggerFactory.getLogger(eBusTag);

    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        eBus = vertx.eventBus();
        L.info("Initializing server instance at port " + port);

        server = vertx.createHttpServer();

        server.websocketHandler(webSock -> {

            if (!webSock.path().equals(path)) {

                webSock.reject();

            } else {

                conns.add(webSock);

                conns.forEach(sock -> {
                    if (sock != webSock) {
                        sock.writeBinaryMessage(Utils.jsonToBuf(Utils.msg("SERVER: new client " + webSock.remoteAddress().toString())));
                    }
                });

                eBus.publish(backwardTag, Utils.msg("SERVER: new client " + webSock.remoteAddress().toString()));

                webSock.binaryMessageHandler(buf -> {
                    JsonObject msg = Utils.bufToJson(buf);
                    conns.forEach(sock -> {
                        if (sock != webSock) {
                            sock.writeBinaryMessage(buf);
                        }
                    });
                    eBus.publish(backwardTag, msg);
                });

            }

        });

        server.listen(port);

        startFuture.complete();
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        conns.forEach(sock -> {
            sock.writeFinalTextFrame("Server is shutting down...");
        });
        server.close();
        stopFuture.complete();
    }
}
