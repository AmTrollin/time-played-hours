package com.daystohours;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Days to Hours"
)
public class DaysToHoursPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private DaysToHoursConfig config;

	@Override
	protected void startUp()
	{
		log.debug("Days to Hours started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Days to Hours stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int days = Math.max(0, config.days());
		int hours = days * 24;
		client.addChatMessage(
			ChatMessageType.GAMEMESSAGE,
			"",
			String.format("Days to Hours: %d day%s = %d hour%s", days, days == 1 ? "" : "s", hours, hours == 1 ? "" : "s"),
			null
		);
	}

	@Provides
	DaysToHoursConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DaysToHoursConfig.class);
	}
}
