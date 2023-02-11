package de.blazemcworld.jsscripts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Pair;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Crypt {

    private static final Path sigFile = JsScripts.MC.runDirectory.toPath().resolve("JsScripts").resolve("signature.json");

    public static Pair<String, String> generateSignatureKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(512, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();
            return new Pair<>(base64encode(pair.getPublic().getEncoded()), base64encode(pair.getPrivate().getEncoded()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String base64encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] base64decode(String str) {
        return Base64.getDecoder().decode(str);
    }

    public static String sign(String data, String privateKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(base64decode(privateKey))));
            sig.update(data.replace("\r", "").getBytes(StandardCharsets.UTF_8));
            return base64encode(sig.sign());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(String data, String signature, String publicKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(base64decode(publicKey))));
            sig.update(data.replace("\r", "").getBytes(StandardCharsets.UTF_8));
            return sig.verify(base64decode(signature));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String hash(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data.replace("\r", "").getBytes(StandardCharsets.UTF_8));
            return base64encode(md.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Pair<String, String> getKeyPair() {
        try {
            if (!Files.isRegularFile(sigFile)) {
                Pair<String, String> sig = generateSignatureKeyPair();
                JsonObject j = new JsonObject();
                j.addProperty("public_key", sig.getLeft());
                j.addProperty("private_key", sig.getRight());
                Files.writeString(sigFile, j.toString());
            }

            JsonObject obj = JsonParser.parseString(Files.readString(sigFile)).getAsJsonObject();
            return new Pair<>(
                    obj.get("public_key").getAsString(),
                    obj.get("private_key").getAsString()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
