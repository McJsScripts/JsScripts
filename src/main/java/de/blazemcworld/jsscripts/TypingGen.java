package de.blazemcworld.jsscripts;

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

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypingGen {

    public static void genTypesIn(String targets) {
        try {
            Path out = JsScripts.MC.runDirectory.toPath()
                    .resolve("JsScripts").resolve("types");

            JsScripts.displayChat(Text.literal("Scanning available classes...").formatted(Formatting.AQUA));

            ClassLoader cl = JsScripts.class.getClassLoader();

            Set<String> all = new HashSet<>();

            for (String target : targets.split(" ")) {
                if (!target.endsWith("*")) {
                    all.add(Mappings.remapClass("named", Mappings.current(), target));
                }
            }

            Set<String> checkedPackages = new HashSet<>();

            List<FileSystem> fsList = new ArrayList<>();

            for (Class<?> c : Injector.listLoadedClasses()) {
                String p = c.getPackageName();
                while (true) {
                    if (checkedPackages.contains(p)) break;
                    checkedPackages.add(p);

                    Enumeration<URL> enu = cl.getResources(p.replace('.', '/'));

                    while (enu.hasMoreElements()) {
                        URI u = enu.nextElement().toURI();

                        try {
                            FileSystems.getFileSystem(u);
                        } catch (FileSystemNotFoundException err) {
                            HashMap<String, Boolean> options = new HashMap<>();
                            options.put("create", true);
                            fsList.add(FileSystems.newFileSystem(u, options));
                        }

                        try (Stream<Path> javaClasses = Files.walk(Path.of(u))) {
                            for (Path cp : javaClasses.collect(Collectors.toSet())) {
                                String str = cp.toString();
                                if (str.endsWith(".class")) {
                                    all.add(str.substring(1).replaceAll("/", ".").substring(0, str.length() - 7));
                                }
                            }
                        }
                    }

                    if (!p.contains(".")) {
                        break;
                    }
                    p = p.substring(0, p.indexOf("."));
                }
            }

            for (FileSystem fs : fsList) {
                fs.close();
            }

            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));

            Stream<Path> javaClasses = Files.walk(fs.getPath("/"));
            for (Path path : javaClasses.collect(Collectors.toSet())) {
                List<String> parts = new ArrayList<>(Arrays.asList(path.toString().split("/")));

                if (parts.size() > 3) {
                    parts = parts.subList(3, parts.size());
                    if (parts.get(parts.size() - 1).endsWith(".class")) {
                        String str = String.join(".", parts);
                        all.add(str.substring(0, str.length() - 6));
                    }
                }
            }
            javaClasses.close();

            JsScripts.displayChat(Text.literal("Found " + all.size() + " classes!").formatted(Formatting.AQUA));

            all = all.stream().filter(name -> {
                if (name.startsWith("jdk")) return false;
                if (!name.contains(".")) return false;

                for (String target : targets.split(" ")) {
                    if (target.endsWith("*") && name.startsWith(target.substring(0, target.length() - 1))) {
                        return true;
                    }
                    if (target.equals(name)) {
                        return true;
                    }
                }

                return false;
            }).collect(Collectors.toSet());

            JsScripts.displayChat(Text.literal("Of which " + all.size() + " classes match the filter.").formatted(Formatting.AQUA));

            JsScripts.displayChat(Text.literal("Starting generation...").formatted(Formatting.AQUA));

            long nextProgress = System.currentTimeMillis() + 3000;
            long start = System.currentTimeMillis();
            int progress = 0;
            int errors = 0;

            for (String currentName : all) {
                if (nextProgress < System.currentTimeMillis()) {
                    nextProgress = System.currentTimeMillis() + 3000;
                    String est = "%.1fs".formatted((float) (all.size() - progress) / ((float) progress / (System.currentTimeMillis() - start)) / 1000f);
                    JsScripts.displayChat(Text.literal("Generating... " + progress + " of " + all.size() + " completed, est: " + est).formatted(Formatting.AQUA));
                }
                try {
                    genTypesFor(currentName, out, cl);
                } catch (Exception e) {
                    e.printStackTrace();
                    errors++;
                }
                progress++;
            }

            JsScripts.displayChat(Text.literal("Done!").formatted(Formatting.AQUA));

            if (errors > 0) {
                JsScripts.displayChat(Text.literal("Got an error for " + errors + " classes.").formatted(Formatting.AQUA));
            }
        } catch (Exception err) {
            JsScripts.displayChat(Text.literal("Error generating type declarations").formatted(Formatting.RED));
            err.printStackTrace();
        }
    }

    private static void genTypesFor(String currentName, Path outDir, ClassLoader cl) throws Exception {
        currentName = Mappings.remapClass("named", Mappings.current(), currentName);
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
