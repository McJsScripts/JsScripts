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
                            JsScripts.displayChat(Text.literal("Invalid usage! Try one of:").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts gen_types *").formatted(Formatting.AQUA));
                            JsScripts.displayChat(Text.literal("/jsscripts gen_types some.class.Name").formatted(Formatting.AQUA));
                            return 1;
                        })
                        .then(argument("classes", StringArgumentType.greedyString())
                                .executes((e) -> {
                                    String[] classes = e.getArgument("classes",String.class).split(" ");
                                    for (String target : classes) {
                                        if (target.equals("*")) {
                                            new Thread(TypingGen::genAllTypes).start();
                                            continue;
                                        }
                                        try {
                                            TypingGen.genTypesFor(target);
                                            JsScripts.displayChat(Text.literal("Generated types for " + target).formatted(Formatting.AQUA));
                                        } catch (Exception err) {
                                            JsScripts.displayChat(Text.literal("Error generating types for " + target).formatted(Formatting.AQUA));
                                            err.printStackTrace();
                                        }
                                    }
                                    return 1;
                                })
                        )
                )
        ));
    }

}
