package com.daystohours;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DaysToHoursPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(DaysToHoursPlugin.class);
		RuneLite.main(args);
	}
}
