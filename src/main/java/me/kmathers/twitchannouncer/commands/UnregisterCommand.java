package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class UnregisterCommand {
    private static final String TWITCH_ICON_URL = "https://cdn-icons-png.flaticon.com/512/5968/5968819.png";
    private final TwitchAnnouncer bot;

    public UnregisterCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        String username = event.getOption("username").getAsString().toLowerCase();
        executeUnregister(username, event.getGuild().getId(),
            message -> event.replyEmbeds(message.build()).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue());
    }

    public void execute(MessageReceivedEvent event, String username) {
        username = username.toLowerCase();
        executeUnregister(username, event.getGuild().getId(),
            message -> event.getMessage().replyEmbeds(message.build()).queue(),
            error -> event.getMessage().reply(error).queue());
    }

    private void executeUnregister(String username, String guildId, SuccessCallback success, ErrorCallback error) {
        try {
            String primaryId = bot.getDatabase().getPrimaryId(guildId);
            if (primaryId == null) {
                error.onError("Guild is not registered in the database. Please run initial setup first.");
                return;
            }

            List<DatabaseManager.RegisteredUser> users = bot.getDatabase().getRegisteredUsers(primaryId);
            List<DatabaseManager.RegisteredUser> newList = users.stream()
                .filter(u -> !u.getUsername().equals(username))
                .collect(Collectors.toList());

            if (newList.size() == users.size()) {
                error.onError("No registered Twitch account found for `" + username + "`.");
                return;
            }

            bot.getDatabase().saveRegisteredUsers(primaryId, newList);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Twitch User Unregistered");
            embed.setDescription("The user `" + username + "` has been removed from this server's registered Twitch accounts.");
            embed.setColor(new Color(145, 70, 255));
            embed.setThumbnail(TWITCH_ICON_URL);
            embed.setFooter("Twitch Unregistration Complete");

            success.onSuccess(embed);
        } catch (Exception e) {
            error.onError("An error occurred while unregistering: " + e.getMessage());
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
