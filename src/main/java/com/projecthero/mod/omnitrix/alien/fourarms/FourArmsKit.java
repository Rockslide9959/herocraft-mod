package com.projecthero.mod.omnitrix.alien.fourarms;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Four Arms, the Tetramand -- big (1.4x), red, four arms, four eyes; 40 health, heavy armour, hard to knock back,
 * slower on his feet.
 *
 * <pre>
 *   R        Haymaker        a four-fisted hammer blow (12, launches)    Shift+R  Quad Barrage   four rapid punches (4 x 5)
 *   G        Thunder Clap    a clap shockwave cone (8, slows)            Shift+G  Grab / Throw   grab what you look at; G again throws it (12)
 *   X        Super Leap      a huge bound; landing shockwave (6)        Shift+X  Charge         a shoulder charge through everything (7)
 *   Z        Ground Pound    fists into the ground, a ring of force (10) Shift+Z  Meteor Drop    dive from the air; harder the higher (8-18)
 *   C        War Cry         a roar: foes weakened and slowed, you
 *                            stronger                                     Shift+C  Iron Stance    4 s braced: 70% less damage, no knockback
 *   Attack   heavy punches (the fist hits for 7 and shoves); Use on a creature: grab it; Use while holding: throw it
 * </pre>
 */
public final class FourArmsKit extends AlienKit {
	public static final String ID = "four_arms";
	public static final int COLOUR = 0xD8323A;

	public static final int ANIM_PUNCH_L = 1;
	public static final int ANIM_PUNCH_R = 2;
	public static final int ANIM_HAYMAKER = 3;
	public static final int ANIM_BARRAGE = 4;
	public static final int ANIM_CLAP = 5;
	public static final int ANIM_GRAB = 6;
	public static final int ANIM_THROW = 7;
	public static final int ANIM_LEAP = 8;
	public static final int ANIM_CHARGE = 9;
	public static final int ANIM_POUND = 10;
	public static final int ANIM_DIVE = 11;
	public static final int ANIM_ROAR = 12;
	public static final int ANIM_STANCE = 13;

	public static final int F_HOLDING = 1;
	public static final int F_STANCE = 2;
	public static final int F_LEAPING = 4;
	public static final int F_DIVING = 8;
	public static final int F_CHARGING = 16;

	public static final String HAYMAKER = "haymaker";
	public static final String BARRAGE = "quad_barrage";
	public static final String CLAP = "thunder_clap";
	public static final String GRAB = "grab";
	public static final String THROW = "throw";
	public static final String LEAP = "super_leap";
	public static final String CHARGE = "charge";
	public static final String POUND = "ground_pound";
	public static final String METEOR = "meteor_drop";
	public static final String ROAR = "war_cry";
	public static final String STANCE = "iron_stance";

	public static final float HAYMAKER_DAMAGE = 12f;
	public static final float BARRAGE_DAMAGE = 5f;
	public static final float CLAP_DAMAGE = 8f;
	public static final float CLAP_RANGE = 9f;
	public static final float THROW_DAMAGE = 12f;
	public static final float LEAP_LAND_DAMAGE = 6f;
	public static final float CHARGE_DAMAGE = 7f;
	public static final float POUND_DAMAGE = 10f;
	public static final float POUND_RADIUS = 6f;
	public static final float STANCE_FACTOR = 0.3f;
	public static final int STANCE_TICKS = 80;
	public static final int HOLD_TICKS = 200;
	public static final double REACH = 4.5;

	private static final ResourceLocation STANCE_KB = PowerToggles.id("omnitrix_four_arms_stance");

	/** player -> creatures this Charge already hit. */
	private static final Map<UUID, Set<Integer>> CHARGE_HITS = new ConcurrentHashMap<>();
	/** thrown creature id -> the thrower and when it was thrown (it hits on landing). */
	private static final Map<Integer, Thrown> THROWN = new ConcurrentHashMap<>();

	private record Thrown(UUID thrower, long at) {
	}

	private FourArmsKit() {
	}

	public static void register() {
		Aliens.register(new FourArmsKit());
	}

