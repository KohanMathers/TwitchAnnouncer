package me.kmathers.twitchannouncer.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.config.Config;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class RegisterCommand {
    private static final Logger logger = LoggerFactory.getLogger(RegisterCommand.class);
    private static final String TWITCH_ICON_URL = "https://cdn-icons-png.flaticon.com/512/5968/5968819.png";
    private final TwitchAnnouncer bot;
    private final OkHttpClient httpClient;

    public RegisterCommand(TwitchAnnouncer bot) {
        this.bot = bot;
        this.httpClient = new OkHttpClient();
    }

    public void execute(SlashCommandInteractionEvent event) {
        String username = event.getOption("username").getAsString().toLowerCase();
        event.deferReply().queue();
        executeRegistration(username, event.getGuild().getId(), (embed, imageBytes) -> {
            if (imageBytes != null) {
                InputStream is = new ByteArrayInputStream(imageBytes);
                event.getHook().sendMessageEmbeds(embed.build())
                    .addFiles(FileUpload.fromData(is, "profile.jpg"))
                    .queue();
            } else {
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }
        }, error -> {
            event.getHook().sendMessage(error).setEphemeral(true).queue();
        });
    }

    public void execute(MessageReceivedEvent event, String username) {
        username = username.toLowerCase();
        event.getChannel().sendTyping().queue();
        executeRegistration(username, event.getGuild().getId(), (embed, imageBytes) -> {
            if (imageBytes != null) {
                InputStream is = new ByteArrayInputStream(imageBytes);
                event.getMessage().replyEmbeds(embed.build())
                    .addFiles(FileUpload.fromData(is, "profile.jpg"))
                    .queue();
            } else {
                event.getMessage().replyEmbeds(embed.build()).queue();
            }
        }, error -> {
            event.getMessage().reply(error).queue();
        });
    }

    private void executeRegistration(String username, String guildId, RegistrationCallback callback, ErrorCallback errorCallback) {
        Config.TwitchConfig twitch = bot.getConfig().getTwitch();
        if (twitch == null) {
            errorCallback.onError("Twitch credentials not configured.");
            return;
        }

        String url = "https://api.twitch.tv/helix/users?login=" + username;
        Request request = new Request.Builder()
            .url(url)
            .header("Client-ID", twitch.getClientId())
            .header("Authorization", "Bearer " + twitch.getAccessToken())
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                errorCallback.onError("Failed to validate username. Error from Twitch: `" + response.message() + "`");
                return;
            }

            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray data = json.getAsJsonArray("data");

            if (data.size() == 0) {
                EmbedBuilder errorEmbed = new EmbedBuilder();
                errorEmbed.setTitle("Twitch Username Not Found");
                errorEmbed.setDescription("The username `" + username + "` does not exist on Twitch.");
                errorEmbed.setColor(Color.RED);
                errorEmbed.setThumbnail(TWITCH_ICON_URL);
                errorEmbed.setFooter("Twitch Registration Failed");
                callback.onSuccess(errorEmbed, null);
                return;
            }

            JsonObject userData = data.get(0).getAsJsonObject();
            String displayName = userData.get("display_name").getAsString();
            String profileImageUrl = userData.get("profile_image_url").getAsString();
            String description = userData.has("description") ? userData.get("description").getAsString() : "No description.";
            String createdAt = userData.get("created_at").getAsString();

            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMMM dd, yyyy");
            Date date = inputFormat.parse(createdAt);
            String createdFmt = outputFormat.format(date);

            String primaryId = bot.getDatabase().getPrimaryId(guildId);
            if (primaryId == null) {
                errorCallback.onError("Guild is not registered in the database. Please run initial setup first.");
                return;
            }

            List<DatabaseManager.RegisteredUser> users = bot.getDatabase().getRegisteredUsers(primaryId);

            if (users.stream().anyMatch(u -> u.getUsername().equals(username))) {
                errorCallback.onError("The Twitch username `" + username + "` is already registered for this server.");
                return;
            }

            users.add(new DatabaseManager.RegisteredUser(username, displayName, profileImageUrl, createdFmt));
            bot.getDatabase().saveRegisteredUsers(primaryId, users);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(displayName + " has been registered!");
            embed.setDescription(description);
            embed.setColor(new Color(145, 70, 255));
            embed.addField("Username", "`" + username + "`", true);
            embed.addField("Account Created", createdFmt, true);
            embed.setThumbnail("attachment://profile.jpg");
            embed.setFooter("Twitch Registration Complete");

            byte[] imageBytes = null;
            try (Response imgResponse = httpClient.newCall(new Request.Builder().url(profileImageUrl).build()).execute()) {
                if (imgResponse.isSuccessful()) {
                    imageBytes = imgResponse.body().bytes();
                }
            } catch (Exception e) {
                logger.warn("Failed to download profile image", e);
            }

            callback.onSuccess(embed, imageBytes);

        } catch (Exception e) {
            logger.error("Error registering user", e);
            errorCallback.onError("An unexpected error occurred while registering Twitch username: `" + e.getMessage() + "`");
        }
    }

    @FunctionalInterface
    interface RegistrationCallback {
        void onSuccess(EmbedBuilder embed, byte[] imageBytes);
    }

    @FunctionalInterface
    interface ErrorCallback {
        void onError(String message);
    }
}
