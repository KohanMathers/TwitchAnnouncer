package me.kmathers.twitchannouncer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String TOKEN_FILE = "token.json";
    private JsonObject config;
    private final Gson gson;

    public ConfigManager() {
        this.gson = new Gson();
        loadConfig();
    }

    private void loadConfig() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(TOKEN_FILE)));
            config = gson.fromJson(content, JsonObject.class);
            logger.info("Configuration loaded successfully");
        } catch (IOException e) {
            logger.error("Failed to load configuration from {}", TOKEN_FILE, e);
            config = new JsonObject();
        }
    }

    public void saveConfig() {
        try (FileWriter writer = new FileWriter(TOKEN_FILE)) {
            gson.toJson(config, writer);
            logger.info("Configuration saved successfully");
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
        }
    }

    public String getDiscordToken() {
        return config.has("TOKEN") ? config.get("TOKEN").getAsString() : null;
    }

    public String getTwitchClientId() {
        if (config.has("twitch") && config.getAsJsonObject("twitch").has("CLIENT_ID")) {
            return config.getAsJsonObject("twitch").get("CLIENT_ID").getAsString();
        }
        return null;
    }

    public String getTwitchClientSecret() {
        if (config.has("twitch") && config.getAsJsonObject("twitch").has("CLIENT_SECRET")) {
            return config.getAsJsonObject("twitch").get("CLIENT_SECRET").getAsString();
        }
        return null;
    }

    public String getTwitchAccessToken() {
        if (config.has("twitch") && config.getAsJsonObject("twitch").has("ACCESS_TOKEN")) {
            return config.getAsJsonObject("twitch").get("ACCESS_TOKEN").getAsString();
        }
        return null;
    }

    public String getTwitchRefreshToken() {
        if (config.has("twitch") && config.getAsJsonObject("twitch").has("REFRESH_TOKEN")) {
            return config.getAsJsonObject("twitch").get("REFRESH_TOKEN").getAsString();
        }
        return null;
    }

    public void setTwitchAccessToken(String accessToken) {
        if (!config.has("twitch")) {
            config.add("twitch", new JsonObject());
        }
        config.getAsJsonObject("twitch").addProperty("ACCESS_TOKEN", accessToken);
        saveConfig();
    }

    public void setTwitchRefreshToken(String refreshToken) {
        if (!config.has("twitch")) {
            config.add("twitch", new JsonObject());
        }
        config.getAsJsonObject("twitch").addProperty("REFRESH_TOKEN", refreshToken);
        saveConfig();
    }

    public String getYouTubeApiKey() {
        if (config.has("youtube") && config.getAsJsonObject("youtube").has("API_KEY")) {
            return config.getAsJsonObject("youtube").get("API_KEY").getAsString();
        }
        return null;
    }
}