package com.shopmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.shopmod.network.ShopNetworking;
import com.shopmod.shop.ShopEvents;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Shopmod.MODID)
public class Shopmod {
    public static final String MODID = "shopmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Shopmod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(ShopNetworking::register);
        NeoForge.EVENT_BUS.register(ShopEvents.class);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
