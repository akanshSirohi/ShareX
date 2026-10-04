package com.akansh.fileserversuit.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Password hashing and browser-bound, authenticated absolute-expiry sessions. */
public final class WebSecurity {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    private WebSecurity() {}
    public static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return hex(bytes);
    }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
    public static String browserId(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) return "";
        try { return "v2:" + hex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static String hashPassword(char[] password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return hex(salt) + ":" + hex(derive(password, salt));
    }
    public static boolean verifyPassword(char[] password, String record) {
        try {
            String[] parts = record.split(":", -1);
            return parts.length == 2 && MessageDigest.isEqual(derive(password, unhex(parts[0])), unhex(parts[1]));
        } catch (RuntimeException error) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception error) { throw new IllegalStateException(error); }
        finally { spec.clearPassword(); }
    }
    private static byte[] unhex(String value) {
        if (!value.matches("(?:[a-f0-9]{2})+")) throw new IllegalArgumentException("Invalid key");
        byte[] bytes = new byte[value.length()/2];
        for (int i=0;i<bytes.length;i++) bytes[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return bytes;
    }
    private static byte[] sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(unhex(secret), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static String session(String browser, String revision, long now, long duration, String secret) {
        String payload = browser + ":" + revision + ":" + now + ":" + (now + duration);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.US_ASCII)) + "." + hex(sign(payload, secret));
    }
    public static boolean validSession(String token, String browser, String revision, long now, long duration, String secret) {
        if (browser.isEmpty() || token == null || token.length() > 512) return false;
        try {
            String[] encoded = token.split("\\.", -1);
            if (encoded.length != 2) return false;
            String payload = new String(Base64.getUrlDecoder().decode(encoded[0]), StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(sign(payload, secret), unhex(encoded[1]))) return false;
            String[] parts = payload.split(":", -1);
            if (parts.length != 5 || !browser.equals(parts[0]+":"+parts[1]) || !revision.equals(parts[2])) return false;
            long issued = Long.parseLong(parts[3]), expires = Long.parseLong(parts[4]);
            return issued <= now && expires > now && expires - issued == duration && duration > 0;
        } catch (RuntimeException error) { return false; }
    }
}
