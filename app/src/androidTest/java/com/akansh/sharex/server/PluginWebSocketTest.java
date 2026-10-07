package com.akansh.sharex.server;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import okhttp3.*;
import static org.junit.Assert.*;

/** Exercises the real Netty handshake and SDK wire protocol on Android. */
@RunWith(AndroidJUnit4.class)
public class PluginWebSocketTest {
    private static class Browser extends WebSocketListener {
        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<>();
        final CountDownLatch opened = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        Throwable failure;
        WebSocket socket;
        @Override public void onOpen(WebSocket socket, Response response) { opened.countDown(); }
        @Override public void onMessage(WebSocket socket, String text) {
            try { messages.add(new JSONObject(text)); } catch (Exception error) { failure = error; }
        }
        @Override public void onFailure(WebSocket socket, Throwable error, Response response) { failure = error; closed.countDown(); }
        @Override public void onClosed(WebSocket socket, int code, String reason) { closed.countDown(); }
        @Override public void onClosing(WebSocket socket, int code, String reason) { socket.close(code, reason); closed.countDown(); }
        void send(String action, JSONObject data) throws Exception {
            JSONObject message = new JSONObject().put("action", action);
            if (data != null) message.put("data", data);
            assertTrue(socket.send(message.toString()));
        }
        JSONObject await(String action) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline) {
                JSONObject message = messages.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && action.equals(message.getString("action"))) return message;
                if (failure != null) throw new AssertionError(failure);
            }
            throw new AssertionError("Missing socket event: " + action);
        }
    }

    private Browser connect(OkHttpClient client, int port, String token, String plugin) throws Exception {
        Browser browser = new Browser();
        browser.socket = client.newWebSocket(new Request.Builder()
                .url("ws://127.0.0.1:" + port + "/__sharex_dev?token=" + token + "&package=" + plugin)
                .header("Origin", "http://localhost:3000").build(), browser);
        assertTrue("WebSocket handshake timed out", browser.opened.await(8, TimeUnit.SECONDS));
        assertNull(browser.failure);
        browser.socket.send(new JSONObject().put("action", "init_user").put("package_name", "dev." + plugin)
                .put("data", new JSONObject().put("uuid", java.util.UUID.randomUUID().toString())
                        .put("public_data", new JSONObject().put("name", plugin))).toString());
        browser.send("get_all_users", null);
        browser.await("return_all_users");
        browser.messages.clear();
        return browser;
    }

    @Test public void realHandshakeMessagingStorageIsolationAndCredentialRevocation() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Utils utils = new Utils(context);
        boolean priorSsl = utils.loadSetting(Constants.SSL);
        boolean priorDevelopment = utils.loadSetting(Constants.PLUGIN_DEV);
        PluginDevelopment development = new PluginDevelopment(context);
        WebServer http = new WebServer("127.0.0.1", 0);
        WebServerSocket server = null;
        OkHttpClient client = new OkHttpClient.Builder().build();
        Browser first = null, second = null, other = null;
        try {
            utils.saveSetting(Constants.SSL, false);
            development.setEnabled(true);
            String token = development.token();
            http.setContext(context);
            http.start();
            server = new WebServerSocket(0, context.getPackageName(), http::isAuthorized, null, development::socketPackage);
            server.start();
            int port = server.getListeningPort();
            first = connect(client, port, token, "sharex.instrumentation.plugin");
            second = connect(client, port, token, "sharex.instrumentation.plugin");
            other = connect(client, port, token, "sharex.other.plugin");
            first.send("get_all_users", null);
            org.json.JSONArray users = first.await("return_all_users").getJSONArray("all_users");
            assertEquals(2, users.length());
            for (int i = 0; i < users.length(); i++) {
                first.send("send_msg", new JSONObject().put("uuid", users.getJSONObject(i).getString("uuid")).put("msg", "Hello from SDK"));
            }
            assertEquals("Hello from SDK", second.await("msg_arrive").getString("message"));
            assertNull("Messages must not cross plugin namespaces", other.messages.poll(300, TimeUnit.MILLISECONDS));

            String database = "test_" + System.currentTimeMillis();
            first.send("db_action_init_db", new JSONObject().put("db_name", database));
            assertEquals("success", first.await("db_action_init_db_result").getString("data"));
            first.send("db_action_insert_data", new JSONObject().put("db_name", database).put("collection", "notes")
                    .put("new_data", new JSONObject().put("text", "Saved from SDK").put("_uuid", "note-1")));
            assertEquals("success", new JSONObject(first.await("db_action_insert_data_result").getString("data")).getString("status"));
            first.send("db_action_get_all_data", new JSONObject().put("db_name", database).put("collection", "notes"));
            assertEquals("Saved from SDK", new JSONObject(first.await("db_action_get_all_data_result").getString("data"))
                    .getJSONArray("data").getJSONObject(0).getString("text"));

            first.send("create_json_file", new JSONObject().put("filename", "sample").put("data", new JSONObject().put("works", true)));
            assertTrue(first.await("return_create_json_file").getBoolean("result"));
            first.send("read_json_file", new JSONObject().put("filename", "sample"));
            assertTrue(new JSONObject(first.await("return_read_json_file").getString("data")).getBoolean("works"));

            try (Response response = client.newCall(new Request.Builder().url("http://127.0.0.1:" + http.getListeningPort()
                    + "/ShareX?action=getFiles&token=" + token).build()).execute()) { assertEquals(401, response.code()); }
            Browser badKey = new Browser();
            badKey.socket = client.newWebSocket(new Request.Builder().url("ws://127.0.0.1:" + port
                    + "/__sharex_dev?token=" + "b".repeat(64) + "&package=sharex.instrumentation.plugin").build(), badKey);
            assertTrue(badKey.closed.await(8, TimeUnit.SECONDS));
            assertNotNull(badKey.failure);
            badKey.socket.cancel();

            first.messages.clear(); second.messages.clear(); other.messages.clear();
            second.socket.close(1000, "Done");
            first.await("user_left");
            assertNull("Departure events must not cross plugin namespaces", other.messages.poll(300, TimeUnit.MILLISECONDS));

            development.setEnabled(false);
            first.send("get_all_users", null);
            assertTrue("Existing socket must lose access", first.closed.await(8, TimeUnit.SECONDS));
        } finally {
            if (first != null) first.socket.cancel();
            if (second != null) second.socket.cancel();
            if (other != null) other.socket.cancel();
            if (server != null) server.stop();
            http.stop();
            development.setEnabled(priorDevelopment);
            utils.saveSetting(Constants.SSL, priorSsl);
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
    }
}
