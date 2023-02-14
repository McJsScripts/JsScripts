package de.blazemcworld.jsscripts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScriptManager {

    public static final List<Script> scripts = new ArrayList<>();
    public static final File scriptDir = JsScripts.MC.runDirectory.toPath()
            .resolve("JsScripts").resolve("scripts").toFile();
    private static final File config = JsScripts.MC.runDirectory.toPath()
            .resolve("JsScripts").resolve("jsscripts.json").toFile();
    private static JsonObject configData;
    private static final List<UnknownScript> pending = new ArrayList<>();

    public static void init() {
        ClientLifecycleEvents.CLIENT_STOPPING.register((e) -> shutdown());
        loadScripts();
    }

    public static void loadScripts() {
        if (!scriptDir.exists()) scriptDir.mkdirs();

        injectMappings();
        if (!config.exists()) {
            try {
                Files.writeString(config.toPath(), """
                        {
                            "trusted_signatures": [
                                "%s"
                            ],
                            "loaded_scripts": [],
                            "dev_scripts": [],
                            "path_aliases": []
                        }
                        """.formatted(Crypt.getKeyPair().getLeft()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try {
            configData = JsonParser.parseString(Files.readString(config.toPath())).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (JsonElement elm : configData.get("loaded_scripts").getAsJsonArray()) {
            File f = scriptDir.toPath().resolve(elm.getAsString()).toFile();
            if (f.exists()) {
                try {
                    scripts.add(new Script(f, false));
                } catch (Exception err) {
                    JsScripts.LOGGER.error("Error initializing " + f);
                    err.printStackTrace();
                }
            } else {
                JsScripts.LOGGER.warn(f + " specified in config, but doesn't exist!");
            }
        }

        for (JsonElement elm : configData.get("dev_scripts").getAsJsonArray()) {
            File f = scriptDir.toPath().resolve(elm.getAsString()).toFile();
            if (f.exists()) {
                if (f.isDirectory()) {
                    loadDevScriptsIn(f);
                } else {
                    try {
                        scripts.add(new Script(f, true));
                    } catch (Exception err) {
                        JsScripts.LOGGER.error("Error initializing " + f);
                        err.printStackTrace();
                    }
                }
            } else {
                JsScripts.LOGGER.warn(f + " specified in config, but doesn't exist!");
            }
        }

        resolvePending:
        while (!pending.isEmpty()) {
            UnknownScript p = pending.remove(0);
            try {
                if (p.source().startsWith("file:")) {
                    File target = Path.of(URI.create(p.source())).toFile();
                    for (Script s : scripts) {
                        if (s.getFile().equals(target)) {
                            p.cb().accept(s);
                            continue resolvePending;
                        }
                    }
                    try {
                        Script newScript = new Script(target, p.trusted());
                        scripts.add(newScript);
                        p.cb().accept(newScript);
                    } catch (Exception err) {
                        JsScripts.LOGGER.error("Error initializing " + target);
                        err.printStackTrace();
                    }
                } else if (p.source().startsWith("https:")) {
                    for (Script s : scripts) {
                        if (s.getHash().equals(p.hash())) {
                            p.cb().accept(s);
                            continue resolvePending;
                        }
                    }

                    for (File f : availableScripts()) {
                        if (Crypt.hash(Files.readString(f.toPath())).equals(p.hash())) {
                            pending.add(new UnknownScript(f.toURI().toString(), null, p.cb(), false));
                            continue resolvePending;
                        }
                    }
                    try (InputStream stream = new URL(p.source()).openStream()) {
                        String source = new String(stream.readAllBytes());
                        String remoteHash = Crypt.hash(source);
                        if (!Objects.equals(remoteHash, p.hash())) {
                            throw new Exception("Remote content does not match expected hash! Remote: " + remoteHash + " Expected: " + p.hash());
                        }

                        String preferredName = p.source().substring(p.source().lastIndexOf("/") + 1);

                        if (!preferredName.matches("^[\\w.-]{3,50}$")) {
                            preferredName = "dependency";
                        }
                        if (preferredName.endsWith(".js")) {
                            preferredName = preferredName.substring(0, preferredName.length() - 3);
                        }

                        if (scriptDir.toPath().resolve(preferredName + ".js").toFile().exists()) {
                            int c = 1;
                            while (scriptDir.toPath().resolve(preferredName + c + ".js").toFile().exists()) {
                                c++;
                            }
                            preferredName += c + ".js";
                        } else {
                            preferredName += ".js";
                        }

                        Path path = scriptDir.toPath().resolve(preferredName);
                        Files.writeString(path, source);
                        pending.add(new UnknownScript(path.toAbsolutePath().toUri().toString(), null, p.cb(), false));
                    }
                } else {
                    throw new Exception("Unsupported protocol for " + p.source());
                }
            } catch (Exception e) {
                JsScripts.LOGGER.error("Error loading from " + p.source());
                e.printStackTrace();
            }
        }
    }

    private static void loadDevScriptsIn(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                loadDevScriptsIn(f);
                continue;
            }
            try {
                scripts.add(new Script(f, true));
            } catch (Exception err) {
                JsScripts.LOGGER.error("Error initializing " + f);
                err.printStackTrace();
            }
        }
    }

    public static void reload() {
        shutdown();
        Injector.reset();
        loadScripts();
    }

    public static void shutdown() {
        for (Script s : scripts) {
            if (s.onDisable != null) {
                try {
                    s.onDisable.run();
                } catch (Exception err) {
                    JsScripts.LOGGER.error("Error shutting down " + s.getFile());
                    err.printStackTrace();
                }
            }
        }
        for (Script s : scripts) {
            s.close();
        }
        scripts.clear();
    }

    private static void injectMappings() {
        Injector.transformMethod("com.oracle.truffle.host.HostContext", "findClassImpl", method -> {
            try {
                InsnList instructions = new InsnList();

                instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "de/blazemcworld/jsscripts/Mappings", "graalRemapClass", "(Ljava/lang/String;)Ljava/lang/String;"));
                instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));

                method.instructions.insertBefore(method.instructions.getFirst(), instructions);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Injector.transformMethod("com.oracle.truffle.host.HostInteropReflect", "findField", method -> {
            try {
                InsnList instructions = new InsnList();

                instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "de/blazemcworld/jsscripts/Mappings", "graalRemapField", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/String;"));
                instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));

                method.instructions.insertBefore(method.instructions.getFirst(), instructions);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Injector.transformMethod("com.oracle.truffle.host.HostInteropReflect", "findMethod", method -> {
            try {
                method.instructions.clear();
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
                method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "de/blazemcworld/jsscripts/Mappings", "graalRemapOverloadedMethod", "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Z)Ljava/lang/Object;"));
                method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "com/oracle/truffle/host/HostMethodDesc"));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static boolean isTrusted(String data, String signature) {
        for (JsonElement elm : configData.get("trusted_signatures").getAsJsonArray()) {
            if (Crypt.verify(data, signature, elm.getAsString())) {
                return true;
            }
        }
        return false;
    }

    public static void addUnknown(String source, String hash, Consumer<Script> cb, boolean trusted) {
        pending.add(new UnknownScript(source, hash, cb, trusted));
    }

    public static List<File> availableScripts() throws IOException {
        List<File> available = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(scriptDir.toPath())) {
            for (Path p : stream.collect(Collectors.toSet())) {
                if (Files.isRegularFile(p) && p.toString().endsWith(".js")) {
                    available.add(p.toFile());
                }
            }
        }
        return available;
    }

    public static String resolveAliases(String source) {
        for (JsonElement elm : configData.get("path_aliases").getAsJsonArray()) {
            String prefix = elm.getAsJsonObject().get("prefix").getAsString();
            String target = elm.getAsJsonObject().get("target").getAsString();
            if (source.startsWith(prefix)) {
                source = target + source.substring(prefix.length());
                break;
            }
        }
        return source;
    }
}
