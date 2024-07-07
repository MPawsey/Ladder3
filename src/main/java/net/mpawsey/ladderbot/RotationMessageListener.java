package net.mpawsey.ladderbot;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.mpawsey.bothelper.AbstractMessageListener;
import net.mpawsey.bothelper.FileManager;
import net.mpawsey.bothelper.MessageHelper;

public class RotationMessageListener extends AbstractMessageListener
{
	private final int LADDER_PERM_BIT = 4;
	public static final String TEAM1 = "U+31U+fe0fU+20e3";
	public static final String TEAM2 = "U+32U+fe0fU+20e3";
	public static final String CANCEL = "U+1f6ab";
	private final long RAZ_UP = 656900685308362802l;
	private final long RAZ_DOWN = 656900927558778880l;
	
	private Timer timerFromStart, timerFromValid;
	private Rotation rotation;
	private int team1 = 0, team2 = 0, cancel = 0;
	
	private Message adminMessage;
	private RotationMessageListener next = null;
	
	public RotationMessageListener(Rotation rotation)
	{
		super(rotation.getResultMessage());
		
		this.rotation = rotation;
		rotation.getResultMessage().addReaction(TEAM1).queue();
		rotation.getResultMessage().addReaction(TEAM2).queue();
		rotation.getResultMessage().addReaction(CANCEL).queue();
		
		timerFromStart = new Timer();
		timerFromStart.schedule(new TimerTask() {
			@Override
			public void run()
			{
				cancel();
			}
		}, 1 * 5 * 60 * 1000); // 12 hrs
	}
	
	public void setNext(RotationMessageListener next)
	{
		this.next = next;
	}
	
	public void cancel()
	{
		if (next != null)
			next.cancel();
		
		if (timerFromStart != null)
			timerFromStart.cancel();
		timerFromStart = null;
		
		if (timerFromValid != null)
			timerFromValid.cancel();
		timerFromValid = null;
		
		rotation.getResultMessage().delete().queue();
		
		stopListening();
						
		if (adminMessage != null)
			adminMessage.delete().queue();
	}
	
	public void setComplete()
	{
		if (timerFromStart != null)
			timerFromStart.cancel();
		timerFromStart = null;
		if (timerFromValid != null)
			timerFromValid.cancel();
		timerFromValid = null;
		
		List<Long> winners, losers;
		if (team1 > team2)
		{
			winners = rotation.getTeam1();
			losers = rotation.getTeam2();
		}
		else
		{
			winners = rotation.getTeam2();
			losers = rotation.getTeam1();
		}
		
		Result result = new Result(rotation.getResultMessage(), winners, losers, 1, 0);
		
		EmbedBuilder eb = new EmbedBuilder(result.getResultMessage().getEmbeds().get(result.getResultMessage().getEmbeds().size()-1));
		eb.clearFields();

		eb.setTitle("Ladder Update - Series #" + (LadderBot.ladder.getSeriesCount()+1));
		
		eb.addField("Winners (1)", winners.stream().map(id -> (String)MessageHelper.getNameFromId(id, LadderBot.ladder.guild, true)).collect(Collectors.joining("\n")), false);
		eb.addField("Losers (0)", losers.stream().map(id -> (String)MessageHelper.getNameFromId(id, LadderBot.ladder.guild, true)).collect(Collectors.joining("\n")), false);
		eb.addField("", "This result will take affect once the results before have been processed.", false);
		result.getResultMessage().editMessage(eb.build()).complete();
		
		result.setValid(true);
		
		LadderBot.ladder.addResult(result);
		
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
		if (timerFromStart != null)
			timerFromStart.cancel();
		timerFromStart = null;
		if (timerFromValid != null)
			timerFromValid.cancel();
		timerFromValid = null;
		
		
		stopListening();
	}
	
