package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class DebugCommand {
    private final TwitchAnnouncer bot;
    private static final String CONFIG_DB = "TwitchAnnouncerConfig.db";
    private static final Random random = new Random();

    private static final String[] FUN_FACTS = {
        "'Ping' is named after sonar, not the game.",
        "Discord.py was originally created by Rapptz.",
        "The first computer bug was an actual moth.",
        "Python's name comes from Monty Python, not the snake.",
        "The 'Uptime' metric is older than Unix.",
        "\"Hello, world!\" was first used in Brian Kernighan's 1972 tutorial for the B programming language.",
        "'null' in programming is often called 'The Billion Dollar Mistake'.",
        "The Unicode snowman character is called \"☃\" and it exists just to be cute.",
        "COBOL still runs 95% of ATM transactions.",
        "Git was created by Linus Torvalds in just 10 days.",
        "The word 'robot' comes from a 1920 play and means 'forced labor'.",
        "Over 90% of the world's currency exists only in digital form.",
        "The first domain ever registered was 'symbolics.com' in 1985.",
        "The term 'debugging' predates computers — Thomas Edison used it in 1878.",
        "Nintendo was founded in 1889 — as a playing card company.",
        "There are more transistors in a modern smartphone than stars in the Milky Way.",
        "Emoji is a Japanese word that predates the iPhone.",
        "SpaceX uses C++ and Python to write rocket software.",
        "The first 1GB hard drive cost $40,000 in 1980 and weighed 500 pounds.",
        "Ctrl+Alt+Del was never meant for users — it was a shortcut for developers."
    };

    public DebugCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = buildDebugEmbed(event.getGuild(), event.getMember());
        event.replyEmbeds(embed.build()).queue();
    }

    public void execute(MessageReceivedEvent event) {
        EmbedBuilder embed = buildDebugEmbed(event.getGuild(), event.getMember());
        event.getMessage().replyEmbeds(embed.build()).queue();
    }

    private EmbedBuilder buildDebugEmbed(Guild guild, Member member) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Debug Report");
        embed.setColor(Color.ORANGE);

        long uptimeMillis = System.currentTimeMillis() - TwitchAnnouncer.getStartTime();
        Duration uptime = Duration.ofMillis(uptimeMillis);
        String uptimeStr = String.format("%dd %dh %dm %ds",
            uptime.toDays(),
            uptime.toHoursPart(),
            uptime.toMinutesPart(),
            uptime.toSecondsPart()
        );

        Runtime runtime = Runtime.getRuntime();
        long memoryUsedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

        long latency = bot.getJda().getGatewayPing();

        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String environment = osName + " " + osVersion + " | Java " + javaVersion + " | JDA " + net.dv8tion.jda.api.JDAInfo.VERSION;

        String prefix;
        String lastWrite;
        String dbStatus;

        try {
            prefix = bot.getDatabase().loadPrefix(guild.getId());
            File dbFile = new File(CONFIG_DB);
            if (dbFile.exists()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                lastWrite = sdf.format(new Date(dbFile.lastModified())) + " UTC";
                dbStatus = "Connected";
            } else {
                lastWrite = "N/A";
                dbStatus = "File not found";
            }
        } catch (Exception e) {
            prefix = "?";
            lastWrite = "N/A";
            dbStatus = "Error (" + e.getMessage() + ")";
        }

        List<String> perms = new ArrayList<>();
        if (guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR)) {
            perms.add("ADMIN");
        }
        if (guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            perms.add("MANAGE_MESSAGES");
        }
        if (guild.getSelfMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            perms.add("MANAGE_CHANNELS");
        }
        String permsString = perms.isEmpty() ? "Normal" : String.join(", ", perms);

        int cacheMembers = guild.getMemberCount();
        int cacheChannels = guild.getChannels().size();
        String cacheSize = cacheMembers + " members | " + cacheChannels + " channels";

        embed.addField("Bot Uptime", uptimeStr, true);
        embed.addField("Active Commands Loaded", "12", true);
        embed.addField("Guild ID", guild.getId(), true);
        embed.addField("Database Status", dbStatus, true);
        embed.addField("Last Database Write", lastWrite, true);
        embed.addField("Memory Usage", memoryUsedMB + " MB", true);
        embed.addField("Latency", latency + "ms", true);
        embed.addField("Running On", environment, true);
        embed.addField("Prefix", prefix, true);
        embed.addField("Guild Locale", guild.getLocale().getLanguageName(), true);
        embed.addField("Command Cooldowns", "None active", true);
        embed.addField("Cache Size", cacheSize, true);
        embed.addField("Shards", "1", true);
        embed.addField("Permissions", permsString, true);
        embed.addField("Environment", osName + " " + osVersion, true);
        embed.addField("CPU Load", "N/A", true);
        embed.addField("Fun Fact", FUN_FACTS[random.nextInt(FUN_FACTS.length)], false);

        embed.setFooter("Requested by " + member.getEffectiveName(), member.getEffectiveAvatarUrl());

        return embed;
    }
}
