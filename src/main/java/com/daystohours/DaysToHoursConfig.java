package com.daystohours;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("daystohours")
public interface DaysToHoursConfig extends Config
{
	@ConfigItem(
		keyName = "days",
		name = "Days",
		description = "How many days to convert to hours"
	)
	default int days()
	{
		return 1;
	}
}
