package de.blazemcworld.jsscripts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Random;

public class JSPM {

    private static final String HOST = "https://backend-1-a2537223.deta.app";
    private static String token = "";
    private static long expires = 0;

    public static void upload(String name, byte[] zip) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(HOST + "/pkg/" + name))
                .header("Authorization", getToken())
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(zip))
                .build();

        JsonObject res = JsonParser.parseString(client.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
        if (!res.get("success").getAsBoolean()) {
            throw new Exception(res.get("error").getAsString());
        }
    }

    private static String getToken() throws Exception {
        if (expires > System.currentTimeMillis()) {
            return token;
        }

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(HOST + "/auth/getnonce/" + JsScripts.MC.getSession().getProfile().getId().toString()))
                .build();

        JsonObject nonceRes = JsonParser.parseString(client.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
        if (!nonceRes.get("success").getAsBoolean()) {
            throw new Exception(nonceRes.get("error").getAsString());
        }
        String serverNonce = nonceRes.get("nonce").getAsString();

        StringBuilder clientNonce = new StringBuilder();

        Random rng = new SecureRandom();
        String chars = "0123456789abcdef";
        for (int i = 0; i < 32; i++) {
            clientNonce.append(chars.charAt(rng.nextInt(16)));
        }

        MessageDigest digester = MessageDigest.getInstance("SHA-256");
        digester.update((serverNonce + "+" + clientNonce).getBytes(StandardCharsets.UTF_8));
        byte[] hashBytes = digester.digest();
        StringBuilder hash = new StringBuilder();
        for (byte b : hashBytes) {
            hash.append(String.format("%02x", b));
        }

        JsScripts.MC.getSessionService().joinServer(
                JsScripts.MC.getSession().getProfile(),
                JsScripts.MC.getSession().getAccessToken(),
                hash.substring(0, 40)
        );

        req = HttpRequest.newBuilder()
                .uri(URI.create(HOST + "/auth/puttoken/" + JsScripts.MC.getSession().getProfile().getId().toString()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                            "nonce": "%s"
                        }
                        """.formatted(clientNonce)))
                .build();

        JsonObject tokenRes = JsonParser.parseString(client.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
        if (!tokenRes.get("success").getAsBoolean()) {
            throw new Exception(nonceRes.get("error").getAsString());
        }
        token = tokenRes.get("token").getAsString();
        expires = System.currentTimeMillis() + 50 * 60 * 1000;

        return token;
    }

    public static void download(String name) throws Exception {
        downloadDir(name);
    }

    private static void downloadDir(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/McJsScripts/JSPMRegistry/contents/packages/" + path))
                .build();

        JsonArray res = JsonParser.parseString(client.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonArray();

        for (JsonElement f : res) {
            if (f.getAsJsonObject().get("type").getAsString().equals("dir")) {
                downloadDir(f.getAsJsonObject().get("path").getAsString());
            }
            if (f.getAsJsonObject().get("type").getAsString().equals("file")) {
                req = HttpRequest.newBuilder()
                        .uri(URI.create(f.getAsJsonObject().get("download_url").getAsString()))
                        .build();

                String filePath = f.getAsJsonObject().get("path").getAsString().replaceFirst("packages/", "");
                JsScripts.displayChat(Text.literal("Downloading " + filePath).formatted(Formatting.AQUA));
                Path p = ScriptManager.modDir.resolve("scripts").resolve(filePath.replace('/', File.separatorChar));
                Files.createDirectories(p.getParent());
                Files.write(p, client.send(req, HttpResponse.BodyHandlers.ofByteArray()).body());
            }
        }
    }

    public static boolean has(String name) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/McJsScripts/JSPMRegistry/contents/packages/" + name))
                .build();

        return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() != 404;
    }
}
