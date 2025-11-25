package me.kmathers.twitchannouncer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class SlashCommandRegistrar {
    private static final Logger logger = LoggerFactory.getLogger(SlashCommandRegistrar.class);

    public static void registerCommands(JDA jda) {
        jda.updateCommands().addCommands(
                Commands.slash("ping", "Ping the bot"),
                Commands.slash("help", "Display the help message"),
                Commands.slash("info", "Get information about the bot"),
                Commands.slash("debug", "Show detailed bot debug information"),
                Commands.slash("setprefix", "Set a new prefix for the bot")
                        .addOption(OptionType.STRING, "prefix", "The new prefix", true),
                Commands.slash("register", "Register a Twitch username for stream announcements")
                        .addOption(OptionType.STRING, "username", "Twitch username", true),
                Commands.slash("listusers", "List all registered Twitch users"),
                Commands.slash("unregister", "Remove a Twitch username from announcements")
                        .addOption(OptionType.STRING, "username", "Twitch username", true),
                Commands.slash("setchannel", "Set the announcement channel")
                        .addOption(OptionType.STRING, "platform", "Platform (twitch/youtube)", true),
                Commands.slash("registeryoutube", "Register a YouTube channel for announcements")
                        .addOption(OptionType.STRING, "handle", "YouTube handle (e.g., @channel)", true),
                Commands.slash("listyoutube", "List all registered YouTube channels"),
                Commands.slash("unregisteryoutube", "Remove a YouTube channel from announcements")
                        .addOption(OptionType.STRING, "handle", "YouTube handle", true)
        ).queue(
                success -> logger.info("Successfully registered slash commands"),
                error -> logger.error("Failed to register slash commands", error)
        );
    }
}