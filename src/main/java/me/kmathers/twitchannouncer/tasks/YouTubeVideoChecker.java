package me.kmathers.twitchannouncer.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.config.Config;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class YouTubeVideoChecker implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(YouTubeVideoChecker.class);
    private final TwitchAnnouncer bot;
    private final OkHttpClient httpClient;

    public YouTubeVideoChecker(TwitchAnnouncer bot) {
        this.bot = bot;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public void run() {
        Config.YouTubeConfig youtube = bot.getConfig().getYoutube();
        if (youtube == null) {
            logger.error("Missing YouTube API key.");
            return;
        }

        try {
            List<DatabaseManager.GuildInfo> guilds = bot.getDatabase().getAllGuilds();

            for (DatabaseManager.GuildInfo guildInfo : guilds) {
                String guildId = guildInfo.getGuildId();
                String primaryId = guildInfo.getPrimaryId();

                String channelId = bot.getDatabase().getAnnouncementChannel(primaryId, "youtube");
                if (channelId == null) {
                    continue;
                }

                TextChannel discordChannel = bot.getJda().getTextChannelById(channelId);
                if (discordChannel == null) {
                    Guild guild = bot.getJda().getGuildById(guildId);
                    if (guild != null) {
                        discordChannel = guild.getTextChannelById(channelId);
                    }
                }

                if (discordChannel == null) {
                    logger.warn("Discord channel {} not found for guild {}", channelId, guildId);
                    continue;
                }

                List<DatabaseManager.RegisteredYouTube> channels = bot.getDatabase().getRegisteredYouTubes(primaryId);
                if (channels.isEmpty()) {
                    continue;
                }

                for (DatabaseManager.RegisteredYouTube ytChannel : channels) {
                    String handle = ytChannel.getHandle();
                    if (!handle.startsWith("@")) {
                        logger.warn("Invalid handle format: {}", handle);
                        continue;
                    }

                    checkYouTubeChannel(handle, guildId, discordChannel, youtube);
                }
            }

            bot.getDatabase().saveAnnouncedStreams(bot.getAnnouncedStreams());

        } catch (Exception e) {
            logger.error("Error checking YouTube videos", e);
        }
    }

    private void checkYouTubeChannel(String handle, String guildId, TextChannel discordChannel, Config.YouTubeConfig youtube) {
        String handleName = handle.substring(1);
        String resolveUrl = "https://www.googleapis.com/youtube/v3/channels?part=id,snippet&forHandle=" + handleName + "&key=" + youtube.getApiKey();

        try {
            Request resolveRequest = new Request.Builder().url(resolveUrl).build();

            try (Response resolveResponse = httpClient.newCall(resolveRequest).execute()) {
                if (!resolveResponse.isSuccessful()) {
                    logger.error("Failed to resolve handle {}: {} - {}", handle, resolveResponse.code(), resolveResponse.body().string());
                    return;
                }

                JsonObject resolveJson = JsonParser.parseString(resolveResponse.body().string()).getAsJsonObject();
                JsonArray items = resolveJson.getAsJsonArray("items");

                if (items.size() == 0) {
                    logger.error("No channel found for handle {}", handle);
                    return;
                }

                String channelId = items.get(0).getAsJsonObject().get("id").getAsString();
                String channelTitle = items.get(0).getAsJsonObject().getAsJsonObject("snippet").get("title").getAsString();

                String uploadsPlaylistId = "UU" + channelId.substring(2);
                String playlistUrl = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&playlistId=" + uploadsPlaylistId + "&maxResults=5&order=date&key=" + youtube.getApiKey();

                Request playlistRequest = new Request.Builder().url(playlistUrl).build();

                try (Response playlistResponse = httpClient.newCall(playlistRequest).execute()) {
                    if (!playlistResponse.isSuccessful()) {
                        logger.error("YouTube API error for uploads playlist {}: {} - {}", uploadsPlaylistId, playlistResponse.code(), playlistResponse.body().string());
                        return;
                    }

                    JsonObject playlistJson = JsonParser.parseString(playlistResponse.body().string()).getAsJsonObject();
                    JsonArray videoItems = playlistJson.getAsJsonArray("items");

                    if (videoItems.size() == 0) {
                        return;
                    }

                    for (int i = 0; i < videoItems.size(); i++) {
                        JsonObject item = videoItems.get(i).getAsJsonObject();
                        JsonObject videoSnippet = item.getAsJsonObject("snippet");
                        String videoId = videoSnippet.getAsJsonObject("resourceId").get("videoId").getAsString();
                        String title = videoSnippet.get("title").getAsString();
                        String publishedAt = videoSnippet.get("publishedAt").getAsString();
                        String description = videoSnippet.has("description") ? videoSnippet.get("description").getAsString() : "";
                        if (description.length() > 200) {
                            description = description.substring(0, 200) + "...";
                        }

                        List<String> announced = bot.getAnnouncedStreams().computeIfAbsent(guildId, k -> new ArrayList<>());
                        if (announced.contains(videoId)) {
                            continue;
                        }

                        Instant publishedInstant = Instant.parse(publishedAt);
                        Instant now = Instant.now();
                        Duration timeDiff = Duration.between(publishedInstant, now);

                        if (timeDiff.toHours() > 24) {
                            continue;
                        }

                        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                        String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg";

                        EmbedBuilder embed = new EmbedBuilder();
                        embed.setTitle("📺 " + channelTitle + " uploaded a new video!");
                        embed.setDescription("**" + title + "**\n\n" + description + "\n\n[Watch here](" + videoUrl + ")");
                        embed.setColor(Color.RED);
                        embed.setImage(thumbnailUrl);
                        embed.setFooter("YouTube Video Announcement");
                        embed.setTimestamp(publishedInstant);

                        try {
                            discordChannel.sendMessageEmbeds(embed.build()).queue();
                            announced.add(videoId);
                        } catch (Exception e) {
                            logger.error("Failed to send YouTube announcement", e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error checking YouTube channel {}", handle, e);
        }
    }
}
