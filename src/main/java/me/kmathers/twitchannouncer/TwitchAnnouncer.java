package me.kmathers.twitchannouncer;

import me.kmathers.twitchannouncer.config.Config;
import me.kmathers.twitchannouncer.database.DatabaseManager;
import me.kmathers.twitchannouncer.commands.CommandManager;
import me.kmathers.twitchannouncer.tasks.TwitchStreamChecker;
import me.kmathers.twitchannouncer.tasks.YouTubeVideoChecker;
import me.kmathers.twitchannouncer.tasks.TwitchTokenRefresher;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TwitchAnnouncer {
    private static final Logger logger = LoggerFactory.getLogger(TwitchAnnouncer.class);
    private static final long START_TIME = System.currentTimeMillis();

    private final JDA jda;
    private final Config config;
    private final DatabaseManager database;
    private final Map<String, List<String>> announcedStreams;
    private final ScheduledExecutorService scheduler;

    public TwitchAnnouncer() throws Exception {
        logger.info("Starting TwitchAnnouncer bot...");

        this.config = Config.load();
        if (config == null || config.getDiscordToken() == null) {
            throw new IllegalStateException("Failed to load configuration. Check token.json file.");
        }

        this.database = new DatabaseManager();
        this.announcedStreams = database.loadAnnouncedStreams();
        this.scheduler = Executors.newScheduledThreadPool(3);

        this.jda = JDABuilder.createDefault(config.getDiscordToken())
            .enableIntents(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_PRESENCES
            )
            .setChunkingFilter(ChunkingFilter.ALL)
            .setMemberCachePolicy(MemberCachePolicy.ALL)
            .setActivity(Activity.watching("for streams"))
            .build();

        jda.awaitReady();

        CommandManager commandManager = new CommandManager(this);
        jda.addEventListener(commandManager);

        jda.updateCommands().addCommands(commandManager.getSlashCommands()).queue();

        startScheduledTasks();

        logger.info("Logged in as {}!", jda.getSelfUser().getAsTag());
    }

    private void startScheduledTasks() {
        if (config.getTwitch() != null) {
            TwitchStreamChecker streamChecker = new TwitchStreamChecker(this);
            scheduler.scheduleAtFixedRate(streamChecker, 0, 1, TimeUnit.MINUTES);
            logger.info("Started Twitch stream checker (every 1 minute)");

            TwitchTokenRefresher tokenRefresher = new TwitchTokenRefresher(this);
            scheduler.scheduleAtFixedRate(tokenRefresher, 0, 24, TimeUnit.HOURS);
            logger.info("Started Twitch token refresher (every 24 hours)");
        }

        if (config.getYoutube() != null) {
            YouTubeVideoChecker videoChecker = new YouTubeVideoChecker(this);
            scheduler.scheduleAtFixedRate(videoChecker, 0, 15, TimeUnit.MINUTES);
            logger.info("Started YouTube video checker (every 15 minutes)");
        }
    }

    public JDA getJda() {
        return jda;
    }

    public Config getConfig() {
        return config;
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    public Map<String, List<String>> getAnnouncedStreams() {
        return announcedStreams;
    }

    public static long getStartTime() {
        return START_TIME;
    }

    public void shutdown() {
        logger.info("Shutting down TwitchAnnouncer...");
        scheduler.shutdown();
        jda.shutdown();
    }

    public static void main(String[] args) {
        try {
            new TwitchAnnouncer();
        } catch (Exception e) {
            logger.error("Failed to start TwitchAnnouncer", e);
            System.exit(1);
        }
    }
}
