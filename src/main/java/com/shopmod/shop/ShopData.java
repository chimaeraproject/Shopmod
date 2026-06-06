package com.shopmod.shop;

import java.util.UUID;

import net.minecraft.core.BlockPos;

public record ShopData(
        String dimension,
        BlockPos chestPos,
        BlockPos signPos,
        String ownerName,
        UUID ownerId,
        String inputItemId,
        int inputAmount,
        String outputItemId,
        int outputAmount,
        String passwordHash) {
}
