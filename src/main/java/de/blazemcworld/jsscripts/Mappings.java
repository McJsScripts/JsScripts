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
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
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

    @SuppressWarnings("unused")
    public static String graalRemapClass(String clazz) {
        return remapClass("named", current(), clazz);
    }

    @SuppressWarnings("unused")
    public static String graalRemapField(Class<?> clazz, String field) {
        return remapField(current(), clazz.getName(), "named", current(), field);
    }

    @SuppressWarnings("unused")
    public static String graalRemapMethod(Class<?> clazz, String method) {
        return remapMethod(current(), clazz.getName(), "named", current(), method);
    }
}
