package de.blazemcworld.jsscripts;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                            JsScripts.displayChat(Text.literal("Specific classes: the.class.Name"));
                            JsScripts.displayChat(Text.literal("All in a package: some.package.*"));
                            JsScripts.displayChat(Text.literal("Enable asm for methods: -asm"));
                            JsScripts.displayChat(Text.literal("Remap asm output: -remap"));
                            JsScripts.displayChat(Text.literal("Show private methods: -private"));
                            JsScripts.displayChat(Text.literal("Example: /jsscripts gen_types java.lang.System -private -asm"));
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
        ));
    }

}
