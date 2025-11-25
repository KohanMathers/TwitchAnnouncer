package me.kmathers.twitchannouncer;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
    private final DatabaseManager databaseManager;
    private final TwitchManager twitchManager;
    private final long startTime;

    public CommandListener(DatabaseManager databaseManager, TwitchManager twitchManager, long startTime) {
        this.databaseManager = databaseManager;
        this.twitchManager = twitchManager;
        this.startTime = startTime;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        Message message = event.getMessage();
        String content = message.getContentRaw();
        String guildId = event.getGuild().getId();
        String prefix = databaseManager.getPrefix(guildId);

        if (!content.toLowerCase().startsWith(prefix.toLowerCase() + ",")) return;

        String commandLine = content.substring(prefix.length() + 1).trim();
        String[] parts = commandLine.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        try {
            switch (command) {
                case "ping" -> handlePing(message);
                case "setprefix" -> handleSetPrefix(message, args);
                case "help" -> handleHelp(message);
                case "info" -> handleInfo(message);
                case "debug" -> handleDebug(message, event);
                case "register" -> handleRegister(message, args);
                case "listusers" -> handleListUsers(message);
                case "unregister" -> handleUnregister(message, args);
                case "setchannel" -> handleSetChannel(message, args);
                case "registeryoutube" -> handleRegisterYouTube(message, args);
                case "listyoutube" -> handleListYouTube(message);
                case "unregisteryoutube" -> handleUnregisterYouTube(message, args);
                default -> message.reply("Unknown command: " + command).queue();
            }
        } catch (Exception e) {
            logger.error("Error executing command: " + command, e);
            message.reply("An error occurred while executing the command.").queue();
        }
    }

    private void handlePing(Message message) {
        message.reply("Pong!").queue();
    }

    private void handleSetPrefix(Message message, String args) {
        if (!message.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            message.reply("You need the Manage Messages permission to change the prefix.").queue();
            return;
        }

        if (args.isEmpty()) {
            message.reply("Usage: prefix, setprefix <new_prefix>").queue();
            return;
        }

        String newPrefix = args.trim();
        databaseManager.setPrefix(message.getGuild().getId(), newPrefix);
        message.reply("Prefix set to `" + newPrefix + "` for this server.").queue();
    }

    private void handleHelp(Message message) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Commands List")
                .setDescription("Available commands:")
                .setColor(Color.BLUE)
                .addField("/ping", "Ping the bot", false)
                .addField("/setprefix <new_prefix>", "Set a new prefix for the bot (requires Manage Messages permission)", false)
                .addField("/help", "Displays this message", false)
                .addField("/info", "Get information about the bot", false)
                .addField("/debug", "Show detailed bot debug information", false)
                .addField("/register <username>", "Register a Twitch username for stream announcements", false)
                .addField("/unregister <username>", "Remove a Twitch username from announcements", false)
                .addField("/listusers", "List all registered Twitch users for this server", false)
                .addField("/registeryoutube <handle>", "Register a YouTube channel handle for announcements", false)
                .addField("/unregisteryoutube <handle>", "Remove a YouTube channel from announcements", false)
                .addField("/listyoutube", "List all registered YouTube channels for this server", false)
                .addField("/setchannel <platform>", "Set the current channel for announcements (twitch/youtube) - requires Manage Channels permission", false);

        message.replyEmbeds(embed.build()).queue();
    }

    private void handleInfo(Message message) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("TwitchAnnouncer")
                .setDescription("Information about the bot")
                .setColor(Color.BLUE)
                .addField("Description", "TwitchAnnouncer is a multi-account Discord bot allowing you to register any amount of twitch accounts to have streams announced in your guild.", false)
                .addField("Version", "2.0 (Java Edition)", false)
                .addField("Author", "[Kohan Mathers](https://kmathers.co.uk)", false)
                .addField("Support Server", "[Join here](https://discord.gg/FZuVXszuuM)", false)
                .addField("Source Code", "[GitHub](https://github.com/KohanMathers/TwitchAnnouncer)", false)
                .addField("Commands", "Use `/help` to see available commands.", false);

        message.replyEmbeds(embed.build()).queue();
    }

    private void handleDebug(Message message, MessageReceivedEvent event) {
        long uptime = System.currentTimeMillis() - startTime;
        long uptimeSeconds = uptime / 1000;
        long days = uptimeSeconds / 86400;
        long hours = (uptimeSeconds % 86400) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;
        String uptimeStr = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);

        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long ping = event.getJDA().getGatewayPing();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Debug Report")
                .setColor(Color.ORANGE)
                .addField("Bot Uptime", uptimeStr, true)
                .addField("Memory Usage", memoryUsed + " MB", true)
                .addField("Latency", ping + "ms", true)
                .addField("JDA Version", "5.x", true)
                .addField("Java Version", System.getProperty("java.version"), true)
                .addField("Guild ID", message.getGuild().getId(), true)
                .setFooter("Requested by " + message.getAuthor().getAsTag(), message.getAuthor().getAvatarUrl());

        message.replyEmbeds(embed.build()).queue();
    }

    private void handleRegister(Message message, String args) {
        if (args.isEmpty()) {
            message.reply("Usage: prefix, register <twitch_username>").queue();
            return;
        }

        String username = args.trim().toLowerCase();
        Integer primaryId = databaseManager.getPrimaryId(message.getGuild().getId());

        if (primaryId == null) {
            message.reply("Guild is not registered in the database. Please run initial setup first.").queue();
            return;
        }

        try {
            JsonObject userData = twitchManager.validateUsername(username);
            if (userData == null) {
                message.reply("The username `" + username + "` does not exist on Twitch.").queue();
                return;
            }

            String displayName = userData.get("display_name").getAsString();
            String profileImageUrl = userData.get("profile_image_url").getAsString();
            String createdAt = userData.get("created_at").getAsString();
            String description = userData.has("description") ? userData.get("description").getAsString() : "No description.";

            String createdFmt = OffsetDateTime.parse(createdAt)
                    .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));

            List<TwitchUser> users = databaseManager.getRegisteredTwitchUsers(primaryId);
            if (users.stream().anyMatch(u -> u.getUsername().equals(username))) {
                message.reply("The Twitch username `" + username + "` is already registered for this server.").queue();
                return;
            }

            users.add(new TwitchUser(username, displayName, profileImageUrl, createdFmt));
            databaseManager.saveRegisteredTwitchUsers(primaryId, users);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(displayName + " has been registered!")
                    .setDescription(description)
                    .setColor(Color.decode("#9146FF"))
                    .addField("Username", "`" + username + "`", true)
                    .addField("Account Created", createdFmt, true)
                    .setThumbnail(profileImageUrl)
                    .setFooter("Twitch Registration Complete");

            message.replyEmbeds(embed.build()).queue();
        } catch (IOException e) {
            logger.error("Error registering Twitch user", e);
            message.reply("An error occurred while registering the Twitch user.").queue();
        }
    }

    private void handleListUsers(Message message) {
        Integer primaryId = databaseManager.getPrimaryId(message.getGuild().getId());
        if (primaryId == null) {
            message.reply("Guild is not registered in the database.").queue();
            return;
        }

        List<TwitchUser> users = databaseManager.getRegisteredTwitchUsers(primaryId);
        if (users.isEmpty()) {
            message.reply("No users registered for this server.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Registered Twitch Users (" + users.size() + ")")
                .setDescription("Twitch users registered for this server:")
                .setColor(Color.decode("#9146FF"))
                .setThumbnail("https://cdn-icons-png.flaticon.com/512/5968/5968819.png")
                .setFooter("Use /register to add more users.");

        for (TwitchUser user : users) {
            embed.addField(user.getDisplayName(),
                    "**Username**: `" + user.getUsername() + "`\n**Created**: " + user.getCreatedAt(),
                    false);
        }

        message.replyEmbeds(embed.build()).queue();
    }

    private void handleUnregister(Message message, String args) {
        if (args.isEmpty()) {
            message.reply("Usage: prefix, unregister <twitch_username>").queue();
            return;
        }

        String username = args.trim().toLowerCase();
        Integer primaryId = databaseManager.getPrimaryId(message.getGuild().getId());

        if (primaryId == null) {
            message.reply("Guild is not registered in the database.").queue();
            return;
        }

        List<TwitchUser> users = databaseManager.getRegisteredTwitchUsers(primaryId);
        boolean removed = users.removeIf(u -> u.getUsername().equals(username));

        if (!removed) {
            message.reply("No registered Twitch account found for `" + username + "`.").queue();
            return;
        }

        databaseManager.saveRegisteredTwitchUsers(primaryId, users);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Twitch User Unregistered")
                .setDescription("The user `" + username + "` has been removed from this server's registered Twitch accounts.")
                .setColor(Color.decode("#9146FF"))
                .setFooter("Twitch Unregistration Complete");

        message.replyEmbeds(embed.build()).queue();
    }

    private void handleSetChannel(Message message, String args) {
        if (!message.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            message.reply("You need the Manage Channels permission to set the announcement channel.").queue();
            return;
        }

        if (args.isEmpty() || (!args.equals("twitch") && !args.equals("youtube"))) {
            message.reply("Usage: prefix, setchannel <twitch/youtube>").queue();
            return;
        }

        String platform = args.trim().toLowerCase();
        Integer primaryId = databaseManager.getPrimaryId(message.getGuild().getId());

        if (primaryId == null) {
            message.reply("Guild is not registered in the database.").queue();
            return;
        }

        if (!(message.getChannel() instanceof TextChannel)) {
            message.reply("This command can only be used in a text channel.").queue();
            return;
        }

        databaseManager.setAnnouncementChannel(primaryId, platform, message.getChannel().getId());
        message.reply("Announcement channel for " + platform + " set to " + message.getChannel().getAsMention() + ".").queue();
    }

    private void handleRegisterYouTube(Message message, String args) {
        if (args.isEmpty()) {
            message.reply("Usage: prefix, registeryoutube <channel_handle>").queue();
            return;
        }

        String handleString = args.trim().toLowerCase();
        final String handle = handleString.startsWith("@") ? handleString : "@" + handleString;
        
        Integer primaryId = databaseManager.getPrimaryId(message.getGuild().getId());
        if (primaryId == null) {
            message.reply("Guild is not registered in the database.").queue();
            return;
        }

        List<YouTubeChannel> channels = databaseManager.getRegisteredYouTubeChannels(primaryId);
        if (channels.stream().anyMatch(c -> c.getHandle().equals(handle))) {
            message.reply("The YouTube handle `" + handle + "` is already registered for this server.").queue();
            return;
        }

        String registeredAt = java.time.OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"));
        channels.add(new YouTubeChannel(handle, registeredAt));
        databaseManager.saveRegisteredYouTubeChannels(primaryId, channels);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("YouTube Channel Registered")
                .setDescription("`" + handle + "` has been added to this server's YouTube announcements.")
                .setColor(Color.RED)
                .setThumbnail("https://cdn-icons-png.flaticon.com/512/1384/1384060.png")
                .setFooter("YouTube Registration Complete");

        message.replyEmbeds(embed.build()).queue();
    }

    private void handleListYouTube(Message message) {
        Integer primaryId = databaseManager.getPrimaryId(message.getGuild().getId());
        if (primaryId == null) {
            message.reply("Guild is not registered in the database.").queue();
            return;
        }

        List<YouTubeChannel> channels = databaseManager.getRegisteredYouTubeChannels(primaryId);
        if (channels.isEmpty()) {
            message.reply("No YouTube channels registered for this server.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Registered YouTube Channels (" + channels.size() + ")")
                .setDescription("List of YouTube handles registered for this server:")
                .setColor(Color.RED)
                .setThumbnail("https://cdn-icons-png.flaticon.com/512/1384/1384060.png")
                .setFooter("Use /registeryoutube to add more channels.");

        for (YouTubeChannel channel : channels) {
            embed.addField(channel.getHandle(), "Registered: " + channel.getRegisteredAt(), false);
        }

        message.replyEmbeds(embed.build()).queue();
    }

    private void handleUnregisterYouTube(Message message, String args) {
        if (args.isEmpty()) {
            message.reply("Usage: prefix, unregisteryoutube <channel_handle>").queue();
            return;
        }

        String handle = args.trim().toLowerCase();
        if (!handle.startsWith("@")) {
            handle = "@" + handle;
        }

        Integer primaryId = databaseManager.getPrimaryId(message.getGuild().getId());
        if (primaryId == null) {
            message.reply("Guild is not registered in the database.").queue();
            return;
        }

        List<YouTubeChannel> channels = databaseManager.getRegisteredYouTubeChannels(primaryId);
        String finalHandle = handle;
        boolean removed = channels.removeIf(c -> c.getHandle().equals(finalHandle));

        if (!removed) {
            message.reply("No registered YouTube handle found for `" + handle + "`.").queue();
            return;
        }

        databaseManager.saveRegisteredYouTubeChannels(primaryId, channels);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("YouTube Channel Unregistered")
                .setDescription("`" + handle + "` has been removed from this server's announcements.")
                .setColor(Color.RED)
                .setThumbnail("https://cdn-icons-png.flaticon.com/512/1384/1384060.png")
                .setFooter("YouTube Unregistration Complete");

        message.replyEmbeds(embed.build()).queue();
    }
}