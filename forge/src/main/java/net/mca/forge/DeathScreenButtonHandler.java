package net.mca.forge;

import net.mca.HardcoreRespawnHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mca", value = Dist.CLIENT)
public class DeathScreenButtonHandler {
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof DeathScreen) {
            int width = screen.width;
            // Find the lowest button Y in the current button list (stack below vanilla buttons)
            int maxY = 0;
            for (var widget : screen.children()) {
                if (widget instanceof ButtonWidget) {
                    maxY = Math.max(maxY, ((ButtonWidget)widget).getY() + ((ButtonWidget)widget).getHeight());
                }
            }
            int buttonY = (maxY > 0) ? maxY + 4 : screen.height / 4 + 72 + 12;
            event.addListener(ButtonWidget.builder(
                net.minecraft.text.Text.literal("Respawn as Child"), btn -> {
                    // Send packet to server to trigger respawn-as-child logic
                    net.mca.forge.cobalt.network.NetworkHandlerImpl network = new net.mca.forge.cobalt.network.NetworkHandlerImpl();
                    network.sendToServer(new net.mca.forge.cobalt.network.RespawnAsChildPacket());
                })
                .dimensions(width / 2 - 100, buttonY, 200, 20)
                .build()
            );
        }
    }
}
