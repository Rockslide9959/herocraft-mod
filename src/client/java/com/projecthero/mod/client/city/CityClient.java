package com.projecthero.mod.client.city;

import com.projecthero.mod.city.CityBlocks;

import net.minecraft.client.gui.screens.MenuScreens;

/** v0.16.2 City part B: client registrations (the Safe Deposit screen). */
public final class CityClient {
	private CityClient() {
	}

	public static void initialize() {
		MenuScreens.register(CityBlocks.SAFE_DEPOSIT_MENU, SafeDepositScreen::new);
	}
}
