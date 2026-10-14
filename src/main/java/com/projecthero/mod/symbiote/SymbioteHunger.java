package com.projecthero.mod.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.SonicVulnerability;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: Black Suit Spider-Man's <b>Hunger</b> -- the meter that replaced the suit's old flat, always-on buffs.
 * Landing hits and taking them feeds it; it bleeds away out of combat. The suit's bonuses scale with it:
 *
 * <ul>
 * <li><b>Restrained</b> (under 30): a light touch -- +10% melee, +5% speed.</li>
 * <li><b>Bonded</b> (30-80): the old suit, a little under -- +25% melee, +12% speed, +15% jump.</li>
 * <li><b>Overdrive</b> (80+): the strongest it gets, at a price -- it eats your food, fire and sound hurt twice as
 * much, and the suit will not come off.</li>
 * <li><b>Let It Take Over</b> (hold V at a full meter): 10 seconds as a bigger, monstrous form, then a crash --
 * Weakness and Slowness, an empty meter that cannot refill for the crash's length.</li>
 * </ul>
 * Everything here self-gates on {@link #blackSuit}: the meter is only fed and only matters while the black suit is on.
 */
public final class SymbioteHunger {
	public static final float MAX = 100.0f;
	public static final float BONDED = 30.0f;
	public static final float OVERDRIVE = 80.0f;
	/** Resurrection only answers a black-suited host fed at least this much, and eats the whole meter. */
	public static final float RESURRECT_MIN = 50.0f;

	public static final int TAKEOVER_TICKS = 200; // 10 s
	public static final int CRASH_TICKS = 160;    // 8 s

	private static final float GAIN_PER_HIT = 4.0f;
	private static final float GAIN_PER_DAMAGE = 1.5f;
	private static final float GAIN_TAKEN_CAP = 8.0f;
	private static final int OUT_OF_COMBAT_TICKS = 100;
	private static final float DECAY_PER_TICK = 3.0f / 20.0f;
	private static final float OVERDRIVE_EXHAUSTION = 0.06f; // per tick: ~ a hunger shank every 7 s
	private static final float WEAKNESS_MULTIPLIER = 1.5f;
	private static final float OVERDRIVE_WEAKNESS_MULTIPLIER = 2.0f;
	private static final float TAKEOVER_DAMAGE_FACTOR = 0.6f;
	private static final double TAKEOVER_SCALE = 0.35;
	private static final double CLEAVE_RADIUS = 2.5;
	private static final float CLEAVE_FRACTION = 0.5f;

	private static final ResourceLocation ATTACK = ProjectHeroMod.id("symbiote_black_suit_attack");
	private static final ResourceLocation SPEED = ProjectHeroMod.id("symbiote_black_suit_speed");
	private static final ResourceLocation JUMP = ProjectHeroMod.id("symbiote_black_suit_jump");
	private static final ResourceLocation KNOCKBACK = ProjectHeroMod.id("symbiote_black_suit_knockback");
	private static final ResourceLocation SCALE = ProjectHeroMod.id("symbiote_takeover_scale");

	/** Last game time each player was in a fight (hit or hurt), for the out-of-combat decay. Transient. */
	private static final java.util.Map<Integer, Long> LAST_COMBAT = new java.util.concurrent.ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	public enum Tier { RESTRAINED, BONDED, OVERDRIVE, TAKEOVER }

	private SymbioteHunger() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SymbioteHunger::onAllowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || taken <= 0.0f) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && blackSuit(attacker)) {
				LAST_COMBAT.put(attacker.getId(), attacker.level().getGameTime());
				add(attacker, GAIN_PER_HIT);
				if (takingOver(attacker) && source.getDirectEntity() == attacker && !REENTRANT.get()) {
					cleave(attacker, entity, taken);
				}
			}
			if (entity instanceof ServerPlayer host && blackSuit(host)
					&& !com.projecthero.mod.squad.Squads.isFriendlyFire(host, source)) {
				LAST_COMBAT.put(host.getId(), host.level().getGameTime());
				add(host, Math.min(GAIN_TAKEN_CAP, taken * GAIN_PER_DAMAGE));
			}
		});
	}

	// ---------------- queries (client + server) ----------------

	public static boolean blackSuit(Player player) {
		return Symbiote.isActive(player) && SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
	}

	public static float get(Player player) {
		return player.getAttachedOrElse(ModAttachments.SYMBIOTE_HUNGER, 0.0f);
	}

	public static long takeoverEnd(Player player) {
		return player.getAttachedOrElse(ModAttachments.SYMBIOTE_TAKEOVER, 0L);
	}

	public static boolean takingOver(Player player) {
		return player.level().getGameTime() < takeoverEnd(player);
	}

	public static boolean crashing(Player player) {
		long end = takeoverEnd(player);
		long now = player.level().getGameTime();
		return end > 0L && now >= end && now < end + CRASH_TICKS;
	}

	public static Tier tier(Player player) {
		if (takingOver(player)) {
			return Tier.TAKEOVER;
		}
		float h = get(player);
		return h >= OVERDRIVE ? Tier.OVERDRIVE : h >= BONDED ? Tier.BONDED : Tier.RESTRAINED;
	}

	/** Overdrive and the Take Over form keep the suit on: H will not retract it. */
	public static boolean locksSuit(Player player) {
		if (!blackSuit(player)) {
			return false;
		}
		Tier t = tier(player);
		return t == Tier.OVERDRIVE || t == Tier.TAKEOVER;
	}

	// ---------------- writes ----------------

	public static void set(ServerPlayer player, float value) {
		float v = Math.max(0.0f, Math.min(MAX, value));
		if (Math.abs(v - get(player)) > 1.0e-3f) {
			player.setAttached(ModAttachments.SYMBIOTE_HUNGER, v);
		}
	}

	/** Feed the meter (no-op while the form runs or the crash lasts). */
	public static void add(ServerPlayer player, float amount) {
		if (!blackSuit(player) || takingOver(player) || crashing(player)) {
			return;
		}
		float before = get(player);
		set(player, before + amount);
		if (before < OVERDRIVE && get(player) >= OVERDRIVE) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.hunger.overdrive")
					.withStyle(ChatFormatting.DARK_RED), true);
			SymbioteSounds.organic(player, 1.0f, 0.45f);
		}
	}

	/** Resurrection gate: true (and the meter emptied) if a black-suited host is fed enough to be brought back. */
	public static boolean consumeForResurrect(ServerPlayer player) {
		if (get(player) < RESURRECT_MIN && !takingOver(player)) {
			return false;
		}
		set(player, 0.0f);
		return true;
	}

	// ---------------- Let It Take Over ----------------

	/** Hold V at a full meter: the suit takes over for {@link #TAKEOVER_TICKS}. */
	public static boolean beginTakeover(ServerPlayer player) {
		if (!blackSuit(player) || takingOver(player)) {
			return false;
		}
		if (crashing(player) || get(player) < MAX) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.hunger.not_full",
					Math.round(get(player))).withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		long now = player.level().getGameTime();
		player.setAttached(ModAttachments.SYMBIOTE_TAKEOVER, now + TAKEOVER_TICKS);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 c = player.position().add(0, 1, 0);
		AbilityHelpers.burst(level, player.position().add(0, 0.4, 0), ParticleTypes.SQUID_INK, 30, 0.9);
		AbilityHelpers.burst(level, player.position().add(0, 0.2, 0), ParticleTypes.LARGE_SMOKE, 10, 0.8);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 1.2f, 1.3f);
		SymbioteSounds.organic(player, 1.2f, 0.35f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.hunger.takeover")
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), true);
		return true;
	}

	private static void endTakeover(ServerPlayer player) {
		set(player, 0.0f);
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CRASH_TICKS, 0, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CRASH_TICKS, 1, false, true, true));
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.hunger.crash")
				.withStyle(ChatFormatting.GRAY), true);
		AbilityHelpers.burst(AbilityHelpers.level(player), player.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 30, 0.7);
		SymbioteSounds.organic(player, 0.9f, 0.6f);
	}

	/** In the form, every melee hit splashes half its damage onto everything else close to the target. */
	private static void cleave(ServerPlayer player, LivingEntity hit, float taken) {
		REENTRANT.set(true);
		try {
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, hit.position(), CLEAVE_RADIUS)) {
				if (e != hit && !com.projecthero.mod.squad.Squads.areAllies(player, e)) {
					AbilityHelpers.hurt(player, e, taken * CLEAVE_FRACTION);
				}
			}
		} finally {
			REENTRANT.set(false);
		}
		AbilityHelpers.burst(AbilityHelpers.level(player), hit.position().add(0, hit.getBbHeight() * 0.5, 0),
				ParticleTypes.SQUID_INK, 18, 0.9);
	}

	// ---------------- per tick ----------------

	/** Called every tick for a Spider-Man (from {@code SpiderManAbilityManager}). */
	public static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		long end = takeoverEnd(player);
		if (end > 0L && now == end) {
			endTakeover(player);
		}
		if (!blackSuit(player)) {
			if (get(player) > 0.0f) {
				set(player, 0.0f);
			}
			if (end > now) {
				player.setAttached(ModAttachments.SYMBIOTE_TAKEOVER, now);
				endTakeover(player);
			}
			reconcile(player, null);
			return;
		}
		Tier t = tier(player);
		if (t == Tier.TAKEOVER) {
			// the meter drains with the form, so the HUD shows the time left
			set(player, MAX * (end - now) / (float) TAKEOVER_TICKS);
			if (player.tickCount % 4 == 0) {
				ServerLevel level = AbilityHelpers.level(player);
				Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
				forOthers(level, player, ParticleTypes.SQUID_INK, c, 3, 0.5, player.getBbHeight() * 0.4);
			}
		} else {
			Long last = LAST_COMBAT.get(player.getId());
			if (get(player) > 0.0f && (last == null || now - last > OUT_OF_COMBAT_TICKS)) {
				set(player, get(player) - DECAY_PER_TICK);
			}
			if (t == Tier.OVERDRIVE) {
				player.causeFoodExhaustion(OVERDRIVE_EXHAUSTION);
				if (player.tickCount % 6 == 0) {
					ServerLevel level = AbilityHelpers.level(player);
					Vec3 c = player.position().add(0, 1.2, 0);
					forOthers(level, player, ParticleTypes.SQUID_INK, c, 2, 0.35, 0.5);
				}
			}
		}
		if (player.tickCount % 5 == 0 || t == Tier.TAKEOVER) {
			reconcile(player, t);
		}
	}

	/** Ambient drips around the host, for everyone else only -- in his own first person they would fill the screen. */
	private static void forOthers(ServerLevel level, ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle,
			Vec3 c, int count, double xz, double y) {
		for (ServerPlayer viewer : level.players()) {
			if (viewer != player) {
				level.sendParticles(viewer, particle, false, c.x, c.y, c.z, count, xz, y, xz, 0.0);
			}
		}
	}

	/** The tier's attribute bonuses, or none at all ({@code tier == null}). */
	public static void reconcile(ServerPlayer player, Tier tier) {
		double atk = 0, speed = 0, jump = 0, kb = 0, scale = 0;
		if (tier != null) {
			switch (tier) {
				case RESTRAINED -> { atk = 0.10; speed = 0.05; }
				case BONDED -> { atk = 0.25; speed = 0.12; jump = 0.15; kb = 0.10; }
				case OVERDRIVE -> { atk = 0.40; speed = 0.18; jump = 0.20; kb = 0.20; }
				case TAKEOVER -> { atk = 0.80; speed = 0.20; jump = 0.25; kb = 0.60; scale = TAKEOVER_SCALE; }
			}
		}
		apply(player, Attributes.ATTACK_DAMAGE, ATTACK, atk, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		apply(player, Attributes.MOVEMENT_SPEED, SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		apply(player, Attributes.JUMP_STRENGTH, JUMP, jump, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		apply(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, kb, AttributeModifier.Operation.ADD_VALUE);
		apply(player, Attributes.SCALE, SCALE, scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void apply(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			ResourceLocation id, double amount, AttributeModifier.Operation op) {
		if (amount == 0.0) {
			PowerToggles.clearModifier(player, attr, id);
		} else {
			PowerToggles.modifier(player, attr, id, amount, op);
		}
	}

	// ---------------- damage ----------------

	/** The black suit's weaknesses (fire and sound, worse in Overdrive) and the form's toughness. */
	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || !blackSuit(player)
				|| com.projecthero.mod.squad.Squads.isFriendlyFire(player, source)) {
			return true;
		}
		Tier t = tier(player);
		float factor = 1.0f;
		boolean weak = source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.SONIC_BOOM)
				|| SonicVulnerability.isDisrupted(player, player.level().getGameTime());
		if (weak) {
			factor *= t == Tier.OVERDRIVE || t == Tier.TAKEOVER ? OVERDRIVE_WEAKNESS_MULTIPLIER : WEAKNESS_MULTIPLIER;
		}
		if (t == Tier.TAKEOVER) {
			factor *= TAKEOVER_DAMAGE_FACTOR;
		}
		if (Math.abs(factor - 1.0f) < 0.01f) {
			return true;
		}
		float scaled = amount * factor;
		if (scaled < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, scaled);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}

	// ---------------- lifecycle ----------------

	public static void clearFor(ServerPlayer player) {
		LAST_COMBAT.remove(player.getId());
		player.setAttached(ModAttachments.SYMBIOTE_HUNGER, 0.0f);
		player.setAttached(ModAttachments.SYMBIOTE_TAKEOVER, 0L);
		reconcile(player, null);
	}

	public static void clearSessionState() {
		LAST_COMBAT.clear();
	}
}
