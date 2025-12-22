package me.kmathers.twitchannouncer.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.config.Config;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TwitchStreamChecker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TwitchStreamChecker.class);
    private final TwitchAnnouncer bot;
    private final OkHttpClient httpClient;

    public TwitchStreamChecker(TwitchAnnouncer bot) {
        this.bot = bot;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public void run() {
        Config.TwitchConfig twitch = bot.getConfig().getTwitch();
        if (twitch == null) {
            logger.error("Missing Twitch credentials.");
            return;
        }

        try {
            List<DatabaseManager.GuildInfo> guilds = bot.getDatabase().getAllGuilds();

            for (DatabaseManager.GuildInfo guildInfo : guilds) {
                String guildId = guildInfo.getGuildId();
                String primaryId = guildInfo.getPrimaryId();

                String channelId = bot.getDatabase().getAnnouncementChannel(primaryId, "twitch");
                if (channelId == null) {
                    continue;
                }

                TextChannel channel = bot.getJda().getTextChannelById(channelId);
                if (channel == null) {
                    continue;
                }

                List<DatabaseManager.RegisteredUser> users = bot.getDatabase().getRegisteredUsers(primaryId);
                if (users.isEmpty()) {
                    continue;
                }

                List<String> usernames = new ArrayList<>();
                for (DatabaseManager.RegisteredUser user : users) {
                    usernames.add(user.getUsername());
                }

                for (int i = 0; i < usernames.size(); i += 100) {
                    List<String> batch = usernames.subList(i, Math.min(i + 100, usernames.size()));
                    checkStreamsForBatch(batch, guildId, channel, twitch);
                }
            }

            bot.getDatabase().saveAnnouncedStreams(bot.getAnnouncedStreams());

        } catch (Exception e) {
            logger.error("Error checking Twitch streams", e);
        }
    }

    private void checkStreamsForBatch(List<String> usernames, String guildId, TextChannel channel, Config.TwitchConfig twitch) {
        StringBuilder queryParams = new StringBuilder();
        for (String username : usernames) {
            if (queryParams.length() > 0) {
                queryParams.append("&");
            }
            queryParams.append("user_login=").append(username);
        }

        String url = "https://api.twitch.tv/helix/streams?" + queryParams;

        Request request = new Request.Builder()
            .url(url)
            .header("Client-ID", twitch.getClientId())
            .header("Authorization", "Bearer " + twitch.getAccessToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Twitch API error: {} - {}", response.code(), response.body().string());
                return;
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray liveStreams = json.getAsJsonArray("data");

            for (int i = 0; i < liveStreams.size(); i++) {
                JsonObject stream = liveStreams.get(i).getAsJsonObject();
                String streamId = stream.get("id").getAsString();
                String userLogin = stream.get("user_login").getAsString();
                String title = stream.has("title") ? stream.get("title").getAsString() : "No Title";
                String gameName = stream.has("game_name") ? stream.get("game_name").getAsString() : "Unknown Game";

                List<String> announced = bot.getAnnouncedStreams().computeIfAbsent(guildId, k -> new ArrayList<>());
                if (announced.contains(streamId)) {
                    continue;
                }

                String displayName = userLogin;
                String profileImageUrl = null;

                String userInfoUrl = "https://api.twitch.tv/helix/users?login=" + userLogin;
                Request userRequest = new Request.Builder()
                    .url(userInfoUrl)
                    .header("Client-ID", twitch.getClientId())
                    .header("Authorization", "Bearer " + twitch.getAccessToken())
                    .build();

                try (Response userResponse = httpClient.newCall(userRequest).execute()) {
                    if (userResponse.isSuccessful()) {
                        JsonObject userJson = JsonParser.parseString(userResponse.body().string()).getAsJsonObject();
                        JsonArray userData = userJson.getAsJsonArray("data");
                        if (userData.size() > 0) {
                            JsonObject userDetails = userData.get(0).getAsJsonObject();
                            displayName = userDetails.get("display_name").getAsString();
                            profileImageUrl = userDetails.get("profile_image_url").getAsString();
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error fetching user info for {}", userLogin, e);
                }

                String previewUrl = "https://static-cdn.jtvnw.net/previews-ttv/live_user_" + userLogin.toLowerCase() + "-1920x1080.jpg";

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("🔴 " + displayName + " is live!");
                embed.setDescription("**" + title + "**\nNow playing: " + gameName + "\n[Watch here](https://twitch.tv/" + userLogin + ")");
                embed.setColor(new Color(145, 70, 255));
                embed.setImage(previewUrl);

                if (profileImageUrl != null) {
                    embed.setThumbnail(profileImageUrl);
                }

                embed.setFooter("Twitch Stream Announcement");

                channel.sendMessageEmbeds(embed.build()).queue();
                announced.add(streamId);
            }

        } catch (Exception e) {
            logger.error("Error checking streams for batch", e);
        }
    }
}
