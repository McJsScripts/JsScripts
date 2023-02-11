package de.blazemcworld.jsscripts;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                            JsScripts.displayChat(Text.literal("Current Scripts (" + ScriptManager.scripts.size() + ")").formatted(Formatting.AQUA));
                            for (Script s : ScriptManager.scripts) {
                                JsScripts.displayChat(Text.literal("-" + s.getFile().getName()).formatted(Formatting.AQUA));
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
                        .then(argument("script", StringArgumentType.string())
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
        ));
    }

}
