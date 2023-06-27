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
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypingGen {

    private static final Pattern remapAsmPattern = Pattern.compile("net/minecraft/class_\\d+(\\.(method|field)_\\d+)?");

    public static void genTypesIn(String targets) {
        try {
            Path out = JsScripts.MC.runDirectory.toPath()
                    .resolve("JsScripts");

            if (!out.toFile().exists()) {
                out.toFile().mkdirs();
            }

            Files.writeString(out.resolve("jsconfig.json"), """
                    {
                        "compilerOptions": {
                            "paths": {
                                "$*": ["./types/*"],
                                "#*": ["./scripts/*"]
                            }
                        }
                    }
                    """);

            out = out.resolve("types");

            if (!out.toFile().exists()) {
                out.toFile().mkdirs();
            }

            JsScripts.displayChat(Text.literal("Scanning available classes...").formatted(Formatting.AQUA));

            ClassLoader cl = JsScripts.class.getClassLoader();

            Set<String> all = new HashSet<>();
            Set<String> forced = new HashSet<>();

            Set<String> optionFlags = new HashSet<>();

            boolean needsScan = false;

            for (String target : targets.split(" ")) {
                if (target.endsWith("*")) {
                    needsScan = true;
                    continue;
                }
                if (target.startsWith("-")) {
                    optionFlags.add(target.substring(1).toLowerCase());
                    continue;
                }
                forced.add(Mappings.remapClass("named", Mappings.current(), target));
            }

            if (needsScan) {
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

                JsScripts.displayChat(Text.literal("Found " + (all.size() + forced.size()) + " classes!").formatted(Formatting.AQUA));

                all = all.stream().filter(name -> {
                    if (name.startsWith("jdk")) return false;
                    if (!name.contains(".")) return false;

                    for (String target : targets.split(" ")) {
                        if (target.endsWith("*") && name.startsWith(target.substring(0, target.length() - 1))) {
                            return true;
                        }
                    }

                    return false;
                }).collect(Collectors.toSet());

                all.addAll(forced);
                JsScripts.displayChat(Text.literal("Of which " + all.size() + " classes match the filter.").formatted(Formatting.AQUA));
            } else {
                all.addAll(forced);
                JsScripts.displayChat(Text.literal("Got " + all.size() + " classes.").formatted(Formatting.AQUA));
            }

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
                    genTypesFor(currentName, out, cl, optionFlags);
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

    private static void genTypesFor(String currentName, Path outDir, ClassLoader cl, Set<String> optionFlags) throws Exception {
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
            if ((field.access & Opcodes.ACC_PUBLIC) == 0 && !optionFlags.contains("private")) {
                continue;
            }

            fields.add("%s%s%s: %s;".formatted(
                    ((field.access & Opcodes.ACC_PUBLIC) == 0) ? "private " : "",
                    ((field.access & Opcodes.ACC_STATIC) != 0) ? "static " : "",
                    Mappings.remapField("named", namedName, Mappings.current(), "named", field.name),
                    parseSingleDescriptor(field.desc, imports)
            ));
        }

        for (MethodNode method : classNode.methods) {
            if (((method.access & Opcodes.ACC_PUBLIC) == 0 || method.name.equals("<clinit>")) && !optionFlags.contains("private")) {
                continue;
            }
            List<String> params = new ArrayList<>();

            Pair<List<String>, String> info = parseMethodDescriptor(method.desc, imports);

            for (int i = 0; i < info.getLeft().size(); i++) {
                params.add("arg%s: %s".formatted(i, info.getLeft().get(i)));
            }

            if (method.name.equals("<init>")) {
                methods.add("constructor(%s); %s".formatted(
                        String.join(", ", params),
                        optionFlags.contains("asm") ? "/*" + getAsm(method, optionFlags.contains("remap")) + "*/" : ""
                ));
            } else if (method.name.equals("<clinit>")) {
                methods.add("/*<clinit>%s*/".formatted(
                        optionFlags.contains("asm") ? getAsm(method, optionFlags.contains("remap")) : ""
                ));
            } else {
                String displayName = Mappings.remapMethod("named", namedName, Mappings.current(), "named", method.name);

                StringBuilder comment = new StringBuilder();

                if (!displayName.equals(method.name)) {
                    comment.append(method.name);
                }
                if (optionFlags.contains("asm")) {
                    if (!comment.isEmpty()) {
                        comment.append(" ");
                    }
                    comment.append(getAsm(method, optionFlags.contains("remap")));
                }

                methods.add("%s%s%s(%s): %s;%s".formatted(
                        ((method.access & Opcodes.ACC_PUBLIC) == 0) ? "private " : "",
                        ((method.access & Opcodes.ACC_STATIC) != 0) ? "static " : "",
                        displayName,
                        String.join(", ", params),
                        info.getRight(),
                        comment.length() == 0 ? "" : "/*" + comment + "*/"
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

    private static String getAsm(MethodNode method, boolean remap) {
        Textifier t = new Textifier();
        TraceMethodVisitor tmv = new TraceMethodVisitor(t);
        method.accept(tmv);
        StringWriter sw = new StringWriter();
        t.print(new PrintWriter(sw));
        StringBuilder out = new StringBuilder();

        if (remap) {
            Matcher m = remapAsmPattern.matcher(sw.toString());

            while (m.find()) {
                if (m.group().contains(".")) {
                    String[] parts = m.group().split("\\.");
                    parts[1] = Mappings.remapField(Mappings.current(), parts[0], Mappings.current(), "named", parts[1]);
                    parts[1] = Mappings.remapMethod(Mappings.current(), parts[0], Mappings.current(), "named", parts[1]);
                    parts[0] = Mappings.remapClass(Mappings.current(), "named", parts[0]).replace('.', '/');

                    m.appendReplacement(out, parts[0] + "." + parts[1]);
                } else {
                    m.appendReplacement(out, Mappings.remapClass(Mappings.current(), "named", m.group()).replace('.','/'));
                }
            }
            m.appendTail(out);
        } else {
            out.append(sw);
        }

        return " {\n" + out + "    \n    }";
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
