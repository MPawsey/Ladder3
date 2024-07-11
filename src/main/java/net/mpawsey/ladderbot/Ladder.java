package net.mpawsey.ladderbot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.mpawsey.bothelper.FileManager;
import net.mpawsey.bothelper.MessageHelper;

public class Ladder 
{
	public Role LEADER_ROLE = null;
	
	public Guild guild;
	public MessageChannel inChannel, adminChannel, ladderChannel, historyChannel;
	private int seriesCount;
	
	private Queue<Result> pendingResults = new LinkedList<Result>();
	
	private HashMap<Long, Player> players = new HashMap<Long, Player>();
	
	public Ladder()
	{		
		boolean found = false;
		for (Guild g : LadderBot.api.getGuilds())
		{
			for (Role r : g.getRoles())
			{
				if (r.getIdLong() == 658078009877856256l)
				{
					this.guild = g;
					this.LEADER_ROLE = r;
					found = true;
					break;
				}
			}
			if (found)
				break;
		}
		
		if (this.guild == null)
			System.err.println("DID NOT FIND GUILD!!!");
		
		if (!FileManager.doesFileExist("settings"))
		{
			this.inChannel = null;
			this.adminChannel = null;
			this.ladderChannel = null;
			this.historyChannel = null;
			this.seriesCount = 0;
			return;
		}
		
		// Get Settings
		String[] settings = FileManager.readFile("settings").get(0).split(",");

		if (!settings[0].equals(""))
			this.inChannel = this.guild.getTextChannelById(settings[0]);
		if (!settings[1].equals(""))
			this.ladderChannel = this.guild.getTextChannelById(settings[1]);
		if (!settings[2].equals(""))
			this.historyChannel = this.guild.getTextChannelById(settings[2]);
		if (!settings[3].equals(""))
			this.adminChannel = this.guild.getTextChannelById(settings[3]);

		this.seriesCount = Integer.parseInt(settings[4]);
		
		// Fix histories/players
		
		String latestPlayers = "";
		String latestPending = null;
		
		String[] files = new File("./").list();
		Arrays.sort(files);
		for (String file : files)
		{
			if (file.startsWith("history"))
			{
				if (!file.equals("history"))
				{
					if (FileManager.writeFile("history", FileManager.readFile(file), true))
					{
						if (!new File(latestPlayers).delete())
						{
							System.err.println("Could not delete file " + latestPlayers);
							if (adminChannel != null)
								MessageHelper.sendError("Could not delete file " + latestPlayers + " on startup.", adminChannel);
						}
					}
					else
					{
						System.err.println("Could not update history with file " + file);
						if (adminChannel != null)
							MessageHelper.sendError("Could not update history with file " + file + " on startup.", adminChannel);
					}
				}
			}
			else if (file.startsWith("pending"))
			{
				if (latestPending != null)
				{
					if (!new File(latestPending).delete())
					{
						System.err.println("Could not delete file " + latestPending);
						if (adminChannel != null)
							MessageHelper.sendError("Could not delete file " + latestPending + " on startup.", adminChannel);
					}
				}
				latestPending = file;
			}
			else if (file.startsWith("players"))
			{
				if (!latestPlayers.equals(""))
				{
					if (!new File(latestPlayers).delete())
					{
						System.err.println("Could not delete file " + latestPlayers);
						if (adminChannel != null)
							MessageHelper.sendError("Could not delete file " + latestPlayers + " on startup.", adminChannel);
					}
				}
				latestPlayers = file;
			}
			else if (file.startsWith("settings"))
			{
				if (!file.equals("settings"))
				{
					if (new File("settings").delete())
					{
						if (!new File(file).renameTo(new File("settings")))
						{
							System.err.println("Could not rename file " + file);
							if (adminChannel != null)
								MessageHelper.sendError("Could not rename file " + file + " on startup.", adminChannel);
						}
					}
					else
					{
						System.err.println("Could not delete file settings");
						if (adminChannel != null)
							MessageHelper.sendError("Could not delete file settings on startup.", adminChannel);
					}
					
					String[] st = FileManager.readFile("settings").get(0).split(",");

					if (!st[0].equals(""))
						this.inChannel = this.guild.getTextChannelById(st[0]);
					if (!st[1].equals(""))
						this.ladderChannel = this.guild.getTextChannelById(st[1]);
					if (!st[2].equals(""))
						this.historyChannel = this.guild.getTextChannelById(st[2]);
					if (!st[3].equals(""))
						this.adminChannel = this.guild.getTextChannelById(st[3]);

					this.seriesCount = Integer.parseInt(st[4]);
				}
			}
		}
		
		if (!latestPlayers.equals("players"))
			new File(latestPlayers).renameTo(new File("players"));
		
		for (String player : FileManager.readFile("players"))
		{
			Player p = new Player(player);
			players.put(p.getId(), p);
		}
		
	}
	