	public static void clearSessionState() {
		CHARGE_HITS.clear();
		THROWN.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(40f).speed(0.85f).scale(1.4f).armor(8f, 2f).knockbackResist(0.6f).attack(6f).step(0.6f).jump(0.12f)
				.safeFall(6f).reach(0.5f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(HAYMAKER, AbilitySlot.SLOT_1, 30), AlienMove.shift(BARRAGE, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(CLAP, AbilitySlot.SLOT_2, 160), AlienMove.shift(GRAB, AbilitySlot.SLOT_2, 20),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 60), AlienMove.shift(CHARGE, AbilitySlot.SLOT_3, 100),
				AlienMove.tap(POUND, AbilitySlot.SLOT_4, 240), AlienMove.shift(METEOR, AbilitySlot.SLOT_4, 280),
				AlienMove.tap(ROAR, AbilitySlot.SLOT_6, 500), AlienMove.shift(STANCE, AbilitySlot.SLOT_6, 360),
				AlienMove.vanilla("heavy_punch", "Attack", 0), AlienMove.vanilla(THROW, "Use (holding) / G (holding)", 200));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.8f, 1.1f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.6f).life(14).ground().spawn();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		drop(p, false);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, STANCE_KB);
		CHARGE_HITS.remove(p.getUUID());
		THROWN.values().removeIf(t -> t.thrower().equals(p.getUUID()));
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// the held creature rides above his head
		if (s.heldEntity != 0) {
			Entity e = l.getEntity(s.heldEntity);
			long since = now - timer(p, "held_at");
			if (!(e instanceof LivingEntity le) || !le.isAlive() || since > HOLD_TICKS) {
				drop(p, true);
			} else {
				Vec3 at = p.position().add(0, p.getBbHeight() + 0.15, 0).add(p.getLookAngle().multiply(1, 0, 1).scale(0.3));
				le.teleportTo(at.x, at.y, at.z);
				le.setDeltaMovement(Vec3.ZERO);
				le.resetFallDistance();
				le.hurtMarked = true;
			}
		}
		// Quad Barrage: a punch every 4 ticks
		long barrage = timer(p, "barrage_end");
		if (barrage > 0) {
			if (now >= barrage) {
				setTimer(p, "barrage_end", 0);
			} else if ((barrage - now) % 4 == 0) {
				int n = (int) ((barrage - now) / 4) - 1; // 3, 2, 1, 0: the last one sends them flying
				anim(p, n % 2 == 0 ? ANIM_PUNCH_L : ANIM_PUNCH_R);
				LivingEntity t = lookTarget(p, REACH);
				OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.8f, 1.0f + n * 0.08f);
				if (t != null) {
					t.invulnerableTime = 0;
					hurt(p, t, BARRAGE_DAMAGE, false);
					shove(t, p.position(), n == 0 ? 1.2 : 0.25, n == 0 ? 0.35 : 0.05);
					l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
				}
			}
		}
		// Super Leap / Meteor Drop landing
		boolean leaping = flag(p, F_LEAPING);
		boolean diving = flag(p, F_DIVING);
		if ((leaping || diving) && now - timer(p, "air_at") > 4 && (p.onGround() || p.isInWater())) {
			setFlag(p, F_LEAPING, false);
			setFlag(p, F_DIVING, false);
			float extra = diving ? (float) Math.min(10.0, Math.max(0, timer(p, "dive_from") / 1000.0 - p.getY()) * 0.8) : 0f;
			land(p, diving ? 5f : 3.5f, diving ? 8f + extra : LEAP_LAND_DAMAGE, diving);
			p.resetFallDistance();
		}
		if (leaping || diving) {
			p.resetFallDistance();
		}
		long hop = timer(p, "dive_hop");
		if (hop > 0 && now >= hop) {
			setTimer(p, "dive_hop", 0);
			startDive(p);
		}
		// Charge
		long charge = timer(p, "charge_end");
		if (charge > 0) {
			if (now >= charge || p.horizontalCollision) {
				setTimer(p, "charge_end", 0);
				setFlag(p, F_CHARGING, false);
				CHARGE_HITS.remove(p.getUUID());
				if (p.horizontalCollision) {
					QuirkCamera.shake(p, 0.4f, 8);
					OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_SLAM, 0.8f, 1.3f);
				}
			} else {
				Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
				p.setDeltaMovement(dir.x * 0.9, Math.min(0, p.getDeltaMovement().y), dir.z * 0.9);
				p.hurtMarked = true;
				Set<Integer> hits = CHARGE_HITS.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
				for (LivingEntity e : around(p, p.position().add(dir.scale(1.0)).add(0, 1, 0), 1.9)) {
					if (hits.add(e.getId())) {
						hurt(p, e, CHARGE_DAMAGE, false);
						shove(e, p.position(), 1.4, 0.4);
						OmnitrixSounds.play(l, e.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 1.0f, 0.7f);
					}
				}
				if (now % 2 == 0) {
					BlockState under = l.getBlockState(p.blockPosition().below());
					if (!under.isAir()) {
						l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.4, 0.05, 0.4, 0.1);
					}
				}
			}
		}
		// Iron Stance
		long stance = timer(p, "stance_end");
		if (stance > 0) {
			if (now >= stance) {
				setTimer(p, "stance_end", 0);
				setFlag(p, F_STANCE, false);
				PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, STANCE_KB);
			} else {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 1, false, false, false));
			}
		}
		tickThrown(l);
	}

	/** A thrown creature hits (or is dropped after 2 s): damage to it and a little to what it lands among. */
	private void tickThrown(ServerLevel l) {
		long now = l.getGameTime();
		for (Map.Entry<Integer, Thrown> e : List.copyOf(THROWN.entrySet())) {
			Entity ent = l.getEntity(e.getKey());
			ServerPlayer thrower = l.getServer().getPlayerList().getPlayer(e.getValue().thrower());
			if (ent == null || thrower == null || now - e.getValue().at() > 60) {
				THROWN.remove(e.getKey());
				continue;
			}
			if (!(ent instanceof LivingEntity le)) {
				THROWN.remove(e.getKey());
				continue;
			}
			if (now - e.getValue().at() > 2 && (le.onGround() || le.horizontalCollision || le.isInWater())) {
				THROWN.remove(e.getKey());
				le.invulnerableTime = 0;
				hurt(thrower, le, THROW_DAMAGE, false);
				for (LivingEntity o : around(thrower, le.position(), 2.5)) {
					if (o != le) {
						hurt(thrower, o, 4f, false);
						shove(o, le.position(), 0.6, 0.3);
					}
				}
				QuirkFx.ring(l, le.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2.5f).thickness(0.5f).life(12).ground().spawn();
				OmnitrixSounds.play(l, le.position(), OmnitrixSounds.FOUR_ARMS_SLAM, 1.0f, 1.2f);
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					barrage(p);
				} else {
					haymaker(p);
				}
			}
			case SLOT_2 -> {
				if (Omnitrix.state(p).heldEntity != 0) {
					throwHeld(p);
				} else if (shift) {
					grab(p, lookTarget(p, REACH));
				} else {
					clap(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					charge(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					meteorDrop(p);
				} else {
					groundPound(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					ironStance(p);
				} else {
					warCry(p);
				}
			}
			default -> {
			}
		}
	}

	public boolean haymaker(ServerPlayer p) {
		if (!use(p, HAYMAKER)) {
			return false;
		}
		anim(p, ANIM_HAYMAKER);
		ServerLevel l = level(p);
		LivingEntity t = lookTarget(p, REACH);
		Vec3 fist = p.getEyePosition().add(p.getLookAngle().scale(2.0));
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 1.2f, 0.7f);
		if (t != null) {
			t.invulnerableTime = 0;
			hurt(p, t, HAYMAKER_DAMAGE, false);
			shove(t, p.position(), 1.7, 0.45);
			Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
			QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, 0xFFC0A0).size(1.8f).thickness(0.4f).life(8).spawn();
			l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		} else {
			QuirkFx.ring(l, fist).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).colours(0xFFFFFF, 0xE0D0C0).size(1.2f).thickness(0.3f).life(6).spawn();
		}
		return true;
	}

	public boolean barrage(ServerPlayer p) {
		if (!use(p, BARRAGE)) {
			return false;
		}
		anim(p, ANIM_BARRAGE);
		setTimer(p, "barrage_end", p.level().getGameTime() + 17);
		return true;
	}

	public boolean clap(ServerPlayer p) {
		if (!use(p, CLAP)) {
			return false;
		}
		anim(p, ANIM_CLAP);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 hands = eye.add(look.scale(1.2)).add(0, -0.4, 0);
		QuirkFx.ring(l, hands).flags(QuirkFx.F_VERTICAL).vec(look).colours(0xFFFFFF, 0xC8D8FF).size(3.5f).thickness(0.6f).life(12).spawn();
		QuirkFx.cone(l, hands, look).style(QuirkFx.CONE_DEBRIS).colours(0xD8D0C0, 0x8A8070).size(2.5f).length(CLAP_RANGE).life(14).spawn();
		OmnitrixSounds.play(l, hands, OmnitrixSounds.FOUR_ARMS_CLAP, 1.4f, 1.0f);
		QuirkCamera.shakeAround(l, hands, 14, 0.5f, 10);
		for (LivingEntity e : around(p, eye.add(look.scale(CLAP_RANGE * 0.5)), CLAP_RANGE * 0.7)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= CLAP_RANGE + 0.5 && to.normalize().dot(look) > Math.cos(Math.toRadians(45))) {
				hurt(p, e, CLAP_DAMAGE, false);
				shove(e, p.position(), 1.5, 0.35);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			}
		}
		return true;
	}

	/** Grabs {@code target} (anything up to horse size; not the Ender Dragon / Wither, not a boss with a threat table). */
	public boolean grab(ServerPlayer p, LivingEntity target) {
		if (target == null) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.four_arms.nothing_to_grab")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (target.getBbHeight() > 3.2f || target.getBbWidth() > 2.2f || target instanceof EnderDragon || target instanceof WitherBoss
				|| com.projecthero.mod.quirk.QuirkThreat.add(target, p, 6f)) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.four_arms.too_big")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, GRAB)) {
			return false;
		}
		anim(p, ANIM_GRAB);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.heldEntity = target.getId();
		n.kitFlags |= F_HOLDING;
		Omnitrix.save(p, n);
		setTimer(p, "held_at", p.level().getGameTime());
		target.stopRiding();
		OmnitrixSounds.play(level(p), target.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.7f, 0.6f);
		return true;
	}

	public boolean throwHeld(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		Entity e = level(p).getEntity(s.heldEntity);
		if (!(e instanceof LivingEntity le)) {
			drop(p, false);
			return false;
		}
		drop(p, false);
		anim(p, ANIM_THROW);
		Vec3 look = p.getLookAngle();
		le.setDeltaMovement(look.x * 2.1, look.y * 2.1 + 0.35, look.z * 2.1);
		le.hurtMarked = true;
		THROWN.put(le.getId(), new Thrown(p.getUUID(), p.level().getGameTime()));
		cooldown(p, THROW, -1);
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_THROW, 1.2f, 1.0f);
		return true;
	}

	/** Lets go of whatever he holds ({@code gently}: it is set down in front of him). */
	private void drop(ServerPlayer p, boolean gently) {
		OmnitrixState s = Omnitrix.state(p);
		if (s.heldEntity == 0) {
			return;
		}
		Entity e = level(p).getEntity(s.heldEntity);
		OmnitrixState n = s.copy();
		n.heldEntity = 0;
		n.kitFlags &= ~F_HOLDING;
		Omnitrix.save(p, n);
		if (e != null && gently) {
			Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
			e.teleportTo(at.x, p.getY(), at.z);
		}
	}

	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.x * 1.4, 1.15, h.z * 1.4);
		p.hurtMarked = true;
		setFlag(p, F_LEAPING, true);
		setTimer(p, "air_at", p.level().getGameTime());
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2.2f).thickness(0.5f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_SLAM, 0.7f, 1.4f);
		return true;
	}

	public boolean charge(ServerPlayer p) {
		if (!use(p, CHARGE)) {
			return false;
		}
		anim(p, ANIM_CHARGE);
		setFlag(p, F_CHARGING, true);
		setTimer(p, "charge_end", p.level().getGameTime() + 14);
		CHARGE_HITS.put(p.getUUID(), new HashSet<>());
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.7f, 1.3f);
		return true;
	}

	public boolean groundPound(ServerPlayer p) {
		if (!p.onGround()) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.four_arms.need_ground")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, POUND)) {
			return false;
		}
		anim(p, ANIM_POUND);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		BlockState under = l.getBlockState(p.blockPosition().below());
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(POUND_RADIUS).thickness(0.9f).life(16).ground().spawn();
		QuirkFx.decal(l, c.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(4.5f).life(300).spawn();
		if (!under.isAir()) {
			QuirkFx.shards(l, c.add(0, 0.2, 0)).block(under).size(3f).count(26).spawn();
		}
		OmnitrixSounds.play(l, c, OmnitrixSounds.FOUR_ARMS_SLAM, 1.5f, 0.8f);
		QuirkCamera.shakeAround(l, c, 18, 0.7f, 14);
		for (LivingEntity e : around(p, c, POUND_RADIUS)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			hurt(p, e, (float) (POUND_DAMAGE * (1.0 - 0.4 * Math.min(1.0, d / POUND_RADIUS))), false);
			shove(e, c, 0.8, 0.75);
		}
		return true;
	}

	public boolean meteorDrop(ServerPlayer p) {
		if (!use(p, METEOR)) {
			return false;
		}
		if (p.onGround()) {
			// a hop first, then the dive
			p.setDeltaMovement(p.getDeltaMovement().x, 1.25, p.getDeltaMovement().z);
			p.hurtMarked = true;
			anim(p, ANIM_LEAP);
			setTimer(p, "dive_hop", p.level().getGameTime() + 9);
			setFlag(p, F_LEAPING, true);
			setTimer(p, "air_at", p.level().getGameTime());
			return true;
		}
		startDive(p);
		return true;
	}

	private void startDive(ServerPlayer p) {
		anim(p, ANIM_DIVE);
		setFlag(p, F_LEAPING, false);
		setFlag(p, F_DIVING, true);
		setTimer(p, "air_at", p.level().getGameTime());
		setTimer(p, "dive_from", (long) (p.getY() * 1000.0));
		p.setDeltaMovement(p.getDeltaMovement().x * 0.3, -2.2, p.getDeltaMovement().z * 0.3);
		p.hurtMarked = true;
		OmnitrixSounds.play(level(p), p.position(), OmnitrixSounds.FOUR_ARMS_ROAR, 0.8f, 0.9f);
	}

	private void land(ServerPlayer p, float radius, float damage, boolean big) {
		ServerLevel l = level(p);
		Vec3 c = p.position();
		BlockState under = l.getBlockState(p.blockPosition().below());
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(radius + 1f).thickness(0.8f).life(14).ground().spawn();
		if (big) {
			QuirkFx.decal(l, c.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A2420).size(radius).life(300).spawn();
			if (!under.isAir()) {
				QuirkFx.shards(l, c.add(0, 0.2, 0)).block(under).size(2.5f).count(20).spawn();
			}
		}
		OmnitrixSounds.play(l, c, OmnitrixSounds.FOUR_ARMS_SLAM, big ? 1.5f : 1.0f, big ? 0.7f : 1.0f);
		QuirkCamera.shakeAround(l, c, 14, big ? 0.7f : 0.4f, 10);
		for (LivingEntity e : around(p, c, radius)) {
			hurt(p, e, damage, false);
			shove(e, c, 0.9, 0.5);
		}
	}

	public boolean warCry(ServerPlayer p) {
		if (!use(p, ROAR)) {
			return false;
		}
		anim(p, ANIM_ROAR);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.6, 0);
		QuirkFx.ring(l, c).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xFF8080).size(4f).life(16).spawn();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, COLOUR).size(8f).life(20).ground().spawn();
		OmnitrixSounds.play(l, c, OmnitrixSounds.FOUR_ARMS_ROAR, 1.6f, 0.9f);
		QuirkCamera.shakeAround(l, c, 12, 0.4f, 12);
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0));
		for (LivingEntity e : around(p, c, 10)) {
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
			if (e.distanceToSqr(c) < 25) {
				shove(e, c, 0.8, 0.2);
			}
			com.projecthero.mod.quirk.QuirkThreat.add(e, p, 4f);
		}
		return true;
	}

	public boolean ironStance(ServerPlayer p) {
		if (!use(p, STANCE)) {
			return false;
		}
		anim(p, ANIM_STANCE);
		setFlag(p, F_STANCE, true);
		setTimer(p, "stance_end", p.level().getGameTime() + STANCE_TICKS);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, STANCE_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, 0xB0A090).size(2f).thickness(0.6f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), OmnitrixSounds.FOUR_ARMS_STANCE, 1.0f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		OmnitrixState s = Omnitrix.state(p);
		anim(p, (p.tickCount / 4) % 2 == 0 ? ANIM_PUNCH_L : ANIM_PUNCH_R);
		if (target instanceof LivingEntity le) {
			shove(le, p.position(), 0.5, 0.15);
			OmnitrixSounds.play(level(p), le.position(), OmnitrixSounds.FOUR_ARMS_PUNCH, 0.7f, 1.1f);
		}
		return s.heldEntity != 0 && target.getId() == s.heldEntity; // never punch what you are holding
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && Omnitrix.state(p).heldEntity != 0) {
			return throwHeld(p) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (Omnitrix.state(p).heldEntity != 0) {
			return throwHeld(p) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return target instanceof LivingEntity le && grab(p, le) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) && (flag(p, F_LEAPING) || flag(p, F_DIVING))) {
			return false; // his own landings
		}
		// whatever he holds cannot hurt him
		OmnitrixState s = Omnitrix.state(p);
		return s.heldEntity == 0 || source.getEntity() == null || source.getEntity().getId() != s.heldEntity;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return flag(p, F_STANCE) ? amount * STANCE_FACTOR : amount;
	}
}
