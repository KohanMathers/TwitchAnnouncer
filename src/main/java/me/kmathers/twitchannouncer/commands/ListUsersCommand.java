package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.util.List;

public class ListUsersCommand {
    private static final String TWITCH_ICON_URL = "https://cdn-icons-png.flaticon.com/512/5968/5968819.png";
    private final TwitchAnnouncer bot;

    public ListUsersCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        try {
            String primaryId = bot.getDatabase().getPrimaryId(event.getGuild().getId());
            if (primaryId == null) {
                event.reply("Guild is not registered in the database. Please run initial setup first.").setEphemeral(true).queue();
                return;
            }

            List<DatabaseManager.RegisteredUser> users = bot.getDatabase().getRegisteredUsers(primaryId);

            if (users.isEmpty()) {
                event.reply("No users registered for this server.").setEphemeral(true).queue();
                return;
            }

            EmbedBuilder embed = buildUserListEmbed(users);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("An error occurred: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    public void execute(MessageReceivedEvent event) {
        try {
            String primaryId = bot.getDatabase().getPrimaryId(event.getGuild().getId());
            if (primaryId == null) {
                event.getMessage().reply("Guild is not registered in the database. Please run initial setup first.").queue();
                return;
            }

            List<DatabaseManager.RegisteredUser> users = bot.getDatabase().getRegisteredUsers(primaryId);

            if (users.isEmpty()) {
                event.getMessage().reply("No users registered for this server.").queue();
                return;
            }

            EmbedBuilder embed = buildUserListEmbed(users);
            event.getMessage().replyEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getMessage().reply("An error occurred: " + e.getMessage()).queue();
        }
    }

    private EmbedBuilder buildUserListEmbed(List<DatabaseManager.RegisteredUser> users) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Registered Twitch Users (" + users.size() + ")");
        embed.setDescription("Twitch users registered for this server:");
        embed.setColor(new Color(145, 70, 255));

        for (DatabaseManager.RegisteredUser user : users) {
            String displayName = user.getDisplayName() != null ? user.getDisplayName() : "Unknown";
            String username = user.getUsername() != null ? user.getUsername() : "unknown";
            String created = user.getCreatedAt() != null ? user.getCreatedAt() : "Unknown";
            String profileUrl = user.getProfileImageUrl() != null ? user.getProfileImageUrl() : TWITCH_ICON_URL;

            embed.addField(
                displayName,
                "**Username**: `" + username + "`\n**Created**: " + created + "\n[Profile Image](" + profileUrl + ")",
                false
            );
        }

        embed.setThumbnail(TWITCH_ICON_URL);
        embed.setFooter("Use /register to add more users.");

        return embed;
    }
}
