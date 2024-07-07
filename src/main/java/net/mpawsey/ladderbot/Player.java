package net.mpawsey.ladderbot;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Player implements Comparable<Player>
{
	public static final DateFormat ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	public static final double STARTING_MMR = 1000;
	public static final String BALLCHASING_GROUP = "wrl-ladder-season-5-g6ugk2x4h9";
	
	private final long id;
	private String statsId = null;
	private double mmr;
	private int position;

	private int seriesTotal;
	private int seriesWins;
	private int gamesTotal;
	private int gamesWins;
	private int winstreak;
	private int highestWinstreak;
	private Date lastPlayed;
	private Double[] lastMmr = new Double[14];
	

	public Player(long id)
	{
		this.id = id;
		this.mmr = STARTING_MMR;
		this.lastMmr[0] = STARTING_MMR;
	}
	
	public Player(String player)
	{
		String[] segs = player.split(",");
		
		this.id = Long.parseLong(segs[0]);
		this.mmr = Double.parseDouble(segs[1]);
		
		this.seriesTotal = Integer.parseInt(segs[2]);
		this.seriesWins = Integer.parseInt(segs[3]);
		this.gamesTotal = Integer.parseInt(segs[4]);
		this.gamesWins = Integer.parseInt(segs[5]);
		this.winstreak = Integer.parseInt(segs[6]);
		this.highestWinstreak = Integer.parseInt(segs[7]);
		
		try {
			this.lastPlayed = ISO8601.parse(segs[8]);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		if (segs[8].equals("null"))
			this.statsId = null;
		else
			this.statsId = segs[9];
		
		for (int i = 0; i < lastMmr.length; ++i)
		{
			if (segs[i+10].equals("null"))
				this.lastMmr[i] = null;
			else
				this.lastMmr[i] = Double.parseDouble(segs[i+10]);
		}
	}
	
	public void addResult(Result result, double mmrChange, boolean isWinner)
	{
		++seriesTotal;
		if (isWinner)
			++seriesWins;
		
		gamesTotal += result.getWinningScore() + result.getLosingScore();
		gamesWins += (isWinner ? result.getWinningScore() : result.getLosingScore());
		
		if (isWinner)
		{
			if (++winstreak > highestWinstreak)
				highestWinstreak = winstreak;
		}
		else
			winstreak = 0;
		
		lastPlayed = result.getResultTime();
		mmr += mmrChange;
		if (mmr < 0)
		{
			mmr = 0;
		}
	}
	
	public void setPosition(int pos)
	{
		this.position = pos;
	}
	
	public int getPosition()
	{
		return position;
	}
	
	public long getId()
	{
		return id;
	}
	
	public double getMmr()
	{
		return mmr;
	}
	
	public int getLastPosition()
	{
		return position;
	}
	
	public int getSeriesTotal()
	{
		return seriesTotal;
	}
	
	public int getSeriesWins()
	{
		return seriesWins;
	}
	
	public int getGamesTotal()
	{
		return gamesTotal;
	}
	
	public int getGamesWins()
	{
		return gamesWins;
	}
	
	public int getWinstreak()
	{
		return winstreak;
	}
	
	public int getHighestWinstreak()
	{
		return highestWinstreak;
	}
	
	public Date getLastPlayed()
	{
		return lastPlayed;
	}
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(id);
		sb.append(',');
		sb.append(mmr);
		sb.append(',');
		sb.append(seriesTotal);
		sb.append(',');
		sb.append(seriesWins);
		sb.append(',');
		sb.append(gamesTotal);
		sb.append(',');
		sb.append(gamesWins);
		sb.append(',');
		sb.append(winstreak);
		sb.append(',');
		sb.append(highestWinstreak);
		sb.append(',');
		if (lastPlayed != null)
			sb.append(ISO8601.format(lastPlayed));
		sb.append(',');
		sb.append(statsId == null ? "null" : statsId);
		
		for (Double d : lastMmr)
		{
			sb.append(',');
			if (d == null)
				sb.append("null");
			else
				sb.append(d);
		}
		
		return sb.toString();
	}

	@Override
	public int compareTo(Player player) {
		double tmp = mmr - player.getMmr();
		return tmp < 0 ? -1 : tmp > 0 ? 1 : 0;
	}
	
	
	public void pushMmr()
	{
		for (int i = lastMmr.length - 1; i > 0; --i)
			lastMmr[i] = lastMmr[i-1];
		lastMmr[0] = mmr;
	}
	
	public Double[] getPreviousMmr()
	{
		return lastMmr;
	}
	
	
	public JSONObject getStats2()
	{
		if (statsId == null)
			return null;
		
		try {
			URL url = new URL("https://ballchasing.com/api/groups/" + BALLCHASING_GROUP);
			HttpURLConnection http = (HttpURLConnection)url.openConnection();
			http.setRequestMethod("GET");
			http.setDoInput(true);
			http.setRequestProperty("Accept-Type", "application/json");
			http.setRequestProperty("Authorization", "PKZwZLEmJ7Qfc7N8vUfUIpm0TYca1Jae0xNnGGwN");

			http.connect();
			if (http.getResponseCode() == 200)
			{
				InputStream in = http.getInputStream();
				
				JSONObject jo = new JSONObject(new JSONTokener(new InputStreamReader(in, "UTF-8")));
				JSONArray players = jo.getJSONArray("players");
				for (int i = 0; i < players.length(); ++i)
				{
					JSONObject obj = players.getJSONObject(i);
					
					if (obj.getString("id").equals(statsId))
							return obj;
				}
			}
			else
			{
				System.err.println("Error could not retrieve stats 2.0 (Code: " + http.getResponseCode() + ")");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public void setStatsId(String statsId)
	{
		this.statsId = statsId;
	}
	
	public String getStatsId()
	{
		return statsId;
	}
}
