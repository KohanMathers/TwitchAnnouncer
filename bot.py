import discord
import logging
import time
import json
import aiohttp
import re
import asyncio
import urllib.parse
from discord.ext import commands, tasks

# Configuration
log_level = logging.INFO
log_dir = "./logs"
token_file = "./token.txt"
twitch_file = "./twitch.txt"
settings_file = "./settings.json"
stream_file = "./announced_streams.json"
announced_streams = {}
ALLOWED_GUILD_ID = 1227640355625766963
allowed_to_call_api = False

# Set up logging
logging.basicConfig(level=log_level, format="%(asctime)s - %(levelname)s - %(message)s")

# Load Bot Token
try:
    with open(token_file) as f:
        token = f.read().strip()
except FileNotFoundError:
    logging.error(f"Token file '{token_file}' not found!")
    raise

# Load Twitch credentials
credentials = {}
try:
    with open(twitch_file) as f:
        for line in f:
            key, value = line.strip().split("=")
            credentials[key] = value
except FileNotFoundError:
    logging.error(f"Twitch file '{twitch_file}' not found!")
    raise

# Load previous announced streams
try:
    with open(stream_file, "r") as f:
        announced_streams = json.load(f)
except FileNotFoundError:
    logging.warning(f"Announced streams file '{stream_file}' not found. Starting fresh.")
    announced_streams = {}
    with open(stream_file, "w") as f:
        json.dump({}, f)

# Load settings
try:
    with open(settings_file, "r") as f:
        settings = json.load(f)
except FileNotFoundError:
    logging.warning(f"Settings file '{settings_file}' not found. Creating a new one.")
    settings = {}
    with open(settings_file, "w") as f:
        json.dump(settings, f)

# Discord bot setup
intents = discord.Intents.default()
intents.messages = True
intents.message_content = True
intents.guilds = True
bot = commands.Bot(command_prefix="$", intents=intents)
start_time = time.time()

@bot.event
async def on_ready():
    try:
        await bot.tree.sync()
        logging.info(f"Registered commands: {await bot.tree.fetch_commands()}")
    except Exception as e:
        logging.error(f"Error registering commands: {e}")
    logging.info(f"Bot connected as {bot.user.name}")
    check_live_streams.start()
    refresh_twitch_token.start()

@bot.tree.command(name="setchannel", description="Set the notification channel.")
async def setchannel_command(interaction: discord.Interaction, role: discord.Role = None):
    if not interaction.user.guild_permissions.manage_channels:
        await interaction.response.send_message("You do not have permission to manage channels.", ephemeral=True)
        return

    guild_id = str(interaction.guild.id)
    settings.setdefault(guild_id, {"registered_users": []})
    settings[guild_id]["ping_channel_id"] = interaction.channel.id

    if role:
        settings[guild_id]["ping_role_id"] = role.id
        await interaction.response.send_message(
            f"Ping channel set to {interaction.channel.mention} and will notify the role {role.mention}."
        )
    else:
        settings[guild_id].pop("ping_role_id", None)  # Remove if no role is provided
        await interaction.response.send_message(f"Ping channel set to {interaction.channel.mention}. No role will be notified.")

    with open(settings_file, "w") as f:
        json.dump(settings, f)

@bot.tree.command(name="register", description="Register a Twitch account.")
async def register_command(interaction: discord.Interaction, username: str):
    settings.setdefault(str(interaction.guild.id), {"registered_users": []})
    guild_settings = settings[str(interaction.guild.id)]
    if username not in guild_settings["registered_users"]:
        guild_settings["registered_users"].append(username)
        with open(settings_file, "w") as f:
            json.dump(settings, f)
        await interaction.response.send_message(f"Registered Twitch account: {username}.")
    else:
        await interaction.response.send_message(f"{username} is already registered.")

@bot.tree.command(name="unregister", description="Unregister a Twitch account.")
async def unregister_command(interaction: discord.Interaction, username: str):
    guild_settings = settings.setdefault(str(interaction.guild.id), {"registered_users": []})
    if username in guild_settings["registered_users"]:
        guild_settings["registered_users"].remove(username)
        with open(settings_file, "w") as f:
            json.dump(settings, f)
        await interaction.response.send_message(f"Unregistered Twitch account: {username}.")
    else:
        await interaction.response.send_message(f"{username} is not registered.")

@bot.tree.command(name="listaccounts", description="Show registered Twitch accounts.")
async def listaccounts_command(interaction: discord.Interaction):
    registered_users = settings.get(str(interaction.guild.id), {}).get("registered_users", [])
    response = "\n".join(registered_users) if registered_users else "No registered accounts."
    await interaction.response.send_message(f"**Registered Twitch Accounts:**\n`{response}`")

@bot.tree.command(name="info", description="Display bot information.")
async def info_command(interaction: discord.Interaction):
    uptime = int(time.time() - start_time)

    embed = discord.Embed(
        title="Bot Information",
        description="Details about the bot:",
        color=discord.Color.blue(),
    )
    embed.add_field(name="Bot Name", value=bot.user.name, inline=False)
    embed.add_field(
        name="Uptime", value=f"{uptime // 3600}h {(uptime % 3600) // 60}m", inline=False
    )
    embed.add_field(
        name="Developers",
        value=f"[KohanMathers](https://github.com/kohanmathers)",
        inline=False,
    )
    embed.add_field(
        name="Source Code",
        value="[GitHub Repo](https://github.com/kohanmathers/twitchannouncer)",
        inline=False,
    )
    embed.add_field(
        name="Total Users",
        value=len(bot.guilds),
        inline=False,
    )
    embed.set_footer(text="Use /help for available commands.")

    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="help", description="Shows a list of available commands.")
