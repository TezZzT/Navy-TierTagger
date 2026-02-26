package com.tezzt.tiertagger.tierlist;

import com.tezzt.tiertagger.TierCache;
import com.tezzt.tiertagger.model.PlayerInfo;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.utils.Ukutils;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerSearchScreen extends Screen {
    private EditBox textField;
    private Button searchButton;

    private boolean searching = false;
    private CompletableFuture<Void> future = null;

    private final Screen parent;

    public PlayerSearchScreen(Screen parent) {
        super(Component.translatable("tiertagger.search.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String username = I18n.get("tiertagger.search.user");
        this.textField = this
                .addWidget(new EditBox(this.font, this.width / 2 - 100, 116, 200, 20, Component.literal("")));
        this.textField.setMaxLength(32);
        this.textField.setHint(Component.literal(username));

        this.searchButton = this.addRenderableWidget(
                Button.builder(Component.translatable("tiertagger.search"), button -> this.loadAndShowProfile())
                        .bounds(this.width / 2 - 100, this.height / 4 + 120 + 36, 200, 20)
                        .tooltip(null)
                        .build());
        this.searchButton.active = false;

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, button -> {
                    if (this.future != null) {
                        this.future.cancel(true);
                    }
                    this.onClose();
                })
                        .bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20)
                        .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 80, 0xFFFFFF);
        this.textField.render(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        // EditBox doesn't have tick() in newer versions, but needed in older?
        // In 1.20.1 EditBox does NOT have tick(). Screen.tick() calls children? No.
        // We can skip ticking the editbox if it doesn't need it.
        // this.textField.tick();

        this.searchButton.active = this.textField.getValue().matches("[a-zA-Z0-9_]+") && !searching;
    }

    private void loadAndShowProfile() {
        String username = this.textField.getValue();
        this.searching = true;
        this.searchButton.setMessage(Component.translatable("tiertagger.search.loading"));

        this.future = TierCache.searchPlayer(username)
                .thenAccept(info -> {
                    if (info == null || info.rankings().isEmpty()) {
                        throw new RuntimeException("Player not found");
                    }
                    Minecraft.getInstance().execute(() -> {
                        GameProfile profile = new GameProfile(UUID.randomUUID(), username);
                        Minecraft.getInstance().setScreen(new PlayerInfoScreen(this, info, profile));
                    });
                })
                .whenComplete((v, t) -> {
                    if (t != null) {
                        // Fallback warning since system toast might be missing
                        this.searchButton.setMessage(Component.literal("Error: Not Found"));
                    } else {
                        this.searchButton.setMessage(Component.translatable("tiertagger.search"));
                    }
                    this.searching = false;
                });
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String string = this.textField.getValue();
        this.init(minecraft, width, height);
        this.textField.setValue(string);
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
