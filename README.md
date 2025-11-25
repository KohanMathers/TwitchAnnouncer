# TwitchAnnouncer

A Discord bot built with JDA (Java Discord API) that announces Twitch streams and YouTube videos in your Discord server.

## Features

- 🎮 **Twitch Integration**: Monitor and announce when registered streamers go live
- 📺 **YouTube Integration**: Announce new video uploads from registered channels
- 🔧 **Customizable**: Set custom prefixes and announcement channels per server
- 💾 **SQLite Database**: Persistent storage of guild configurations
- ⚡ **Slash Commands**: Modern Discord slash command support
- 🔄 **Auto Token Refresh**: Automatic Twitch OAuth token refresh

## Requirements

- Java 17 or higher
- Maven 3.6+
- Discord Bot Token
- Twitch API Credentials (Client ID, Client Secret, Access Token, Refresh Token)
- YouTube Data API v3 Key

## Project Structure

```
src/main/java/me/kmathers/twitchannouncer/
├── TwitchAnnouncer.java          # Main entry point
├── ConfigManager.java            # Configuration and token management
├── DatabaseManager.java          # SQLite database operations
├── TwitchManager.java            # Twitch API integration
├── YouTubeManager.java           # YouTube API integration
├── CommandListener.java          # Text command handler
├── SlashCommandListener.java    # Slash command handler
├── SlashCommandRegistrar.java   # Command registration
├── TwitchUser.java              # Twitch user model
└── YouTubeChannel.java          # YouTube channel model
```

## Setup

### 1. Clone the Repository

```bash
git clone https://github.com/KohanMathers/TwitchAnnouncer.git
cd TwitchAnnouncer
```

### 2. Configure token.json

Create a `token.json` file in the project root:

```json
{
  "TOKEN": "your-discord-bot-token",
  "twitch": {
    "CLIENT_ID": "your-twitch-client-id",
    "CLIENT_SECRET": "your-twitch-client-secret",
    "ACCESS_TOKEN": "your-twitch-access-token",
    "REFRESH_TOKEN": "your-twitch-refresh-token"
  },
  "youtube": {
    "API_KEY": "your-youtube-api-key"
  }
}
```

#### Getting Twitch Credentials:
1. Go to https://dev.twitch.tv/console/apps
2. Create a new application
3. Set OAuth Redirect URL to `http://localhost`
4. Copy the Client ID and generate a Client Secret
5. Generate tokens at https://twitchtokengenerator.com/

#### Getting YouTube API Key:
1. Go to https://console.cloud.google.com/
2. Create a new project
3. Enable YouTube Data API v3
4. Create credentials (API Key)

### 3. Build the Project

```bash
mvn clean package
```

This will create a fat JAR with all dependencies in `target/twitchannouncer-2.0.jar`

### 4. Run the Bot

```bash
java -jar target/twitchannouncer-2.0.jar
```

## Commands

### Slash Commands (/)

| Command | Description | Permission Required |
|---------|-------------|-------------------|
| `/ping` | Check if bot is responsive | None |
| `/help` | Display all available commands | None |
| `/info` | Show bot information | None |
| `/debug` | Display debug information | None |
| `/setprefix <prefix>` | Change the bot's text command prefix | Manage Server |
| `/register <username>` | Register a Twitch streamer | None |
| `/unregister <username>` | Remove a Twitch streamer | None |
| `/listusers` | List all registered Twitch users | None |
| `/registeryoutube <handle>` | Register a YouTube channel | None |
| `/unregisteryoutube <handle>` | Remove a YouTube channel | None |
| `/listyoutube` | List all registered YouTube channels | None |
| `/setchannel <platform>` | Set announcement channel (twitch/youtube) | Manage Channels |

### Text Commands (prefix,)

All slash commands are also available as text commands using the configured prefix (default: `twitchannouncer,`).

Example:
```
twitchannouncer, register xqc
twitchannouncer, setchannel twitch
```

## Database Schema

The bot uses SQLite with the following tables:

- **guildInfo**: Stores guild configuration (prefix, announced streams)
- **registeredUsers**: Stores registered Twitch users per guild
- **registeredYoutubes**: Stores registered YouTube channels per guild
- **announcementChannels**: Stores announcement channel IDs per platform

## Scheduled Tasks

- **Twitch Stream Check**: Every 1 minute
- **YouTube Video Check**: Every 1 minute
- **Token Refresh**: Every 24 hours

## Dependencies

- **JDA 5.0.0-beta.20**: Discord API wrapper
- **SQLite JDBC 3.45.0.0**: Database driver
- **Gson 2.10.1**: JSON parsing
- **OkHttp 4.12.0**: HTTP client
- **SLF4J + Logback**: Logging framework

## Development

### Building from Source

```bash
# Clean previous builds
mvn clean

# Compile
mvn compile

# Run tests (if any)
mvn test

# Package
mvn package

# Run without packaging
mvn exec:java -Dexec.mainClass="me.kmathers.twitchannouncer.TwitchAnnouncer"
```

### IDE Setup

**IntelliJ IDEA:**
1. Open the project folder
2. IntelliJ will automatically detect the Maven project
3. Wait for dependencies to download
4. Run `TwitchAnnouncer.java`

**Eclipse:**
1. Import as "Existing Maven Project"
2. Right-click project → Maven → Update Project
3. Run `TwitchAnnouncer.java`

## Logging

Logs are output to console and can be configured via `logback.xml` (create in `src/main/resources/`):

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is open source. See LICENSE file for details.

## Support

Join the support server: [Discord Invite](https://discord.gg/FZuVXszuuM)

## Author

**Kohan Mathers**
- Website: https://kmathers.co.uk
- GitHub: [@KohanMathers](https://github.com/KohanMathers)

## Acknowledgments

- Original Python version: [TwitchAnnouncer](https://github.com/KohanMathers/TwitchAnnouncer)
- JDA Library: [DV8FromTheWorld/JDA](https://github.com/DV8FromTheWorld/JDA)
- Discord Developer Portal: https://discord.com/developers