async def info_command(interaction: discord.Interaction):
    embed = discord.Embed(
        title="Commands List",
        description="Available commands:",
        color=discord.Color.blue(),
    )
    embed.add_field(
        name="/setchannel <OPTIONAL: role>",
        value="Sets the announcements channel and registeres the specified roles for announcement pings if specified (requires the Manage Channels permission).",
        inline=False
    )
    embed.add_field(
        name="/register <username>",
        value="Registers a twitch username for announcements.",
        inline=False,
    )
    embed.add_field(
        name="/unregister <username>",
        value="Unregisters a twitch username from announcements.",
        inline=False,
    )
    embed.add_field(
        name="/listaccounts",
        value="Lists all accounts registered in this guild.",
        inline=False,
    )
    embed.add_field(
        name="/info",
        value="Displays information about the bot.",
        inline=False,
    )
    embed.add_field(
        name="/help",
        value="Shows this message.",
        inline=False,
    )
    embed.set_footer(text="Use /help for available commands.")

    await interaction.response.send_message(embed=embed)

@tasks.loop(minutes=1)
async def check_live_streams():
    global allowed_to_call_api
    headers = {"Client-ID": credentials.get("CLIENT_ID"), "Authorization": f"Bearer {credentials.get('ACCESS_TOKEN')}"}
    
    for guild_id, guild_settings in settings.items():
        ping_channel_id = guild_settings.get("ping_channel_id")
        ping_role_id = guild_settings.get("ping_role_id")
        registered_users = guild_settings.get("registered_users", [])

        if not ping_channel_id or not registered_users:
            continue

        ping_channel = bot.get_channel(ping_channel_id)
        if not ping_channel:
            continue

        batch_size = 100
        if allowed_to_call_api:
            for i in range(0, len(registered_users), batch_size):
                batch = registered_users[i:i+batch_size]
                query_params = "&".join(f"user_login={urllib.parse.quote_plus(user)}" for user in batch)
                url = f"https://api.twitch.tv/helix/streams?{query_params}&_={int(time.time())}"

                async with aiohttp.ClientSession() as session:
                    async with session.get(url, headers=headers) as response:
                        if response.status != 200:
                            logging.error(await response.text())
                            continue
                        data = await response.json()

                        for stream in data.get("data", []):
                            stream_id = stream.get("id")
                            if stream_id not in announced_streams.get(guild_id, []):
                                username = stream.get("user_name")
                                game_name = stream.get("game_name", "Unknown Game")
                                title = stream.get("title", "No Title")
                                username_lower = username.lower()

                                embed = discord.Embed(
                                    title=f"🔴 {username} is live!",
                                    description=f"**{title}**\nNow playing: {game_name}\n[Watch here](https://twitch.tv/{username})"
                                )
                                embed.set_image(
                                    url=f"https://static-cdn.jtvnw.net/previews-ttv/live_user_{username_lower}-1920x1080.jpg"
                                )

                                mention = f"<@&{ping_role_id}> " if ping_role_id else ""
                                await ping_channel.send(content=mention, embed=embed)

                                announced_streams.setdefault(guild_id, []).append(stream_id)

        await asyncio.sleep(1)

    with open(stream_file, "w") as f:
        json.dump(announced_streams, f)

@tasks.loop(hours=24)
async def refresh_twitch_token():
    """Refresh the Twitch access token using the refresh token"""
    url = "https://id.twitch.tv/oauth2/token"

    global allowed_to_call_api
    allowed_to_call_api = False

    data = {
        "grant_type": "refresh_token",
        "refresh_token": credentials.get("REFRESH_TOKEN"),
        "client_id": credentials.get("CLIENT_ID"),
        "client_secret": credentials.get("CLIENT_SECRET"),
    }

    logging.info(f"Attempting to refresh Twitch token.")

    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(url, data=data) as response:
                if response.status == 200:
                    result = await response.json()
                    new_access_token = result.get("access_token")
                    new_refresh_token = result.get("refresh_token")

                    if new_access_token and new_refresh_token:
                        # Update the credentials
                        credentials["ACCESS_TOKEN"] = new_access_token
                        credentials["REFRESH_TOKEN"] = new_refresh_token

                        # Save the new tokens in the twitch.txt file
                        with open(twitch_file, "w") as f:
                            for key, value in credentials.items():
                                f.write(f"{key}={value}\n")
                        logging.info(
                            "Successfully refreshed Twitch access token and refresh token."
                        )
                        allowed_to_call_api = True
                    else:
                        logging.error(
                            "Failed to retrieve new tokens. Response: {}".format(result)
                        )
                else:
                    logging.error(
                        f"Failed to refresh token. Status code: {response.status}, Response: {await response.text()}"
                    )
    except Exception as e:
        logging.error(f"An error occurred while refreshing Twitch token: {e}")

bot.run(token)
