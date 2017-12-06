import com.codahale.metrics.MetricRegistry;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;


public class Utils {

    public static JsonObject eBusMsgErr(String reason) {
        return new JsonObject().put("error", reason);
    }

    public static JsonObject msg(String payload) {
        return new JsonObject().put("msg", payload);
    }

    public static Buffer jsonToBuf(JsonObject json) {
        return Buffer.buffer(json.encode().getBytes(StandardCharsets.UTF_8));
    }

    public static JsonObject bufToJson(Buffer buffer) {
        return new JsonObject(new String(buffer.getBytes(), StandardCharsets.UTF_8));
    }

    public static JsonObject snapshotMetrics(MetricRegistry reg) {

        JsonObject obj = new JsonObject();

        JsonArray counters = new JsonArray();
        reg.getCounters().forEach((n, c) -> {
            counters.add(
                    new JsonObject().put("name", n).put("counter", c.getCount())
            );
        });
        obj.put("counters", counters);

        JsonArray gauges = new JsonArray();
        reg.getGauges().forEach((n, g) -> {
            gauges.add(
                    new JsonObject().put("name", n).put("gauge", g.getValue())
            );
        });
        obj.put("gauges", gauges);

        JsonArray histograms = new JsonArray();
        reg.getHistograms().forEach((n, h) -> {
            histograms.add(
                    new JsonObject().put("name", n).put("histogram", h.getCount())
            );
        });
        obj.put("histograms", histograms);

        JsonArray meters = new JsonArray();
        reg.getMeters().forEach((n, m) -> {
            meters.add(
                    new JsonObject().put("name", n).put("meterCurr", m.getCount()).put("meterMean", m.getMeanRate())
            );
        });
        obj.put("meters", meters);

        return obj;

    }


}
