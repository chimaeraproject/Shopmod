package com.shopmod.shop;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

public final class ShopManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORED_SHOP_LIST = new TypeToken<List<StoredShop>>() {}.getType();
    private static final List<ShopData> SHOPS = new ArrayList<>();
    private static boolean loaded;

    private ShopManager() {
    }

    public static Optional<Item> validateItemId(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !itemId.contains(":")) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(id);
    }

    public static boolean registerShop(ServerLevel level, Player owner, BlockPos chestPos, BlockPos signPos, String inputItemId, int inputAmount,
            String outputItemId, int outputAmount, String password) {
        load(level.getServer());
        if (inputAmount < 1 || outputAmount < 1 || password.isBlank()) {
            return false;
        }
        if (validateItemId(inputItemId).isEmpty() || validateItemId(outputItemId).isEmpty()) {
            return false;
        }
        if (!isChest(level, chestPos) || !isSign(level, signPos)) {
            return false;
        }

        String dimension = dimensionId(level);
        SHOPS.removeIf(shop -> shop.dimension().equals(dimension) && (shop.chestPos().equals(chestPos) || shop.signPos().equals(signPos)));
        SHOPS.add(new ShopData(dimension, chestPos, signPos, owner.getGameProfile().name(), owner.getUUID(), inputItemId, inputAmount,
                outputItemId, outputAmount, hash(password)));
        save(level.getServer());
        return true;
    }

    public static Optional<ShopData> findShop(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        load(serverLevel.getServer());
        String dimension = dimensionId(level);
        return SHOPS.stream()
                .filter(shop -> shop.dimension().equals(dimension))
                .filter(shop -> shop.signPos().equals(pos) || shop.chestPos().equals(pos) || connectedChest(level, shop.chestPos()).map(pos::equals).orElse(false))
                .findFirst();
    }

    public static void removeShop(ServerLevel level, ShopData shop) {
        load(level.getServer());
        SHOPS.removeIf(existing -> existing.dimension().equals(shop.dimension()) && existing.chestPos().equals(shop.chestPos()) && existing.signPos().equals(shop.signPos()));
        save(level.getServer());
    }

    public static boolean isActive(ServerLevel level, ShopData shop) {
        return isChest(level, shop.chestPos()) && isSign(level, shop.signPos()) && countInContainer(chestContainer(level, shop.chestPos()), shop.outputItemId()) >= shop.outputAmount();
    }

    public static boolean hasKey(Player player, ShopData shop) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getCustomName() == null) {
            return false;
        }
        return hash(held.getCustomName().getString()).equals(shop.passwordHash());
    }

    public static TradeResult trade(ServerLevel level, Player customer, ShopData shop) {
        if (!isChest(level, shop.chestPos()) || !isSign(level, shop.signPos())) {
            return TradeResult.SHOP_INACTIVE;
        }
        if (customer.getUUID().equals(shop.ownerId())) {
            return TradeResult.OWNER;
        }
        Item input = validateItemId(shop.inputItemId()).orElse(null);
        Item output = validateItemId(shop.outputItemId()).orElse(null);
        if (input == null || output == null) {
            return TradeResult.SHOP_INACTIVE;
        }
        Inventory inventory = customer.getInventory();
        Container chest = chestContainer(level, shop.chestPos());
        if (chest == null) {
            return TradeResult.SHOP_INACTIVE;
        }
        if (countInInventory(inventory, input) < shop.inputAmount()) {
            return TradeResult.NO_PAYMENT;
        }
        if (countInContainer(chest, shop.outputItemId()) < shop.outputAmount()) {
            return TradeResult.OUT_OF_STOCK;
        }
        if (!canFit(chest, new ItemStack(input, shop.inputAmount()))) {
            return TradeResult.CHEST_FULL;
        }
        if (!canFit(inventory, new ItemStack(output, shop.outputAmount()))) {
            return TradeResult.PLAYER_FULL;
        }

        removeFromInventory(inventory, input, shop.inputAmount());
        removeFromContainer(chest, output, shop.outputAmount());
        addToContainer(chest, new ItemStack(input, shop.inputAmount()));
        inventory.add(new ItemStack(output, shop.outputAmount()));
        chest.setChanged();
        inventory.setChanged();
        return TradeResult.SUCCESS;
    }

    public static Optional<BlockPos> attachedChest(Level level, BlockPos signPos) {
        var state = level.getBlockState(signPos);
        if (!(state.getBlock() instanceof SignBlock)) {
            return Optional.empty();
        }
        if (state.getBlock() instanceof WallSignBlock) {
            BlockPos attached = signPos.relative(state.getValue(WallSignBlock.FACING).getOpposite());
            return isChest(level, attached) ? Optional.of(attached) : Optional.empty();
        }
        BlockPos below = signPos.below();
        return isChest(level, below) ? Optional.of(below) : Optional.empty();
    }

    public static boolean isChest(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ChestBlock;
    }

    public static boolean isSign(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SignBlock;
    }

    private static Container chestContainer(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, level, pos, true);
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container ? container : null;
    }

    private static Optional<BlockPos> connectedChest(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return Optional.empty();
        }
        BlockPos connected = ChestBlock.getConnectedBlockPos(pos, state);
        return isChest(level, connected) ? Optional.of(connected) : Optional.empty();
    }

    private static int countInInventory(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countInContainer(Container container, String itemId) {
        Item item = validateItemId(itemId).orElse(null);
        if (container == null || item == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeFromInventory(Inventory inventory, Item item, int amount) {
        removeFromContainer(inventory, item, amount);
    }

    private static void removeFromContainer(Container container, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.getItem() == item) {
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                if (stack.isEmpty()) {
                    container.setItem(slot, ItemStack.EMPTY);
                }
                remaining -= removed;
            }
        }
        container.setChanged();
    }

    private static boolean addToContainer(Container container, ItemStack toAdd) {
        ItemStack remaining = toAdd.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack stack = remaining.copyWithCount(moved);
                container.setItem(slot, stack);
                remaining.shrink(moved);
            }
        }
        container.setChanged();
        return remaining.isEmpty();
    }

    private static boolean canFit(Container container, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                remaining.shrink(Math.min(remaining.getCount(), remaining.getMaxStackSize()));
            } else if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                remaining.shrink(Math.max(0, existing.getMaxStackSize() - existing.getCount()));
            }
        }
        return remaining.isEmpty();
    }

    private static String dimensionId(Level level) {
        return level.dimension().identifier().toString();
    }

    private static Path storagePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve("shopmod_shops.json");
    }

    private static void load(MinecraftServer server) {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = storagePath(server);
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            List<StoredShop> stored = GSON.fromJson(reader, STORED_SHOP_LIST);
            if (stored != null) {
                stored.stream().map(StoredShop::toData).forEach(SHOPS::add);
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to load Shopmod shops from {}", path, e);
        }
    }

    private static void save(MinecraftServer server) {
        Path path = storagePath(server);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(SHOPS.stream().map(StoredShop::fromData).toList(), writer);
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to save Shopmod shops to {}", path, e);
        }
    }

    private static String hash(String password) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private record StoredShop(String dimension, int chestX, int chestY, int chestZ, int signX, int signY, int signZ, String ownerName,
            String ownerId, String inputItemId, int inputAmount, String outputItemId, int outputAmount, String passwordHash) {
        private static StoredShop fromData(ShopData data) {
            return new StoredShop(data.dimension(), data.chestPos().getX(), data.chestPos().getY(), data.chestPos().getZ(), data.signPos().getX(),
                    data.signPos().getY(), data.signPos().getZ(), data.ownerName(), data.ownerId().toString(), data.inputItemId(), data.inputAmount(),
                    data.outputItemId(), data.outputAmount(), data.passwordHash());
        }

        private ShopData toData() {
            return new ShopData(dimension, new BlockPos(chestX, chestY, chestZ), new BlockPos(signX, signY, signZ), ownerName, UUID.fromString(ownerId),
                    inputItemId, inputAmount, outputItemId, outputAmount, passwordHash);
        }
    }
}
