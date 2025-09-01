package net.mca.forge.cobalt.network;

import net.mca.MCA;
import net.mca.HardcoreRespawnHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

import net.mca.cobalt.network.Message;

public class RespawnAsChildPacket implements Message {
    @Override
    public void receive(ServerPlayerEntity player) {
        if (player != null) {
            System.out.println("[MCA] RespawnAsChildPacket received on server for player: " + player.getName().getString());
            HardcoreRespawnHandler.onPlayerDeath(player);
        }
    }

    @Override
    public void receive() {
        // Not used
    }
    public static void handle(RespawnAsChildPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                System.out.println("[MCA] RespawnAsChildPacket received on server for player: " + player.getName().getString());
                HardcoreRespawnHandler.onPlayerDeath(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
