package com.projecthero.mod.punisher.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.firearm.AmmoKind;
import com.projecthero.mod.firearm.FirearmData;
import com.projecthero.mod.firearm.FirearmHooks;
import com.projecthero.mod.firearm.FirearmReload;
import com.projecthero.mod.firearm.FirearmShooting;
import com.projecthero.mod.firearm.FirearmStack;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.firearm.GunSounds;
import com.projecthero.mod.firearm.ShotSpec;
import com.projecthero.mod.network.PunisherLockOnPayload;
import com.projecthero.mod.punisher.Punisher;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18: the Punisher's <b>Weapon Ability</b> (V). What it does depends on the gun in the main hand; plain V and
 * Shift+V are two moves with their own cooldowns. Every shot goes through the real firing path
 * ({@link FirearmShooting#fire(ServerPlayer, ItemStack, FirearmData, ShotSpec)} -- ammo, sound, flash, tracers,
 * recoil, headshots, every damage hook); the damage changes are {@link ShotSpec} fields folded into that maths.
 *
 * <pre>
 *   Pistol    V  next 3 shots deal double damage (12 s)
 *             Shift+V  one shot right now: 18 damage + Slowness II for 4 s (20 s)
 *   Shotgun   V  3 shells in one blast, 15 per pellet, 5 blocks (12 s; needs 3 loaded)
 *             Shift+V  3 shells in a 90 degree fan of 12 pellets, 10 per pellet, 5 blocks (20 s)
 *   Rifle     V  double fire rate + half reload time for 15 s (20 s)
 *             Shift+V  lock onto the target under the crosshair (40 blocks) and auto-fire the magazine into it;
 *                      the camera tracks it and reloading is blocked until it ends (25 s)
 *   Sniper    V  next shot pierces everything on its line and ignores 40% of armour (12 s)
 *             Shift+V  Steady Shot: scope in and hold steady for 3 s -- no auto-aim, the player aims -- then one shot
 *                      fires along their own look and pays double headshot damage wherever it lands (30 s)
 * </pre>
 *
 * Anything still running (the pistol's charged shots, rapid fire, an armed piercing round, a lock-on) ends the moment
 * the player puts that gun away -- another hotbar slot or another item. Transient: nothing here is saved.
 */
public final class PunisherWeaponAbilities {
	// ---- tuning (ticks / damage) ----
	public static final int PISTOL_CD = 240;
	public static final int PISTOL_SHIFT_CD = 400;
	public static final int PISTOL_CHARGES = 3;
	public static final float PISTOL_CHARGE_FACTOR = 2.0f;
	public static final float PISTOL_SHOT_DAMAGE = 18f;
	public static final int PISTOL_SLOW_TICKS = 80;
	public static final int PISTOL_SLOW_AMP = 1; // Slowness II

	public static final int SHOTGUN_CD = 240;
	public static final int SHOTGUN_SHIFT_CD = 400;
	public static final int SHOTGUN_SHELLS = 3;
	public static final float SHOTGUN_PELLET_DAMAGE = 15f;
	public static final float SHOTGUN_WIDE_PELLET_DAMAGE = 10f;
	public static final int SHOTGUN_WIDE_PELLETS = 12;
	public static final float SHOTGUN_WIDE_DEGREES = 90f;
	public static final double SHOTGUN_RANGE = 5.0;

	public static final int RIFLE_CD = 400;
	public static final int RIFLE_SHIFT_CD = 500;
	public static final int RIFLE_RAPID_TICKS = 300;
	public static final float RIFLE_RAPID_FACTOR = 0.5f; // fire interval and reload time
	public static final double RIFLE_LOCK_RANGE = 40.0;
	public static final float RIFLE_LOCK_SPREAD = 0.25f;

	public static final int SNIPER_CD = 240;
	public static final int SNIPER_SHIFT_CD = 600;
	public static final float SNIPER_ARMOR_IGNORE = 0.4f;
	/** Shift+V Steady Shot: the wait before the round goes, and its multiplier on the gun's headshot damage. */
	public static final int SNIPER_STEADY_TICKS = 60;
	public static final float SNIPER_STEADY_FACTOR = 2.0f;

	/** The near-the-crosshair cone a lock-on searches when nothing is right under it. */
	private static final double LOCK_CONE_COS = Math.cos(Math.toRadians(10.0));

	/** What is running for one player, all bound to one gun (its id + hotbar slot). */
	private static final class Active {
		final String weaponId;
		final int slot;
		int pistolCharges;
		long rapidUntil;
		boolean sniperPierce;
		int lockTarget = -1;
		/** Sniper Steady Shot: game time the round fires, or 0 while none is lined up. */
		long steadyFireAt;
		boolean forcedAim;

		Active(String weaponId, int slot) {
			this.weaponId = weaponId;
			this.slot = slot;
		}

		boolean idle(long now) {
			return pistolCharges <= 0 && rapidUntil <= now && !sniperPierce && lockTarget < 0 && steadyFireAt == 0L;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

	private PunisherWeaponAbilities() {
	}

	/** Called once from {@code ProjectHeroMod} right after {@code Punisher.initialize()} (which installs the hooks we wrap). */
	public static void initialize() {
		FirearmHooks.install(new Hooks(FirearmHooks.get()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.getPlayer().getUUID()));
	}

	public static void clearSessionState() {
		ACTIVE.clear();
	}

	// ---------------------------------------------------------------- input

	public static void handle(ServerPlayer player, boolean pressed) {
		if (!pressed || !Punisher.hasPower(player)) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		FirearmData data = Firearms.of(held);
		if (data == null) {
			message(player, "hold_gun", ChatFormatting.GRAY);
			return;
		}
		boolean shift = player.isShiftKeyDown();
		String cd = cooldownId(data.id, shift);
		if (cd == null) {
			message(player, "hold_gun", ChatFormatting.GRAY);
			return;
		}
		if (!Punisher.abilityReady(player, cd)) {
			player.displayClientMessage(Component.translatable("message.projecthero.punisher.weapon.cooldown",
					(Punisher.cooldownRemaining(player, cd) + 19) / 20).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		switch (data.id) {
			case Firearms.PISTOL -> {
				if (shift) {
					pistolShot(player, held, data);
				} else {
					pistolCharge(player);
				}
			}
			case Firearms.SHOTGUN -> shotgunBlast(player, held, data, shift);
			case Firearms.RIFLE -> {
				if (shift) {
					lockOn(player, held, data);
				} else {
					startRapidFire(player);
				}
			}
			case Firearms.SNIPER -> {
				if (shift) {
					steadyShot(player, held, data);
				} else {
					armPiercing(player);
				}
			}
			default -> message(player, "hold_gun", ChatFormatting.GRAY);
		}
	}

	// ---------------------------------------------------------------- the moves

	private static void pistolCharge(ServerPlayer player) {
		Active a = bind(player, Firearms.PISTOL);
		a.pistolCharges = PISTOL_CHARGES;
		Punisher.triggerCooldown(player, cooldownId(Firearms.PISTOL, false), PISTOL_CD);
		sound(player, GunSounds.RACK, 1.0f, 0.8f);
		message(player, "pistol_charged", ChatFormatting.GOLD);
	}

	private static void pistolShot(ServerPlayer player, ItemStack held, FirearmData data) {
		if (!canFireNow(player, held, data, 1)) {
			return;
		}
		ShotSpec spec = new ShotSpec();
		spec.bodyDamage = PISTOL_SHOT_DAMAGE;
		spec.headDamage = PISTOL_SHOT_DAMAGE;
		spec.ignoreFireRate = true;
		spec.onHit = t -> t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
				PISTOL_SLOW_TICKS, PISTOL_SLOW_AMP, false, true, true));
		if (FirearmShooting.fire(player, held, data, spec) == FirearmShooting.Result.FIRED) {
			Punisher.triggerCooldown(player, cooldownId(Firearms.PISTOL, true), PISTOL_SHIFT_CD);
		}
	}

	private static void shotgunBlast(ServerPlayer player, ItemStack held, FirearmData data, boolean wide) {
		if (FirearmStack.magazine(held, data) < SHOTGUN_SHELLS) {
			message(player, "need_shells", ChatFormatting.RED);
			return;
		}
		ShotSpec spec = new ShotSpec();
		spec.ammoCost = SHOTGUN_SHELLS;
		spec.range = SHOTGUN_RANGE;
		spec.ignoreFireRate = true;
		float dmg = wide ? SHOTGUN_WIDE_PELLET_DAMAGE : SHOTGUN_PELLET_DAMAGE;
		spec.bodyDamage = dmg;
		spec.headDamage = dmg;
		if (wide) {
			spec.pellets = SHOTGUN_WIDE_PELLETS;
			spec.fanDegrees = SHOTGUN_WIDE_DEGREES;
		}
		if (FirearmShooting.fire(player, held, data, spec) == FirearmShooting.Result.FIRED) {
			Punisher.triggerCooldown(player, cooldownId(Firearms.SHOTGUN, wide), wide ? SHOTGUN_SHIFT_CD : SHOTGUN_CD);
		}
	}

	private static void startRapidFire(ServerPlayer player) {
		Active a = bind(player, Firearms.RIFLE);
		a.rapidUntil = player.level().getGameTime() + RIFLE_RAPID_TICKS;
		Punisher.triggerCooldown(player, cooldownId(Firearms.RIFLE, false), RIFLE_CD);
		sound(player, GunSounds.RACK, 1.0f, 1.3f);
		message(player, "rifle_rapid", ChatFormatting.GOLD);
	}

	private static void armPiercing(ServerPlayer player) {
		Active a = bind(player, Firearms.SNIPER);
		a.sniperPierce = true;
		Punisher.triggerCooldown(player, cooldownId(Firearms.SNIPER, false), SNIPER_CD);
		sound(player, GunSounds.BOLT, 1.0f, 0.8f);
		message(player, "sniper_piercing", ChatFormatting.GOLD);
	}

	/** Assault Rifle Shift+V: lock onto the target under the crosshair and auto-fire the magazine into it. */
	private static void lockOn(ServerPlayer player, ItemStack held, FirearmData data) {
		if (FirearmStack.magazine(held, data) <= 0) {
			message(player, "empty", ChatFormatting.RED);
			return;
		}
		LivingEntity target = acquire(player, RIFLE_LOCK_RANGE);
		if (target == null) {
			message(player, "no_target", ChatFormatting.GRAY);
			return;
		}
		FirearmReload.cancel(held); // a rifle mid-reload with rounds still in it: the lock fires those first
		Active a = bind(player, data.id);
		a.lockTarget = target.getId();
		ServerPlayNetworking.send(player, new PunisherLockOnPayload(target.getId(), false));
		Punisher.triggerCooldown(player, cooldownId(data.id, true), RIFLE_SHIFT_CD);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 2.0f);
		message(player, "rifle_lock", ChatFormatting.GOLD);
	}

	/**
	 * Sniper Shift+V, Steady Shot (playtest v0.15.18: no auto-aim): raise the scope if it is not up and hold steady for
	 * {@link #SNIPER_STEADY_TICKS}; the player keeps full control of the camera the whole time. Then one round fires
	 * along their own look, paid as a headshot at {@link #SNIPER_STEADY_FACTOR}x wherever it lands. Reloading waits.
	 */
	private static void steadyShot(ServerPlayer player, ItemStack held, FirearmData data) {
		if (FirearmStack.magazine(held, data) <= 0) {
			message(player, "empty", ChatFormatting.RED);
			return;
		}
		if (FirearmStack.isReloading(held)) {
			message(player, "reloading", ChatFormatting.GRAY);
			return;
		}
		Active a = bind(player, data.id);
		a.steadyFireAt = player.level().getGameTime() + SNIPER_STEADY_TICKS;
		if (!player.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false)) {
			player.setAttached(ModAttachments.FIREARM_AIMING, true);
			a.forcedAim = true;
			ServerPlayNetworking.send(player, new PunisherLockOnPayload(PunisherLockOnPayload.SCOPE_ONLY, true));
		}
		Punisher.triggerCooldown(player, cooldownId(data.id, true), SNIPER_SHIFT_CD);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
		message(player, "sniper_steady", ChatFormatting.GOLD);
	}

	// ---------------------------------------------------------------- tick

	/** Every server tick for every player (from {@code ProjectHeroMod.tickPlayerSystems}); a no-op unless something runs. */
	public static void tick(ServerPlayer player) {
		Active a = ACTIVE.get(player.getUUID());
		if (a == null) {
			return;
		}
		if (!player.isAlive() || !Punisher.hasPower(player)) {
			cancel(player, false);
			return;
		}
		ItemStack held = player.getMainHandItem();
		FirearmData data = Firearms.of(held);
		if (data == null || !data.id.equals(a.weaponId) || player.getInventory().selected != a.slot) {
			cancel(player, true); // swapped or put the gun away
			return;
		}
		long now = player.level().getGameTime();
		if (a.rapidUntil != 0L && now >= a.rapidUntil) {
			a.rapidUntil = 0L;
		}
		if (a.lockTarget >= 0) {
			tickLock(player, a, held, data, now);
		}
		if (a.steadyFireAt != 0L && now >= a.steadyFireAt) {
			fireSteadyShot(player, a, held, data);
		}
		if (a.idle(now) && ACTIVE.get(player.getUUID()) == a) {
			ACTIVE.remove(player.getUUID());
		}
	}

	private static void tickLock(ServerPlayer player, Active a, ItemStack held, FirearmData data, long now) {
		Entity e = player.level().getEntity(a.lockTarget);
		double keep = RIFLE_LOCK_RANGE + 8.0;
		if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceTo(player) > keep || !player.hasLineOfSight(t)) {
			endLock(player, a);
			if (!(e instanceof LivingEntity dead) || dead.isAlive()) { // a kill needs no "lost" note
				message(player, "lock_lost", ChatFormatting.GRAY);
			}
			return;
		}
		Vec3 dir = aimPoint(t).subtract(player.getEyePosition());
		if (FirearmStack.magazine(held, data) > 0) {
			ShotSpec spec = new ShotSpec();
			spec.aimDir = dir;
			spec.spreadFactor = RIFLE_LOCK_SPREAD;
			FirearmShooting.fire(player, held, data, spec);
		}
		if (FirearmStack.magazine(held, data) <= 0 || !t.isAlive()) {
			endLock(player, a);
		}
	}

	/** The Steady Shot's round: along the player's own look, no spread, every hit paid as a doubled headshot. */
	private static void fireSteadyShot(ServerPlayer player, Active a, ItemStack held, FirearmData data) {
		ShotSpec spec = new ShotSpec();
		spec.spreadFactor = 0f;
		spec.forceHeadshot = true;
		spec.damageFactor = SNIPER_STEADY_FACTOR;
		spec.ignoreFireRate = true;
		if (a.sniperPierce) { // an armed piercing round is the next shot -- this one
			spec.pierce = true;
			spec.armorIgnore = SNIPER_ARMOR_IGNORE;
			spec.onFired = () -> a.sniperPierce = false;
		}
		if (FirearmShooting.fire(player, held, data, spec) != FirearmShooting.Result.FIRED) {
			message(player, "empty", ChatFormatting.RED);
		}
		endSteady(player, a);
	}

	private static void endSteady(ServerPlayer player, Active a) {
		if (a.steadyFireAt == 0L) {
			return;
		}
		a.steadyFireAt = 0L;
		if (a.forcedAim) {
			a.forcedAim = false;
			player.setAttached(ModAttachments.FIREARM_AIMING, false);
			ServerPlayNetworking.send(player, new PunisherLockOnPayload(-1, false));
		}
	}

	private static void endLock(ServerPlayer player, Active a) {
		if (a.lockTarget < 0) {
			return;
		}
		a.lockTarget = -1;
		ServerPlayNetworking.send(player, new PunisherLockOnPayload(-1, false));
	}

	/** Drop everything running for this player; {@code announce} = tell them it was cancelled (a weapon swap). */
	public static void cancel(ServerPlayer player, boolean announce) {
		Active a = ACTIVE.remove(player.getUUID());
		if (a == null) {
			return;
		}
		boolean running = !a.idle(player.level().getGameTime());
		endLock(player, a);
		endSteady(player, a);
		if (announce && running) {
			message(player, "cancelled", ChatFormatting.GRAY);
		}
	}

	// ---------------------------------------------------------------- hook support

	/** The special shot armed for the next ordinary pull of {@code data}, if any. */
	static ShotSpec nextShot(ServerPlayer player, FirearmData data) {
		Active a = ACTIVE.get(player.getUUID());
		if (a == null || !a.weaponId.equals(data.id)) {
			return null;
		}
		if (Firearms.PISTOL.equals(data.id) && a.pistolCharges > 0) {
			ShotSpec spec = new ShotSpec();
			spec.damageFactor = PISTOL_CHARGE_FACTOR;
			spec.onFired = () -> a.pistolCharges--;
			return spec;
		}
		if (Firearms.SNIPER.equals(data.id) && a.sniperPierce) {
			ShotSpec spec = new ShotSpec();
			spec.pierce = true;
			spec.armorIgnore = SNIPER_ARMOR_IGNORE;
			spec.onFired = () -> a.sniperPierce = false;
			return spec;
		}
		return null;
	}

	/** True while the rifle's Rapid Fire (V) runs. */
	public static boolean rapidFire(Player player) {
		Active a = ACTIVE.get(player.getUUID());
		return a != null && a.rapidUntil > player.level().getGameTime();
	}

	/** True while the sniper's Shift+V Steady Shot is lined up (not yet fired). */
	public static boolean steadying(Player player) {
		Active a = ACTIVE.get(player.getUUID());
		return a != null && a.steadyFireAt != 0L;
	}

	/** True while the rifle's Shift+V lock-on is running. */
	public static boolean lockedOn(Player player) {
		Active a = ACTIVE.get(player.getUUID());
		return a != null && a.lockTarget >= 0;
	}

	/** Pistol V shots still charged (for tests / the HUD). */
	public static int pistolCharges(Player player) {
		Active a = ACTIVE.get(player.getUUID());
		return a == null ? 0 : a.pistolCharges;
	}

	/** True while the sniper's piercing round is chambered. */
	public static boolean piercingArmed(Player player) {
		Active a = ACTIVE.get(player.getUUID());
		return a != null && a.sniperPierce;
	}

	// ---------------------------------------------------------------- HUD / cooldown ids

	/** The cooldown id of a gun's V ({@code shift} false) or Shift+V move; null for a gun with no Weapon Ability. */
	public static String cooldownId(String weaponId, boolean shift) {
		String base = switch (weaponId) {
			case Firearms.PISTOL -> "weapon_pistol";
			case Firearms.SHOTGUN -> "weapon_shotgun";
			case Firearms.RIFLE -> "weapon_rifle";
			case Firearms.SNIPER -> "weapon_sniper";
			default -> null;
		};
		return base == null ? null : shift ? base + "_shift" : base;
	}

	public static int maxCooldown(String weaponId, boolean shift) {
		return switch (weaponId) {
			case Firearms.PISTOL -> shift ? PISTOL_SHIFT_CD : PISTOL_CD;
			case Firearms.SHOTGUN -> shift ? SHOTGUN_SHIFT_CD : SHOTGUN_CD;
			case Firearms.RIFLE -> shift ? RIFLE_SHIFT_CD : RIFLE_CD;
			case Firearms.SNIPER -> shift ? SNIPER_SHIFT_CD : SNIPER_CD;
			default -> 0;
		};
	}

	/**
	 * For the HUD's V box: {remaining ticks, full cooldown} of the held gun's V ({@code shift} false) or Shift+V move,
	 * or null while no gun is in the main hand.
	 */
	public static int[] hudCooldown(Player player, boolean shift) {
		FirearmData data = Firearms.of(player.getMainHandItem());
		String id = data == null ? null : cooldownId(data.id, shift);
		if (id == null) {
			return null;
		}
		return new int[] { Punisher.cooldownRemaining(player, id), maxCooldown(data.id, shift) };
	}

	// ---------------------------------------------------------------- helpers

	private static Active bind(ServerPlayer player, String weaponId) {
		int slot = player.getInventory().selected;
		Active a = ACTIVE.get(player.getUUID());
		if (a == null || !a.weaponId.equals(weaponId) || a.slot != slot) {
			if (a != null) {
				cancel(player, false);
			}
			a = new Active(weaponId, slot);
			ACTIVE.put(player.getUUID(), a);
		}
		return a;
	}

	private static boolean canFireNow(ServerPlayer player, ItemStack held, FirearmData data, int rounds) {
		if (FirearmStack.isReloading(held) && !(data.shellReload && FirearmStack.magazine(held, data) >= rounds)) {
			message(player, "reloading", ChatFormatting.GRAY);
			return false;
		}
		if (FirearmStack.magazine(held, data) < rounds) {
			message(player, "empty", ChatFormatting.RED);
			return false;
		}
		return true;
	}

	/** Centre / upper body -- where every auto-aimed round goes. */
	public static Vec3 aimPoint(Entity target) {
		return target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
	}

	/** The target right under the crosshair, else the nearest to it inside a narrow cone; line of sight required. */
	static LivingEntity acquire(ServerPlayer player, double range) {
		ServerLevel level = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 rayEnd = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult direct = ProjectileUtil.getEntityHitResult(level, player, eye, rayEnd,
				new AABB(eye, rayEnd).inflate(1.0), e -> HeroTargets.canHarm(player, e));
		if (direct != null && direct.getEntity() instanceof LivingEntity t) {
			return t;
		}
		LivingEntity best = null;
		double bestCos = LOCK_CONE_COS;
		for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
				e -> HeroTargets.canHarm(player, e))) {
			Vec3 to = aimPoint(t).subtract(eye);
			double dist = to.length();
			if (dist > range || dist < 0.01) {
				continue;
			}
			double cos = to.scale(1.0 / dist).dot(look);
			if (cos > bestCos && player.hasLineOfSight(t)) {
				bestCos = cos;
				best = t;
			}
		}
		return best;
	}

	private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void message(ServerPlayer player, String key, ChatFormatting colour) {
		player.displayClientMessage(Component.translatable("message.projecthero.punisher.weapon." + key).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- the firearm-hook wrapper

	/**
	 * Wraps whatever {@link FirearmHooks} were installed before (the Punisher's passives) and folds the Weapon
	 * Abilities in: the armed special shot, Rapid Fire's fire rate + reload time, and the lock-on / Steady Shot reload block.
	 * Everything else is passed straight through.
	 */
	private static final class Hooks implements FirearmHooks {
		private final FirearmHooks inner;

		Hooks(FirearmHooks inner) {
			this.inner = inner;
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
			float f = inner.reloadSpeedFactor(player);
			return rapidFire(player) ? f * RIFLE_RAPID_FACTOR : f;
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
		public float fireIntervalFactor(ServerPlayer player) {
			float f = inner.fireIntervalFactor(player);
			return rapidFire(player) ? f * RIFLE_RAPID_FACTOR : f;
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
		public void onCrafted(ServerPlayer player, String weaponId) {
			inner.onCrafted(player, weaponId);
		}

		@Override
		public ShotSpec nextShot(ServerPlayer player, FirearmData data) {
			ShotSpec mine = PunisherWeaponAbilities.nextShot(player, data);
			return mine != null ? mine : inner.nextShot(player, data);
		}

		@Override
		public boolean reloadBlocked(ServerPlayer player) {
			return inner.reloadBlocked(player) || lockedOn(player) || steadying(player);
		}
	}
}
