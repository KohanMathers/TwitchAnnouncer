package me.kmathers.twitchannouncer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_NAME = "TwitchAnnouncerConfig.db";
    private Connection connection;
    private final Gson gson;

    public DatabaseManager() {
        this.gson = new Gson();
    }

    public void initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
            createTables();
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
        }
    }

    private void createTables() throws SQLException {
        String createGuildInfo = """
            CREATE TABLE IF NOT EXISTS guildInfo (
                primaryID INTEGER PRIMARY KEY AUTOINCREMENT,
                guildID TEXT UNIQUE NOT NULL,
                prefix TEXT DEFAULT 'twitchannouncer',
                announced TEXT DEFAULT '[]'
            )
        """;

        String createRegisteredUsers = """
            CREATE TABLE IF NOT EXISTS registeredUsers (
                primaryID INTEGER PRIMARY KEY,
                registered TEXT DEFAULT '[]',
                FOREIGN KEY (primaryID) REFERENCES guildInfo(primaryID)
            )
        """;

        String createRegisteredYoutubes = """
            CREATE TABLE IF NOT EXISTS registeredYoutubes (
                primaryID INTEGER PRIMARY KEY,
                registered TEXT DEFAULT '[]',
                FOREIGN KEY (primaryID) REFERENCES guildInfo(primaryID)
            )
        """;

        String createAnnouncementChannels = """
            CREATE TABLE IF NOT EXISTS announcementChannels (
                primaryID INTEGER PRIMARY KEY,
                twitch TEXT,
                youtube TEXT,
                FOREIGN KEY (primaryID) REFERENCES guildInfo(primaryID)
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createGuildInfo);
            stmt.execute(createRegisteredUsers);
            stmt.execute(createRegisteredYoutubes);
            stmt.execute(createAnnouncementChannels);
        }
    }

    public String getPrefix(String guildId) {
        String sql = "SELECT prefix FROM guildInfo WHERE guildID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("prefix");
            } else {
                // Initialize guild
                initializeGuild(guildId);
                return "twitchannouncer";
            }
        } catch (SQLException e) {
            logger.error("Error getting prefix for guild {}", guildId, e);
            return "twitchannouncer";
        }
    }

    public void setPrefix(String guildId, String prefix) {
        String sql = "UPDATE guildInfo SET prefix = ? WHERE guildID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, prefix);
            stmt.setString(2, guildId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error setting prefix for guild {}", guildId, e);
        }
    }

    public void initializeGuild(String guildId) {
        String sql = "INSERT OR IGNORE INTO guildInfo (guildID, prefix) VALUES (?, 'twitchannouncer')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error initializing guild {}", guildId, e);
        }
    }

    public Integer getPrimaryId(String guildId) {
        String sql = "SELECT primaryID FROM guildInfo WHERE guildID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("primaryID");
            }
        } catch (SQLException e) {
            logger.error("Error getting primaryID for guild {}", guildId, e);
        }
        return null;
    }

    public List<TwitchUser> getRegisteredTwitchUsers(Integer primaryId) {
        String sql = "SELECT registered FROM registeredUsers WHERE primaryID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, primaryId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String json = rs.getString("registered");
                return gson.fromJson(json, new TypeToken<List<TwitchUser>>(){}.getType());
            }
        } catch (SQLException e) {
            logger.error("Error getting registered Twitch users", e);
        }
        return new ArrayList<>();
    }

    public void saveRegisteredTwitchUsers(Integer primaryId, List<TwitchUser> users) {
        String sql = "INSERT INTO registeredUsers (primaryID, registered) VALUES (?, ?) " +
                     "ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, primaryId);
            stmt.setString(2, gson.toJson(users));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving registered Twitch users", e);
        }
    }

    public List<YouTubeChannel> getRegisteredYouTubeChannels(Integer primaryId) {
        String sql = "SELECT registered FROM registeredYoutubes WHERE primaryID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, primaryId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String json = rs.getString("registered");
                return gson.fromJson(json, new TypeToken<List<YouTubeChannel>>(){}.getType());
            }
        } catch (SQLException e) {
            logger.error("Error getting registered YouTube channels", e);
        }
        return new ArrayList<>();
    }

    public void saveRegisteredYouTubeChannels(Integer primaryId, List<YouTubeChannel> channels) {
        String sql = "INSERT INTO registeredYoutubes (primaryID, registered) VALUES (?, ?) " +
                     "ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, primaryId);
            stmt.setString(2, gson.toJson(channels));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving registered YouTube channels", e);
        }
    }

    public String getAnnouncementChannel(Integer primaryId, String platform) {
        String sql = "SELECT " + platform + " FROM announcementChannels WHERE primaryID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, primaryId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString(platform);
            }
        } catch (SQLException e) {
            logger.error("Error getting announcement channel", e);
        }
        return null;
    }

    public void setAnnouncementChannel(Integer primaryId, String platform, String channelId) {
        String checkSql = "SELECT 1 FROM announcementChannels WHERE primaryID = ?";
        String insertSql = "INSERT INTO announcementChannels (primaryID, " + platform + ") VALUES (?, ?)";
        String updateSql = "UPDATE announcementChannels SET " + platform + " = ? WHERE primaryID = ?";
        
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, primaryId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                    updateStmt.setString(1, channelId);
                    updateStmt.setInt(2, primaryId);
                    updateStmt.executeUpdate();
                }
            } else {
                try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, primaryId);
                    insertStmt.setString(2, channelId);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.error("Error setting announcement channel", e);
        }
    }

    public List<String> getAnnouncedStreams(String guildId) {
        String sql = "SELECT announced FROM guildInfo WHERE guildID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String json = rs.getString("announced");
                return gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
            }
        } catch (SQLException e) {
            logger.error("Error getting announced streams", e);
        }
        return new ArrayList<>();
    }

    public void saveAnnouncedStreams(String guildId, List<String> streamIds) {
        String sql = "UPDATE guildInfo SET announced = ? WHERE guildID = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, gson.toJson(streamIds));
            stmt.setString(2, guildId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving announced streams", e);
        }
    }

    public List<GuildInfo> getAllGuilds() {
        List<GuildInfo> guilds = new ArrayList<>();
        String sql = "SELECT guildID, primaryID FROM guildInfo";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                guilds.add(new GuildInfo(rs.getString("guildID"), rs.getInt("primaryID")));
            }
        } catch (SQLException e) {
            logger.error("Error getting all guilds", e);
        }
        return guilds;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }

    public static class GuildInfo {
        public final String guildId;
        public final int primaryId;

        public GuildInfo(String guildId, int primaryId) {
            this.guildId = guildId;
            this.primaryId = primaryId;
        }
    }
}