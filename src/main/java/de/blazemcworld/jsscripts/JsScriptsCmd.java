package de.blazemcworld.jsscripts;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.MinecraftVersion;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
                    JsScripts.displayChat(Text.literal("/jsscripts upload - Upload a script to jspm.").formatted(Formatting.AQUA));
                    JsScripts.displayChat(Text.literal("/jsscripts download - Download a script from jspm.").formatted(Formatting.AQUA));
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
                            int total = 0;
                            int loaded = 0;

                            List<Text> messages = new ArrayList<>();

                            File dir = ScriptManager.modDir.resolve("scripts").toFile();

                            for (File f : dir.listFiles()) {
                                boolean isLoaded = ScriptManager.loadedScripts.contains(f.toPath().resolve("index.js").toAbsolutePath().toString());
                                boolean isDirect = ScriptManager.configData.getAsJsonArray("enabled_scripts").contains(new JsonPrimitive(f.getName()));

                                total++;
                                loaded += isLoaded ? 1 : 0;

                                if (isLoaded) {
                                    if (isDirect) {
                                        messages.add(Text.literal(f.getName() + " - Enabled").formatted(Formatting.GREEN)
                                                .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts disable " + f.getName()))));
                                    } else {
                                        messages.add(Text.literal(f.getName() + " - Dependency").styled(s -> s.withColor(TextColor.fromRgb(4031824))));
                                    }
                                } else {
                                    messages.add(Text.literal(f.getName() + " - Disabled").formatted(Formatting.GRAY)
                                            .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts enable " + f.getName()))));
                                }
                            }

                            JsScripts.displayChat(Text.literal("Scripts (" + loaded + " of " + total + " loaded)").formatted(Formatting.AQUA));

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
                .then(literal("upload")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Invalid usage! Usage:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts upload <script>").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("script", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    new Thread(() -> {
                                        try {
                                            String name = e.getArgument("script", String.class);
                                            Path root = ScriptManager.modDir.resolve("scripts").resolve(name);

                                            if (!root.toFile().exists()) {
                                                JsScripts.displayChat(Text.literal("Unknown Script!").formatted(Formatting.RED));
                                                return;
                                            }

                                            Path configFile = root.resolve("jspm.json");
                                            if (!configFile.toFile().exists()) {
                                                Files.writeString(configFile, """
                                                        {
                                                            "author": {
                                                                "name": "%s",
                                                                "uuid": "%s"
                                                            },
                                                            "version": {
                                                                "pkg": "1.0.0",
                                                                "minecraft": "%s"
                                                            }
                                                        }
                                                        """.formatted(
                                                        JsScripts.MC.getSession().getProfile().getName(),
                                                        JsScripts.MC.getSession().getProfile().getId().toString(),
                                                        MinecraftVersion.CURRENT.getName()
                                                ));
                                                JsScripts.displayChat(Text.literal("Please update the newly created jspm.json file in the script if necessary, then retry.").formatted(Formatting.AQUA));
                                                return;
                                            }

                                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                            ZipOutputStream zos = new ZipOutputStream(baos);

                                            try (Stream<Path> paths = Files.walk(root)) {
                                                for (Path p : paths.toList()) {
                                                    if (Files.isDirectory(p)) {
                                                        continue;
                                                    }
                                                    ZipEntry zipEntry = new ZipEntry(root.relativize(p).toString());
                                                    zos.putNextEntry(zipEntry);
                                                    byte[] bytes = Files.readAllBytes(p);
                                                    zos.write(bytes, 0, bytes.length);
                                                    zos.closeEntry();
                                                }
                                            }

                                            zos.close();
                                            baos.close();

                                            JsScripts.displayChat(Text.literal("Uploading script to JSPM...").formatted(Formatting.AQUA));
                                            JSPM.upload(name, baos.toByteArray());
                                            JsScripts.displayChat(Text.literal("Done.").formatted(Formatting.AQUA));
                                        } catch (Exception err) {
                                            err.printStackTrace();
                                            JsScripts.displayChat(Text.literal("Error (" + err.getMessage() + ")").formatted(Formatting.RED));
                                        }
                                    }).start();
                                    return 1;
                                })
                        )
                )
                .then(literal("download")
                        .executes((e) -> {
                            JsScripts.displayChat(Text.literal("Invalid usage! Usage:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts download <script>").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("script", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    new Thread(() -> {
                                        try {
                                            String name = e.getArgument("script", String.class);
                                            Path root = ScriptManager.modDir.resolve("scripts").resolve(name);

                                            if (!JSPM.has(name)) {
                                                JsScripts.displayChat(Text.literal("Script doesn't exist on JSPM!").formatted(Formatting.RED));
                                                return;
                                            }

                                            if (root.toFile().exists()) {
                                                JsScripts.displayChat(Text.literal("Script already found locally! Delete it to re-download.").formatted(Formatting.RED));
                                                return;
                                            }

                                            JsScripts.displayChat(Text.literal("Downloading script from JSPM...").formatted(Formatting.AQUA));
                                            JSPM.download(name);
                                            JsScripts.displayChat(Text.literal("Done.").formatted(Formatting.AQUA));
                                            JsScripts.displayChat(Text.literal("Click to reload now.").formatted(Formatting.AQUA)
                                                    .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts reload"))));
                                        } catch (Exception err) {
                                            err.printStackTrace();
                                            JsScripts.displayChat(Text.literal("Error (" + err.getMessage() + ")").formatted(Formatting.RED));
                                        }
                                    }).start();
                                    return 1;
                                })
                        )
                )
        ));
    }
}
