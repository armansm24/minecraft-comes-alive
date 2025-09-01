package net.mca.forge.cobalt.network;

import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandlerImpl extends NetworkHandler.Impl {
    private static NetworkHandlerImpl instance;
    private final String PROTOCOL_VERSION = "1";
    private final SimpleChannel channel;

    private NetworkHandlerImpl() {
        System.out.println("[MCA] NetworkHandlerImpl constructor called. FMLEnvironment.dist=" + net.minecraftforge.fml.loading.FMLEnvironment.dist);
        channel = NetworkRegistry.newSimpleChannel(
            new Identifier(MCA.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
        );
    }

    public static void init() {
        if (instance == null) {
            System.out.println("[MCA] NetworkHandlerImpl.init called");
            instance = new NetworkHandlerImpl();
            instance.registerPackets();
        }
    }

    public static NetworkHandlerImpl getInstance() {
        return instance;
    }
    private int id = 0;

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Message> void registerMessage(Class<T> msg) {
        channel.registerMessage(id++, msg,
                Message::encode,
                b -> (T) Message.decode(b),
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayerEntity sender = ctx.get().getSender();
                        if (sender == null) {
                            m.receive();
                        } else {
                            m.receive(sender);
                        }
                    });
                    ctx.get().setPacketHandled(true);
                });
    }

    public void registerPackets() {
        System.out.println("[MCA] NetworkHandlerImpl.registerPackets called");
        // Register RespawnAsChildPacket
        channel.registerMessage(id++, RespawnAsChildPacket.class,
            (msg, buf) -> {}, // No data to encode
            buf -> new RespawnAsChildPacket(),
            RespawnAsChildPacket::handle
        );

        // Register SyncChildrenCountPacket
        channel.registerMessage(id++, SyncChildrenCountPacket.class,
            (msg, buf) -> msg.encode(buf),
            SyncChildrenCountPacket::new,
            (msg, ctx) -> msg.receive()
        );

        // Register CloseDeathScreenMessage
        channel.registerMessage(id++, net.mca.network.c2s.CloseDeathScreenMessage.class,
            (msg, buf) -> {}, // No data to encode
            buf -> new net.mca.network.c2s.CloseDeathScreenMessage(),
            (msg, ctx) -> msg.receive()
        );
    }

    @Override
    public void sendToServer(Message m) {
        channel.sendToServer(m);
    }

    @Override
    public void sendToPlayer(Message m, ServerPlayerEntity e) {
        channel.send(PacketDistributor.PLAYER.with(() -> e), m);
    }
}
