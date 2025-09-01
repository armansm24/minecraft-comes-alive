package net.mca.forge;

import dev.architectury.platform.forge.EventBuses;
import net.mca.MCA;
import net.mca.ParticleTypesMCA;
import net.mca.SoundsMCA;
import net.mca.TradeOffersMCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.block.BlocksMCA;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.interaction.gifts.GiftLoader;
import net.mca.forge.cobalt.network.NetworkHandlerImpl;
import net.mca.item.ItemsMCA;
import net.mca.network.MessagesMCA;
import net.mca.resources.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MCA.MOD_ID)
@Mod.EventBusSubscriber(modid = MCA.MOD_ID, bus = Bus.MOD)
public final class MCAForge {
    // Force client-side network handler initialization as early as possible
    static {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            System.out.println("[MCA] Static block: Initializing NetworkHandlerImpl for CLIENT");
            net.mca.forge.cobalt.network.NetworkHandlerImpl.init();
        }
    }
    public MCAForge() {
        EventBuses.registerModEventBus(MCA.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        // Register packets for both sides
        NetworkHandlerImpl.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(MCAForge::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);

        BlocksMCA.bootstrap();
        ItemsMCA.bootstrap();
        SoundsMCA.bootstrap();
        ParticleTypesMCA.bootstrap();
        EntitiesMCA.bootstrap();
        MessagesMCA.bootstrap();
        CriterionMCA.bootstrap();
    }
    // Ensure packets are registered on the client
    private static void onClientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        NetworkHandlerImpl.init();
    }

    @SubscribeEvent
    public static void onFMLCommonSetupEvent(FMLCommonSetupEvent event) {
        TradeOffersMCA.bootstrap();
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ApiReloadListener());
        event.addListener(new ClothingList());
        event.addListener(new HairList());
        event.addListener(new GiftLoader());
        event.addListener(new Dialogues());
        event.addListener(new Tasks());
        event.addListener(new Names());
        event.addListener(new BuildingTypes());
    }
}
