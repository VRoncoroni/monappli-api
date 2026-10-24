package fr.exemple.monappli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

class AppTest {

    @Test
    void healthRepondUp() {
        JavalinTest.test(App.create(), (server, client) -> {
            var res = client.get("/health");
            assertEquals(200, res.code());
            assertTrue(res.body().string().contains("UP"));
        });
    }

    @Test
    void versionEstExposee() {
        JavalinTest.test(App.create(), (server, client) ->
            assertTrue(client.get("/version").body().string().contains("version")));
    }

    @Test
    void versionAnnonceLEnvironnement() {
        JavalinTest.test(App.create("preprod"), (server, client) -> {
            var body = client.get("/version").body().string();
            assertTrue(body.contains("\"environment\":\"preprod\""), body);
            assertTrue(body.contains("\"version\":\"dev\""), body);
        });
    }
}
