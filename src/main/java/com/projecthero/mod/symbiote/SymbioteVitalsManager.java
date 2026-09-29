package com.projecthero.mod.symbiote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * The Symbiote health bar and the two toggles that hang off {@link SymbioteVitals} (Symbiote Blade,
 * Symbiote Spikes), plus the passive behaviours the bonded organism does on its own -- regenerate,
 * warn its host when it is running low, catch incoming arrows, and (while the suit is worn) turn the
 * host invisible after a long crouch.
 *
 * <p>All of this is keyed off {@link Symbiote#hasSymbiote} rather than the suit being worn (v0.9.23:
 * "the Symbiote is always with you"), except the sneak-invisibility, which the spec explicitly gates
 * on the armour being on.
 */
public final class SymbioteVitalsManager {
	/** Full Biomass -- the Symbiote's own life pool, shown in game as the "Biomass" bar. */
	public static final float MAX_HP = 200.0f;
	/**
	 * Regen once safe, in Biomass per second, after {@link #REGEN_SAFE_TICKS} out of combat. v0.13.21: 9 -> 8 on
	 * paper, but the old 9 only ever landed a quarter of the time (the per-tick gain was recomputed from the saved
	 * value and only saved every 4th tick, so three ticks in four were thrown away -- 2.25/s in practice). It is
	 * now paid in {@link #REGEN_STEP_TICKS}-tick installments that are all kept: 8/s for real, ~3.5x as fast.
	 */
	public static final float REGEN_PER_SECOND = 8.0f;
	/** v0.13.21: regen (and its save) happens once every this many ticks, a whole installment at a time. */
	private static final int REGEN_STEP_TICKS = 4;
	/** Suit up and the Biomass climbs slower -- wearing the armour is a drain of its own. v0.13.21: 0.3 -> 0.5 (4/s). */
	public static final float SUITED_REGEN_FACTOR = 0.5f;
	/** Biomass only regenerates after this long with no damage dealt or taken. v0.13.21: 5 s -> 3 s. */
	public static final int REGEN_SAFE_TICKS = 60;
	/**
	 * Once the bar has emptied, abilities stay locked -- and (v0.13.21) the suit stays off, and will not form or
	 * wrap the host on its own -- until it climbs back to this fraction (40 Biomass).
	 */
	public static final float RECOVER_FRACTION = 0.20f;
	/** The Symbiote starts warning its host below this fraction. */
	private static final float WARN_FRACTION = 0.35f;
	/**
	 * The host takes every hit in full -- the damage is <em>not</em> split. On top of that, the Symbiote
	 * loses this fraction of the hit from its own Biomass (a parallel drain, not a redirect).
	 */
	public static final float BIOMASS_HIT_FRACTION = 0.4f;

	/** Legacy Symbiote Blade charge cap -- v0.13.19 removed the blade's time limit; kept for the save format. */
	public static final float BLADE_MAX = 300.0f;
	private static final float BLADE_BONUS_DAMAGE = 5.0f;
	/** v0.13.19: the blade has no time limit -- it costs 0.3 Biomass a second while it is out instead. */
	public static final float BLADE_BIOMASS_PER_SECOND = 0.3f;
	/** v0.13.19: the Symbiote Shield has no time limit -- it costs 0.5 Biomass a second while it is up. */
	public static final float SHIELD_BIOMASS_PER_SECOND = 0.5f;
	/** v0.13.19: a Symbiote revive is ready again ten minutes after the last one (no Biomass cost). */
	public static final int RESURRECT_COOLDOWN_TICKS = 12000;
	/**
	 * v0.13.21: Symbiote Spider-Man and Agent Venom already bring a whole hero's kit of their own (Spider-Man's
	 * Resistance, the Punisher's armour-grade suit) -- their revive waits twice as long, twenty minutes.
	 */
	public static final int HERO_HOST_RESURRECT_COOLDOWN_TICKS = 24000;

