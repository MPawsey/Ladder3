package net.mpawsey.ladderbot;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.mpawsey.bothelper.AbstractMessageHandler;
import net.mpawsey.bothelper.CmdParam;
import net.mpawsey.bothelper.CmdParam.Type;
import net.mpawsey.bothelper.CommandDescription;
import net.mpawsey.bothelper.Command;
import net.mpawsey.bothelper.CommandRequires;
import net.mpawsey.bothelper.MessageHelper;
import net.mpawsey.bothelper.OnMessageReceived;

public class LadderMessageHandler extends AbstractMessageHandler
{
	
	@Command("terminate")
	@CommandRequires(LadderBot.TERMINATE_PERM)
	@CommandDescription(description = "Terminates the bot", usage = "terminate [force|save]")
	public void terminateCmd(@CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.ARGS_RAW) String args)
	{
		if (args.equals("save"))
		{
			if (!LadderBot.ladder.savePending())
			{
				MessageHelper.sendMessage("Unable to save the pending results, please resolve them or type `terminate force` to forcibly terminate ladder.", channel);
				return;
			}
			MessageHelper.sendMessage("Saved.", channel);
				
		}
		else if (!args.equals("force") && LadderBot.ladder.hasPendingResults())
		{
			MessageHelper.sendMessage("The ladder still has results pending, please resolve them, type `terminate force` to forcibly terminate ladder, or `save` to save the results for when the bot starts (although more likely to break).", channel);
			return;
		}
		
		System.out.println("Shutting down.");
		LadderBot.ladder.save();
		MessageHelper.sendMessage("Beep Boop :(", channel);
		LadderBot.api.shutdownNow();
		System.exit(0);
	}
	
	@Command("terminateforce")
	@CommandRequires(LadderBot.TERMINATE_PERM)
	@CommandDescription(description = "Terminates the bot", usage = "terminate [force|save]")
	public void terminateforceCmd(@CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.ARGS_RAW) String args)
	{
		System.out.println("Shutting down.");
		LadderBot.ladder.save();
		MessageHelper.sendMessage("Beep Boop :(", channel);
		LadderBot.api.shutdownNow();
		System.exit(0);
	}
	
	@Command("terminatesave")
	@CommandRequires(LadderBot.TERMINATE_PERM)
	@CommandDescription(description = "Terminates the bot", usage = "terminate [force|save]")
	public void terminatesaveCmd(@CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.ARGS_RAW) String args)
	{
		if (!LadderBot.ladder.savePending())
		{
			MessageHelper.sendMessage("Unable to save the pending results, please resolve them or type `terminate force` to forcibly terminate ladder.", channel);
			return;
		}
		
		System.out.println("Shutting down.");
		LadderBot.ladder.save();
		MessageHelper.sendMessage("Beep Boop :(", channel);
		LadderBot.api.shutdownNow();
		System.exit(0);
	}
	
	@Command("set")
	@CommandRequires(LadderBot.LADDER_PERM)
	@CommandDescription(description = "Sets a channel for the bot", usage = "set <in|out|history|admin>")
	public void setCmd(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.ARGS_RAW) String arg, @CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.GUILD) Guild guild)
	{
		MessageHelper.deleteMessageAfter(msg, messageLife);
		switch (arg)
		{
		case "in":
			LadderBot.ladder.inChannel = channel;
			MessageHelper.sendSuccess("You can now put results into this channel.", channel, messageLife);
			break;
		case "out":
			LadderBot.ladder.ladderChannel = channel;
			MessageHelper.sendSuccess("Now printing the ladder in this channel.", channel, messageLife);
			break;
		case "history":
			LadderBot.ladder.historyChannel = channel;
			MessageHelper.sendSuccess("Now putting the history in this channel.", channel, messageLife);
			break;
		case "admin":
			LadderBot.ladder.adminChannel = channel;
			MessageHelper.sendSuccess("This is now the admin channel.", channel, messageLife);
			break;
		default:
			MessageHelper.sendError("Could not understand `" + arg + "`. Expected `in`, `out`, `history`, or `admin`.", channel, messageLife);
			return;
		}
		LadderBot.ladder.save();
	}
	
	@Command("result")
	@CommandDescription(description = "Adds a pending result", usage = "result <Score 1> <Team 1 @'s...> <Score 2> <Team 2 @'s...>")
	public void resultCmd(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.ARGS) String[] args, @CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.GUILD) Guild guild)
	{
		if (LadderBot.ladder.inChannel != null && LadderBot.ladder.inChannel.getIdLong() == channel.getIdLong())
		{
			MessageHelper.deleteMessageAfter(msg, messageLife);
			
			if (args.length < 4)
			{
				MessageHelper.sendError("To few arguments.", channel, messageLife);
				return;
			}
			
			
			int score1, score2 = 0;
			List<Long> team1 = new ArrayList<Long>(), team2 = new ArrayList<Long>();
			
			if (!args[0].matches("[0-9]+"))
			{
				MessageHelper.sendError("Could not parse `" + args[0] + "`. Expected a number.", channel, messageLife);
				return;
			}
			
			score1 = Integer.parseInt(args[0]);
			
			boolean hasSwappedTeam = false;
			
			for (int i = 1; i < args.length; ++i)
			{
				if (args[i].matches("[0-9]+"))
				{
					if (!hasSwappedTeam || team1.size() == 0)
						hasSwappedTeam = true;
					else
					{
						MessageHelper.sendError("Expected a user, was given the number `" + args[i] + "`.`", channel, messageLife);
						return;
					}
					
					score2 = Integer.parseInt(args[i]);
				}
				else if (args[i].matches("<@!?[0-9]+>"))
				{
					long id = MessageHelper.extractId(args[i]);
					if (team1.contains(id) || team2.contains(id))
					{
						MessageHelper.sendError("The same user was entered twice.", channel, messageLife);
						return;
					}
					
					if (hasSwappedTeam)
						team2.add(id);
					else
						team1.add(id);
				}
				else
				{
					MessageHelper.sendError("Expected a user or a number, was given `" + args[i] + "` instead.", channel, messageLife);
					return;
				}
			}
			
			if (score1 <= 1 && score2 <= 1)
			{
				MessageHelper.sendError("Cannot have a series with less than 3 games.", channel, messageLife);
				return;
			}
			
			if (score1 == score2)
			{
				MessageHelper.sendError("A series must have a winner.", channel, messageLife);
			}
			
			if (team1.size() == 0 || team2.size() == 0)
			{
				MessageHelper.sendError("A team must be entered.", channel, messageLife);
				return;
			}
			
			if (score1 < score2)
			{
				List<Long> tmp = team2;
				team2 = team1;
				team1 = tmp;
			}
			
			EmbedBuilder eb = new EmbedBuilder();
			
			eb.setColor(new Color(105, 105, 255, 255));
			eb.setTitle("Ladder Update - Series #" + (LadderBot.ladder.getSeriesCount()+1));
			eb.setFooter("Made by R0bit");
			eb.setThumbnail("https://cdn.discordapp.com/emojis/747427401230712902.png");
			
			eb.addField("Winners (" + score1 + ")", team1.stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true)).collect(Collectors.joining("\n")), false);
			eb.addField("Losers (" + score2 + ")", team2.stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true)).collect(Collectors.joining("\n")), false);
			eb.addField("", "Please upvote this result if it is correct or downvote it if it is incorrect.", false);
			
			Result result = new Result(channel.sendMessage(eb.build()).complete(), team1, team2, score1, score2);
			LadderBot.ladder.addResult(result);
			addMessageListener(new ResultMessageListener(result));
		}
	}
	
	
	@Command("stats")
	@CommandDescription(description = "Gets stats about either yourself or a given player", usage = "stats [Player @] [show]")
	public void statsCmd(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.ARGS) String[] args, @CmdParam(Type.AUTHOR_USER) User user)
	{
		MessageHelper.deleteMessageAfter(msg, messageLife);

		String arg = "";
		boolean show = false;
		if (args.length == 1)
		{
			if (args[0].equals("show"))
				show = true;
			else
				arg = args[0];
		}
		else if (args.length == 2)
		{
			if (args[1].equals("show"))
				show = true;
			else
			{
				MessageHelper.sendError("Could not understand " + args[1] + ". Expected `show`.", channel, messageLife);
				return;
			}
			arg = args[0];
		}
		else if (args.length > 2)
		{
			MessageHelper.sendError("Too many args passed into command.", channel, messageLife);
			return;
		}
		
		if (arg.equals("") || arg.matches("<@!?[0-9]+>"))
		{
			long id;
			Player player;
			if (arg.equals(""))
			{
				id = msg.getAuthor().getIdLong();
				player = LadderBot.ladder.getPlayer(id);
			}
			else
			{
				id = MessageHelper.extractId(arg);
				player = LadderBot.ladder.getPlayer(id);
			}
			
			if (player == null)
			{
				MessageHelper.sendError("The user <@!" + id + "> has not played a ladder series.", channel, messageLife);
				return;
			}
			
			
			EmbedBuilder eb = new EmbedBuilder();
			eb.setColor(new Color(105, 105, 255, 255));
			eb.setTitle("Ladder Stats");
			eb.setFooter("Made by R0bit");
			
			eb.addField(MessageHelper.getNameFromId(id, LadderBot.ladder.guild, true),
					"Games: " + player.getGamesTotal() + 
					"\nWins: " + player.getGamesWins() + 
					"\nWin %: " + String.format("%.1f", (double)player.getGamesWins() / (double)player.getGamesTotal() * 100) +
					"\nSeries: " + player.getSeriesTotal() + 
					"\nSeries Wins: " + player.getSeriesWins() + 
					"\nSeries Win %: " + String.format("%.1f", (double)player.getSeriesWins() / (double)player.getSeriesTotal() * 100) + 
					"\nCurrent Winstreak: " + player.getWinstreak() +
					"\nHighest Winstreak: " + player.getHighestWinstreak() + 
					"\nLast Played: " + new SimpleDateFormat("dd/MM/yyyy").format(player.getLastPlayed()), false);
			
			if (show)
				MessageHelper.deleteMessageAfter(channel.sendMessage(eb.build()).complete(), 60 * 5);
			else
			{
				user.openPrivateChannel().queue(c -> 
					c.sendMessage(eb.build()).queue()
				);
			}
		}
		else
			MessageHelper.sendError("Couldn't find user matching " + arg + ".", channel, messageLife);
	}
	
	
	@Command("print")
	@CommandRequires(LadderBot.LADDER_PERM)
	@CommandDescription(description = "Prints the current state of ladder (user if ladder was manually updated)", usage = "print")
	public void printCmd(@CmdParam(Type.MSG) Message msg)
	{
		msg.delete().queue();
		LadderBot.ladder.print(false);
	}
	
	
	@Command("graph")
	@CommandRequires(LadderBot.LADDER_PERM)
	@CommandDescription(description = "Prints the current ladder graph in the channel this command is used", usage = "graph")
	public void graphCmd(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.CHANNEL) MessageChannel channel)
	{
		msg.delete().queue();
		BufferedImage bi = LadderBot.ladder.generateGraph();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			ImageIO.write(bi, "png", os);
			
			channel.sendFile(os.toByteArray(), "ladder.png").queue();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	
	/*
	 * STATS 2 OVER RIP BAKKES MOD ME AND MY HOMIES HATE BAKKESMOD
	@Command("link")
	@CommandDescription(description = "Links your discord account to your game account (Currently only for steam until I figure out platforms)", usage = "link <Steam ID/vanity url/Steam link>")
	public void linkCmd(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.AUTHOR_USER) User user, @CmdParam(Type.ARGS_RAW) String arg)
	{		
		MessageHelper.deleteMessageAfter(msg, messageLife);
		
		if (arg.endsWith("/"))
			arg = arg.substring(0, arg.length() - 1);
		
		if (LadderBot.ladder.getPlayer(user.getIdLong()) == null)
		{
			MessageHelper.sendError("Please play a ladder series before linking.", channel, messageLife);
		}
		
		if (arg.matches("[0-9]+") || arg.matches("https?://steamcommunity.com/profiles/[0-9]*"))
		{
			String id;
			
			if (arg.matches("[0-9]+"))
				id = arg;
			else
				id = arg.substring(arg.lastIndexOf('/') + 1);
			
			//Check if account exists
			try
			{
				URL url = new URL("http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=CD90986E172D0CB9760E407322B36116&steamids=" + id);
				HttpURLConnection http = (HttpURLConnection)url.openConnection();
				http.setRequestMethod("GET");
				http.setDoInput(true);
				http.setRequestProperty("Accept-Type", "application/json");

				http.connect();
				
				if (http.getResponseCode() == 200)
				{
					InputStream in = http.getInputStream();
					
					JSONObject jo = new JSONObject(new JSONTokener(new InputStreamReader(in, "UTF-8")));
					JSONArray players = jo.getJSONObject("response").getJSONArray("players");
					
					if (players.length() == 0)
						MessageHelper.sendError("Could not find steam account with the given id.", channel, messageLife);
					else
					{
						String profileUrl = players.getJSONObject(0).getString("profileurl");
						LadderBot.ladder.getPlayer(user.getIdLong()).setStatsId(id);
						MessageHelper.sendSuccess("Linked with the following steam account: <" + profileUrl + ">", channel, messageLife);
					}
					
				}
				else
				{
					MessageHelper.sendError("Could not find steam account with the given id.", channel, messageLife);
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				MessageHelper.sendError("Could not find steam account with the given id.", channel, messageLife);
			}
		}
		else
		{
			String vanity;
			
			if (arg.matches("https?://steamcommunity.com/id/.*"))
				vanity = arg.substring(arg.lastIndexOf('/') + 1);
			else
				vanity = arg;
			
			//Check if account exists
			try
			{
				URL url = new URL("http://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=CD90986E172D0CB9760E407322B36116&vanityurl=" + vanity);
				HttpURLConnection http = (HttpURLConnection)url.openConnection();
				http.setRequestMethod("GET");
				http.setDoInput(true);
				http.setRequestProperty("Accept-Type", "application/json");

				http.connect();
				
				if (http.getResponseCode() == 200)
				{
					InputStream in = http.getInputStream();
					
					JSONObject jo = new JSONObject(new JSONTokener(new InputStreamReader(in, "UTF-8"))).getJSONObject("response");
					
					if (jo.getInt("success") == 1)
					{
						String id = jo.getString("steamid");
						String profileUrl = "https://steamcommunity.com/profiles/" + id;
						LadderBot.ladder.getPlayer(user.getIdLong()).setStatsId(id);
						MessageHelper.sendSuccess("Linked with the following steam account: <" + profileUrl + ">", channel, messageLife);
					}
					else
						MessageHelper.sendError("Could not find steam account with the given vanity.", channel, messageLife);
					
					
				}
				else
				{
					MessageHelper.sendError("Could not find steam account with the given id.", channel, messageLife);
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				MessageHelper.sendError("Could not find steam account with the given id.", channel, messageLife);
			}
		}
	}
	
	@Command("unlink")
	@CommandDescription(description = "Unlinks your discord account from your game account", usage="unlink")
	public void unlinkCmd(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.AUTHOR_USER) User user)
	{
		msg.delete().queue();
		LadderBot.ladder.getPlayer(user.getIdLong()).setStatsId(null);
		MessageHelper.sendSuccess("Successfully unlinked your account.", channel, messageLife);
	}
	@Command("stats2")
	@CommandDescription(description = "Dumps stats2", usage = "stats2 [Player @] [show]")
	public void stats2Cmd(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.ARGS) String[] args, @CmdParam(Type.AUTHOR_USER) User user)
	{
		msg.delete().queue();

		long id = user.getIdLong();
		boolean show = false;
		if (args.length == 1)
		{
			if (args[0].equals("show"))
				show = true;
			else if (args[0].matches("<@!?[0-9]+>"))
				id = MessageHelper.extractId(args[0]);
			else
			{
				MessageHelper.sendError("Could not understand " + args[1] + ". Expected either a user @ or `show`.", channel, messageLife);
				return;
			}
		}
		else if (args.length == 2)
		{
			if (args[1].equals("show"))
				show = true;
			else
			{
				MessageHelper.sendError("Could not understand " + args[1] + ". Expected `show`.", channel, messageLife);
				return;
			}
			
			if (args[0].matches("<@!?[0-9]+>"))
				id = MessageHelper.extractId(args[0]);
			else
			{
				MessageHelper.sendError("Could not understand " + args[1] + ". Expected either a user @ or `show`.", channel, messageLife);
				return;
			}
		}
		else if (args.length > 2)
		{
			MessageHelper.sendError("Too many args passed into command.", channel, messageLife);
			return;
		}
		
		Player player = LadderBot.ladder.getPlayer(id);
		
		if (player.getStatsId() == null)
		{
			MessageHelper.sendError("Your steam ID is not linked.", channel, messageLife);
			return;
		}
		
		JSONObject stats2 = player.getStats2();
		if (stats2 != null)
		{
			try
			{
				//MessageHelper.sendTemporaryMessage(stats2.getJSONObject("game_average").toString(), channel, 60 * 2);
				JSONObject avg = stats2.getJSONObject("game_average");
				JSONObject total = stats2.getJSONObject("cumulative");
				EmbedBuilder eb = new EmbedBuilder();
				eb.setColor(new Color(105, 105, 255, 255));
				eb.setTitle("Ladder Stats 2.0 - " + MessageHelper.getNameFromId(user.getIdLong(), LadderBot.ladder.guild, true));
				eb.setFooter("Made by R0bit");
				
				JSONObject core = avg.getJSONObject("core");
				JSONObject pos = avg.getJSONObject("positioning");
				JSONObject boost = avg.getJSONObject("boost");
				JSONObject movement = avg.getJSONObject("movement");
				JSONObject demos = avg.getJSONObject("demo");
				
				JSONObject core2 = total.getJSONObject("core");
				JSONObject pos2 = total.getJSONObject("positioning");
				JSONObject boost2 = total.getJSONObject("boost");
				JSONObject movement2 = total.getJSONObject("movement");
				JSONObject demos2 = total.getJSONObject("demo");
				
				eb.addField("Average Core",
						"Score: " + core.getDouble("score") + 
						"\nGoals: " + core.getDouble("goals") +
						"\nGoals Conceded: " + core.getDouble("goals_against") +
						"\nAssists: " + core.getDouble("assists") + 
						"\nSaves: " + core.getDouble("saves") + 
						"\nShots: " + core.getDouble("shots"),
						//"\nMVP: " + core.getDouble("mvp"),
						false);
				
				eb.addField("Average Boost",
						"Amount: " + boost.getDouble("avg_amount") + 
						"\nAmount Overfill: " + boost.getDouble("amount_overfill") +
						"\nAmount Used While Supersonic: " + boost.getDouble("amount_used_while_supersonic") + 
						"\n% Time With 0 Boost: " + boost.getDouble("percent_zero_boost") + 
						"\nBPM: " + boost.getDouble("bpm") +
						"\nBCPM: " + boost.getDouble("bcpm") +
						"\nNumber of Small Pads Collected: " + boost.getDouble("count_collected_small") +
						"\nNumber of Large Pads Collected: " + boost.getDouble("count_collected_big"),
						false);
				
				eb.addField("Average Movement",
						"Speed: " + movement.getDouble("avg_speed") + 
						"\n% Time Supersonic: " + movement.getDouble("percent_supersonic_speed") +
						"\n% Time Slow: " + movement.getDouble("percent_slow_speed") + 
						"\n% Time on Ground: " + movement.getDouble("percent_ground") + 
						"\n% Time Low Air: " + movement.getDouble("percent_low_air") +
						"\n% Time High Air: " + movement.getDouble("percent_high_air") +
						"\nPowerslide Duration: " + movement.getDouble("avg_powerslide_duration") +
						"\nNumber of Powerslides: " + movement.getDouble("count_powerslide"),
						false);
				
				eb.addField("Average Other",
						"Goals Conceded as Last Defender: " + pos.getDouble("goals_against_while_last_defender") + 
						"\n% Time Infront of Ball: " + pos.getDouble("percent_infront_ball") +
						"\n% Time In Opponents Half: " + pos.getDouble("percent_offensive_half") + 
						"\nDemos Given: " + demos.getDouble("inflicted") + 
						"\nDemos Taken: " + demos.getDouble("taken"),
						false);
				
				eb.addField("Total Core",
						"Score: " + core2.getInt("score") + 
						"\nGoals: " + core2.getInt("goals") +
						"\nGoals Conceded: " + core2.getInt("goals_against") +
						"\nAssists: " + core2.getInt("assists") + 
						"\nSaves: " + core2.getInt("saves") + 
						"\nShots: " + core2.getInt("shots"),
						//"\nMVP: " + core2.getInt("mvp"),
						false);
				
				eb.addField("Total Boost",
						"Amount Collected: " + boost2.getInt("amount_collected") + 
						"\nAmount Overfill: " + boost2.getInt("amount_overfill") +
						"\nAmount Used While Supersonic: " + boost2.getInt("amount_used_while_supersonic") + 
						"\nAmount of Time on 0 Boost: " + boost2.getDouble("time_zero_boost") +
						"\nNumber of Small Pads Collected: " + boost2.getInt("count_collected_small") +
						"\nNumber of Large Pads Collected: " + boost2.getInt("count_collected_big"),
						false);
				
				eb.addField("Total Movement",
						"Distance Travelled: " + movement.getLong("total_distance") + 
						"\nTime Supersonic: " + movement2.getDouble("time_supersonic_speed") + "s" +
						"\nTime Slow: " + movement2.getDouble("time_slow_speed") + "s" + 
						"\nTime on Ground: " + movement2.getDouble("time_ground") + "s" + 
						"\nTime Low Air: " + movement2.getDouble("time_low_air") + "s" +
						"\nTime High Air: " + movement2.getDouble("time_high_air") + "s" +
						"\nNumber of Powerslides: " + movement2.getInt("count_powerslide"),
						false);
				
				eb.addField("Total Other",
						"Time played: " + total.getLong("play_duration") + "s" + 
						"\nGoals Conceded as Last Defender: " + pos2.getInt("goals_against_while_last_defender") + 
						"\nTime Infront of Ball: " + pos2.getDouble("time_infront_ball") + "s" +
						"\nTime In Opponents Half: " + pos2.getDouble("time_offensive_half") + "s" + 
						"\nDemos Given: " + demos2.getInt("inflicted") + 
						"\nDemos Taken: " + demos2.getInt("taken"),
						false);
				
				if (show)
					MessageHelper.deleteMessageAfter(channel.sendMessage(eb.build()).complete(), 60 * 5);
				else
				{
					user.openPrivateChannel().queue(c ->
						c.sendMessage(eb.build()).queue()
					);
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		else
			MessageHelper.sendError("Could not retrieve stats2.", channel, messageLife);
	}
	*/
	
	@Command("debug")
	@CommandRequires(LadderBot.LADDER_PERM)
	@CommandDescription(description = "Dumps some debug info", usage = "debug")
	public void onDebugCmd(@CmdParam(Type.CHANNEL) MessageChannel channel)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("queue:\n");
		sb.append(LadderBot.ladder.getDebug());
		MessageHelper.sendMessage(sb.toString(), channel);
	}
	
	@Command("pop")
	@CommandRequires(LadderBot.LADDER_PERM)
	@CommandDescription(description = "Pops the front of the pending ladder queue", usage = "pop")
	public void onPopCmd(@CmdParam(Type.CHANNEL) MessageChannel channel)
	{
		Result r = LadderBot.ladder.popQueue();
		if (r == null)
			MessageHelper.sendMessage("The front of the queue was null.", channel);
		else
			MessageHelper.sendMessage("The front of the queue was <" + r.getResultMessage().getJumpUrl() + ">", channel);
	}
	
	@Command("poll")
	@CommandRequires(LadderBot.LADDER_PERM)
	@CommandDescription(description = "Polls the ladder queue", usage = "poll")
	public void onPollCmd(@CmdParam(Type.CHANNEL) MessageChannel channel)
	{
		MessageHelper.sendMessage("Force polling results.", channel);
		LadderBot.ladder.pollResults(true);
		
	}
	
	
	@OnMessageReceived(ignorePing=true)
	public void onMessageRecieved(@CmdParam(Type.MSG) Message msg, @CmdParam(Type.CHANNEL) MessageChannel channel)
	{
		if (LadderBot.ladder.inChannel != null && channel.equals(LadderBot.ladder.inChannel))
		{
			MessageHelper.deleteMessageAfter(msg, messageLife);
			return;
		}
	}
	
	@Override
    public void onReconnect(ReconnectedEvent event) 
    {
    	LadderBot.ladder.pollResults(true);
    }
	
	@Command("ping")
	public void onPingCmd(@CmdParam(Type.CHANNEL) MessageChannel channel)
	{
		channel.sendMessage("pong").queue();
	}
	
	@Command("rotation")
	@CommandDescription(description="Creates a series of rotation games", usage="rotatoin [Number of Games]")
	public void rotationCmd(@CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.MSG) Message msg, @CmdParam(Type.AUTHOR_MEMBER) Member author, @CmdParam(Type.GUILD) Guild guild, @CmdParam(Type.ARGS_RAW) String args)
	{
		if (LadderBot.ladder.inChannel != null && LadderBot.ladder.inChannel.getIdLong() == channel.getIdLong())
		{
			MessageHelper.deleteMessageAfter(msg, messageLife);
			
			if (!args.matches("[0-9]{1,2}"))
			{
				MessageHelper.sendError("The number of games must be a positive integer.", channel, messageLife);
				return;
			}
			int count = Integer.parseInt(args);
			if (count == 0)
			{
				MessageHelper.sendError("The number of games must be a positive integer.", channel, messageLife);
				return;
			}
			
			VoiceChannel vc = author.getVoiceState().getChannel();
			
			if (vc == null)
			{
				MessageHelper.sendError("You must be in a voice channel to use this command.", channel, messageLife);
				return;
			}
			
			List<Member> members = vc.getMembers();
			System.out.println("Members: " + members.size());
			if (members.size() != 6 && members.size() != 4)
			{
				MessageHelper.sendError("Currently this only works for 4/6 people in the given channel.", channel, messageLife);
				return;
			}
			
			Set<PlayerMember> playersSet = members.stream().map(x -> new PlayerMember(LadderBot.ladder.getPlayer(x.getIdLong()), MessageHelper.getNameFromId(x.getIdLong(), guild, true), x.getUser().getAsTag())).collect(Collectors.toSet());
			List<Long> players = playersSet.stream().map(x -> x.player.getId()).collect(Collectors.toList());
			
			RotationMessageListener rmlBefore = null;
			
			if (members.size() == 4)
			{
				int perms[][] = {
						{0, 3, 1, 2},
						{0, 2, 1, 3},
						{0, 1, 2, 3}
				};
				
				for (int i = 0; i < count; ++i)
				{
					List<Long> team1 = new ArrayList<Long>(), team2 = new ArrayList<Long>();
					
					team1.add(players.get(perms[i % perms.length][0]));
					team1.add(players.get(perms[i % perms.length][1]));
					team2.add(players.get(perms[i % perms.length][2]));
					team2.add(players.get(perms[i % perms.length][3]));
					
					EmbedBuilder eb = new EmbedBuilder();
					
					eb.setColor(new Color(105, 105, 255, 255));
					eb.setTitle("Rotation Match #" + (i+1));
					eb.setFooter("Made by R0bit");
					eb.setThumbnail("https://cdn.discordapp.com/emojis/747427401230712902.png");
					
					eb.addField("Team 1: ", team1.stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true)).collect(Collectors.joining("\n")), false);
					eb.addField("Team 2: ", team2.stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true)).collect(Collectors.joining("\n")), false);
					eb.addField("", "Please emote for the winning team.", false);
					
					Rotation rotation = new Rotation(channel.sendMessage(eb.build()).complete(), team1, team2);
					RotationMessageListener rml = new RotationMessageListener(rotation);
					if (rmlBefore != null)
						rmlBefore.setNext(rml);
					rmlBefore = rml;
					addMessageListener(rml);
				}
			}
			else // size == 6
			{
				int perms[][] = {
						{0, 3, 5, 1, 2, 4},
						{0, 2, 5, 1, 3, 4},
						{0, 3, 4, 1, 2, 5},
						{0, 4, 5, 1, 2, 3},
						{0, 2, 4, 1, 3, 5},
						{0, 1, 5, 2, 3, 4},
						{0, 2, 3, 1, 4, 5},
						{0, 1, 4, 2, 3, 5},
						{0, 1, 3, 2, 4, 5},
						{0, 1, 2, 3, 4, 5}
				};
				
				for (int i = 0; i < count; ++i)
				{
					List<Long> team1 = new ArrayList<Long>(), team2 = new ArrayList<Long>();
					team1.clear();
					team2.clear();
					
					team1.add(players.get(perms[i % perms.length][0]));
					team1.add(players.get(perms[i % perms.length][1]));
					team1.add(players.get(perms[i % perms.length][2]));
					team2.add(players.get(perms[i % perms.length][3]));
					team2.add(players.get(perms[i % perms.length][4]));
					team2.add(players.get(perms[i % perms.length][5]));
					
					EmbedBuilder eb = new EmbedBuilder();
					
					eb.setColor(new Color(105, 105, 255, 255));
					eb.setTitle("Rotation Match #" + (i+1));
					eb.setFooter("Made by R0bit");
					eb.setThumbnail("https://cdn.discordapp.com/emojis/747427401230712902.png");
					
					eb.addField("Team 1: ", team1.stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true)).collect(Collectors.joining("\n")), false);
					eb.addField("Team 2: ", team2.stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true)).collect(Collectors.joining("\n")), false);
					eb.addField("", "Please emote for the winning team.", false);
					
					Rotation rotation = new Rotation(channel.sendMessage(eb.build()).complete(), team1, team2);
					RotationMessageListener rml = new RotationMessageListener(rotation);
					if (rmlBefore != null)
						rmlBefore.setNext(rml);
					rmlBefore = rml;
					addMessageListener(rml);
				}
				
			}
		}
	}
	
}
