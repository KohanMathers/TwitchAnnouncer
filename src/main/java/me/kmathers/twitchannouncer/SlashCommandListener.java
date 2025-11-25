package me.kmathers.twitchannouncer;

import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SlashCommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SlashCommandListener.class);
    private final DatabaseManager databaseManager;
    private final TwitchManager twitchManager;
    private final long startTime;

    public SlashCommandListener(DatabaseManager databaseManager, TwitchManager twitchManager, long startTime) {
        this.databaseManager = databaseManager;
        this.twitchManager = twitchManager;
        this.startTime = startTime;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        databaseManager.initializeGuild(guildId);

        switch (event.getName()) {
            case "ping" -> handlePing(event);
            case "help" -> handleHelp(event);
            case "info" -> handleInfo(event);
            case "debug" -> handleDebug(event);
            case "setprefix" -> handleSetPrefix(event);
            case "register" -> handleRegister(event);
            case "listusers" -> handleListUsers(event);
            case "unregister" -> handleUnregister(event);
            case "setchannel" -> handleSetChannel(event);
            case "registeryoutube" -> handleRegisterYouTube(event);
            case "listyoutube" -> handleListYouTube(event);
            case "unregisteryoutube" -> handleUnregisterYouTube(event);
        }
    }

    private void handlePing(SlashCommandInteractionEvent event) {
        event.reply("Pong!").setEphemeral(true).queue();
    }

    private void handleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Commands List")
                .setDescription("Available commands:")
                .setColor(Color.BLUE)
                .addField("/ping", "Ping the bot", false)
                .addField("/setprefix <new_prefix>", "Set a new prefix (requires Manage Server)", false)
                .addField("/help", "Displays this message", false)
                .addField("/info", "Get information about the bot", false)
                .addField("/debug", "Show detailed bot debug information", false)
                .addField("/register <username>", "Register a Twitch username", false)
                .addField("/unregister <username>", "Remove a Twitch username", false)
                .addField("/listusers", "List all registered Twitch users", false)
                .addField("/registeryoutube <handle>", "Register a YouTube channel", false)
                .addField("/unregisteryoutube <handle>", "Remove a YouTube channel", false)
                .addField("/listyoutube", "List all registered YouTube channels", false)
                .addField("/setchannel <platform>", "Set announcement channel (requires Manage Channels)", false);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleInfo(SlashCommandInteractionEvent event) {
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

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleDebug(SlashCommandInteractionEvent event) {
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
                .addField("Guild ID", event.getGuild().getId(), true)
                .setFooter("Requested by " + event.getUser().getAsTag(), event.getUser().getAvatarUrl());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleSetPrefix(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need the Manage Server permission to change the prefix.").setEphemeral(true).queue();
            return;
        }

        String newPrefix = event.getOption("prefix").getAsString();
        databaseManager.setPrefix(event.getGuild().getId(), newPrefix);
        event.reply("Prefix set to `" + newPrefix + "` for this server.").setEphemeral(true).queue();
    }

    private void handleRegister(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String username = event.getOption("username").getAsString().toLowerCase();
        Integer primaryId = databaseManager.getPrimaryId(event.getGuild().getId());

        if (primaryId == null) {
            event.getHook().sendMessage("Guild is not registered in the database.").setEphemeral(true).queue();
            return;
        }

        try {
            JsonObject userData = twitchManager.validateUsername(username);
            if (userData == null) {
                event.getHook().sendMessage("The username `" + username + "` does not exist on Twitch.").setEphemeral(true).queue();
                return;
            }

            String displayName = userData.get("display_name").getAsString();
            String profileImageUrl = userData.get("profile_image_url").getAsString();
            String createdAt = userData.get("created_at").getAsString();
            String description = userData.has("description") ? userData.get("description").getAsString() : "No description.";

            String createdFmt = java.time.OffsetDateTime.parse(createdAt)
                    .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));

            List<TwitchUser> users = databaseManager.getRegisteredTwitchUsers(primaryId);
            if (users.stream().anyMatch(u -> u.getUsername().equals(username))) {
                event.getHook().sendMessage("The Twitch username `" + username + "` is already registered for this server.").setEphemeral(true).queue();
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

            event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
        } catch (IOException e) {
            logger.error("Error registering Twitch user", e);
            event.getHook().sendMessage("An error occurred while registering the Twitch user.").setEphemeral(true).queue();
        }
    }

    private void handleListUsers(SlashCommandInteractionEvent event) {
        Integer primaryId = databaseManager.getPrimaryId(event.getGuild().getId());
        if (primaryId == null) {
            event.reply("Guild is not registered in the database.").setEphemeral(true).queue();
            return;
        }

        List<TwitchUser> users = databaseManager.getRegisteredTwitchUsers(primaryId);
        if (users.isEmpty()) {
            event.reply("No users registered for this server.").setEphemeral(true).queue();
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

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleUnregister(SlashCommandInteractionEvent event) {
        String username = event.getOption("username").getAsString().toLowerCase();
        Integer primaryId = databaseManager.getPrimaryId(event.getGuild().getId());

        if (primaryId == null) {
            event.reply("Guild is not registered in the database.").setEphemeral(true).queue();
            return;
        }

        List<TwitchUser> users = databaseManager.getRegisteredTwitchUsers(primaryId);
        boolean removed = users.removeIf(u -> u.getUsername().equals(username));

        if (!removed) {
            event.reply("No registered Twitch account found for `" + username + "`.").setEphemeral(true).queue();
            return;
        }

        databaseManager.saveRegisteredTwitchUsers(primaryId, users);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Twitch User Unregistered")
                .setDescription("The user `" + username + "` has been removed from this server's registered Twitch accounts.")
                .setColor(Color.decode("#9146FF"))
                .setFooter("Twitch Unregistration Complete");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleSetChannel(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You need the Manage Channels permission to set the announcement channel.").setEphemeral(true).queue();
            return;
        }

        String platform = event.getOption("platform").getAsString().toLowerCase();
        if (!platform.equals("twitch") && !platform.equals("youtube")) {
            event.reply("Invalid platform. Must be either `twitch` or `youtube`.").setEphemeral(true).queue();
            return;
        }

        Integer primaryId = databaseManager.getPrimaryId(event.getGuild().getId());
        if (primaryId == null) {
            event.reply("Guild is not registered in the database.").setEphemeral(true).queue();
            return;
        }

        if (!(event.getChannel() instanceof TextChannel)) {
            event.reply("This command can only be used in a text channel.").setEphemeral(true).queue();
            return;
        }

        databaseManager.setAnnouncementChannel(primaryId, platform, event.getChannel().getId());
        event.reply("Announcement channel for " + platform + " set to " + event.getChannel().getAsMention() + ".").setEphemeral(true).queue();
    }

    private void handleRegisterYouTube(SlashCommandInteractionEvent event) {
        String handleString = event.getOption("handle").getAsString().toLowerCase();
        final String handle = handleString.startsWith("@") ? handleString : "@" + handleString;

        Integer primaryId = databaseManager.getPrimaryId(event.getGuild().getId());
        if (primaryId == null) {
            event.reply("Guild is not registered in the database.").setEphemeral(true).queue();
            return;
        }

        List<YouTubeChannel> channels = databaseManager.getRegisteredYouTubeChannels(primaryId);
        if (channels.stream().anyMatch(c -> c.getHandle().equals(handle))) {
            event.reply("The YouTube handle `" + handle + "` is already registered for this server.").setEphemeral(true).queue();
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

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleListYouTube(SlashCommandInteractionEvent event) {
        Integer primaryId = databaseManager.getPrimaryId(event.getGuild().getId());
        if (primaryId == null) {
            event.reply("Guild is not registered in the database.").setEphemeral(true).queue();
            return;
        }

        List<YouTubeChannel> channels = databaseManager.getRegisteredYouTubeChannels(primaryId);
        if (channels.isEmpty()) {
            event.reply("No YouTube channels registered for this server.").setEphemeral(true).queue();
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

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleUnregisterYouTube(SlashCommandInteractionEvent event) {
        String handle = event.getOption("handle").getAsString().toLowerCase();
        if (!handle.startsWith("@")) {
            handle = "@" + handle;
        }

        Integer primaryId = databaseManager.getPrimaryId(event.getGuild().getId());
        if (primaryId == null) {
            event.reply("Guild is not registered in the database.").setEphemeral(true).queue();
            return;
        }

        List<YouTubeChannel> channels = databaseManager.getRegisteredYouTubeChannels(primaryId);
        String finalHandle = handle;
        boolean removed = channels.removeIf(c -> c.getHandle().equals(finalHandle));

        if (!removed) {
            event.reply("No registered YouTube handle found for `" + handle + "`.").setEphemeral(true).queue();
            return;
        }

        databaseManager.saveRegisteredYouTubeChannels(primaryId, channels);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("YouTube Channel Unregistered")
                .setDescription("`" + handle + "` has been removed from this server's announcements.")
                .setColor(Color.RED)
                .setThumbnail("https://cdn-icons-png.flaticon.com/512/1384/1384060.png")
                .setFooter("YouTube Unregistration Complete");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}

// SlashCommandRegistrar.java