	public int getSeriesCount()
	{
		return seriesCount + pendingResults.size();
	}
	
	public Player getPlayer(long id)
	{
		Player player = players.get(id);
		if (player == null)
		{
			player = new Player(id);
			players.put(id, player);
		}
		return player;
	}
	
	public void addResult(Result result)
	{
		pendingResults.add(result);
	}
	
	public void removeResult(Result result)
	{
		boolean front = false, update = false;
		int count = 0;
		
		for (Result res : pendingResults)
		{
			if (res == result)
			{
				front = count == 0;
				update = true;
			}
			
			if (update)
			{
				MessageEmbed embed = res.getResultMessage().getEmbeds().get(0);
				embed.getTitle().replaceAll("#[0-9]+", "#" + (seriesCount + count + 1));
			}
			
			++count;
		}
		
		pendingResults.remove(result);
		
		if (front)
			pollResults(false);
	}

	public void pollResults(boolean check) 
	{
		boolean update = false;
		
		while (!pendingResults.isEmpty())
		{
			if (pendingResults.peek().isValid() || (check && pendingResults.peek().checkResultValid()))
			{
				update = true;
				pushMmr();
				
				// apply result
				++seriesCount;
				
				Result result = pendingResults.poll();
				
				double winnerMmr = 0, loserMmr = 0;
				Player winners[] = new Player[result.getWinners().size()], losers[] = new Player[result.getLosers().size()];
				
				int tmp = 0;
				for (Long id : result.getWinners())
				{
					Player p = players.get(id);
					if (p == null)
					{
						p = new Player(id);
						players.put(id, p);
					}
					winnerMmr += p.getMmr();
					winners[tmp++] = p;
				}
				
				tmp = 0;
				for (Long id : result.getLosers())
				{
					Player p = players.get(id);
					if (p == null)
					{
						p = new Player(id);
						players.put(id, p);
					}
					loserMmr += p.getMmr();
					losers[tmp++] = p;
				}
				
				double delta = Math.pow(10.0, 3.0-(4.0 * winnerMmr / (winnerMmr + loserMmr)));
				

				// winners
				double[] winRatios = new double[winners.length];
				double winRatiosSum = 0.0;
				for (int i = 0; i < winRatios.length; ++i)
					winRatiosSum += (winRatios[i] = winnerMmr / winners[i].getMmr());
				
				double winMult = winRatios.length * delta / winRatiosSum;
				
				for (int i = 0; i < winners.length; ++i)
					winners[i].addResult(result, winRatios[i] * winMult, true);
				
				// losers
				for (int i = 0; i < losers.length; ++i)
					losers[i].addResult(result, -losers.length * delta * losers[i].getMmr() / loserMmr, false);
				
				Message msg = inChannel.retrieveMessageById(result.getResultMessage().getIdLong()).complete();
				EmbedBuilder eb = new EmbedBuilder(msg.getEmbeds().get(msg.getEmbeds().size()-1));
				eb.clearFields();
				
				//eb.addField("Winners", String.join("\n", result.getWinners().stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true) + "(+" + ).collect(Collectors.joining(", "))), false);
				//eb.addField("Losers", String.join("\n", result.getLosers().stream().map(id -> (String)MessageHelper.getNameFromId(id, guild, true)).collect(Collectors.joining(", "))), false);
				
				final double tmp2 = loserMmr;
				
				eb.addField("Winners (" + result.getWinningScore() + ")", IntStream.range(0, winners.length).mapToObj(i -> MessageHelper.getNameFromId(result.getWinners().get(i), guild, true) + "(+" + (Math.round(winRatios[i] * winMult * 10.0) / 10.0) + ")").collect(Collectors.joining("\n")), false);
				eb.addField("Losers (" + result.getLosingScore() + ")", IntStream.range(0, losers.length).mapToObj(i -> MessageHelper.getNameFromId(result.getLosers().get(i), guild, true) + "(-" + (Math.round(losers.length * delta * losers[i].getMmr() * 10.0 / tmp2) / 10.0) + ")").collect(Collectors.joining("\n")), false);

				msg.editMessage(eb.build()).queue();
				
				StringBuilder sb = new StringBuilder();
				
				sb.append(result.getWinningScore());
				sb.append(',');
				sb.append(String.join("-", result.getWinners().stream().map(x -> x.toString()).collect(Collectors.toList())));
				sb.append(',');
				sb.append(result.getLosingScore());
				sb.append(',');
				sb.append(String.join("-", result.getLosers().stream().map(x -> x.toString()).collect(Collectors.toList())));
				sb.append(',');
				sb.append(Player.ISO8601.format(result.getResultTime()));
				
				FileManager.tryWriteToFile("history", sb.toString(), true, () -> MessageHelper.sendError("Could not save the following result to history:\n```" + sb.toString() + "```", adminChannel));
				
				print(true);
			}
			else
				break;
		}
		
		if (update)
		{
			long oldEP = -1l;
			Player currentBest = null;
			for (Player player : players.values())
			{
				try
				{
				if (currentBest == null || currentBest.getMmr() < player.getMmr())
					currentBest = player;
				
				if (LEADER_ROLE != null && guild.retrieveMemberById(player.getId()).complete().getRoles().contains(LEADER_ROLE))
					oldEP = player.getId();
				}
				catch (Exception e)
				{
					System.err.println("Could not find user " + player.getId() + " in the guild.");
				}
			}
			
			if (LEADER_ROLE != null && currentBest != null && oldEP != currentBest.getId())
			{
				if (oldEP != -1l)
					guild.removeRoleFromMember(oldEP, LEADER_ROLE).queue();
				
				guild.addRoleToMember(currentBest.getId(), LEADER_ROLE).queue();
			}
			
			print(false);
			
			System.out.println("Attempting to save...");
			save();
			System.out.println("Saved.");
		}
	}
	
