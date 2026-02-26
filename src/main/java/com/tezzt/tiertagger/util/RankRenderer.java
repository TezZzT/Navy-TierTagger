package com.tezzt.tiertagger.util;

import com.tezzt.tiertagger.TierTagger;
import com.tezzt.tiertagger.model.GameMode;
import com.tezzt.tiertagger.model.PlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class RankRenderer {
    private RankRenderer() {
    }

    public static Component formatRanking(PlayerInfo.Ranking ranking, GameMode mode, boolean showIcons) {
        MutableComponent tierText = (MutableComponent) getRankingText(ranking, false);

        if (showIcons && mode != null && mode.icon().isPresent()) {
            int color = TierTagger.getConfig().isShowTierColor()
                    ? TierTagger.getTierColor(tierText.getString())
                    : 0xFFFFFFFF;
            return mode.iconWithColor(color).append(Component.literal(" ")).append(tierText);
        } else {
            return tierText;
        }
    }

    public static Component getRankingText(PlayerInfo.Ranking ranking, boolean showPeak) {
        if (ranking.retired() && ranking.peakTier() != null && ranking.peakPos() != null) {
            return getTierText(ranking.peakTier(), ranking.peakPos(), true);
        } else {
            MutableComponent tierText = getTierText(ranking.tier(), ranking.pos(), false);

            if (showPeak && ranking.comparablePeak() < ranking.comparableTier()) {
                tierText.append(Component.literal(" (peak: ").withStyle(s -> s.withColor(ChatFormatting.GRAY)))
                        .append(getTierText(ranking.peakTier(), ranking.peakPos(), false))
                        .append(Component.literal(")").withStyle(s -> s.withColor(ChatFormatting.GRAY)));
            }

            return tierText;
        }
    }

    public static MutableComponent getTierText(int tier, int pos, boolean retired) {
        StringBuilder text = new StringBuilder();
        if (retired)
            text.append("R");
        text.append(pos == 0 ? "H" : "L").append("T").append(tier);

        int color = TierTagger.getTierColor(text.toString());
        return Component.literal(text.toString()).withStyle(s -> s.withColor(color));
    }

    public static Component getBanIndicator(NavyDataCache.BanInfo info) {
        // Use ☠ (Skull and Crossbones) instead of 💀
        // We can color it differently or add a bold/underline for cheaters if desired
        MutableComponent component = Component.literal("☠ ").withStyle(s -> s.withColor(ChatFormatting.RED));
        if (info != null && info.isCheater()) {
            component.withStyle(ChatFormatting.BOLD);
        }
        return component;
    }

    public static Component getStaffRankComponent(NavyDataCache.StaffRole staffRole) {
        if (staffRole == null) {
            return Component.empty();
        }

        return Component.literal("[" + staffRole.name() + "] ")
                .withStyle(s -> s.withColor(staffRole.color()).withBold(true));
    }

    public static Component formatPlayerDisplay(PlayerInfo playerInfo, PlayerInfo.Ranking ranking, GameMode mode,
            boolean showIcons) {
        MutableComponent result = Component.empty();

        // Add ban indicator if player is banned
        if (playerInfo.isBanned() && TierTagger.getConfig().isShowBanIndicator()) {
            result.append(getBanIndicator(playerInfo.banInfo()));
        }

        // Add staff rank if enabled and present
        if (playerInfo.staffRole() != null && TierTagger.getConfig().isShowStaffRanks()) {
            result.append(getStaffRankComponent(playerInfo.staffRole()));
        }

        // Add tier ranking
        result.append(formatRanking(ranking, mode, showIcons));

        return result;
    }
}