	/** A wild bond takes this long to settle: protection but no abilities, and the host feels sick. */
	public static final int BONDING_TICKS = 700; // 35 s
	private static final Map<Integer, Long> LAST_BOND_FX = new ConcurrentHashMap<>();

	private static final int WARN_INTERVAL_TICKS = 220;   // ~11 s between "I can't keep this up" lines
	private static final int SNEAK_INVIS_TICKS = 100;     // 5 s of unbroken crouch (suit on) -> invisible
	private static final double ARROW_CATCH_RADIUS = 2.4;
	private static final float ARROW_CATCH_HP_COST = 3.0f;

	private static final ResourceLocation BLADE_ATTACK = ProjectHeroMod.id("symbiote_blade_attack");
	private static final ResourceLocation BLADE_SPEED = ProjectHeroMod.id("symbiote_blade_speed");

	private static final String[] LOW_WARNINGS = {
			"message.projecthero.symbiote.warn_1", "message.projecthero.symbiote.warn_2",
			"message.projecthero.symbiote.warn_3", "message.projecthero.symbiote.warn_4"
	};

	/** Per-player crouch-start game time while the suit is on -- transient. */
	private static final Map<Integer, Long> SNEAK_START = new ConcurrentHashMap<>();
	/** Per-player last low-health warning game time -- transient. */
	private static final Map<Integer, Long> LAST_WARN = new ConcurrentHashMap<>();
	/** Per-player last game time a hit was dealt or taken -- gates Biomass regen and feeds the dialogue. */
	private static final Map<Integer, Long> LAST_COMBAT = new ConcurrentHashMap<>();
	/** v0.13.19: game time of the last blade / shield Biomass charge -- at most one per tick, however often tick() runs. */
	private static final Map<Integer, Long> LAST_DRAIN = new ConcurrentHashMap<>();
	/** Per-player last game time the Symbiote lost a large chunk of Biomass from a single hit. */
	private static final Map<Integer, Long> LAST_BIG_HIT = new ConcurrentHashMap<>();
	/** v0.13.21: game time of the last Biomass regen installment -- at most one per tick, however often tick() runs. */
	private static final Map<Integer, Long> LAST_REGEN = new ConcurrentHashMap<>();
	/** v0.13.21: the (empty) hotbar slot the Symbiote Blade was last held in -- a scroll onto an item snaps back here. */
	private static final Map<Integer, Integer> BLADE_SLOT = new ConcurrentHashMap<>();

	private SymbioteVitalsManager() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			// v0.13.21: the Biomass drain lives here, after the hit, rather than in SymbioteDamageRules' ALLOW_DAMAGE:
			// only damage that actually LANDED costs Biomass. A squadmate's blow (vetoed by Squads further down the
			// ALLOW_DAMAGE chain), one eaten by invulnerability frames, a fall -- none of them touch the bar. The
			// squadmate check is repeated here as a belt-and-braces guard.
			if (entity instanceof ServerPlayer host && taken > 0.0f && !source.is(DamageTypeTags.IS_FALL)
					&& !Squads.areAllies(host, source.getEntity())) {
				onHostHit(host, taken);
			}

			// Any hit a bonded host lands counts as "in combat" for the Biomass regen gate.
			if (source.getEntity() instanceof ServerPlayer attackerPlayer && Symbiote.hasSymbiote(attackerPlayer)
					&& entity != attackerPlayer && taken > 0.0f) {
				markCombat(attackerPlayer);
			}

