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
	public static final int LIGHTNING_RANGE = 30;
	public static final int LIGHTNING_COOLDOWN_TICKS = 50; // 2.5s
	/** v0.6.22: raised 10 -> 13. v0.13.4: raised again, 13 -> 22. */
	private static final float LIGHTNING_STRIKE_DAMAGE = 22.0f;
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
	private static final double CHAIN_LIGHTNING_CONE_RANGE = 122.0;
	/** Cone half-angle: a mob whose direction from the caster is within this of the look vector is in. */
	private static final double CHAIN_LIGHTNING_CONE_DEGREES = 40.0;
	/** v0.13.4: raised 8 -> 18. */
	private static final float CHAIN_LIGHTNING_DAMAGE = 18.0f;
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
	private static final float THUNDERCLAP_DAMAGE = 22.0f;

	// ---------------- storm call ----------------
	// Radius is centered on and follows the player (rather than staying fixed at the cast location)
	// so the storm stays useful while chasing/kiting during a fight -- see chat summary for the note.
	private static final int STORM_CALL_DURATION_TICKS = 360; // 18s
	private static final int STORM_CALL_COOLDOWN_TICKS = 2400; // 2min
	private static final double STORM_CALL_RADIUS = 12.0;
	private static final int STORM_CALL_STRIKE_INTERVAL_TICKS = 50;

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

	// ---------------- hammer volley (v0.13.4, key V) ----------------

	private static final int HAMMER_VOLLEY_DURATION_TICKS = 12 * 20;
	private static final int HAMMER_VOLLEY_COOLDOWN_TICKS = 32 * 20;

	/**
	 * Sends Mjolnir out of the player's hand to autonomously strike nearby enemies for
	 * {@link #HAMMER_VOLLEY_DURATION_TICKS} (see {@link MjolnirEntity#startVolley} for the actual
	 * targeting/flight behaviour) -- or until the player calls it back early with {@link #callHammer}.
	 * Requires the hammer specifically in the main hand, like {@link #throwMjolnir}, since it has to
	 * physically leave the hand for the volley to read as "the hammer flew off."
	 */
	public static void hammerVolley(ServerPlayer player) {
		if (!Worthiness.isWorthy(player)) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.MJOLNIR)) {
			return;
		}
		if (!consumeCooldown(player, ThorAbility.HAMMER_VOLLEY, HAMMER_VOLLEY_COOLDOWN_TICKS)) {
			return;
		}

		ItemStack sent = stack.copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		MjolnirEntity entity = new MjolnirEntity(player.level(), player);
		entity.setItem(sent);
		entity.startVolley(player, HAMMER_VOLLEY_DURATION_TICKS);
		player.level().addFreshEntity(entity);
		playThrowSound(player);
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
		// Clears BOUND_HAMMER_ID and reconciles every attribute/effect off immediately, whether or not
		// the physical hammer itself was reachable above -- this alone is what fixes the lingering HUD.
		ThorPassives.unbind(player);
		Worthiness.setScore(player, 0);

		StormEnergy.set(player, StormEnergy.MAX);
		player.setAttached(ModAttachments.LASER_ACTIVE, false);
		player.setAttached(ModAttachments.THOR_WRATH_CHARGE, 0);
		player.setAttached(ModAttachments.STORM_CALL_STATE, new StormCallState());
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

		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		if (!StormEnergy.has(player, 1.0f)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}

		setFlying(player, true);
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
		player.getAbilities().setFlyingSpeed(flying
				? com.projecthero.mod.hero.power.HeroFlight.HERO_FLYING_SPEED
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

		if (isFlying(player)) {
			if (player.getAbilities().instabuild) {
				// They've switched to creative/spectator mid-flight -- hand off to the game's own
				// flight entirely, per requirement 32. setFlying(false) only clears OUR tracking; see
				// its own instabuild guard for why it never touches their creative mayfly/flying.
				setFlying(player, false);
			} else {
				boolean holding = isHoldingMjolnir(player);
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
				} else if (player.onGround() && cooldowns(player).isReady(ThorAbility.FLIGHT, player.level().getGameTime())) {
					// Touching down ends the flight, matching vanilla's own creative-flight behavior,
					// and cleanly cancels any hammerless grace in progress -- requirement 27.
					setFlying(player, false);
				} else {
					StormEnergy.spend(player, StormEnergy.FLIGHT_DRAIN_PER_SECOND / 20.0f);
					if (!StormEnergy.has(player, 0.001f)) {
						setFlying(player, false);
					} else {
						player.getAbilities().flying = true;
						tickFlightTrail(player);
						if (grace) {
							tickHammerlessFlightGrace(player);
						}
					}
				}
			}
		}

		tickLaser(player);
		tickWrathCharge(player);
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
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		// Check readiness without consuming yet -- an insufficient-energy cast shouldn't start the
		// cooldown (mirrors the pre-refactor behavior, where addCooldown only ran after a real cast).
		if (!cooldowns(player).isReady(ThorAbility.LIGHTNING_STRIKE, player.level().getGameTime())) {
			return;
		}
		if (!StormEnergy.has(player, StormEnergy.LIGHTNING_COST)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}
		triggerCooldown(player, ThorAbility.LIGHTNING_STRIKE, LIGHTNING_COOLDOWN_TICKS);

		HitResult hit = ProjectileUtil.getHitResultOnViewVector(
				player, target -> target != player && target.isPickable(), LIGHTNING_RANGE);

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

		if (player.level() instanceof ServerLevel serverLevel) {
			spawnVisualBolt(serverLevel, player, pos);

			if (primaryTarget instanceof LivingEntity livingPrimary) {
				Set<Entity> struck = new HashSet<>();
				struck.add(livingPrimary);
				strikeEntity(serverLevel, player, livingPrimary, LIGHTNING_STRIKE_DAMAGE);
				chainLightning(serverLevel, player, livingPrimary, LIGHTNING_STRIKE_DAMAGE, struck);
			}
		}

		StormEnergy.spend(player, StormEnergy.LIGHTNING_COST);
	}

	/**
	 * v0.7.5: soft aim assist for Lightning Strike, generalized in v0.13.3 so God of Thunder's Wrath
	 * can reuse it with its own range/cone -- returns the living entity whose direction from the
	 * player's eye is closest to the look vector, provided it is inside {@code angleDegrees}, within
	 * {@code range}, and in line of sight; null if nothing qualifies. One AABB-bounded query plus a
	 * cone filter, never an unbounded scan.
	 */
	private static LivingEntity findAimAssistTarget(ServerPlayer player, double range, double angleDegrees) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(angleDegrees));
		LivingEntity best = null;
		for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(range),
				e -> e != player && e.isAlive() && e.isPickable()
						&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
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
		float jumpDamage = incomingDamage * CHAIN_DAMAGE_FALLOFF;
		List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
				from.getBoundingBox().inflate(CHAIN_RANGE),
				e -> e.isAlive() && e instanceof Monster && !struck.contains(e)));
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
		DamageSource source = level.damageSources().lightningBolt();
		igniteIfAnimal(target);
		if (target.hurt(source, damage)) {
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
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		if (!consumeCooldown(player, ThorAbility.CHAIN_LIGHTNING, CHAIN_LIGHTNING_COOLDOWN_TICKS)) {
			return;
		}
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double cosLimit = Math.cos(Math.toRadians(CHAIN_LIGHTNING_CONE_DEGREES));

		List<LivingEntity> inCone = new ArrayList<>();
		for (LivingEntity candidate : serverLevel.getEntitiesOfClass(LivingEntity.class,
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
		if (inCone.isEmpty()) {
			// Nothing in front to chain through -- a quiet whiff, not a wasted loud cast.
			return;
		}
		inCone.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));

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
	 * Hostile mobs, valid combat targets, and players (only if the server allows PvP) -- never the
	 * caster, non-living decoration (armor stands, item entities, XP orbs), or Mjolnir itself. See
	 * requirements 10-11.
	 */
	private static boolean isValidChainLightningTarget(Player caster, Entity target) {
		if (target == caster || !target.isAlive() || !target.isPickable()) {
			return false;
		}
		if (!(target instanceof LivingEntity living) || living instanceof net.minecraft.world.entity.decoration.ArmorStand) {
			return false;
		}
		if (target instanceof Player) {
			MinecraftServer server = caster.level().getServer();
			return server != null && server.isPvpAllowed();
		}
		return true;
	}

	// ---------------- god of thunder's wrath (ultimate, key Z) ----------------

	/**
	 * v0.6.22 ultimate. Thor calls down a single colossal bolt on the exact point he is looking at
	 * (out to {@link #GOD_OF_THUNDER_RANGE} blocks): {@link #GOD_OF_THUNDER_DAMAGE} to everything
	 * within {@link #GOD_OF_THUNDER_RADIUS} of the impact, a real (non-visual) {@link LightningBolt}
	 * for the spectacle, a 60-second cooldown, and it drains 150 Storm Energy -- it is meant to be a
	 * fight-ender you can only reach for once.
	 *
	 * <p>v0.6.23: this is no longer an instant cast. The player must <b>hold Z for 5 seconds</b> to
	 * charge it ({@link #setWrathCharging} / {@link #tickWrathCharge}); this method is the payload the
	 * charge fires when it completes.
	 */
	public static void godOfThundersWrath(ServerPlayer player) {
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		if (!cooldowns(player).isReady(ThorAbility.GOD_OF_THUNDER, player.level().getGameTime())) {
			return;
		}
		if (!StormEnergy.has(player, StormEnergy.GOD_OF_THUNDER_MIN)) {
			player.displayClientMessage(
					net.minecraft.network.chat.Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		triggerCooldown(player, ThorAbility.GOD_OF_THUNDER, GOD_OF_THUNDER_COOLDOWN_TICKS);
		StormEnergy.spend(player, StormEnergy.GOD_OF_THUNDER_COST);

		HitResult hit = ProjectileUtil.getHitResultOnViewVector(
				player, target -> target != player && target.isPickable(), GOD_OF_THUNDER_RANGE);
		Vec3 pos;
		if (hit instanceof EntityHitResult entityHit) {
			pos = entityHit.getEntity().position();
		} else {
			// v0.13.3: same soft aim assist Lightning Strike uses (tighter cone -- see the constant's
			// javadoc) so a near-miss on a distant enemy still lands the ultimate on them instead of
			// the bare ground/air point.
			LivingEntity assisted = findAimAssistTarget(player, GOD_OF_THUNDER_RANGE, GOD_OF_THUNDER_AIM_ASSIST_DEGREES);
			if (assisted != null) {
				pos = assisted.position();
			} else if (hit != null && hit.getType() != HitResult.Type.MISS) {
				pos = hit.getLocation();
			} else {
				pos = player.getEyePosition().add(player.getLookAngle().scale(GOD_OF_THUNDER_RANGE));
			}
		}

		spawnWrathBolt(serverLevel, player, pos);
		// v0.13.4: a barrage of extra bolts on jittered points inside the radius, so the ultimate reads
		// as a storm crashing down rather than one lone strike -- damage is still the single AABB sweep
		// below, these are purely the spectacle.
		for (int i = 0; i < GOD_OF_THUNDER_EXTRA_BOLTS; i++) {
			double jx = pos.x + (serverLevel.random.nextDouble() - 0.5) * GOD_OF_THUNDER_RADIUS * 1.6;
			double jz = pos.z + (serverLevel.random.nextDouble() - 0.5) * GOD_OF_THUNDER_RADIUS * 1.6;
			spawnWrathBolt(serverLevel, player, new Vec3(jx, pos.y, jz));
		}

		DamageSource source = serverLevel.damageSources().lightningBolt();
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(pos, pos).inflate(GOD_OF_THUNDER_RADIUS);
		for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area,
				e -> e != player && e.isAlive())) {
			if (com.projecthero.mod.hero.power.AbilityHelpers.distanceSqToBox(target, pos)
					> GOD_OF_THUNDER_RADIUS * GOD_OF_THUNDER_RADIUS + 4.0) {
				continue;
			}
			igniteIfAnimal(target);
			// v0.10.11: a one-shot ultimate must land its full hit even if the boss is mid-i-frame from
			// a melee swing -- clear the window for non-players first.
			if (!(target instanceof net.minecraft.world.entity.player.Player)) {
				target.invulnerableTime = 0;
			}
			target.hurt(source, GOD_OF_THUNDER_DAMAGE);
		}

		serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z, 120,
				GOD_OF_THUNDER_RADIUS, 2.0, GOD_OF_THUNDER_RADIUS, 0.3);
		serverLevel.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 4, 0.1, 0.1, 0.1, 0.0);
		serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 4.0f, 0.6f);
		serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 0.7f);
	}

	/** A single visual-only bolt at {@code pos} -- damage for the ultimate is always the one AABB sweep
	 * in {@link #godOfThundersWrath}, this is purely the spectacle, so any number of these can be spawned
	 * without touching how much damage actually lands. */
	private static void spawnWrathBolt(ServerLevel serverLevel, ServerPlayer player, Vec3 pos) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
		if (bolt != null) {
			bolt.moveTo(pos.x, pos.y, pos.z);
			bolt.setVisualOnly(true);
			bolt.setCause(player);
			serverLevel.addFreshEntity(bolt);
		}
	}

	/**
	 * Start / stop charging God of Thunder's Wrath (the Z key press / release edge). The ultimate can
	 * never be cast instantly -- the player must hold Z for {@link #WRATH_CHARGE_TICKS} (5 s), during
	 * which the HUD shows a buildup bar. Releasing early cancels the charge harmlessly (no cooldown,
	 * no energy spent -- those only happen when {@link #godOfThundersWrath} actually fires).
	 */
	public static void setWrathCharging(ServerPlayer player, boolean charging) {
		int current = player.getAttachedOrElse(ModAttachments.THOR_WRATH_CHARGE, 0);
		if (!charging) {
			if (current > 0) {
				player.setAttached(ModAttachments.THOR_WRATH_CHARGE, 0);
				if (player.level() instanceof ServerLevel sl) {
					sl.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
				}
			}
			return;
		}
		if (current > 0) {
			return; // already charging
		}
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		if (!cooldowns(player).isReady(ThorAbility.GOD_OF_THUNDER, player.level().getGameTime())) {
			int remain = cooldowns(player).remainingTicks(ThorAbility.GOD_OF_THUNDER, player.level().getGameTime());
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.ability.on_cooldown",
					net.minecraft.network.chat.Component.translatable("projecthero.thor.ability.god_of_thunders_wrath"),
					String.format(java.util.Locale.ROOT, "%.1f", remain / 20.0f)), true);
			return;
		}
		if (!StormEnergy.has(player, StormEnergy.GOD_OF_THUNDER_MIN)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.no_storm_energy"), true);
			return;
		}
		player.setAttached(ModAttachments.THOR_WRATH_CHARGE, 1);
		if (player.level() instanceof ServerLevel sl) {
			sl.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_ACTIVATE,
					SoundSource.PLAYERS, 0.8f, 0.7f);
		}
	}

	/** Advances an in-progress Wrath charge; fires {@link #godOfThundersWrath} at 5 s. */
	private static void tickWrathCharge(ServerPlayer player) {
		int c = player.getAttachedOrElse(ModAttachments.THOR_WRATH_CHARGE, 0);
		if (c <= 0) {
			return;
		}
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)
				|| !StormEnergy.has(player, StormEnergy.GOD_OF_THUNDER_MIN)) {
			player.setAttached(ModAttachments.THOR_WRATH_CHARGE, 0);
			return;
		}
		c++;
		if (player.level() instanceof ServerLevel sl) {
			double a = Math.min(1.0, c / (double) WRATH_CHARGE_TICKS);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
					(int) (2 + a * 12), 0.7, 1.0, 0.7, 0.25);
			if (c % 8 == 0) {
				sl.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT,
						SoundSource.PLAYERS, 0.5f, 0.5f + (float) a);
			}
		}
		if (c >= WRATH_CHARGE_TICKS) {
			player.setAttached(ModAttachments.THOR_WRATH_CHARGE, 0);
			godOfThundersWrath(player);
		} else {
			player.setAttached(ModAttachments.THOR_WRATH_CHARGE, c);
		}
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
	private static void sendLightningArc(ServerPlayer caster, int slot, Entity fromEntity, Entity toEntity,
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

		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		if (!StormEnergy.has(player, StormEnergy.LASER_DRAIN_PER_SECOND / 20.0f)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}

		player.setAttached(ModAttachments.LASER_ACTIVE, true);
	}

	private static void tickLaser(ServerPlayer player) {
		if (!isLaserActive(player)) {
			return;
		}
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)
				|| !StormEnergy.has(player, StormEnergy.LASER_DRAIN_PER_SECOND / 20.0f)) {
			player.setAttached(ModAttachments.LASER_ACTIVE, false);
			return;
		}

		StormEnergy.spend(player, StormEnergy.LASER_DRAIN_PER_SECOND / 20.0f);

		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// Hit detection stays eye/look-based (unchanged) -- only the *rendered* beam start point
		// below is lowered, so firing the laser doesn't put a particle stream right in the player's
		// own first-person sightline.
		HitResult hit = ProjectileUtil.getHitResultOnViewVector(
				player, target -> target != player && target.isPickable(), LASER_RANGE);

		Entity hitEntity = null;
		Vec3 end;
		if (hit instanceof EntityHitResult entityHit) {
			hitEntity = entityHit.getEntity();
			end = hitEntity.position().add(0, hitEntity.getBbHeight() / 2, 0);
			if (hitEntity instanceof LivingEntity living) {
				igniteIfAnimal(living);
				living.hurt(serverLevel.damageSources().lightningBolt(), LASER_DAMAGE_PER_TICK);
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
	}

	// ---------------- thunderclap shockwave ----------------

	public static void thunderclap(ServerPlayer player) {
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		if (!StormEnergy.has(player, StormEnergy.THUNDERCLAP_COST)) {
			player.displayClientMessage(
					net.minecraft.network.chat.Component.translatable("message.projecthero.no_storm_energy"), true);
			return;
		}
		if (!consumeCooldown(player, ThorAbility.THUNDERCLAP, THUNDERCLAP_COOLDOWN_TICKS)) {
			return;
		}
		StormEnergy.spend(player, StormEnergy.THUNDERCLAP_COST);

		if (player.level() instanceof ServerLevel serverLevel) {
			DamageSource clap = serverLevel.damageSources().lightningBolt();
			for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(THUNDERCLAP_RADIUS), e -> e != player && e.isAlive())) {
				double dx = target.getX() - player.getX();
				double dz = target.getZ() - player.getZ();
				double dist = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
				double strength = 1.4 * (1.0 - Math.min(1.0, dist / THUNDERCLAP_RADIUS)) + 0.4;
				igniteIfAnimal(target);
				target.hurt(clap, THUNDERCLAP_DAMAGE);
				target.knockback(strength, -dx / dist, -dz / dist);
				target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5));
			}

			serverLevel.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0, 0, 0, 0);
			serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
					40, THUNDERCLAP_RADIUS / 2, 0.1, THUNDERCLAP_RADIUS / 2, 0.05);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.6f);
		}
	}

	// ---------------- storm call ----------------

	public static void stormCall(ServerPlayer player) {
		if (!Worthiness.isWorthy(player) || !isHoldingMjolnir(player)) {
			return;
		}
		if (!consumeCooldown(player, ThorAbility.STORM_CALL, STORM_CALL_COOLDOWN_TICKS)) {
			return;
		}

		long tick = player.level().getGameTime();
		StormCallState state = player.getAttachedOrCreate(ModAttachments.STORM_CALL_STATE);
		state.activeUntilTick = tick + STORM_CALL_DURATION_TICKS;
		state.nextStrikeTick = tick + 20;
		player.setAttached(ModAttachments.STORM_CALL_STATE, state);

		if (player.level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.8f);
			serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2, player.getZ(),
					60, STORM_CALL_RADIUS / 2, 1.0, STORM_CALL_RADIUS / 2, 0.05);
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
		if (!Worthiness.isWorthy(player)) {
			player.setAttached(ModAttachments.STORM_CALL_STATE, new StormCallState());
			return;
		}
		StormCallState state = player.getAttachedOrCreate(ModAttachments.STORM_CALL_STATE);
		long tick = player.level().getGameTime();

		if (tick % 10 == 0) {
			serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 3, player.getZ(),
					3, STORM_CALL_RADIUS / 2, 0.2, STORM_CALL_RADIUS / 2, 0.02);
		}

		if (tick >= state.nextStrikeTick) {
			List<Monster> hostiles = serverLevel.getEntitiesOfClass(Monster.class,
					player.getBoundingBox().inflate(STORM_CALL_RADIUS), Entity::isAlive);
			if (!hostiles.isEmpty()) {
				Monster target = hostiles.get(serverLevel.random.nextInt(hostiles.size()));
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
				if (bolt != null) {
					bolt.moveTo(target.getX(), target.getY(), target.getZ());
					bolt.setCause(player);
					serverLevel.addFreshEntity(bolt);
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
