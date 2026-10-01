package com.projecthero.mod.client.sorter;

import com.projecthero.mod.ironman.sorter.StarkSorter;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.gui.screens.MenuScreens;

/** v0.14.16: client registration for the Stark Sorting Station screen and the Sorter Bot renderer. */
public final class StarkSorterClient {
	private StarkSorterClient() {
	}

	public static void initialize() {
		MenuScreens.register(StarkSorter.STATION_MENU, SortingStationScreen::new);
		EntityRendererRegistry.register(StarkSorter.BOT, SorterBotRenderer::new);
	}
}
