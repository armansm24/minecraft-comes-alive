package net.mca.forge.cobalt.network;

import net.mca.cobalt.network.Message;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class SyncChildrenCountPacket implements Message {
    private final int count;

    public SyncChildrenCountPacket(int count) {
        this.count = count;
    }

    public SyncChildrenCountPacket(PacketByteBuf buf) {
        this.count = buf.readInt();
    }

    public void encode(PacketByteBuf buf) {
        buf.writeInt(count);
    }

    @OnlyIn(Dist.CLIENT)
    public void receive() {
        net.mca.forge.DeathScreenButtonHandler.setChildrenCount(count);
    }

    public void receive(ServerPlayerEntity player) {
        // Not used on server
    }
}
