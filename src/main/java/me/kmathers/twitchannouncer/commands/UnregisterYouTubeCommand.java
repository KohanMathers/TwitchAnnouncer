package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class UnregisterYouTubeCommand {
    private final TwitchAnnouncer bot;

    public UnregisterYouTubeCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        String handle = event.getOption("handle").getAsString();
        if (!handle.startsWith("@")) {
            handle = "@" + handle;
        }
        final String finalHandle = handle.toLowerCase();

        executeUnregister(finalHandle, event.getGuild().getId(),
            embed -> event.replyEmbeds(embed.build()).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue());
    }

    public void execute(MessageReceivedEvent event, String handle) {
        if (!handle.startsWith("@")) {
            handle = "@" + handle;
        }
        final String finalHandle = handle.toLowerCase();

        executeUnregister(finalHandle, event.getGuild().getId(),
            embed -> event.getMessage().replyEmbeds(embed.build()).queue(),
            error -> event.getMessage().reply(error).queue());
    }

    private void executeUnregister(String handle, String guildId, SuccessCallback success, ErrorCallback error) {
        try {
            String primaryId = bot.getDatabase().getPrimaryId(guildId);
            if (primaryId == null) {
                error.onError("Guild is not registered in the database. Please run initial setup first.");
                return;
            }

            List<DatabaseManager.RegisteredYouTube> channels = bot.getDatabase().getRegisteredYouTubes(primaryId);
            List<DatabaseManager.RegisteredYouTube> newList = channels.stream()
                .filter(c -> !c.getHandle().equalsIgnoreCase(handle))
                .collect(Collectors.toList());

            if (newList.size() == channels.size()) {
                error.onError("No registered YouTube handle found for `" + handle + "`.");
                return;
            }

            bot.getDatabase().saveRegisteredYouTubes(primaryId, newList);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("YouTube Channel Unregistered");
            embed.setDescription("`" + handle + "` has been removed from this server's announcements.");
            embed.setColor(Color.RED);
            embed.setThumbnail("https://cdn-icons-png.flaticon.com/512/1384/1384060.png");
            embed.setFooter("YouTube Unregistration Complete");

            success.onSuccess(embed);
        } catch (Exception e) {
            error.onError("An error occurred while unregistering the YouTube channel: `" + e.getMessage() + "`");
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
