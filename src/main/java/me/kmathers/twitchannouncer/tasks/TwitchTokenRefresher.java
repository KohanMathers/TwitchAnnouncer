package me.kmathers.twitchannouncer.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.kmathers.twitchannouncer.TwitchAnnouncer;
import me.kmathers.twitchannouncer.config.Config;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwitchTokenRefresher implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TwitchTokenRefresher.class);
    private final TwitchAnnouncer bot;
    private final OkHttpClient httpClient;

    public TwitchTokenRefresher(TwitchAnnouncer bot) {
        this.bot = bot;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public void run() {
        Config.TwitchConfig twitch = bot.getConfig().getTwitch();
        if (twitch == null) {
            logger.error("Twitch credentials not loaded.");
            return;
        }

        logger.info("Attempting to refresh Twitch token...");

        RequestBody formBody = new FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", twitch.getRefreshToken())
            .add("client_id", twitch.getClientId())
            .add("client_secret", twitch.getClientSecret())
            .build();

        Request request = new Request.Builder()
            .url("https://id.twitch.tv/oauth2/token")
            .post(formBody)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                if (json.has("access_token") && json.has("refresh_token")) {
                    String newAccessToken = json.get("access_token").getAsString();
                    String newRefreshToken = json.get("refresh_token").getAsString();

                    bot.getConfig().saveTwitchTokens(newAccessToken, newRefreshToken);
                    logger.info("Successfully refreshed Twitch token.");
                } else {
                    logger.error("Missing tokens in Twitch response: {}", body);
                }
            } else {
                logger.error("Failed to refresh token. Status code: {}, Response: {}",
                    response.code(), response.body().string());
            }
        } catch (Exception e) {
            logger.error("Exception while refreshing Twitch token", e);
        }
    }
}
