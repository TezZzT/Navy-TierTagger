package com.tezzt.tiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tezzt.tiertagger.config.TierTaggerConfig;
import com.tezzt.tiertagger.model.GameMode;
import com.tezzt.tiertagger.model.PlayerInfo;
import com.tezzt.tiertagger.util.RankRenderer;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.uku3lig.ukulib.utils.PlayerArgumentType;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TierTagger implements ModInitializer {
    public static final String MOD_ID = "navy-tiertagger";
    private static final String UPDATE_URL_FORMAT = "https://api.modrinth.com/v2/project/%s/version?game_versions=%s";
    public static final Gson GSON = new GsonBuilder().create();

    private static TierTaggerConfig config;
    private static final File CONFIG_FILE = new File(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toFile(), "navy-tiertagger.json");

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
        // Load Config
        loadConfig();
        TierCache.init();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal(MOD_ID)
                        .then(argument("player", PlayerArgumentType.player())
                                .executes(TierTagger::displayTierInfo))
                        .then(literal("reload").executes(context -> {
                            loadConfig();
                            context.getSource().sendFeedback(
                                    Component.literal("Reloaded config!").withStyle(ChatFormatting.GREEN));
                            return 1;
                        }))));

        // Use Fabric KeyBindingHelper instead of Ukutils
        KeyMapping gamemodeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "navytiertagger.keybind.gamemode",
                GLFW.GLFW_KEY_UNKNOWN,
                "key.category.navytiertagger"));

        // Register client tick event to check key press?
        // Or Ukutils registered it with a callback. Fabric KeyBinding is just
        // registration.
        // We need to poll it.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (gamemodeKey.consumeClick()) {
                GameMode next = TierCache.findNextMode(getConfig().getGameMode());
                getConfig().setGameMode(next.id());
                saveConfig();
                if (client.player != null) {
                    Component message = Component.literal("Displayed gamemode: ").append(next.asStyled(false));
                    client.player.displayClientMessage(message, true);
                }
            }
        });

        checkForUpdates();

        // Initialize Navy API cache
        com.tezzt.tiertagger.util.NavyDataCache.initialize(client);
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = new Gson().fromJson(reader, TierTaggerConfig.class);
            } catch (Exception e) {
                e.printStackTrace();
                config = new TierTaggerConfig();
            }
        } else {
            config = new TierTaggerConfig();
            saveConfig();
        }
    }

    public static TierTaggerConfig getConfig() {
        if (config == null)
            loadConfig();
        return config;
    }

    // Alias for mixins that used getManager().getConfig()
    // We can't easily mock getManager() unless we create a fake manager class.
    // Instead I'll just change Mixins to use TierTagger.getConfig() via previous
    // steps.
    // But user code used getManager().getConfig().
    // I will add a static helper if needed or assume I changed mixins.
    // Step 573/574 changed mixins to use TierTagger.getManager().getConfig().
    // I MUST Change them back to TierTagger.getConfig().

    public static Component appendTier(UUID uuid, Component text) {
        String name = Optional.ofNullable(Minecraft.getInstance().level)
                .map(l -> l.getPlayerByUUID(uuid))
                .map(p -> p.getGameProfile().getName())
                .orElse(null);

        if (name == null)
            return text;

        TierTaggerConfig config = getConfig();
        Optional<PlayerInfo> playerInfo = TierCache.getPlayerInfo(uuid, name);

        Component tierComponent = switch (config.getDisplayMode()) {
            case SELECTED -> playerInfo
                    .map(info -> {
                        PlayerInfo.Ranking ranking = info.rankings().get(config.getGameMode().id());
                        if (ranking == null)
                            return null;
                        return RankRenderer.formatPlayerDisplay(info, ranking,
                                config.getGameMode(), config.isShowIcons());
                    })
                    .orElse(null);
            case HIGHEST -> playerInfo
                    .flatMap(info -> PlayerInfo.getHighestRanking(info.rankings())
                            .map(entry -> RankRenderer.formatPlayerDisplay(info,
                                    entry.ranking(), entry.mode(), config.isShowIcons())))
                    .orElse(null);
            case ALL -> playerInfo
                    .map(info -> {
                        MutableComponent allTiers = Component.empty();

                        // Add indicators first
                        if (info.isBanned() && config.isShowBanIndicator()) {
                            allTiers.append(RankRenderer.getBanIndicator(info.banInfo()));
                        }
                        if (info.staffRole() != null && config.isShowStaffRanks()) {
                            allTiers.append(
                                    RankRenderer.getStaffRankComponent(info.staffRole()));
                        }

                        info.rankings().forEach((m, r) -> {
                            if (!allTiers.getSiblings().isEmpty())
                                allTiers.append(Component.literal(" ").withStyle(ChatFormatting.GRAY));

                            allTiers.append(RankRenderer.formatRanking(r,
                                    TierCache.findModeOrUgly(m), config.isShowIcons()));
                        });
                        return allTiers.getSiblings().isEmpty() ? null : allTiers;
                    })
                    .orElse(null);
        };

        if (tierComponent != null) {
            MutableComponent following = Component.empty();
            if (config.isShowRegion() && playerInfo.isPresent() && playerInfo.get().region() != null) {
                following.append(Component.literal("[" + playerInfo.get().region() + "] ")
                        .withStyle(s -> s.withColor(0xD8B4FE)));
            }
            following.append(tierComponent.copy());
            following.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            return following.append(text);
        }

        return text;
    }

    public static Optional<PlayerInfo.NamedRanking> getPlayerTier(UUID uuid, String name) {
        GameMode mode = getConfig().getGameMode();

        return TierCache.getPlayerInfo(uuid, name)
                .map(info -> {
                    PlayerInfo.Ranking ranking = info.rankings().get(mode.id());
                    if (ranking != null) {
                        return ranking.asNamed(mode);
                    }
                    return null;
                });
    }

    private static int displayTierInfo(CommandContext<FabricClientCommandSource> ctx) {
        PlayerArgumentType.PlayerSelector selector = ctx.getArgument("player", PlayerArgumentType.PlayerSelector.class);
        String name = selector.name();

        Optional<UUID> uuid = ctx.getSource().getWorld().players().stream()
                .filter(p -> p.getScoreboardName().equalsIgnoreCase(name)
                        || p.getStringUUID().equalsIgnoreCase(name))
                .findFirst()
                .map(Entity::getUUID);

        Optional<PlayerInfo> playerInfo = uuid.flatMap(u -> TierCache.getPlayerInfo(u, name));

        if (playerInfo.isPresent()) {
            ctx.getSource().sendFeedback(printPlayerInfo(name, playerInfo.get()));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[TierTagger] Searching..."));
            TierCache.searchPlayer(name)
                    .thenAccept(p -> Minecraft.getInstance().execute(
                            () -> ctx.getSource().sendFeedback(printPlayerInfo(name, p))))
                    .exceptionally(t -> {
                        ctx.getSource().sendError(Component.literal("Could not find player " + name));
                        return null;
                    });
        }

        return 0;
    }

    private static Component printPlayerInfo(String name, PlayerInfo info) {
        if (info == null || info.rankings().isEmpty()) {
            return Component.literal(name + " does not have any tiers.");
        } else {
            MutableComponent text = Component.empty().append("=== Rankings for " + name);
            if (info.region() != null) {
                text.append(" (" + info.region() + ")");
            }
            if (info.isBanned()) {
                text.append(" ").append(RankRenderer.getBanIndicator(info.banInfo()));
            }
            if (info.staffRole() != null) {
                text.append(" ").append(RankRenderer.getStaffRankComponent(info.staffRole()));
            }
            text.append(" ===");

            info.rankings().forEach((m, r) -> {
                if (m == null)
                    return;
                GameMode mode = TierCache.findModeOrUgly(m);
                Component tierText = RankRenderer.getRankingText(r, true);
                text.append(Component.literal("\n").append(mode.asStyled(true)).append(": ").append(tierText));
            });

            return text;
        }
    }

    public static int getTierColor(String tier) {
        if (tier.startsWith("R")) {
            return getConfig().getRetiredColor();
        } else {
            return getConfig().getTierColors().getOrDefault(tier, 0xD3D3D3);
        }
    }

    private static void checkForUpdates() {
        String versionParam = "[\"%s\"]".formatted(SharedConstants.getCurrentVersion().id());
        String fullUrl = UPDATE_URL_FORMAT.formatted(MOD_ID, URLEncoder.encode(versionParam, StandardCharsets.UTF_8));

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
