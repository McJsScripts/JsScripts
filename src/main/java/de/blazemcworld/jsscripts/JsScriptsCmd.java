package de.blazemcworld.jsscripts;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class JsScriptsCmd {

    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("jsscripts")
                .executes((e) -> {
                    JsScripts.displayChat(Text.literal("Available commands:").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts reload - Reload all scripts").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts gen_types - Generate .d.ts files").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts list - List all currently enabled scripts.").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts sign - Adds your signature to a script.").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts enable - Enable a script.").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts disable - Disable a script.").formatted(Formatting.AQUA));
                    return 1;
                })
                .then(literal("reload")
                        .executes((e) -> {
                            ScriptManager.reload();
                            JsScripts.displayChat(Text.literal("Reloaded scripts").formatted(Formatting.AQUA));
                            return 1;
                        })
                )
                .then(literal("gen_types")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Invalid usage! Usage:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts gen_types <args>").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("Specific classes: the.class.Name").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("All in a package: some.package.*").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("Enable asm for methods: -asm").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("Remap asm output: -remap").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("Show private methods: -private").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("Example: /jsscripts gen_types java.lang.System -private -asm").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("classes", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    new Thread(() -> {
                                        String target = e.getArgument("classes", String.class);
                                        try {
                                            TypingGen.genTypesIn(target);
                                        } catch (Exception err) {
                                            JsScripts.displayChat(Text.literal("Error generating types for " + target).formatted(Formatting.AQUA));
                                            err.printStackTrace();
                                        }
                                    }).start();
                                    return 1;
                                })
                        )
                )
                .then(literal("list")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Scripts (Loaded " + ScriptManager.scripts.size() + ")").formatted(Formatting.AQUA));
                            try {
                                for (File f : ScriptManager.availableScripts()) {
                                    Script s = ScriptManager.scriptByFile(f);

                                    MutableText msg = Text.literal("-" + f.getName()).copy();
                                    Style style = msg.getStyle();
                                    if (s == null) {
                                        if (ScriptManager.errors.contains(f)) {
                                            style = style.withColor(Formatting.RED);
                                            msg.append(" - Error");
                                        } else {
                                            style = style.withColor(Formatting.GRAY);
                                            msg.append(" - Disabled");
                                            style = style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts enable " + ScriptManager.scriptDir.toPath().relativize(f.toPath())));
                                            style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to enable " + f.getName()).formatted(Formatting.AQUA)));
                                        }
                                    } else {
                                        msg.append(" - ");
                                        switch (s.getCause()) {
                                            case DIRECT -> {
                                                msg.append("Directly");
                                                style = style.withColor(Formatting.GREEN);
                                                style = style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts disable " + ScriptManager.scriptDir.toPath().relativize(f.toPath())));
                                                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to disable " + f.getName()).formatted(Formatting.AQUA)));
                                            }
                                            case DEPENDED_UPON -> {
                                                msg.append("Dependency");
                                                style = style.withColor(TextColor.fromRgb(4031824));
                                            }
                                        }
                                    }
                                    JsScripts.displayChat(msg.setStyle(style));
                                }
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                            return 1;
                        })
                )
                .then(literal("sign")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Invalid usage! Usage:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts sign <script>").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("script", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    try {
                                        Path p = ScriptManager.scriptDir.toPath().resolve(e.getArgument("script", String.class));
                                        String src = Files.readString(p);
                                        src += "\n//SIGNED " + Crypt.sign(src.replaceAll("\\n?\\r?//SIGNED .+", ""), Crypt.getKeyPair().getRight());
                                        Files.writeString(p, src);
                                        JsScripts.displayChat(Text.literal("Signed script!").formatted(Formatting.AQUA));
                                    } catch (Exception err) {
                                        JsScripts.displayChat(Text.literal("Error signing script!").formatted(Formatting.AQUA));
                                        err.printStackTrace();
                                    }
                                    return 1;
                                })
                        )
                )
                .then(literal("enable")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Invalid usage! Usage:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts enable <script>").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("script", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    File f = ScriptManager.scriptDir.toPath().resolve(e.getArgument("script", String.class)).toFile();

                                    if (ScriptManager.scriptByFile(f) != null) {
                                        JsScripts.displayChat(Text.literal("Already enabled!").formatted(Formatting.AQUA));
                                        return 1;
                                    }

                                    if (!f.exists()) {
                                        JsScripts.displayChat(Text.literal("Unknown script!").formatted(Formatting.AQUA));
                                        return 1;
                                    }

                                    try {
                                        JsonObject obj = JsonParser.parseString(Files.readString(ScriptManager.config.toPath())).getAsJsonObject();

                                        JsonArray loaded = obj.getAsJsonArray("loaded_scripts");

                                        if (loaded.contains(new JsonPrimitive(e.getArgument("script", String.class)))) {
                                            JsScripts.displayChat(Text.literal("Should be enabled! Check logs in case it's not.").formatted(Formatting.AQUA));
                                            return 1;
                                        }

                                        loaded.add(e.getArgument("script", String.class));
                                        obj.add("loaded_scripts", loaded);
                                        Files.writeString(ScriptManager.config.toPath(), obj.toString());
                                        ScriptManager.reload();
                                        JsScripts.displayChat(Text.literal("Enabled script!").formatted(Formatting.AQUA));
                                    } catch (Exception err) {
                                        JsScripts.displayChat(Text.literal("Error enabling script!").formatted(Formatting.AQUA));
                                        err.printStackTrace();
                                    }
                                    return 1;
                                })
                        )
                )
                .then(literal("disable")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Invalid usage! Usage:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts disable <script>").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("script", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    try {
                                        JsonObject obj = JsonParser.parseString(Files.readString(ScriptManager.config.toPath())).getAsJsonObject();

                                        File query = ScriptManager.scriptDir.toPath().resolve(e.getArgument("script", String.class)).toFile();
                                        String found = null;

                                        JsonArray loaded = obj.getAsJsonArray("loaded_scripts");
                                        for (JsonElement elm : loaded) {
                                            if (ScriptManager.scriptDir.toPath().resolve(elm.getAsString()).toFile().equals(query)) {
                                                found = elm.getAsString();
                                                break;
                                            }
                                        }
                                        if (found != null) {
                                            loaded.remove(new JsonPrimitive(found));
                                        }
                                        obj.add("loaded_scripts", loaded);

                                        JsonArray devScripts = obj.getAsJsonArray("dev_scripts");
                                        for (JsonElement elm : devScripts) {
                                            if (ScriptManager.scriptDir.toPath().resolve(elm.getAsString()).toFile().equals(query)) {
                                                found = elm.getAsString();
                                                break;
                                            }
                                        }
                                        if (found != null) {
                                            devScripts.remove(new JsonPrimitive(found));
                                        }
                                        obj.add("dev_scripts", devScripts);

                                        if (found == null) {
                                            JsScripts.displayChat(Text.literal("Script not found in config! Is it in a dev scripts folder?").formatted(Formatting.AQUA));
                                            return 1;
                                        }

                                        Files.writeString(ScriptManager.config.toPath(), obj.toString());
                                        ScriptManager.reload();
                                        JsScripts.displayChat(Text.literal("Disabled script!").formatted(Formatting.AQUA));
                                    } catch (Exception err) {
                                        JsScripts.displayChat(Text.literal("Error disabling script!").formatted(Formatting.AQUA));
                                        err.printStackTrace();
                                    }
                                    return 1;
                                })
                        )
                )
        ));
    }

}
