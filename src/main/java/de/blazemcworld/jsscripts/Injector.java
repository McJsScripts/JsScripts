package de.blazemcworld.jsscripts;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.spongepowered.asm.transformers.MixinClassWriter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class Injector {
    private static final Set<String> modified = new HashSet<>();
    private static final Instrumentation inst = ByteBuddyAgent.getInstrumentation();
    private static final List<ClassFileTransformer> transformers = new ArrayList<>();
    private static final List<InjectionCallback> callbacks = new ArrayList<>();
    private static List<Object> callbackInfo = new ArrayList<>();

    @SuppressWarnings("unused")
    public static int registerCallback(InjectionCallback cb) {
        callbacks.add(cb);
        return callbacks.size() - 1;
    }

    @SuppressWarnings("unused")
    public static void addCallbackInfo(Object info) {
        callbackInfo.add(info);
    }

    @SuppressWarnings("unused")
    public static Object invokeCallback(int id) {
        List<Object> info = callbackInfo;
        callbackInfo = new ArrayList<>();
        return callbacks.get(id).invoke(info);
    }

    public static void transformMethod(String className, String methodName, Consumer<MethodNode> transformer) {
        transform(className, (clazz) -> {
            boolean found = false;

            String remappedName = Mappings.remapMethod("named", className, "named", Mappings.current(), methodName);

            for (MethodNode method : clazz.methods) {
                if (Objects.equals(method.name, remappedName)) {
                    found = true;
                    transformer.accept(method);
                }
            }

            if (!found) {
                JsScripts.LOGGER.warn("No method to transform found.");
            }
        });
    }

    public static void transform(String className, Consumer<ClassNode> transformer) {
        transformBytes(className, (bytes) -> {
            try {
                ClassNode node = bytes2node(bytes);
                transformer.accept(node);
                return node2bytes(node);
            } catch (Exception err) {
                err.printStackTrace();
                return null;
            }
        });
    }

    public static void transformBytes(String className, Function<byte[], byte[]> transformer) {
        try {
            className = Mappings.remapClass("named", Mappings.current(), className);
            String slashName = className.replace('.', '/');

            Class.forName(className); //ensure class is loaded

            ClassFileTransformer classFileTransformer = new ClassFileTransformer() {
                @Override
                public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                    if (!Objects.equals(slashName, className)) return null;
                    byte[] out = transformer.apply(classfileBuffer);

                    if (out != null) {
                        StringWriter sw = new StringWriter();
                        CheckClassAdapter.verify(new ClassReader(out), JsScripts.class.getClassLoader(), false, new PrintWriter(sw));
                        if (sw.toString().length() > 0) {
                            JsScripts.LOGGER.warn(sw);
                        }
                    }

                    return out;
                }
            };

            inst.addTransformer(classFileTransformer, true);
            transformers.add(classFileTransformer);

            inst.retransformClasses(Class.forName(className));

            modified.add(className);
        } catch (Exception err) {
            JsScripts.LOGGER.error("Error transforming " + className);
            err.printStackTrace();
        }
    }

    public static void reset() {
        for (ClassFileTransformer transformer : transformers) {
            inst.removeTransformer(transformer);
        }
        transformers.clear();

        for (String name : modified) {
            try {
                inst.retransformClasses(Class.forName(name));
            } catch (Exception err) {
                JsScripts.LOGGER.error("Error resetting " + name + " to its original state");
                err.printStackTrace();
            }
        }
        modified.clear();
        callbacks.clear();
    }

    private static ClassNode bytes2node(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static byte[] node2bytes(ClassNode node) {
        ClassWriter writer = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static Class<?>[] listLoadedClasses() {
        return inst.getAllLoadedClasses();
    }
}
