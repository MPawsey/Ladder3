package net.mpawsey.bothelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.mpawsey.bothelper.CmdParam.Type;

class MessageReceivedAction
{
	Method method;
	double probability;
	boolean allowBotChat;
	long[] channelIds;
	long[] userIds;
	boolean requirePing;
	boolean ignorePing;
	
	MessageReceivedAction(Method method, double probability, boolean allowBotChat, long[] channelIds, long[] userIds, boolean requirePing, boolean ignorePing)
	{
		this.method = method;
		this.probability = probability;
		this.allowBotChat = allowBotChat;
		this.channelIds = channelIds;
		this.userIds = userIds;
		this.requirePing = requirePing;
		this.ignorePing = ignorePing;
	}
	
	public boolean isValid(long channelId, long userId, boolean isBot, boolean hasPing) {
		if ((isBot && !allowBotChat) || (requirePing && !hasPing) || (ignorePing && hasPing))
			return false;
		
		boolean valid = channelIds.length == 0;
		
		for (long id : channelIds) {
			if (id == channelId) {
				valid = true;
				break;
			}
		}

		if (valid)
			valid = userIds.length == 0;
		else
			return false;
		
		for (long id : userIds) {
			if (id == userId) {
				valid = true;
				break;
			}
		}
		
		return valid && Math.random() < probability;
	}
}

public abstract class AbstractMessageHandler extends ListenerAdapter
{
	private Map<String, MethodWrapper> cmds = new HashMap<String, MethodWrapper>();
	private List<MessageReceivedAction> actions = new ArrayList<MessageReceivedAction>();
	
	private List<AbstractMessageListener> messageListeners = new ArrayList<AbstractMessageListener>();
	
	private String botId;
	protected long messageLife;
	private boolean initialised = false;
	
	
	public final void initialise(String botId) throws DuplicateCommandException, InvalidMessageMethodException
	{
		initialise(botId, 30);
	}
	
	public final void initialise(String botId, long messageLife) throws DuplicateCommandException, InvalidMessageMethodException
	{
		this.botId = botId;
		this.messageLife = messageLife;
		this.initialised = true;
		
		System.out.println("Initialising message handler");
		
		for (Method m : this.getClass().getMethods())
		{
			Command cmd = (Command)m.getAnnotation(Command.class);
			if (cmd != null)
			{
				if (cmds.containsKey(cmd.value()))
					throw new DuplicateCommandException("Two commands have been made under the name " + cmd.value() + ". See '" + m.getName() + "' and '" + cmds.get(cmd.value()).method.getName() + "'.");
				
				CommandRequires req = (CommandRequires)m.getAnnotation(CommandRequires.class);
				
				System.out.println("Added command: " + cmd.value());
				cmds.put(cmd.value(), new MethodWrapper(m, req == null ? -1 : req.value()));
			}
			
			OnMessageReceived onMR = (OnMessageReceived)m.getAnnotation(OnMessageReceived.class);
			if (onMR != null)
			{
				if (onMR.requirePing() && onMR.ignorePing())
					throw new InvalidMessageMethodException("Require ping and ignore ping cannot both be true for the same method.");
				
				if (isMethodValid(m))
					actions.add(new MessageReceivedAction(m, 1.0, onMR.allowBotChat(), onMR.channelIds(), onMR.userIds(), onMR.requirePing(), onMR.ignorePing()));
				else
					throw new InvalidMessageMethodException("A message event must have all parameters with the @CmdParam annotation.");
				
				System.out.println("Added onMR: " + m.getName());
			}
			
			OnRandomMessageReceived onRMR = (OnRandomMessageReceived)m.getAnnotation(OnRandomMessageReceived.class);
			if (onRMR != null)
			{
				if (onRMR.requirePing() && onRMR.ignorePing())
					throw new InvalidMessageMethodException("Require ping and ignore ping cannot both be true for the same method.");
				
				if (isMethodValid(m))
					actions.add(new MessageReceivedAction(m, onRMR.value(), onRMR.allowBotChat(), onRMR.channelIds(), onRMR.userIds(), onRMR.requirePing(), onRMR.ignorePing()));
				else
					throw new InvalidMessageMethodException("A message event must have all parameters with the @CmdParam annotation.");
				
				System.out.println("Added onRMR: " + m.getName());
			}
		}
		System.out.println("Finished initialising");
	}
	
	private boolean isMethodValid(Method m)
	{
		for (Annotation[] ans : m.getParameterAnnotations())
		{
			boolean valid = false;
			for (Annotation an : ans)
			{
				if (an instanceof CmdParam)
				{
					valid = true;
					break;
				}
			}
			if (!valid)
				return false;
		}
		return true;
	}
	
