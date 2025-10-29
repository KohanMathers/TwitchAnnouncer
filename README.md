# TwitchAnnouncer

A multi-platform Discord bot that automatically announces Twitch streams and YouTube videos in your server.

## Features

- 🔴 **Twitch Stream Announcements** - Get notified when registered streamers go live
- 📺 **YouTube Video Announcements** - Get notified when channels upload new videos
- 🔧 **Easy Configuration** - Simple commands to set up and manage
- 📊 **Multi-Account Support** - Register unlimited Twitch users and YouTube channels
- 🎨 **Rich Embeds** - Beautiful announcement messages with thumbnails and stream info
- ⚙️ **Customizable Prefix** - Set your own command prefix per server

## Installation

### Prerequisites

- Python 3.8 or higher
- Discord Bot Token
- Twitch API Credentials (Client ID, Client Secret, Access Token, Refresh Token)
- YouTube API Key

### Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/TwitchAnnouncer.git
cd TwitchAnnouncer
```

2. Install required dependencies:
```bash
pip install discord.py aiohttp psutil
```

3. Create a `token.json` file in the root directory:
```json
{
  "TOKEN": "your_discord_bot_token",
  "twitch": {
    "CLIENT_ID": "your_twitch_client_id",
    "CLIENT_SECRET": "your_twitch_client_secret",
    "ACCESS_TOKEN": "your_twitch_access_token",
    "REFRESH_TOKEN": "your_twitch_refresh_token"
  },
  "youtube": {
    "API_KEY": "your_youtube_api_key"
  }
}
```

4. Initialize the database:
```sql
CREATE TABLE IF NOT EXISTS guildInfo (
    guildID TEXT PRIMARY KEY,
    primaryID INTEGER UNIQUE,
    prefix TEXT DEFAULT 'twitchannouncer',
    announced TEXT
);

CREATE TABLE IF NOT EXISTS registeredUsers (
    primaryID INTEGER PRIMARY KEY,
    registered TEXT,
    FOREIGN KEY (primaryID) REFERENCES guildInfo(primaryID)
);

CREATE TABLE IF NOT EXISTS registeredYoutubes (
    primaryID INTEGER PRIMARY KEY,
    registered TEXT,
    FOREIGN KEY (primaryID) REFERENCES guildInfo(primaryID)
);

CREATE TABLE IF NOT EXISTS announcementChannels (
    primaryID INTEGER PRIMARY KEY,
    twitch TEXT,
    youtube TEXT,
    FOREIGN KEY (primaryID) REFERENCES guildInfo(primaryID)
);
```

5. Run the bot:
```bash
python main.py
```

## Commands

### General Commands

| Command | Description | Permission Required |
|---------|-------------|-------------------|
| `/ping` | Check if the bot is responsive | None |
| `/help` | Display all available commands | None |
| `/info` | Get information about the bot | None |
| `/debug` | Show detailed bot debug information | None |
| `/setprefix <prefix>` | Change the command prefix | Manage Messages |

### Twitch Commands

| Command | Description | Permission Required |
|---------|-------------|-------------------|
| `/register <username>` | Register a Twitch user for announcements | None |
| `/unregister <username>` | Remove a Twitch user from announcements | None |
| `/listusers` | List all registered Twitch users | None |
| `/setchannel twitch` | Set current channel for Twitch announcements | Manage Channels |

### YouTube Commands

| Command | Description | Permission Required |
|---------|-------------|-------------------|
| `/registeryoutube <handle>` | Register a YouTube channel (e.g., @channelname) | None |
| `/unregisteryoutube <handle>` | Remove a YouTube channel from announcements | None |
| `/listyoutube` | List all registered YouTube channels | None |
| `/setchannel youtube` | Set current channel for YouTube announcements | Manage Channels |

## Usage Example

1. Set the announcement channel:
```
/setchannel twitch
```

2. Register a Twitch streamer:
```
/register ninja
```

3. The bot will automatically announce when the streamer goes live with a rich embed containing:
   - Stream title
   - Game being played
   - Streamer profile picture
   - Stream preview image
   - Direct link to the stream

## How It Works

- **Twitch Monitoring**: Checks for live streams every 1 minute using the Twitch Helix API
- **YouTube Monitoring**: Checks for new videos every 15 minutes using the YouTube Data API
- **Token Management**: Automatically refreshes Twitch OAuth tokens every 24 hours
- **Duplicate Prevention**: Tracks announced streams to avoid duplicate notifications

## API Rate Limits

- Twitch API: Handles up to 100 users per batch request
- YouTube API: Checks 5 most recent videos per channel
- Both services implement proper rate limiting and error handling

## Configuration

The bot uses SQLite for persistent storage:
- `TwitchAnnouncerConfig.db` - Stores guild settings, registered users, and announcement history
- Supports multiple Discord servers with independent configurations

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

Join the support server: [Discord Server](https://discord.gg/FZuVXszuuM)

## Author

[Kohan Mathers](https://github.com/KohanMathers)

## Version

2.0

## License

This project is open source and available under the MIT License.

## Troubleshooting

### Bot not announcing streams
- Verify Twitch API credentials are valid
- Check that the bot has permission to send messages in the announcement channel
- Ensure users are registered with correct usernames (case-insensitive)

### Database errors
- Ensure the database file has write permissions
- Verify all required tables are created
- Check logs for specific error messages

### YouTube not working
- Verify YouTube API key is valid and has quota remaining
- Ensure channel handles start with `@`
- Check that channels exist and are public

## Notes

- The bot requires the Message Content intent to be enabled in the Discord Developer Portal
- Twitch tokens expire and are automatically refreshed every 24 hours
- YouTube video checks are limited to content published within the last 24 hours
