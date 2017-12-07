import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import javafx.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpVerticle extends AbstractVerticle {

    private HttpServer server;
    private Router router;
    private MetricRegistry metrics;

    private Map<String, Pair<String, List<JsonObject>>> chats;

    private EventBus eBus;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        eBus = vertx.eventBus();

        chats = new ConcurrentHashMap<>();

        metrics = SharedMetricRegistries.getOrCreate("root");

        server = vertx.createHttpServer();

        router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.route().handler(LoggerHandler.create());

        router.get("/stats").handler(rtx -> {

            rtx.response().setStatusCode(200).setChunked(true).headers().add("Content-Type", "application/json");

            rtx.response().end(Utils.snapshotMetrics(metrics).encodePrettily());

        });

        router.post("/server/:port/:path/start").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String path = "/" + rtx.request().getParam("path");
            if (chats.containsKey(port + "@" + path)) {
                rtx.response().setStatusCode(403).end();
            } else {
                vertx.deployVerticle(new MsgServerVerticle(port, path, port + "@" + path), res -> {
                    if (res.succeeded()) {
                        String depId = res.result();
                        chats.put(port + "@" + path, new Pair<>(depId, new CopyOnWriteArrayList<>()));
                        eBus.consumer(port + "@" + path).handler(msg -> {
                           chats.get(port + "@" + path).getValue().add((JsonObject) msg.body());
                        });
                        rtx.response().setStatusCode(201).end();
                    } else {
                        rtx.response().setStatusCode(500).end();
                    }
                });

            }

        });

        router.get("/server/:port/:path/messages").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String path = "/" + rtx.request().getParam("path");
            if (!chats.containsKey(port + "@" + path)) {
                rtx.response().setStatusCode(404).end();
            } else {
                JsonObject rsp = new JsonObject();
                JsonArray messages = new JsonArray();
                chats.get(port + "@" + path).getValue().forEach(messages::add);
                rtx.response().setChunked(true).headers().add("Content-Type", "application/json");
                rtx.response().write(rsp.put("messages", messages).encodePrettily());
                rtx.response().end();
            }

        });

        router.post("/server/:port/:path/destroy").handler(rtx -> {
           int port = Integer.parseInt(rtx.request().getParam("port"));
           String path = "/" + rtx.request().getParam("path");
           if (!chats.containsKey(port + "@" + path)) {
               rtx.response().setStatusCode(404).end();
           } else {
               String depId = chats.get(port + "@" + path).getKey();
               vertx.undeploy(depId);
               chats.remove(port + "@" + path);
               rtx.response().setStatusCode(200).end();
           }
        });

        router.post("/client/:port/:path/:login/create").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String path = "/" + rtx.request().getParam("path");

            String login = rtx.request().getParam("login");
            if (chats.containsKey(port + "@" + login + "@" + path)) {
                rtx.response().setStatusCode(403).end();
            } else {
                vertx.deployVerticle(new MsgClientVerticle(
                        port + "@" + login + "@" + path,
                        "127.0.0.1",
                        port, path,
                        login, port + "@" + login + "@" + path
                ), res -> {
                    if (res.succeeded()) {
                        String depId = res.result();
                        chats.put(port + "@" + login + "@" + path, new Pair<>(depId, new CopyOnWriteArrayList<>()));
                        eBus.consumer(port + "@" + login + "@" + path).handler(msg -> {
                            chats.get(port + "@" + login + "@" + path).getValue().add((JsonObject) msg.body());
                        });
                        rtx.response().setStatusCode(200).end();
                    } else rtx.response().setStatusCode(500).end();
                });
            }

        });

        router.post("/client/:port/:path/:login/sendmsg").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String path = "/" + rtx.request().getParam("path");
            String login = rtx.request().getParam("login");
            if (!chats.containsKey(port + "@" + login + "@" + path)) {
                rtx.response().setStatusCode(404).end();
            } else {
               String message = rtx.getBodyAsString();
               eBus.publish(port + "@" + login + "@" + path, Utils.msg(login + ": " + message));
               rtx.response().setStatusCode(200).end();
            }

        });

        router.get("/client/:port/:path/:login/messages").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String path = "/" + rtx.request().getParam("path");
            String login = rtx.request().getParam("login");
            if (!chats.containsKey(port + "@" + login + "@" + path)) {
                rtx.response().setStatusCode(404).end();
            } else {
                JsonArray messages = new JsonArray();
                chats.get(port + "@" + login + "@" + path).getValue().forEach(messages::add);
                rtx.response().setStatusCode(200).setChunked(true).headers().add("Content-Type", "application/json");
                rtx.response().write(new JsonObject().put("messages", messages).encodePrettily());
                rtx.response().end();
            }

        });

        router.post("/client/:port/:path/:login/destroy").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String path = "/" + rtx.request().getParam("path");
            String login = rtx.request().getParam("login");
            if (!chats.containsKey(port + "@" + login + "@" + path)) {
                rtx.response().setStatusCode(404).end();
            } else {
                String depId = chats.get(port + "@" + login + "@" + path).getKey();
                vertx.undeploy(depId);
                chats.remove(port + "@" + login + "@" + path);
                rtx.response().setStatusCode(200).end();
            }

        });

        server.requestHandler(router::accept).listen(8080);

        startFuture.complete();

    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
    }
}
