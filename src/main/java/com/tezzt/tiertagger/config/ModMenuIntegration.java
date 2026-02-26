package com.tezzt.tiertagger.config;

import com.tezzt.tiertagger.TierTagger;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TierTaggerConfigScreen::new;
    }
}
