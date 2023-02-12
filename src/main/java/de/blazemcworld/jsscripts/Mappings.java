package de.blazemcworld.jsscripts;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.mapping.tree.*;
import net.minecraft.MinecraftVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Mappings {

    private static final TinyTree MAPPINGS = loadMappings();

    private static TinyTree loadMappings() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            JsScripts.LOGGER.info("Skipped Mappings (DevEnv)");
            return TinyMappingFactory.EMPTY_TREE;
        }
        try {
            String yarnVersion = getYarnVersion();

            File mappings = JsScripts.MC.runDirectory.toPath()
                    .resolve("JsScripts").resolve("mappings").resolve(yarnVersion + ".tiny").toFile();

            if (!mappings.getParentFile().exists()) {
                mappings.getParentFile().mkdirs();
            }

            if (!mappings.exists()) {
                downloadMappings(mappings, yarnVersion);
            }

            return TinyMappingFactory.loadWithDetection(new BufferedReader(new FileReader(mappings)));
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
    }

    private static String getYarnVersion() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://meta.fabricmc.net/v2/versions/yarn/" + MinecraftVersion.create().getName()))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        return JsonParser.parseString(res.body()).getAsJsonArray().get(0).getAsJsonObject().get("version").getAsString();
    }

    private static void downloadMappings(File output, String yarnVersion) throws Exception {
        URL url = new URL(
                "https://maven.fabricmc.net/net/fabricmc/yarn/" + yarnVersion + "/yarn-"
                        + yarnVersion + ".jar");
        try (InputStream in = url.openStream()) {
            try (ZipInputStream zin = new ZipInputStream(in)) {
                ZipEntry entry = zin.getNextEntry();
                while (entry != null) {
                    if (entry.getName().equals("mappings/mappings.tiny")) {
                        Files.copy(zin, output.toPath());
                        return;
                    }
                    entry = zin.getNextEntry();
                }
            }
        }
        throw new RuntimeException("Could not find mappings in yarn jar.");
    }

    private static final String current = FabricLauncherBase.getLauncher().getMappingConfiguration().getTargetNamespace();

    private static ClassDef getClass(String namespace, String name) {
        name = name.replace('.', '/');

        for (ClassDef def : MAPPINGS.getClasses()) {
            if (def.getName(namespace).equals(name)) {
                return def;
            }
        }

        return null;
    }

    private static FieldDef getField(ClassDef classDef, String namespace, String name) {
        for (FieldDef def : classDef.getFields()) {
            if (def.getName(namespace).equals(name)) {
                return def;
            }
        }

        try {
            Class<?> parent = Class.forName(classDef.getName(current()).replace('/', '.')).getSuperclass();
            if (parent != null) {
                ClassDef def = getClass(current(), parent.getName());
                if (def != null) {
                    return getField(def, namespace, name);
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private static MethodDef getMethod(ClassDef classDef, String namespace, String name) {
        for (MethodDef def : classDef.getMethods()) {
            if (def.getName(namespace).equals(name)) {
                return def;
            }
        }

        try {
            Class<?> currentClass = Class.forName(classDef.getName(current()).replace('/', '.'));
            Class<?> parent = currentClass.getSuperclass();
            if (parent != null) {
                ClassDef def = getClass(current(), parent.getName());
                if (def != null) {
                    MethodDef res = getMethod(def, namespace, name);
                    if (res != null) {
                        return res;
                    }
                }
            }
            for (Class<?> iface : currentClass.getInterfaces()) {
                ClassDef def = getClass(current(), iface.getName());
                if (def != null) {
                    MethodDef res = getMethod(def, namespace, name);
                    if (res != null) {
                        return res;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private static Set<MethodDef> getOverloadedMethod(ClassDef classDef, String namespace, String name) {
        Set<MethodDef> out = new HashSet<>();
        for (MethodDef def : classDef.getMethods()) {
            if (def.getName(namespace).equals(name)) {
                out.add(def);
            }
        }

        try {
            Class<?> currentClass = Class.forName(classDef.getName(current()).replace('/', '.'));
            Class<?> parent = currentClass.getSuperclass();
            if (parent != null) {
                ClassDef def = getClass(current(), parent.getName());
                if (def != null) {
                    out.addAll(getOverloadedMethod(def, namespace, name));
                }
            }
            for (Class<?> iface : currentClass.getInterfaces()) {
                ClassDef def = getClass(current(), iface.getName());
                if (def != null) {
                    out.addAll(getOverloadedMethod(def, namespace, name));
                }
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        return out;
    }

    public static String current() {
        return current;
    }

    public static String remapClass(String from, String to, String name) {
        ClassDef classDef = Mappings.getClass(from, name);
        if (classDef == null) return name;
        String res = classDef.getName(to);
        if (res == null) return name;
        return res.replace('/', '.');
    }

    public static String remapField(String classNamespace, String className, String from, String to, String name) {
        ClassDef classDef = Mappings.getClass(classNamespace, className);
        if (classDef == null) return name;
        FieldDef fieldDef = getField(classDef, from, name);
        if (fieldDef == null) return name;
        String res = fieldDef.getName(to);
        if (res == null) return name;
        return res;
    }

    public static String remapMethod(String classNamespace, String className, String from, String to, String name) {
        ClassDef classDef = Mappings.getClass(classNamespace, className);
        if (classDef == null) return name;
        MethodDef methodDef = getMethod(classDef, from, name);
        if (methodDef == null) return name;
        String res = methodDef.getName(to);
        if (res == null) return name;
        return res;
    }

    public static Set<String> remapOverloadedMethod(String classNamespace, String className, String from, String to, String name) {
        ClassDef classDef = Mappings.getClass(classNamespace, className);
        if (classDef == null) return new HashSet<>(Set.of(name));
        Set<MethodDef> methodDefs = getOverloadedMethod(classDef, from, name);
        if (methodDefs.size() == 0) return new HashSet<>(Set.of(name));
        Set<String> out = new HashSet<>();
        for (MethodDef def : methodDefs) {
            String res = def.getName(to);
            if (res != null) out.add(res);
        }
        if (out.size() == 0) return new HashSet<>(Set.of(name));
        return out;
    }

    @SuppressWarnings("unused")
    public static String graalRemapClass(String clazz) {
        return remapClass("named", current(), clazz);
    }

    @SuppressWarnings("unused")
    public static String graalRemapField(Class<?> clazz, String field) {
        return remapField(current(), clazz.getName(), "named", current(), field);
    }

    @SuppressWarnings("unused")
    public static Object graalRemapOverloadedMethod(Object hostCtx, Class<?> clazz, String searchName, boolean onlyStatic) {
        try {
            Set<String> names = remapOverloadedMethod(current(), clazz.getName(), "named", current(), searchName);

            Class<?> hostClassDescClass = Class.forName("com.oracle.truffle.host.HostClassDesc");
            Class<?> hostContextClass = Class.forName("com.oracle.truffle.host.HostContext");
            Class<?> overloadedMethodClass = Class.forName("com.oracle.truffle.host.HostMethodDesc$OverloadedMethod");
            Class<?> singleMethodClass = Class.forName("com.oracle.truffle.host.HostMethodDesc$SingleMethod");
            Class<?> hostMethodDescClass = Class.forName("com.oracle.truffle.host.HostMethodDesc");
            Class<?> membersClass = Class.forName("com.oracle.truffle.host.HostClassDesc$Members");

            Method forClassMethod = hostClassDescClass.getDeclaredMethod("forClass", hostContextClass, Class.class);
            forClassMethod.setAccessible(true);

            Object hostClassDesc = forClassMethod.invoke(null, hostCtx, clazz);

            List<Object> overloads = new ArrayList<>();
            Method lookupMethodMethod = hostClassDescClass.getDeclaredMethod("lookupMethod", String.class, boolean.class);
            Method lookupMethodBySignatureMethod = hostClassDescClass.getDeclaredMethod("lookupMethodBySignature", String.class, boolean.class);
            Method lookupMethodByJNINameMethod = hostClassDescClass.getDeclaredMethod("lookupMethodByJNIName", String.class, boolean.class);

            lookupMethodMethod.setAccessible(true);
            lookupMethodBySignatureMethod.setAccessible(true);
            lookupMethodByJNINameMethod.setAccessible(true);

            Field overloadsField = overloadedMethodClass.getDeclaredField("overloads");
            overloadsField.setAccessible(true);

            for (String name : names) {
                overloads.add(lookupMethodMethod.invoke(hostClassDesc, name, onlyStatic));
                overloads.add(lookupMethodBySignatureMethod.invoke(hostClassDesc, name, onlyStatic));
                overloads.add(lookupMethodByJNINameMethod.invoke(hostClassDesc, name, onlyStatic));
            }

            while (overloads.contains(null)) {
                overloads.remove(null);
            }

            List<Object> singleMethods = new ArrayList<>();

            for (Object possibleMethod : overloads) {
                if (singleMethodClass.isInstance(possibleMethod)) {
                    singleMethods.add(possibleMethod);
                } else {
                    singleMethods.addAll(List.of((Object[]) overloadsField.get(possibleMethod)));
                }
            }

            if (singleMethods.size() == 0) {
                return null;
            }

            Method mergeMethod = membersClass.getDeclaredMethod("merge", hostMethodDescClass, hostMethodDescClass);
            mergeMethod.setAccessible(true);

            Object out = singleMethods.get(0);
            for (int i = 1; i < singleMethods.size(); i++) {
                out = mergeMethod.invoke(null, out, singleMethods.get(i));
            }

            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
