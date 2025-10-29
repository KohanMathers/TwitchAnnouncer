import discord
import json
import logging
import time
import sqlite3
import commands
import aiohttp
from discord.ext import tasks

log_level = logging.INFO
CONFIG_DB = "TwitchAnnouncerConfig.db"
TOKEN_FILE = "token.json"
logging.basicConfig(level=log_level)
START_TIME = time.time()

def load_announced_streams():
    conn = sqlite3.connect(CONFIG_DB)
    cursor = conn.cursor()
    cursor.execute("SELECT guildID, announced FROM guildInfo")
    result = cursor.fetchall()
    cursor.close()
    conn.close()

    announced = {}
    for guild_id, json_data in result:
        try:
            if json_data:
                loaded_data = json.loads(json_data)
                if isinstance(loaded_data, list):
                    announced[guild_id] = loaded_data
                elif isinstance(loaded_data, dict):
                    logging.warning(f"Converting dict to list for guild {guild_id}")
                    announced[guild_id] = []
                else:
                    announced[guild_id] = []
            else:
                announced[guild_id] = []
        except json.JSONDecodeError as e:
            logging.error(f"JSON decode error for guild {guild_id}: {e}")
            announced[guild_id] = []
        except Exception as e:
            logging.error(f"Unexpected error loading announced streams for guild {guild_id}: {e}")
            announced[guild_id] = []
    return announced

announced_streams = load_announced_streams()

def load_token():
    try:
        with open(TOKEN_FILE, 'r') as file:
            data = json.load(file)
            return data.get('TOKEN')
    except (FileNotFoundError, json.JSONDecodeError):
        logging.error(f"Error: Could not read token from {TOKEN_FILE}.")
        return None

def load_twitch_token():
    try:
        with open(TOKEN_FILE, 'r') as file:
            data = json.load(file)
            return data.get('twitch')
    except (FileNotFoundError, json.JSONDecodeError):
        logging.error(f"Error: Could not read twitch details from {TOKEN_FILE}.")
        return None

def load_youtube_token():
    try:
        with open(TOKEN_FILE, 'r') as file:
            data = json.load(file)
            youtube = data.get('youtube')
            return youtube.get('API_KEY') if youtube else None
    except (FileNotFoundError, json.JSONDecodeError):
        logging.error(f"Error: Could not read youtube details from {TOKEN_FILE}.")
        return None

def load_prefix(guild_id):
    conn = sqlite3.connect(CONFIG_DB)
    cursor = conn.cursor()
    cursor.execute("SELECT prefix FROM guildInfo WHERE guildID = ?", (str(guild_id),))
    result = cursor.fetchone()
    if result:
        prefix = result[0]
    else:
        prefix = "twitchannouncer"
        cursor.execute("INSERT INTO guildInfo(guildID, prefix) VALUES (?, ?)", (str(guild_id), prefix))
        conn.commit()
    cursor.close()
    conn.close()
    return prefix

token = load_token()
twitch_details = load_twitch_token()
youtube_token = load_youtube_token()

intents = discord.Intents.default()
intents.message_content = True
intents.guilds = True
intents.messages = True
intents.members = True
intents.presences = True

class TwitchAnnouncer(discord.Client):
    def __init__(self):
        super().__init__(intents=intents)
        self.tree = discord.app_commands.CommandTree(self)
        self.start_time = time.time()

    async def setup_hook(self):
        await self.tree.sync()
        check_live_streams.start(self)
        check_youtube_videos.start()
        refresh_twitch_token.start()

client = TwitchAnnouncer()


@client.event
async def on_ready():
    logging.info(f"Logged in as {client.user}!")

