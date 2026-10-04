package com.akansh.fileserversuit.server;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WebAuthenticationTest {
    private static final class IsolatedContext extends ContextWrapper {
        final String prefix = "auth-test-"+UUID.randomUUID()+"-";
        IsolatedContext() { super(ApplicationProvider.getApplicationContext()); }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return super.getSharedPreferences(prefix+name, mode); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) { return super.openOrCreateDatabase(prefix+name,mode,factory); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errors) { return super.openOrCreateDatabase(prefix+name,mode,factory,errors); }
        @Override public void sendBroadcast(Intent intent) { /* Approval UI is intentionally isolated from the real app. */ }
        void clean() { getBaseContext().deleteDatabase(prefix+DeviceManager.DATABASE_NAME); getBaseContext().deleteSharedPreferences(prefix+"web_password"); getBaseContext().deleteSharedPreferences(prefix+getPackageName()); }
    }
    private FullHttpResponse send(WebRequestHandler handler, HttpMethod method, String path, String cookies, String body) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path,
                Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set("Host", "127.0.0.1:6060").set("Origin", "http://127.0.0.1:6060");
        if (!cookies.isEmpty()) request.headers().set("Cookie", cookies);
        EmbeddedChannel channel = new EmbeddedChannel(handler.newChannelHandler()) {
            @Override protected SocketAddress remoteAddress0() { return new InetSocketAddress("127.0.0.1", 12345); }
        };
        try {
            channel.writeInbound(request);
            FullHttpResponse response = channel.readOutbound();
            assertNotNull(response);
            return response;
        } finally { channel.finishAndReleaseAll(); }
    }
    @Test public void directRequestsAndForgedDisplayIdsNeverBypassApproval() {
        IsolatedContext context = new IsolatedContext();
        WebRequestHandler handler = new WebRequestHandler(context, new Utils(context), new ServerUtils(context));
        String secret = WebSecurity.randomToken(), id = WebSecurity.browserId(secret);
        try (DeviceManager devices = new DeviceManager(context)) {
            devices.addDevice(id, Constants.DEVICE_TYPE_PERMANENT, "Test browser");
            for (String path : new String[]{"/ShareX?action=getState", "/ShareX?action=previewFile&location=secret.txt", "/ShareX?action=downloadFiles&data=[]", "/ShareX/thumbnail/app/test", "/SharexApp/test/", "/ShareX/uploadFile"}) {
                FullHttpResponse response = send(handler, path.endsWith("uploadFile") ? HttpMethod.POST : HttpMethod.GET, path, "fsx_auth_token="+id, "");
                try { assertEquals(path, 401, response.status().code()); } finally { response.release(); }
            }
            FullHttpResponse auth = send(handler, HttpMethod.GET, "/ShareX?action=auth&device_id="+id, "", "");
            try {
                assertEquals("false", auth.content().toString(StandardCharsets.UTF_8));
                assertTrue(auth.headers().get("Set-Cookie").contains("HttpOnly; SameSite=Strict"));
            } finally { auth.release(); }
        } finally { handler.shutdown(); context.clean(); }
    }
    @Test public void passwordCookieIsRequiredAndRememberedExemptionRequiresPermanentProof() {
        IsolatedContext context = new IsolatedContext();
        WebPasswordSettings settings = new WebPasswordSettings(context);
        settings.save(true, WebSecurity.hashPassword("test password".toCharArray()), 24, false);
        WebRequestHandler handler = new WebRequestHandler(context, new Utils(context), new ServerUtils(context));
        String secret = WebSecurity.randomToken(), id = WebSecurity.browserId(secret);
        String cookie = "sx_browser="+secret;
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ShareX?action=getState");
        request.headers().set("Cookie", cookie);
        try (DeviceManager devices = new DeviceManager(context)) {
            devices.addDevice(id, Constants.DEVICE_TYPE_TEMP, "Test browser");
            assertFalse(handler.isAuthorized(request));
            FullHttpResponse wrong = send(handler, HttpMethod.POST, "/ShareX/password", cookie, "{\"password\":\"wrong\"}");
            try { assertEquals(401, wrong.status().code()); } finally { wrong.release(); }
            FullHttpResponse login = send(handler, HttpMethod.POST, "/ShareX/password", cookie, "{\"password\":\"test password\"}");
            String passwordCookie;
            try {
                assertEquals(200, login.status().code());
                assertTrue(login.headers().get("Set-Cookie").contains("Max-Age=86400; HttpOnly; SameSite=Strict"));
                passwordCookie = login.headers().get("Set-Cookie").split(";",2)[0];
            } finally { login.release(); }
            request.headers().set("Cookie", cookie+"; "+passwordCookie);
            assertTrue(handler.isAuthorized(request));
            settings.save(true, null, 1, false);
            assertFalse(handler.isAuthorized(request));
            settings.save(true, null, 24, true);
            request.headers().set("Cookie", cookie);
            assertFalse(handler.isAuthorized(request));
            devices.addDevice(id, Constants.DEVICE_TYPE_PERMANENT, "Test browser");
            assertTrue(handler.isAuthorized(request));
            devices.forgetDevice(id);
            assertFalse(handler.isAuthorized(request));
        } finally { handler.shutdown(); context.clean(); }
    }
    @Test public void legacyRowsRequireFreshApprovalEvenWithAValidLookingIdentity() {
        IsolatedContext context = new IsolatedContext();
        String token = WebSecurity.randomToken(), id = WebSecurity.browserId(token);
        try (SQLiteDatabase old = context.openOrCreateDatabase(DeviceManager.DATABASE_NAME, Context.MODE_PRIVATE, null)) {
            old.execSQL("CREATE TABLE D_LIST(ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,DEVICE_ID TEXT NOT NULL UNIQUE,DEVICE_TYPE INT NOT NULL,DEVICE_NAME TEXT NOT NULL DEFAULT '',LAST_CONNECTED INTEGER NOT NULL DEFAULT 0)");
            old.execSQL("INSERT INTO D_LIST(DEVICE_ID,DEVICE_TYPE) VALUES (?,1)", new Object[]{id});
            old.setVersion(3);
        }
        try (DeviceManager devices = new DeviceManager(context)) {
            assertTrue(devices.isDeviceExist(id));
            assertFalse(devices.isSecureApproved(id, true));
            devices.addDevice(id, Constants.DEVICE_TYPE_PERMANENT);
            assertTrue(devices.isSecureApproved(id, true));
        } finally { context.clean(); }
    }
}