	public final void parseMessage(MessageReceivedEvent mre) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, IllegalStateException
	{
		if (!initialised)
			throw new IllegalStateException("Message Handler not initialised.");
		
		MessageChannel channel = mre.getChannel();

		if (channel.getType().isGuild()) {
			Message message = mre.getMessage();
			String content = message.getContentRaw();
			String[] split = content.split("\\s+");

			Member member = mre.getMember();
			User user = member.getUser();
			
			if (split[0].matches("<@!?"+botId+">")) {
				if (cmds.containsKey(split[1]))
				{
					MethodWrapper m = cmds.get(split[1]);
					if (!m.hasPerm(mre.getAuthor().getId()))
					{
						System.out.println("Missing Perm");
						return;
					}
					
					Object[] args = new Object[m.method.getParameterCount()];
					
					for (int i = 0; i < args.length; ++i)
					{
						Parameter param = m.method.getParameters()[i];
						CmdParam cmdParam = (CmdParam)param.getAnnotation(CmdParam.class);
						
						switch (cmdParam.value())
						{
						case MRE:
							args[i] = mre;
							break;
						case ARGS:
							List<String> a = Arrays.asList(Arrays.copyOfRange(split, 2, split.length)), b = new ArrayList<String>();
							boolean newArg = true;
							String tmp = "";
							for (String s : a)
							{
								if (newArg)
								{
									if (s.startsWith("\""))
									{
										if (s.endsWith("\""))
											b.add(s.substring(1, s.length() - 1));
										else
										{
											newArg = false;
											tmp = s.substring(1);
										}
									}
									else
										b.add(s);
								}
								else if (s.endsWith("\""))
								{
									tmp += ' ' + s.substring(0, s.length() - 1);
									b.add(tmp);
									newArg = true;
								}
								else
								{
									tmp += ' ' + s;
								}
							}
							args[i] = b.toArray(new String[0]);
							
							break;
						case ARGS_RAW:
							StringBuilder sb =  new StringBuilder();
							for (int j = 2; j < split.length; ++j)
								sb.append(split[j]);
							args[i] = sb.toString();
							break;
						case CONTENT:
							args[i] = content;
							break;
						case MSG:
							args[i] = message;
							break;
						case AUTHOR_MEMBER:
							args[i] = member;
							break;
						case AUTHOR_USER:
							args[i] = user;
							break;
						case CHANNEL:
							args[i] = channel;
							break;
						case GUILD:
							args[i] = mre.getGuild();
							break;
						case NONE:
							args[i] = null;
						}
					}
					
					m.method.invoke(this, args);
				}
				else
				{
					onUnrecognisedCommand(message, channel, split[1]);
				}
			}
			
			for (MessageReceivedAction action : actions) {
				if (action.isValid(channel.getIdLong(), user.getIdLong(), user.isBot(), split[0].matches("<@!?"+botId+">"))) {
					Method m = action.method;
					Object[] args = new Object[m.getParameterCount()];
					
					for (int i = 0; i < args.length; ++i)
					{
						Parameter param = m.getParameters()[i];
						CmdParam cmdParam = (CmdParam)param.getAnnotation(CmdParam.class);
						
						switch (cmdParam.value())
						{
						case MRE:
							args[i] = mre;
							break;
						case ARGS:
							args[i] = Arrays.copyOfRange(split, 1, split.length);
							break;
						case ARGS_RAW:
							StringBuilder sb =  new StringBuilder();
							for (int j = 1; j < split.length; ++j)
								sb.append(split[j]);
							args[i] = sb.toString();
							break;
						case CONTENT:
							args[i] = content;
							break;
						case MSG:
							args[i] = message;
							break;
						case AUTHOR_MEMBER:
							args[i] = member;
							break;
						case AUTHOR_USER:
							args[i] = user;
							break;
						case CHANNEL:
							args[i] = channel;
							break;
						case GUILD:
							args[i] = mre.getGuild();
							break;
						case NONE:
							args[i] = null;
						}
					}
					
					m.invoke(this, args);
				}
			}
		}
	}
	
	public final void updateMessageListeners(GenericMessageEvent event)
	{
		System.out.println("Update " + event.getMessageId() + ", size: " + messageListeners.size() + ":");
		for (AbstractMessageListener msgListener : messageListeners)
			System.out.println("    " + msgListener.msg.getId());
		
		boolean isComplete = false;
		
		for (AbstractMessageListener msgListener : messageListeners)
		{
			System.out.println("Checking " + msgListener.msg.getId());
			if (!msgListener.isMessage(event.getMessageIdLong()))
			{
				System.out.println("Is not " + msgListener.msg.getId());
				continue;
			}
			
			System.out.println("Is Message");
			
			if (event instanceof MessageUpdateEvent)
			{
				System.out.println("UPDATE EVENT");
				msgListener.onMessageEdited((MessageUpdateEvent)event);
				isComplete = msgListener.isComplete();
			}
			else if (event instanceof MessageDeleteEvent)
			{
				System.out.println("DELETE EVENT");
				msgListener.onMessageDeleted((MessageDeleteEvent)event);
				isComplete = msgListener.isComplete();
			}
			else if (event instanceof MessageReactionAddEvent)
			{
				System.out.println("REACTION ADD EVENT");
				msgListener.onReactionAdded((MessageReactionAddEvent)event);
				isComplete = msgListener.isComplete();
			}
			else if (event instanceof MessageReactionRemoveEvent)
			{
				System.out.println("REACTION REMOVE EVENT");
				msgListener.onReactionRemoved((MessageReactionRemoveEvent)event);
				isComplete = msgListener.isComplete();
			}
			else
			{
				System.err.println("Unknown message listener event " + event.getClass().getName());
			}
		}
		
		
		if (isComplete)
		{
			System.out.println("No longer listening to " + event.getMessageIdLong());
			removeMessageListener(event.getMessageIdLong());
		}
	}
	
	
	public final void addMessageListener(AbstractMessageListener msgListener)
	{
		System.out.println("Listening " + msgListener.msg.getId());
		messageListeners.add(msgListener);
		System.out.println("Now at " + messageListeners.size());
	}
	
