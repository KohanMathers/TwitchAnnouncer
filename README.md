# TwitchAnnouncer

A Discord bot that monitors and announces Twitch streams and YouTube videos for multiple accounts.

## Features

- Monitor multiple Twitch channels for live streams
- Monitor YouTube channels for new video uploads
- Automated stream/video announcements in Discord
- Slash command support
- SQLite database for tracking announced content
- Automatic Twitch token refresh (every 24 hours)
- Configurable check intervals

## Requirements

- Java 17 or higher
- Maven
- Discord Bot Token
- Twitch API credentials (Client ID, Client Secret)
- YouTube API credentials (API Key)

## Setup

1. Clone this repository
2. Create a `token.json` file in the project root with your configuration:

```json
{
  "discordToken": "your-discord-bot-token",
  "twitch": {
    "clientId": "your-twitch-client-id",
    "clientSecret": "your-twitch-client-secret"
  },
  "youtube": {
    "apiKey": "your-youtube-api-key"
  }
}
```

3. Build the project:
```bash
mvn clean package
```

4. Run the bot:
```bash
java -jar target/twitch-announcer-2.0.jar
```

## Configuration

The bot uses a `token.json` file for configuration. This file should contain:

- **discordToken**: Your Discord bot token
- **twitch**: Twitch API credentials (optional if only using YouTube)
  - clientId
  - clientSecret
- **youtube**: YouTube API credentials (optional if only using Twitch)
  - apiKey

## How It Works

- **Twitch Stream Checker**: Runs every 1 minute to check for live streams
- **Twitch Token Refresher**: Runs every 24 hours to refresh API tokens
- **YouTube Video Checker**: Runs every 15 minutes to check for new videos

The bot tracks previously announced streams/videos in an SQLite database to avoid duplicate announcements.

## Dependencies

- [JDA](https://github.com/discord-jda/JDA) - Java Discord API
- SQLite JDBC Driver
- Gson - JSON parsing
- OkHttp - HTTP requests
- SLF4J & Logback - Logging

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
