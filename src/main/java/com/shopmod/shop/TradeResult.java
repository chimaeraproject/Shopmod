package com.shopmod.shop;

import net.minecraft.network.chat.Component;

public enum TradeResult {
    SUCCESS("Trade complete."),
    SHOP_INACTIVE("This shop is not currently active."),
    OWNER("You cannot buy from your own shop."),
    NO_PAYMENT("You do not have enough payment items to trade."),
    OUT_OF_STOCK("This shop is out of stock."),
    CHEST_FULL("This shop cannot accept payment right now."),
    PLAYER_FULL("You do not have enough inventory space for this trade.");

    private final String message;

    TradeResult(String message) {
        this.message = message;
    }

    public Component message() {
        return Component.literal(message);
    }
}
