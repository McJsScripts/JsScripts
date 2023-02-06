package de.blazemcworld.jsscripts;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Script {

    private final File file;
    private final Context ctx;
    private static final Pattern importReplacePattern = Pattern.compile("import +(\\w+) +from +([\"'`])([^\"'`]+)\\2 *;? *");

    @SuppressWarnings("CanBeFinal")
    public Runnable onDisable = null;

    public Script(File file) throws Exception {
        this.file = file;
        ctx = Context.newBuilder()
                .allowAllAccess(true)
                .logHandler(System.out)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
        Value bindings = ctx.getBindings("js");
        bindings.putMember("script", this);

        StringBuilder source = new StringBuilder();
        Matcher m = importReplacePattern.matcher(Files.readString(file.toPath()));

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

        ctx.eval(Source.newBuilder("js", source.toString(), file.getName()).build());
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
        ScriptManager.saveValue(identifier, obj);
    }

    @SuppressWarnings("unused")
    public Value load(String identifier) {
        return ScriptManager.loadValue(identifier, ctx);
    }
}
