package net.mpawsey.bothelper;

import java.util.Timer;
import java.util.TimerTask;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

public class MessageHelper 
{
	public static void sendSuccess(String success, MessageChannel channel)
	{
		channel.sendMessage("> Success: " + success).queue();
	}
	
	public static void sendSuccess(String success, MessageChannel channel, long seconds)
	{
		sendTemporaryMessage("> Success: " + success, channel, seconds);
	}
	
	public static void sendError(String error, MessageChannel channel)
	{
		channel.sendMessage("> **ERROR**: " + error).queue();
	}
	
	public static void sendError(String error, MessageChannel channel, long seconds)
	{
		sendTemporaryMessage("> **ERROR**: " + error, channel, seconds);
	}
	
	public static Message sendMessage(String text, MessageChannel channel)
	{
		return channel.sendMessage(text).complete();
	}
	
	public static Message sendTemporaryMessage(String text, MessageChannel channel, long seconds)
	{
		Message msg = sendMessage(text, channel);
		deleteMessageAfter(msg, seconds);
		return msg;
	}
	
	public static void deleteMessageAfter(Message msg, long seconds)
	{
		new Timer().schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				msg.delete().queue();
			}
		}, (seconds * 1000));
	}
	
	public static Long extractId(String user)
	{
		if (user.startsWith("<@!"))
			return Long.parseLong(user.substring(3, user.length() - 1));
		else
			return Long.parseLong(user.substring(2, user.length() - 1));
	}
	
	// Following format FIRST "nick" L
	// "nick" ignored
	public static String getNameFromId(long id, Guild guild, boolean useFormat)
	{
		String nickname = guild.retrieveMemberById(id).complete().getEffectiveName();
		if (useFormat)
			return nickname.replaceAll(" \".*\" ", " ");
		return nickname;
	}
}
