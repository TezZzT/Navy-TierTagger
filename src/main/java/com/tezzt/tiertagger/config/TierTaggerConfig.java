package com.tezzt.tiertagger.config;

import com.google.gson.internal.LinkedTreeMap;
import com.tezzt.tiertagger.TierCache;
import com.tezzt.tiertagger.model.GameMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierTaggerConfig implements Serializable {
    private boolean enabled = true;
    private String gameMode = "crystal";
    private DisplayMode displayMode = DisplayMode.HIGHEST;
    private boolean showRetired = true;
    private boolean showIcons = true;
    private boolean showRegion = true;
    private boolean playerList = true;
    private boolean showTierColor = true;
    private boolean showStaffRanks = false;
    private boolean showBanIndicator = true;
    private int retiredColor = 0xa2d6ff;
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();

    public enum DisplayMode {
        SELECTED, HIGHEST, ALL
    }

    // === internal stuff ===

    private String navyApiUrl = "https://navytiers.com/api";

    public GameMode getGameMode() {
        Optional<GameMode> opt = TierCache.findMode(this.gameMode);
        if (opt.isPresent()) {
            return opt.get();
        } else {
            GameMode first = TierCache.getGamemodes().get(0);
            if (!first.isNone())
                this.gameMode = first.id();
            return first;
        }
    }

    private static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xFACC15);
        colors.put("LT1", 0xF8AE3E);
        colors.put("HT2", 0xF0F1FA);
        colors.put("LT2", 0x60A5FA);
        colors.put("HT3", 0xFB923C);
        colors.put("LT3", 0xF87171);
        colors.put("HT4", 0xA78BFA);
        colors.put("LT4", 0x4B0082);
        colors.put("HT5", 0x9CA3AF);
        colors.put("LT5", 0x4ADE80);

        return colors;
    }
}
