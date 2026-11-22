package com.daystohours;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Time Played in Hours"
)
public class DaysToHoursPlugin extends Plugin
{
	private static final String TIME_PLAYED_PREFIX = "time played:";
	private static final String HOURS_TOKEN = "hour";
	private static final int CHARACTER_SUMMARY_GROUP_ID = ComponentID.CHARACTER_SUMMARY_CONTAINER >> 16;
	private static final Pattern TIME_PLAYED_PATTERN = Pattern.compile(
		"(?i)Time Played\\s*:\\s*(\\d+)\\s*day(?:s)?(?:\\s*,\\s*(\\d+)\\s*hour(?:s)?)?"
	);
	private static final Pattern TIME_PLAYED_HOURS_PATTERN = Pattern.compile(
		"(?i)Time Played\\s*:\\s*(\\d+)\\s*hour(?:s)?"
	);
	private static final Pattern TIME_PLAYED_LESS_THAN_ONE_HOUR_PATTERN = Pattern.compile(
		"(?i)Time Played\\s*:\\s*>\\s*1\\s*hour"
	);

	private final Map<Integer, String> originalTimePlayedTextByWidgetId = new HashMap<>();
	private boolean conversionErrorLogged;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Override
	protected void startUp()
	{
		originalTimePlayedTextByWidgetId.clear();
		conversionErrorLogged = false;
		clientThread.invokeLater(this::convertVisibleTimePlayedToHoursSafely);
		log.debug("Time Played in Hours started");
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(this::restoreVisibleTimePlayedTextSafely);
		originalTimePlayedTextByWidgetId.clear();
		log.debug("Time Played in Hours stopped");
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		convertVisibleTimePlayedToHoursSafely();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		convertVisibleTimePlayedToHoursSafely();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != CHARACTER_SUMMARY_GROUP_ID)
		{
			return;
		}

		convertVisibleTimePlayedToHoursSafely();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			originalTimePlayedTextByWidgetId.clear();
			conversionErrorLogged = false;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String optionRaw = event.getMenuOption();
		String targetRaw = event.getMenuTarget();
		if (optionRaw == null || targetRaw == null)
		{
			return;
		}

		String option = Text.removeTags(optionRaw);
		String target = Text.removeTags(targetRaw);