	public void save()
	{
		List<String> contents = new ArrayList<String>();
		for (Player player : players.values())
			contents.add(player.toString());
		
		FileManager.tryWriteToFile("players", contents, false, () -> MessageHelper.sendError("Could not save players, dumping:\n```" + contents + "```", adminChannel));
		
		FileManager.tryWriteToFile("settings", 
				(inChannel == null ? "" : inChannel.getId()) + ',' + 
				(ladderChannel == null ? "" : ladderChannel.getId()) + ',' + 
				(historyChannel == null ? "" : historyChannel.getId()) + ',' + 
				(adminChannel == null ? "" : adminChannel.getId()) + ',' + 
				seriesCount,
				false, 
				() -> MessageHelper.sendError("Could not save players, dumping:\n```" + contents + "```", adminChannel));
		
	}
	
	public void print(boolean onlyHistory)
	{
		TreeSet<PlayerMember> set = new TreeSet<PlayerMember>();
		int longestName = 0, longestDiscord = 0;
		for (Player player : players.values())
		{
			try
			{
			if (guild.retrieveMemberById(player.getId()).complete() == null)
				continue;
			
			PlayerMember pm = new PlayerMember(player, MessageHelper.getNameFromId(player.getId(), guild, true), guild.retrieveMemberById(player.getId()).complete().getUser().getName()); 
			set.add(pm);
			
			if (pm.name.length() > longestName)
				longestName = pm.name.length();
			if (pm.discord.length() > longestDiscord)
				longestDiscord = pm.discord.length();
			}
			catch (Exception e)
			{
				System.err.println("Could not find user " + player.getId() + " in the guild.");
			}
		}
		
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("```py\n");
		sb.append(new SimpleDateFormat("dd/MM").format(new Date()));
		sb.append(" │ Pos. │ Player");
		for (int i = 0; i < longestName - 6; ++i)
			sb.append(' ');
		sb.append(" │  MMR  │ Discord\n");
		sb.append("──────┼──────┼─");
		for (int i = 0; i < longestName; ++i)
			sb.append('─');
		sb.append("─┼───────┼─");
		for (int i = 0; i < longestDiscord; ++i)
			sb.append('─');
		
		sb.append('\n');
		
		int i = 0;
		for (PlayerMember pm : set)
		{
			++i;
			
			if (i == pm.player.getLastPosition() || pm.player.getLastPosition() == -1)
				sb.append(" →   ");
			else if (i < pm.player.getLastPosition())
			{
				sb.append(" ↑ ");
				int change = pm.player.getLastPosition() - i;
				if (change < 10)
					sb.append(' ');
				sb.append(change);
			}
			else
			{
				sb.append(" ↓ ");
				int change = i - pm.player.getLastPosition();
				if (change < 10)
					sb.append(' ');
				sb.append(change);
			}
			pm.player.setPosition(i);
			
			sb.append(" │ ");
			
			if (i < 10)
				sb.append(' ');
			sb.append(i);			
			switch (i % 10)
			{
			case 1:
				if (i == 11)
					sb.append("th");
				else
					sb.append("st");
				break;
			case 2:
				if (i == 12)
					sb.append("th");
				else
					sb.append("nd");
				break;
			case 3:
				if (i == 13)
					sb.append("th");
				else
					sb.append("rd");
				break;
			default:
				sb.append("th");
			}
			
			sb.append(" │ ");
			
			sb.append(pm.name);
			
			for (int j = 0; j < longestName - pm.name.length(); ++j)
				sb.append(' ');
			
			sb.append(" │ ");
			
			int size = 0;
			for (int j = (int)pm.player.getMmr(); j > 0; j/=10)
				++size;
			
			for (int j = 0; j < 5 - size; ++j)
				sb.append(' ');
			sb.append((int)pm.player.getMmr());
			
			sb.append(" │ ");
			sb.append(pm.discord);
			sb.append('\n');
		}
		
		sb.append("```");
		

		for (Message msg : ladderChannel.getIterableHistory())
		{
			if (msg.getAuthor().getId().equals(LadderBot.api.getSelfUser().getId()))
				msg.delete().queue();
		}
		
		if (ladderChannel != null && !onlyHistory)
		{
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			try {
				ImageIO.write(generateGraph(), "png", os);

				ladderChannel.sendMessage(sb.toString()).addFile(os.toByteArray(), "ladder.png").queue();
			} catch (IOException e) {
				e.printStackTrace();
				MessageHelper.sendMessage(sb.toString(), ladderChannel);
				return;
			}
		}
		if (historyChannel != null && onlyHistory)
			MessageHelper.sendMessage(sb.toString(), historyChannel);
	}
	
	
	public boolean hasPendingResults()
	{
		return !pendingResults.isEmpty();
	}
	
