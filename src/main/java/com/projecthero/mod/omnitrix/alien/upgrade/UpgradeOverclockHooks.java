package com.projecthero.mod.omnitrix.alien.upgrade;

import com.projecthero.mod.firearm.AmmoKind;
import com.projecthero.mod.firearm.FirearmData;
import com.projecthero.mod.firearm.FirearmHooks;
import com.projecthero.mod.firearm.ShotSpec;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 Upgrade revamp (Overclock's weapon boost -- Ultimate Upgrade): an overclocked gun from the mod's firearm engine fires
 * 50% faster. Wraps whatever hooks are installed (the Punisher's, his Weapon Abilities') once the server starts -- after every
 * mod init has installed its own -- and passes everything else straight through. The "no reload" half is
 * {@code UpgradeKit.tickV} keeping the magazine full.
 */
final class UpgradeOverclockHooks implements FirearmHooks {
	/** The fire interval while overclocked: 1 / 1.5. */
	static final float FIRE_INTERVAL = 1f / 1.5f;
	private static boolean installed;

	private final FirearmHooks inner;

	private UpgradeOverclockHooks(FirearmHooks inner) {
		this.inner = inner;
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (!installed) {
				installed = true;
				FirearmHooks.install(new UpgradeOverclockHooks(FirearmHooks.get()));
			}
		});
	}

	@Override
	public float fireIntervalFactor(ServerPlayer player) {
		float f = inner.fireIntervalFactor(player);
		return UpgradeKit.overclocked(player) ? f * FIRE_INTERVAL : f;
	}

	@Override
	public boolean usesPersonalReserve(ServerPlayer player) {
		return inner.usesPersonalReserve(player);
	}

	@Override
	public int personalReserveCount(ServerPlayer player, AmmoKind kind) {
		return inner.personalReserveCount(player, kind);
	}

	@Override
	public int personalReserveTake(ServerPlayer player, AmmoKind kind, int want) {
		return inner.personalReserveTake(player, kind, want);
	}

	@Override
	public float reloadSpeedFactor(ServerPlayer player) {
		return inner.reloadSpeedFactor(player);
	}

	@Override
	public float spreadFactor(ServerPlayer player, boolean aiming) {
		return inner.spreadFactor(player, aiming);
	}

	@Override
	public float recoilFactor(ServerPlayer player) {
		return inner.recoilFactor(player);
	}

	@Override
	public float damageFactor(ServerPlayer player, LivingEntity target, boolean headshot) {
		return inner.damageFactor(player, target, headshot);
	}

	@Override
	public void onFired(ServerPlayer player, String weaponId, boolean aiming) {
		inner.onFired(player, weaponId, aiming);
	}

	@Override
	public void onHit(ServerPlayer player, LivingEntity target, boolean headshot) {
		inner.onHit(player, target, headshot);
	}

	@Override
	public void onHeadshot(ServerPlayer player, LivingEntity target) {
		inner.onHeadshot(player, target);
	}

	@Override
	public void onFirearmKill(ServerPlayer player, LivingEntity target) {
		inner.onFirearmKill(player, target);
	}

	@Override
	public ShotSpec nextShot(ServerPlayer player, FirearmData data) {
		return inner.nextShot(player, data);
	}

	@Override
	public boolean reloadBlocked(ServerPlayer player) {
		return inner.reloadBlocked(player);
	}

	@Override
	public void onCrafted(ServerPlayer player, String weaponId) {
		inner.onCrafted(player, weaponId);
	}
}
