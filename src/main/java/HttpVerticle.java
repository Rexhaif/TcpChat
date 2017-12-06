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

            rtx.response().end(Utils.snapshotMetrics(metrics).encodePrettily());

        });

        router.post("/server/:port/start").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            if (chats.containsKey(port + "S")) {
                rtx.response().setStatusCode(403).end();
            } else {
                vertx.deployVerticle(
                        new MsgServerVerticle(port, "portS" + port, "backS" + port),
                        res -> {

                            chats.put(port + "S", new Pair<>(res.result(), new CopyOnWriteArrayList<>()));

                            eBus.consumer("backS" + port).handler(msg -> {
                                chats.get(port + "S").getValue().add((JsonObject) msg.body());
                            });

                        }
                );
                rtx.response().setStatusCode(201).end();
            }

        });

        router.get("/server/:port/messages").handler(rtx -> {

            JsonObject rsp = new JsonObject();
            JsonArray messages = new JsonArray();
            int port = Integer.parseInt(rtx.request().getParam("port"));
            if (!chats.containsKey(port + "S")) {
                rtx.response().setStatusCode(404).end();
            } else {
                chats.get(port + "S").getValue().forEach(messages::add);
                rsp.put("messages", messages);
                rtx.response().setChunked(true).write(rsp.encodePrettily()).end();
            }

        });

        router.post("/server/:port/destroy").handler(rtx -> {
           int port = Integer.parseInt(rtx.request().getParam("port"));
           if (!chats.containsKey(port + "S")) {
               rtx.response().setStatusCode(404).end();
           } else {
               String depId = chats.get(port + "S").getKey();
               vertx.undeploy(depId);
               chats.remove(port + "S");
               rtx.response().setStatusCode(200).end();
           }
        });

        router.post("/client/:port/:login/create").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String login = rtx.request().getParam("login");
            if (chats.containsKey(port + "@" + login)) {
                rtx.response().setStatusCode(403).end();
            } else {
                vertx.deployVerticle(
                        new MsgClientVerticle(
                                port + "@" + login,
                                "127.0.0.1",
                                port, login,
                                "back" + port + "@" + login
                        ), res -> {
                            chats.put(port + "@" + login, new Pair<>(res.result(), new CopyOnWriteArrayList<>()));
                            eBus.consumer("back" + port + "@" + login).handler(msg -> {
                                chats.get(port + "@" + login).getValue().add((JsonObject) msg.body());
                            });
                        }
                );
                rtx.response().setStatusCode(201).end();
            }

        });

        router.post("/client/:port/:login/sendmsg").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String login = rtx.request().getParam("login");
            if (!chats.containsKey(port + "@" + login)) {
                rtx.response().setStatusCode(404).end();
            } else {
                String msg = rtx.getBodyAsString();
                eBus.publish(port + "@" + login, Utils.msg(login + ": " + msg));
                rtx.response().setStatusCode(200).end();
            }

        });

        router.get("/client/:port/:login/messages").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String login = rtx.request().getParam("login");
            if (!chats.containsKey(port + "@" + login)) {
                rtx.response().setStatusCode(404).end();
            } else {
                JsonObject rsp = new JsonObject();
                JsonArray messages = new JsonArray();
                chats.get(port + "@" + login).getValue().forEach(messages::add);
                rsp.put("messages", messages);
                rtx.response().setChunked(true).write(rsp.encodePrettily()).end();
            }

        });

        router.post("/client/:port/:login/destroy").handler(rtx -> {

            int port = Integer.parseInt(rtx.request().getParam("port"));
            String login = rtx.request().getParam("login");
            if (!chats.containsKey(port + "@" + login)) {
                rtx.response().setStatusCode(404).end();
            } else {
                String depId = chats.get(port + "@" + login).getKey();
                vertx.undeploy(depId);
                chats.remove(port + "@" + login);
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
