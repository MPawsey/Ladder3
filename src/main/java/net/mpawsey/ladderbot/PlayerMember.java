package net.mpawsey.ladderbot;

class PlayerMember implements Comparable<PlayerMember>
{
	final Player player;
	final String name;
	final String discord;
	
	public PlayerMember(Player player, String name, String discord)
	{
		this.player = player;
		this.name = name;
		this.discord = discord;
	}

	@Override
	public int compareTo(PlayerMember o) {
		int result = -player.compareTo(o.player);
		if (result == 0)
			return o.player.getId() < player.getId() ? 1 : -1;
		return result;
	}
}