		if ("show".equalsIgnoreCase(option.trim()) && target.toLowerCase().contains("time played"))
		{
			clientThread.invokeLater(this::convertVisibleTimePlayedToHoursSafely);
		}
	}

	private void convertVisibleTimePlayedToHoursSafely()
	{
		try
		{
			convertVisibleTimePlayedToHours();
			conversionErrorLogged = false;
		}
		catch (Exception ex)
		{
			if (!conversionErrorLogged)
			{
				log.warn("Time Played in Hours conversion error (further repeats suppressed until recovery)", ex);
				conversionErrorLogged = true;
			}
		}
	}

	private void restoreVisibleTimePlayedTextSafely()
	{
		try
		{
			restoreVisibleTimePlayedText();
		}
		catch (Exception ex)
		{
			log.debug("Time Played in Hours restore skipped during shutdown", ex);
		}
	}

	private void convertVisibleTimePlayedToHours()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Widget container = client.getWidget(ComponentID.CHARACTER_SUMMARY_CONTAINER);
		if (container == null || container.isHidden())
		{
			return;
		}

		Integer latestTotalHours = findLatestTotalHoursFromDaysText(container);
		walkWidgetTree(container, widget -> convertTimePlayedTextToHours(widget, latestTotalHours));
	}

	private void restoreVisibleTimePlayedText()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Widget container = client.getWidget(ComponentID.CHARACTER_SUMMARY_CONTAINER);
		if (container == null || container.isHidden())
		{
			return;
		}

		walkWidgetTree(container, this::restoreTimePlayedText);
	}

	private void restoreTimePlayedText(Widget widget)
	{
		if (widget == null)
		{
			return;
		}

		String currentText = widget.getText();
		if (currentText != null)
		{
			String plainText = Text.removeTags(currentText);
			String original = originalTimePlayedTextByWidgetId.get(widget.getId());
			if (isConvertedHoursOnlyText(plainText))
			{
				if (original != null && !original.equals(currentText))
				{
					widget.setText(original);
				}
				else
				{
					String restored = restoreDaysTextFromHours(plainText);
					if (restored != null)
					{
						widget.setText(restored);
					}
				}
			}
		}
	}

	private void walkWidgetTree(Widget root, Consumer<Widget> visitor)
	{
		Deque<Widget> stack = new ArrayDeque<>();
		Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		stack.push(root);

		while (!stack.isEmpty())
		{
			Widget widget = stack.pop();
			if (widget == null || !visited.add(widget))
			{
				continue;
			}

			visitor.accept(widget);
			pushChildren(stack, widget.getChildren());
			pushChildren(stack, widget.getDynamicChildren());
			pushChildren(stack, widget.getStaticChildren());
			pushChildren(stack, widget.getNestedChildren());
		}
	}

	private void pushChildren(Deque<Widget> stack, Widget[] children)
	{
		if (children == null)
		{
			return;
		}

		for (Widget child : children)
		{
			if (child != null)
			{
				stack.push(child);
			}
		}
	}

	private Integer findLatestTotalHoursFromDaysText(Widget root)
	{
		final int[] latestTotalHours = { -1 };
		walkWidgetTree(root, widget ->
		{
			if (widget == null)
			{
				return;
			}

			String text = widget.getText();
			if (text == null || text.isEmpty())
			{
				return;
			}

			String plainText = Text.removeTags(text);
			Matcher matcher = TIME_PLAYED_PATTERN.matcher(plainText);
			if (!matcher.find())
			{
				return;
			}

			int days = Integer.parseInt(matcher.group(1));
			int extraHours = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
			int totalHours = days * 24 + extraHours;
			if (totalHours > latestTotalHours[0])
			{
				latestTotalHours[0] = totalHours;
			}
		});

		return latestTotalHours[0] >= 0 ? latestTotalHours[0] : null;
	}

	private void convertTimePlayedTextToHours(Widget widget, Integer latestTotalHours)
	{
		String currentText = widget.getText();
		if (currentText == null || currentText.isEmpty())
		{
			return;
		}

		String plainText = Text.removeTags(currentText);
		if (!isTimePlayedText(plainText))
		{
			return;
		}

		int totalHours;
		Matcher matcher = TIME_PLAYED_PATTERN.matcher(plainText);
		if (matcher.find())
		{
			int days = Integer.parseInt(matcher.group(1));
			int extraHours = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
			totalHours = days * 24 + extraHours;
		}
		else if (latestTotalHours != null)
		{
			totalHours = latestTotalHours;
		}
		else
		{
			return;
		}

		int widgetId = widget.getId();

		if (matcher.find(0))
		{
			originalTimePlayedTextByWidgetId.put(widgetId, currentText);
		}

		String newText = String.format("Time Played: <col=00cc00>%s</col>", formatHoursDisplay(totalHours));
		if (!Text.removeTags(newText).equals(Text.removeTags(currentText)))
		{
			widget.setText(newText);
		}
	}

	private String formatHoursDisplay(int totalHours)
	{
		if (totalHours <= 0)
		{
			return "> 1 hour";
		}

		if (totalHours == 1)
		{
			return "1 hour";
		}

		return totalHours + " hours";
	}

	private boolean isTimePlayedText(String text)
	{
		if (text == null)
		{
			return false;
		}

		return text.trim().toLowerCase().startsWith(TIME_PLAYED_PREFIX);
	}

	private boolean isConvertedHoursOnlyText(String plainText)
	{
		if (!isTimePlayedText(plainText))
		{
			return false;
		}

		Matcher hoursMatcher = TIME_PLAYED_HOURS_PATTERN.matcher(plainText);
		Matcher lessThanOneHourMatcher = TIME_PLAYED_LESS_THAN_ONE_HOUR_PATTERN.matcher(plainText);
		if (!hoursMatcher.find() && !lessThanOneHourMatcher.find())
		{
			return false;
		}

		Matcher daysMatcher = TIME_PLAYED_PATTERN.matcher(plainText);
		return !daysMatcher.find();
	}

	private String restoreDaysTextFromHours(String plainTimePlayedText)
	{
		Matcher lessThanOneHourMatcher = TIME_PLAYED_LESS_THAN_ONE_HOUR_PATTERN.matcher(plainTimePlayedText);
		if (lessThanOneHourMatcher.find())
		{
			return "Time Played: <col=00cc00>0 days, 0 hours</col>";
		}

		Matcher matcher = TIME_PLAYED_HOURS_PATTERN.matcher(plainTimePlayedText);
		if (!matcher.find())
		{
			return null;
		}

		int totalHours = Integer.parseInt(matcher.group(1));
		int days = totalHours / 24;
		int remainingHours = totalHours % 24;

		return String.format(
			"Time Played: <col=00cc00>%d day%s, %d hour%s</col>",
			days,
			days == 1 ? "" : "s",
			remainingHours,
			remainingHours == 1 ? "" : "s"
		);
	}
}
