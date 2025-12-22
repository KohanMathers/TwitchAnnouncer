package me.kmathers.twitchannouncer.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;

public class InfoCommand {
    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = buildInfoEmbed();
        event.replyEmbeds(embed.build()).queue();
    }

    public void execute(MessageReceivedEvent event) {
        EmbedBuilder embed = buildInfoEmbed();
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private EmbedBuilder buildInfoEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("TwitchAnnouncer");
        embed.setDescription("Information about the bot");
        embed.setColor(Color.BLUE);

        embed.addField("Description", "TwitchAnnouncer is a multi-account Discord bot allowing you to register any amount of twitch accounts to have streams announced in your guild.", false);
        embed.addField("Version", "2.0", false);
        embed.addField("Author", "[Kohan Mathers](https://kmathers.co.uk)", false);
        embed.addField("Support Server", "[Join here](https://discord.gg/FZuVXszuuM)", false);
        embed.addField("Source Code", "[GitHub](https://github.com/KohanMathers/TwitchAnnouncer)", false);
        embed.addField("Commands", "Use `/help` to see available commands.", false);

        return embed;
    }
}
