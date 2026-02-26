package com.tezzt.tiertagger.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tezzt.tiertagger.TierTagger;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NavyDataCache {
    public record StaffRole(String name, int color) {
    }

    public record BanInfo(boolean isCheater, @Nullable String reason) {
    }

    private static final Map<String, BanInfo> banCache = new HashMap<>();
    private static final Map<String, StaffRole> staffCache = new HashMap<>();
    private static long lastBanUpdate = 0;
    private static long lastStaffUpdate = 0;
    private static final long CACHE_DURATION = 15 * 60 * 1000; // Increased to 15 minutes

    private static CompletableFuture<Void> banUpdateFuture = null;
    private static CompletableFuture<Void> staffUpdateFuture = null;

    public static synchronized CompletableFuture<Void> updateBanList(HttpClient client) {
        if (banUpdateFuture != null && !banUpdateFuture.isDone()) {
            return banUpdateFuture;
        }

        lastBanUpdate = System.currentTimeMillis();

        String endpoint = TierTagger.getConfig().getNavyApiUrl() + "/bans";
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        banUpdateFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(s -> {
                    try {
                        JsonObject root = TierTagger.GSON.fromJson(s, JsonObject.class);
                        if (root != null && root.has("active")) {
                            JsonArray active = root.getAsJsonArray("active");
                            synchronized (banCache) {
                                banCache.clear();
                                for (JsonElement element : active) {
                                    JsonObject ban = element.getAsJsonObject();
                                    String uuid = ban.has("uuid") && !ban.get("uuid").isJsonNull()
                                            ? ban.get("uuid").getAsString()
                                            : null;
                                    String nick = ban.has("nick") && !ban.get("nick").isJsonNull()
                                            ? ban.get("nick").getAsString().toLowerCase()
                                            : null;

                                    boolean isCheater = ban.has("is_cheater") && ban.get("is_cheater").getAsBoolean();
                                    String reason = ban.has("reason") && !ban.get("reason").isJsonNull()
                                            ? ban.get("reason").getAsString()
                                            : null;

                                    BanInfo info = new BanInfo(isCheater, reason);

                                    if (uuid != null)
                                        banCache.put(uuid, info);
                                    if (nick != null)
                                        banCache.put(nick, info);
                                }
                            }
                        }
                    } catch (Exception e) {
                        TierTagger.getLogger().warn("Error parsing ban list", e);
                    }
                })
                .exceptionally(t -> {
                    TierTagger.getLogger().warn("Error fetching ban list", t);
                    return null;
                });

        return banUpdateFuture;
    }

    public static synchronized CompletableFuture<Void> updateStaffList(HttpClient client) {
        if (staffUpdateFuture != null && !staffUpdateFuture.isDone()) {
            return staffUpdateFuture;
        }

        lastStaffUpdate = System.currentTimeMillis();

        String endpoint = TierTagger.getConfig().getNavyApiUrl() + "/staff";
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        staffUpdateFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(s -> {
                    try {
                        JsonArray roles = TierTagger.GSON.fromJson(s, JsonArray.class);
                        if (roles != null) {
                            synchronized (staffCache) {
                                staffCache.clear();
                                for (JsonElement roleElement : roles) {
                                    JsonObject role = roleElement.getAsJsonObject();
                                    String roleName = role.get("role_name").getAsString();
                                    String lowerRole = roleName.toLowerCase();

                                    // Solo procesar los roles exactos que pidió el usuario
                                    if (!lowerRole.equals("network founder") &&
                                            !lowerRole.equals("dev") &&
                                            !lowerRole.equals("admin") &&
                                            !lowerRole.equals("discord mod")) {
                                        continue;
                                    }

                                    String colorStr = role.has("role_colour") ? role.get("role_colour").getAsString()
                                            : null;
                                    int color = 0xFFFFFF; // Default white
                                    if (colorStr != null && colorStr.startsWith("#")) {
                                        try {
                                            color = Integer.parseInt(colorStr.substring(1), 16);
                                        } catch (NumberFormatException ignored) {
                                        }
                                    }

                                    StaffRole staffRole = new StaffRole(roleName, color);
                                    JsonArray members = role.getAsJsonArray("members");

                                    for (JsonElement memberElement : members) {
                                        JsonObject member = memberElement.getAsJsonObject();
                                        String uuid = member.has("uuid") && !member.get("uuid").isJsonNull()
                                                ? member.get("uuid").getAsString()
                                                : null;
                                        String nick = member.has("nick") && !member.get("nick").isJsonNull()
                                                ? member.get("nick").getAsString().toLowerCase()
                                                : null;

                                        if (uuid != null)
                                            staffCache.put(uuid, staffRole);
                                        if (nick != null)
                                            staffCache.put(nick, staffRole);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        TierTagger.getLogger().warn("Error parsing staff list", e);
                    }
                })
                .exceptionally(t -> {
                    TierTagger.getLogger().warn("Error fetching staff list", t);
                    return null;
                });

        return staffUpdateFuture;
    }

    @Nullable
    public static BanInfo getBanInfo(String uuid, String nick) {
        if (System.currentTimeMillis() - lastBanUpdate > CACHE_DURATION) {
            updateBanList(TierTagger.getClient());
        }

        synchronized (banCache) {
            if (uuid != null && banCache.containsKey(uuid))
                return banCache.get(uuid);
            if (nick != null && banCache.containsKey(nick.toLowerCase()))
                return banCache.get(nick.toLowerCase());
        }
        return null;
    }

    public static boolean isBanned(String uuid, String nick) {
        return getBanInfo(uuid, nick) != null;
    }

    @Nullable
    public static StaffRole getStaffRole(String uuid, String nick) {
        if (System.currentTimeMillis() - lastStaffUpdate > CACHE_DURATION) {
            updateStaffList(TierTagger.getClient());
        }

        synchronized (staffCache) {
            if (uuid != null && staffCache.containsKey(uuid))
                return staffCache.get(uuid);
            if (nick != null && staffCache.containsKey(nick.toLowerCase()))
                return staffCache.get(nick.toLowerCase());
        }
        return null;
    }

    public static void initialize(HttpClient client) {
        updateBanList(client).join();
        updateStaffList(client).join();
    }
}
