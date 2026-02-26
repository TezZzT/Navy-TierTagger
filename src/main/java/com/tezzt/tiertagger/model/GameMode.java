package com.tezzt.tiertagger.model;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Optional;

public record GameMode(String id, String title) {
    public static final GameMode NONE = new GameMode("annoying_long_id_that_no_one_will_ever_use_just_to_make_sure",
            "§cNone§r");

    public static List<GameMode> fetchGamemodes() {
        return List.of(
                new GameMode("crystal", "Crystal PvP"),
                new GameMode("netherite", "Netherite Pot"),
                new GameMode("sword", "Sword PvP"));
    }

    public boolean isNone() {
        return this.id.equals(NONE.id);
    }

    private Pair<Character, TextColor> iconAndColor() {
        return switch (this.id) {
            case "crystal" -> Pair.of('\uE709', TextColor.fromRgb(0xadefff));
            case "netherite" -> Pair.of('\uE710', TextColor.fromRgb(0x4d4d4d));
            case "sword" -> Pair.of('\uE711', TextColor.fromRgb(0xffffff));
            default -> Pair.of('•', TextColor.fromLegacyFormat(ChatFormatting.WHITE));
        };
    }

    public Optional<Character> icon() {
        return Optional.of(this.iconAndColor().left());
    }

    public MutableComponent iconWithColor(int color) {
        Character iconChar = this.iconAndColor().left();
        return Component.literal(String.valueOf(iconChar))
                .withStyle(s -> s.withColor(TextColor.fromRgb(color)));
    }

    public Component asStyled(boolean withDefaultDot) {
        Pair<Character, TextColor> pair = this.iconAndColor();

        if (pair.right().getValue() == 0xFFFFFF && !withDefaultDot) {
            return Component.literal(this.title);
        } else {
            Component name = Component.literal(this.title).withStyle(s -> s.withColor(pair.right()));
            return Component.literal(pair.left() + " ").append(name);
        }
    }
}
