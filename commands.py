import discord
import sqlite3
import datetime
import random
from datetime import datetime
import time
import psutil
import os
import platform
import aiohttp
import json
import io

CONFIG_DB = "TwitchAnnouncerConfig.db"
SUPPORT_MESSAGE = "Please let <@520872721060462592> know with the error code in this message."
MAX_IMAGE_WIDTH = 300
TWITCH_ICON_URL = "https://cdn-icons-png.flaticon.com/512/5968/5968819.png"

class MessageWrapper:
    """Wrapper to make Interaction objects compatible with Message-based functions"""
    def __init__(self, obj):
        self._obj = obj

    @property
    def author(self):
        if hasattr(self._obj, 'author'):
            return self._obj.author
        elif hasattr(self._obj, 'user'):
            return self._obj.user
        else:
            raise AttributeError("No author or user found")

    @property
    def guild(self):
        return self._obj.guild

    @property
    def channel(self):
        return self._obj.channel

    def __getattr__(self, name):
        return getattr(self._obj, name)

def wrap_message(obj):
    """Wrap Message or Interaction objects to ensure compatibility"""
    if isinstance(obj, discord.Interaction):
        return MessageWrapper(obj)
    return obj

async def reply(target, content=None, embed=None, **kwargs):
    if isinstance(target, discord.Interaction):
        try:
            if not target.response.is_done():
                await target.response.send_message(content=content, embed=embed, **kwargs)
            else:
                await target.followup.send(content=content, embed=embed, **kwargs)
        except discord.NotFound:
            await target.followup.send(content=f"An error occurred while trying to reply: ERR-USER-NOT-FOUND. {SUPPORT_MESSAGE}", ephemeral=True)
    elif isinstance(target, (discord.Message, MessageWrapper)):
        kwargs.pop("ephemeral", None)
        await target.reply(content=content, embed=embed, **kwargs)


async def ping(target):
    await reply(target, "Pong!")

