package de.blazemcworld.jsscripts;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Script {

    private final File file;
    private final Context ctx;
    private static final Pattern importReplacePattern = Pattern.compile("import +(\\w+) +from +([\"'`])([^\"'`]+)\\2 *;? *");

    @SuppressWarnings("CanBeFinal")
    public Runnable onDisable = null;
    private final String hash;
    private final Map<String, Value> exports = new HashMap<>();
    private final Map<String, List<Runnable>> exportCallbacks = new HashMap<>();
    private final LoadCause cause;

    public Script(File file, boolean trusted, LoadCause cause) throws Exception {
        this.file = file;
        this.cause = cause;

        String rawSource = Files.readString(file.toPath());
        hash = Crypt.hash(rawSource);

        String noSignatureSrc = rawSource.replaceAll("\\n?\\r?//SIGNED .+", "");
        String[] lines = rawSource.split("\n");

        for (String line : lines) {
            if (trusted) {
                break;
            }
            if (line.startsWith("//SIGNED ")) {
                String signature = line.substring(9);
                if (ScriptManager.isTrusted(noSignatureSrc, signature.trim())) {
                    trusted = true;
                }
            }
        }

        if (!trusted) {
            throw new Exception("No trusted signature found in " + file);
        }

        ctx = Context.newBuilder()
                .allowAllAccess(true)
                .logHandler(System.out)
                .build();

        for (String line : lines) {
            if (line.startsWith("//DEPEND ")) {
                line = line.substring(9);
                String[] parts = line.trim().split(" ");
                if (parts.length <= 1) {
                    ctx.close();
                    throw new Exception("Invalid dependency comment.");
                }
                String libName = parts[0];
                String source = parts[1];

                ScriptAccess pending = new ScriptAccess();
                ctx.getBindings("js").putMember(libName, pending);

                source = ScriptManager.resolveAliases(source);

                if (source.startsWith("file:")) {
                    ScriptManager.addUnknown(source, null, pending::set, true);
                } else if (source.startsWith("https:")) {
                    if (parts.length != 3) {
                        ctx.close();
                        throw new Exception("Invalid dependency comment.");
                    }
                    String hash = parts[2];
                    ScriptManager.addUnknown(source, hash, pending::set, false);
                } else {
                    throw new Exception("Invalid dependency source protocol.");
                }
            }
        }

        Value bindings = ctx.getBindings("js");
        bindings.putMember("script", this);

        StringBuilder source = new StringBuilder();
        Matcher m = importReplacePattern.matcher(rawSource);

        while (m.find()) {
            String className = m.group(3).replace('/', '.');
            while (className.startsWith(".")) {
                className = className.substring(1);
            }
            if (className.startsWith("types.")) {
                className = className.substring((6));
            }
            m.appendReplacement(source, "const " + m.group(1) + " = Java.type('" + className + "');");
        }
        m.appendTail(source);

        ctx.eval(Source.newBuilder("js", source.toString(), file.getName()).mimeType("application/javascript+module").build());
    }


    public File getFile() {
        return file;
    }

    public void close() {
        ctx.close();
    }

    @SuppressWarnings("unused")
    public Context getCtx() {
        return ctx;
    }

    @SuppressWarnings("unused")
    public void debug(Object... objects) {
        for (Object obj : objects) {
            if (JsScripts.MC.player == null) {
                JsScripts.LOGGER.info(file.getName() + ": " + obj);
            } else {
                JsScripts.MC.player.sendMessage(Text.literal(file.getName() + ": " + obj).formatted(Formatting.GRAY));
            }
        }
    }

    @SuppressWarnings("unused")
    public void export(String identifier, Value obj) {
        exports.put(identifier, obj);
        if (exportCallbacks.containsKey(identifier)) {
            for (Runnable cb : exportCallbacks.get(identifier)) {
                cb.run();
            }
            exportCallbacks.remove(identifier);
        }
    }

    public void load(String identifier, Consumer<Value> cb) {
        if (exports.containsKey(identifier)) {
            cb.accept(exports.get(identifier));
            return;
        }
        if (!exportCallbacks.containsKey(identifier)) {
            exportCallbacks.put(identifier, new ArrayList<>());
        }
        exportCallbacks.get(identifier).add(() -> cb.accept(exports.get(identifier)));
    }

    public String getHash() {
        return hash;
    }

    public LoadCause getCause() {
        return cause;
    }

    enum LoadCause {
        DEPENDED_UPON,
        DIRECT
    }
}
