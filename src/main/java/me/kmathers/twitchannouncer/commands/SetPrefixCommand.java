package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SetPrefixCommand {
    private final TwitchAnnouncer bot;

    public SetPrefixCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Messages permission to change the prefix.").setEphemeral(true).queue();
            return;
        }

        String newPrefix = event.getOption("prefix").getAsString();

        try {
            bot.getDatabase().setPrefix(event.getGuild().getId(), newPrefix);
            event.reply("Prefix set to `" + newPrefix + "` for this server.").queue();
        } catch (Exception e) {
            event.reply("Failed to set prefix: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    public void execute(MessageReceivedEvent event, String newPrefix) {
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getMessage().reply("You need the Manage Messages permission to change the prefix.").queue();
            return;
        }

        try {
            bot.getDatabase().setPrefix(event.getGuild().getId(), newPrefix);
            event.getMessage().reply("Prefix set to `" + newPrefix + "` for this server.").queue();
        } catch (Exception e) {
            event.getMessage().reply("Failed to set prefix: " + e.getMessage()).queue();
        }
    }
}
