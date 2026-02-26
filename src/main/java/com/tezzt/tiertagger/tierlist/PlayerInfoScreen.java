package com.tezzt.tiertagger.tierlist;

import com.tezzt.tiertagger.model.GameMode;
import com.tezzt.tiertagger.model.PlayerInfo;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.RemotePlayer;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class PlayerInfoScreen extends Screen {
    private final PlayerInfo info;
    private final Screen parent;
    private final RemotePlayer dummyPlayer;

    public PlayerInfoScreen(Screen parent, PlayerInfo info, GameProfile profile) {
        super(Component.literal("Player Info"));
        this.parent = parent;
        this.info = info;
        this.dummyPlayer = new RemotePlayer(Minecraft.getInstance().level, profile);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.translatable("gui.back"), button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);

        // Draw player model
        // 1.21.1 signature: renderEntityInInventoryFollowsMouse(GuiGraphics, int x1,
        // int y1, int x2, int y2, int scale, float mouseXOffset, float mouseX, float
        // mouseY, LivingEntity entity)

        int x = this.width / 2 - 100;
        int y = this.height / 2 - 80;

        // Background box
        graphics.fill(x, y, x + 200, y + 160, 0xAA000000);

        // Render Entity
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 10, y + 10, x + 50, y + 130, 30, 0.0625F,
                mouseX, mouseY, this.dummyPlayer);

        // Info Text
        graphics.drawString(this.font, Component.literal(info.name()).withStyle(s -> s.withBold(true)), x + 60, y + 10,
                0xFFFFFF);
        if (info.region() != null) {
            graphics.drawString(this.font, "Region: " + info.region(), x + 60, y + 25, 0xAAAAAA);
        }

        graphics.drawString(this.font, "Global: #" + info.globalPos(), x + 60, y + 40, 0xFFFFFF);
        graphics.drawString(this.font, "Score: " + info.score(), x + 60, y + 50, 0xFFFFFF);

        // Render Tiers List
        int ty = y + 70;
        graphics.drawString(this.font, "Rankings:", x + 10, ty, 0xFFFF55);
        ty += 15;

        for (String modeId : info.rankings().keySet()) {
            if (ty > y + 150)
                break;
            PlayerInfo.Ranking r = info.rankings().get(modeId);
            graphics.drawString(this.font, modeId + ": " + r.tier() + " (#" + r.pos() + ")", x + 15, ty, 0xFFFFFF);
            ty += 12;
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }
}
