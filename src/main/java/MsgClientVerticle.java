import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MsgClientVerticle extends AbstractVerticle {

    private Logger L;

    private String eBusTag;
    private String backwardTag;
    private String targetHost;
    private int port;
    private String id;
    private String path;

    private EventBus eBus;

    private HttpClient client;

    public MsgClientVerticle(String eBusTag, String targetHost, int port, String path, String id, String backwardTag) {
        this.eBusTag = eBusTag;
        this.targetHost = targetHost;
        this.path = path;
        this.port = port;
        this.id = id;
        this.backwardTag = backwardTag;

        L = LoggerFactory.getLogger(eBusTag);
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        L.info("Initializing client connection to " + targetHost + ":" + port + path);
        eBus = vertx.eventBus();

        try {


            client = vertx.createHttpClient();

            client.websocket(port, targetHost, path, webSock -> {
                L.info("Connected to " + targetHost + ":" + port + "/" + path);
                eBus.publish(backwardTag, Utils.msg("Connected"));
                webSock.binaryMessageHandler(buf -> {
                    eBus.publish(backwardTag, Utils.bufToJson(buf));
                });
                eBus.consumer(eBusTag).handler(msg -> {
                    JsonObject message = (JsonObject) msg.body();
                    webSock.writeBinaryMessage(Utils.jsonToBuf(message));
                });
            });
        } catch (NullPointerException e) {
            L.error("Null Pointer: " + e.getLocalizedMessage());
            e.printStackTrace();
        }


        startFuture.complete();
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        L.info("Connection to " + targetHost + ":" + port + "/" + path + " closed");
        client.close();
        stopFuture.complete();
    }
}
