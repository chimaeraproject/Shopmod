package com.shopmod.shop;

import com.shopmod.Config;
import com.shopmod.network.ShopNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.SignBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

public final class ShopEvents {
    private ShopEvents() {
    }

    @SubscribeEvent
    public static void onSignPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!Config.ENABLE_SHOPS.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getPlacedBlock().getBlock() instanceof SignBlock)) {
            return;
        }
        ShopManager.attachedChest(level, event.getPos()).ifPresent(chestPos -> ShopNetworking.openConfig(player, event.getPos(), chestPos));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.ENABLE_SHOPS.get()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ShopManager.findShop(level, event.getPos()).ifPresent(shop -> {
            if (ShopManager.isChest(level, event.getPos()) && ShopManager.hasKey(player, shop)) {
                return;
            }
            if (!ShopManager.isActive(level, shop)) {
                player.sendSystemMessage(TradeResult.SHOP_INACTIVE.message());
            } else {
                ShopNetworking.openTrade(player, shop);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        });
    }

    @SubscribeEvent
    public static void onBreakBlock(BreakBlockEvent event) {
        if (!Config.ENABLE_SHOPS.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ShopManager.findShop(level, event.getPos()).ifPresent(shop -> {
            if (ShopManager.hasKey(player, shop)) {
                ShopManager.removeShop(level, shop);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Shop deregistered."));
            } else {
                event.setCanceled(true);
                event.setNotifyClient(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("This shop is locked. Hold the named key item to break it."));
            }
        });
    }
}
