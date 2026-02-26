package com.tezzt.tiertagger.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.tezzt.tiertagger.TierCache;
import com.tezzt.tiertagger.TierTagger;
import com.tezzt.tiertagger.util.NavyDataCache;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public record PlayerInfo(String uuid, String name, @Nullable String region, int score, int globalPos,
        Map<String, Ranking> rankings, @Nullable NavyDataCache.StaffRole staffRole,
        @Nullable NavyDataCache.BanInfo banInfo) {
    public record Ranking(int tier, int pos, @Nullable @SerializedName("peak_tier") Integer peakTier,
            @Nullable @SerializedName("peak_pos") Integer peakPos, long attained,
            boolean retired) {

        public int comparableTier() {
            return tier * 2 + pos;
        }

        public int comparablePeak() {
            if (peakTier == null || peakPos == null) {
                return Integer.MAX_VALUE;
            } else {
                return peakTier * 2 + peakPos;
            }
        }

        public NamedRanking asNamed(GameMode mode) {
            return new NamedRanking(mode, this);
        }
    }

    public record NamedRanking(@Nullable GameMode mode, Ranking ranking) {
    }

    public static CompletableFuture<PlayerInfo> getNavyRankings(HttpClient client, String name) {
        String endpoint = TierTagger.getConfig().getNavyApiUrl() + "/tierlist/overall?nick=" + name;
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(s -> {
                    JsonObject root = TierTagger.GSON.fromJson(s, JsonObject.class);
                    if (root == null || !root.has("data") || root.get("data").isJsonNull())
                        return null;

                    JsonObject data = root.getAsJsonObject("data");
                    String region = data.has("region") && !data.get("region").isJsonNull()
                            ? data.get("region").getAsString()
                            : null;
                    int score = data.has("score") && !data.get("score").isJsonNull() ? data.get("score").getAsInt() : 0;
                    int globalPos = data.has("pos") && !data.get("pos").isJsonNull() ? data.get("pos").getAsInt() : 0;

                    // Extract UUID for ban/staff checks
                    String uuid = data.has("uuid") && !data.get("uuid").isJsonNull()
                            ? data.get("uuid").getAsString()
                            : null;

                    // Check ban and staff status from Navy API cache
                    NavyDataCache.BanInfo banInfo = com.tezzt.tiertagger.util.NavyDataCache.getBanInfo(uuid, name);
                    NavyDataCache.StaffRole staffRole = com.tezzt.tiertagger.util.NavyDataCache.getStaffRole(uuid,
                            name);

                    if (!data.has("games"))
                        return new PlayerInfo(uuid, name, region, score, globalPos, Collections.emptyMap(), staffRole,
                                banInfo);

                    JsonObject games = data.getAsJsonObject("games");
                    Map<String, Ranking> rankings = new HashMap<>();

                    games.entrySet().forEach(e -> {
                        String mode = e.getKey();
                        JsonObject gameData = e.getValue().getAsJsonObject();
                        if (gameData.has("tier")) {
                            String tierStr = gameData.get("tier").getAsString();
                            Ranking ranking = parseNavyTier(tierStr);
                            if (ranking != null) {
                                rankings.put(mode, ranking);
                            }
                        }
                    });

                    return new PlayerInfo(uuid, name, region, score, globalPos, rankings, staffRole, banInfo);
                })
                .whenComplete((i, t) -> {
                    if (t != null)
                        TierTagger.getLogger().warn("Error getting Navy rankings for {}", name, t);
                });
    }

    public boolean isBanned() {
        return banInfo != null;
    }

    private static Ranking parseNavyTier(String tierStr) {
        try {
            boolean high = tierStr.startsWith("H");
            int tier = Integer.parseInt(tierStr.substring(1));
            int pos = high ? 0 : 1;
            return new Ranking(tier, pos, null, null, System.currentTimeMillis(), false);
        } catch (Exception e) {
            return null;
        }
    }

    public static Optional<NamedRanking> getHighestRanking(Map<String, Ranking> rankings) {
        return rankings.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .min(Comparator.comparingInt(e -> e.getValue().comparableTier()))
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())));
    }

    public List<NamedRanking> getSortedTiers() {
        List<NamedRanking> tiers = new ArrayList<>(this.rankings.entrySet().stream()
                .map(e -> e.getValue().asNamed(TierCache.findModeOrUgly(e.getKey())))
                .toList());

        tiers.sort(Comparator.comparing((NamedRanking a) -> a.ranking.retired, Boolean::compare)
                .thenComparingInt(a -> a.ranking.tier)
                .thenComparingInt(a -> a.ranking.pos));

        return tiers;
    }
}
