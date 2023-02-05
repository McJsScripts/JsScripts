package de.blazemcworld.jsscripts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TypingGen {

    public static void genAllTypes() {
        try {
            Path out = JsScripts.MC.runDirectory.toPath()
                    .resolve("JsScripts").resolve("types");

            if (out.toFile().exists()) {
                JsScripts.displayChat(Text.literal("Deleting previous typings...").formatted(Formatting.AQUA));
                deleteRecursively(out.toFile());
                JsScripts.displayChat(Text.literal("Done!").formatted(Formatting.AQUA));
            }

            JsScripts.displayChat(Text.literal("Scanning available classes...").formatted(Formatting.AQUA));
            Reflections r = new Reflections(new ConfigurationBuilder()
                    .forPackages("com", "net", "java", "org", "io", "de")
                    .addClassLoaders(MinecraftClient.class.getClassLoader(), TypingGen.class.getClassLoader())
                    .addScanners(Scanners.MethodsReturn, Scanners.SubTypes, Scanners.MethodsParameter));

            Set<String> all = new HashSet<>();

            all.addAll(r.getStore().get("SubTypes").keySet());
            all.addAll(r.getStore().get("MethodsReturn").keySet());
            all.addAll(r.getStore().get("MethodsParameter").keySet());

            JsScripts.displayChat(Text.literal("Found " + all.size() + " classes!").formatted(Formatting.AQUA));

            JsScripts.displayChat(Text.literal("Starting generation...").formatted(Formatting.AQUA));

            long nextProgress = System.currentTimeMillis() + 3000;
            long start = System.currentTimeMillis();
            int progress = 0;

            ClassLoader cl = JsScripts.class.getClassLoader();

            for (String currentName : all) {
                if (nextProgress < System.currentTimeMillis()) {
                    nextProgress = System.currentTimeMillis() + 3000;
                    String est = "%.1fs".formatted((float) (all.size() - progress) / ((float) progress / (System.currentTimeMillis() - start)) / 1000f);
                    JsScripts.displayChat(Text.literal("Generating... " + progress + " of " + all.size() + " completed, est: " + est).formatted(Formatting.AQUA));
                }
                if (currentName.startsWith("jdk.") || !currentName.contains(".") || currentName.endsWith("[]")) {
                    progress++;
                    continue;
                }
                try {
                    genTypesFor(currentName, out, cl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                progress++;
            }

            JsScripts.displayChat(Text.literal("Done!").formatted(Formatting.AQUA));
        } catch (Exception err) {
            JsScripts.displayChat(Text.literal("Error generating type declarations").formatted(Formatting.RED));
            err.printStackTrace();
        }
    }

    public static void genTypesFor(String className) throws Exception {
        Path out = JsScripts.MC.runDirectory.toPath()
                .resolve("JsScripts").resolve("types");
        ClassLoader cl = JsScripts.class.getClassLoader();
        className = Mappings.remapClass("named",Mappings.current(), className);
        genTypesFor(className, out, cl);
    }

    private static void genTypesFor(String currentName, Path outDir, ClassLoader cl) throws Exception {
        InputStream stream = cl.getResourceAsStream(currentName.replace('.', '/') + ".class");
        if (stream == null) {
            stream = Class.forName(currentName).getClassLoader().getResourceAsStream(currentName.replace('.', '/') + ".class");
            if (stream == null) {
                throw new Exception("Unable to find class bytes for " + currentName);
            }
        }

        byte[] bytes = IOUtils.toByteArray(stream);

        ClassReader reader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        String namedName = Mappings.remapClass(Mappings.current(), "named", currentName);
        String[] namedParts = namedName.split("\\.");

        Path classPath = outDir;

        for (String part : namedParts) {
            classPath = classPath.resolve(part);
        }

        HashMap<String, String> imports = new HashMap<>();
        Set<String> fields = new HashSet<>();
        Set<String> methods = new HashSet<>();

        String className = namedParts[namedParts.length - 1];
        String extendsProp = "";

        if (classNode.superName != null) {
            extendsProp = " extends " + typeImport(classNode.superName, imports);
        }

        for (FieldNode field : classNode.fields) {
            if ((field.access & Opcodes.ACC_PUBLIC) == 0) {
                continue;
            }

            fields.add("%s%s: %s;".formatted(
                    ((field.access & Opcodes.ACC_STATIC) != 0) ? "static " : "",
                    Mappings.remapField("named", namedName, Mappings.current(), "named", field.name),
                    parseSingleDescriptor(field.desc, imports)
            ));
        }

        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_PUBLIC) == 0 || method.name.equals("<clinit>")) {
                continue;
            }
            List<String> params = new ArrayList<>();

            Pair<List<String>, String> info = parseMethodDescriptor(method.desc, imports);

            for (int i = 0; i < info.getLeft().size(); i++) {
                params.add("arg%s: %s".formatted(i, info.getLeft().get(i)));
            }

            if (method.name.equals("<init>")) {
                methods.add("constructor(%s);".formatted(
                        String.join(", ", params)
                ));
            } else {
                String displayName = Mappings.remapMethod("named", namedName, Mappings.current(), "named", method.name);

                methods.add("%s%s(%s): %s;%s".formatted(
                        ((method.access & Opcodes.ACC_STATIC) != 0) ? "static " : "",
                        displayName,
                        String.join(", ", params),
                        info.getRight(),
                        displayName.equals(method.name) ? "" : " //" + method.name
                ));
            }
        }

        File folder = classPath.toFile().getParentFile();
        if (!folder.exists()) {
            folder.mkdirs();
        }

        Files.writeString(Path.of(classPath.toAbsolutePath() + ".d.ts"), """
                %s
                export default class %s%s {
                    %s
                    %s
                }
                """.formatted(
                String.join("\n", imports.entrySet().stream().map((e) -> {
                    String path = "../".repeat(namedParts.length - 1) +
                            Mappings.remapClass(Mappings.current(), "named", e.getKey())
                                    .replace('.', '/');

                    return "import %s from \"%s\";".formatted(e.getValue(), path);
                }).collect(Collectors.toSet())),
                className,
                extendsProp,
                String.join("\n    ", fields),
                String.join("\n    ", methods)
        ));
    }

    private static String parseSingleDescriptor(String descriptor, HashMap<String, String> imports) throws Exception {
        Type t = Type.getType(descriptor);

        if (t == Type.INT_TYPE
                || t == Type.SHORT_TYPE
                || t == Type.BYTE_TYPE
                || t == Type.DOUBLE_TYPE
                || t == Type.FLOAT_TYPE
                || t == Type.LONG_TYPE) {
            return "number";
        }

        if (t == Type.VOID_TYPE) {
            return "void";
        }

        if (t == Type.BOOLEAN_TYPE) {
            return "boolean";
        }

        if (t == Type.CHAR_TYPE) {
            return "string";
        }

        if (t.getSort() == Type.OBJECT) {
            if (t.getDescriptor().equals("Ljava/lang/String;")) {
                return "string";
            }
            return typeImport(t.getDescriptor(), imports);
        }

        if (t.getSort() == Type.ARRAY) {
            return "Array<" + parseSingleDescriptor(t.getDescriptor().substring(1), imports) + ">";
        }

        throw new Exception("Unable to parse field descriptor: " + descriptor);
    }

    private static Pair<List<String>, String> parseMethodDescriptor(String descriptor, HashMap<String, String> imports) throws Exception {
        Type t = Type.getType(descriptor);

        List<String> params = new ArrayList<>();

        for (Type p : t.getArgumentTypes()) {
            params.add(parseSingleDescriptor(p.getDescriptor(), imports));
        }

        String out = parseSingleDescriptor(t.getReturnType().getDescriptor(), imports);
        return new Pair<>(params, out);
    }

    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }

    private static String typeImport(String clazz, HashMap<String, String> imports) {
        if (clazz.startsWith("L") && clazz.endsWith(";")) {
            clazz = clazz.substring(1, clazz.length() - 1);
        }
        clazz = clazz.replace('/', '.');
        if (!imports.containsKey(clazz)) {
            int c = 0;
            String[] nameParts = Mappings.remapClass(Mappings.current(), "named", clazz).split("\\.");
            while (imports.containsValue("$" + c + "_" + nameParts[nameParts.length - 1])) {
                c++;
            }
            imports.put(clazz, "$" + c + "_" + nameParts[nameParts.length - 1]);
        }
        return imports.get(clazz);
    }
}