@client.event
async def on_message(message: discord.Message):
    if message.author.bot or not message.guild:
        return

    prefix = load_prefix(message.guild.id)

    if message.content.lower().startswith(prefix.lower() + ","):
        command_line = message.content[len(prefix) + 1:].strip()
        command, *args = command_line.split(maxsplit=1)
        command = command.lower()

        if command == "ping":
            await commands.ping(message)
        elif command == "setprefix":
            if args:
                await commands.setprefix(message, args[0].strip())
            else:
                await commands.reply(message, f"Usage: {prefix}, setprefix <new_prefix>")
        elif command == "help":
            await commands.help(message)
        elif command == "info":
            await commands.info(message)
        elif command == "debug":
            await commands.debug(message, client, START_TIME)
        elif command == "register":
            if args:
                await commands.register_user(message, args[0].strip(), twitch_details)
            else:
                await commands.reply(message, f"Usage: {prefix}, register <twitch_username>")
        elif command == "listusers":
            await commands.list_users(message)
        elif command == "unregister":
            if args:
                await commands.unregister_user(message, args[0].strip())
            else:
                await commands.reply(message, f"Usage: {prefix}, unregister <twitch_username>")
        elif command == "setchannel":
            if args:
                await commands.set_announcement_channel(message, args[0].strip())
            else:
                await commands.reply(message, f"Usage: {prefix}, setchannel <twitch/youtube>")
        elif command == "registeryoutube":
            if args:
                await commands.register_youtube(message, args[0].strip())
            else:
                await commands.reply(message, f"Usage: {prefix}, registeryoutube <channel_handle>")
        elif command == "listyoutube":
            await commands.list_youtube(message)
        elif command == "unregisteryoutube":
            if args:
                await commands.unregister_youtube(message, args[0].strip())
            else:
                await commands.reply(message, f"Usage: {prefix}, unregister <channel_handle>")
        else:
            await commands.reply(message, f"Unknown command: {command}")

@tasks.loop(hours=24)
async def refresh_twitch_token():
    """Refresh the Twitch access token using the refresh token from token.json"""
    global twitch_details

    if not twitch_details:
        logging.error("Twitch credentials not loaded.")
        return

    url = "https://id.twitch.tv/oauth2/token"
    data = {
        "grant_type": "refresh_token",
        "refresh_token": twitch_details.get("REFRESH_TOKEN"),
        "client_id": twitch_details.get("CLIENT_ID"),
        "client_secret": twitch_details.get("CLIENT_SECRET"),
    }

    logging.info("Attempting to refresh Twitch token...")

    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(url, data=data) as response:
                if response.status == 200:
                    result = await response.json()
                    new_access_token = result.get("access_token")
                    new_refresh_token = result.get("refresh_token")

                    if new_access_token and new_refresh_token:
                        twitch_details["ACCESS_TOKEN"] = new_access_token
                        twitch_details["REFRESH_TOKEN"] = new_refresh_token

                        with open(TOKEN_FILE, "r") as f:
                            all_data = json.load(f)

                        all_data["twitch"] = twitch_details

                        with open(TOKEN_FILE, "w") as f:
                            json.dump(all_data, f, indent=2)

                        logging.info("Successfully refreshed Twitch token.")
                    else:
                        logging.error(f"Missing tokens in Twitch response: {result}")
                else:
                    logging.error(f"Failed to refresh token. Status code: {response.status}, Response: {await response.text()}")
    except Exception as e:
        logging.error(f"Exception while refreshing Twitch token: {e}")

