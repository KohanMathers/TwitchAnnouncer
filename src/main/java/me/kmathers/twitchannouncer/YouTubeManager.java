package me.kmathers.twitchannouncer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class YouTubeManager {
    private static final Logger logger = LoggerFactory.getLogger(YouTubeManager.class);
    private static final String YOUTUBE_ICON_URL = "https://cdn-icons-png.flaticon.com/512/1384/1384060.png";
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final OkHttpClient httpClient;

    public YouTubeManager(ConfigManager configManager, DatabaseManager databaseManager) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.httpClient = new OkHttpClient();
    }

    public void checkNewVideos(JDA jda) {
        String apiKey = configManager.getYouTubeApiKey();
        if (apiKey == null) {
            logger.error("Missing YouTube API key");
            return;
        }

        List<DatabaseManager.GuildInfo> guilds = databaseManager.getAllGuilds();

        for (DatabaseManager.GuildInfo guildInfo : guilds) {
            try {
                Guild guild = jda.getGuildById(guildInfo.guildId);
                if (guild == null) continue;

                String channelId = databaseManager.getAnnouncementChannel(guildInfo.primaryId, "youtube");
                if (channelId == null) continue;

                TextChannel channel = guild.getTextChannelById(channelId);
                if (channel == null) continue;

                List<YouTubeChannel> registeredChannels = databaseManager.getRegisteredYouTubeChannels(guildInfo.primaryId);
                if (registeredChannels.isEmpty()) continue;

                List<String> announcedVideos = databaseManager.getAnnouncedStreams(guildInfo.guildId);

                for (YouTubeChannel ytChannel : registeredChannels) {
                    try {
                        String handle = ytChannel.getHandle();
                        if (!handle.startsWith("@")) continue;

                        String handleName = handle.substring(1);
                        
                        // Resolve handle to channel ID
                        String resolveUrl = String.format(
                            "https://www.googleapis.com/youtube/v3/channels?part=id,snippet&forHandle=%s&key=%s",
                            handleName, apiKey
                        );

                        Request resolveRequest = new Request.Builder().url(resolveUrl).build();
                        
                        try (Response resolveResponse = httpClient.newCall(resolveRequest).execute()) {
                            if (!resolveResponse.isSuccessful() || resolveResponse.body() == null) {
                                logger.error("Failed to resolve YouTube handle: {}", handle);
                                continue;
                            }

                            JsonObject resolveJson = JsonParser.parseString(resolveResponse.body().string()).getAsJsonObject();
                            JsonArray items = resolveJson.getAsJsonArray("items");
                            
                            if (items.size() == 0) {
                                logger.error("No channel found for handle: {}", handle);
                                continue;
                            }

                            String ytChannelId = items.get(0).getAsJsonObject().get("id").getAsString();
                            String channelTitle = items.get(0).getAsJsonObject()
                                .getAsJsonObject("snippet").get("title").getAsString();

                            // Get uploads playlist
                            String uploadsPlaylistId = "UU" + ytChannelId.substring(2);

                            String playlistUrl = String.format(
                                "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&playlistId=%s&maxResults=5&order=date&key=%s",
                                uploadsPlaylistId, apiKey
                            );

                            Request playlistRequest = new Request.Builder().url(playlistUrl).build();

                            try (Response playlistResponse = httpClient.newCall(playlistRequest).execute()) {
                                if (!playlistResponse.isSuccessful() || playlistResponse.body() == null) {
                                    logger.error("Failed to get YouTube playlist: {}", uploadsPlaylistId);
                                    continue;
                                }

                                JsonObject playlistJson = JsonParser.parseString(playlistResponse.body().string()).getAsJsonObject();
                                JsonArray videos = playlistJson.getAsJsonArray("items");

                                for (int i = 0; i < videos.size(); i++) {
                                    JsonObject video = videos.get(i).getAsJsonObject();
                                    JsonObject snippet = video.getAsJsonObject("snippet");
                                    
                                    String videoId = snippet.getAsJsonObject("resourceId").get("videoId").getAsString();
                                    String title = snippet.get("title").getAsString();
                                    String publishedAt = snippet.get("publishedAt").getAsString();
                                    String description = snippet.get("description").getAsString();
                                    
                                    if (description.length() > 200) {
                                        description = description.substring(0, 200) + "...";
                                    }

                                    if (announcedVideos.contains(videoId)) continue;

                                    // Check if video is within 24 hours
                                    OffsetDateTime publishedDate = OffsetDateTime.parse(publishedAt);
                                    OffsetDateTime now = OffsetDateTime.now();
                                    long hoursDiff = ChronoUnit.HOURS.between(publishedDate, now);

                                    if (hoursDiff > 24) continue;

                                    String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                                    String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg";

                                    EmbedBuilder embed = new EmbedBuilder()
                                            .setTitle("📺 " + channelTitle + " uploaded a new video!")
                                            .setDescription("**" + title + "**\n\n" + description + "\n\n[Watch here](" + videoUrl + ")")
                                            .setColor(Color.RED)
                                            .setImage(thumbnailUrl)
                                            .setFooter("YouTube Video Announcement")
                                            .setTimestamp(publishedDate);

                                    channel.sendMessageEmbeds(embed.build()).queue();
                                    announcedVideos.add(videoId);
                                    logger.info("Announced YouTube video {} in guild {}", videoId, guildInfo.guildId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error checking YouTube channel: {}", ytChannel.getHandle(), e);
                    }
                }

                databaseManager.saveAnnouncedStreams(guildInfo.guildId, announcedVideos);
            } catch (Exception e) {
                logger.error("Error checking YouTube videos for guild {}", guildInfo.guildId, e);
            }
        }
    }
}