	public BufferedImage generateGraph()
	{
		BufferedImage bi = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bi.createGraphics();
		
		// Setup background
		g2d.setColor(new Color(47, 49, 52));
		g2d.fillRect(0, 0, 1600, 900);
		g2d.setColor(Color.BLACK);
		g2d.setStroke(new BasicStroke(5));
		g2d.drawRoundRect(0, 0, 1600, 900, 1, 1);
		
		
		BufferedImage img;
		try {
			HttpURLConnection con = (HttpURLConnection)(new URL("https://cdn.discordapp.com/emojis/747427401230712902.png").openConnection());
			con.setRequestProperty("User-Agent", "Mozilla");
			img = ImageIO.read(con.getInputStream());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return bi;
		} catch (IOException e) {
			e.printStackTrace();
			return bi;
		}
		
		
		// Find Thumbnail dimensions
		final int MAX_SIZE = 128;
		final int MIN_X = 25;
		final int MIN_Y = 10;
		double ratio = img.getWidth() / img.getHeight();
		int imgX, imgY, imgWidth, imgHeight;
		if (img.getWidth() > img.getHeight())
		{
			imgWidth = MAX_SIZE;
			imgHeight = (int)(imgWidth / ratio);
			imgX = MIN_X;
			imgY = MIN_Y + (MAX_SIZE - imgHeight) / 2;
		}
		else
		{
			imgHeight = MAX_SIZE;
			imgWidth = (int)(ratio * imgHeight);
			imgY = MIN_Y;
			imgX = MIN_X + (MAX_SIZE - imgWidth) / 2;
		}
		

		// Draw icon
		g2d.drawImage(img, imgX, imgY, imgWidth, imgHeight, null);
		Font font = new Font("Ariel", Font.BOLD | Font.ITALIC, 32);
		g2d.setFont(font);
		g2d.setColor(Color.WHITE);
	    FontRenderContext frc = new FontRenderContext(null, false, false);
		Rectangle2D bounds = font.getStringBounds("LADDER", frc);
		g2d.drawString("LADDER", imgWidth / 2 - (float)bounds.getCenterX() + imgX, (float)(-15 + imgHeight + bounds.getHeight())); // -15 magic number do not touch :)
		
		// Draw Axes
		
		Stroke thick = new BasicStroke(2);
		Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {9}, 0);
		