	public final void removeMessageListener(AbstractMessageListener msgListener)
	{
		System.out.println("No longer listening " + msgListener.msg.getId());
		messageListeners.remove(msgListener);
		System.out.println("Now at " + messageListeners.size());
	}
	
	public final void removeMessageListener(long msgId)
	{
		for (int i = 0; i < messageListeners.size(); ++i)
		{
			if (messageListeners.get(i).isMessage(msgId))
			{
				System.out.println("No longer listening " + msgId);
				messageListeners.remove(i);
				System.out.println("Now at " + messageListeners.size());
				return;
			}
		}
	}
	
	public final void removeMessageListener(Message msg)
	{
		for (int i = 0; i < messageListeners.size(); ++i)
		{
			if (msg.equals(messageListeners.get(i).msg))
			{
				System.out.println("No longer listening " + msg.getId());
				messageListeners.remove(i);
				System.out.println("Now at " + messageListeners.size());
				return;
			}
		}
	}
	
	@Command("help")
	@CommandDescription(usage="help [command]", description="Shows all commands or the description of the given command.")
	public final void helpCommand(@CmdParam(Type.CHANNEL) MessageChannel channel, @CmdParam(Type.ARGS) String[] args, @CmdParam(Type.AUTHOR_USER) User user)
	{
		if (args.length > 1)
			MessageHelper.sendError("`help` can only have 0 or 1 arguements. You have entered " + args.length, channel, messageLife);
		else if (args.length == 1)
		{
			if (cmds.containsKey(args[0]))
			{
				if (cmds.get(args[0]).hasPerm(user.getId()))
				{
					CommandDescription tmp = cmds.get(args[0]).method.getAnnotation(CommandDescription.class);
					if (tmp != null)
						MessageHelper.sendMessage("Usage: `" + tmp.usage() + "`\n" + tmp.description(), channel);
					else
						MessageHelper.sendMessage('`' + args[0] + "` does not have a description set.", channel);
				}
				else
					MessageHelper.sendMessage("You do not have the permissions to use `" + args[0] + "`", channel);
				
			}
			else
				MessageHelper.sendError("Could not find command `" + args[0] + "`.", channel, messageLife);
		}
		else
		{
			EmbedBuilder eb = new EmbedBuilder();
			eb.setTitle("Commands");
			for (Entry<String, MethodWrapper> e : cmds.entrySet())
			{
				if (e.getValue().hasPerm(user.getId()))
				{
					CommandDescription tmp = e.getValue().method.getAnnotation(CommandDescription.class);
					eb.addField(e.getKey(), tmp == null ? "" : tmp.description(), false);
				}
			}
			channel.sendMessage(eb.build()).queue();
		}
		
	}
	
	public void onUnrecognisedCommand(Message msg, MessageChannel channel, String command)
	{
		msg.delete().queue();
		MessageHelper.sendError("Did not recognise command `" + command + "`.", channel, messageLife);
	}
	
	private class MethodWrapper
	{
		public Method method;
		public int[] perms;
		
		public MethodWrapper(Method method, int perm)
		{
			this.method = method;
			
			ArrayList<Integer> permsList = new ArrayList<Integer>();
			int i = 0;
			while (perm != 0)
			{
				if (perm % 2 == 1)
					permsList.add(i);
				perm /= 2;
			}
			perms = permsList.stream().mapToInt(x -> x).toArray();
		}
		
		public boolean hasPerm(String userId)
		{
			for (int i : perms)
			{
				if (!FileManager.hasPermission(perms[i], userId))
					return false;
			}
			return true;
		}
		
	}
	
	@Override
	public void onMessageReceived(MessageReceivedEvent mre) 
	{
		try {
			parseMessage(mre);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onMessageUpdate(MessageUpdateEvent event)
	{
		updateMessageListeners(event);
	}
	

	@Override
	public void onMessageDelete(MessageDeleteEvent event)
	{
		updateMessageListeners(event);
	}

	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event)
	{
		updateMessageListeners(event);
	}

	@Override
	public void onMessageReactionRemove(MessageReactionRemoveEvent event)
	{
		updateMessageListeners(event);
	}
	
}
