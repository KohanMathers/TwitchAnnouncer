package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.util.List;

public class ListYouTubeCommand {
    private final TwitchAnnouncer bot;

    public ListYouTubeCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        try {
            String primaryId = bot.getDatabase().getPrimaryId(event.getGuild().getId());
            if (primaryId == null) {
                event.reply("Guild is not registered in the database. Please run initial setup first.").setEphemeral(true).queue();
                return;
            }

            List<DatabaseManager.RegisteredYouTube> channels = bot.getDatabase().getRegisteredYouTubes(primaryId);

            if (channels.isEmpty()) {
                event.reply("No YouTube channels registered for this server.").setEphemeral(true).queue();
                return;
            }

            EmbedBuilder embed = buildChannelListEmbed(channels);
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

            List<DatabaseManager.RegisteredYouTube> channels = bot.getDatabase().getRegisteredYouTubes(primaryId);

            if (channels.isEmpty()) {
                event.getMessage().reply("No YouTube channels registered for this server.").queue();
                return;
            }

            EmbedBuilder embed = buildChannelListEmbed(channels);
            event.getMessage().replyEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getMessage().reply("An error occurred: " + e.getMessage()).queue();
        }
    }

    private EmbedBuilder buildChannelListEmbed(List<DatabaseManager.RegisteredYouTube> channels) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Registered YouTube Channels (" + channels.size() + ")");
        embed.setDescription("List of YouTube handles registered for this server:");
        embed.setColor(Color.RED);

        for (DatabaseManager.RegisteredYouTube channel : channels) {
            embed.addField(
                channel.getHandle(),
                "Registered: " + (channel.getRegisteredAt() != null ? channel.getRegisteredAt() : "Unknown"),
                false
            );
        }

        embed.setThumbnail("https://cdn-icons-png.flaticon.com/512/1384/1384060.png");
        embed.setFooter("Use /registeryoutube to add more channels.");

        return embed;
    }
}
