package me.kmathers.twitchannouncer.database;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String CONFIG_DB = "TwitchAnnouncerConfig.db";
    private static final Gson gson = new Gson();

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + CONFIG_DB);
    }

    public Map<String, List<String>> loadAnnouncedStreams() {
        Map<String, List<String>> announced = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT guildID, announced FROM guildInfo")) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String guildId = rs.getString("guildID");
                String jsonData = rs.getString("announced");

                try {
                    if (jsonData != null && !jsonData.isEmpty()) {
                        List<String> streamList = gson.fromJson(jsonData, new TypeToken<List<String>>(){}.getType());
                        announced.put(guildId, streamList != null ? streamList : new ArrayList<>());
                    } else {
                        announced.put(guildId, new ArrayList<>());
                    }
                } catch (JsonSyntaxException e) {
                    logger.error("JSON decode error for guild {}: {}", guildId, e.getMessage());
                    announced.put(guildId, new ArrayList<>());
                }
            }
        } catch (SQLException e) {
            logger.error("Error loading announced streams", e);
        }

        return announced;
    }

    public void saveAnnouncedStreams(Map<String, List<String>> announcedStreams) {
        try (Connection conn = getConnection()) {
            for (Map.Entry<String, List<String>> entry : announcedStreams.entrySet()) {
                String guildId = entry.getKey();
                String json = gson.toJson(entry.getValue());

                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE guildInfo SET announced = ? WHERE guildID = ?")) {
                    stmt.setString(1, json);
                    stmt.setString(2, guildId);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.error("Error saving announced streams", e);
        }
    }

    public String loadPrefix(String guildId) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT prefix FROM guildInfo WHERE guildID = ?")) {

            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("prefix");
            } else {
                String prefix = "twitchannouncer";
                try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO guildInfo(guildID, prefix) VALUES (?, ?)")) {
                    insertStmt.setString(1, guildId);
                    insertStmt.setString(2, prefix);
                    insertStmt.executeUpdate();
                }
                return prefix;
            }
        } catch (SQLException e) {
            logger.error("Error loading prefix for guild {}", guildId, e);
            return "twitchannouncer";
        }
    }

    public void setPrefix(String guildId, String newPrefix) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM guildInfo WHERE guildID = ?")) {

            checkStmt.setString(1, guildId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                try (PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE guildInfo SET prefix = ? WHERE guildID = ?")) {
                    updateStmt.setString(1, newPrefix);
                    updateStmt.setString(2, guildId);
                    updateStmt.executeUpdate();
                }
            } else {
                try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO guildInfo (guildID, prefix) VALUES (?, ?)")) {
                    insertStmt.setString(1, guildId);
                    insertStmt.setString(2, newPrefix);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    public String getPrimaryId(String guildId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT primaryID FROM guildInfo WHERE guildID = ?")) {

            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("primaryID");
            }
            return null;
        }
    }

    public List<RegisteredUser> getRegisteredUsers(String primaryId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT registered FROM registeredUsers WHERE primaryID = ?")) {

            stmt.setString(1, primaryId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String json = rs.getString("registered");
                if (json != null && !json.isEmpty() && !json.equals("{}")) {
                    return gson.fromJson(json, new TypeToken<List<RegisteredUser>>(){}.getType());
                }
            }
            return new ArrayList<>();
        }
    }

    public void saveRegisteredUsers(String primaryId, List<RegisteredUser> users) throws SQLException {
        String json = gson.toJson(users);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO registeredUsers (primaryID, registered) VALUES (?, ?) " +
                 "ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered")) {

            stmt.setString(1, primaryId);
            stmt.setString(2, json);
            stmt.executeUpdate();
        }
    }

    public List<RegisteredYouTube> getRegisteredYouTubes(String primaryId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT registered FROM registeredYoutubes WHERE primaryID = ?")) {

            stmt.setString(1, primaryId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String json = rs.getString("registered");
                if (json != null && !json.isEmpty() && !json.equals("[]")) {
                    return gson.fromJson(json, new TypeToken<List<RegisteredYouTube>>(){}.getType());
                }
            }
            return new ArrayList<>();
        }
    }

    public void saveRegisteredYouTubes(String primaryId, List<RegisteredYouTube> channels) throws SQLException {
        String json = gson.toJson(channels);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO registeredYoutubes (primaryID, registered) VALUES (?, ?) " +
                 "ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered")) {

            stmt.setString(1, primaryId);
            stmt.setString(2, json);
            stmt.executeUpdate();
        }
    }

    public String getAnnouncementChannel(String primaryId, String platform) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT " + platform + " FROM announcementChannels WHERE primaryID = ?")) {

            stmt.setString(1, primaryId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        }
    }

    public void setAnnouncementChannel(String primaryId, String platform, String channelId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM announcementChannels WHERE primaryID = ?")) {

            checkStmt.setString(1, primaryId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                try (PreparedStatement updateStmt = conn.prepareStatement(
                    "UPDATE announcementChannels SET " + platform + " = ? WHERE primaryID = ?")) {
                    updateStmt.setString(1, channelId);
                    updateStmt.setString(2, primaryId);
                    updateStmt.executeUpdate();
                }
            } else {
                String twitchId = platform.equals("twitch") ? channelId : null;
                String youtubeId = platform.equals("youtube") ? channelId : null;

                try (PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO announcementChannels (primaryID, twitch, youtube) VALUES (?, ?, ?)")) {
                    insertStmt.setString(1, primaryId);
                    insertStmt.setString(2, twitchId);
                    insertStmt.setString(3, youtubeId);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    public List<GuildInfo> getAllGuilds() throws SQLException {
        List<GuildInfo> guilds = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT guildID, primaryID FROM guildInfo")) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                guilds.add(new GuildInfo(rs.getString("guildID"), rs.getString("primaryID")));
            }
        }

        return guilds;
    }

    public static class RegisteredUser {
        private String username;
        private String display_name;
        private String profile_image_url;
        private String created_at;

        public RegisteredUser(String username, String displayName, String profileImageUrl, String createdAt) {
            this.username = username;
            this.display_name = displayName;
            this.profile_image_url = profileImageUrl;
            this.created_at = createdAt;
        }

        public String getUsername() { return username; }
        public String getDisplayName() { return display_name; }
        public String getProfileImageUrl() { return profile_image_url; }
        public String getCreatedAt() { return created_at; }
    }

    public static class RegisteredYouTube {
        private String handle;
        private String registered_at;

        public RegisteredYouTube(String handle, String registeredAt) {
            this.handle = handle;
            this.registered_at = registeredAt;
        }

        public String getHandle() { return handle; }
        public String getRegisteredAt() { return registered_at; }
    }

    public static class GuildInfo {
        private final String guildId;
        private final String primaryId;

        public GuildInfo(String guildId, String primaryId) {
            this.guildId = guildId;
            this.primaryId = primaryId;
        }

        public String getGuildId() { return guildId; }
        public String getPrimaryId() { return primaryId; }
    }
}
