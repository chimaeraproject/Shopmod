package com.shopmod.network;

import com.shopmod.Shopmod;
import com.shopmod.shop.ShopManager;
import com.shopmod.shop.TradeResult;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SignBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ShopNetworking {
    private static final String VERSION = "1";

    private ShopNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(OpenConfigPayload.TYPE, OpenConfigPayload.STREAM_CODEC);
        registrar.playToClient(OpenTradePayload.TYPE, OpenTradePayload.STREAM_CODEC);
        registrar.playToServer(SubmitConfigPayload.TYPE, SubmitConfigPayload.STREAM_CODEC, ShopNetworking::handleSubmitConfig);
        registrar.playToServer(CancelConfigPayload.TYPE, CancelConfigPayload.STREAM_CODEC, ShopNetworking::handleCancelConfig);
        registrar.playToServer(ConfirmTradePayload.TYPE, ConfirmTradePayload.STREAM_CODEC, ShopNetworking::handleConfirmTrade);
    }

    public static void openConfig(ServerPlayer player, BlockPos signPos, BlockPos chestPos) {
        PacketDistributor.sendToPlayer(player, new OpenConfigPayload(signPos, chestPos));
    }

    public static void openTrade(ServerPlayer player, com.shopmod.shop.ShopData shop) {
        PacketDistributor.sendToPlayer(player, new OpenTradePayload(shop.chestPos(), shop.signPos(), shop.inputItemId(), shop.inputAmount(), shop.outputItemId(), shop.outputAmount()));
    }

    private static void handleSubmitConfig(SubmitConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean registered = ShopManager.registerShop(level, player, payload.chestPos(), payload.signPos(), payload.inputItemId(), payload.inputAmount(),
                payload.outputItemId(), payload.outputAmount(), payload.password());
        player.sendSystemMessage(registered ? Component.literal("Shop registered.") : Component.literal("Shop setup failed. Check item IDs, amounts, and chest/sign placement."));
    }

    private static void handleCancelConfig(CancelConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getBlockState(payload.signPos()).getBlock() instanceof SignBlock && ShopManager.findShop(level, payload.signPos()).isEmpty()) {
            level.destroyBlock(payload.signPos(), false, player);
        }
        player.sendSystemMessage(Component.literal("Shop setup cancelled."));
    }

    private static void handleConfirmTrade(ConfirmTradePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        TradeResult result = ShopManager.findShop(level, payload.chestPos())
                .map(shop -> ShopManager.trade(level, player, shop))
                .orElse(TradeResult.SHOP_INACTIVE);
        player.sendSystemMessage(result.message());
    }

    public record OpenConfigPayload(BlockPos signPos, BlockPos chestPos) implements CustomPacketPayload {
        public static final Type<OpenConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Shopmod.MODID, "open_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenConfigPayload> STREAM_CODEC = StreamCodec.ofMember(OpenConfigPayload::write, OpenConfigPayload::read);

        private static OpenConfigPayload read(RegistryFriendlyByteBuf buffer) {
            return new OpenConfigPayload(buffer.readBlockPos(), buffer.readBlockPos());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(signPos);
            buffer.writeBlockPos(chestPos);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenTradePayload(BlockPos chestPos, BlockPos signPos, String inputItemId, int inputAmount, String outputItemId, int outputAmount)
            implements CustomPacketPayload {
        public static final Type<OpenTradePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Shopmod.MODID, "open_trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTradePayload> STREAM_CODEC = StreamCodec.ofMember(OpenTradePayload::write, OpenTradePayload::read);

        private static OpenTradePayload read(RegistryFriendlyByteBuf buffer) {
            return new OpenTradePayload(buffer.readBlockPos(), buffer.readBlockPos(), ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readVarInt(),
                    ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(chestPos);
            buffer.writeBlockPos(signPos);
            ByteBufCodecs.STRING_UTF8.encode(buffer, inputItemId);
            buffer.writeVarInt(inputAmount);
            ByteBufCodecs.STRING_UTF8.encode(buffer, outputItemId);
            buffer.writeVarInt(outputAmount);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SubmitConfigPayload(BlockPos signPos, BlockPos chestPos, String inputItemId, int inputAmount, String outputItemId, int outputAmount,
            String password) implements CustomPacketPayload {
        public static final Type<SubmitConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Shopmod.MODID, "submit_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SubmitConfigPayload> STREAM_CODEC = StreamCodec.ofMember(SubmitConfigPayload::write, SubmitConfigPayload::read);

        private static SubmitConfigPayload read(RegistryFriendlyByteBuf buffer) {
            return new SubmitConfigPayload(buffer.readBlockPos(), buffer.readBlockPos(), ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readVarInt(),
                    ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readVarInt(), ByteBufCodecs.STRING_UTF8.decode(buffer));
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(signPos);
            buffer.writeBlockPos(chestPos);
            ByteBufCodecs.STRING_UTF8.encode(buffer, inputItemId);
            buffer.writeVarInt(inputAmount);
            ByteBufCodecs.STRING_UTF8.encode(buffer, outputItemId);
            buffer.writeVarInt(outputAmount);
            ByteBufCodecs.STRING_UTF8.encode(buffer, password);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CancelConfigPayload(BlockPos signPos) implements CustomPacketPayload {
        public static final Type<CancelConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Shopmod.MODID, "cancel_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CancelConfigPayload> STREAM_CODEC = StreamCodec.ofMember(CancelConfigPayload::write, CancelConfigPayload::read);

        private static CancelConfigPayload read(RegistryFriendlyByteBuf buffer) {
            return new CancelConfigPayload(buffer.readBlockPos());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(signPos);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ConfirmTradePayload(BlockPos chestPos) implements CustomPacketPayload {
        public static final Type<ConfirmTradePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Shopmod.MODID, "confirm_trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmTradePayload> STREAM_CODEC = StreamCodec.ofMember(ConfirmTradePayload::write, ConfirmTradePayload::read);

        private static ConfirmTradePayload read(RegistryFriendlyByteBuf buffer) {
            return new ConfirmTradePayload(buffer.readBlockPos());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(chestPos);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
