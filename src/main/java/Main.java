import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    public static void main(String[] args) {

        Logger L = LoggerFactory.getLogger("MAIN");
        L.debug("Smartify Messaging System");
        L.debug("Loading...");
        L.debug("-------------------------");

        Vertx vertx = Vertx.vertx(
                new VertxOptions()
                        .setMetricsOptions(
                                new DropwizardMetricsOptions(
                                        new DropwizardMetricsOptions()
                                                .setEnabled(true)
                                                .setRegistryName("root")
                                )
                        )
        );

        vertx.deployVerticle(new HttpVerticle());

    }

}
