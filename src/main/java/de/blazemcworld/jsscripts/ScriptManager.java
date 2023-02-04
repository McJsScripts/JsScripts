package de.blazemcworld.jsscripts;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ScriptManager {

    public static final List<Script> scripts = new ArrayList<>();
    private static final File scriptDir = JsScripts.MC.runDirectory.toPath()
            .resolve("JsScripts").resolve("scripts").toFile();
    private static final HashMap<String, Value> exports = new HashMap<>();
    private static final HashMap<String, List<Runnable>> exportCallbacks = new HashMap<>();

    public static void init() {
        ClientLifecycleEvents.CLIENT_STOPPING.register((e) -> shutdown());
        loadScripts();
    }

    public static void loadScripts() {
        if (!scriptDir.exists()) scriptDir.mkdirs();

        injectMappings();
        loadScriptsIn(scriptDir);
    }

    private static void loadScriptsIn(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                loadScriptsIn(file);
                continue;
            }
            if (!file.getName().endsWith(".js")) {
                continue;
            }
            try {
                scripts.add(new Script(file));
            } catch (Exception err) {
                JsScripts.LOGGER.error("Error initializing " + file);
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
            s.close();
        }
        scripts.clear();
        exports.clear();
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
                InsnList instructions = new InsnList();

                instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "de/blazemcworld/jsscripts/Mappings", "graalRemapMethod", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/String;"));
                instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));

                method.instructions.insertBefore(method.instructions.getFirst(), instructions);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void saveValue(String identifier, Value obj) {
        if (exports.containsKey(identifier)) {
            JsScripts.LOGGER.warn(identifier + " exported already! Overwriting.");
        }
        exports.put(identifier, obj);
        if (exportCallbacks.containsKey(identifier)) {
            List<Runnable> callbacks = exportCallbacks.remove(identifier);
            for (Runnable cb : callbacks) {
                cb.run();
            }
        }
    }

    public static Value loadValue(String identifier, Context ctx) {
        return ctx.getBindings("js").getMember("Promise").newInstance((BiConsumer<Function<Object[], Object>, Function<Object[], Object>>) (resolve, reject) -> {
            if (exports.containsKey(identifier)) {
                resolve.apply(new Object[]{exports.get(identifier)});
                return;
            }
            if (!exportCallbacks.containsKey(identifier)) {
                exportCallbacks.put(identifier, new ArrayList<>());
            }
            exportCallbacks.get(identifier).add(() -> resolve.apply(new Object[]{exports.get(identifier)}));
        });
    }
}
