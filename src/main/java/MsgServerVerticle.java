import io.netty.util.internal.ConcurrentSet;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
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
               sock.write(Utils.jsonToBinary(Utils.msg("New client " + sock.remoteAddress())));
           });
           eBus.publish(backwardTag, Utils.msg("New client " + sock.remoteAddress()));
           RecordParser parser = RecordParser.newDelimited(Buffer.buffer("\n".getBytes(StandardCharsets.US_ASCII)), sock)
                   .endHandler((v) -> {
                        connections.remove(sock);
                        connections.forEach(c -> {
                            c.write(Utils.jsonToBinary(Utils.msg("Client " + sock.remoteAddress() + " disconnected")));
                        });
                        eBus.publish(backwardTag, Utils.msg("Client " + sock.remoteAddress() + " disconnected"));
                        sock.close();
                   })
                   .exceptionHandler(t -> {
                        connections.remove(sock);
                       connections.forEach(c -> {
                           c.write(Utils.jsonToBinary(Utils.msg("Client " + sock.remoteAddress() + " error")));
                       });
                       eBus.publish(backwardTag, Utils.msg("Client " + sock.remoteAddress() + " error"));
                       sock.close();
                   })
                   .handler(buf -> {
                       JsonObject msg = Utils.msg(new String(buf.getBytes(), StandardCharsets.US_ASCII));
                       connections.forEach(c -> {
                           if (!c.equals(sock)) c.write(Utils.jsonToBinary(msg));
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
            sock.write(Utils.jsonToBinary(Utils.msg("Server is shutting down..")));
            sock.close();
        });
        server.close();
        stopFuture.complete();
    }
}