			// Symbiote Blade: an extra armour-bypassing bite on a melee hit while it is out.
			if (source.getEntity() instanceof ServerPlayer sp && entity instanceof LivingEntity living
					&& living != sp && taken > 0.0f && bladeActive(sp) && !Squads.areAllies(sp, living)
					&& source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) {
				living.invulnerableTime = 0;
				living.hurt(sp.damageSources().magic(), 2.0f);
				if (sp.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SQUID_INK, living.getX(),
							living.getY() + living.getBbHeight() * 0.5, living.getZ(), 10, 0.2, 0.3, 0.2, 0.02);
				}
			}

			// Symbiote Spikes (C): reflect melee damage like Thorns IV.
			if (entity instanceof ServerPlayer player && Symbiote.hasSymbiote(player)
					&& thornsMode(player) && taken > 0.0f
					&& source.getEntity() instanceof LivingEntity attacker && attacker != player
					&& !Squads.areAllies(player, attacker)
					&& player.distanceToSqr(attacker) < 36.0 && player.getRandom().nextFloat() < 0.66f) {
				float reflect = 2.0f + player.getRandom().nextFloat() * 2.0f;
				attacker.hurt(player.damageSources().thorns(player), reflect);
				if (player.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SQUID_INK, attacker.getX(),
							attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(), 8, 0.25, 0.3, 0.25, 0.01);
				}
			}
		});

		// v0.13.21: nothing can be used from the blade hand while the Symbiote Blade is out. The server keeps the
		// hand empty every tick (keepBladeHandEmpty); this closes the one-tick window before it does. Runs on both
		// sides -- the vitals attachment is synced.
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (hand == InteractionHand.MAIN_HAND && !stack.isEmpty() && bladeOut(player)) {
				return InteractionResultHolder.fail(stack);
			}
			return InteractionResultHolder.pass(stack);
		});
	}

	// ---------------- state ----------------

	public static SymbioteVitals vitals(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SYMBIOTE_VITALS);
	}

	static void save(ServerPlayer player, SymbioteVitals v) {
		player.setAttached(ModAttachments.SYMBIOTE_VITALS, v);
	}

	/** Can the Symbiote's abilities be used right now? (Bonded, bond has settled, health bar not spent.) */
	public static boolean usable(ServerPlayer player) {
		if (!Symbiote.hasSymbiote(player)) {
			return false;
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null) {
			return true;
		}
		return !v.broken && v.bondingUntil <= player.level().getGameTime();
	}

	/** True while a freshly-formed wild bond is still settling in (protection, but no abilities). */
	public static boolean bonding(ServerPlayer player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v != null && v.bondingUntil > player.level().getGameTime();
	}

	/** Ticks left in the bonding phase, 0 if not bonding. */
	public static long bondingTicksLeft(ServerPlayer player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v == null ? 0L : Math.max(0L, v.bondingUntil - player.level().getGameTime());
	}

	/** Start the initial bonding phase for a player who just picked up a wild Symbiote. */
	public static void beginBonding(ServerPlayer player) {
		SymbioteVitals c = vitals(player).copy();
		c.bondingUntil = player.level().getGameTime() + BONDING_TICKS;
		save(player, c);
		if (player.level() instanceof ServerLevel level) {
			SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 1.2f, 0.35f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4f, 0.8f);
		}
	}

	public static boolean bladeActive(ServerPlayer player) {
		return bladeOut(player);
	}

	/** {@link #bladeActive} for either side (the attachment is synced) -- the client-side item-use guard needs it. */
	public static boolean bladeOut(Player player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v != null && v.bladeActive;
	}

	/**
	 * v0.13.21: a Normal host whose Biomass has run dry cannot wear the suit -- it melts off at zero and will not
	 * form again (by H, by the Symbiote's own protective wrap, or on a resurrection) until the bar has climbed back
	 * to {@link #RECOVER_FRACTION}, the same threshold that lifts the ability lock. Hosts without a Biomass bar
	 * (Symbiote Spider-Man, Agent Venom) are never locked.
	 */
	public static boolean suitLocked(Player player) {
		if (!Symbiote.isNormalHost(player)) {
			return false;
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v != null && (v.broken || v.hp <= 0.0f);
	}

	public static boolean thornsMode(ServerPlayer player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v != null && v.thornsMode;
	}

	// ---------------- damage / Biomass drain ----------------

	/**
	 * The host has just been dealt {@code dealt} damage <em>in full</em> (v0.9.24: no more redirect --
	 * the player takes everything). The Symbiote loses {@link #BIOMASS_HIT_FRACTION} of that from its own
	 * Biomass, in parallel. At zero the bar breaks and every ability locks until it recovers.
	 */
	public static void onHostHit(ServerPlayer player, float dealt) {
		if (dealt <= 0.0f || !Symbiote.isNormalHost(player)) {
			return;
		}
		markCombat(player);
		SymbioteVitals v = vitals(player);
		if (v.hp <= 0.0f) {
			return;
		}
		float drain = dealt * BIOMASS_HIT_FRACTION;
		SymbioteVitals c = v.copy();
		c.hp = Math.max(0.0f, v.hp - drain);
		boolean nowBroken = c.hp <= 0.0f && !v.broken;
		if (c.hp <= 0.0f) {
			c.broken = true;
		}
		save(player, c);
		if (drain >= MAX_HP * 0.12f) {
			LAST_BIG_HIT.put(player.getId(), player.level().getGameTime());
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(),
					10, 0.35, 0.6, 0.35, 0.02);
			if (nowBroken) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.WARDEN_DEATH, SoundSource.PLAYERS, 0.6f, 1.6f);
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent")
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
			}
		}
	}

	/** A direct Biomass cost (arrow catch, ability spend) -- no combat marking. */
	static void spendBiomass(ServerPlayer player, float amount) {
		SymbioteVitals v = vitals(player);
		if (v.hp <= 0.0f) {
			return;
		}
		SymbioteVitals c = v.copy();
		c.hp = Math.max(0.0f, v.hp - amount);
		if (c.hp <= 0.0f) {
			c.broken = true;
		}
		save(player, c);
	}

	public static void markCombat(ServerPlayer player) {
		LAST_COMBAT.put(player.getId(), player.level().getGameTime());
	}

	/** Ticks since this player last dealt or took a hit ({@link Long#MAX_VALUE} if never). */
	public static long ticksSinceCombat(ServerPlayer player, long now) {
		Long last = LAST_COMBAT.get(player.getId());
		return last == null ? Long.MAX_VALUE : now - last;
	}

	public static boolean outOfCombat(ServerPlayer player, long now) {
		return ticksSinceCombat(player, now) >= REGEN_SAFE_TICKS;
	}

	public static float biomass(ServerPlayer player) {
		return vitals(player).hp;
	}

	public static float biomassFraction(ServerPlayer player) {
		return Math.max(0.0f, Math.min(1.0f, vitals(player).hp / MAX_HP));
	}

	public static boolean regenerating(ServerPlayer player, long now) {
		return outOfCombat(player, now) && vitals(player).hp < MAX_HP;
	}

	public static boolean tookBigHitRecently(ServerPlayer player, long now) {
		Long last = LAST_BIG_HIT.get(player.getId());
		return last != null && now - last < 60L;
	}

	/** A sound attack sheathes the Symbiote Blade and drops Symbiote Spikes. */
	static void disruptToggles(ServerPlayer player) {
		SymbioteVitals v = vitals(player);
		if (!v.bladeActive && !v.thornsMode) {
			return;
		}
		SymbioteVitals c = v.copy();
		c.bladeActive = false;
		c.thornsMode = false;
		save(player, c);
		reconcileBlade(player, false);
	}

	// ---------------- blade / spikes toggles ----------------

	/** Symbiote Blade (V) toggle. Needs an empty main hand and some charge left. */
	public static void toggleBlade(ServerPlayer player) {
		SymbioteVitals v = vitals(player);
		if (v.bladeActive) {
			setBlade(player, v, false);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_off"), true);
			return;
		}
		if (v.broken) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
			return;
		}
		if (!player.getMainHandItem().isEmpty()) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_hands_full"), true);
			return;
		}
		if (v.hp <= 0.0f) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_spent"), true);
			return;
		}
		setBlade(player, v, true);
		BLADE_SLOT.put(player.getId(), player.getInventory().selected);
		SymbioteAnim.play(player, SymbioteAnim.BLADE_FORM);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_on")
				.withStyle(ChatFormatting.DARK_PURPLE), true);
		if (player.level() instanceof ServerLevel level) {
			SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 0.9f, 0.4f);
			SymbioteSounds.lash(player, 0.8f, 1.2f);
		}
	}

	private static void setBlade(ServerPlayer player, SymbioteVitals v, boolean on) {
		SymbioteVitals c = v.copy();
		c.bladeActive = on;
		save(player, c);
		reconcileBlade(player, on);
	}

	private static void reconcileBlade(ServerPlayer player, boolean on) {
		if (on) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, BLADE_ATTACK, BLADE_BONUS_DAMAGE,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ATTACK_SPEED, BLADE_SPEED, 0.5,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, BLADE_ATTACK);
			PowerToggles.clearModifier(player, Attributes.ATTACK_SPEED, BLADE_SPEED);
		}
	}

	/** Symbiote Spikes (C) toggle -- Thorns IV while on. */
	public static void toggleThorns(ServerPlayer player) {
		SymbioteVitals v = vitals(player);
		SymbioteVitals c = v.copy();
		c.thornsMode = !v.thornsMode;
		save(player, c);
		if (c.thornsMode) {
			SymbioteAnim.play(player, SymbioteAnim.SPIKES_FLEX);
		}
		player.displayClientMessage(Component.translatable(c.thornsMode
						? "message.projecthero.symbiote.spikes_on" : "message.projecthero.symbiote.spikes_off")
				.withStyle(ChatFormatting.DARK_PURPLE), true);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(),
					16, 0.4, 0.5, 0.4, 0.02);
		}
	}

	// ---------------- per-tick upkeep ----------------

	public static void tick(ServerPlayer player) {
		if (!Symbiote.hasSymbiote(player)) {
			SNEAK_START.remove(player.getId());
			LAST_BOND_FX.remove(player.getId());
			return;
		}
		if (tickBonding(player)) {
			return; // still settling -- no vitals upkeep, no abilities
		}
		if (!Symbiote.isNormalHost(player)) {
			SNEAK_START.remove(player.getId());
			return; // Symbiote Spider-Man has no Biomass bar, cloak or blade upkeep
		}
		SymbioteVitals v = vitals(player);
		SymbioteVitals c = v.copy();
		boolean dirty = false;
		boolean transition = false;

		// Regeneration -- REGEN_PER_SECOND, but only after REGEN_SAFE_TICKS clear of combat. A broken bar
		// climbs the same way (that is how the ability lock lifts), so staying in a fight keeps it locked.
		// v0.13.19: the blade and the shield feed on Biomass while they are out, and it does not grow back
		// while it is being spent.
		long nowTime = player.level().getGameTime();
		boolean shieldUp = Symbiote.state(player).shieldHeld;
		float drain = (c.bladeActive ? BLADE_BIOMASS_PER_SECOND : 0.0f) + (shieldUp ? SHIELD_BIOMASS_PER_SECOND : 0.0f);
		if (drain > 0.0f && c.hp > 0.0f) {
			// charged in 4-tick installments on the game clock, each saved at once, so none of it is lost
			// between the throttled saves below
			if (nowTime % 4L == 0L && !Long.valueOf(nowTime).equals(LAST_DRAIN.put(player.getId(), nowTime))) {
				c.hp = Math.max(0.0f, c.hp - drain * 4.0f / 20.0f);
				transition = true;
			}
			if (c.hp <= 0.0f && !c.broken) {
				c.broken = true;
				transition = true;
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent")
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
			}
		} else if (c.hp < MAX_HP && outOfCombat(player, nowTime)) {
			// v0.13.21: paid in whole REGEN_STEP_TICKS installments on the game clock and saved on the spot, like the
			// drain above. The old per-tick gain was recomputed from the saved value but only saved every 4th tick,
			// so three ticks' worth in four simply vanished.
			if (nowTime % REGEN_STEP_TICKS == 0L && !Long.valueOf(nowTime).equals(LAST_REGEN.put(player.getId(), nowTime))) {
				float perSecond = Symbiote.isActive(player) ? REGEN_PER_SECOND * SUITED_REGEN_FACTOR : REGEN_PER_SECOND;
				c.hp = Math.min(MAX_HP, c.hp + perSecond * REGEN_STEP_TICKS / 20.0f);
				dirty = true;
			}
		}
		if (c.broken && c.hp >= MAX_HP * RECOVER_FRACTION) {
			c.broken = false;
			transition = true;
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.recovered")
					.withStyle(ChatFormatting.DARK_PURPLE), true);
		}

		// Blade upkeep (v0.13.19: no time limit, no particle sheath -- it has a model now): sheathe it if the
		// Biomass runs dry. v0.13.21: an item reaching the hand no longer sheathes it -- the hand is kept empty
		// instead (keepBladeHandEmpty); only when there is truly nowhere to put the item does the blade give way.
		if (c.bladeActive) {
			if (c.broken || c.hp <= 0.0f) {
				c.bladeActive = false;
				reconcileBlade(player, false);
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_spent"), true);
				transition = true;
			} else if (!keepBladeHandEmpty(player)) {
				c.bladeActive = false;
				reconcileBlade(player, false);
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_hands_full"), true);
				transition = true;
			}
		} else {
			BLADE_SLOT.remove(player.getId());
			reconcileBlade(player, false);
		}

		// Persist on a real transition immediately; the regen installment (dirty) only comes every
		// REGEN_STEP_TICKS, so the synced attachment is still not rewritten and re-broadcast every single tick.
		if (transition || dirty) {
			save(player, c);
		}

		// v0.13.21: out of Biomass, out of the suit -- it melts off, and suitLocked keeps it off until the bar recovers.
		if (c.broken && Symbiote.isActive(player)) {
			Symbiote.collapseSuit(player);
		}

		// Low-health warnings from the Symbiote itself.
		if (c.hp < MAX_HP * WARN_FRACTION) {
			long now = player.level().getGameTime();
			Long last = LAST_WARN.get(player.getId());
			if (last == null || now - last >= WARN_INTERVAL_TICKS) {
				LAST_WARN.put(player.getId(), now);
				String key = LOW_WARNINGS[player.getRandom().nextInt(LOW_WARNINGS.length)];
				player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_RED), true);
			}
		}

		sneakInvisibility(player);
		catchArrows(player, c);
	}

	/**
	 * v0.13.21: nothing can be held in the hand the Symbiote Blade grows from. Scroll (or number-key) onto a slot
	 * with an item in it and the selection snaps straight back to the empty slot the blade was in; an item that
	 * lands in the blade's own slot (a pickup) is tucked into the backpack, or failing that another empty hotbar
	 * slot. Nothing is ever dropped or deleted.
	 *
	 * @return false only when the hand holds an item and there is nowhere at all to put it -- the caller then
	 *         sheathes the blade instead, leaving the item where it is
	 */
	private static boolean keepBladeHandEmpty(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int held = inv.selected;
		if (inv.getItem(held).isEmpty()) {
			BLADE_SLOT.put(player.getId(), held);
			return true;
		}
		Integer last = BLADE_SLOT.get(player.getId());
		if (last != null && last != held && Inventory.isHotbarSlot(last) && inv.getItem(last).isEmpty()) {
			selectSlot(player, last);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_no_items"), true);
			return true;
		}
		int free = -1;
		for (int i = Inventory.getSelectionSize(); i < inv.items.size() && free < 0; i++) {
			if (inv.items.get(i).isEmpty()) {
				free = i;
			}
		}
		for (int i = 0; i < Inventory.getSelectionSize() && free < 0; i++) {
			if (i != held && inv.items.get(i).isEmpty()) {
				free = i;
			}
		}
		if (free < 0) {
			return false;
		}
		inv.setItem(free, inv.getItem(held));
		inv.setItem(held, ItemStack.EMPTY);
		BLADE_SLOT.put(player.getId(), held);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_no_items"), true);
		return true;
	}

	private static void selectSlot(ServerPlayer player, int slot) {
		player.getInventory().selected = slot;
		if (player.connection != null) {
			player.connection.send(new ClientboundSetCarriedItemPacket(slot));
		}
	}

	/**
	 * The wild-bond settling phase. Returns true while it is still running. The host keeps the
	 * Symbiote's passive protection (the bond is already granted) but every ability is locked
	 * ({@link #usable}/{@link #bonding}), and their body fights the intrusion: nausea, weakness,
	 * slowness, mining fatigue and hunger, wet writhing sounds, and black ichor crawling over them.
	 * When the timer runs out the sickness lifts and the bond is complete.
	 */
	private static boolean tickBonding(ServerPlayer player) {
		SymbioteVitals v = vitals(player);
		long now = player.level().getGameTime();
		if (v.bondingUntil <= 0L) {
			return false;
		}
		if (now >= v.bondingUntil) {
			SymbioteVitals c = v.copy();
			c.bondingUntil = 0L;
			c.hp = MAX_HP;
			save(player, c);
			LAST_BOND_FX.remove(player.getId());
			player.removeEffect(MobEffects.CONFUSION);
			player.removeEffect(MobEffects.WEAKNESS);
			player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
			player.removeEffect(MobEffects.DIG_SLOWDOWN);
			player.removeEffect(MobEffects.HUNGER);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.bond_complete")
					.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
			if (player.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
						60, 0.4, 0.9, 0.4, 0.08);
				level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
				// v0.10.10: the bond completing is the Symbiote taking hold, not an achievement chime --
				// a layered roar (Warden bellow + a pitched-down Ravager snarl) over the wet writhing of
				// the suit settling, instead of the old beacon/level-up "ding".
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.4f, 1.1f);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2f, 0.5f);
				SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 1.0f, 0.4f);
			}
			return false;
		}

		// Sickness -- refreshed well before it can lapse.
		if (player.tickCount % 20 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 1, false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0, false, false, false));
		}
		if (player.level() instanceof ServerLevel level) {
			if (player.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK,
						player.getX(), player.getY() + player.getBbHeight() * player.getRandom().nextFloat(), player.getZ(),
						3, 0.35, 0.4, 0.35, 0.01);
			}
			Long lastFx = LAST_BOND_FX.get(player.getId());
			if (lastFx == null || now - lastFx >= 40L) {
				LAST_BOND_FX.put(player.getId(), now);
				float pitch = 0.4f + player.getRandom().nextFloat() * 0.3f;
				SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 0.9f, pitch);
				if (((now / 40L) & 1L) == 0L) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 0.9f);
				}
			}
		}
		return true;
	}

	/** Suit on, and crouched without a break for {@link #SNEAK_INVIS_TICKS}: the Symbiote hides the host. */
	private static void sneakInvisibility(ServerPlayer player) {
		if (!Symbiote.isActive(player) || !player.isShiftKeyDown()) {
			SNEAK_START.remove(player.getId());
			return;
		}
		long now = player.level().getGameTime();
		long start = SNEAK_START.computeIfAbsent(player.getId(), k -> now);
		if (now - start >= SNEAK_INVIS_TICKS) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, false));
			if (now - start == SNEAK_INVIS_TICKS && player.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(),
						20, 0.3, 0.5, 0.3, 0.02);
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.cloaked")
						.withStyle(ChatFormatting.DARK_GRAY), true);
			}
		}
	}

	/** The Symbiote snatches arrows out of the air -- at a cost to its own health. */
	private static void catchArrows(ServerPlayer player, SymbioteVitals v) {
		if (v.broken || v.hp <= ARROW_CATCH_HP_COST || player.getAbilities().invulnerable
				|| !(player.level() instanceof ServerLevel level)) {
			return;
		}
		float hp = v.hp;
		boolean caught = false;
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class,
				player.getBoundingBox().inflate(ARROW_CATCH_RADIUS))) {
			// v0.13.21: a squadmate's arrow is left alone -- it cannot hurt the host anyway, and catching it cost Biomass
			if (arrow.isRemoved() || arrow.getOwner() == player || Squads.areAllies(player, arrow.getOwner())) {
				continue;
			}
			// A stuck / spent arrow has near-zero velocity -- only catch ones still in flight.
			if (arrow.getDeltaMovement().lengthSqr() < 0.2) {
				continue;
			}
			arrow.discard();
			hp = Math.max(0.0f, hp - ARROW_CATCH_HP_COST);
			caught = true;
			level.sendParticles(ParticleTypes.SQUID_INK, arrow.getX(), arrow.getY(), arrow.getZ(),
					14, 0.2, 0.2, 0.2, 0.03);
			SymbioteSounds.organic(level, player.getX(), player.getY(), player.getZ(), 0.7f, 1.4f);
			if (hp <= 0.0f) {
				break;
			}
		}
		if (caught) {
			SymbioteVitals c = v.copy();
			c.hp = hp;
			if (c.hp <= 0.0f) {
				c.broken = true;
			}
			save(player, c);
		}
	}

	// ---------------- lifecycle ----------------

	/**
	 * Death / relog / dimension change: drop the Symbiote's toggles. v0.13.19: the Biomass itself is KEPT --
	 * dying used to hand the host a full bar on respawn; now they come back with exactly what they had.
	 */
	public static void clearTransient(ServerPlayer player) {
		SNEAK_START.remove(player.getId());
		LAST_WARN.remove(player.getId());
		LAST_COMBAT.remove(player.getId());
		LAST_BIG_HIT.remove(player.getId());
		LAST_REGEN.remove(player.getId());
		BLADE_SLOT.remove(player.getId());
		reconcileBlade(player, false);
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null) {
			return;
		}
		SymbioteVitals c = v.copy();
		c.bladeActive = false;
		c.bladeCharge = BLADE_MAX;
		c.thornsMode = false;
		c.animId = SymbioteAnim.NONE;
		save(player, c);
	}

	/**
	 * Join: game time is per world, so a revive cooldown stamped in another world (or before a /time change)
	 * could sit hours in the future. Never let it wait longer than one full cooldown from now.
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null) {
			return;
		}
		long now = player.level().getGameTime();
		long cap = now + resurrectCooldownFor(player);
		if (v.resurrectReadyAt > cap || v.spikeConeReadyAt > now + 400L) {
			SymbioteVitals c = v.copy();
			c.resurrectReadyAt = Math.min(c.resurrectReadyAt, cap);
			c.spikeConeReadyAt = Math.min(c.spikeConeReadyAt, now + 400L);
			save(player, c);
		}
	}

	/** Game time the host's next Symbiote revive is ready (v0.13.19). */
	public static long resurrectReadyAt(ServerPlayer player) {
		return vitals(player).resurrectReadyAt;
	}

	/** How long this host waits between Symbiote revives: 10 minutes for a Normal host, 20 (v0.13.21) for the hero hosts. */
	public static int resurrectCooldownFor(Player player) {
		return SymbioteHostType.of(player) == SymbioteHostType.NORMAL
				? RESURRECT_COOLDOWN_TICKS : HERO_HOST_RESURRECT_COOLDOWN_TICKS;
	}

	static void markResurrected(ServerPlayer player) {
		SymbioteVitals c = vitals(player).copy();
		c.resurrectReadyAt = player.level().getGameTime() + resurrectCooldownFor(player);
		save(player, c);
	}

	public static void clearSessionState() {
		LAST_DRAIN.clear();
		LAST_REGEN.clear();
		BLADE_SLOT.clear();
		SNEAK_START.clear();
		LAST_WARN.clear();
		LAST_COMBAT.clear();
		LAST_BIG_HIT.clear();
	}
}
