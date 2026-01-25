package com.tezzt.tiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tezzt.tiertagger.config.TierTaggerConfig;
import com.tezzt.tiertagger.model.GameMode;
import com.tezzt.tiertagger.model.PlayerInfo;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.uku3lig.ukulib.config.ConfigManager;
import net.uku3lig.ukulib.utils.PlayerArgumentType;
import net.uku3lig.ukulib.utils.Ukutils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TierTagger implements ModInitializer {
    public static final String MOD_ID = "tiertagger";
    // Place your Modrinth project ID here for update checking
    private static final String UPDATE_URL_FORMAT = "https://api.modrinth.com/v2/project/%s/version?game_versions=%s";

    public static final Gson GSON = new GsonBuilder().create();

    @Getter
    private static final ConfigManager<TierTaggerConfig> manager = ConfigManager.createDefault(TierTaggerConfig.class,
            MOD_ID);
    @Getter
    private static final Logger logger = LoggerFactory.getLogger(TierTagger.class);
    @Getter
    private static final HttpClient client = HttpClient.newHttpClient();

    // === version checker stuff ===
    @Getter
    private static Version latestVersion = null;
    private static final AtomicBoolean isObsolete = new AtomicBoolean(false);

    @Override
    public void onInitialize() {
        TierCache.init();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal(MOD_ID)
                        .then(argument("player", PlayerArgumentType.player())
                                .executes(TierTagger::displayTierInfo))));

        Ukutils.registerKeybinding(
                new KeyMapping("tiertagger.keybind.gamemode", GLFW.GLFW_KEY_UNKNOWN, "tiertagger.key"),
                mc -> {
                    GameMode next = TierCache.findNextMode(manager.getConfig().getGameMode());
                    manager.getConfig().setGameMode(next.id());

                    if (mc.player != null) {
                        Component message = Component.literal("Displayed gamemode: ").append(next.asStyled(false));
                        mc.player.displayClientMessage(message, true);
                    }
                });

        checkForUpdates();
    }

    public static Component appendTier(UUID uuid, Component text) {
        String name = Optional.ofNullable(Minecraft.getInstance().level)
                .map(l -> l.getPlayerByUUID(uuid))
                .map(p -> p.getGameProfile().getName())
                .orElse(null);

        if (name == null)
            return text;

        TierTaggerConfig config = manager.getConfig();
        Component tierComponent = switch (config.getDisplayMode()) {
            case SELECTED -> getPlayerTier(uuid, name)
                    .map(entry -> formatRanking(entry.ranking(), entry.mode()))
                    .orElse(null);
            case HIGHEST -> TierCache.getPlayerRankings(uuid, name)
                    .flatMap(PlayerInfo::getHighestRanking)
                    .map(entry -> formatRanking(entry.ranking(), entry.mode()))
                    .orElse(null);
            case ALL -> TierCache.getPlayerRankings(uuid, name)
                    .map(rankings -> {
                        MutableComponent allTiers = Component.empty();
                        rankings.forEach((m, r) -> {
                            if (!allTiers.getSiblings().isEmpty())
                                allTiers.append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
                            allTiers.append(formatRanking(r, TierCache.findModeOrUgly(m)));
                        });
                        return allTiers.getSiblings().isEmpty() ? null : allTiers;
                    })
                    .orElse(null);
        };

        if (tierComponent != null) {
            MutableComponent following = tierComponent.copy();
            following.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            return following.append(text);
        }

        return text;
    }

    public static Optional<PlayerInfo.NamedRanking> getPlayerTier(UUID uuid, String name) {
        GameMode mode = manager.getConfig().getGameMode();

        return TierCache.getPlayerRankings(uuid, name)
                .map(rankings -> {
                    PlayerInfo.Ranking ranking = rankings.get(mode.id());
                    if (ranking != null) {
                        return ranking.asNamed(mode);
                    }
                    return null;
                });
    }

    private static Component formatRanking(PlayerInfo.Ranking ranking, GameMode mode) {
        Component tierText = getRankingText(ranking, false);

        if (manager.getConfig().isShowIcons() && mode != null && mode.icon().isPresent()) {
            return Component.literal(mode.icon().get().toString()).append(tierText);
        } else {
            return tierText;
        }
    }

    private static MutableComponent getTierText(int tier, int pos, boolean retired) {
        StringBuilder text = new StringBuilder();
        if (retired)
            text.append("R");
        text.append(pos == 0 ? "H" : "L").append("T").append(tier);

        int color = TierTagger.getTierColor(text.toString());
        return Component.literal(text.toString()).withStyle(s -> s.withColor(color));
    }

    public static Component getRankingText(PlayerInfo.Ranking ranking, boolean showPeak) {
        if (ranking.retired() && ranking.peakTier() != null && ranking.peakPos() != null) {
            return getTierText(ranking.peakTier(), ranking.peakPos(), true);
        } else {
            MutableComponent tierText = getTierText(ranking.tier(), ranking.pos(), false);

            if (showPeak && ranking.comparablePeak() < ranking.comparableTier()) {
                // Peak tier is higher than current tier
                tierText.append(Component.literal(" (peak: ").withStyle(s -> s.withColor(ChatFormatting.GRAY)))
                        .append(getTierText(ranking.peakTier(), ranking.peakPos(), false))
                        .append(Component.literal(")").withStyle(s -> s.withColor(ChatFormatting.GRAY)));
            }

            return tierText;
        }
    }

    private static int displayTierInfo(CommandContext<FabricClientCommandSource> ctx) {
        PlayerArgumentType.PlayerSelector selector = ctx.getArgument("player", PlayerArgumentType.PlayerSelector.class);
        String name = selector.name();

        // Navy is name-based. We can try to get from cache first if we have a UUID from
        // world
        Optional<UUID> uuid = ctx.getSource().getWorld().players().stream()
                .filter(p -> p.getScoreboardName().equalsIgnoreCase(name)
                        || p.getStringUUID().equalsIgnoreCase(name))
                .findFirst()
                .map(Entity::getUUID);

        Optional<Map<String, PlayerInfo.Ranking>> rankings = uuid.flatMap(u -> TierCache.getPlayerRankings(u, name));

        if (rankings.isPresent()) {
            ctx.getSource().sendFeedback(printPlayerInfo(name, rankings.get()));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[TierTagger] Searching..."));
            TierCache.searchPlayer(name)
                    .thenAccept(p -> Minecraft.getInstance().execute(
                            () -> ctx.getSource().sendFeedback(printPlayerInfo(name, p.rankings()))))
                    .exceptionally(t -> {
                        ctx.getSource().sendError(Component.literal("Could not find player " + name));
                        return null;
                    });
        }

        return 0;
    }

    private static Component printPlayerInfo(String name, Map<String, PlayerInfo.Ranking> rankings) {
        if (rankings.isEmpty()) {
            return Component.literal(name + " does not have any tiers.");
        } else {
            MutableComponent text = Component.empty().append("=== Rankings for " + name + " ===");

            rankings.forEach((m, r) -> {
                if (m == null)
                    return;
                GameMode mode = TierCache.findModeOrUgly(m);
                Component tierText = getRankingText(r, true);
                text.append(Component.literal("\n").append(mode.asStyled(true)).append(": ").append(tierText));
            });

            return text;
        }
    }

    public static int getTierColor(String tier) {
        if (tier.startsWith("R")) {
            return manager.getConfig().getRetiredColor();
        } else {
            return manager.getConfig().getTierColors().getOrDefault(tier, 0xD3D3D3);
        }
    }

    private static void checkForUpdates() {
        String versionParam = "[\"%s\"]".formatted(SharedConstants.getCurrentVersion().getId());
        String fullUrl = UPDATE_URL_FORMAT.formatted(URLEncoder.encode(versionParam, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(fullUrl)).GET().build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    String body = r.body();
                    JsonArray array = GSON.fromJson(body, JsonArray.class);

                    if (!array.isEmpty()) {
                        JsonObject root = array.get(0).getAsJsonObject();

                        String versionName = root.get("name").getAsString();
                        if (versionName != null && versionName.toLowerCase(Locale.ROOT).startsWith("[o")) {
                            isObsolete.set(true);
                        }

                        String latestVer = root.get("version_number").getAsString();
                        try {
                            return Version.parse(latestVer);
                        } catch (VersionParsingException e) {
                            logger.warn("Could not parse version number {}", latestVer);
                        }
                    }

                    return null;
                })
                .exceptionally(t -> {
                    logger.warn("Error checking for updates", t);
                    return null;
                }).thenAccept(v -> {
                    logger.info("Found latest version {}", v.getFriendlyString());
                    latestVersion = v;
                });
    }

    public static boolean isObsolete() {
        return isObsolete.get();
    }
}
