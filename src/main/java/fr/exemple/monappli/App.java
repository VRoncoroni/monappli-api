package fr.exemple.monappli;

import io.javalin.Javalin;
import java.util.Map;
import java.util.Optional;

public final class App {

    // Lue dans le manifeste du jar : "2.4.1-38" en CI, "dev" en local
    static final String VERSION =
        Optional.ofNullable(App.class.getPackage().getImplementationVersion()).orElse("dev");

    private App() {
    }

    public static Javalin create() {
        return create(System.getenv().getOrDefault("APP_ENV", "local"));
    }

    static Javalin create(String environment) {
        return Javalin.create()
            .get("/health", ctx -> ctx.json(Map.of("status", "UP")))
            .get("/version", ctx -> ctx.json(Map.of("version", VERSION, "environment", environment)));
    }

    public static void main(String[] args) {
        // APP_PORT, APP_ENV et APP_LOG_LEVEL viennent de /etc/monappli/api.env, généré par Ansible
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel",
            System.getenv().getOrDefault("APP_LOG_LEVEL", "info"));
        int port = Integer.parseInt(System.getenv().getOrDefault("APP_PORT", "8080"));
        create().start(port);
    }
}
