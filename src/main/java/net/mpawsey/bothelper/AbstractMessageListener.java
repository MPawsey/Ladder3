package net.mpawsey.bothelper;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;

public abstract class AbstractMessageListener 
{
	protected Message msg;
	private boolean complete = false;
	
	public AbstractMessageListener(Message msg)
	{
		this.msg = msg;
	}
	
	public final boolean isMessage(Message msg)
	{
		return this.msg.equals(msg);
	}
	
	public final boolean isMessage(long id)
	{
		return this.msg.getIdLong() == id;
	}
	
	public final boolean isComplete()
	{
		return complete;
	}
	
	public final void stopListening()
	{
		complete = true;
		System.out.println("stopListening");
	}
	
	public void onMessageEdited(MessageUpdateEvent event) {}
	
	public void onMessageDeleted(MessageDeleteEvent event) {}
	
	public void onReactionAdded(MessageReactionAddEvent event) {}

	public void onReactionRemoved(MessageReactionRemoveEvent event) {}
	
}
