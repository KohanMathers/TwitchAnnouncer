package me.kmathers.twitchannouncer.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PingCommand {
    public void execute(SlashCommandInteractionEvent event) {
        event.reply("Pong!").queue();
    }

    public void execute(MessageReceivedEvent event) {
        event.getMessage().reply("Pong!").queue();
    }
}
