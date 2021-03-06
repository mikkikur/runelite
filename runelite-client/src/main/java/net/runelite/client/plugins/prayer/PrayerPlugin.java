/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Raqes <j.raqes@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.prayer;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@PluginDescriptor(
	name = "Prayer",
	description = "Show various information related to prayer",
	tags = {"combat", "flicking", "overlay"}
)
public class PrayerPlugin extends Plugin
{
	private final PrayerCounter[] prayerCounter = new PrayerCounter[PrayerType.values().length];

	@Inject
	private Client client;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PrayerFlickOverlay flickOverlay;

	@Inject
	private PrayerDoseOverlay doseOverlay;

	@Inject
	private PrayerConfig config;

	@Provides
	PrayerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrayerConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(flickOverlay);
		overlayManager.add(doseOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(flickOverlay);
		overlayManager.remove(doseOverlay);
		removeIndicators();
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("prayer"))
		{
			if (!config.prayerIndicator())
			{
				removeIndicators();
			}
			else if (!config.prayerIndicatorOverheads())
			{
				removeOverheadsIndicators();
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event)
	{
		final ItemContainer container = event.getItemContainer();
		final ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		final ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);

		if (container == inventory || container == equipment)
		{
			doseOverlay.setHasHolyWrench(false);
			doseOverlay.setHasPrayerPotion(false);
			doseOverlay.setHasRestorePotion(false);

			if (inventory != null)
			{
				checkContainerForPrayer(inventory.getItems());
			}

			if (equipment != null)
			{
				doseOverlay.setPrayerBonus(checkContainerForPrayer(equipment.getItems()));
			}

		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (config.prayerFlickHelper())
		{
			flickOverlay.onTick();
		}

		if (config.showPrayerDoseIndicator())
		{
			doseOverlay.onTick();
		}

		if (!config.prayerIndicator())
		{
			return;
		}

		for (PrayerType prayerType : PrayerType.values())
		{
			Prayer prayer = prayerType.getPrayer();
			int ord = prayerType.ordinal();

			if (client.isPrayerActive(prayer))
			{
				if (prayerType.isOverhead() && !config.prayerIndicatorOverheads())
				{
					continue;
				}

				if (prayerCounter[ord] == null)
				{
					PrayerCounter counter = prayerCounter[ord] = new PrayerCounter(this, prayerType);
					spriteManager.getSpriteAsync(prayerType.getSpriteID(), 0,
						counter::setImage);
					infoBoxManager.addInfoBox(counter);
				}
			}
			else if (prayerCounter[ord] != null)
			{
				infoBoxManager.removeInfoBox(prayerCounter[ord]);
				prayerCounter[ord] = null;
			}
		}
	}

	private int checkContainerForPrayer(Item[] items)
	{
		if (items == null)
		{
			return 0;
		}

		int total = 0;

		for (Item item : items)
		{
			if (item == null)
			{
				continue;
			}

			final PrayerRestoreType type = PrayerRestoreType.getType(item.getId());

			if (type != null)
			{
				switch (type)
				{
					case PRAYERPOT:
						doseOverlay.setHasPrayerPotion(true);
						break;
					case HOLYWRENCH:
						doseOverlay.setHasHolyWrench(true);
						break;
					case RESTOREPOT:
						doseOverlay.setHasRestorePotion(true);
						break;
				}
			}

			int bonus = PrayerItems.getItemPrayerBonus(item.getId());
			total += bonus;
		}

		return total;
	}

	private void removeIndicators()
	{
		infoBoxManager.removeIf(entry -> entry instanceof PrayerCounter);
	}

	private void removeOverheadsIndicators()
	{
		infoBoxManager.removeIf(entry -> entry instanceof PrayerCounter
			&& ((PrayerCounter) entry).getPrayerType().isOverhead());
	}
}
