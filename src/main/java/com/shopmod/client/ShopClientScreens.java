package com.shopmod.client;

import com.shopmod.network.ShopNetworking.CancelConfigPayload;
import com.shopmod.network.ShopNetworking.ConfirmTradePayload;
import com.shopmod.network.ShopNetworking.OpenConfigPayload;
import com.shopmod.network.ShopNetworking.OpenTradePayload;
import com.shopmod.network.ShopNetworking.SubmitConfigPayload;
import com.shopmod.shop.ShopManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ShopClientScreens {
    private ShopClientScreens() {
    }

    public static void openConfig(OpenConfigPayload payload) {
        Minecraft.getInstance().setScreen(new ConfigScreen(payload.signPos(), payload.chestPos()));
    }

    public static void openTrade(OpenTradePayload payload) {
        Minecraft.getInstance().setScreen(new TradeScreen(payload));
    }

    private static final class ConfigScreen extends Screen {
        private final BlockPos signPos;
        private final BlockPos chestPos;
        private EditBox inputId;
        private EditBox inputAmount;
        private EditBox outputId;
        private EditBox outputAmount;
        private EditBox password;
        private Button confirm;
        private String validationMessage = "Enter fully namespaced item IDs, positive amounts, and a password.";

        private ConfigScreen(BlockPos signPos, BlockPos chestPos) {
            super(Component.literal("Create Item Shop"));
            this.signPos = signPos;
            this.chestPos = chestPos;
        }

        @Override
        protected void init() {
            int left = this.width / 2 - 130;
            int y = 48;
            this.inputId = addBox(left, y, "minecraft:diamond");
            this.inputAmount = addBox(left + 170, y, "1");
            this.outputId = addBox(left, y + 42, "minecraft:apple");
            this.outputAmount = addBox(left + 170, y + 42, "1");
            this.password = addBox(left, y + 84, "");
            this.password.setMaxLength(64);

            this.confirm = this.addRenderableWidget(Button.builder(Component.literal("Confirm"), button -> submit())
                    .bounds(left, y + 130, 120, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel())
                    .bounds(left + 140, y + 130, 120, 20).build());
            validate();
        }

        private EditBox addBox(int x, int y, String value) {
            EditBox box = new EditBox(this.font, x, y, 150, 20, Component.empty());
            this.addWidget(box);
            box.setValue(value);
            box.setResponder(ignored -> validate());
            return box;
        }

        private void validate() {
            if (this.confirm == null) {
                return;
            }
            boolean validInput = ShopManager.validateItemId(inputId.getValue()).isPresent();
            boolean validOutput = ShopManager.validateItemId(outputId.getValue()).isPresent();
            int inputCount = parseAmount(inputAmount.getValue());
            int outputCount = parseAmount(outputAmount.getValue());
            boolean validPassword = !password.getValue().isBlank();
            inputId.setTextColor(validInput ? 0xFFE0E0E0 : 0xFFFF5555);
            outputId.setTextColor(validOutput ? 0xFFE0E0E0 : 0xFFFF5555);
            inputAmount.setTextColor(inputCount > 0 ? 0xFFE0E0E0 : 0xFFFF5555);
            outputAmount.setTextColor(outputCount > 0 ? 0xFFE0E0E0 : 0xFFFF5555);
            password.setTextColor(validPassword ? 0xFFE0E0E0 : 0xFFFF5555);
            this.confirm.active = validInput && validOutput && inputCount > 0 && outputCount > 0 && validPassword;
            if (!validInput || !validOutput) {
                validationMessage = "Invalid item ID. Use a real namespaced ID like minecraft:diamond.";
            } else if (inputCount < 1 || outputCount < 1) {
                validationMessage = "Amounts must be whole numbers greater than 0.";
            } else if (!validPassword) {
                validationMessage = "Password cannot be blank.";
            } else {
                validationMessage = "Ready to register this shop.";
            }
        }

        private int parseAmount(String value) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private void submit() {
            ClientPacketDistributor.sendToServer(new SubmitConfigPayload(signPos, chestPos, inputId.getValue().trim(), parseAmount(inputAmount.getValue()),
                    outputId.getValue().trim(), parseAmount(outputAmount.getValue()), password.getValue()));
            this.onClose();
        }

        private void cancel() {
            ClientPacketDistributor.sendToServer(new CancelConfigPayload(signPos));
            this.onClose();
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(null);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            int left = this.width / 2 - 130;
            graphics.centeredText(this.font, this.title, this.width / 2, 22, 0xFFFFFFFF);
            graphics.text(this.font, "Payment item ID", left, 36, 0xFFA0A0A0);
            graphics.text(this.font, "Pay amount", left + 170, 36, 0xFFA0A0A0);
            graphics.text(this.font, "Output item ID", left, 78, 0xFFA0A0A0);
            graphics.text(this.font, "Output amount", left + 170, 78, 0xFFA0A0A0);
            graphics.text(this.font, "Chest key password", left, 120, 0xFFA0A0A0);
            this.inputId.extractRenderState(graphics, mouseX, mouseY, partialTick);
            this.inputAmount.extractRenderState(graphics, mouseX, mouseY, partialTick);
            this.outputId.extractRenderState(graphics, mouseX, mouseY, partialTick);
            this.outputAmount.extractRenderState(graphics, mouseX, mouseY, partialTick);
            this.password.extractRenderState(graphics, mouseX, mouseY, partialTick);
            graphics.text(this.font, validationMessage, left, 158, 0xFFFFFF55);
        }
    }

    private static final class TradeScreen extends Screen {
        private final OpenTradePayload payload;

        private TradeScreen(OpenTradePayload payload) {
            super(Component.literal("Confirm Trade"));
            this.payload = payload;
        }

        @Override
        protected void init() {
            int left = this.width / 2 - 100;
            int y = this.height / 2 + 20;
            this.addRenderableWidget(Button.builder(Component.literal("Confirm"), button -> {
                ClientPacketDistributor.sendToServer(new ConfirmTradePayload(payload.chestPos()));
                this.onClose();
            }).bounds(left, y, 90, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose()).bounds(left + 110, y, 90, 20).build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            int left = this.width / 2 - 130;
            int y = this.height / 2 - 50;
            graphics.centeredText(this.font, this.title, this.width / 2, y - 28, 0xFFFFFFFF);
            graphics.text(this.font, "You pay: " + payload.inputAmount() + " x " + payload.inputItemId(), left, y, 0xFFFFFFFF);
            graphics.text(this.font, "You receive: " + payload.outputAmount() + " x " + payload.outputItemId(), left, y + 22, 0xFFFFFFFF);
        }
    }
}
