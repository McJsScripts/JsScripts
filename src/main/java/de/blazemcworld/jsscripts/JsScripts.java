package de.blazemcworld.jsscripts;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JsScripts implements ModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("JsScripts");
    public static final MinecraftClient MC = MinecraftClient.getInstance();

    public static void displayChat(Text t) {
        if (MC.player != null) {
            MC.player.sendMessage(t);
        }
    }

    @Override
    public void onInitialize() {
        ByteBuddyAgent.install();
        new JsScriptsCmd().register();
        ScriptManager.init();
        LOGGER.info("Initialized!");
    }
}
