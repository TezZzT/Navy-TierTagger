package com.tezzt.tiertagger.tierlist;

import com.tezzt.tiertagger.TierTagger;
import com.tezzt.tiertagger.model.GameMode;
import com.tezzt.tiertagger.model.PlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.screen.CloseableScreen;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class PlayerInfoScreen extends CloseableScreen {
    private final PlayerInfo info;
    private final PlayerSkinWidget skin;

    public PlayerInfoScreen(Screen parent, PlayerInfo info, PlayerSkinWidget skin) {
        super(Component.literal("Player Info"), parent);
        this.info = info;
        this.skin = skin;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, button -> Minecraft.getInstance().setScreen(parent))
                        .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
                        .build());

        this.addRenderableWidget(this.skin);

        int rankingHeight = this.info.rankings().size() * 11;
        int infoHeight = 10; // Only "Rankings:" line
        int startY = (this.height - infoHeight - rankingHeight) / 2;
        int rankingY = startY + infoHeight;

        for (PlayerInfo.NamedRanking namedRanking : this.info.getSortedTiers()) {
            if (namedRanking.mode() == null)
                continue;

            StringWidget text = new StringWidget(formatTier(namedRanking.mode(), namedRanking.ranking()), this.font);
            text.setX(this.width / 2 + 5);
            text.setY(rankingY);

            String date = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
                    .format(Instant.ofEpochSecond(namedRanking.ranking().attained()));
            Component tooltipText = Component.literal("Attained: " + date).withStyle(ChatFormatting.GRAY);
            text.setTooltip(Tooltip.create(tooltipText));
            this.addRenderableWidget(text);
            rankingY += 11;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.info.name() + "'s profile", this.width / 2, 20, 0xFFFFFFFF);

        int rankingHeight = this.info.rankings().size() * 11;
        int infoHeight = 10;
        int startY = (this.height - infoHeight - rankingHeight) / 2;

        graphics.drawString(this.font, "Rankings:", this.width / 2 + 5, startY, 0xFFFFFFFF);
    }

    private Component formatTier(@NotNull GameMode gamemode, PlayerInfo.Ranking ranking) {
        Component tierText = TierTagger.getRankingText(ranking, true);

        return Component.empty()
                .append(gamemode.asStyled(true))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(tierText);
    }
}