@tasks.loop(minutes=1)
async def check_live_streams(client):
    global announced_streams
    if not twitch_details:
        logging.error("Missing Twitch credentials.")
        return

    headers = {
        "Client-ID": twitch_details.get("CLIENT_ID"),
        "Authorization": f"Bearer {twitch_details.get('ACCESS_TOKEN')}"
    }

    conn = sqlite3.connect(CONFIG_DB)
    cursor = conn.cursor()

    cursor.execute("SELECT guildID, primaryID FROM guildInfo")
    guild_rows = cursor.fetchall()

    for guild_id, primary_id in guild_rows:
        cursor.execute("SELECT twitch FROM announcementChannels WHERE primaryID = ?", (primary_id,))
        channel_row = cursor.fetchone()
        if not channel_row or not channel_row[0]:
            continue

        channel = client.get_channel(int(channel_row[0]))
        if not isinstance(channel, discord.TextChannel):
            continue

        cursor.execute("SELECT registered FROM registeredUsers WHERE primaryID = ?", (primary_id,))
        user_row = cursor.fetchone()
        if not user_row:
            continue

        try:
            user_data = json.loads(user_row[0])
        except Exception as e:
            logging.warning(f"Failed to parse registeredUsers JSON for guild {guild_id}: {e}")
            continue

        usernames = [user.get("username") for user in user_data if "username" in user]
        if not usernames:
            continue

        for i in range(0, len(usernames), 100):
            batch = usernames[i:i+100]
            query_params = "&".join(f"user_login={user}" for user in batch)
            url = f"https://api.twitch.tv/helix/streams?{query_params}"

            async with aiohttp.ClientSession() as session:
                async with session.get(url, headers=headers) as resp:
                    if resp.status != 200:
                        logging.error(f"Twitch API error: {resp.status} - {await resp.text()}")
                        continue

                    data = await resp.json()
                    live_streams = data.get("data", [])

                    for stream in live_streams:
                        stream_id = stream.get("id")
                        user_login = stream.get("user_login")
                        title = stream.get("title", "No Title")
                        game_name = stream.get("game_name", "Unknown Game")
                        preview_url = f"https://static-cdn.jtvnw.net/previews-ttv/live_user_{user_login.lower()}-1920x1080.jpg"

                        if stream_id in announced_streams.get(guild_id, []):
                            continue

                        user_info_url = f"https://api.twitch.tv/helix/users?login={user_login}"
                        try:
                            async with session.get(user_info_url, headers=headers) as user_resp:
                                if user_resp.status == 200:
                                    user_info_data = await user_resp.json()
                                    if user_info_data.get("data"):
                                        user_details = user_info_data["data"][0]
                                        display_name = user_details.get("display_name", user_login)
                                        profile_image_url = user_details.get("profile_image_url")
                                    else:
                                        display_name = user_login
                                        profile_image_url = None
                                else:
                                    logging.warning(f"Failed to fetch user info for {user_login}: {user_resp.status}")
                                    display_name = user_login
                                    profile_image_url = None
                        except Exception as e:
                            logging.error(f"Error fetching user info for {user_login}: {e}")
                            display_name = user_login
                            profile_image_url = None

                        embed = discord.Embed(
                            title=f"🔴 {display_name} is live!",
                            description=f"**{title}**\nNow playing: {game_name}\n[Watch here](https://twitch.tv/{user_login})",
                            color=discord.Color.purple()
                        )
                        embed.set_image(url=preview_url)

                        if profile_image_url:
                            embed.set_thumbnail(url=profile_image_url)

                        embed.set_footer(text="Twitch Stream Announcement")

                        await channel.send(embed=embed)
                        announced_streams.setdefault(guild_id, []).append(stream_id)

    for guild_id, stream_ids in announced_streams.items():
        cursor.execute(
            "UPDATE guildInfo SET announced = ? WHERE guildID = ?",
            (json.dumps(stream_ids), str(guild_id))
        )

    conn.commit()
    cursor.close()
    conn.close()

