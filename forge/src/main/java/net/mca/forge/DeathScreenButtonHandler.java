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
    private static int syncedChildrenCount = -1;

    public static void setChildrenCount(int count) {
        syncedChildrenCount = count;
    }
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

            boolean hasChildren = syncedChildrenCount > 0;
            ButtonWidget respawnButton = ButtonWidget.builder(
                net.minecraft.text.Text.literal("Respawn as Child"), btn -> {
                    System.out.println("[MCA] Respawn as Child button clicked. Sending packet to server.");
                    net.mca.forge.cobalt.network.NetworkHandlerImpl.getInstance().sendToServer(new net.mca.forge.cobalt.network.RespawnAsChildPacket());
                })
                .dimensions(width / 2 - 100, buttonY, 200, 20)
                .build();
            respawnButton.active = hasChildren;
            if (!hasChildren) {
                respawnButton.setMessage(net.minecraft.text.Text.literal("Respawn as Child (No Children)").styled(s -> s.withColor(0xAAAAAA)));
            }
            event.addListener(respawnButton);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof DeathScreen) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int width = screen.width;
            String childrenText = getChildrenText();
            int textY = screen.height / 4 + 48;
            var textRenderer = mc.textRenderer;
            var text = net.minecraft.text.Text.literal(childrenText);
            int textWidth = textRenderer.getWidth(text);
            int textX = (width - textWidth) / 2;
            event.getGuiGraphics().drawText(textRenderer, text, textX, textY, 0xFFFFFF, false);
        }
    }

    private static String getChildrenText() {
        if (syncedChildrenCount >= 0) {
            return "Children: " + syncedChildrenCount;
        } else {
            return "Children: ?";
        }
    }
}
