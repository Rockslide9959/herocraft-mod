package com.projecthero.mod.omnitrix.alien.humungousaur;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Humungousaur, the Vaxasaurian (Recalibrated Omnitrix) -- a BASIC kit (user: "Just make them basic versions of the aliens
 * for now please that we can revamp later"). A giant dinosaur brawler who can grow bigger still.
 *
 * <pre>
 *   Passive  Vaxasaurian hide  armour 10, hard to knock back, long falls shrugged off. Size-shift (V): x1.5 -> x2.2 -> x3.0, each
 *                              step +20% damage, more armour, reach and knockback resistance, a little slower. Weak: big and slow.
 *   R        Haymaker          a huge punch (8, knocked away)
 *   Shift+R  Uppercut          launches the creature skyward (14)
 *   G        Ground Pound      both fists into the ground: a shockwave ring (15 in 4.5 blocks, +1.5 a size step, thrown up)
 *   Shift+G  Tail Sweep        a full spin of the tail (12 in 3.5 blocks, +1 a step, swept aside)
 *   X        Stampede          a 1 s charge, ramming what is in the way (12, bowled over)
 *   Shift+X  Dino Leap         a great bound; the landing shakes the ground (10 in 4)
 *   Z        Boulder Toss      tears up a chunk of the ground and hurls it (17; a 2.5-block burst of 8)
 *   Shift+Z  Grab and Hurl     seizes the creature in front (not a boss) and throws it where you look (12)
 *   V        Grow              one size step bigger (up to x3.0)
 *   Shift+V  Shrink            one size step smaller
 *   C        Primal Roar       a 10-block cone of a roar (12, weakened and slowed)
 *   Shift+C  Titanic Slam      ULTIMATE: a leap and a crash down -- 55 at the centre, 40 at the edge, 8 blocks (+2 a step)
 * </pre>
 * Every damage is x(1 + 0.2 a size step); the Titanic Slam x(1 + 0.1 a step), so it stays within 40-70.
 */
public final class HumungousaurKit extends AlienKit {
	public static final String ID = Aliens.HUMUNGOUSAUR.id();

	public static final int ANIM_PUNCH = 1;
	public static final int ANIM_UPPER = 2;
	public static final int ANIM_POUND = 3;
	public static final int ANIM_TAIL = 4;
	public static final int ANIM_CHARGE = 5;
	public static final int ANIM_LEAP = 6;
	public static final int ANIM_LAND = 7;
	public static final int ANIM_TOSS = 8;
	public static final int ANIM_GRAB = 9;
	public static final int ANIM_GROW = 10;
	public static final int ANIM_SHRINK = 11;
	public static final int ANIM_ROAR = 12;
	public static final int ANIM_SLAM_UP = 13;
	public static final int ANIM_SLAM = 14;

	public static final int F_CHARGE = 1;
	public static final int F_LEAP = 2;
	public static final int F_SLAM = 4;

	public static final String PUNCH = "haymaker";
	public static final String UPPER = "uppercut";
	public static final String POUND = "ground_pound";
	public static final String TAIL = "tail_sweep";
	public static final String CHARGE = "stampede";
	public static final String LEAP = "dino_leap";
	public static final String TOSS = "boulder_toss";
	public static final String GRAB = "grab_and_hurl";
	public static final String GROW = "grow";
	public static final String SHRINK = "shrink";
	public static final String ROAR = "primal_roar";
	public static final String SLAM = "titanic_slam";

	/** Synced: the size step (absent = 0, 1, 2) and when a leap / charge / slam began. */
	public static final String STAGE = "stage", MOVE_START = "move_start";

	public static final int MAX_STAGE = 2;
	public static final float[] STAGE_SCALE = { 0f, 0.7f, 1.5f }; // added to the body's x1.5 (ADD_MULTIPLIED_BASE): 1.5 / 2.2 / 3.0
	public static final float[] STAGE_ARMOR = { 0f, 3f, 6f };
	public static final float[] STAGE_ATTACK = { 0f, 2f, 5f };
	public static final float[] STAGE_REACH = { 0f, 0.75f, 1.5f };
	public static final float[] STAGE_KB = { 0f, 0.15f, 0.3f };
	public static final float[] STAGE_SPEED = { 0f, -0.08f, -0.16f };

	private static final ResourceLocation M_SCALE = PowerToggles.id("humungousaur_grow_scale");
	private static final ResourceLocation M_ARMOR = PowerToggles.id("humungousaur_grow_armor");
	private static final ResourceLocation M_ATTACK = PowerToggles.id("humungousaur_grow_attack");
	private static final ResourceLocation M_REACH_E = PowerToggles.id("humungousaur_grow_reach_entity");
	private static final ResourceLocation M_REACH_B = PowerToggles.id("humungousaur_grow_reach_block");
	private static final ResourceLocation M_KB = PowerToggles.id("humungousaur_grow_knockback");
	private static final ResourceLocation M_SPEED = PowerToggles.id("humungousaur_grow_speed");

	public static final float PUNCH_DAMAGE = 8f;
	public static final float UPPER_DAMAGE = 14f;
	public static final float POUND_DAMAGE = 15f;
	public static final double POUND_RADIUS = 4.5;
	public static final float TAIL_DAMAGE = 12f;
	public static final double TAIL_RADIUS = 3.5;
	public static final float CHARGE_DAMAGE = 12f;
	public static final int CHARGE_TICKS = 20;
	public static final float LAND_DAMAGE = 10f;
	public static final double LAND_RADIUS = 4.0;
	public static final float TOSS_DAMAGE = 17f, TOSS_BLAST = 8f;
	public static final float GRAB_DAMAGE = 12f;
	public static final float ROAR_DAMAGE = 12f;
	public static final double ROAR_RANGE = 10.0, ROAR_DEG = 70.0;
	public static final float SLAM_DAMAGE = 55f, SLAM_EDGE = 40f;
	public static final double SLAM_RADIUS = 8.0;
	public static final int AIR_MAX = 60;

	public static final OmnitrixSfx.Cue SND_PUNCH = OmnitrixSfx.cue("humungousaur_punch")
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.5f, 0.6f)
			.layer(SoundEvents.IRON_GOLEM_ATTACK, 0.45f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.3f, 0.6f, 1);
	public static final OmnitrixSfx.Cue SND_QUAKE = OmnitrixSfx.cue("humungousaur_quake")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.4f, 0.6f)
			.layer(SoundEvents.RAVAGER_STEP, 0.6f, 0.5f)
			.layer(SoundEvents.DEEPSLATE_BREAK, 0.55f, 0.6f, 1)
			.layer(SoundEvents.GRAVEL_BREAK, 0.5f, 0.6f, 3);
	public static final OmnitrixSfx.Cue SND_SWOOSH = OmnitrixSfx.cue("humungousaur_swoosh")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 0.55f)
			.layer(SoundEvents.RAVAGER_ATTACK, 0.35f, 0.8f, 2);
	public static final OmnitrixSfx.Cue SND_ROAR = OmnitrixSfx.cue("humungousaur_roar")
			.layer(SoundEvents.RAVAGER_ROAR, 0.6f, 0.75f)
			.layer(SoundEvents.ENDER_DRAGON_GROWL, 0.15f, 1.3f)
			.layer(SoundEvents.POLAR_BEAR_WARNING, 0.4f, 0.6f, 2);
	public static final OmnitrixSfx.Cue SND_GROW = OmnitrixSfx.cue("humungousaur_grow")
			.layer(SoundEvents.RAVAGER_STUNNED, 0.45f, 0.6f)
			.layer(SoundEvents.ARMOR_EQUIP_TURTLE.value(), 0.5f, 0.6f, 2)
			.layer(SoundEvents.DRIPSTONE_BLOCK_BREAK, 0.4f, 0.6f, 4);
	public static final OmnitrixSfx.Cue SND_SHRINK = OmnitrixSfx.cue("humungousaur_shrink")
			.layer(SoundEvents.ARMOR_EQUIP_TURTLE.value(), 0.45f, 0.9f)
			.layer(SoundEvents.RAVAGER_STEP, 0.4f, 1.0f, 2);
	public static final OmnitrixSfx.Cue SND_TEAR = OmnitrixSfx.cue("humungousaur_tear")
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.6f, 0.6f)
			.layer(SoundEvents.STONE_BREAK, 0.55f, 0.6f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.35f, 0.6f, 4);
	public static final OmnitrixSfx.Cue SND_STOMP = OmnitrixSfx.cue("humungousaur_stomp")
			.layer(SoundEvents.RAVAGER_STEP, 0.5f, 0.6f)
			.layer(SoundEvents.HORSE_GALLOP, 0.4f, 0.5f)
			.jitter(0.08f).gap(5);

	private record Run(Set<Integer> hits) {
	}

	/** player -> creatures the running Stampede already rammed. */
	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();

	private HumungousaurKit() {
	}

	public static void register() {
		Aliens.register(new HumungousaurKit());
		AlienProjectileEntity.onHit("humungousaur_boulder", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), at.x, at.y + 0.3, at.z, 24, 0.8,
						0.4, 0.8, 0.2);
				OmnitrixSfx.play(l, at, SND_QUAKE, 0.9f, 1.2f);
			}
		});
	}

	public static void clearSessionState() {
		RUNS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(44).speed(0.85f).scale(1.5f).armor(10, 3).knockbackResist(0.6f).attack(5).step(0.5f).safeFall(6).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(PUNCH, AbilitySlot.SLOT_1, 14), AlienMove.shift(UPPER, AbilitySlot.SLOT_1, 80),
				AlienMove.tap(POUND, AbilitySlot.SLOT_2, 120), AlienMove.shift(TAIL, AbilitySlot.SLOT_2, 100),
				AlienMove.tap(CHARGE, AbilitySlot.SLOT_3, 100), AlienMove.shift(LEAP, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(TOSS, AbilitySlot.SLOT_4, 160), AlienMove.shift(GRAB, AbilitySlot.SLOT_4, 140),
				AlienMove.tap(GROW, AbilitySlot.SLOT_5, 40), AlienMove.shift(SHRINK, AbilitySlot.SLOT_5, 10),
				AlienMove.tap(ROAR, AbilitySlot.SLOT_6, 160), AlienMove.shift(SLAM, AbilitySlot.SLOT_6, 1200));
	}

	// ---------------------------------------------------------------- size-shift

	/** The size step (0 = the base x1.5; client-safe). */
	public static int stage(Player p) {
		return (int) Math.max(0, Math.min(MAX_STAGE, Omnitrix.state(p).readyAt(ID + "." + STAGE)));
	}

	/** Damage multiplier for the current size. */
	public static float mult(Player p) {
		return 1f + 0.2f * stage(p);
	}

	/** Sets the size step and its attribute modifiers (0 removes them all). */
	public void setStage(ServerPlayer p, int stage) {
		stage = Math.max(0, Math.min(MAX_STAGE, stage));
		setTimer(p, STAGE, stage);
		if (stage == 0) {
			clearGrowth(p);
			return;
		}
		PowerToggles.modifier(p, Attributes.SCALE, M_SCALE, STAGE_SCALE[stage], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.ARMOR, M_ARMOR, STAGE_ARMOR[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, M_ATTACK, STAGE_ATTACK[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, M_REACH_E, STAGE_REACH[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.BLOCK_INTERACTION_RANGE, M_REACH_B, STAGE_REACH[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, M_KB, STAGE_KB[stage], AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, M_SPEED, STAGE_SPEED[stage], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void clearGrowth(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.SCALE, M_SCALE);
		PowerToggles.clearModifier(p, Attributes.ARMOR, M_ARMOR);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, M_ATTACK);
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, M_REACH_E);
		PowerToggles.clearModifier(p, Attributes.BLOCK_INTERACTION_RANGE, M_REACH_B);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, M_KB);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, M_SPEED);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		RUNS.remove(p.getUUID());
		clearGrowth(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(STAGE));
		n.timers.remove(key(MOVE_START));
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !((flag(p, F_LEAP) || flag(p, F_SLAM)) && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		long t = now - timer(p, MOVE_START);
		if (flag(p, F_CHARGE)) {
			if (t >= CHARGE_TICKS) {
				setFlag(p, F_CHARGE, false);
				RUNS.remove(p.getUUID());
			} else {
				Vec3 look = p.getLookAngle();
				Vec3 h = new Vec3(look.x, 0, look.z);
				h = h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
				p.setDeltaMovement(h.x * 0.75, Math.min(p.getDeltaMovement().y, 0.0), h.z * 0.75);
				p.hurtMarked = true;
				Run run = RUNS.computeIfAbsent(p.getUUID(), k -> new Run(new HashSet<>()));
				Vec3 front = p.position().add(h.scale(0.6 * p.getScale())).add(0, p.getBbHeight() * 0.4, 0);
				for (LivingEntity e : around(p, front, 1.0 + p.getBbWidth() * 0.6)) {
					if (run.hits().add(e.getId()) && hurt(p, e, CHARGE_DAMAGE * mult(p), false)) {
						shove(e, p.position(), 1.6, 0.5);
					}
				}
				if (t % 4 == 0) {
					OmnitrixSfx.play(l, p.position(), SND_STOMP, 0.8f, 1f);
					BlockState under = l.getBlockState(p.blockPosition().below());
					if (!under.isAir()) {
						l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.4, 0.05, 0.4, 0.1);
					}
				}
			}
		}
		if (flag(p, F_LEAP) && t > 4 && (p.onGround() || t > AIR_MAX)) {
			setFlag(p, F_LEAP, false);
			land(p);
		}
		if (flag(p, F_SLAM)) {
			if (t == 10) {
				// the dive
				anim(p, ANIM_SLAM);
				p.setDeltaMovement(p.getDeltaMovement().x * 0.4, -2.2, p.getDeltaMovement().z * 0.4);
				p.hurtMarked = true;
			}
			if (t > 10 && (p.onGround() || t > AIR_MAX)) {
				setFlag(p, F_SLAM, false);
				slam(p);
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					uppercut(p);
				} else {
					punch(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					tail(p);
				} else {
					pound(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					leap(p);
				} else {
					charge(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					grab(p);
				} else {
					toss(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					shrink(p);
				} else {
					grow(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					slamStart(p);
				} else {
					roar(p);
				}
			}
			default -> {
			}
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	private static double reach(ServerPlayer p) {
		return 2.5 + p.getScale() * 1.2;
	}

	public boolean punch(ServerPlayer p) {
		if (!use(p, PUNCH)) {
			return false;
		}
		anim(p, ANIM_PUNCH);
		LivingEntity t = lookTarget(p, reach(p));
		if (t != null && hurt(p, t, PUNCH_DAMAGE * mult(p), false)) {
			shove(t, p.position(), 1.3, 0.35);
		}
		OmnitrixSfx.play(level(p), p.position(), SND_PUNCH, 1f, 1f);
		return true;
	}

	public boolean uppercut(ServerPlayer p) {
		if (!use(p, UPPER)) {
			return false;
		}
		anim(p, ANIM_UPPER);
		LivingEntity t = lookTarget(p, reach(p));
		if (t != null && hurt(p, t, UPPER_DAMAGE * mult(p), false)) {
			double res = 1.0 - Math.min(1.0, t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			t.setDeltaMovement(t.getDeltaMovement().add(0, 1.15 * Math.max(0.3, res), 0));
			t.hurtMarked = true;
		}
		OmnitrixSfx.play(level(p), p.position(), SND_PUNCH, 1.2f, 0.9f);
		return true;
	}

	/** A shockwave of {@code radius} at his feet: {@code dmg}, thrown up. */
	private void quake(ServerPlayer p, double radius, float dmg, double up) {
		ServerLevel l = level(p);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, radius)) {
			if (hurt(p, e, dmg, false)) {
				shove(e, c, 0.7, up);
			}
		}
		BlockState under = l.getBlockState(p.blockPosition().below());
		if (!under.isAir()) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.1, c.z, (int) (radius * 8), radius * 0.5, 0.05, radius * 0.5,
					0.15);
		}
		QuirkCamera.shakeAround(l, c, radius * 3, 0.35f, 8);
		OmnitrixSfx.play(l, c, SND_QUAKE, 1.2f, 1f);
	}

	public boolean pound(ServerPlayer p) {
		if (!use(p, POUND)) {
			return false;
		}
		anim(p, ANIM_POUND);
		quake(p, POUND_RADIUS + 1.5 * stage(p), POUND_DAMAGE * mult(p), 0.6);
		return true;
	}

	public boolean tail(ServerPlayer p) {
		if (!use(p, TAIL)) {
			return false;
		}
		anim(p, ANIM_TAIL);
		Vec3 c = p.position().add(0, 0.5, 0);
		for (LivingEntity e : around(p, c, TAIL_RADIUS + stage(p))) {
			if (hurt(p, e, TAIL_DAMAGE * mult(p), false)) {
				shove(e, c, 1.5, 0.3);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_SWOOSH, 1.2f, 1f);
		return true;
	}

	public boolean charge(ServerPlayer p) {
		if (!use(p, CHARGE)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		RUNS.put(p.getUUID(), new Run(new HashSet<>()));
		setTimer(p, MOVE_START, p.level().getGameTime());
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_ROAR, 0.6f, 1.2f);
		return true;
	}

	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.x * 1.1, 1.05, h.z * 1.1);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, MOVE_START, p.level().getGameTime());
		setFlag(p, F_LEAP, true);
		OmnitrixSfx.play(level(p), p.position(), SND_STOMP, 1.2f, 0.8f);
		return true;
	}

	/** The Dino Leap's landing. */
	public void land(ServerPlayer p) {
		anim(p, ANIM_LAND);
		p.fallDistance = 0f;
		quake(p, LAND_RADIUS + stage(p), LAND_DAMAGE * mult(p), 0.4);
	}

	public boolean toss(ServerPlayer p) {
		if (!use(p, TOSS)) {
			return false;
		}
		anim(p, ANIM_TOSS);
		float m = mult(p);
		AlienProjectileEntity.of(p, "humungousaur_boulder").colour(0x7A5A3A).size(0.6f + 0.2f * stage(p)).damage(TOSS_DAMAGE * m).blast(2.5f, TOSS_BLAST * m)
				.gravity(0.035).speed(1.35).life(80).spawn();
		ServerLevel l = level(p);
		BlockState under = l.getBlockState(p.blockPosition().below());
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under.isAir() ? Blocks.DIRT.defaultBlockState() : under), p.getX(), p.getY() + 0.1,
				p.getZ(), 14, 0.5, 0.1, 0.5, 0.1);
		OmnitrixSfx.play(l, p.position(), SND_TEAR, 1f, 1f);
		return true;
	}

	public boolean grab(ServerPlayer p) {
		LivingEntity t = lookTarget(p, reach(p) + 0.5);
		if (t == null || AlienStatus.isBoss(t)) {
			say(p, t == null ? "message.projecthero.omnitrix.humungousaur.no_target" : "message.projecthero.omnitrix.humungousaur.too_big");
			return false;
		}
		if (!use(p, GRAB)) {
			return false;
		}
		anim(p, ANIM_GRAB);
		hurt(p, t, GRAB_DAMAGE * mult(p), false);
		Vec3 look = p.getLookAngle();
		t.setDeltaMovement(look.x * 2.0, Math.max(0.5, look.y * 1.5 + 0.5), look.z * 2.0);
		t.hurtMarked = true;
		OmnitrixSfx.play(level(p), p.position(), SND_SWOOSH, 1.2f, 0.8f);
		return true;
	}

	public boolean grow(ServerPlayer p) {
		int s = stage(p);
		if (s >= MAX_STAGE) {
			say(p, "message.projecthero.omnitrix.humungousaur.full_size");
			return false;
		}
		if (!use(p, GROW)) {
			return false;
		}
		anim(p, ANIM_GROW);
		setStage(p, s + 1);
		OmnitrixSfx.play(level(p), p.position(), SND_GROW, 1f + 0.2f * s, 1f);
		return true;
	}

	public boolean shrink(ServerPlayer p) {
		int s = stage(p);
		if (s <= 0) {
			say(p, "message.projecthero.omnitrix.humungousaur.base_size");
			return false;
		}
		if (!use(p, SHRINK)) {
			return false;
		}
		anim(p, ANIM_SHRINK);
		setStage(p, s - 1);
		OmnitrixSfx.play(level(p), p.position(), SND_SHRINK, 1f, 1f);
		return true;
	}

	/** Creatures in a cone of {@code deg} degrees, {@code range} long, from {@code from} along {@code dir}. */
	public static List<LivingEntity> cone(ServerPlayer p, Vec3 from, Vec3 dir, double range, double deg) {
		double cos = Math.cos(Math.toRadians(deg / 2.0));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(p, from, range + 1.0)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(from);
			double d = to.length();
			if (d <= range + e.getBbWidth() * 0.5 && (d < 1.0 || to.normalize().dot(dir) >= cos)) {
				out.add(e);
			}
		}
		return out;
	}

	public boolean roar(ServerPlayer p) {
		if (!use(p, ROAR)) {
			return false;
		}
		anim(p, ANIM_ROAR);
		for (LivingEntity e : cone(p, p.getEyePosition(), p.getLookAngle(), ROAR_RANGE + stage(p) * 2, ROAR_DEG)) {
			hurt(p, e, ROAR_DAMAGE * mult(p), false);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
		}
		QuirkCamera.shakeAround(level(p), p.position(), 10, 0.25f, 6);
		OmnitrixSfx.play(level(p), p.position(), SND_ROAR, 1.4f, 1f);
		return true;
	}

	public boolean slamStart(ServerPlayer p) {
		if (!use(p, SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM_UP);
		p.setDeltaMovement(p.getDeltaMovement().x * 0.3, 1.25, p.getDeltaMovement().z * 0.3);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, MOVE_START, p.level().getGameTime());
		setFlag(p, F_SLAM, true);
		OmnitrixSfx.play(level(p), p.position(), SND_ROAR, 1.2f, 0.9f);
		return true;
	}

	/** The Titanic Slam's impact (tests call it straight). */
	public void slam(ServerPlayer p) {
		anim(p, ANIM_SLAM);
		p.fallDistance = 0f;
		ServerLevel l = level(p);
		Vec3 c = p.position();
		double radius = SLAM_RADIUS + 2 * stage(p);
		float m = 1f + 0.1f * stage(p);
		for (LivingEntity e : around(p, c, radius)) {
			double d = Math.min(1.0, e.position().distanceTo(c) / radius);
			float dmg = (float) (SLAM_DAMAGE + (SLAM_EDGE - SLAM_DAMAGE) * d) * m;
			if (hurt(p, e, dmg, false)) {
				shove(e, c, 1.2, 0.8);
			}
		}
		BlockState under = l.getBlockState(p.blockPosition().below());
		if (!under.isAir()) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.1, c.z, 80, radius * 0.5, 0.1, radius * 0.5, 0.2);
		}
		l.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, 1.5, 0.2, 1.5, 0);
		QuirkCamera.shakeAround(l, c, radius * 3, 0.7f, 14);
		OmnitrixSfx.play(l, c, SND_QUAKE, 1.6f, 0.8f);
	}

	/** Static state held for {@code p} (tests: nothing is left after turning back). */
	public static boolean holds(ServerPlayer p) {
		return RUNS.containsKey(p.getUUID());
	}
}
