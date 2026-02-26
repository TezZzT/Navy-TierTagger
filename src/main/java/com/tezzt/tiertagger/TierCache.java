package com.tezzt.tiertagger;

import com.tezzt.tiertagger.model.GameMode;
import com.tezzt.tiertagger.model.PlayerInfo;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TierCache {
    private static final List<GameMode> GAMEMODES = new ArrayList<>();
    private static final Map<UUID, Optional<PlayerInfo>> TIERS = new ConcurrentHashMap<>();

    // Request Queue for rate-limiting
    private static final Queue<Runnable> REQUEST_QUEUE = new LinkedList<>();
    private static final long REQUEST_INTERVAL = 200; // 200ms between requests (5/sec)
    private static long lastRequestTime = 0;
    private static boolean isProcessingQueue = false;

    public static void init() {
        GAMEMODES.clear();
        GAMEMODES.addAll(GameMode.fetchGamemodes());
        TierTagger.getLogger().info("Initialized {} modes", GAMEMODES.size());
    }

    public static List<GameMode> getGamemodes() {
        if (GAMEMODES.isEmpty()) {
            return Collections.singletonList(GameMode.NONE);
        } else {
            return GAMEMODES;
        }
    }

    public static Optional<PlayerInfo> getPlayerInfo(UUID uuid, String name) {
        if (!TierTagger.getConfig().isEnabled())
            return Optional.empty();

        return TIERS.computeIfAbsent(uuid, u -> {
            queueRequest(() -> {
                PlayerInfo.getNavyRankings(TierTagger.getClient(), name).thenAccept(info -> {
                    TIERS.put(uuid, Optional.ofNullable(info));
                });
            });

            return Optional.empty();
        });
    }

    private static synchronized void queueRequest(Runnable request) {
        REQUEST_QUEUE.add(request);
        if (!isProcessingQueue) {
            isProcessingQueue = true;
            processQueue();
        }
    }

    private static void processQueue() {
        long now = System.currentTimeMillis();
        long delay = Math.max(0, lastRequestTime + REQUEST_INTERVAL - now);

        CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
            Runnable next;
            synchronized (TierCache.class) {
                next = REQUEST_QUEUE.poll();
                if (next == null) {
                    isProcessingQueue = false;
                    return;
                }
                lastRequestTime = System.currentTimeMillis();
            }

            try {
                next.run();
            } catch (Exception e) {
                TierTagger.getLogger().error("Error executing queued request", e);
            }

            processQueue();
        });
    }

    public static Optional<Map<String, PlayerInfo.Ranking>> getPlayerRankings(UUID uuid, String name) {
        return getPlayerInfo(uuid, name).map(PlayerInfo::rankings);
    }

    public static CompletableFuture<PlayerInfo> searchPlayer(String query) {
        return PlayerInfo.getNavyRankings(TierTagger.getClient(), query);
    }

    public static void clearCache() {
        TIERS.clear();
    }

    public static GameMode findNextMode(GameMode current) {
        if (GAMEMODES.isEmpty()) {
            return GameMode.NONE;
        } else {
            return GAMEMODES.get((GAMEMODES.indexOf(current) + 1) % GAMEMODES.size());
        }
    }

    public static Optional<GameMode> findMode(String id) {
        return GAMEMODES.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst();
    }

    public static GameMode findModeOrUgly(String id) {
        return findMode(id).orElseGet(() -> new GameMode(id, id));
    }

    private TierCache() {
    }
}
