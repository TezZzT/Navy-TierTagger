package com.tezzt.tiertagger.config;

import com.tezzt.tiertagger.TierCache;
import com.tezzt.tiertagger.TierTagger;
import com.tezzt.tiertagger.model.GameMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class TierTaggerConfigScreen extends Screen {
        private final Screen parent;
        private final TierTaggerConfig config;

        public TierTaggerConfigScreen(Screen parent) {
                super(Component.literal("TierTagger Config"));
                this.parent = parent;
                this.config = TierTagger.getConfig();
        }

        @Override
        protected void init() {
                super.init();
                int y = 40;
                int center = this.width / 2;

                // Enabled
                this.addRenderableWidget(CycleButton.onOffBuilder(config.isEnabled())
                                .create(center - 100, y, 200, 20, Component.literal("Mod Enabled"),
                                                (b, v) -> config.setEnabled(v)));
                y += 24;

                // Game Mode
                this.addRenderableWidget(CycleButton.builder((GameMode m) -> Component.literal(m.title()))
                                .withValues(TierCache.getGamemodes())
                                .withInitialValue(TierCache.getGamemodes().stream()
                                                .filter(m -> m.id().equals(config.getGameMode()))
                                                .findFirst()
                                                .orElse(TierCache.getGamemodes().get(0)))
                                .create(center - 100, y, 200, 20, Component.literal("Game Mode"),
                                                (b, v) -> config.setGameMode(v.id())));
                y += 24;

                // Display Mode
                this.addRenderableWidget(
                                CycleButton.builder((TierTaggerConfig.DisplayMode m) -> Component.literal(m.name()))
                                                .withValues(TierTaggerConfig.DisplayMode.values())
                                                .withInitialValue(config.getDisplayMode())
                                                .create(center - 100, y, 200, 20, Component.literal("Display Mode"),
                                                                (b, v) -> config.setDisplayMode(v)));
                y += 24;

                // Show Icons
                this.addRenderableWidget(CycleButton.onOffBuilder(config.isShowIcons())
                                .create(center - 100, y, 200, 20, Component.literal("Show Icons"),
                                                (b, v) -> config.setShowIcons(v)));
                y += 24;

                // Show Tier Color
                this.addRenderableWidget(CycleButton.onOffBuilder(config.isShowTierColor())
                                .create(center - 100, y, 200, 20, Component.literal("Show Tier Color"),
                                                (b, v) -> config.setShowTierColor(v)));
                y += 24;

                // Show Staff Ranks
                this.addRenderableWidget(CycleButton.onOffBuilder(config.isShowStaffRanks())
                                .create(center - 100, y, 200, 20, Component.literal("Show Staff Ranks"),
                                                (b, v) -> config.setShowStaffRanks(v)));
                y += 24;

                // Show Ban Indicator
                this.addRenderableWidget(CycleButton.onOffBuilder(config.isShowBanIndicator())
                                .create(center - 100, y, 200, 20, Component.literal("Show Ban Indicator"),
                                                (b, v) -> config.setShowBanIndicator(v)));
                y += 24;

                this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                                .bounds(center - 100, this.height - 40, 200, 20)
                                .build());
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                if (this.minecraft != null && this.minecraft.level != null) {
                        this.renderTransparentBackground(graphics);
                } else {
                        this.renderBackground(graphics, mouseX, mouseY, delta);
                }
                graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
                super.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public void onClose() {
                TierTagger.saveConfig();
                if (this.parent != null) {
                        this.minecraft.setScreen(this.parent);
                } else {
                        super.onClose();
                }
        }
}
