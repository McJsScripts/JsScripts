package de.blazemcworld.jsscripts;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
                    JsScripts.displayChat(Text.literal("/jsscripts enable - Add a script to the auto-enable list.").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts disable - Remove a script from the auto-enable list.").formatted(Formatting.AQUA));
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
                        .executes(e -> {
                            int[] counts = {0, 0}; //total, loaded
                            List<Text> messages = new ArrayList<>();
                            messages.addAll(listScripts(ScriptManager.modDir.resolve("jspm"), "jspm/", counts, true));
                            messages.addAll(listScripts(ScriptManager.modDir.resolve("scripts"), "local/", counts, true));

                            JsScripts.displayChat(Text.literal("Scripts (" + counts[1] + " of " + counts[0] + " loaded)").formatted(Formatting.AQUA));

                            for (Text msg : messages) {
                                JsScripts.displayChat(msg);
                            }
                            return 1;
                        })
                )
                .then(literal("enable")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Invalid usage! Usage:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts enable <script>").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("script", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    try {
                                        JsonObject obj = JsonParser.parseString(Files.readString(ScriptManager.config.toPath())).getAsJsonObject();

                                        JsonArray enabled = obj.getAsJsonArray("enabled_scripts");

                                        if (enabled.contains(new JsonPrimitive(e.getArgument("script", String.class)))) {
                                            JsScripts.displayChat(Text.literal("Already added!").formatted(Formatting.RED));
                                            return 1;
                                        }

                                        enabled.add(e.getArgument("script", String.class));
                                        obj.add("enabled_scripts", enabled);
                                        Files.writeString(ScriptManager.config.toPath(), obj.toString());

                                        JsScripts.displayChat(Text.literal("Updated auto-enable list:").formatted(Formatting.AQUA));
                                        for (JsonElement id : enabled) {
                                            JsScripts.displayChat(Text.literal("- " + id.getAsString()).formatted(Formatting.AQUA)
                                                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts disable " + id.getAsString()))));
                                        }

                                        ScriptManager.reload();
                                    } catch (Exception err) {
                                        JsScripts.displayChat(Text.literal("Error adding script!").formatted(Formatting.AQUA));
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

                                        JsonArray enabled = obj.getAsJsonArray("enabled_scripts");

                                        if (!enabled.contains(new JsonPrimitive(e.getArgument("script", String.class)))) {
                                            JsScripts.displayChat(Text.literal("Not in list!").formatted(Formatting.RED));
                                            return 1;
                                        }

                                        enabled.remove(new JsonPrimitive(e.getArgument("script", String.class)));
                                        obj.add("enabled_scripts", enabled);
                                        Files.writeString(ScriptManager.config.toPath(), obj.toString());

                                        JsScripts.displayChat(Text.literal("Updated auto-enable list:").formatted(Formatting.AQUA));
                                        for (JsonElement id : enabled) {
                                            JsScripts.displayChat(Text.literal("- " + id.getAsString()).formatted(Formatting.AQUA)
                                                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts disable " + id.getAsString()))));
                                        }

                                        ScriptManager.reload();
                                    } catch (Exception err) {
                                        JsScripts.displayChat(Text.literal("Error removing script!").formatted(Formatting.AQUA));
                                        err.printStackTrace();
                                    }
                                    return 1;
                                })
                        )
                )
        ));
    }

    private List<Text> listScripts(Path p, String prefix, int[] counts, boolean root) {
        File f = p.toFile();
        List<Text> messages = new ArrayList<>();

        if (f.isDirectory()) {
            for (File child : f.listFiles()) {
                messages.addAll(listScripts(child.toPath(), prefix + (root ? "" : f.getName() + "/"), counts, false));
            }
            return messages;
        }

        boolean loaded = ScriptManager.loadedScripts.contains(p.toAbsolutePath().toString());
        boolean direct = ScriptManager.configData.getAsJsonArray("enabled_scripts").contains(new JsonPrimitive(prefix + f.getName()));

        counts[0]++;
        counts[1] += loaded ? 1 : 0;

        if (loaded) {
            if (direct) {
                messages.add(Text.literal(prefix + f.getName() + " - Enabled").formatted(Formatting.GREEN)
                        .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts disable " + prefix + f.getName()))));
            } else {
                messages.add(Text.literal(prefix + f.getName() + " - Dependency").styled(s -> s.withColor(TextColor.fromRgb(4031824))));
            }
        } else {
            messages.add(Text.literal(prefix + f.getName() + " - Disabled").formatted(Formatting.GRAY)
                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts enable " + prefix + f.getName()))));
        }

        return messages;
    }

}
