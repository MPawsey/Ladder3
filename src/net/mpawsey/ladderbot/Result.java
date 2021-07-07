package net.mpawsey.ladderbot;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

public class Result 
{
	private Message resultMsg;
	private List<Long> winners, losers;
	private int scoreW, scoreL;
	private boolean valid;
	
	public Result(Message resultMsg, List<Long> winners, List<Long> losers, int scoreW, int scoreL)
	{
		this.resultMsg = resultMsg;
		this.winners = winners;
		this.losers = losers;
		this.scoreW = scoreW;
		this.scoreL = scoreL;
	}
	
	public Result(String str)
	{
		String[] args = str.split(";");
		LadderBot.api.getTextChannelById(args[0]).retrieveMessageById(args[1]);
		
		winners = Arrays.asList(args[2].split(",")).stream().map(x -> Long.parseLong(x)).collect(Collectors.toList());
		losers = Arrays.asList(args[3].split(",")).stream().map(x -> Long.parseLong(x)).collect(Collectors.toList());
		
		scoreW = Integer.parseInt(args[4]);
		scoreL = Integer.parseInt(args[5]);
		
		valid = Boolean.parseBoolean(args[6]);
	}
	
	public void setValid(boolean isValid)
	{
		valid = isValid;
	}
	
	public List<Long> getWinners()
	{
		return winners;
	}
	
	public List<Long> getLosers()
	{
		return losers;
	}
	
	public int getWinningScore()
	{
		return scoreW;
	}
	
	public int getLosingScore()
	{
		return scoreL;
	}
	
	public boolean isValid()
	{
		return valid;
	}
	
	public Message getResultMessage() {
		return resultMsg;
	}
	
	public Date getResultTime()
	{
		return Date.from(resultMsg.getTimeCreated().toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant());
	}
	

	
	public boolean checkResultValid()
	{
		List<User> up = resultMsg.retrieveReactionUsers("U+1f44d").complete();
		List<User> down = resultMsg.retrieveReactionUsers("U+1f44e").complete();
		
		for (User user : down)
		{
			if (winners.contains(user.getIdLong()) || losers.contains(user.getIdLong()))
				return false;
		}
		
		if (resultMsg.getTimeCreated().plusHours(12).isBefore(OffsetDateTime.now()))
			return true;
		
		int count = 0;
		for (User user : up)
		{
			if (winners.contains(user.getIdLong()) || losers.contains(user.getIdLong()))
				++count;
		}
		
		return count > (winners.size() + losers.size()) / 2 + 1;
	}
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(resultMsg.getChannel().getId());
		sb.append(';');
		sb.append(resultMsg.getId());
		sb.append(';');
		sb.append(String.join(",", winners.stream().map(x -> x.toString()).collect(Collectors.toList())));
		sb.append(';');
		sb.append(String.join(",", losers.stream().map(x -> x.toString()).collect(Collectors.toList())));
		sb.append(';');
		sb.append(scoreW);
		sb.append(';');
		sb.append(scoreL);
		sb.append(';');
		sb.append(valid);
		
		return sb.toString();
	}
}
