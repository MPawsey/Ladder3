package net.mpawsey.ladderbot;

import java.util.Timer;
import java.util.TimerTask;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.mpawsey.bothelper.AbstractMessageListener;
import net.mpawsey.bothelper.FileManager;
import net.mpawsey.bothelper.MessageHelper;

public class ResultMessageListener extends AbstractMessageListener
{

	private final int LADDER_PERM_BIT = 4;
	private final String THUMBS_UP = "U+1f44d";
	private final String THUMBS_DOWN = "U+1f44e";
	private final long RAZ_UP = 656900685308362802l;
	private final long RAZ_DOWN = 656900927558778880l;
	
	private Timer timerFromStart, timerFromValid;
	private Result result;
	private int successes = 0, failures = 0;
	
	private Message adminMessage;
	
	public ResultMessageListener(Result result)
	{
		super(result.getResultMessage());
		
		this.result = result;
		result.getResultMessage().addReaction(THUMBS_UP).queue();
		result.getResultMessage().addReaction(THUMBS_DOWN).queue();
		
		timerFromStart = new Timer();
		timerFromStart.schedule(new TimerTask() {
			@Override
			public void run()
			{
				setComplete();
			}
		}, 1 * 5 * 60 * 1000); // 12 hrs
	}
	
	void setComplete()
	{
		
		if (timerFromStart != null)
			timerFromStart.cancel();
		timerFromStart = null;
		
		if (timerFromValid != null)
			timerFromValid.cancel();
		timerFromValid = null;
		
		result.setValid(true);
		
		EmbedBuilder eb = new EmbedBuilder(result.getResultMessage().getEmbeds().get(0));
		eb.clearFields();
		eb.addField(result.getResultMessage().getEmbeds().get(0).getFields().get(0));
		eb.addField(result.getResultMessage().getEmbeds().get(0).getFields().get(1));
		eb.addField("", "This result will take affect once the results before have been processed.", false);
		result.getResultMessage().editMessage(eb.build()).queue();

		
		if (adminMessage != null)
		{
			adminMessage.delete().complete();
			adminMessage = null;
		}
		
		stopListening();
		
		LadderBot.ladder.pollResults(false);
	}
	
	@Override
	public void onMessageDeleted(MessageDeleteEvent event) 
	{
		LadderBot.ladder.removeResult(result);
		stopListening();
	}

	@Override
	public void onReactionAdded(MessageReactionAddEvent event) 
	{
		if (result.getWinners().contains(event.getUser().getIdLong()) || result.getLosers().contains(event.getUserIdLong()))
		{
			if (event.getReactionEmote().isEmoji())
			{
				String emote = event.getReactionEmote().getAsCodepoints();
				if (emote.equals(THUMBS_UP))
				{
					if (++successes == result.getWinners().size() + result.getLosers().size() && failures == 0)
						setComplete();
					
					if (successes == (result.getWinners().size() + result.getLosers().size()) / 2 + 1 && failures == 0)
					{
						if (timerFromStart != null)
							timerFromStart.cancel();
						timerFromStart = null;
						
						timerFromValid = new Timer();
						timerFromValid.schedule(new TimerTask() {
							@Override
							public void run()
							{
								setComplete();
							}
						}, 60 * 1000);
					}
				}
				else if (emote.equals(THUMBS_DOWN))
				{
					if (timerFromStart != null)
						timerFromStart.cancel();
					timerFromStart = null;
					
					if (timerFromValid != null)
						timerFromValid.cancel();
					timerFromValid = null;
					
					// THUMBS DOWN
					if (++failures > (result.getWinners().size() + result.getLosers().size()))
					{
						LadderBot.ladder.removeResult(result);
						result.getResultMessage().delete().queue();
						
						stopListening();
						
						if (adminMessage != null)
							adminMessage.delete().queue();
					}
					else if (failures == 1 && LadderBot.ladder.adminChannel != null)
						adminMessage = MessageHelper.sendMessage("Ladder dispute at: <" + result.getResultMessage().getJumpUrl() + ">", LadderBot.ladder.adminChannel);
				}
			}
		}
		
		
		if (FileManager.hasPermission(LADDER_PERM_BIT, event.getUserId()))
		{
			if (event.getReactionEmote().isEmote())
			{
				Long emote = event.getReactionEmote().getEmote().getIdLong();
				
				if (emote == RAZ_UP)
					setComplete();
				else if (emote == RAZ_DOWN)
				{
					// Raz down
					LadderBot.ladder.removeResult(result);
	
					if (timerFromStart != null)
						timerFromStart.cancel();
					timerFromStart = null;
					
					if (timerFromValid != null)
						timerFromValid.cancel();
					timerFromValid = null;
					
					if (adminMessage != null)
					{
						adminMessage.delete().complete();
						adminMessage = null;
					}
					
					stopListening();
					result.getResultMessage().delete().queue();
				}
			}
		}
	}
	
	@Override
	public void onReactionRemoved(MessageReactionRemoveEvent event) 
	{
		if (result.getWinners().contains(event.getUserIdLong()) || result.getLosers().contains(event.getUserIdLong()))
		{
			if (event.getReactionEmote().isEmoji())
			{
				String emote = event.getReactionEmote().getAsCodepoints();
				if (emote.equals(THUMBS_UP))
				{
					if (--successes == (result.getWinners().size() + result.getLosers().size()) / 2)
					{						
						if (timerFromValid != null)
							timerFromValid.cancel();
						timerFromValid = null;
						
						if (failures == 0)
						{
							timerFromStart = new Timer();
							timerFromStart.schedule(new TimerTask() {
								@Override
								public void run()
								{
									setComplete();
								}
							}, 12 * 60 * 60 * 1000); // 12 hrs
						}
					}
				}
				else if (emote.equals(THUMBS_DOWN))
				{
					if (--failures == 0)
					{
						if (adminMessage != null)
						{
							adminMessage.delete().complete();
							adminMessage = null;
						}
						
						if (successes == (result.getWinners().size() + result.getLosers().size()) / 2 + 1)
						{
							timerFromValid = new Timer();
							timerFromValid.schedule(new TimerTask() {
								@Override
								public void run()
								{
									setComplete();
								}
							}, 60 * 1000);
						}
						else if (successes == (result.getWinners().size() + result.getLosers().size()))
							setComplete();
						else
						{
							timerFromStart = new Timer();
							timerFromStart.schedule(new TimerTask() {
								@Override
								public void run()
								{
									setComplete();
								}
							}, 12 * 60 * 60 * 1000); // 12 hrs
						}
					}
				}
			}
		}
	}
}
