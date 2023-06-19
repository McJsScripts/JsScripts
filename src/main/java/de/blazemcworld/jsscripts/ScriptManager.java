package de.blazemcworld.jsscripts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ScriptManager {

    public static final Path modDir = JsScripts.MC.runDirectory.toPath()
            .resolve("JsScripts");
    public static final File config = modDir.resolve("jsscripts.json").toFile();
    public static Context ctx;
    private static int importCounter = 0;
    private static final List<Runnable> disableHooks = new ArrayList<>();
    public static HashSet<String> loadedScripts = new HashSet<>();
    public static JsonObject configData = null;

    public static void init() {
        ClientLifecycleEvents.CLIENT_STOPPING.register((e) -> shutdown());
        loadScripts();
    }

    public static void loadScripts() {
        ctx = Context.newBuilder()
                .allowAllAccess(true)
                .fileSystem(new ScriptFS())
                .build();
        if (!modDir.resolve("scripts").toFile().exists()) {
            modDir.resolve("scripts").toFile().mkdirs();
        }

        injectMappings();
        if (!config.exists()) {
            try {
                Files.writeString(config.toPath(), """
                        {
                            "enabled_scripts": []
                        }
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try {
            configData = JsonParser.parseString(Files.readString(config.toPath())).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (JsonElement elm : configData.get("enabled_scripts").getAsJsonArray()) {
            try {
                importCounter++;
                ctx.eval(Source.newBuilder("js", """
                                import * as _%s from "%s";
                                """.formatted(importCounter, elm.getAsString()), "loader" + importCounter + ".js")
                        .mimeType("application/javascript+module")
                        .build());
            } catch (Exception err) {
                JsScripts.LOGGER.error("Error initializing " + elm.getAsString());
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
        for (Runnable hook : disableHooks) {
            try {
                hook.run();
            } catch (Exception err) {
                err.printStackTrace();
            }
        }
        disableHooks.clear();
        if (ctx != null) {
            ctx.close();
            ctx = null;
        }
        importCounter = 0;
        loadedScripts.clear();
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

    @SuppressWarnings("unused")
    public static void onDisable(Runnable hook) {
        disableHooks.add(hook);
    }
}
