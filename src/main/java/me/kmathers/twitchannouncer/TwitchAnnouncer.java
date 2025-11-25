package me.kmathers.twitchannouncer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TwitchAnnouncer {
    private static final Logger logger = LoggerFactory.getLogger(TwitchAnnouncer.class);
    private static JDA jda;
    private static final long START_TIME = System.currentTimeMillis();
    private static ConfigManager configManager;
    private static DatabaseManager databaseManager;
    private static TwitchManager twitchManager;
    private static YouTubeManager youtubeManager;
    private static ScheduledExecutorService scheduler;

    public static void main(String[] args) {
        try {
            logger.info("Starting TwitchAnnouncer Bot...");

            configManager = new ConfigManager();
            String token = configManager.getDiscordToken();
            
            if (token == null || token.isEmpty()) {
                logger.error("Discord token not found in configuration!");
                System.exit(1);
            }

            databaseManager = new DatabaseManager();
            databaseManager.initialize();

            twitchManager = new TwitchManager(configManager, databaseManager);
            youtubeManager = new YouTubeManager(configManager, databaseManager);

            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_PRESENCES
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setActivity(Activity.watching("for /help"))
                    .disableCache(CacheFlag.FORUM_TAGS)
                    .addEventListeners(new CommandListener(databaseManager, twitchManager, START_TIME))
                    .addEventListeners(new SlashCommandListener(databaseManager, twitchManager, START_TIME))
                    .build();

            jda.awaitReady();
            logger.info("Bot is ready! Logged in as: {}", jda.getSelfUser().getAsTag());

            SlashCommandRegistrar.registerCommands(jda);

            startScheduledTasks();

        } catch (Exception e) {
            logger.error("Failed to start bot", e);
            System.exit(1);
        }
    }

    private static void startScheduledTasks() {
        scheduler = Executors.newScheduledThreadPool(3);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                twitchManager.checkLiveStreams(jda);
            } catch (Exception e) {
                logger.error("Error checking Twitch streams", e);
            }
        }, 0, 1, TimeUnit.MINUTES);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                youtubeManager.checkNewVideos(jda);
            } catch (Exception e) {
                logger.error("Error checking YouTube videos", e);
            }
        }, 0, 1, TimeUnit.MINUTES);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                twitchManager.refreshAccessToken();
            } catch (Exception e) {
                logger.error("Error refreshing Twitch token", e);
            }
        }, 0, 24, TimeUnit.HOURS);

        logger.info("Scheduled tasks started successfully");
    }

    public static JDA getJda() {
        return jda;
    }

    public static long getStartTime() {
        return START_TIME;
    }

    public static void shutdown() {
        logger.info("Shutting down bot...");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        if (jda != null) {
            jda.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}