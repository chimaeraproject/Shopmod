package com.shopmod;

import com.shopmod.client.ShopClientScreens;
import com.shopmod.network.ShopNetworking;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@Mod(value = Shopmod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Shopmod.MODID, value = Dist.CLIENT)
public class ShopmodClient {
    public ShopmodClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(this::registerClientPayloadHandlers);
    }

    private void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ShopNetworking.OpenConfigPayload.TYPE, (payload, context) -> ShopClientScreens.openConfig(payload));
        event.register(ShopNetworking.OpenTradePayload.TYPE, (payload, context) -> ShopClientScreens.openTrade(payload));
    }
}