		g2d.setStroke(thick);
		g2d.drawLine(200, 850, 1525, 850);
		g2d.drawLine(1525, 50, 1525, 850);
		
		
		// Draw player names
		double highestMmr = 1000;
		double lowestMmr = 999;
		double bestMmr = 0;
		Player bestPlayer = null;
		Font font2 = new Font("Ariel", Font.BOLD | Font.ITALIC, 16);
		g2d.setFont(font2);

		List<Player> graphPlayers = new ArrayList<Player>();
		
		for (Player player : players.values())
		{
			try
			{
			if (guild.retrieveMemberById(player.getId()).complete() == null)
				continue;
			}
			catch (Exception e)
			{
				System.err.println("Could not find member: " + player.getId());
				continue;
			}
			
			if (player.getPreviousMmr()[0] != null && player.getMmr() == player.getPreviousMmr()[0])
			{
				boolean valid = false;
				for (int i = 1; i < player.getPreviousMmr().length; ++i)
				{
					if (!player.getPreviousMmr()[i-1].equals(player.getPreviousMmr()[i]))
					{
						valid = true;
						break;
					}
				}
				
				if (!valid)
					continue;
			}
			
			graphPlayers.add(player);

			
			if (player.getMmr() > bestMmr)
			{
				bestMmr = player.getMmr();
				bestPlayer = player;
			}
			
			if (player.getMmr() > highestMmr)
				highestMmr = player.getMmr();
			else if (player.getMmr() < lowestMmr)
				lowestMmr = player.getMmr();
			
			for (Double d : player.getPreviousMmr())
			{
				if (d != null && d > highestMmr)
					highestMmr = d;
			}
		}
		
		// Round mmr scale to first 200 up/down
		int yMax = (int)highestMmr + 200 - ((int)highestMmr % 200);
		int yMin = (int)lowestMmr - ((int)lowestMmr % 200);
		
		
		for (int i = 0; i <= (yMax - yMin) / 200; ++i)
		{
			int y = 850 - (800 * 200 / (yMax - yMin)) * i;
			
			g2d.setStroke(thick);
			g2d.setColor(Color.WHITE);
			g2d.drawLine(1525, y, 1540, y);
			g2d.drawString("" + (i * 200 + yMin), 1545, y + 5);
			
			if (i != 0)
			{
				g2d.setStroke(dashed);
				g2d.setColor(Color.GRAY);
				g2d.drawLine(200, y, 1524, y);
			}
		}

