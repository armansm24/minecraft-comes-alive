package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.server.network.ServerPlayerEntity;

public class CloseDeathScreenMessage implements Message {
    @Override
    public void receive(ServerPlayerEntity player) {
        // No server-side logic needed
    }

    @Override
    public void receive() {
        System.out.println("[MCA] CloseDeathScreenMessage received on client.");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            System.out.println("[MCA] MinecraftClient instance is valid.");
            if (mc.currentScreen instanceof DeathScreen) {
                System.out.println("[MCA] Closing death screen.");
                mc.setScreen(null);
            } else {
                System.out.println("[MCA] Current screen is not DeathScreen: " + mc.currentScreen);
            }
        } else {
            System.out.println("[MCA] MinecraftClient instance is null.");
        }
    }
}
