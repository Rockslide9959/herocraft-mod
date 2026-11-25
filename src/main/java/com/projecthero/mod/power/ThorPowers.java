package com.projecthero.mod.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.attachment.CooldownState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.attachment.StormCallState;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.network.ThorLightningArcPayload;
import com.projecthero.mod.sound.ProjectHeroSounds;
import com.projecthero.mod.worthiness.Worthiness;
import com.projecthero.mod.worthiness.WorthinessEnforcer;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side handlers for every keybind-driven Thor power, plus the item-use throw. All gated
 * behind {@link Worthiness#isWorthy}. Each ability that needs one tracks its own cooldown via
 * {@link ThorAbility}/{@link CooldownState} rather than vanilla's item-keyed cooldown system, so
 * e.g. Lightning Strike being on cooldown never blocks Flight, Thunderclap, Storm Call, or Chain
 * Lightning.
 */
public final class ThorPowers {
	/**
	 * v0.14.11: Thor's own vanilla flying speed (was the shared hero 0.06, ~26 blocks/s sprinting): sprint flight now
	 * ~30 blocks a second (vanilla flight settles at speed x 2 sprinting / 0.09 drag a tick).
	 */
	public static final float THOR_FLYING_SPEED = 0.069f;
	public static final int LIGHTNING_RANGE = 30;
	public static final int LIGHTNING_COOLDOWN_TICKS = 50; // 2.5s
	/** v0.6.22: raised 10 -> 13. v0.13.4: raised again, 13 -> 22. */
	private static final float LIGHTNING_STRIKE_DAMAGE = ThorKit.STRIKE_DAMAGE; // v0.17.7 (Cosmic): 22 -> 26
	/** v0.7.5: Lightning Strike soft aim assist. If the raw crosshair ray doesn't land on an entity,
	 * the strike snaps onto the living entity closest to the look vector within this half-angle (and
	 * within {@link #LIGHTNING_RANGE}, with line of sight) so a near-miss lands on the target instead
	 * of cracking down behind it. */
	// v0.13.3: widened 14 -> 20 degrees -- players wanted Lightning Strike to snap onto a target more
	// readily rather than fizzling to the bare impact point on a near-miss.
	private static final double LIGHTNING_AIM_ASSIST_DEGREES = 20.0;
	/** God of Thunder's Wrath now gets the same kind of soft aim assist, just a tighter cone since its
	 * range (120 blocks) is four times Lightning Strike's -- the same angle there would sweep a much
	 * wider area than intended. */
	private static final double GOD_OF_THUNDER_AIM_ASSIST_DEGREES = 8.0;

	// ---------------- auto-chain (Lightning Strike's own extra jump, not the standalone ability) ----------------
	private static final double CHAIN_RANGE = 6.0;
	private static final int CHAIN_MAX_JUMPS = 2;
	private static final float CHAIN_DAMAGE_FALLOFF = 0.5f;

	// ---------------- chain lightning (standalone ability, key C) ----------------
	/** v0.6.22: a forward cone AoE -- every mob within this reach and the cone half-angle is hit. */
	private static final double CHAIN_LIGHTNING_CONE_RANGE = 32.0; // v0.17.7: 122 -> 32 (a cone, not the horizon)
	/** Cone half-angle: a mob whose direction from the caster is within this of the look vector is in. */
	private static final double CHAIN_LIGHTNING_CONE_DEGREES = 40.0;
	/** v0.13.4: raised 8 -> 18. */
	private static final float CHAIN_LIGHTNING_DAMAGE = ThorKit.CHAIN_DAMAGE; // v0.17.7 (Cosmic): 18 -> 24
	private static final int CHAIN_LIGHTNING_COOLDOWN_TICKS = 160; // 8s

	// ---------------- god of thunder's wrath (ultimate, key Z) ----------------
	private static final double GOD_OF_THUNDER_RANGE = 120.0;
	/** v0.13.4: raised 80 -> 100. */
	private static final float GOD_OF_THUNDER_DAMAGE = 100.0f;
	/** v0.13.4: raised 5 -> 7 -- "bigger" per the same request that raised the damage. */
	private static final double GOD_OF_THUNDER_RADIUS = 7.0;
	/** v0.13.4: how many extra bolts strike nearby (jittered) points inside the radius, on top of the
	 * one dead-center on the impact point, so the ultimate reads as a barrage rather than one bolt. */
	private static final int GOD_OF_THUNDER_EXTRA_BOLTS = 4;
	private static final int GOD_OF_THUNDER_COOLDOWN_TICKS = 90 * 20; // 90s
	/** v0.6.23: hold Z for this long (5 s) to charge the ultimate -- it can never be cast instantly. */
	private static final int WRATH_CHARGE_TICKS = 5 * 20;

	// ---------------- lightning laser ----------------
	private static final int LASER_RANGE = 20;
	/** v0.6.22: raised 3 -> 4 per tick. v0.13.4: raised again, 4 -> 8. */
	private static final float LASER_DAMAGE_PER_TICK = 8.0f;

	/** Any animal a lightning ability damages gets briefly ignited so a lethal hit drops cooked
	 * food -- vanilla's own animal loot tables already smelt their drops when the entity is on
	 * fire at death (see e.g. pig.json's furnace_smelt function gated on entity_properties
	 * is_on_fire), so this needs no custom loot handling of its own. */
	private static final int LIGHTNING_SINGE_FIRE_TICKS = 20;

	// ---------------- thunderclap shockwave ----------------
	private static final int THUNDERCLAP_COOLDOWN_TICKS = 100; // 5s -- short, "get them off me" panic button
	/** v0.6.22: 5 -> 7 blocks, and it now deals damage as well as knocking back. */
	private static final double THUNDERCLAP_RADIUS = 7.0;
	/** v0.13.4: raised 10 -> 22. */
	private static final float THUNDERCLAP_DAMAGE = ThorKit.CLAP_DAMAGE; // v0.17.7 (Cosmic): 22 -> 26

	// ---------------- storm call ----------------
	// Radius is centered on and follows the player (rather than staying fixed at the cast location)
	// so the storm stays useful while chasing/kiting during a fight -- see chat summary for the note.
	private static final int STORM_CALL_DURATION_TICKS = 360; // 18s
	private static final int STORM_CALL_COOLDOWN_TICKS = 2400; // 2min
	private static final double STORM_CALL_RADIUS = 12.0;
	private static final int STORM_CALL_STRIKE_INTERVAL_TICKS = 50;
	/** v0.14.4: Storm Call's bolts are Thor's own now (see {@link #tickStormCall}): damage and splash per bolt. */
	private static final float STORM_CALL_STRIKE_DAMAGE = 22.0f; // v0.17.7 (Cosmic): 8 -> 22
	private static final double STORM_CALL_SPLASH_RADIUS = 3.0;

	// ---------------- flight ----------------
	/**
	 * How long after take-off the "you touched the ground, stop flying" check is suppressed. The
	 * server's idea of {@code onGround} only advances when a movement packet arrives, so for the
	 * first tick or two after take-off it can still be reporting the ground the player just jumped
	 * off -- which would cancel the flight the same tick it started.
	 */
	private static final int FLIGHT_TAKEOFF_GRACE_TICKS = 10;
	/**
	 * How long a player who throws Mjolnir mid-flight keeps flying without it in hand -- 15 seconds,
	 * in ticks so it is entirely server-authoritative (see {@link #throwMjolnir} and the flying branch
	 * of {@link #serverTick}).
	 */
	private static final int HAMMERLESS_FLIGHT_GRACE_TICKS = 15 * 20;
	/** Below this many ticks remaining, the HUD escalates -- see {@code ThorHud}. */
	private static final int HAMMERLESS_FLIGHT_WARNING_TICKS = 5 * 20;

	// ---------------- call hammer ----------------
	/**
	 * Purely an anti-spam guard, not a gameplay cooldown: it stops a held or mashed call key from
	 * repeating the action-bar line and the thunder cue every tick, while still being short enough
	 * that a deliberate second press feels instant.
	 */
	private static final int CALL_HAMMER_COOLDOWN_TICKS = 10;

	private ThorPowers() {
	}

	public static boolean isHoldingMjolnir(Player player) {
		return player.getMainHandItem().is(ModItems.MJOLNIR) || player.getOffhandItem().is(ModItems.MJOLNIR);
	}

	/** v0.14.19: Stormbreaker in either hand. */
	public static boolean isHoldingStormbreaker(Player player) {
		return player.getMainHandItem().is(ModItems.STORMBREAKER) || player.getOffhandItem().is(ModItems.STORMBREAKER);
	}

	/**
	 * v0.14.19: Mjolnir OR Stormbreaker in either hand -- what every lightning power, the Lightning Beam, Wrath,
	 * Thunderclap, Storm Call and flight gate on. Anything that physically sends, recalls, binds or identifies
	 * MJOLNIR (the throw, Hammer Volley, Call Mjolnir, the registry, the arm poses) still checks Mjolnir only.
	 * Callers pair this with {@link Worthiness#isWorthy} -- an unworthy player can hold Stormbreaker, unlike Mjolnir.
	 */
	public static boolean isHoldingThorWeapon(Player player) {
		return isHoldingMjolnir(player) || isHoldingStormbreaker(player);
	}

	/**
	 * v0.14.19: whether the player's held weapon gives them Thor's kit right now -- Mjolnir (only the worthy can hold
	 * it at all), or Stormbreaker in a worthy hand. For client-side key/HUD routing, where an unworthy player merely
	 * carrying Stormbreaker must keep their own mutation keys and HUD. Worthiness is synced to the owning client.
	 */
	public static boolean wieldsThorWeapon(Player player) {
		return isHoldingMjolnir(player) || (isHoldingStormbreaker(player) && Worthiness.isWorthy(player));
	}

	/**
	 * v0.14.19: the hammerless-flight grace a mid-flight Mjolnir throw grants, shared with Stormbreaker's throw so a
	 * flying Thor does not drop out of the sky the moment the axe leaves his hand. Only while really Thor-flying.
	 */
	public static void grantThrowFlightGrace(ServerPlayer player) {
		if (isFlying(player) && !player.getAbilities().instabuild) {
			player.setAttached(ModAttachments.HAMMERLESS_FLIGHT_TICKS, HAMMERLESS_FLIGHT_GRACE_TICKS);
		}
	}

	public static boolean isFlying(Player player) {
		return player.getAttachedOrElse(ModAttachments.FLYING, false);
	}

	private static CooldownState cooldowns(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.COOLDOWNS);
	}

	private static boolean consumeCooldown(ServerPlayer player, ThorAbility ability, int cooldownTicks) {
		CooldownState state = cooldowns(player);
		long tick = player.level().getGameTime();
		if (!state.isReady(ability, tick)) {
			return false;
		}
		triggerCooldown(player, ability, cooldownTicks);
		return true;
	}

	/** Start {@code ability}'s cooldown AND flag the attachment dirty so the (synced) HUD updates. */
	static void triggerCooldown(ServerPlayer player, ThorAbility ability, int cooldownTicks) {
		CooldownState state = cooldowns(player);
		state.trigger(ability, player.level().getGameTime(), cooldownTicks);
		player.setAttached(ModAttachments.COOLDOWNS, state);
	}

	// ---------------- throw & return ----------------

	public static boolean throwMjolnir(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.MJOLNIR)) {
			return false;
		}

		// Whether this throw should grant hammerless-flight grace, decided BEFORE the hammer actually
		// leaves the hand: only a real throw while genuinely already Thor-flying counts (never a
		// Q-drop -- that never calls this method at all -- and never a throw from the ground).
		boolean grantGrace = isFlying(player) && !player.getAbilities().instabuild;

		// Carry the actual held stack onto the projectile rather than spawning a fresh plain one --
		// otherwise the bound owner and the hammer's identity are silently erased by every throw, and
		// it stops answering its owner's call key the moment they use it.
		ItemStack thrown = stack.copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		MjolnirEntity entity = new MjolnirEntity(player.level(), player);
		entity.setItem(thrown);
		entity.throwFromPlayer(player, player.getLookAngle());
		player.level().addFreshEntity(entity);
		playThrowSound(player);
		ThorVisuals.anim(player, ThorFx.ANIM_THROW);

		if (grantGrace) {
			player.setAttached(ModAttachments.HAMMERLESS_FLIGHT_TICKS, HAMMERLESS_FLIGHT_GRACE_TICKS);
		}
		return true;
	}

	/**
	 * Heavy air displacement + a fast whoosh + a small metallic element + a subtle electrical
	 * undertone -- deliberately NOT {@code SoundEvents.TRIDENT_THROW} (see {@link ProjectHeroSounds}'s
	 * javadoc for why, and for exactly what {@code .ogg} to drop in later).
	 * {@link ProjectHeroSounds#MJOLNIR_THROW} is the real, registered custom event; the vanilla layers
	 * alongside it are what make the throw sound complete today, before that file exists.
	 *
	 * <p>This used to also layer in {@code SoundEvents.ANVIL_LAND} at 0.5 volume -- a genuinely
	 * loud, percussive "crash" sample never meant to play quietly, which combined with the other
	 * three FULL-volume layers playing at the exact same instant is what actually produced the
	 * "BANG!!!!" the volume complaint described (four simultaneous sounds reinforce rather than
	 * average). Fixed two ways at once, per the "reduce volume, and rework anything that turns out
	 * not to suit Mjolnir" instruction: every layer's volume is now roughly a third of what it was,
	 * AND the anvil crash is replaced with {@code MACE_SMASH_AIR} -- a real "heavy weapon moving
	 * through the air" sample with no crash transient at all, so turning it down doesn't just make a
	 * bang quieter, it removes the bang.
	 */
	private static void playThrowSound(ServerPlayer player) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		var level = player.level();
		level.playSound(null, x, y, z, ProjectHeroSounds.MJOLNIR_THROW, SoundSource.PLAYERS, 0.5f, 1.0f);
		level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.35f, 0.9f);
		level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.45f, 0.8f);
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.15f, 1.7f);
	}

	// ---------------- call hammer ----------------

	/**
	 * Resolution, feedback and the actual return all live in
	 * {@link com.projecthero.mod.hammer.MjolnirRecall}; this is only the rate limit that stops a held
	 * or mashed key from spamming the action bar and the thunder cue.
	 */
	public static void callHammer(ServerPlayer player) {
		if (!consumeCooldown(player, ThorAbility.CALL_HAMMER, CALL_HAMMER_COOLDOWN_TICKS)) {
			return;
		}
		MjolnirRecall.call(player);
	}

	/**
	 * Sneak-right-click on Mjolnir: bind it to this player, or release it if it is already theirs.
	 * Requires worthiness (checked by the caller, {@link com.projecthero.mod.item.MjolnirItem#use})
	 * same as every other Mjolnir interaction.
	 *
	 * <p>Binding is what grants the {@linkplain ThorPassives Power of Thor}, so the two are done
	 * together and can never drift apart. A hammer already bound to <em>somebody else</em> refuses:
	 * ownership is deliberately strong, and picking one up off the floor must not quietly transfer
	 * it. Unbinding your own is the supported way to hand one over.
	 */
	public static void toggleBinding(ServerPlayer player, ItemStack stack) {
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		MjolnirRegistry registry = MjolnirRegistry.get(serverLevel);
		UUID hammerId = registry.identify(stack);
		UUID currentOwner = stack.get(ModDataComponents.BOUND_OWNER);

		if (currentOwner != null && !currentOwner.equals(player.getUUID())) {
			String name = stack.get(ModDataComponents.BOUND_OWNER_NAME);
			ThorFeedback.boundToSomeoneElse(player, name != null ? name : currentOwner.toString().substring(0, 8));
			return;
		}

		if (currentOwner != null) {
			unbindHammer(player, stack, registry);
			return;
		}

		bindHammer(player, stack, registry, hammerId, serverLevel);
	}

	/**
	 * Binds a freshly-lifted hammer to the player who just ascended (see {@code Worthiness#ascend}). A hammer
	 * already bound to someone else is left with its owner -- ownership is deliberately strong.
	 */
	public static void bindOnAscend(ServerPlayer player, ItemStack stack) {
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		UUID currentOwner = stack.get(ModDataComponents.BOUND_OWNER);
		if (currentOwner != null && !currentOwner.equals(player.getUUID())) {
			return;
		}
		MjolnirRegistry registry = MjolnirRegistry.get(serverLevel);
		bindHammer(player, stack, registry, registry.identify(stack), serverLevel);
	}

	private static void bindHammer(ServerPlayer player, ItemStack stack, MjolnirRegistry registry,
			UUID hammerId, ServerLevel serverLevel) {
		stack.set(ModDataComponents.BOUND_OWNER, player.getUUID());
		// Cosmetic companion to the UUID -- see ModDataComponents.BOUND_OWNER_NAME. Written here so
		// the hammer's tooltip can name its owner on any client that sees the stack.
		stack.set(ModDataComponents.BOUND_OWNER_NAME, player.getGameProfile().getName());
		registry.setOwner(stack, Optional.of(player.getUUID()), player.getGameProfile().getName());

		// Only announce on a genuine first bind of *this* hammer -- rebinding the same one after an
		// unbind is a normal thing to do and does not deserve the full ceremony every time.
		boolean changed = ThorPassives.bind(player, hammerId);
		if (changed) {
			ThorFeedback.bound(player);
		}

		serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getEyeY(), player.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
	}

	private static void unbindHammer(ServerPlayer player, ItemStack stack, MjolnirRegistry registry) {
		stack.remove(ModDataComponents.BOUND_OWNER);
		stack.remove(ModDataComponents.BOUND_OWNER_NAME);
		registry.setOwner(stack, Optional.empty(), "");
		ThorPassives.unbind(player);
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.HAMMER_RELEASED, true);
		ThorFeedback.unbound(player);

		if (player.level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.2f);
		}
	}

	/**
	 * v0.13.5: fully strips the Power of Thor from {@code player} -- unlike every other Primary power
	 * (a suit/serum item that is simply consumed), Thor's power comes from being <em>bound</em> to a
	 * physical Mjolnir, so a plain {@code Worthiness.setScore(player, 0)} (all {@code revoke} call sites
	 * used to do) left the hammer itself still bound to them, the HUD still rendering (it keys off the
	 * binding, not worthiness), Storm Energy/Storm Call/Laser/Wrath-charge state untouched, and nobody
	 * else able to ever bind that specific hammer again. Called from both
	 * {@code HeroTiers#revokeHero} (an evicted Primary power) and {@code HeroCommand#revokeHeroByKey}
	 * (the admin command), replacing their old bare worthiness reset.
	 */
	public static void revokePower(ServerPlayer player) {
		UUID hammerId = ThorPassives.boundHammerId(player);
		if (hammerId != null) {
			MjolnirRecall.forceUnbind(player, hammerId);
		}
		// v0.15.1: a bound Stormbreaker is released along with the power, so it can bind to the next Thor
		UUID axeId = player.getAttachedOrElse(ModAttachments.BOUND_STORMBREAKER_ID, null);
		if (axeId != null) {
			MjolnirRecall.forceUnbind(player, com.projecthero.mod.hammer.ThorWeapon.STORMBREAKER, axeId);
			player.setAttached(ModAttachments.BOUND_STORMBREAKER_ID, null);
		}
		// Clears BOUND_HAMMER_ID and reconciles every attribute/effect off immediately, whether or not
		// the physical hammer itself was reachable above -- this alone is what fixes the lingering HUD.
		ThorPassives.unbind(player);
		Worthiness.setScore(player, 0);

		StormEnergy.set(player, StormEnergy.MAX);
		player.setAttached(ModAttachments.LASER_ACTIVE, false);
		player.setAttached(ModAttachments.THOR_WRATH_CHARGE, 0);
		player.setAttached(ModAttachments.STORM_CALL_STATE, new StormCallState());
		player.setAttached(ModAttachments.THOR_FX, ThorFx.EMPTY);
	}

	// ---------------- flight ----------------

	public static void toggleFlight(ServerPlayer player) {
		if (player.getAbilities().instabuild) {
			// Leave creative/spectator flight alone -- Thor's toggle never touches it either way.
			return;
		}

		if (isFlying(player)) {
			// Landing is always allowed, hammer in hand or not: a player mid hammerless-flight grace
			// (see the class javadoc's "hammerless flight" section) still has to be able to manually
			// cancel it via the same double-tap-jump control, even though they don't currently pass
			// the worthy+holding checks below that gate taking off.
			setFlying(player, false);
			return;
		}

		if (!Worthiness.isWorthy(player) || !isHoldingThorWeapon(player)) {
			return;
		}
		if (!StormEnergy.has(player, 1.0f)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}

		setFlying(player, true);
	}

	/** v0.17.7: ends Thor flight (a leap, a dive or an ultimate takes over the motion). */
	public static void landFlight(ServerPlayer player) {
		setFlying(player, false);
	}

	private static void setFlying(ServerPlayer player, boolean flying) {
		player.setAttached(ModAttachments.FLYING, flying);

		if (flying) {
			triggerCooldown(player, ThorAbility.FLIGHT, FLIGHT_TAKEOFF_GRACE_TICKS);
		} else if (player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0) != 0) {
			// Single choke point: landing, manually toggling off, switching to creative mid-grace, and
			// the grace timer itself expiring all end up here, so none of them can leave a stale
			// countdown that silently reactivates flight later (see requirements 27/28/33).
			player.setAttached(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0);
		}

		if (player.getAbilities().instabuild) {
			// Creative/spectator flight is the game's, not ours -- clearing mayfly here would strip
			// a player who switched to creative mid-flight of their creative flight entirely.
			return;
		}

		player.getAbilities().mayfly = flying;
		player.getAbilities().flying = flying;
		// v0.9.2: fly at the shared hero-flight speed so Thor keeps pace with Turbo Flight / the
		// experimental Flight power instead of drifting along at the plain creative-mode rate.
		// v0.17.7: per weapon state -- Mjolnir fastest, Twin Gods medium, Stormbreaker slow
		player.getAbilities().setFlyingSpeed(flying
				? ThorKit.flyingSpeed(player)
				: com.projecthero.mod.hero.power.HeroFlight.VANILLA_FLYING_SPEED);
		player.onUpdateAbilities();
		player.resetFallDistance();

		if (player.level() instanceof ServerLevel serverLevel) {
			// A wind-gust cue, not a mob screech: this used to reuse SoundEvents.PHANTOM_FLAP/SWOOP,
			// which -- being literally the Phantom's own flap/dive sounds -- is exactly the
			// "phantom-like sound after flying" players were hearing on landing. Breeze's air-burst
			// sounds fit a storm-god's flight far better and aren't used anywhere else in this mod.
			serverLevel.playSound(null, player.blockPosition(),
					flying ? SoundEvents.BREEZE_JUMP : SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.8f, flying ? 1.1f : 0.9f);
		}
	}

	/** Called every server tick for every player to drain/regen Storm Energy and drive flight/laser/storm call. */
	public static void serverTick(ServerPlayer player) {
		WorthinessEnforcer.serverTick(player);
		ThorPassives.serverTick(player);
		ThorStorm.tick(player); // v0.16.1: the thunderstorm buff's synced flag + sparks
		ThorKit.tick(player); // v0.17.7: melee per weapon state, God of Thunder / Storm Sprint / Lightning Incarnate

		if (isFlying(player)) {
			if (player.getAbilities().instabuild) {
				// They've switched to creative/spectator mid-flight -- hand off to the game's own
				// flight entirely, per requirement 32. setFlying(false) only clears OUR tracking; see
				// its own instabuild guard for why it never touches their creative mayfly/flying.
				setFlying(player, false);
			} else {
				boolean holding = isHoldingThorWeapon(player);
				if (holding && player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0) != 0) {
					// Caught it back mid-grace: resume ordinary flight seamlessly, no sudden fall --
					// requirement 25. The grace ticks reset to 0 (via the branch below not firing
					// again) rather than lingering at whatever value they stopped at.
					player.setAttached(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0);
				}
				boolean grace = !holding && player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0) > 0;

				if (!holding && !grace) {
					// No hammer, and no (or expired) grace: flight ends, ordinary gravity takes over.
					setFlying(player, false);
				} else if (player.onGround() && cooldowns(player).isReady(ThorAbility.FLIGHT, player.level().getGameTime())
						&& com.projecthero.mod.flight.FlightLanding.descending(player)) { // v0.15.19: and not rising
					// Touching down ends the flight, matching vanilla's own creative-flight behavior,
					// and cleanly cancels any hammerless grace in progress -- requirement 27.
					setFlying(player, false);
				} else {
					StormEnergy.spend(player, StormEnergy.FLIGHT_DRAIN_PER_SECOND / 20.0f);
					if (!StormEnergy.has(player, 0.001f)) {
						setFlying(player, false);
					} else {
						player.getAbilities().flying = true;
						float speed = ThorKit.flyingSpeed(player); // v0.17.7: follows a weapon swap mid-flight
						if (player.getAbilities().getFlyingSpeed() != speed) {
							player.getAbilities().setFlyingSpeed(speed);
							player.onUpdateAbilities();
						}
						tickFlightTrail(player);
						if (grace) {
							tickHammerlessFlightGrace(player);
						}
					}
				}
			}
		}

		tickLaser(player);
		tickStormCall(player);

		if (!isFlying(player)) {
			float regenPerSecond;
			if (isStormCallActive(player)) {
				regenPerSecond = StormEnergy.REGEN_PER_SECOND_STORM_CALL;
			} else if (player.level().isThundering()) {
				regenPerSecond = StormEnergy.REGEN_PER_SECOND_THUNDER;
			} else if (player.level().isRaining()) {
				regenPerSecond = StormEnergy.REGEN_PER_SECOND_RAIN;
			} else {
				regenPerSecond = StormEnergy.REGEN_PER_SECOND;
			}
			// v0.16.7 (user): wearing Thor's Armour regenerates Storm Energy 50% faster
			if (com.projecthero.mod.thorarmor.ThorArmor.isWearing(player)) {
				regenPerSecond *= StormEnergy.ARMOUR_REGEN_MULTIPLIER;
			}
			StormEnergy.add(player, regenPerSecond / 20.0f);
		}
	}

	/**
	 * Counts a hammerless-flight grace period down by one tick, expiring it (and ending flight -- no
	 * teleport, no velocity change, ordinary gravity plus whatever fall-damage rules already apply)
	 * the instant it hits zero. Only reached while {@code grace} is true in {@link #serverTick}, i.e.
	 * the player is flying, not currently holding Mjolnir, and had ticks remaining.
	 */
	private static void tickHammerlessFlightGrace(ServerPlayer player) {
		int remaining = player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0) - 1;
		if (remaining <= 0) {
			setFlying(player, false);
			return;
		}
		player.setAttached(ModAttachments.HAMMERLESS_FLIGHT_TICKS, remaining);
	}

	private static void tickFlightTrail(ServerPlayer player) {
		if (player.tickCount % 2 != 0 || !(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 behind = player.position().subtract(player.getLookAngle().scale(0.6)).add(0, player.getBbHeight() / 2, 0);
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, behind.x, behind.y, behind.z, 3, 0.2, 0.2, 0.2, 0.01);
		serverLevel.sendParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, 2, 0.15, 0.15, 0.15, 0.01);
	}

	// ---------------- lightning strike (+ chain lightning) ----------------

	public static void lightningStrike(ServerPlayer player) {
		if (!Worthiness.isWorthy(player) || !isHoldingThorWeapon(player)) {
			return;
		}
		// Check readiness without consuming yet -- an insufficient-energy cast shouldn't start the
		// cooldown (mirrors the pre-refactor behavior, where addCooldown only ran after a real cast).
		// v0.17.7: cooldown / cost through ThorKit (state multipliers)
		if (!ThorKit.begin(player, ThorAbility.LIGHTNING_STRIKE, ThorKit.STRIKE_CD, ThorKit.STRIKE_COST)) {
			return;
		}

		HitResult hit = ProjectileUtil.getHitResultOnViewVector(
				player, target -> aimable(player, target), LIGHTNING_RANGE);

		Vec3 pos;
		Entity primaryTarget = null;
		if (hit instanceof EntityHitResult entityHit) {
			primaryTarget = entityHit.getEntity();
			pos = primaryTarget.position();
		} else {
			// The raw ray missed every entity -- try the soft aim assist before falling back to the
			// block/air impact point, so looking near an enemy still lands the bolt on it.
			LivingEntity assisted = findAimAssistTarget(player, LIGHTNING_RANGE, LIGHTNING_AIM_ASSIST_DEGREES);
			if (assisted != null) {
				primaryTarget = assisted;
				pos = assisted.position();
			} else if (hit != null) {
				pos = hit.getLocation();
			} else {
				pos = player.getEyePosition().add(player.getLookAngle().scale(LIGHTNING_RANGE));
			}
		}

		ThorVisuals.anim(player, ThorFx.ANIM_STRIKE, pos);
		if (player.level() instanceof ServerLevel serverLevel) {
			spawnVisualBolt(serverLevel, player, pos);

			if (primaryTarget instanceof LivingEntity livingPrimary && ThorTargets.canAffect(player, livingPrimary)) {
				Set<Entity> struck = new HashSet<>();
				struck.add(livingPrimary);
				strikeEntity(serverLevel, player, livingPrimary, LIGHTNING_STRIKE_DAMAGE);
				chainLightning(serverLevel, player, livingPrimary, LIGHTNING_STRIKE_DAMAGE, struck);
			}
			serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.1f);
			ThorTwinThunder.shake(serverLevel, pos, 0.5f, 6, 16.0);
		}
	}

	/**
	 * v0.7.5: soft aim assist for Lightning Strike, generalized in v0.13.3 so God of Thunder's Wrath
	 * can reuse it with its own range/cone -- returns the living entity whose direction from the
	 * player's eye is closest to the look vector, provided it is inside {@code angleDegrees}, within
	 * {@code range}, and in line of sight; null if nothing qualifies. One AABB-bounded query plus a
	 * cone filter, never an unbounded scan.
	 */
	/**
	 * v0.14.4: what a Thor aim ray (Lightning Strike, the Beam, Wrath) stops on. A squadmate (or anything else
	 * {@link ThorTargets#canAffect} spares) is see-through -- the bolt goes past them to whatever is behind, rather than
	 * stopping on a friend and fizzling.
	 */
	static boolean aimable(Player player, Entity target) {
		return target != player && target.isPickable()
				&& (!(target instanceof LivingEntity) || ThorTargets.canAffect(player, target));
	}

	static LivingEntity findAimAssistTarget(ServerPlayer player, double range, double angleDegrees) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(angleDegrees));
		LivingEntity best = null;
		for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(range),
				e -> e.isPickable() && ThorTargets.canAffect(player, e))) {
			Vec3 to = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist < 1.0E-3 || dist > range) {
				continue;
			}
			double dot = to.scale(1.0 / dist).dot(look);
			if (dot > bestDot && player.hasLineOfSight(candidate)) {
				bestDot = dot;
				best = candidate;
			}
		}
		return best;
	}

	private static void chainLightning(ServerLevel level, ServerPlayer player, LivingEntity from, float incomingDamage, Set<Entity> struck) {
		float jumpDamage = ThorKit.STRIKE_CHAIN_DAMAGE; // v0.17.7 (Cosmic): a flat 22 (was half the strike)
		List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
				from.getBoundingBox().inflate(CHAIN_RANGE),
				e -> !struck.contains(e) && ThorTargets.isHostile(player, e))); // v0.14.20: jumps only to threats (rule 2)
		candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));

		int jumps = Math.min(CHAIN_MAX_JUMPS, candidates.size());
		for (int i = 0; i < jumps; i++) {
			LivingEntity next = candidates.get(i);
			struck.add(next);
			spawnVisualBolt(level, player, next.position());
			strikeEntity(level, player, next, jumpDamage);
		}
	}

	private static void spawnVisualBolt(ServerLevel level, ServerPlayer cause, Vec3 pos) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(pos.x, pos.y, pos.z);
			bolt.setVisualOnly(true);
			bolt.setCause(cause);
			level.addFreshEntity(bolt);
		}
	}

	private static void strikeEntity(ServerLevel level, ServerPlayer player, LivingEntity target, float damage) {
		strikeEntity(level, player, target, damage, 0.7f);
	}

	private static void strikeEntity(ServerLevel level, ServerPlayer player, LivingEntity target, float damage, float hitSoundVolume) {
		// v0.14.4: the last line of squad safety -- nothing below (fire, damage, knockback) ever reaches a squadmate
		if (!ThorTargets.canAffect(player, target)) {
			return;
		}
		// v0.17.7: through ThorKit.hit -- the state rules (Mjolnir's stun, Stormbreaker's armour cut), the cap
		if (ThorKit.hit(player, target, damage, ThorKit.Kind.LIGHTNING)) {
			target.knockback(0.3, player.getX() - target.getX(), player.getZ() - target.getZ());
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, hitSoundVolume, 1.4f);
		}
	}

	/** See {@link #LIGHTNING_SINGE_FIRE_TICKS}. Fire must be applied before {@code hurt()} so the
	 * entity is already on fire at the moment a lethal hit's loot table is rolled. */
	private static void igniteIfAnimal(LivingEntity target) {
		if (target instanceof net.minecraft.world.entity.animal.Animal) {
			target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), LIGHTNING_SINGE_FIRE_TICKS));
		}
	}

	// ---------------- chain lightning (standalone ability, key C) ----------------

	/**
	 * v0.6.22: a wide forward cone. Every valid mob within {@link #CHAIN_LIGHTNING_CONE_RANGE} blocks
	 * ahead of the caster and inside the {@link #CHAIN_LIGHTNING_CONE_DEGREES} half-angle takes
	 * {@link #CHAIN_LIGHTNING_DAMAGE}, and a visible lightning arc jumps from the caster to the nearest
	 * target and then from each target to the next further one -- so the power reads as a single bolt
	 * chaining through the whole group. The candidate query is one AABB-bounded
	 * {@code getEntitiesOfClass} call, then a fixed cone filter, so it is never an unbounded scan.
	 */
	public static void chainLightningCast(ServerPlayer player) {
		if (!Worthiness.isWorthy(player) || !isHoldingThorWeapon(player)) {
			return;
		}
		if (!ThorKit.begin(player, ThorAbility.CHAIN_LIGHTNING, ThorKit.CHAIN_CD, ThorKit.CHAIN_COST)) {
			return;
		}
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		List<LivingEntity> inCone = chainLightningTargets(player);
		ThorVisuals.anim(player, ThorFx.ANIM_CHAIN,
				inCone.isEmpty() ? player.getEyePosition().add(player.getLookAngle().scale(8.0)) : inCone.get(0).position());
		if (inCone.isEmpty()) {
			// Nothing in front to chain through -- a quiet whiff, not a wasted loud cast.
			return;
		}

		Entity previousEntity = null; // null -- the caster's own hand, per sendLightningArc's contract
		for (int i = 0; i < inCone.size(); i++) {
			LivingEntity target = inCone.get(i);
			Vec3 targetPoint = target.position().add(0, target.getBbHeight() * 0.5, 0);
			// The first arc is the "cast" crack (louder); the rest are quiet so a big group reads as
			// one fast chain rather than a wall of simultaneous thunderclaps.
			float hitVolume = i == 0 ? 0.9f : 0.25f;
			sendLightningArc(player, i % LIGHTNING_ARC_CHAIN_SLOTS, previousEntity, target, targetPoint,
					LIGHTNING_ARC_CHAIN_HOLD_TICKS, LIGHTNING_ARC_CHAIN_FADE_TICKS);
			strikeEntity(serverLevel, player, target, CHAIN_LIGHTNING_DAMAGE, hitVolume);
			previousEntity = target;
		}
	}

	/**
	 * Everything Chain Lightning would hit right now, nearest first: every valid target inside the forward cone. Public
	 * so the squad-safety gametests can check exactly who is picked.
	 */
	public static List<LivingEntity> chainLightningTargets(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double cosLimit = Math.cos(Math.toRadians(CHAIN_LIGHTNING_CONE_DEGREES));

		List<LivingEntity> inCone = new ArrayList<>();
		for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(CHAIN_LIGHTNING_CONE_RANGE),
				e -> isValidChainLightningTarget(player, e))) {
			Vec3 to = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist < 1.0E-3 || dist > CHAIN_LIGHTNING_CONE_RANGE) {
				continue;
			}
			if (to.scale(1.0 / dist).dot(look) >= cosLimit) {
				inCone.add(candidate);
			}
		}
		inCone.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		return inCone;
	}

	/**
	 * Hostile mobs, valid combat targets, and players (only if the server allows PvP) -- never the
	 * caster, non-living decoration (armor stands, item entities, XP orbs), or Mjolnir itself. See
	 * requirements 10-11. v0.14.4: and never a squadmate or a squad pet ({@link ThorTargets#canAffect}).
	 */
	private static boolean isValidChainLightningTarget(Player caster, Entity target) {
		return target instanceof LivingEntity && target.isPickable() && ThorTargets.canAffect(caster, target);
	}

	// ---------------- god of thunder's wrath (ultimate, key Z) ----------------

	/**
	 * Everything the ultimate's blast at {@code pos} lands on: within {@link #GOD_OF_THUNDER_RADIUS} (box distance,
	 * with the same two-block grace it always had), minus the caster, their squad and anything else
	 * {@link ThorTargets#canAffect} spares. Public for the squad-safety gametests.
	 */
	public static List<LivingEntity> wrathTargets(ServerLevel level, ServerPlayer player, Vec3 pos) {
		return ThorTargets.inRadius(level, player, pos, Math.sqrt(GOD_OF_THUNDER_RADIUS * GOD_OF_THUNDER_RADIUS + 4.0));
	}

	// ---------------- crackling lightning arcs (v0.13.4: replaces the old particle-line arcs) ----------------

	/** Reserved slot for Lightning Beam's continuous, self-replacing segment. */
	private static final int LIGHTNING_ARC_SLOT_BEAM = 64;
	/** Chain Lightning can hit an unbounded cone of targets; hop slots wrap rather than grow forever --
	 * two hops sharing a slot in an extreme mob pile is a harmless cosmetic overlap, not a bug. */
	private static final int LIGHTNING_ARC_CHAIN_SLOTS = 16;
	private static final int LIGHTNING_ARC_BEAM_HOLD_TICKS = 2;
	private static final int LIGHTNING_ARC_BEAM_FADE_TICKS = 5;
	private static final int LIGHTNING_ARC_CHAIN_HOLD_TICKS = 6;
	private static final int LIGHTNING_ARC_CHAIN_FADE_TICKS = 10;

	/**
	 * Sends one crackling lightning arc segment ({@code ThorLightningArcRenderer}, client-side) to the
	 * caster and everyone tracking them. {@code fromEntity == null} means "the caster's own hand" (the
	 * client derives that point itself, the same formula {@link #tickLaser} used to use for its particle
	 * beam's start); {@code toEntity == null} means the raw {@code toPoint}.
	 */
	static void sendLightningArc(ServerPlayer caster, int slot, Entity fromEntity, Entity toEntity,
			Vec3 toPoint, int holdTicks, int fadeTicks) {
		Vec3 p = toEntity != null ? toEntity.position() : toPoint;
		ThorLightningArcPayload payload = new ThorLightningArcPayload(caster.getId(), slot,
				fromEntity == null ? -1 : fromEntity.getId(), toEntity == null ? -1 : toEntity.getId(),
				p.x, p.y, p.z, holdTicks, fadeTicks);
		ServerPlayNetworking.send(caster, payload);
		for (ServerPlayer viewer : PlayerLookup.tracking(caster)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	// ---------------- lightning laser ----------------

	public static boolean isLaserActive(Player player) {
		return player.getAttachedOrElse(ModAttachments.LASER_ACTIVE, false);
	}

	/** Client sends this on the rising/falling edge of the laser keybind being held. */
	public static void setLaserActive(ServerPlayer player, boolean active) {
		if (!active) {
			if (isLaserActive(player)) {
				player.setAttached(ModAttachments.LASER_ACTIVE, false);
			}
			return;
		}

		if (!Worthiness.isWorthy(player) || !isHoldingThorWeapon(player)) {
			return;
		}
		if (!StormEnergy.has(player, beamDrain(player) / 20.0f)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}

		player.setAttached(ModAttachments.LASER_ACTIVE, true);
		player.setAttached(ModAttachments.THOR_WRATH_CHARGE, 0); // (v0.17.7: reused as the beam's tick clock)
	}

	private static void tickLaser(ServerPlayer player) {
		if (!isLaserActive(player)) {
			ThorVisuals.channel(player, ThorFx.CH_BEAM, false);
			return;
		}
		if (com.projecthero.mod.beamclash.BeamClash.locked(player)) {
			// v0.17.5: locked in a clash -- no drain, damage or timer
			com.projecthero.mod.beamclash.BeamClash.report(player, player.getEyePosition(), player.getLookAngle(), LASER_RANGE, beamWidth(player), beamColour(player));
			return;
		}
		if (!Worthiness.isWorthy(player) || !isHoldingThorWeapon(player)
				|| !StormEnergy.has(player, beamDrain(player) / 20.0f)) {
			player.setAttached(ModAttachments.LASER_ACTIVE, false);
			ThorVisuals.channel(player, ThorFx.CH_BEAM, false);
			return;
		}
		ThorVisuals.channel(player, ThorFx.CH_BEAM, true);

		StormEnergy.spend(player, beamDrain(player) / 20.0f);
		com.projecthero.mod.beamclash.BeamClash.report(player, player.getEyePosition(), player.getLookAngle(), LASER_RANGE, beamWidth(player), beamColour(player)); // v0.17.3
		int clock = player.getAttachedOrElse(ModAttachments.THOR_WRATH_CHARGE, 0) + 1;
		player.setAttached(ModAttachments.THOR_WRATH_CHARGE, clock);
		boolean damageTick = clock % ThorKit.BEAM_INTERVAL == 1; // v0.17.7: a hit every 1/4 s (the first on the press)

		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// Hit detection stays eye/look-based (unchanged) -- only the *rendered* beam start point
		// below is lowered, so firing the laser doesn't put a particle stream right in the player's
		// own first-person sightline.
		HitResult hit = ProjectileUtil.getHitResultOnViewVector(
				player, target -> aimable(player, target), LASER_RANGE);

		Entity hitEntity = null;
		Vec3 end;
		if (hit instanceof EntityHitResult entityHit) {
			hitEntity = entityHit.getEntity();
			end = hitEntity.position().add(0, hitEntity.getBbHeight() / 2, 0);
			if (damageTick && hitEntity instanceof LivingEntity living && ThorTargets.canAffect(player, living)) {
				ThorKit.hit(player, living, beamDamage(player), ThorKit.Kind.LIGHTNING, true, true);
				serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 10, 0.2, 0.2, 0.2, 0.2);
				if (ThorState.of(player) != ThorState.MJOLNIR) {
					ThorKit.storm(serverLevel, end, 8, 0.25, 0.25);
				}
			}
		} else if (hit != null) {
			end = hit.getLocation();
		} else {
			end = player.getEyePosition().add(player.getLookAngle().scale(LASER_RANGE));
		}

		sendLightningArc(player, LIGHTNING_ARC_SLOT_BEAM, null, hitEntity, end,
				LIGHTNING_ARC_BEAM_HOLD_TICKS, LIGHTNING_ARC_BEAM_FADE_TICKS);
		if (player.tickCount % 4 == 0) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.6f);
		}
		if (player.tickCount % 10 == 0 && ThorState.of(player) != ThorState.MJOLNIR) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.4f, 1.8f); // the axe's heavier crackle
		}
	}

	/** v0.17.7: the held beam per state -- Lightning Beam 22, Stormbreaker Beam 26 (narrow), Twin Beam 28 -- every 1/4 s. */
	public static float beamDamage(Player player) {
		return switch (ThorState.of(player)) {
			case STORMBREAKER -> ThorKit.BEAM_STORMBREAKER;
			case TWIN -> ThorKit.BEAM_TWIN;
			default -> ThorKit.BEAM_MJOLNIR;
		};
	}

	/** Storm Energy a second (Twin's own x2 on top). */
	public static float beamDrain(Player player) {
		ThorState st = ThorState.of(player);
		float base = switch (st) {
			case STORMBREAKER -> ThorKit.BEAM_DRAIN_STORMBREAKER;
			case TWIN -> ThorKit.BEAM_DRAIN_TWIN;
			default -> ThorKit.BEAM_DRAIN_MJOLNIR;
		};
		return st.cost(base);
	}

	private static float beamWidth(Player player) {
		return switch (ThorState.of(player)) {
			case STORMBREAKER -> 0.9f;
			case TWIN -> 1.8f;
			default -> 1.4f;
		};
	}

	private static int beamColour(Player player) {
		return switch (ThorState.of(player)) {
			case STORMBREAKER -> 0xFFF1C2; // v0.17.7: golden-white lightning (was the Bifrost's pink)
			case TWIN -> 0xDDEEFF; // white-blue lightning (was lilac)
			default -> 0x9FD8FF;
		};
	}

	// ---------------- thunderclap shockwave ----------------

	public static void thunderclap(ServerPlayer player) {
		if (!Worthiness.isWorthy(player) || !isHoldingThorWeapon(player)) {
			return;
		}
		if (!ThorKit.begin(player, ThorAbility.THUNDERCLAP, ThorKit.CLAP_CD, ThorKit.CLAP_COST)) {
			return;
		}

		ThorVisuals.anim(player, ThorFx.ANIM_THUNDERCLAP);
		if (player.level() instanceof ServerLevel serverLevel) {
			DamageSource clap = ThorTargets.lightning(serverLevel, player);
			// v0.14.4: squadmates (and their pets) are spared the damage, the shove and the slow alike
			for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(THUNDERCLAP_RADIUS), e -> ThorTargets.canAffect(player, e))) {
				double dx = target.getX() - player.getX();
				double dz = target.getZ() - player.getZ();
				double dist = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
				double strength = 1.4 * (1.0 - Math.min(1.0, dist / THUNDERCLAP_RADIUS)) + 0.4;
				if (ThorKit.hit(player, target, THUNDERCLAP_DAMAGE, ThorKit.Kind.LIGHTNING)) {
					ThorKit.knock(target, player.position(), strength, 0.3);
					target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5));
				}
			}

			serverLevel.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0, 0, 0, 0);
			serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
					40, THUNDERCLAP_RADIUS / 2, 0.1, THUNDERCLAP_RADIUS / 2, 0.05);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.6f);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9f, 1.2f);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.8f);
			ThorKit.sparkRing(serverLevel, player.position(), THUNDERCLAP_RADIUS * 0.8, 48);
			ThorTwinThunder.shake(serverLevel, player.position(), 0.9f, 10, 20.0);
		}
	}

	// ---------------- storm call ----------------

	/**
	 * v0.17.7: V in every state -- a local thunderstorm round him for 18 s (27 s with Mjolnir) that charges him (Storm-Charged
	 * +30%, {@link ThorStorm#isCharged}) and drops a 22-damage bolt on a nearby enemy every 2.5 s.
	 */
	public static void stormCall(ServerPlayer player) {
		if (!ThorKit.begin(player, ThorAbility.STORM_CALL, ThorKit.STORM_CALL_CD, ThorKit.STORM_CALL_COST)) {
			return;
		}

		long tick = player.level().getGameTime();
		StormCallState state = player.getAttachedOrCreate(ModAttachments.STORM_CALL_STATE);
		int duration = ThorState.of(player) == ThorState.MJOLNIR
				? Math.round(STORM_CALL_DURATION_TICKS * ThorKit.MJOLNIR_STORM_MUL) : STORM_CALL_DURATION_TICKS;
		state.activeUntilTick = tick + duration;
		state.nextStrikeTick = tick + 20;
		player.setAttached(ModAttachments.STORM_CALL_STATE, state);
		ThorVisuals.anim(player, ThorFx.ANIM_STORM);

		if (player.level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.8f);
			serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2, player.getZ(),
					60, STORM_CALL_RADIUS / 2, 1.0, STORM_CALL_RADIUS / 2, 0.05);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.PLAYERS, 1.0f, 0.8f);
			spawnVisualBolt(serverLevel, player, player.position());
		}
	}

	public static boolean isStormCallActive(Player player) {
		StormCallState state = player.getAttachedOrElse(ModAttachments.STORM_CALL_STATE, null);
		return state != null && player.level().getGameTime() < state.activeUntilTick;
	}

	private static void tickStormCall(ServerPlayer player) {
		if (!isStormCallActive(player) || !(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		// v0.13.5: every other continuous Thor effect (Laser, Wrath charge) already stops the instant
		// worthiness is lost -- Storm Call was the one exception, which is exactly what let it keep
		// calling down lightning after Thor was stripped via a command or a replaced Primary power.
		if (!Worthiness.isWorthy(player) || !ThorState.hasKit(player)) {
			player.setAttached(ModAttachments.STORM_CALL_STATE, new StormCallState());
			return;
		}
		StormCallState state = player.getAttachedOrCreate(ModAttachments.STORM_CALL_STATE);
		long tick = player.level().getGameTime();

		if (tick % 4 == 0) {
			// v0.17.7: the storm over him -- dark cloud, falling rain, the odd rumble
			serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 5, player.getZ(),
					4, STORM_CALL_RADIUS / 2, 0.3, STORM_CALL_RADIUS / 2, 0.01);
			serverLevel.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + 4.5, player.getZ(),
					12, STORM_CALL_RADIUS / 2, 0.2, STORM_CALL_RADIUS / 2, 0.0);
		}
		if (tick % 60 == 0) {
			serverLevel.playSound(null, player.getX(), player.getY() + 6, player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
					SoundSource.WEATHER, 0.5f, 0.6f + serverLevel.random.nextFloat() * 0.3f);
		}

		if (tick >= state.nextStrikeTick) {
			List<LivingEntity> hostiles = serverLevel.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(STORM_CALL_RADIUS), e -> ThorTargets.isHostile(player, e)); // v0.14.20: rule 2
			if (!hostiles.isEmpty()) {
				LivingEntity target = hostiles.get(serverLevel.random.nextInt(hostiles.size()));
				// v0.14.4: a visual-only bolt plus Thor's own damage, instead of a real vanilla bolt -- a real one hits
				// everything within 3 blocks (squadmates included), sets the ground alight and turns villagers into
				// witches. Same 3-block splash, but only on things this Thor may affect.
				spawnVisualBolt(serverLevel, player, target.position());
				for (LivingEntity hit : ThorTargets.hostilesInRadius(serverLevel, player, target.position(), STORM_CALL_SPLASH_RADIUS)) {
					strikeEntity(serverLevel, player, hit, STORM_CALL_STRIKE_DAMAGE, 0.4f);
				}
			}
			state.nextStrikeTick = tick + STORM_CALL_STRIKE_INTERVAL_TICKS;
			player.setAttached(ModAttachments.STORM_CALL_STATE, state);
		}
	}

	// ---------------- lifecycle safety nets ----------------

	/**
	 * Thor flight drives vanilla's {@code mayfly} ability, and that ability <em>is</em> saved in the
	 * player's NBT -- but {@link ModAttachments#FLYING} deliberately isn't. Logging out (or dying)
	 * mid-flight therefore used to leave a survival player with permanent creative-style flight on
	 * their next login. Both hooks below re-sync the ability to the (false) flight flag.
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		clearStaleFlightAbility(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		clearStaleFlightAbility(player);
	}

	private static void clearStaleFlightAbility(ServerPlayer player) {
		if (isFlying(player) || player.getAbilities().instabuild || !player.getAbilities().mayfly) {
			return;
		}
		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.getAbilities().setFlyingSpeed(com.projecthero.mod.hero.power.HeroFlight.VANILLA_FLYING_SPEED);
		player.onUpdateAbilities();
	}
}
