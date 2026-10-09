package com.projecthero.mod.firearm;

import java.util.function.Consumer;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18: a one-off override of a single trigger pull, so an ability's special shot still goes through the real
 * firing path ({@link FirearmShooting#fire(net.minecraft.server.level.ServerPlayer, net.minecraft.world.item.ItemStack,
 * FirearmData, ShotSpec)}: ammo, sounds, muzzle flash, tracers, recoil, headshots, every damage hook) instead of faking
 * damage. Built by the Punisher's Weapon Abilities (V); a normal shot asks {@link FirearmHooks#nextShot} for one and
 * gets null. Every field defaults to "as the gun's own data says".
 */
public final class ShotSpec {
	/** Fire along this direction instead of the shooter's look (auto-aim). Null = the look. */
	public Vec3 aimDir;
	/** Multiplier on the shot's spread (rest + recoil). */
	public float spreadFactor = 1.0f;
	/** &gt; 0: the pellets are fanned evenly across this total horizontal arc, in degrees (a wide shotgun blast). */
	public float fanDegrees;
	/** Pellets in this pull; -1 = the gun's own count. */
	public int pellets = -1;
	/** Rounds this pull takes out of the magazine (it refuses with fewer loaded). */
	public int ammoCost = 1;
	/** Max range in blocks; -1 = the gun's own. */
	public double range = -1;
	/** Fixed per-pellet damage for a body / head hit; -1 = the gun's own. */
	public float bodyDamage = -1f;
	public float headDamage = -1f;
	/** Multiplier on the damage, folded in next to {@link FirearmHooks#damageFactor}. */
	public float damageFactor = 1.0f;
	/** Every hit counts (and is paid) as a headshot. */
	public boolean forceHeadshot;
	/** The round passes through every living thing along its line instead of stopping at the first. */
	public boolean pierce;
	/** Fraction (0..1) of each target's armour value the round ignores. */
	public float armorIgnore;
	/** Skip the fire-rate / pump / bolt gate -- the ability fires right now. */
	public boolean ignoreFireRate;
	/** Extra effect on every living target the shot damages. */
	public Consumer<LivingEntity> onHit;
	/** Runs once the shot has actually fired (ammo spent). */
	public Runnable onFired;
}
