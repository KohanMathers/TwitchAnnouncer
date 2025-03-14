# 🎥 TwitchAnnouncer

A Discord bot that automatically announces when registered Twitch streamers go live. It allows users to register Twitch usernames, set an announcement channel, and notifies a specific role when a stream starts.  

## ✨ Features  

✅ **Live Stream Announcements** – Posts an embedded message when a registered Twitch streamer goes live.  
✅ **Customizable Ping Channel & Role** – Choose where announcements appear and which role gets notified.  
✅ **Slash Commands** – Uses Discord's modern slash commands for easy interaction.  
✅ **Automatic Token Refreshing** – Ensures continuous access to the Twitch API.  
✅ **Persistent Data** – Stores registered users and announced streams to prevent duplicate notifications.  

## 🚀 Setup & Installation  

### 1️⃣ Prerequisites  
- Python 3.8+  
- A Discord bot token  
- Twitch API credentials (`Client ID`, `Client Secret`, `Refresh Token`)  

### 2️⃣ Install Dependencies  
```sh
pip install discord aiohttp
```

### 3️⃣ Configure the Bot  

1. **Create necessary files**  
   - `token.txt` → Store your Discord bot token.  
   - `twitch.txt` → Store Twitch API credentials in the format:  
     ```
     CLIENT_ID=your_client_id
     CLIENT_SECRET=your_client_secret
     ACCESS_TOKEN=your_access_token
     REFRESH_TOKEN=your_refresh_token
     ```
   - `settings.json` → Auto-generated to store guild settings.  
   - `announced_streams.json` → Tracks announced streams to prevent duplicates.  

2. **Run the bot**  
```sh
python bot.py
```

## 📜 Commands  

| Command                | Description |
|------------------------|-------------|
| `/setchannel [role]`   | Set the announcement channel (and mention a role). |
| `/register <username>` | Register a Twitch streamer for announcements. |
| `/unregister <username>` | Remove a Twitch streamer from the list. |
| `/listaccounts`       | Show all registered Twitch usernames. |
| `/info`              | Display bot info and uptime. |
| `/help`              | Show available commands. |

## 🛠 How It Works  

1. **Register Streamers**  
   - Use `/register <twitch_username>` to add a Twitch channel.  
2. **Set an Announcement Channel**  
   - Run `/setchannel` in the desired channel (optionally specify a role for pings).  
3. **Automatic Stream Detection**  
   - The bot checks Twitch every minute for live streams.  
   - If a registered streamer goes live, an embed message is sent to the announcement channel.  

## 🎮 Example Announcement  

> 🔴 **StreamerName is live!**  
> 🎮 Playing: **Game Title**  
> 💬 **Stream Title**  
> 📺 [Watch Now](https://twitch.tv/StreamerName)  

## 📝 Notes  

- The bot requires the `MESSAGE_CONTENT` intent enabled in the Discord Developer Portal.  
- Twitch API tokens automatically refresh every 24 hours.  
- To manually restart the bot and refresh data, simply restart the script.  

## 🤝 Contributing  

Want to improve this bot? Feel free to fork and submit a pull request!  

## 📜 License  

This project is **MIT Licensed**.  
