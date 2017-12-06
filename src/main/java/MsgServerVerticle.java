import io.netty.util.internal.ConcurrentSet;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class MsgServerVerticle extends AbstractVerticle {

    private Logger L;

    private int port;
    private String eBusTag;
    private String backwardTag;

    private NetServer server;

    private Set<NetSocket> connections;

    private EventBus eBus;

    public MsgServerVerticle(int port, String eBusTag, String backwardTag) {
        this.port = port;
        this.eBusTag = eBusTag;
        this.backwardTag = backwardTag;

        L = LoggerFactory.getLogger(eBusTag);
        connections = new ConcurrentSet<>();
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        eBus = vertx.eventBus();
        L.info("Initializing server instance at port " + port);
        server = vertx.createNetServer();
        server.connectHandler(sock -> {
           connections.add(sock);
           connections.forEach(sock1 -> {
               sock.write(Utils.jsonToBuf(Utils.msg("New client " + sock.remoteAddress()))).end();
           });
           eBus.publish(backwardTag, Utils.msg("New client " + sock.remoteAddress()));
           sock.handler(buf -> {
               JsonObject msg = Utils.bufToJson(buf);
               connections.forEach(sock1 -> {
                   sock.write(buf).end();
               });
               eBus.publish(backwardTag, msg);
           });
        });
        server.listen(port);
        startFuture.complete();
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        connections.forEach(sock -> {
            sock.write(Utils.jsonToBuf(Utils.msg("Server is shutting down..")));
        });
        server.close();
        stopFuture.complete();
    }
}