	@Override
	public void onReactionAdded(MessageReactionAddEvent event) 
	{
		if (rotation.getTeam1().contains(event.getUser().getIdLong()) || rotation.getTeam2().contains(event.getUserIdLong()))
		{
			if (event.getReactionEmote().isEmoji())
			{
				String emote = event.getReactionEmote().getAsCodepoints();
				if (emote.equals(TEAM1))
				{
					System.out.println("Here: " + team1);
					if (++team1 == rotation.getTeam1().size() + rotation.getTeam2().size() && team2 < 2&& cancel < 2)
					{
						setComplete();
						return;
					}
					
					if (team1 == (rotation.getTeam1().size() + rotation.getTeam2().size()) / 2 + 1 && team2 < 2&& cancel < 2)
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
				else if (emote.equals(TEAM2))
				{
					if (++team2 == rotation.getTeam1().size() + rotation.getTeam2().size() && team1 < 2 && cancel < 2)
					{
						setComplete();
						return;
					}
					
					if (team2 == (rotation.getTeam1().size() + rotation.getTeam2().size()) / 2 + 1 && team1 < 2&& cancel < 2)
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
				else if (emote.equals(CANCEL))
				{
					if (++cancel == rotation.getTeam1().size() + rotation.getTeam2().size() && team1 < 2 && team2 < 2)
					{
						cancel();
						return;
					}
					
					if (cancel == (rotation.getTeam1().size() + rotation.getTeam2().size()) / 2 + 1 && team1 < 2 && team2 < 2)
					{
						if (timerFromStart != null)
							timerFromStart.cancel();
						timerFromStart = null;
						
						timerFromValid = new Timer();
						timerFromValid.schedule(new TimerTask() {
							@Override
							public void run()
							{
								cancel();
							}
						}, 60 * 1000);
					}
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
					cancel();
			}
		}
	}
	
	@Override
	public void onReactionRemoved(MessageReactionRemoveEvent event)
	{
		if (rotation.getTeam1().contains(event.getUser().getIdLong()) || rotation.getTeam2().contains(event.getUserIdLong()))
		{
			if (event.getReactionEmote().isEmoji())
			{
				String emote = event.getReactionEmote().getAsCodepoints();
				if (emote.equals(TEAM1))
				{
					if (--team1 == (rotation.getTeam1().size() + rotation.getTeam2().size()) / 2)
					{
						if (timerFromValid != null)
							timerFromValid.cancel();
						timerFromValid = null;
						
						timerFromStart.cancel();
						timerFromStart = new Timer();
						timerFromStart.schedule(new TimerTask() {
							@Override
							public void run()
							{
								cancel();
							}
						}, 12 * 60 * 60 * 1000); // 12 hrs
					}
				}
				else if (emote.equals(TEAM2))
				{
					if (--team2 == (rotation.getTeam1().size() + rotation.getTeam2().size()) / 2)
					{
						if (timerFromValid != null)
							timerFromValid.cancel();
						timerFromValid = null;
						
						timerFromStart.cancel();
						timerFromStart = new Timer();
						timerFromStart.schedule(new TimerTask() {
							@Override
							public void run()
							{
								cancel();
							}
						}, 12 * 60 * 60 * 1000); // 12 hrs
					}
				}
				else if (emote.equals(CANCEL))
				{
					if (--cancel == (rotation.getTeam1().size() + rotation.getTeam2().size()) / 2)
					{
						if (timerFromValid != null)
							timerFromValid.cancel();
						timerFromValid = null;
						
						if (team1 > (rotation.getTeam1().size() + rotation.getTeam2().size() / 2) + 1 || team2 > (rotation.getTeam1().size() + rotation.getTeam2().size() / 2) + 1)
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
						
						timerFromStart.cancel();
						timerFromStart = new Timer();
						timerFromStart.schedule(new TimerTask() {
							@Override
							public void run()
							{
								cancel();
							}
						}, 12 * 60 * 60 * 1000); // 12 hrs
					}
				}
			}
		}
	}

}