@tasks.loop(minutes=15)
async def check_youtube_videos():
    global announced_streams
    youtube_key = youtube_token
    if not youtube_key:
        logging.error("Missing YouTube API key.")
        return

    conn = sqlite3.connect(CONFIG_DB)
    cursor = conn.cursor()

    cursor.execute("SELECT guildID, primaryID FROM guildInfo")
    guild_rows = cursor.fetchall()

    for guild_id, primary_id in guild_rows:
        cursor.execute("SELECT youtube FROM announcementChannels WHERE primaryID = ?", (primary_id,))
        channel_row = cursor.fetchone()

        if not channel_row:
            print(f"No announcement channels record found for primaryID {primary_id}")
            continue

        if not channel_row[0]:
            print(f"YouTube channel ID is None/empty for primaryID {primary_id}")
            continue

        youtube_channel_id = channel_row[0]

        discord_channel = client.get_channel(int(youtube_channel_id))

        if discord_channel is None:
            try:
                guild = client.get_guild(int(guild_id))
                if guild:
                    discord_channel = guild.get_channel(int(youtube_channel_id))
            except Exception as e:
                print(f"Error getting guild: {e}")

        if discord_channel is None:
            try:
                discord_channel = await client.fetch_channel(int(youtube_channel_id))
            except Exception as e:
                print(f"Error fetching channel {youtube_channel_id}: {e}")


        if not isinstance(discord_channel, discord.TextChannel):
            print(f"Invalid YouTube announcement channel for guild {guild_id} - Expected TextChannel, got {type(discord_channel)}")
            if discord_channel is None:
                print(f"Channel {youtube_channel_id} not found - bot may not have access to this channel")
            continue

        cursor.execute("SELECT registered FROM registeredYoutubes WHERE primaryID = ?", (primary_id,))
        user_row = cursor.fetchone()
        if not user_row:
            print(f"No YouTube channels registered for guild {guild_id}")
            continue

        try:
            user_data = json.loads(user_row[0])
        except Exception as e:
            logging.warning(f"Failed to parse registeredYoutubes JSON for guild {guild_id}: {e}")
            continue

        for user in user_data:
            handle = user.get("handle", "")
            if not handle.startswith("@"):
                print(f"Invalid handle format: {handle}")
                continue

            handle_name = handle[1:]
            resolve_url = f"https://www.googleapis.com/youtube/v3/channels?part=id,snippet&forHandle={handle_name}&key={youtube_key}"

            async with aiohttp.ClientSession() as session:
                try:
                    async with session.get(resolve_url) as resp:
                        if resp.status != 200:
                            error_text = await resp.text()
                            logging.error(f"Failed to resolve handle {handle}: {resp.status} - {error_text}")
                            continue

                        data = await resp.json()
                        items = data.get("items", [])
                        if not items:
                            logging.error(f"No channel found for handle {handle}")
                            continue

                        channel_id = items[0]["id"]
                        channel_title = items[0]["snippet"]["title"]

                    uploads_playlist_id = "UU" + channel_id[2:]

                    playlist_url = f"https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&playlistId={uploads_playlist_id}&maxResults=5&order=date&key={youtube_key}"

                    async with session.get(playlist_url) as resp:
                        if resp.status != 200:
                            error_text = await resp.text()
                            logging.error(f"YouTube API error for uploads playlist {uploads_playlist_id}: {resp.status} - {error_text}")
                            continue

                        data = await resp.json()
                        items = data.get("items", [])
                        if not items:
                            continue


                        for item in items:
                            video_snippet = item["snippet"]
                            video_id = video_snippet["resourceId"]["videoId"]
                            title = video_snippet["title"]
                            published_at = video_snippet["publishedAt"]
                            description = video_snippet.get("description", "")[:200] + "..." if len(video_snippet.get("description", "")) > 200 else video_snippet.get("description", "")

                            if video_id in announced_streams.get(guild_id, []):
                                continue

                            from datetime import datetime, timezone, timedelta
                            try:
                                published_datetime = datetime.fromisoformat(published_at.replace('Z', '+00:00'))
                                now = datetime.now(timezone.utc)
                                time_diff = now - published_datetime

                                if time_diff > timedelta(hours=24):
                                    continue
                            except Exception as e:
                                logging.error(f"Error parsing published date {published_at}: {e}")
                                continue


                            video_url = f"https://www.youtube.com/watch?v={video_id}"
                            thumbnail_url = f"https://img.youtube.com/vi/{video_id}/maxresdefault.jpg"

                            embed = discord.Embed(
                                title=f"📺 {channel_title} uploaded a new video!",
                                description=f"**{title}**\n\n{description}\n\n[Watch here]({video_url})",
                                color=discord.Color.red()
                            )
                            embed.set_image(url=thumbnail_url)
                            embed.set_footer(text="YouTube Video Announcement")
                            embed.timestamp = published_datetime

                            try:
                                await discord_channel.send(embed=embed)
                                announced_streams.setdefault(guild_id, []).append(video_id)
                            except Exception as e:
                                logging.error(f"Failed to send YouTube announcement: {e}")

                except Exception as e:
                    logging.error(f"Error checking YouTube channel {handle}: {e}")
                    continue

    for guild_id, video_ids in announced_streams.items():
        cursor.execute(
            "UPDATE guildInfo SET announced = ? WHERE guildID = ?",
            (json.dumps(video_ids), str(guild_id))
        )

    conn.commit()
    cursor.close()
    conn.close()

client.run(token)
