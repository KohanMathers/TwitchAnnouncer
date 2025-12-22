package me.kmathers.twitchannouncer.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final String TOKEN_FILE = "token.json";
    private static final Gson gson = new Gson();

    private String discordToken;
    private TwitchConfig twitch;
    private YouTubeConfig youtube;

    public static Config load() {
        try (FileReader reader = new FileReader(TOKEN_FILE)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            Config config = new Config();
            config.discordToken = json.has("TOKEN") ? json.get("TOKEN").getAsString() : null;

            if (json.has("twitch")) {
                JsonObject twitchObj = json.getAsJsonObject("twitch");
                config.twitch = new TwitchConfig(
                    twitchObj.get("CLIENT_ID").getAsString(),
                    twitchObj.get("CLIENT_SECRET").getAsString(),
                    twitchObj.get("ACCESS_TOKEN").getAsString(),
                    twitchObj.get("REFRESH_TOKEN").getAsString()
                );
            }

            if (json.has("youtube")) {
                JsonObject youtubeObj = json.getAsJsonObject("youtube");
                config.youtube = new YouTubeConfig(
                    youtubeObj.get("API_KEY").getAsString()
                );
            }

            return config;
        } catch (IOException e) {
            logger.error("Error: Could not read token from {}", TOKEN_FILE, e);
            return null;
        }
    }

    public void saveTwitchTokens(String accessToken, String refreshToken) {
        try (FileReader reader = new FileReader(TOKEN_FILE)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            if (json.has("twitch")) {
                JsonObject twitchObj = json.getAsJsonObject("twitch");
                twitchObj.addProperty("ACCESS_TOKEN", accessToken);
                twitchObj.addProperty("REFRESH_TOKEN", refreshToken);

                try (FileWriter writer = new FileWriter(TOKEN_FILE)) {
                    gson.toJson(json, writer);
                    this.twitch = new TwitchConfig(
                        twitch.getClientId(),
                        twitch.getClientSecret(),
                        accessToken,
                        refreshToken
                    );
                    logger.info("Successfully refreshed Twitch token.");
                }
            }
        } catch (IOException e) {
            logger.error("Failed to save Twitch tokens", e);
        }
    }

    public String getDiscordToken() {
        return discordToken;
    }

    public TwitchConfig getTwitch() {
        return twitch;
    }

    public YouTubeConfig getYoutube() {
        return youtube;
    }

    public static class TwitchConfig {
        private final String clientId;
        private final String clientSecret;
        private final String accessToken;
        private final String refreshToken;

        public TwitchConfig(String clientId, String clientSecret, String accessToken, String refreshToken) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }

    public static class YouTubeConfig {
        private final String apiKey;

        public YouTubeConfig(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKey() {
            return apiKey;
        }
    }
}
