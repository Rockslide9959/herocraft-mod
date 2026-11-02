package com.projecthero.mod.omnitrix.alien.echoecho;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Echo Echo, the Sonorosian (Recalibrated Omnitrix) -- a BASIC kit (user: "Just make them basic versions of the aliens for
 * now please that we can revamp later"). A small white sound-maker who splits into copies of himself that scream with him.
 *
 * <pre>
 *   Passive  Sonorosian        small and quick; every clone screams with him (R, Shift+R, G and C also come from each clone).
 *                              Weak to water: in it he takes 25% more damage and his screams are halved.
 *   R        Sonic Scream      a sound wave along the look, 14 blocks (7, knocked back); each clone adds a 3 at the same spot
 *   Shift+R  Sonic Blast       a wide cone of sound, 10 blocks (14, hurled back); clones 3 each
 *   G        Echo Ring         a ring of sound bursts out round him (12 in 5) and round every clone (3 in 3)
 *   Shift+G  Disorient         a droning hum round him, 7 blocks (5, nausea and slowness, hunters lose him)
 *   X        Sonic Leap        a blast at the ground throws him up and forward; no fall damage for 1.5 s
 *   Shift+X  Echo Swap         changes places with his farthest clone
 *   Z        Sound Barrier     3 s of a sonic shell: half damage, creatures pushed off, arrows and shots turned back
 *   Shift+Z  Echo Assault      every clone dashes at the creature you look at and screams point blank (6 each)
 *   V        Duplicate         splits off a clone (up to 3; 6 health each; they last until he turns back)
 *   Shift+V  Merge             calls the clones back into him (+2 health for each)
 *   C        Sonic Cannon      a focused beam of sound, 24 blocks, through everything in the line (16)
 *   Shift+C  Ultimate Chorus   ULTIMATE: splits into a full chorus, a 1 s build-up, then a dome of sound (50 at the centre,
 *                              40 at the 10-block edge)
 * </pre>
 */
public final class EchoEchoKit extends AlienKit {
	public static final String ID = Aliens.ECHO_ECHO.id();

	public static EntityType<EchoCloneEntity> CLONE;

	public static final int ANIM_SCREAM = 1;
	public static final int ANIM_BLAST = 2;
	public static final int ANIM_RING = 3;
	public static final int ANIM_HUM = 4;
	public static final int ANIM_LEAP = 5;
	public static final int ANIM_SWAP = 6;
	public static final int ANIM_BARRIER = 7;
	public static final int ANIM_ASSAULT = 8;
	public static final int ANIM_SPLIT = 9;
	public static final int ANIM_MERGE = 10;
	public static final int ANIM_CANNON = 11;
	public static final int ANIM_CHARGE = 12;
	public static final int ANIM_CHORUS = 13;

	public static final int F_BARRIER = 1;
	public static final int F_CHARGE = 2;
	public static final int F_LEAP = 4;

	public static final String SCREAM = "sonic_scream";
	public static final String BLAST = "sonic_blast";
	public static final String RING = "echo_ring";
	public static final String HUM = "disorient";
	public static final String LEAP = "sonic_leap";
	public static final String SWAP = "echo_swap";
	public static final String BARRIER = "sound_barrier";
	public static final String ASSAULT = "echo_assault";
	public static final String SPLIT = "duplicate";
	public static final String MERGE = "merge";
	public static final String CANNON = "sonic_cannon";
	public static final String CHORUS = "ultimate_chorus";

	public static final String BARRIER_UNTIL = "barrier_until", LEAP_UNTIL = "leap_until", CHARGE_AT = "charge_at", AIM_TARGET = "aim_target";

	public static final int MAX_CLONES = 3;
	public static final float SCREAM_DAMAGE = 7f, CLONE_DAMAGE = 3f;
	public static final double SCREAM_RANGE = 14.0;
	public static final float BLAST_DAMAGE = 14f;
	public static final double BLAST_RANGE = 10.0, BLAST_DEG = 50.0;
	public static final float RING_DAMAGE = 12f, RING_CLONE_DAMAGE = 3f;
	public static final double RING_RADIUS = 5.0, RING_CLONE_RADIUS = 3.0;
	public static final float HUM_DAMAGE = 5f;
	public static final double HUM_RADIUS = 7.0;
	public static final int LEAP_TICKS = 30;
	public static final int BARRIER_TICKS = 60;
	public static final float BARRIER_FACTOR = 0.5f;
	public static final float ASSAULT_DAMAGE = 6f;
	public static final double ASSAULT_RANGE = 16.0;
	public static final float CANNON_DAMAGE = 16f;
	public static final double CANNON_RANGE = 24.0;
	public static final int CHARGE_TICKS = 20;
	public static final float CHORUS_DAMAGE = 50f, CHORUS_EDGE = 40f, CHORUS_RADIUS = 10f;
	public static final float WATER_TAKEN = 1.25f, WATER_DEALT = 0.5f;
	public static final float MERGE_HEAL = 2f;

	public static final OmnitrixSfx.Cue SND_SCREAM = OmnitrixSfx.cue("echo_echo_scream")
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.22f, 1.9f)
			.layer(SoundEvents.ALLAY_HURT, 0.3f, 0.7f)
			.layer(SoundEvents.NOTE_BLOCK_BIT.value(), 0.2f, 1.4f, 1);
	public static final OmnitrixSfx.Cue SND_BLAST = OmnitrixSfx.cue("echo_echo_blast")
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.5f)
			.layer(SoundEvents.BREEZE_WIND_CHARGE_BURST, 0.3f, 1.2f)
			.layer(SoundEvents.ALLAY_HURT, 0.3f, 0.6f, 1);
	public static final OmnitrixSfx.Cue SND_RING = OmnitrixSfx.cue("echo_echo_ring")
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.3f, 1.8f)
			.layer(SoundEvents.BELL_RESONATE, 0.3f, 1.6f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.2f, 1.8f, 3);
	public static final OmnitrixSfx.Cue SND_HUM = OmnitrixSfx.cue("echo_echo_hum")
			.layer(SoundEvents.BEACON_AMBIENT, 0.5f, 1.4f)
			.layer(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.3f, 1.0f)
			.layer(SoundEvents.BELL_RESONATE, 0.25f, 0.8f, 4);
	public static final OmnitrixSfx.Cue SND_SPLIT = OmnitrixSfx.cue("echo_echo_split")
			.layer(SoundEvents.CHICKEN_EGG, 0.4f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.4f)
			.layer(SoundEvents.NOTE_BLOCK_BIT.value(), 0.25f, 1.8f, 2);
	public static final OmnitrixSfx.Cue SND_MERGE = OmnitrixSfx.cue("echo_echo_merge")
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 0.9f)
			.layer(SoundEvents.NOTE_BLOCK_BIT.value(), 0.25f, 1.0f, 2);
	public static final OmnitrixSfx.Cue SND_BARRIER = OmnitrixSfx.cue("echo_echo_barrier")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.35f, 1.8f)
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.25f, 1.6f);
	public static final OmnitrixSfx.Cue SND_LEAP = OmnitrixSfx.cue("echo_echo_leap")
			.layer(SoundEvents.BREEZE_JUMP, 0.4f, 1.3f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.15f, 2.0f);
	public static final OmnitrixSfx.Cue SND_CHORUS = OmnitrixSfx.cue("echo_echo_chorus")
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.55f, 1.2f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 1.7f, 2)
			.layer(SoundEvents.BELL_RESONATE, 0.5f, 1.3f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.3f, 1.4f, 3);
	public static final OmnitrixSfx.Cue SND_CHARGE = OmnitrixSfx.cue("echo_echo_charge")
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.45f, 1.5f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.2f, 1.8f, 8);

	/** player -> his clones (server entities). */
	private static final Map<UUID, List<EchoCloneEntity>> CLONES = new ConcurrentHashMap<>();
	/** player -> when the Ultimate Chorus goes off. */
	private static final Map<UUID, Long> CHARGES = new ConcurrentHashMap<>();

	private EchoEchoKit() {
	}

	public static void register() {
		CLONE = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("echo_echo_clone")),
				EntityType.Builder.<EchoCloneEntity>of(EchoCloneEntity::new, MobCategory.MISC).sized(0.36f, 1.1f).noSave().clientTrackingRange(8)
						.updateInterval(1).build("echo_echo_clone"));
		Aliens.register(new EchoEchoKit());
	}

	public static void clearSessionState() {
		CLONES.clear();
		CHARGES.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(16).speed(1.15f).scale(0.6f).armor(2, 0).attack(1).safeFall(3).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(SCREAM, AbilitySlot.SLOT_1, 20), AlienMove.shift(BLAST, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(RING, AbilitySlot.SLOT_2, 120), AlienMove.shift(HUM, AbilitySlot.SLOT_2, 200),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 30), AlienMove.shift(SWAP, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(BARRIER, AbilitySlot.SLOT_4, 240), AlienMove.shift(ASSAULT, AbilitySlot.SLOT_4, 100),
				AlienMove.tap(SPLIT, AbilitySlot.SLOT_5, 40), AlienMove.shift(MERGE, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(CANNON, AbilitySlot.SLOT_6, 160), AlienMove.shift(CHORUS, AbilitySlot.SLOT_6, 1200));
	}

	// ---------------------------------------------------------------- clones

	/** His live clones (dead / removed ones are dropped first). */
	public static List<EchoCloneEntity> clones(ServerPlayer p) {
		List<EchoCloneEntity> l = CLONES.get(p.getUUID());
		if (l == null) {
			return List.of();
		}
		l.removeIf(c -> c.isRemoved());
		return List.copyOf(l);
	}

	/** Splits off one clone if there is room; null if he already has {@link #MAX_CLONES}. */
	public static EchoCloneEntity addClone(ServerPlayer p) {
		List<EchoCloneEntity> l = CLONES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		l.removeIf(c -> c.isRemoved());
		if (l.size() >= MAX_CLONES) {
			return null;
		}
		Set<Integer> used = new LinkedHashSet<>();
		for (EchoCloneEntity c : l) {
			used.add(c.slot());
		}
		int slot = 0;
		while (used.contains(slot)) {
			slot++;
		}
		EchoCloneEntity c = EchoCloneEntity.spawn(p, slot);
		l.add(c);
		level(p).sendParticles(ParticleTypes.POOF, c.getX(), c.getY() + 0.5, c.getZ(), 6, 0.15, 0.3, 0.15, 0.01);
		return c;
	}

	private static void removeClones(ServerPlayer p) {
		List<EchoCloneEntity> l = CLONES.remove(p.getUUID());
		if (l == null) {
			return;
		}
		for (EchoCloneEntity c : l) {
			if (!c.isRemoved()) {
				if (c.level() instanceof ServerLevel sl) {
					sl.sendParticles(ParticleTypes.POOF, c.getX(), c.getY() + 0.5, c.getZ(), 4, 0.1, 0.2, 0.1, 0.01);
				}
				c.discard();
			}
		}
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		removeClones(p);
		CHARGES.remove(p.getUUID());
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : new String[] { BARRIER_UNTIL, LEAP_UNTIL, CHARGE_AT, AIM_TARGET }) {
			n.timers.remove(key(k));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	public static boolean inWater(ServerPlayer p) {
		return p.isInWaterOrBubble();
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(flag(p, F_LEAP) && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		float a = amount;
		if (inWater(p)) {
			a *= WATER_TAKEN;
		}
		if (flag(p, F_BARRIER) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			a *= BARRIER_FACTOR;
		}
		return a;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		if (flag(p, F_LEAP) && now >= timer(p, LEAP_UNTIL)) {
			setFlag(p, F_LEAP, false);
		}
		if (flag(p, F_BARRIER)) {
			if (now >= timer(p, BARRIER_UNTIL)) {
				setFlag(p, F_BARRIER, false);
				setTimer(p, BARRIER_UNTIL, 0);
			} else if (now % 4 == 0) {
				Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
				for (LivingEntity e : around(p, c, 2.6)) {
					shove(e, c, 0.5, 0.15);
				}
				for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(3.0))) {
					if (pr.getOwner() != p && pr.getDeltaMovement().dot(c.subtract(pr.position())) > 0) {
						pr.setDeltaMovement(pr.getDeltaMovement().scale(-0.6));
						pr.setOwner(p);
						pr.hurtMarked = true;
					}
				}
			}
		}
		Long at = CHARGES.get(p.getUUID());
		if (at != null && now >= at) {
			CHARGES.remove(p.getUUID());
			setFlag(p, F_CHARGE, false);
			chorus(p);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CHARGE)) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					blast(p);
				} else {
					scream(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					hum(p);
				} else {
					ring(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					swap(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					assault(p);
				} else {
					barrier(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					merge(p);
				} else {
					split(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					chorusStart(p);
				} else {
					cannon(p);
				}
			}
			default -> {
			}
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	private static float dealt(ServerPlayer p, float dmg) {
		return inWater(p) ? dmg * WATER_DEALT : dmg;
	}

	/** Where the look lands, up to {@code range} (a block or open air). */
	private static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** Creatures whose box comes within {@code radius} of the segment {@code a -> b}. */
	public static List<LivingEntity> line(ServerPlayer p, Vec3 a, Vec3 b, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 ab = b.subtract(a);
		double len2 = Math.max(1.0e-6, ab.lengthSqr());
		Vec3 mid = a.add(ab.scale(0.5));
		for (LivingEntity e : around(p, mid, Math.sqrt(len2) * 0.5 + radius + 1.0)) {
			Vec3 c = e.getBoundingBox().getCenter();
			double t = Math.max(0, Math.min(1, c.subtract(a).dot(ab) / len2));
			if (a.add(ab.scale(t)).distanceTo(c) <= radius + e.getBbWidth() * 0.5) {
				out.add(e);
			}
		}
		return out;
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

	public boolean scream(ServerPlayer p) {
		if (!use(p, SCREAM)) {
			return false;
		}
		anim(p, ANIM_SCREAM);
		Vec3 eye = p.getEyePosition();
		Vec3 end = aimPoint(p, SCREAM_RANGE);
		for (LivingEntity e : line(p, eye, end, 1.1)) {
			if (hurt(p, e, dealt(p, SCREAM_DAMAGE), false)) {
				shove(e, eye, 0.6, 0.15);
			}
		}
		for (EchoCloneEntity c : clones(p)) {
			for (LivingEntity e : line(p, c.mouth(), end, 0.9)) {
				hurt(p, e, dealt(p, CLONE_DAMAGE), false);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_SCREAM, 1f, 1f);
		return true;
	}

	public boolean blast(ServerPlayer p) {
		if (!use(p, BLAST)) {
			return false;
		}
		anim(p, ANIM_BLAST);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : cone(p, eye, look, BLAST_RANGE, BLAST_DEG)) {
			if (hurt(p, e, dealt(p, BLAST_DAMAGE), false)) {
				shove(e, eye, 1.4, 0.35);
			}
		}
		Vec3 aim = eye.add(look.scale(BLAST_RANGE));
		for (EchoCloneEntity c : clones(p)) {
			Vec3 m = c.mouth();
			for (LivingEntity e : cone(p, m, aim.subtract(m).normalize(), BLAST_RANGE, BLAST_DEG * 0.6)) {
				hurt(p, e, dealt(p, CLONE_DAMAGE), false);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_BLAST, 1.3f, 1f);
		return true;
	}

	public boolean ring(ServerPlayer p) {
		if (!use(p, RING)) {
			return false;
		}
		anim(p, ANIM_RING);
		Vec3 c = p.position().add(0, 0.4, 0);
		for (LivingEntity e : around(p, c, RING_RADIUS)) {
			if (hurt(p, e, dealt(p, RING_DAMAGE), false)) {
				shove(e, c, 1.0, 0.3);
			}
		}
		for (EchoCloneEntity cl : clones(p)) {
			for (LivingEntity e : around(p, cl.position(), RING_CLONE_RADIUS)) {
				if (hurt(p, e, dealt(p, RING_CLONE_DAMAGE), false)) {
					shove(e, cl.position(), 0.6, 0.2);
				}
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_RING, 1.2f, 1f);
		return true;
	}

	public boolean hum(ServerPlayer p) {
		if (!use(p, HUM)) {
			return false;
		}
		anim(p, ANIM_HUM);
		for (LivingEntity e : around(p, p.position(), HUM_RADIUS)) {
			hurt(p, e, dealt(p, HUM_DAMAGE), false);
			e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
			if (e instanceof Mob m) {
				AlienStatus.loseTrack(m, p);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_HUM, 1f, 1f);
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
		p.setDeltaMovement(h.x * 0.85, 0.9, h.z * 0.85);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 1f, 1f);
		return true;
	}

	public boolean swap(ServerPlayer p) {
		EchoCloneEntity far = null;
		for (EchoCloneEntity c : clones(p)) {
			if (far == null || c.distanceToSqr(p) > far.distanceToSqr(p)) {
				far = c;
			}
		}
		if (far == null) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_clones");
			return false;
		}
		if (!use(p, SWAP)) {
			return false;
		}
		anim(p, ANIM_SWAP);
		Vec3 me = p.position();
		Vec3 it = far.position();
		p.teleportTo(it.x, it.y, it.z);
		far.setPos(me.x, me.y, me.z);
		p.fallDistance = 0f;
		OmnitrixSfx.play(level(p), it, SND_SPLIT, 0.8f, 1.3f);
		return true;
	}

	public boolean barrier(ServerPlayer p) {
		if (!use(p, BARRIER)) {
			return false;
		}
		anim(p, ANIM_BARRIER);
		setTimer(p, BARRIER_UNTIL, p.level().getGameTime() + BARRIER_TICKS);
		setFlag(p, F_BARRIER, true);
		OmnitrixSfx.play(level(p), p.position(), SND_BARRIER, 1f, 1f);
		return true;
	}

	public boolean assault(ServerPlayer p) {
		List<EchoCloneEntity> cl = clones(p);
		if (cl.isEmpty()) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_clones");
			return false;
		}
		LivingEntity t = lookTarget(p, ASSAULT_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_target");
			return false;
		}
		if (!use(p, ASSAULT)) {
			return false;
		}
		anim(p, ANIM_ASSAULT);
		setTimer(p, AIM_TARGET, t.getId() + 1L);
		for (EchoCloneEntity c : cl) {
			Vec3 to = t.position().subtract(c.position());
			Vec3 stop = t.position().subtract(to.normalize().scale(1.0 + t.getBbWidth() * 0.5));
			c.dashTo(stop, 16);
			if (hurt(p, t, dealt(p, ASSAULT_DAMAGE), false)) {
				shove(t, c.position(), 0.4, 0.1);
			}
			t.invulnerableTime = 0;
		}
		OmnitrixSfx.play(level(p), t.position(), SND_SCREAM, 1.2f, 1.2f);
		return true;
	}

	public boolean split(ServerPlayer p) {
		if (clones(p).size() >= MAX_CLONES) {
			say(p, "message.projecthero.omnitrix.echo_echo.max_clones");
			return false;
		}
		if (!use(p, SPLIT)) {
			return false;
		}
		anim(p, ANIM_SPLIT);
		addClone(p);
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 1f, 1f);
		return true;
	}

	public boolean merge(ServerPlayer p) {
		int n = clones(p).size();
		if (n == 0) {
			say(p, "message.projecthero.omnitrix.echo_echo.no_clones");
			return false;
		}
		if (!use(p, MERGE)) {
			return false;
		}
		anim(p, ANIM_MERGE);
		removeClones(p);
		p.heal(MERGE_HEAL * n);
		OmnitrixSfx.play(level(p), p.position(), SND_MERGE, 1f, 1f);
		return true;
	}

	public boolean cannon(ServerPlayer p) {
		if (!use(p, CANNON)) {
			return false;
		}
		anim(p, ANIM_CANNON);
		Vec3 eye = p.getEyePosition();
		Vec3 end = aimPoint(p, CANNON_RANGE);
		for (LivingEntity e : line(p, eye, end, 0.9)) {
			if (hurt(p, e, dealt(p, CANNON_DAMAGE), false)) {
				shove(e, eye, 1.0, 0.2);
			}
		}
		for (EchoCloneEntity c : clones(p)) {
			for (LivingEntity e : line(p, c.mouth(), end, 0.7)) {
				hurt(p, e, dealt(p, CLONE_DAMAGE), false);
			}
		}
		OmnitrixSfx.play(level(p), p.position(), SND_BLAST, 1.4f, 0.8f);
		return true;
	}

	public boolean chorusStart(ServerPlayer p) {
		if (!use(p, CHORUS)) {
			return false;
		}
		while (addClone(p) != null) {
			// a full chorus
		}
		anim(p, ANIM_CHARGE);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHARGE_TICKS, 9, false, false, false));
		CHARGES.put(p.getUUID(), p.level().getGameTime() + CHARGE_TICKS);
		setTimer(p, CHARGE_AT, p.level().getGameTime() + CHARGE_TICKS);
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 1f, 1f);
		return true;
	}

	/** The Ultimate Chorus going off (tests call it straight). */
	public void chorus(ServerPlayer p) {
		anim(p, ANIM_CHORUS);
		setTimer(p, CHARGE_AT, 0);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 0.5, 0);
		for (LivingEntity e : around(p, c, CHORUS_RADIUS)) {
			double d = Math.min(1.0, e.position().distanceTo(p.position()) / CHORUS_RADIUS);
			float dmg = (float) (CHORUS_DAMAGE + (CHORUS_EDGE - CHORUS_DAMAGE) * d);
			if (hurt(p, e, dealt(p, dmg), false)) {
				shove(e, c, 1.6, 0.5);
			}
		}
		l.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		com.projecthero.mod.quirk.fx.QuirkCamera.shakeAround(l, c, 18, 0.45f, 10);
		OmnitrixSfx.play(l, c, SND_CHORUS, 1.6f, 1f);
	}

	/** Static state held for {@code p} (tests: nothing is left after turning back). */
	public static boolean holds(ServerPlayer p) {
		return CLONES.containsKey(p.getUUID()) || CHARGES.containsKey(p.getUUID());
	}
}
