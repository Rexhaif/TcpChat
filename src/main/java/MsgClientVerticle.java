import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.parsetools.RecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class MsgClientVerticle extends AbstractVerticle {

    private Logger L;

    private String eBusTag;
    private String backwardTag;
    private NetClient netClient;
    private String targetHost;
    private int port;
    private String id;

    private EventBus eBus;

    public MsgClientVerticle(String eBusTag, String targetHost, int port, String id, String backwardTag) {
        this.eBusTag = eBusTag;
        this.targetHost = targetHost;
        this.port = port;
        this.id = id;
        this.backwardTag = backwardTag;

        L = LoggerFactory.getLogger(eBusTag);
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        L.info("Initializing client connection to " + targetHost + ":" + port);
        eBus = vertx.eventBus();
        netClient = vertx.createNetClient();
        netClient.connect(new SocketAddressImpl(port, targetHost), sock -> {
           if (sock.succeeded()) {
               L.info("Successfully connected to " + targetHost + ":" + port);
               eBus.publish(backwardTag, Utils.msg("Connected"));
               NetSocket socket = sock.result();
               eBus.consumer(eBusTag).handler(msg -> {
                   JsonObject message = (JsonObject) msg.body();
                   socket.write(Utils.jsonToBinary(message));
               });
               RecordParser parser = RecordParser.newDelimited(Buffer.buffer("\n".getBytes(StandardCharsets.US_ASCII)), socket)
                       .endHandler((v) -> {
                           eBus.publish(backwardTag, Utils.eBusMsgErr("Server disconnected"));
                           socket.close();
                       })
                       .exceptionHandler(throwable -> {
                           eBus.publish(backwardTag, Utils.eBusMsgErr(throwable.getLocalizedMessage()));
                           throwable.printStackTrace();
                           socket.close();
                       })
                       .handler(buf -> {
                           eBus.publish(backwardTag, Utils.msg(new String(buf.getBytes(), StandardCharsets.US_ASCII)));
                       });
           } else {
               L.info("Cannot connect to " + targetHost + ":" + port);
               eBus.publish(backwardTag, Utils.eBusMsgErr("Cannot connect"));
           }
        });
        startFuture.complete();
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        netClient.close();
        L.info("Connection to " + targetHost + ":" + port + " closed");
        stopFuture.complete();
    }
}
