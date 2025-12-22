package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CommandManager extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);
    private final TwitchAnnouncer bot;
    private final PingCommand pingCommand;
    private final SetPrefixCommand setPrefixCommand;
    private final HelpCommand helpCommand;
    private final InfoCommand infoCommand;
    private final DebugCommand debugCommand;
    private final RegisterCommand registerCommand;
    private final ListUsersCommand listUsersCommand;
    private final UnregisterCommand unregisterCommand;
    private final SetChannelCommand setChannelCommand;
    private final RegisterYouTubeCommand registerYouTubeCommand;
    private final ListYouTubeCommand listYouTubeCommand;
    private final UnregisterYouTubeCommand unregisterYouTubeCommand;

    public CommandManager(TwitchAnnouncer bot) {
        this.bot = bot;
        this.pingCommand = new PingCommand();
        this.setPrefixCommand = new SetPrefixCommand(bot);
        this.helpCommand = new HelpCommand();
        this.infoCommand = new InfoCommand();
        this.debugCommand = new DebugCommand(bot);
        this.registerCommand = new RegisterCommand(bot);
        this.listUsersCommand = new ListUsersCommand(bot);
        this.unregisterCommand = new UnregisterCommand(bot);
        this.setChannelCommand = new SetChannelCommand(bot);
        this.registerYouTubeCommand = new RegisterYouTubeCommand(bot);
        this.listYouTubeCommand = new ListYouTubeCommand(bot);
        this.unregisterYouTubeCommand = new UnregisterYouTubeCommand(bot);
    }

    public List<CommandData> getSlashCommands() {
        List<CommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("ping", "Ping the bot"));
        commands.add(Commands.slash("setprefix", "Set a new prefix for the bot")
            .addOption(OptionType.STRING, "prefix", "The new prefix", true));
        commands.add(Commands.slash("help", "Displays all available commands"));
        commands.add(Commands.slash("info", "Get information about the bot"));
        commands.add(Commands.slash("debug", "Show detailed bot debug information"));
        commands.add(Commands.slash("register", "Register a Twitch username for stream announcements")
            .addOption(OptionType.STRING, "username", "The Twitch username", true));
        commands.add(Commands.slash("listusers", "List all registered Twitch users for this server"));
        commands.add(Commands.slash("unregister", "Remove a Twitch username from announcements")
            .addOption(OptionType.STRING, "username", "The Twitch username", true));
        commands.add(Commands.slash("setchannel", "Set the current channel for announcements")
            .addOption(OptionType.STRING, "platform", "Platform (twitch/youtube)", true));
        commands.add(Commands.slash("registeryoutube", "Register a YouTube channel handle for announcements")
            .addOption(OptionType.STRING, "handle", "The YouTube channel handle (e.g., @username)", true));
        commands.add(Commands.slash("listyoutube", "List all registered YouTube channels for this server"));
        commands.add(Commands.slash("unregisteryoutube", "Remove a YouTube channel from announcements")
            .addOption(OptionType.STRING, "handle", "The YouTube channel handle", true));

        return commands;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        try {
            switch (event.getName()) {
                case "ping" -> pingCommand.execute(event);
                case "setprefix" -> setPrefixCommand.execute(event);
                case "help" -> helpCommand.execute(event);
                case "info" -> infoCommand.execute(event);
                case "debug" -> debugCommand.execute(event);
                case "register" -> registerCommand.execute(event);
                case "listusers" -> listUsersCommand.execute(event);
                case "unregister" -> unregisterCommand.execute(event);
                case "setchannel" -> setChannelCommand.execute(event);
                case "registeryoutube" -> registerYouTubeCommand.execute(event);
                case "listyoutube" -> listYouTubeCommand.execute(event);
                case "unregisteryoutube" -> unregisterYouTubeCommand.execute(event);
            }
        } catch (Exception e) {
            logger.error("Error executing slash command: {}", event.getName(), e);
            event.reply("An error occurred while executing the command. Please try again later.")
                .setEphemeral(true).queue();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        DatabaseManager db = bot.getDatabase();
        String prefix = db.loadPrefix(event.getGuild().getId());

        String content = event.getMessage().getContentRaw();
        if (!content.toLowerCase().startsWith(prefix.toLowerCase() + ",")) {
            return;
        }

        String commandLine = content.substring(prefix.length() + 1).trim();
        String[] parts = commandLine.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : null;

        try {
            switch (command) {
                case "ping" -> pingCommand.execute(event);
                case "setprefix" -> {
                    if (args != null) {
                        setPrefixCommand.execute(event, args);
                    } else {
                        event.getMessage().reply("Usage: " + prefix + ", setprefix <new_prefix>").queue();
                    }
                }
                case "help" -> helpCommand.execute(event);
                case "info" -> infoCommand.execute(event);
                case "debug" -> debugCommand.execute(event);
                case "register" -> {
                    if (args != null) {
                        registerCommand.execute(event, args);
                    } else {
                        event.getMessage().reply("Usage: " + prefix + ", register <twitch_username>").queue();
                    }
                }
                case "listusers" -> listUsersCommand.execute(event);
                case "unregister" -> {
                    if (args != null) {
                        unregisterCommand.execute(event, args);
                    } else {
                        event.getMessage().reply("Usage: " + prefix + ", unregister <twitch_username>").queue();
                    }
                }
                case "setchannel" -> {
                    if (args != null) {
                        setChannelCommand.execute(event, args);
                    } else {
                        event.getMessage().reply("Usage: " + prefix + ", setchannel <twitch/youtube>").queue();
                    }
                }
                case "registeryoutube" -> {
                    if (args != null) {
                        registerYouTubeCommand.execute(event, args);
                    } else {
                        event.getMessage().reply("Usage: " + prefix + ", registeryoutube <channel_handle>").queue();
                    }
                }
                case "listyoutube" -> listYouTubeCommand.execute(event);
                case "unregisteryoutube" -> {
                    if (args != null) {
                        unregisterYouTubeCommand.execute(event, args);
                    } else {
                        event.getMessage().reply("Usage: " + prefix + ", unregisteryoutube <channel_handle>").queue();
                    }
                }
                default -> event.getMessage().reply("Unknown command: " + command).queue();
            }
        } catch (Exception e) {
            logger.error("Error executing message command: {}", command, e);
            event.getMessage().reply("An error occurred while executing the command.").queue();
        }
    }
}
