package net.mpawsey.bothelper;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CmdParam 
{
	public enum Type
	{
		MRE,			// Message Received Event
		ARGS,			// Arguments of the command as String[]
		ARGS_RAW,		// Arguments of the command as String
		CONTENT,		// The full message
		MSG,			// The message that triggered the command
		AUTHOR_MEMBER,	// The author of the command as a member
		AUTHOR_USER,	// The author of the command as a user
		CHANNEL,		// The channel the message came from
		GUILD,			// The guild the message came from
		NONE			// null is passed through. Should only be used if this method will be called by something else
	};
	
	public Type value();
}
