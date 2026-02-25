package com.gielinoreats;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gielinoreats")
public interface GielinorEatsConfig extends Config
{
	@ConfigItem(
		keyName = "pluginToken",
		name = "Plugin Token",
		description = "Auto-generated token linking this client to your web account. Do not share.",
		secret = true,
		hidden = true
	)
	default String pluginToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "notifyRunnerAccepted",
		name = "Notify: Runner Accepted",
		description = "Desktop notification when a runner accepts your order."
	)
	default boolean notifyRunnerAccepted()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyRunnerNear",
		name = "Notify: Runner Near",
		description = "Desktop notification when a runner is nearby."
	)
	default boolean notifyRunnerNear()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyOrderCancelled",
		name = "Notify: Order Cancelled",
		description = "Desktop notification when your order is cancelled."
	)
	default boolean notifyOrderCancelled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyOrderCompleted",
		name = "Notify: Order Completed",
		description = "Desktop notification when your order is completed."
	)
	default boolean notifyOrderCompleted()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyCompetingRunnerCompleted",
		name = "Notify: Competing Runner Completed",
		description = "Desktop notification when another runner completes an order you accepted."
	)
	default boolean notifyCompetingRunnerCompleted()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shortestPathWarningDismissed",
		name = "",
		description = "",
		hidden = true
	)
	default boolean shortestPathWarningDismissed()
	{
		return false;
	}
}
