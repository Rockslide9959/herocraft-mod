package com.projecthero.mod.symbiote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;

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
	/** Full Symbiote health -- 30 hearts of shared pool on top of the host's own. */
	public static final float MAX_HP = 60.0f;
	/** Regen while not broken: ~1 hp per second. */
	private static final float REGEN_PER_TICK = 0.05f;
	/** Once the bar has emptied, abilities stay locked until it climbs back to this fraction. */
	public static final float RECOVER_FRACTION = 0.20f;
	/** The Symbiote starts warning its host below this fraction. */
	private static final float WARN_FRACTION = 0.35f;
	/** Fraction of every hit the host takes that is dealt to the Symbiote instead. */
	public static final float ABSORB_FRACTION = 0.5f;

	/** Symbiote Blade: 30 s of hold time (1 charge point per tick), refills a little faster while sheathed. */
	public static final float BLADE_MAX = 600.0f;
	private static final float BLADE_DRAIN_PER_TICK = 1.0f;
	private static final float BLADE_REGEN_PER_TICK = 1.4f;
	private static final float BLADE_BONUS_DAMAGE = 5.0f;

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

	private SymbioteVitalsManager() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			// Symbiote Blade: an extra armour-bypassing bite on a melee hit while it is out.
			if (source.getEntity() instanceof ServerPlayer sp && entity instanceof LivingEntity living
					&& living != sp && taken > 0.0f && bladeActive(sp)
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
					&& player.distanceToSqr(attacker) < 36.0 && player.getRandom().nextFloat() < 0.66f) {
				float reflect = 2.0f + player.getRandom().nextFloat() * 2.0f;
				attacker.hurt(player.damageSources().thorns(player), reflect);
				if (player.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SQUID_INK, attacker.getX(),
							attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(), 8, 0.25, 0.3, 0.25, 0.01);
				}
			}
		});
	}

	// ---------------- state ----------------

	public static SymbioteVitals vitals(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SYMBIOTE_VITALS);
	}

	static void save(ServerPlayer player, SymbioteVitals v) {
		player.setAttached(ModAttachments.SYMBIOTE_VITALS, v);
	}

	/** Can the Symbiote's abilities be used right now? (Bonded, and the health bar is not spent.) */
	public static boolean usable(ServerPlayer player) {
		if (!Symbiote.hasSymbiote(player)) {
			return false;
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v == null || !v.broken;
	}

	public static boolean bladeActive(ServerPlayer player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v != null && v.bladeActive;
	}

	public static boolean thornsMode(ServerPlayer player) {
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v != null && v.thornsMode;
	}

	// ---------------- damage absorption ----------------

	/**
	 * Route {@link #ABSORB_FRACTION} of {@code incoming} onto the Symbiote's own health. Returns the
	 * amount the player should still take. Once the bar is empty the player takes everything.
	 */
	public static float absorb(ServerPlayer player, float incoming) {
		if (incoming <= 0.0f || !Symbiote.hasSymbiote(player)) {
			return incoming;
		}
		SymbioteVitals v = vitals(player);
		if (v.hp <= 0.0f) {
			return incoming;
		}
		float want = incoming * ABSORB_FRACTION;
		float drained = Math.min(want, v.hp);
		SymbioteVitals c = v.copy();
		c.hp = Math.max(0.0f, v.hp - want);
		if (c.hp <= 0.0f) {
			c.broken = true;
		}
		save(player, c);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(),
					10, 0.35, 0.6, 0.35, 0.02);
			if (c.broken) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.WARDEN_DEATH, SoundSource.PLAYERS, 0.6f, 1.6f);
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent")
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
			}
		}
		return incoming - drained;
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
		if (v.bladeCharge < BLADE_MAX * 0.1f) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_spent"), true);
			return;
		}
		setBlade(player, v, true);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_on")
				.withStyle(ChatFormatting.DARK_PURPLE), true);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.2, player.getZ(),
					24, 0.3, 0.4, 0.3, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9f, 0.4f);
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
			return;
		}
		SymbioteVitals v = vitals(player);
		SymbioteVitals c = v.copy();
		boolean dirty = false;
		boolean transition = false;

		// Regeneration -- unless the bar is fully spent-and-broken, in which case it still climbs (that
		// is how the lock lifts) just at the same rate.
		if (c.hp < MAX_HP) {
			c.hp = Math.min(MAX_HP, c.hp + REGEN_PER_TICK);
			dirty = true;
		}
		if (c.broken && c.hp >= MAX_HP * RECOVER_FRACTION) {
			c.broken = false;
			transition = true;
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.recovered")
					.withStyle(ChatFormatting.DARK_PURPLE), true);
		}

		// Blade upkeep: drain while out, sheathe it if the charge runs dry or the hand is filled.
		if (c.bladeActive) {
			if (!player.getMainHandItem().isEmpty()) {
				c.bladeActive = false;
				reconcileBlade(player, false);
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_hands_full"), true);
				transition = true;
			} else {
				c.bladeCharge = Math.max(0.0f, c.bladeCharge - BLADE_DRAIN_PER_TICK);
				dirty = true;
				if (c.bladeCharge <= 0.0f) {
					c.bladeActive = false;
					reconcileBlade(player, false);
					player.displayClientMessage(Component.translatable("message.projecthero.symbiote.blade_spent"), true);
					transition = true;
				} else if (player.tickCount % 3 == 0 && player.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SQUID_INK,
							player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
							2, 0.25, 0.3, 0.25, 0.0);
				}
			}
		} else if (c.bladeCharge < BLADE_MAX) {
			c.bladeCharge = Math.min(BLADE_MAX, c.bladeCharge + BLADE_REGEN_PER_TICK);
			dirty = true;
		} else {
			reconcileBlade(player, false);
		}

		// Persist on a real transition immediately; otherwise let the slow drift ride ~4 ticks so the
		// synced attachment is not rewritten and re-broadcast to every client every single tick.
		if (transition || (dirty && player.tickCount % 4 == 0)) {
			save(player, c);
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

		fastMining(player, c);
		sneakInvisibility(player);
		catchArrows(player, c);
	}

	/** Bare-handed, a bonded Normal host tears through soft blocks -- Haste II while the main hand is empty. */
	private static void fastMining(ServerPlayer player, SymbioteVitals v) {
		if (v.broken || SymbioteHostType.of(player) != SymbioteHostType.NORMAL) {
			return;
		}
		if (player.getMainHandItem().isEmpty() && player.tickCount % 20 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 1, true, false, false));
		}
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
			if (arrow.isRemoved() || arrow.getOwner() == player) {
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
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.7f, 1.4f);
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

	/** Death / relog / dimension change / unbond: heal the Symbiote back up and drop its toggles. */
	public static void clearTransient(ServerPlayer player) {
		SNEAK_START.remove(player.getId());
		LAST_WARN.remove(player.getId());
		reconcileBlade(player, false);
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null) {
			return;
		}
		SymbioteVitals c = v.copy();
		c.hp = MAX_HP;
		c.broken = false;
		c.bladeActive = false;
		c.bladeCharge = BLADE_MAX;
		c.thornsMode = false;
		save(player, c);
	}

	public static void clearSessionState() {
		SNEAK_START.clear();
		LAST_WARN.clear();
	}
}