async def setprefix(message_obj, new_prefix):
    message = wrap_message(message_obj)

    if isinstance(message_obj, discord.Interaction):
        if not message.author.guild_permissions.manage_messages:
            await reply(message_obj, "You need the Manage Messages permission to change the prefix.")
            return
    else:
        if not message.author.guild_permissions.manage_messages:
            await reply(message_obj, "You need the Manage Messages permission to change the prefix.")
            return

    conn = sqlite3.connect(CONFIG_DB)
    cursor = conn.cursor()
    cursor.execute("SELECT 1 FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),))
    exists = cursor.fetchone()

    if exists:
        cursor.execute("UPDATE guildInfo SET prefix = ? WHERE guildID = ?", (new_prefix, str(message.guild.id)))
    else:
        cursor.execute("INSERT INTO guildInfo (guildID, prefix) VALUES (?, ?)", (str(message.guild.id), new_prefix))

    conn.commit()
    conn.close()
    await reply(message_obj, f"Prefix set to `{new_prefix}` for this server.")

async def help(message_obj):
    message = wrap_message(message_obj)
    embed = discord.Embed(title="Commands List", description="Available commands:", color=discord.Color.blue())

    embed.add_field(name="/ping", value="Ping the bot", inline=False)
    embed.add_field(name="/setprefix <new_prefix>", value="Set a new prefix for the bot (requires Manage Messages permission)", inline=False)
    embed.add_field(name="/help", value="Displays this message", inline=False)
    embed.add_field(name="/info", value="Get information about the bot", inline=False)
    embed.add_field(name="/debug", value="Show detailed bot debug information", inline=False)

    embed.add_field(name="/register <username>", value="Register a Twitch username for stream announcements", inline=False)
    embed.add_field(name="/unregister <username>", value="Remove a Twitch username from announcements", inline=False)
    embed.add_field(name="/listusers", value="List all registered Twitch users for this server", inline=False)

    embed.add_field(name="/registeryoutube <handle>", value="Register a YouTube channel handle for announcements", inline=False)
    embed.add_field(name="/unregisteryoutube <handle>", value="Remove a YouTube channel from announcements", inline=False)
    embed.add_field(name="/listyoutube", value="List all registered YouTube channels for this server", inline=False)

    embed.add_field(name="/setchannel <platform>", value="Set the current channel for announcements (twitch/youtube) - requires Manage Channels permission", inline=False)

    await reply(message_obj, embed=embed)

async def info(message_obj):
    message = wrap_message(message_obj)
    embed = discord.Embed(title="TwitchAnnouncer", description="Information about the bot", color=discord.Color.blue())
    embed.add_field(name="Description", value="TwitchAnnouncer is a multi-account Discord bot allowing you to register any amount of twitch accounts to have streams announced in your guild.", inline=False)
    embed.add_field(name="Version", value="2.0", inline=False)
    embed.add_field(name="Author", value="[Kohan Mathers](https://github.com/KohanMathers)", inline=False)
    embed.add_field(name="Support Server", value="[Join here](https://discord.gg/FZuVXszuuM)", inline=False)
    embed.add_field(name="Commands", value="Use `/help` to see available commands.", inline=False)
    await reply(message_obj, embed=embed)

async def register_user(message_obj, username, twitch_details):
    message = wrap_message(message_obj)
    username = username.lower()

    headers = {
        "Client-ID": twitch_details.get("CLIENT_ID"),
        "Authorization": f"Bearer {twitch_details.get('ACCESS_TOKEN')}"
    }

    url = f"https://api.twitch.tv/helix/users?login={username}"
    async with aiohttp.ClientSession() as session:
        try:
            async with session.get(url, headers=headers) as resp:
                if resp.status != 200:
                    error = await resp.text()
                    await reply(message_obj, f"Failed to validate username. Error from Twitch: `{error}`", ephemeral=True)
                    return

                data = await resp.json()
                if not data.get("data"):
                    error_embed = discord.Embed(
                        title="Twitch Username Not Found",
                        description=f"The username `{username}` does not exist on Twitch.",
                        color=discord.Color.red()
                    )
                    error_embed.set_thumbnail(url=TWITCH_ICON_URL)
                    error_embed.set_footer(text="Twitch Registration Failed")

                    await reply(message_obj, embed=error_embed, ephemeral=True)
                    return

                user_data = data["data"][0]
                display_name = user_data.get("display_name", username)
                profile_image_url = user_data.get("profile_image_url")
                description = user_data.get("description", "No description.")
                created_at = user_data.get("created_at")

                created_fmt = "Unknown"
                if created_at:
                    created_fmt = datetime.strptime(created_at, "%Y-%m-%dT%H:%M:%SZ").strftime("%B %d, %Y")

                conn = sqlite3.connect(CONFIG_DB)
                cursor = conn.cursor()
                primaryID_row = cursor.execute("SELECT primaryID FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),)).fetchone()

                if not primaryID_row:
                    await reply(message_obj, "Guild is not registered in the database. Please run initial setup first.", ephemeral=True)
                    conn.close()
                    return

                primaryID = primaryID_row[0]

                guild_data = cursor.execute("SELECT registered FROM registeredUsers WHERE primaryID = ?;", (primaryID,)).fetchone()
                guild_data_json = guild_data[0] if guild_data else "{}"
                guild_data_dict = json.loads(guild_data_json)
                if not isinstance(guild_data_dict, list):
                    guild_data_dict = []
                if any(entry.get("username") == username for entry in guild_data_dict):
                    await reply(message_obj, f"The Twitch username `{username}` is already registered for this server.", ephemeral=True)
                    conn.close()
                    return
                guild_data_dict.append({
                    "username": username,
                    "display_name": display_name,
                    "profile_image_url": profile_image_url,
                    "created_at": created_fmt
                })
                cursor.execute("""
                    INSERT INTO registeredUsers (primaryID, registered)
                    VALUES (?, ?)
                    ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered;
                """, (primaryID, json.dumps(guild_data_dict)))
                conn.commit()
                conn.close()

                embed = discord.Embed(
                    title=f"{display_name} has been registered!",
                    description=description,
                    color=discord.Color.purple()
                )

                embed.add_field(name="Username", value=f"`{username}`", inline=True)
                embed.add_field(name="Account Created", value=created_fmt, inline=True)

                embed.set_footer(text="Twitch Registration Complete")

                async with aiohttp.ClientSession() as session:
                    async with session.get(profile_image_url) as resp:
                        if resp.status == 200:
                            data = await resp.read()
                            file = discord.File(io.BytesIO(data), filename="profile.jpg")
                            embed.set_thumbnail(url="attachment://profile.jpg")
                            await reply(message_obj, embed=embed, file=file)
                        else:
                            file = discord.File(io.BytesIO(TWITCH_ICON_URL), filename="profile.jpg")
                            embed.set_thumbnail(url="attachment://profile.jpg")
                            await reply(message_obj, embed=embed, file=file)

        except Exception as e:
            await reply(message_obj, f"An unexpected error occurred while registering Twitch username: `{str(e)}`\n{SUPPORT_MESSAGE}", ephemeral=True)

async def register_youtube(message_obj, channel_handle):
    message = wrap_message(message_obj)
    channel_handle = channel_handle.lower().lstrip('@')

    try:
        conn = sqlite3.connect(CONFIG_DB)
        cursor = conn.cursor()

        primaryID_row = cursor.execute("SELECT primaryID FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),)).fetchone()
        if not primaryID_row:
            await reply(message_obj, "Guild is not registered in the database. Please run initial setup first.", ephemeral=True)
            conn.close()
            return

        primaryID = primaryID_row[0]

        existing = cursor.execute("SELECT registered FROM registeredYoutubes WHERE primaryID = ?;", (primaryID,)).fetchone()
        existing_json = existing[0] if existing else "[]"
        user_list = json.loads(existing_json)

        if any(entry.get("handle") == channel_handle for entry in user_list):
            await reply(message_obj, f"The YouTube handle `@{channel_handle}` is already registered for this server.", ephemeral=True)
            conn.close()
            return

        user_list.append({
            "handle": ("@" + channel_handle),
            "registered_at": datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
        })

        cursor.execute("""
            INSERT INTO registeredYoutubes (primaryID, registered)
            VALUES (?, ?)
            ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered;
        """, (primaryID, json.dumps(user_list)))

        conn.commit()
        conn.close()

        embed = discord.Embed(
            title="YouTube Channel Registered",
            description=f"`@{channel_handle}` has been added to this server’s YouTube announcements.",
            color=discord.Color.red()
        )
        embed.set_thumbnail(url="https://cdn-icons-png.flaticon.com/512/1384/1384060.png")
        embed.set_footer(text="YouTube Registration Complete")
        await reply(message_obj, embed=embed, ephemeral=True)

    except Exception as e:
        await reply(message_obj, f"An error occurred while registering the YouTube channel: `{str(e)}`", ephemeral=True)

async def list_users(message_obj):
    message = wrap_message(message_obj)

    try:
        conn = sqlite3.connect(CONFIG_DB)
        cursor = conn.cursor()
        primaryID_row = cursor.execute("SELECT primaryID FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),)).fetchone()

        if not primaryID_row:
            await reply(message_obj, "Guild is not registered in the database. Please run initial setup first.", ephemeral=True)
            conn.close()
            return

        primaryID = primaryID_row[0]

        guild_data = cursor.execute("SELECT registered FROM registeredUsers WHERE primaryID = ?;", (primaryID,)).fetchone()
        guild_data_json = guild_data[0] if guild_data else "{}"
        conn.close()

        if guild_data_json == "{}":
            await reply(message_obj, "No users registered for this server.", ephemeral=True)
            return

        registered_users = json.loads(guild_data_json)

        embed = discord.Embed(
            title=f"Registered Twitch Users ({len(registered_users)})",
            description="Twitch users registered for this server:",
            color=discord.Color.purple()
        )

        for user in registered_users:
            display_name = user.get("display_name", "Unknown")
            username = user.get("username", "unknown")
            created = user.get("created_at", "Unknown")
            profile_url = user.get("profile_image_url", TWITCH_ICON_URL)

            embed.add_field(
                name=f"{display_name}",
                value=f"**Username**: `{username}`\n**Created**: {created}\n[Profile Image]({profile_url})",
                inline=False
            )

        embed.set_thumbnail(url=TWITCH_ICON_URL)
        embed.set_footer(text="Use /register to add more users.")
        await reply(message_obj, embed=embed, ephemeral=True)

    except Exception as e:
        await reply(message_obj, f"An unexpected error occurred while listing registered users: `{str(e)}`\n{SUPPORT_MESSAGE}", ephemeral=True)

async def list_youtube(message_obj):
    message = wrap_message(message_obj)

    try:
        conn = sqlite3.connect(CONFIG_DB)
        cursor = conn.cursor()

        primaryID_row = cursor.execute("SELECT primaryID FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),)).fetchone()
        if not primaryID_row:
            await reply(message_obj, "Guild is not registered in the database. Please run initial setup first.", ephemeral=True)
            conn.close()
            return

        primaryID = primaryID_row[0]

        data = cursor.execute("SELECT registered FROM registeredYoutubes WHERE primaryID = ?;", (primaryID,)).fetchone()
        conn.close()

        registered_json = data[0] if data else "[]"
        registered = json.loads(registered_json)

        if not registered:
            await reply(message_obj, "No YouTube channels registered for this server.", ephemeral=True)
            return

        embed = discord.Embed(
            title=f"Registered YouTube Channels ({len(registered)})",
            description="List of YouTube handles registered for this server:",
            color=discord.Color.red()
        )
        for user in registered:
            embed.add_field(
                name=f"{user.get('handle')}",
                value=f"Registered: {user.get('registered_at', 'Unknown')}",
                inline=False
            )

        embed.set_thumbnail(url="https://cdn-icons-png.flaticon.com/512/1384/1384060.png")
        embed.set_footer(text="Use /registeryoutube to add more channels.")
        await reply(message_obj, embed=embed, ephemeral=True)

    except Exception as e:
        await reply(message_obj, f"An error occurred while listing YouTube channels: `{str(e)}`", ephemeral=True)

async def unregister_user(message_obj, username):
    message = wrap_message(message_obj)
    username = username.lower()

    try:
        conn = sqlite3.connect(CONFIG_DB)
        cursor = conn.cursor()
        primaryID_row = cursor.execute("SELECT primaryID FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),)).fetchone()

        if not primaryID_row:
            await reply(message_obj, "Guild is not registered in the database. Please run initial setup first.", ephemeral=True)
            conn.close()
            return

        primaryID = primaryID_row[0]

        guild_data = cursor.execute("SELECT registered FROM registeredUsers WHERE primaryID = ?;", (primaryID,)).fetchone()
        guild_data_json = guild_data[0] if guild_data else "{}"
        guild_data_dict = json.loads(guild_data_json)

        if not isinstance(guild_data_dict, list):
            guild_data_dict = []

        new_list = [entry for entry in guild_data_dict if entry.get("username") != username]

        if len(new_list) == len(guild_data_dict):
            await reply(message_obj, f"No registered Twitch account found for `{username}`.", ephemeral=True)
            conn.close()
            return

        if new_list:
            cursor.execute("""
                INSERT INTO registeredUsers (primaryID, registered)
                VALUES (?, ?)
                ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered;
            """, (primaryID, json.dumps(new_list)))
        else:
            cursor.execute("DELETE FROM registeredUsers WHERE primaryID = ?;", (primaryID,))

        conn.commit()
        conn.close()

        embed = discord.Embed(
            title="Twitch User Unregistered",
            description=f"The user `{username}` has been removed from this server's registered Twitch accounts.",
            color=discord.Color.purple()
        )
        embed.set_thumbnail(url=TWITCH_ICON_URL)
        embed.set_footer(text="Twitch Unregistration Complete")
        await reply(message_obj, embed=embed, ephemeral=True)

    except Exception as e:
        await reply(message_obj, f"An unexpected error occurred while unregistering Twitch username: `{str(e)}`\n{SUPPORT_MESSAGE}", ephemeral=True)

async def unregister_youtube(message_obj, channel_handle):
    message = wrap_message(message_obj)
    if not channel_handle.startswith('@'):
        channel_handle = '@' + channel_handle
    channel_handle = channel_handle.lower()

    try:
        conn = sqlite3.connect(CONFIG_DB)
        cursor = conn.cursor()

        primaryID_row = cursor.execute("SELECT primaryID FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),)).fetchone()
        if not primaryID_row:
            await reply(message_obj, "Guild is not registered in the database. Please run initial setup first.", ephemeral=True)
            conn.close()
            return

        primaryID = primaryID_row[0]

        data = cursor.execute("SELECT registered FROM registeredYoutubes WHERE primaryID = ?;", (primaryID,)).fetchone()
        existing_json = data[0] if data else "[]"
        users = json.loads(existing_json)

        new_list = [u for u in users if u.get("handle", "").lower() != channel_handle]

        if len(new_list) == len(users):
            await reply(message_obj, f"No registered YouTube handle found for `{channel_handle}`.", ephemeral=True)
            conn.close()
            return

        if new_list:
            cursor.execute("""
                INSERT INTO registeredYoutubes (primaryID, registered)
                VALUES (?, ?)
                ON CONFLICT(primaryID) DO UPDATE SET registered = excluded.registered;
            """, (primaryID, json.dumps(new_list)))
        else:
            cursor.execute("DELETE FROM registeredYoutubes WHERE primaryID = ?;", (primaryID,))

        conn.commit()
        conn.close()

        embed = discord.Embed(
            title="YouTube Channel Unregistered",
            description=f"`{channel_handle}` has been removed from this server's announcements.",
            color=discord.Color.red()
        )
        embed.set_thumbnail(url="https://cdn-icons-png.flaticon.com/512/1384/1384060.png")
        embed.set_footer(text="YouTube Unregistration Complete")
        await reply(message_obj, embed=embed, ephemeral=True)

    except Exception as e:
        await reply(message_obj, f"An error occurred while unregistering the YouTube channel: `{str(e)}`", ephemeral=True)
async def set_announcement_channel(message_obj, platform):
    message = wrap_message(message_obj)

    if not message.author.guild_permissions.manage_channels:
        await reply(message_obj, "You need the Manage Channels permission to set the announcement channel.", ephemeral=True)
        return

    channel = message.channel
    if not isinstance(channel, discord.TextChannel):
        await reply(message_obj, "This command can only be used in a text channel.", ephemeral=True)
        return

    try:
        conn = sqlite3.connect(CONFIG_DB)
        cursor = conn.cursor()

        cursor.execute("SELECT primaryID FROM guildInfo WHERE guildID = ?;", (str(message.guild.id),))
        primaryID_row = cursor.fetchone()

        if not primaryID_row:
            await reply(message_obj, "Guild is not registered in the database. Please run initial setup first.", ephemeral=True)
            conn.close()
            return

        primaryID = primaryID_row[0]

        if platform not in ("twitch", "youtube"):
            await reply(message_obj, f"Invalid platform: `{platform}`. Must be either `twitch` or `youtube`.", ephemeral=True)
            conn.close()
            return

        cursor.execute("SELECT 1 FROM announcementChannels WHERE primaryID = ?;", (primaryID,))
        exists = cursor.fetchone()

        if exists:
            cursor.execute(f"UPDATE announcementChannels SET {platform} = ? WHERE primaryID = ?;", (str(channel.id), primaryID))
        else:
            twitch_id = str(channel.id) if platform == "twitch" else None
            youtube_id = str(channel.id) if platform == "youtube" else None
            cursor.execute("INSERT INTO announcementChannels (primaryID, twitch, youtube) VALUES (?, ?, ?);", (primaryID, twitch_id, youtube_id))

        conn.commit()
        conn.close()

        await reply(message_obj, f"Announcement channel for {platform} set to {channel.mention}.", ephemeral=True)

    except Exception as e:
        await reply(message_obj, f"An error occurred while setting the channel: `{str(e)}`", ephemeral=True)

async def debug(message_obj, client, start_time):
    message = wrap_message(message_obj)

    now = time.time()
    uptime_seconds = int(now - start_time)
    uptime_str = str(datetime.timedelta(seconds=uptime_seconds))

    process = psutil.Process(os.getpid())
    mem_usage_mb = process.memory_info().rss / 1024 / 1024
    cpu_percent = psutil.cpu_percent(interval=0.5)
    platform_info = f"{platform.system()} {platform.release()}"
    python_version = platform.python_version()
    discord_version = discord.__version__
    environment = f"{platform_info} | Python {python_version} | discord.py {discord_version}"

    latency = round(client.latency * 1000)
    command_count = 12

    guild = message.guild
    guild_id = guild.id
    locale = getattr(guild, 'preferred_locale', "Unknown")
    shard_count = 1
    cache_members = len(guild.members)
    cache_channels = len(guild.channels)
    cache_size = f"{cache_members} members | {cache_channels} channels"

    permissions = guild.me.guild_permissions
    top_perms = []
    if permissions.administrator:
        top_perms.append("ADMIN")
    if permissions.manage_messages:
        top_perms.append("MANAGE_MESSAGES")
    if permissions.manage_channels:
        top_perms.append("MANAGE_CHANNELS")
    perms_string = ", ".join(top_perms) if top_perms else "Normal"

    try:
        conn = sqlite3.connect(CONFIG_DB)
        cursor = conn.cursor()
        cursor.execute("SELECT prefix FROM guildInfo WHERE guildID = ?;", (str(guild.id),))
        result = cursor.fetchone()
        prefix = result[0] if result else "/"
        last_write = datetime.datetime.fromtimestamp(os.path.getmtime(CONFIG_DB)).strftime('%Y-%m-%d %H:%M:%S UTC')
        db_status = "Connected"
    except Exception as e:
        prefix = "?"
        last_write = "N/A"
        db_status = f"Error ({str(e)})"
    finally:
        conn.close()

    fun_facts = [
        "‘Ping’ is named after sonar, not the game.",
        "Discord.py was originally created by Rapptz.",
        "The first computer bug was an actual moth.",
        "Python's name comes from Monty Python, not the snake.",
        "The ‘Uptime’ metric is older than Unix.",
        '"Hello, world!" was first used in Brian Kernighan’s 1972 tutorial for the B programming language.',
        "'null' in programming is often called 'The Billion Dollar Mistake'.",
        'The Unicode snowman character is called "☃" and it exists just to be cute.',
        "COBOL still runs 95% of ATM transactions.",
        "Git was created by Linus Torvalds in just 10 days.",
        "The word 'robot' comes from a 1920 play and means 'forced labor'.",
        "Over 90% of the world's currency exists only in digital form.",
        "The first domain ever registered was 'symbolics.com' in 1985.",
        "The term 'debugging' predates computers — Thomas Edison used it in 1878.",
        "Nintendo was founded in 1889 — as a playing card company.",
        "There are more transistors in a modern smartphone than stars in the Milky Way.",
        "Emoji is a Japanese word that predates the iPhone.",
        "SpaceX uses C++ and Python to write rocket software.",
        "The first 1GB hard drive cost $40,000 in 1980 and weighed 500 pounds.",
        "Ctrl+Alt+Del was never meant for users — it was a shortcut for developers."
    ]

    embed = discord.Embed(title="Debug Report", color=discord.Color.orange())
    embed.add_field(name="Bot Uptime", value=uptime_str)
    embed.add_field(name="Active Commands Loaded", value=str(command_count))
    embed.add_field(name="Guild ID", value=str(guild_id))
    embed.add_field(name="Database Status", value=db_status)
    embed.add_field(name="Last Database Write", value=last_write)
    embed.add_field(name="Memory Usage", value=f"{mem_usage_mb:.1f} MB")
    embed.add_field(name="Latency", value=f"{latency}ms")
    embed.add_field(name="Running On", value=environment)
    embed.add_field(name="Prefix", value=prefix)
    embed.add_field(name="Guild Locale", value=locale)
    embed.add_field(name="Command Cooldowns", value="None active")
    embed.add_field(name="Cache Size", value=cache_size)
    embed.add_field(name="Shards", value=str(shard_count))
    embed.add_field(name="Permissions", value=perms_string)
    embed.add_field(name="Environment", value=platform_info)
    embed.add_field(name="CPU Load", value=f"{cpu_percent:.1f}%")
    embed.add_field(name="Fun Fact", value=random.choice(fun_facts))
    embed.set_footer(text=f"Requested by {message.author}", icon_url=message.author.display_avatar.url)

    await reply(message_obj, embed=embed)
