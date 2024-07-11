package net.mpawsey.bothelper;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnRandomMessageReceived {
	public double value();
	public boolean allowBotChat() default false;
	public long[] channelIds() default {};
	public long[] userIds() default {};
	// Require a message to start with @BOT
	public boolean requirePing() default false;
	// Don't listen to a message starting with @BOT
	public boolean ignorePing() default false;
}
