package com.projecthero.mod.omnitrix.alien.ripjaws;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Ripjaws, the Piscciss Volann -- a grey-blue fish-man with a huge toothy jaw, gills and fins, whose legs become a fish
 * tail in the water. In water he is a predator: fast (Dolphin's Grace), never drowns, sees and digs freely (Conduit Power), and
 * hits half again as hard. On land he slowly DRIES OUT (the hydration meter over his keys): two minutes of dry land are free,
 * then he chokes for 1 health every 2 s (slowed, weakened) until he gets wet again. Water refills the meter in 15 s; rain stops
 * the drying and refills it slowly; Drench (Shift+C) splashes half of it back.
 *
 * <pre>
 *   R        Bite           a huge chomp (7; heals 1)                         Shift+R  Jaw Lunge       lunge jaws-first: the first creature in the way (10)
 *   G        Tail Slam      a ring of force around you (8, knockback)         Shift+G  Fin Slash       a spin of razor fins (6, slows)
 *   X        Water Dash     a burst through the water / on land a hop         Shift+X  Breach          leap out of the water / on land a back-flip
 *   Z        Whirlpool      in water: a 5 s vortex that drags and drowns      Shift+Z  Tidal Roar      weakens, slows and shoves around you
 *            Mud Splash     on land: a blinding spray of mud ahead
 *   C        Feeding Frenzy 10 s: +30% damage, faster, bites heal 3          Shift+C  Drench          splash yourself: half the hydration back
 *   Attack   jaw punch (+3 in water)        Use on a block   dig straight through sand, gravel, dirt, clay, mud and snow
 * </pre>
 * Every move does x1.5 in water.
 */
public final class RipjawsKit extends AlienKit {
	public static final String ID = "ripjaws";
	public static final int COLOUR = 0x7A98A4;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** The chomp: a jaw snap and a meaty hit. */
	public static final OmnitrixSfx.Cue BITE_SOUND = OmnitrixSfx.cue("ripjaws_bite")
			.layer(SoundEvents.EVOKER_FANGS_ATTACK, 0.45f, 1.1f)
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.3f, 0.8f);
	/** A wet, low growl. */
	public static final OmnitrixSfx.Cue ROAR = OmnitrixSfx.cue("ripjaws_roar")
			.layer(SoundEvents.POLAR_BEAR_WARNING, 0.4f, 0.8f)
			.layer(SoundEvents.DROWNED_AMBIENT, 0.35f, 0.6f)
			.gap(10);
	public static final OmnitrixSfx.Cue SPLASH = OmnitrixSfx.cue("ripjaws_splash")
			.layer(SoundEvents.GENERIC_SPLASH, 0.45f, 1.0f)
			.layer(SoundEvents.PLAYER_SPLASH, 0.22f, 0.9f)
			.gap(4);
	/** A body slam into water / ground: a heavy splash and a thud. */
	public static final OmnitrixSfx.Cue SLAM = OmnitrixSfx.cue("ripjaws_slam")
			.layer(SoundEvents.GENERIC_SPLASH, 0.55f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.35f, 0.6f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.2f, 1.4f);
	/** Out of water too long: a fishy flop and a choked gasp. */
	public static final OmnitrixSfx.Cue GASP = OmnitrixSfx.cue("ripjaws_gasp")
			.layer(SoundEvents.GUARDIAN_FLOP, 0.45f, 1.0f)
			.layer(SoundEvents.DROWNED_HURT, 0.25f, 0.8f)
			.gap(10);
	/** A whirlpool churning. */
	public static final OmnitrixSfx.Cue WHIRL = OmnitrixSfx.cue("ripjaws_whirl")
			.layer(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.45f, 0.9f)
			.layer(SoundEvents.GENERIC_SWIM, 0.35f, 0.8f)
			.layer(SoundEvents.TRIDENT_RIPTIDE_2, 0.22f, 0.8f)
			.gap(10);

	public static final int ANIM_BITE = 1;
	public static final int ANIM_LUNGE = 2;
	public static final int ANIM_SLAM = 3;
	public static final int ANIM_SLASH = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_BREACH = 6;
	public static final int ANIM_WHIRL = 7;
	public static final int ANIM_MUD = 8;
	public static final int ANIM_ROAR = 9;
	public static final int ANIM_FRENZY = 10;
	public static final int ANIM_DRENCH = 11;
	public static final int ANIM_PUNCH = 12;
	public static final int ANIM_DIG = 13;
	public static final int ANIM_GASP = 14;

	public static final int F_WET = 1;
	public static final int F_RAIN = 2;
	public static final int F_FRENZY = 4;
	public static final int F_WARNED = 8;
	public static final int F_CHOKING = 16;

	public static final String BITE = "bite";
	public static final String LUNGE = "jaw_lunge";
	public static final String TAIL_SLAM = "tail_slam";
	public static final String FIN_SLASH = "fin_slash";
	public static final String DASH = "water_dash";
	public static final String BREACH = "breach";
	public static final String WHIRLPOOL = "whirlpool";
	public static final String ROAR_MOVE = "tidal_roar";
	public static final String FRENZY = "feeding_frenzy";
	public static final String DRENCH = "drench";
	public static final String PUNCH = "jaw_punch";
	public static final String DIG = "dig";

	/** Ticks of dry land before he starts choking (2 minutes). */
	public static final int MAX_DRY = 2400;
	public static final int REFILL_WATER = 8;
	public static final int REFILL_RAIN = 2;
	public static final int CHOKE_EVERY = 40;
	public static final float CHOKE_DAMAGE = 1f;
	public static final float WATER_BONUS = 1.5f;
	public static final float FRENZY_BONUS = 1.3f;
	public static final int FRENZY_TICKS = 200;
	public static final float BITE_DAMAGE = 7f;
	public static final float LUNGE_DAMAGE = 10f;
	public static final float SLAM_DAMAGE = 8f;
	public static final float SLASH_DAMAGE = 6f;
	public static final float WHIRL_RADIUS = 6f;
	public static final int WHIRL_TICKS = 100;
	public static final float WHIRL_DAMAGE = 1.5f;
	public static final float MUD_DAMAGE = 3f;
	public static final float WET_FIST = 3f;

	private static final ResourceLocation SWIM = PowerToggles.id("omnitrix_ripjaws_swim");
	private static final ResourceLocation DIG_WET = PowerToggles.id("omnitrix_ripjaws_dig");
	private static final ResourceLocation FIST_WET = PowerToggles.id("omnitrix_ripjaws_wet_fist");
	private static final ResourceLocation FRENZY_FIST = PowerToggles.id("omnitrix_ripjaws_frenzy_fist");
	private static final ResourceLocation FRENZY_SPEED = PowerToggles.id("omnitrix_ripjaws_frenzy_speed");

	/** player -> the whirlpool they spun up. */
	private static final Map<UUID, Whirl> WHIRLS = new ConcurrentHashMap<>();

	private record Whirl(ResourceKey<Level> dim, Vec3 at, long until) {
	}

	private RipjawsKit() {
	}

	public static void register() {
		Aliens.register(new RipjawsKit());
	}

	public static void clearSessionState() {
		WHIRLS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(30f).speed(0.9f).scale(1.05f).armor(4f, 1f).attack(3f).knockbackResist(0.2f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(BITE, AbilitySlot.SLOT_1, 14), AlienMove.shift(LUNGE, AbilitySlot.SLOT_1, 70),
				AlienMove.tap(TAIL_SLAM, AbilitySlot.SLOT_2, 90), AlienMove.shift(FIN_SLASH, AbilitySlot.SLOT_2, 120),
				AlienMove.tap(DASH, AbilitySlot.SLOT_3, 30), AlienMove.shift(BREACH, AbilitySlot.SLOT_3, 100),
				AlienMove.tap(WHIRLPOOL, AbilitySlot.SLOT_4, 320), AlienMove.shift(ROAR_MOVE, AbilitySlot.SLOT_4, 300),
				AlienMove.tap(FRENZY, AbilitySlot.SLOT_6, 600), AlienMove.shift(DRENCH, AbilitySlot.SLOT_6, 900),
				AlienMove.vanilla(PUNCH, "Attack", 0), AlienMove.vanilla(DIG, "Use on sand / gravel / dirt", 3));
	}

	// ---------------------------------------------------------------- wet and dry

	/** In water (feet or head), or a bubble column. Reads the blocks, so it also holds for a player the server does not tick. */
	public static boolean inWater(Player p) {
		if (p.isInWaterOrBubble()) {
			return true;
		}
		Level l = p.level();
		return l.getFluidState(p.blockPosition()).is(FluidTags.WATER) || l.getFluidState(BlockPos.containing(p.getEyePosition())).is(FluidTags.WATER);
	}

	public static boolean inRain(Player p) {
		Level l = p.level();
		return l.isRainingAt(p.blockPosition()) || l.isRainingAt(BlockPos.containing(p.getEyePosition()).above());
	}

	/**
	 * How long he has been dry, in ticks (0 = soaked, {@link #MAX_DRY} = choking). Lives in the synced state as the value at the
	 * last wet / dry change plus the rate since, so the HUD reads it without a packet every tick.
	 */
	public static int dryness(Player p, long now) {
		OmnitrixState s = Omnitrix.state(p);
		long base = s.readyAt(ID + ".dry_base");
		long since = s.readyAt(ID + ".dry_since");
		if (base <= 0 || since <= 0) {
			return 0;
		}
		int rate = (s.kitFlags & F_WET) != 0 ? -REFILL_WATER : (s.kitFlags & F_RAIN) != 0 ? -REFILL_RAIN : 1;
		long v = (base - 1) + rate * Math.max(0, now - since);
		return (int) Math.max(0, Math.min(MAX_DRY, v));
	}

	/** 1 = fully wet, 0 = choking. */
	public static float hydration(Player p, long now) {
		return 1f - dryness(p, now) / (float) MAX_DRY;
	}

	/** Sets the dryness (tests, Drench) and the wet / rain flags as they are now. */
	public void setDryness(ServerPlayer p, int ticks, boolean wet, boolean rain) {
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key("dry_base"), Math.max(0, Math.min(MAX_DRY, ticks)) + 1L);
		n.timers.put(key("dry_since"), Math.max(1L, now));
		n.kitFlags = (n.kitFlags & ~(F_WET | F_RAIN)) | (wet ? F_WET : 0) | (rain && !wet ? F_RAIN : 0);
		if (ticks < MAX_DRY * 3 / 4) {
			n.kitFlags &= ~F_WARNED;
		}
		if (ticks < MAX_DRY) {
			n.kitFlags &= ~F_CHOKING;
		}
		Omnitrix.save(p, n);
	}

	private void setWetFist(ServerPlayer p, boolean wet) {
		if (wet) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FIST_WET, WET_FIST, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, FIST_WET);
		}
	}

	/** A move's damage now: x1.5 in water, x1.3 in a feeding frenzy. */
	public float damage(ServerPlayer p, float base) {
		float d = base;
		if (inWater(p)) {
			d *= WATER_BONUS;
		}
		if (flag(p, F_FRENZY)) {
			d *= FRENZY_BONUS;
		}
		return d;
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		boolean wet = inWater(p);
		setDryness(p, 0, wet, inRain(p));
		PowerToggles.modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM, 0.65, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.SUBMERGED_MINING_SPEED, DIG_WET, 4.0, AttributeModifier.Operation.ADD_VALUE);
		setWetFist(p, wet);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), ROAR, 0.8f, 1.3f);
		l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		WHIRLS.remove(p.getUUID());
		endFrenzy(p);
		PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM);
		PowerToggles.clearModifier(p, Attributes.SUBMERGED_MINING_SPEED, DIG_WET);
		setWetFist(p, false);
		for (var eff : new net.minecraft.core.Holder[] { MobEffects.DOLPHINS_GRACE, MobEffects.CONDUIT_POWER }) {
			@SuppressWarnings("unchecked")
			var h = (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) eff;
			MobEffectInstance cur = p.getEffect(h);
			if (cur != null && cur.getDuration() <= 60) {
				p.removeEffect(h); // only his own short water buffs, never a real potion
			}
		}
		clearChoke(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { "dry_base", "dry_since", "lunge_end", "frenzy_end" }) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		boolean wet = inWater(p);
		boolean rain = !wet && inRain(p);
		boolean wasWet = flag(p, F_WET);
		if (wet != wasWet || rain != flag(p, F_RAIN)) {
			// the rate changed: fold the time so far into the stored value
			int d = dryness(p, now);
			setDryness(p, d, wet, rain);
			if (wet != wasWet) {
				setWetFist(p, wet);
				if (wet) {
					OmnitrixSounds.play(l, p.position(), SPLASH, 0.7f, 1.2f);
					clearChoke(p);
					refresh(p, MobEffects.DOLPHINS_GRACE, 0);
					refresh(p, MobEffects.CONDUIT_POWER, 0);
				}
			}
		}
		int dry = dryness(p, now);
		if (wet) {
			p.setAirSupply(p.getMaxAirSupply());
			if (p.tickCount % 20 == 0) {
				refresh(p, MobEffects.DOLPHINS_GRACE, 0);
				refresh(p, MobEffects.CONDUIT_POWER, 0);
			}
			if (p.tickCount % 5 == 0) {
				l.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + p.getBbHeight() * 0.8, p.getZ(), 2, 0.2, 0.1, 0.2, 0.02);
			}
		} else if (p.tickCount % 50 == 0 && dry < MAX_DRY / 3) {
			l.sendParticles(ParticleTypes.DRIPPING_WATER, p.getX(), p.getY() + p.getBbHeight() * 0.7, p.getZ(), 2, 0.25, 0.3, 0.25, 0);
		}
		// the warnings and the choking
		if (dry >= MAX_DRY * 3 / 4 && !flag(p, F_WARNED)) {
			setFlag(p, F_WARNED, true);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.drying").withStyle(ChatFormatting.GOLD), true);
			OmnitrixSounds.play(l, p.position(), GASP, 0.8f, 1.1f);
		}
		if (dry >= MAX_DRY) {
			if (!flag(p, F_CHOKING)) {
				setFlag(p, F_CHOKING, true);
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ripjaws.choking").withStyle(ChatFormatting.RED), true);
			}
			if (now % CHOKE_EVERY == 0) {
				p.invulnerableTime = 0;
				p.hurt(l.damageSources().dryOut(), CHOKE_DAMAGE);
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHOKE_EVERY + 4, 0, false, false, true));
				p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CHOKE_EVERY + 4, 0, false, false, true));
				anim(p, ANIM_GASP);
				OmnitrixSounds.play(l, p.position(), GASP, 0.9f, 0.9f + p.getRandom().nextFloat() * 0.2f);
				l.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.5, 0.3, 0);
			}
		} else if (flag(p, F_CHOKING)) {
			setFlag(p, F_CHOKING, false);
		}
		// the jaw lunge: the first creature in the way
		long lunge = timer(p, "lunge_end");
		if (lunge > 0) {
			if (now >= lunge) {
				setTimer(p, "lunge_end", 0);
			} else {
				for (LivingEntity e : around(p, p.position().add(0, p.getBbHeight() * 0.5, 0).add(p.getLookAngle().scale(0.8)), 1.8)) {
					hurt(p, e, damage(p, LUNGE_DAMAGE), false);
					shove(e, p.position(), 0.7, 0.3);
					l.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
					OmnitrixSounds.play(l, e.position(), BITE_SOUND, 1.2f, 0.8f);
					setTimer(p, "lunge_end", 0);
					p.setDeltaMovement(p.getDeltaMovement().scale(0.2));
					p.hurtMarked = true;
					break;
				}
			}
		}
		// the feeding frenzy
		long frenzy = timer(p, "frenzy_end");
		if (frenzy > 0) {
			if (now >= frenzy) {
				endFrenzy(p);
			} else if (now % 6 == 0) {
				l.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, p.getX(), p.getEyeY(), p.getZ(), 2, 0.3, 0.2, 0.3, 0);
			}
		}
		// the whirlpool
		Whirl w = WHIRLS.get(p.getUUID());
		if (w != null) {
			if (now >= w.until() || w.dim() != l.dimension()) {
				WHIRLS.remove(p.getUUID());
			} else {
				if (now % 2 == 0) {
					for (int k = 0; k < 6; k++) {
						double a = now * 0.35 + k * Math.PI / 3;
						double r = 1.0 + ((now + k * 7) % 20) / 20.0 * (WHIRL_RADIUS - 1);
						l.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, w.at().x + Math.cos(a) * r, w.at().y + (k % 3) * 0.8, w.at().z + Math.sin(a) * r, 1,
								0, 0, 0, 0);
					}
					l.sendParticles(ParticleTypes.BUBBLE, w.at().x, w.at().y + 1, w.at().z, 6, 0.5, 1.0, 0.5, 0.1);
				}
				for (LivingEntity e : around(p, w.at(), WHIRL_RADIUS)) {
					Vec3 to = w.at().subtract(e.position());
					Vec3 h = new Vec3(to.x, 0, to.z);
					Vec3 swirl = new Vec3(-h.z, 0, h.x).normalize().scale(0.12);
					Vec3 pull = h.lengthSqr() > 0.25 ? h.normalize().scale(0.1) : Vec3.ZERO;
					double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
					e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(pull.add(swirl).scale(res)).add(0, -0.04 * res, 0));
					e.hurtMarked = true;
					if (now % 10 == 0) {
						hurt(p, e, damage(p, WHIRL_DAMAGE), false);
						e.setAirSupply(Math.max(-19, e.getAirSupply() - 40));
					}
				}
				if (now % 20 == 0) {
					OmnitrixSounds.play(l, w.at(), WHIRL, 0.8f, 1.0f);
				}
			}
		}
	}

	/** The choking's own Slowness / Weakness (short, hidden particles), gone the moment he is wet again or turns back. */
	private static void clearChoke(ServerPlayer p) {
		for (var eff : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS)) {
			MobEffectInstance cur = p.getEffect(eff);
			if (cur != null && cur.getDuration() <= CHOKE_EVERY + 5 && cur.getAmplifier() == 0 && !cur.isVisible()) {
				p.removeEffect(eff);
			}
		}
	}

	private static void refresh(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> eff, int amp) {
		MobEffectInstance cur = p.getEffect(eff);
		if (cur == null || cur.getDuration() < 30 && cur.getAmplifier() <= amp) {
			p.addEffect(new MobEffectInstance(eff, 50, amp, true, false, true));
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					jawLunge(p);
				} else {
					bite(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					finSlash(p);
				} else {
					tailSlam(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					breach(p);
				} else {
					waterDash(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					tidalRoar(p);
				} else {
					whirlpool(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					drench(p);
				} else {
					feedingFrenzy(p);
				}
			}
			default -> {
			}
		}
	}

	public boolean bite(ServerPlayer p) {
		if (!use(p, BITE)) {
			return false;
		}
		anim(p, ANIM_BITE);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), BITE_SOUND, 1.0f, 0.95f + p.getRandom().nextFloat() * 0.15f);
		LivingEntity t = lookTarget(p, 3.75);
		if (t != null) {
			hurt(p, t, damage(p, BITE_DAMAGE), false);
			p.heal(flag(p, F_FRENZY) ? 3f : 1f);
			l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.7, t.getZ(), 10, 0.25, 0.25, 0.25, 0.25);
			l.sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY() + t.getBbHeight() * 0.7, t.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
		}
		return true;
	}

	public boolean jawLunge(ServerPlayer p) {
		if (!use(p, LUNGE)) {
			return false;
		}
		anim(p, ANIM_LUNGE);
		ServerLevel l = level(p);
		boolean wet = inWater(p);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.scale(wet ? 2.0 : 1.3).add(0, wet ? 0 : 0.2, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "lunge_end", l.getGameTime() + 10);
		QuirkFx.trail(l, p).colours(0xE0F4FF, COLOUR).size(0.4f).size2(8f).life(14).vec(new Vec3(0, 1.0, 0)).spawn();
		OmnitrixSounds.play(l, p.position(), ROAR, 0.7f, 1.4f);
		return true;
	}

	public boolean tailSlam(ServerPlayer p) {
		if (!use(p, TAIL_SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		ServerLevel l = level(p);
		boolean wet = inWater(p);
		float r = wet ? 5f : 3.5f;
		Vec3 c = p.position().add(0, 0.6, 0);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0xFFFFFF, wet ? 0x3A9AE0 : 0xA08A6A).size(r + 0.5f).thickness(0.7f).life(14).ground().spawn();
		if (wet) {
			l.sendParticles(ParticleTypes.BUBBLE, c.x, c.y, c.z, 40, r * 0.4, 0.4, r * 0.4, 0.3);
		} else {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(p.blockPosition().below())), c.x, c.y - 0.5, c.z, 30, r * 0.4, 0.1,
					r * 0.4, 0.2);
			QuirkFx.decal(l, p.position().add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(r * 1.2f).life(160).spawn();
		}
		OmnitrixSounds.play(l, c, SLAM, 1.2f, 1.0f);
		QuirkCamera.shakeAround(l, c, 10, 0.4f, 8);
		for (LivingEntity e : around(p, c, r)) {
			hurt(p, e, damage(p, SLAM_DAMAGE), false);
			shove(e, c, 1.2, 0.4);
		}
		return true;
	}

	public boolean finSlash(ServerPlayer p) {
		if (!use(p, FIN_SLASH)) {
			return false;
		}
		anim(p, ANIM_SLASH);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		QuirkFx.ring(l, c).colours(0xF0FAFF, 0x8AB0C0).style(QuirkFx.RING_SOFT).size(3.2f).thickness(0.25f).life(8).spawn();
		QuirkFx.ring(l, c.add(0, 0.3, 0)).colours(0xFFFFFF, 0x6A90A0).size(2.6f).thickness(0.15f).life(6).spawn();
		l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 6, 1.2, 0.2, 1.2, 0);
		OmnitrixSounds.play(l, c, BITE_SOUND, 0.8f, 1.6f);
		for (LivingEntity e : around(p, c, 3.25)) {
			hurt(p, e, damage(p, SLASH_DAMAGE), false);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
		}
		return true;
	}

	public boolean waterDash(ServerPlayer p) {
		if (!use(p, DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		if (inWater(p)) {
			p.setDeltaMovement(look.scale(2.2));
			l.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.8, p.getZ(), 30, 0.4, 0.4, 0.4, 0.2);
			QuirkFx.trail(l, p).colours(0xE0F4FF, 0x3A8AE0).size(0.45f).size2(10f).life(18).vec(new Vec3(0, 0.6, 0)).spawn();
			OmnitrixSounds.play(l, p.position(), SPLASH, 0.8f, 1.4f);
		} else {
			Vec3 f = new Vec3(look.x, 0, look.z).normalize();
			p.setDeltaMovement(f.x * 0.75, 0.55, f.z * 0.75);
			l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.3, 0.1, 0.3, 0.1);
			OmnitrixSounds.play(l, p.position(), SPLASH, 0.5f, 1.6f);
		}
		p.hurtMarked = true;
		p.resetFallDistance();
		return true;
	}

	public boolean breach(ServerPlayer p) {
		if (!use(p, BREACH)) {
			return false;
		}
		anim(p, ANIM_BREACH);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z).normalize();
		if (inWater(p)) {
			p.setDeltaMovement(f.x * 0.8, 1.6, f.z * 0.8);
			l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.4, 0.6, 0.3);
			QuirkFx.ring(l, p.position().add(0, 0.5, 0)).colours(0xFFFFFF, 0x3A9AE0).size(2.5f).thickness(0.5f).life(12).ground().spawn();
			OmnitrixSounds.play(l, p.position(), SPLASH, 1.2f, 0.8f);
		} else {
			p.setDeltaMovement(-f.x * 0.9, 0.7, -f.z * 0.9);
			OmnitrixSounds.play(l, p.position(), SPLASH, 0.5f, 1.8f);
		}
		p.hurtMarked = true;
		p.resetFallDistance();
		return true;
	}

	/** Z: in water a whirlpool; on land a spray of mud. */
	public boolean whirlpool(ServerPlayer p) {
		if (!use(p, WHIRLPOOL)) {
			return false;
		}
		ServerLevel l = level(p);
		if (inWater(p)) {
			anim(p, ANIM_WHIRL);
			Vec3 at = p.position().add(p.getLookAngle().scale(4));
			WHIRLS.put(p.getUUID(), new Whirl(l.dimension(), at, l.getGameTime() + WHIRL_TICKS));
			QuirkFx.ring(l, at).colours(0xE0F4FF, 0x2A7AD0).style(QuirkFx.RING_SOFT).size(WHIRL_RADIUS).thickness(0.8f).life(WHIRL_TICKS / 3).spawn();
			OmnitrixSounds.play(l, at, WHIRL, 1.2f, 0.8f);
			return true;
		}
		anim(p, ANIM_MUD);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		BlockState mud = Blocks.MUD.defaultBlockState();
		QuirkFx.cone(l, eye.add(look.scale(0.7)).add(0, -0.4, 0), look).colours(0x8A6A4A, 0x3A2A1A).style(QuirkFx.CONE_DEBRIS).size(2.2f).length(6f).life(12)
				.spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, mud), eye.x + look.x * 2, eye.y - 0.4, eye.z + look.z * 2, 40, 1.0, 0.5, 1.0, 0.3);
		OmnitrixSounds.play(l, p.position(), SPLASH, 1.0f, 0.6f);
		for (LivingEntity e : around(p, eye.add(look.scale(3)), 4.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= 6.5 && to.normalize().dot(look) > Math.cos(Math.toRadians(40))) {
				hurt(p, e, damage(p, MUD_DAMAGE), false);
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), p);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), p);
			}
		}
		return true;
	}

	public boolean tidalRoar(ServerPlayer p) {
		if (!use(p, ROAR_MOVE)) {
			return false;
		}
		anim(p, ANIM_ROAR);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.2, 0);
		QuirkFx.ring(l, c).colours(0xE0F4FF, COLOUR).style(QuirkFx.RING_SOFT).size(8f).thickness(0.5f).life(16).spawn();
		OmnitrixSounds.play(l, c, ROAR, 1.4f, 0.9f);
		QuirkCamera.shakeAround(l, c, 12, 0.3f, 10);
		for (LivingEntity e : around(p, c, 8f)) {
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), p);
			shove(e, c, 0.7, 0.2);
			com.projecthero.mod.quirk.QuirkThreat.add(e, p, 3f);
		}
		return true;
	}

	public boolean feedingFrenzy(ServerPlayer p) {
		if (!use(p, FRENZY)) {
			return false;
		}
		anim(p, ANIM_FRENZY);
		ServerLevel l = level(p);
		setTimer(p, "frenzy_end", l.getGameTime() + FRENZY_TICKS);
		setFlag(p, F_FRENZY, true);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FRENZY_FIST, 3.0, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, FRENZY_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		OmnitrixSounds.play(l, p.position(), ROAR, 1.2f, 0.7f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFB0A0, 0xC02020).size(3f).thickness(0.5f).life(12).ground().spawn();
		return true;
	}

	private void endFrenzy(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, FRENZY_FIST);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, FRENZY_SPEED);
		if (timer(p, "frenzy_end") > 0) {
			setTimer(p, "frenzy_end", 0);
		}
		setFlag(p, F_FRENZY, false);
	}

	public boolean drench(ServerPlayer p) {
		if (!use(p, DRENCH)) {
			return false;
		}
		anim(p, ANIM_DRENCH);
		ServerLevel l = level(p);
		long now = l.getGameTime();
		setDryness(p, dryness(p, now) - MAX_DRY / 2, inWater(p), inRain(p));
		p.clearFire();
		l.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + p.getBbHeight(), p.getZ(), 60, 0.5, 0.3, 0.5, 0.3);
		l.sendParticles(ParticleTypes.FALLING_WATER, p.getX(), p.getY() + p.getBbHeight() + 0.5, p.getZ(), 30, 0.6, 0.2, 0.6, 0);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0x3A9AE0).style(QuirkFx.RING_STEAM).size(2.5f).thickness(0.5f).life(12).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SPLASH, 1.2f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity) {
			anim(p, ANIM_PUNCH);
		}
		return false;
	}

	/** What his claws dig straight through. */
	public static boolean diggable(BlockState s) {
		return s.is(BlockTags.SAND) || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.SOUL_SAND)
				|| s.is(Blocks.SOUL_SOIL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)
				|| s.is(Blocks.MUDDY_MANGROVE_ROOTS) || s.is(BlockTags.CONCRETE_POWDER);
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		return dig(p, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Use on sand, gravel, dirt...: it is gone (and drops), one block per use. True if it dug. */
	public boolean dig(ServerPlayer p, BlockPos pos) {
		ServerLevel l = level(p);
		BlockState s = l.getBlockState(pos);
		if (!diggable(s) || !p.mayInteract(l, pos) || !p.mayBuild() || cooldownLeft(p, DIG) > 0) {
			return false;
		}
		cooldown(p, DIG, -1);
		anim(p, ANIM_DIG);
		l.destroyBlock(pos, !p.isCreative(), p);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.1);
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ---------------------------------------------------------------- damage

	/** He breathes water: never drowns. */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_DROWNING);
	}
}
