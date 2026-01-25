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
    private DisplayMode displayMode = DisplayMode.SELECTED;
    private boolean showRetired = true;
    private boolean showIcons = true;
    private boolean playerList = true;
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
            GameMode first = TierCache.getGamemodes().getFirst();
            if (!first.isNone())
                this.gameMode = first.id();
            return first;
        }
    }

    private static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xe8ba3a);
        colors.put("LT1", 0xd5b355);
        colors.put("HT2", 0xc4d3e7);
        colors.put("LT2", 0xa0a7b2);
        colors.put("HT3", 0xf89f5a);
        colors.put("LT3", 0xc67b42);
        colors.put("HT4", 0x81749a);
        colors.put("LT4", 0x655b79);
        colors.put("HT5", 0x8f82a8);
        colors.put("LT5", 0x655b79);

        return colors;
    }
}
