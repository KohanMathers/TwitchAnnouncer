package me.kmathers.twitchannouncer.commands;

import me.kmathers.twitchannouncer.TwitchAnnouncer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SetChannelCommand {
    private final TwitchAnnouncer bot;

    public SetChannelCommand(TwitchAnnouncer bot) {
        this.bot = bot;
    }

    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You need the Manage Channels permission to set the announcement channel.").setEphemeral(true).queue();
            return;
        }

        if (!event.getChannelType().equals(ChannelType.TEXT)) {
            event.reply("This command can only be used in a text channel.").setEphemeral(true).queue();
            return;
        }

        String platform = event.getOption("platform").getAsString().toLowerCase();
        executeSetChannel(platform, event.getChannel().getId(), event.getGuild().getId(),
            message -> event.reply(message).setEphemeral(true).queue(),
            error -> event.reply(error).setEphemeral(true).queue());
    }

    public void execute(MessageReceivedEvent event, String platform) {
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.getMessage().reply("You need the Manage Channels permission to set the announcement channel.").queue();
            return;
        }

        if (!event.getChannel().getType().equals(ChannelType.TEXT)) {
            event.getMessage().reply("This command can only be used in a text channel.").queue();
            return;
        }

        platform = platform.toLowerCase();
        executeSetChannel(platform, event.getChannel().getId(), event.getGuild().getId(),
            message -> event.getMessage().reply(message).queue(),
            error -> event.getMessage().reply(error).queue());
    }

    private void executeSetChannel(String platform, String channelId, String guildId, SuccessCallback success, ErrorCallback error) {
        try {
            if (!platform.equals("twitch") && !platform.equals("youtube")) {
                error.onError("Invalid platform: `" + platform + "`. Must be either `twitch` or `youtube`.");
                return;
            }

            String primaryId = bot.getDatabase().getPrimaryId(guildId);
            if (primaryId == null) {
                error.onError("Guild is not registered in the database. Please run initial setup first.");
                return;
            }

            bot.getDatabase().setAnnouncementChannel(primaryId, platform, channelId);
            success.onSuccess("Announcement channel for " + platform + " set to <#" + channelId + ">.");
        } catch (Exception e) {
            error.onError("An error occurred while setting the channel: `" + e.getMessage() + "`");
        }
    }

    @FunctionalInterface
    interface SuccessCallback {
        void onSuccess(String message);
    }

    @FunctionalInterface
    interface ErrorCallback {
        void onError(String message);
    }
}
