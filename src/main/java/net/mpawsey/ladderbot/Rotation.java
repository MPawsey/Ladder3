package net.mpawsey.ladderbot;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

public class Rotation 
{

	private Message resultMsg;
	private List<Long> team1, team2;
	private boolean valid;
	
	public Rotation(Message resultMsg, List<Long> team1, List<Long> team2)
	{
		this.resultMsg = resultMsg;
		this.team1 = team1;
		this.team2 = team2;
	}
	
	public void setValid(boolean isValid)
	{
		valid = isValid;
	}
	
	public List<Long> getTeam1()
	{
		return team1;
	}
	
	public List<Long> getTeam2()
	{
		return team2;
	}
	
	public Message getResultMessage() {
		return resultMsg;
	}
	
	public Date getResultTime()
	{
		return Date.from(resultMsg.getTimeCreated().toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant());
	}
	
	public boolean isValid()
	{
		return valid;
	}
	
	public boolean checkResultValid()
	{
		List<User> t1 = resultMsg.retrieveReactionUsers(RotationMessageListener.TEAM1).complete();
		List<User> t2 = resultMsg.retrieveReactionUsers(RotationMessageListener.TEAM2).complete();
		List<User> c = resultMsg.retrieveReactionUsers(RotationMessageListener.CANCEL).complete();
		
		int t1Count = 0, t2Count = 0, cCount = 0;
		
		for (User user : t1)
		{
			if (team1.contains(user.getIdLong()) || team2.contains(user.getIdLong()))
				++t1Count;
		}
		
		for (User user : t2)
		{
			if (team1.contains(user.getIdLong()) || team2.contains(user.getIdLong()))
				++t2Count;
		}

		
		for (User user : c)
		{
			if (team1.contains(user.getIdLong()) || team2.contains(user.getIdLong()))
				++cCount;
		}
		
		
		if (cCount > 0 || (t1Count > 0 && t2Count > 0))
			return false;
		
		if (t1Count > (team1.size() + team2.size()) / 2 + 1)
			return true;
		if (t2Count > (team1.size() + team2.size()) / 2 + 1)
			return true;
		return false;
	}
}