		Rectangle2D bounds2 = font2.getStringBounds("#000", frc);
		
		int segments = Math.min(14, seriesCount);
		
		for (int i = 0; i < segments + 1; ++i)
		{
			int x = 1525 - (1325 * i / segments);
			
			g2d.setStroke(thick);
			g2d.setColor(Color.WHITE);
			g2d.drawLine(x, 850, x, 865);
			g2d.drawString("#" + (seriesCount - i), x - (int)bounds2.getCenterX(), 885);
		}

		int index = 0;
		// First
		if (bestPlayer != null)
		{
			Color c = generateRandomColour(index, graphPlayers.size());
			int y = (int)(img.getHeight() + bounds.getHeight()) + 30 + (20 * index++);
	
			g2d.setColor(Color.WHITE);
			g2d.drawString(MessageHelper.getNameFromId(bestPlayer.getId(), guild, true), 45, y);
			
			g2d.setColor(c);
			g2d.drawLine(25, y-5, 40, y-5);
			
			g2d.setStroke(thick);
			GeneralPath line = new GeneralPath();
			
			line.moveTo(1523, 850 - ((bestPlayer.getMmr() - yMin) * 800 / (yMax - yMin)));
			
			for (int i = 0; i < segments; ++i)
			{
				if (bestPlayer.getPreviousMmr()[i] != null)
					line.lineTo(1525 - (1325 * (i+1) / segments - 1), 850 - ((bestPlayer.getPreviousMmr()[i] - yMin) * 800 / (yMax - yMin)));
			}
			
			g2d.draw(line);
		}
		
		for (Player player : graphPlayers)
		{
			if (player == bestPlayer)
				continue;
			
			Color c = generateRandomColour(index, graphPlayers.size());
			
			int y = (int)(img.getHeight() + bounds.getHeight()) + 30 + (20 * index++);

			g2d.setColor(Color.WHITE);
			g2d.drawString(MessageHelper.getNameFromId(player.getId(), guild, true), 45, y);
			
			g2d.setColor(c);
			g2d.drawLine(25, y-5, 40, y-5);
			
			g2d.setStroke(thick);
			GeneralPath line = new GeneralPath();
			
			line.moveTo(1523, 850 - ((player.getMmr() - yMin) * 800 / (yMax - yMin)));
			
			for (int i = 0; i < segments; ++i)
			{
				// 850 - (800 * 200 / (yMax - yMin)) * i;
				if (player.getPreviousMmr()[i] != null)
					line.lineTo(1525 - (1325 * (i+1) / segments - 1), 850 - ((player.getPreviousMmr()[i] - yMin) * 800 / (yMax - yMin)));
			}
			
			g2d.draw(line);
			
		}
		
		return bi;
	}
	
	private Color generateRandomColour(int value, int max)
	{
		// the + is so value 0 = GE colour
		return Color.getHSBColor(value / (float)max + 0.813888889f, 0.9f, 0.97f);
	}
	
	
	private void pushMmr()
	{
		for (Player p : players.values())
			p.pushMmr();
		
		save();
	}
	
	public String getDebug()
	{
		StringBuilder sb = new StringBuilder();
		
		for (Result r : pendingResults)
		{
			sb.append('<');
			sb.append(r.getResultMessage().getJumpUrl());
			sb.append('>');
		}
		
		return sb.toString();
	}
	
	public Result popQueue()
	{
		Result r = pendingResults.poll();
		pollResults(true);
		return r;
	}
	
	public boolean savePending()
	{
		List<String> results = new ArrayList<String>();
		for (Result r : pendingResults)
			results.add(r.toString());
		final boolean[] success = {true};
		FileManager.tryWriteToFile("pending", results, false, () -> success[0] = false);
		return success[0];
	}
	
}




