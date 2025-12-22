package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class RegisterYouTubeCommand {
    private final TwitchAnnouncer bot;

    public RegisterYouTubeCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        String handle = event.getOption("handle").getAsString().toLowerCase();
        if (!handle.startsWith("@")) {
            handle = "@" + handle;
        }
        final String finalHandle = handle;

        executeRegister(finalHandle, event.getGuild().getId(),
            embed -> event.replyEmbeds(embed.build()).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue());
    }

    public void execute(MessageReceivedEvent event, String handle) {
        handle = handle.toLowerCase();
        if (!handle.startsWith("@")) {
            handle = "@" + handle;
        }
        final String finalHandle = handle;

        executeRegister(finalHandle, event.getGuild().getId(),
            embed -> event.getMessage().replyEmbeds(embed.build()).queue(),
            error -> event.getMessage().reply(error).queue());
    }

    private void executeRegister(String handle, String guildId, SuccessCallback success, ErrorCallback error) {
        try {
            String primaryId = bot.getDatabase().getPrimaryId(guildId);
            if (primaryId == null) {
                error.onError("Guild is not registered in the database. Please run initial setup first.");
                return;
            }

            List<DatabaseManager.RegisteredYouTube> channels = bot.getDatabase().getRegisteredYouTubes(primaryId);

            if (channels.stream().anyMatch(c -> c.getHandle().equalsIgnoreCase(handle))) {
                error.onError("The YouTube handle `" + handle + "` is already registered for this server.");
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                .withZone(ZoneId.of("UTC"));
            String registeredAt = formatter.format(Instant.now());

            channels.add(new DatabaseManager.RegisteredYouTube(handle, registeredAt));
            bot.getDatabase().saveRegisteredYouTubes(primaryId, channels);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("YouTube Channel Registered");
            embed.setDescription("`" + handle + "` has been added to this server's YouTube announcements.");
            embed.setColor(Color.RED);
            embed.setThumbnail("https://cdn-icons-png.flaticon.com/512/1384/1384060.png");
            embed.setFooter("YouTube Registration Complete");

            success.onSuccess(embed);
        } catch (Exception e) {
            error.onError("An error occurred while registering the YouTube channel: `" + e.getMessage() + "`");
        }
    }

    @FunctionalInterface
    interface SuccessCallback {
        void onSuccess(EmbedBuilder embed);
    }

    @FunctionalInterface
    interface ErrorCallback {
        void onError(String message);
    }
}
