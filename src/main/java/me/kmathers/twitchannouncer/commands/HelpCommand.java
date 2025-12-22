package me.kmathers.twitchannouncer.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;

public class HelpCommand {
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = buildHelpEmbed();
        event.replyEmbeds(embed.build()).queue();
    }

    public void execute(MessageReceivedEvent event) {
        EmbedBuilder embed = buildHelpEmbed();
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private EmbedBuilder buildHelpEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Commands List");
        embed.setDescription("Available commands:");
        embed.setColor(Color.BLUE);

        embed.addField("/ping", "Ping the bot", false);
        embed.addField("/setprefix <new_prefix>", "Set a new prefix for the bot (requires Manage Messages permission)", false);
        embed.addField("/help", "Displays this message", false);
        embed.addField("/info", "Get information about the bot", false);
        embed.addField("/debug", "Show detailed bot debug information", false);

        embed.addField("/register <username>", "Register a Twitch username for stream announcements", false);
        embed.addField("/unregister <username>", "Remove a Twitch username from announcements", false);
        embed.addField("/listusers", "List all registered Twitch users for this server", false);

        embed.addField("/registeryoutube <handle>", "Register a YouTube channel handle for announcements", false);
        embed.addField("/unregisteryoutube <handle>", "Remove a YouTube channel from announcements", false);
        embed.addField("/listyoutube", "List all registered YouTube channels for this server", false);

        embed.addField("/setchannel <platform>", "Set the current channel for announcements (twitch/youtube) - requires Manage Channels permission", false);

        return embed;
    }
}
