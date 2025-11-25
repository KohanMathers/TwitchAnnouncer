package me.kmathers.twitchannouncer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TwitchManager {
    private static final Logger logger = LoggerFactory.getLogger(TwitchManager.class);
    private static final String TWITCH_ICON_URL = "https://cdn-icons-png.flaticon.com/512/5968/5968819.png";
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final OkHttpClient httpClient;

    public TwitchManager(ConfigManager configManager, DatabaseManager databaseManager) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.httpClient = new OkHttpClient();
    }

    public void refreshAccessToken() {
        String clientId = configManager.getTwitchClientId();
        String clientSecret = configManager.getTwitchClientSecret();
        String refreshToken = configManager.getTwitchRefreshToken();

        if (clientId == null || clientSecret == null || refreshToken == null) {
            logger.error("Missing Twitch credentials for token refresh");
            return;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();

        Request request = new Request.Builder()
                .url("https://id.twitch.tv/oauth2/token")
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                String newAccessToken = json.get("access_token").getAsString();
                String newRefreshToken = json.get("refresh_token").getAsString();

                configManager.setTwitchAccessToken(newAccessToken);
                configManager.setTwitchRefreshToken(newRefreshToken);
                logger.info("Successfully refreshed Twitch access token");
            } else {
                logger.error("Failed to refresh Twitch token: {}", response.code());
            }
        } catch (IOException e) {
            logger.error("Error refreshing Twitch token", e);
        }
    }

    public JsonObject validateUsername(String username) throws IOException {
        String clientId = configManager.getTwitchClientId();
        String accessToken = configManager.getTwitchAccessToken();

        Request request = new Request.Builder()
                .url("https://api.twitch.tv/helix/users?login=" + username)
                .addHeader("Client-ID", clientId)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                JsonArray data = json.getAsJsonArray("data");
                if (data.size() > 0) {
                    return data.get(0).getAsJsonObject();
                }
            }
        }
        return null;
    }

    public void checkLiveStreams(JDA jda) {
        String clientId = configManager.getTwitchClientId();
        String accessToken = configManager.getTwitchAccessToken();

        if (clientId == null || accessToken == null) {
            logger.error("Missing Twitch credentials");
            return;
        }

        List<DatabaseManager.GuildInfo> guilds = databaseManager.getAllGuilds();

        for (DatabaseManager.GuildInfo guildInfo : guilds) {
            try {
                Guild guild = jda.getGuildById(guildInfo.guildId);
                if (guild == null) continue;

                String channelId = databaseManager.getAnnouncementChannel(guildInfo.primaryId, "twitch");
                if (channelId == null) continue;

                TextChannel channel = guild.getTextChannelById(channelId);
                if (channel == null) continue;

                List<TwitchUser> registeredUsers = databaseManager.getRegisteredTwitchUsers(guildInfo.primaryId);
                if (registeredUsers.isEmpty()) continue;

                List<String> announcedStreams = databaseManager.getAnnouncedStreams(guildInfo.guildId);

                // Build query for all users (max 100 per request)
                StringBuilder queryParams = new StringBuilder();
                for (int i = 0; i < registeredUsers.size() && i < 100; i++) {
                    if (i > 0) queryParams.append("&");
                    queryParams.append("user_login=").append(registeredUsers.get(i).getUsername());
                }

                Request request = new Request.Builder()
                        .url("https://api.twitch.tv/helix/streams?" + queryParams)
                        .addHeader("Client-ID", clientId)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        JsonArray streams = json.getAsJsonArray("data");

                        for (int i = 0; i < streams.size(); i++) {
                            JsonObject stream = streams.get(i).getAsJsonObject();
                            String streamId = stream.get("id").getAsString();

                            if (announcedStreams.contains(streamId)) continue;

                            String userLogin = stream.get("user_login").getAsString();
                            String userName = stream.get("user_name").getAsString();
                            String title = stream.get("title").getAsString();
                            String gameName = stream.get("game_name").getAsString();
                            String thumbnailUrl = stream.get("thumbnail_url").getAsString()
                                    .replace("{width}", "1920")
                                    .replace("{height}", "1080");

                            // Get user profile image
                            String profileImageUrl = getUserProfileImage(userLogin);

                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle("🔴 " + userName + " is live!")
                                    .setDescription("**" + title + "**\nNow playing: " + gameName + 
                                                   "\n[Watch here](https://twitch.tv/" + userLogin + ")")
                                    .setColor(Color.decode("#9146FF"))
                                    .setImage(thumbnailUrl)
                                    .setFooter("Twitch Stream Announcement");

                            if (profileImageUrl != null) {
                                embed.setThumbnail(profileImageUrl);
                            }

                            channel.sendMessageEmbeds(embed.build()).queue();
                            announcedStreams.add(streamId);
                            logger.info("Announced stream for {} in guild {}", userLogin, guildInfo.guildId);
                        }

                        databaseManager.saveAnnouncedStreams(guildInfo.guildId, announcedStreams);
                    }
                }
            } catch (Exception e) {
                logger.error("Error checking streams for guild {}", guildInfo.guildId, e);
            }
        }
    }

    private String getUserProfileImage(String username) {
        String clientId = configManager.getTwitchClientId();
        String accessToken = configManager.getTwitchAccessToken();

        Request request = new Request.Builder()
                .url("https://api.twitch.tv/helix/users?login=" + username)
                .addHeader("Client-ID", clientId)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                JsonArray data = json.getAsJsonArray("data");
                if (data.size() > 0) {
                    return data.get(0).getAsJsonObject().get("profile_image_url").getAsString();
                }
            }
        } catch (IOException e) {
            logger.error("Error fetching user profile image", e);
        }
        return null;
    }
}