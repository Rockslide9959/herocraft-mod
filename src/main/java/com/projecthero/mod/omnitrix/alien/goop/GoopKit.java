package com.projecthero.mod.omnitrix.alien.goop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (the Recalibrated Omnitrix, basic version -- user: "Just make them basic versions of the aliens for now please that we
 * can revamp later"): Goop, the Polymorph -- a shapeshifting blob of acidic green slime held together by the anti-gravity
 * projector that floats over him.
 *
 * <pre>
 *   R        Acid Spit        a glob of acid: 7                                  Shift+R  Acid Glob      a lobbed glob: 12 splash (r 2.5) + an acid
 *                                                                                                         puddle 8 s (2 every 0.5 s, slowed)
 *   G        Slime Whip       a stretched arm lash (5, cone): 13, pulled in      Shift+G  Engulf         swallows a creature (4.5) for 3 s: 4 x 4
 *   X        Slime Bounce     a springy leap                                     Shift+X  Squeeze        5 s squeezed small (x0.4: through 1-block
 *                                                                                                         gaps), +20% speed; Shift+X again ends it
 *   Z        Slime Slam       a splash round him (r 4): 17 + slowed              Shift+Z  Acid Spray     a cone (6): 12 + weakened, slowed
 *   V        Reform           pulls himself together: +6 HP, clears poisons      Shift+V  Glide          flattens into a parachute (6 s)
 *   C        Acid Flood       ULTIMATE: a lake of acid round him (r 6, 6 s):      Shift+C  Goo Cannon     a huge glob: blast r 3, 18
 *                             5 every 0.5 s (60), slowed
 * </pre>
 *
 * Passives: Polymorph body -- half damage from physical blows ({@code GhostfreakKit.physical}), no fall damage, never suffocates
 * in walls. Weaknesses: the anti-gravity projector -- a hit of 8 or more knocks it off: he slumps into a puddle (x0.45 size,
 * -70% speed, no moves) until he flows back over it (it floats back by itself after 20 s); heat dries him out (fire x1.5).
 *
 * <p>Carriers ({@link AlienProjectileEntity}, held): {@code goop_puddle} (radius = size) and {@code goop_projector} (the knocked-off
 * disc, owner = Goop).
 */
public final class GoopKit extends AlienKit {
	public static final String ID = Aliens.GOOP.id();

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SPIT = OmnitrixSfx.cue("goop_spit")
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.4f, 1.3f)
			.layer(SoundEvents.LLAMA_SPIT, 0.3f, 1.2f)
			.gap(2);
	public static final OmnitrixSfx.Cue SPLAT = OmnitrixSfx.cue("goop_splat")
			.layer(SoundEvents.SLIME_BLOCK_FALL, 0.45f, 0.9f)
			.layer(SoundEvents.BUCKET_EMPTY, 0.25f, 1.4f)
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.12f, 1.6f, 2)
			.gap(3);
	public static final OmnitrixSfx.Cue SIZZLE = OmnitrixSfx.cue("goop_sizzle")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.12f, 1.7f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.15f, 0.8f)
			.gap(10);
	public static final OmnitrixSfx.Cue WHIP = OmnitrixSfx.cue("goop_whip")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.3f, 0.8f)
			.layer(SoundEvents.SLIME_SQUISH, 0.4f, 1.0f);
	public static final OmnitrixSfx.Cue GULP = OmnitrixSfx.cue("goop_gulp")
			.layer(SoundEvents.SLIME_BLOCK_PLACE, 0.5f, 0.7f)
			.layer(SoundEvents.HONEY_DRINK, 0.4f, 0.6f, 2);
	public static final OmnitrixSfx.Cue BOING = OmnitrixSfx.cue("goop_boing")
			.layer(SoundEvents.SLIME_JUMP, 0.45f, 1.0f)
			.layer(SoundEvents.SLIME_BLOCK_STEP, 0.3f, 1.2f);
	public static final OmnitrixSfx.Cue SQUISH = OmnitrixSfx.cue("goop_squish")
			.layer(SoundEvents.HONEY_BLOCK_SLIDE, 0.45f, 1.1f)
			.layer(SoundEvents.SLIME_SQUISH, 0.3f, 1.4f);
	public static final OmnitrixSfx.Cue REFORM = OmnitrixSfx.cue("goop_reform")
			.layer(SoundEvents.SLIME_BLOCK_PLACE, 0.4f, 1.2f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.15f, 1.8f, 3);
	public static final OmnitrixSfx.Cue KNOCKED = OmnitrixSfx.cue("goop_knocked")
			.layer(SoundEvents.ANVIL_LAND, 0.2f, 1.8f)
			.layer(SoundEvents.SLIME_DEATH, 0.4f, 0.8f)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 1.6f, 2);
	public static final OmnitrixSfx.Cue PICKUP = OmnitrixSfx.cue("goop_pickup")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.3f, 1.8f)
			.layer(SoundEvents.SLIME_BLOCK_PLACE, 0.4f, 1.1f);
	public static final OmnitrixSfx.Cue CANNON = OmnitrixSfx.cue("goop_cannon")
			.layer(SoundEvents.SLIME_BLOCK_BREAK, 0.5f, 0.6f)
			.layer(SoundEvents.LLAMA_SPIT, 0.4f, 0.6f)
			.layer(SoundEvents.BUCKET_EMPTY, 0.3f, 0.8f, 1);

	// ---------------------------------------------------------------- poses, flags, moves
	public static final int ANIM_SPIT = 1, ANIM_GLOB = 2, ANIM_WHIP = 3, ANIM_ENGULF = 4, ANIM_BOUNCE = 5, ANIM_SQUEEZE = 6, ANIM_SLAM = 7,
			ANIM_SPRAY = 8, ANIM_REFORM = 9, ANIM_GLIDE = 10, ANIM_FLOOD = 11, ANIM_CANNON = 12, ANIM_KNOCKED = 13;

	public static final int F_SQUEEZE = 1;
	/** The projector is knocked off: he is a puddle. */
	public static final int F_OFF = 2;
	public static final int F_GLIDE = 4;
	public static final int F_ENGULF = 8;

	public static final String ACID_SPIT = "acid_spit", ACID_GLOB = "acid_glob", SLIME_WHIP = "slime_whip", ENGULF = "engulf", BOUNCE = "slime_bounce",
			SQUEEZE = "squeeze", SLIME_SLAM = "slime_slam", ACID_SPRAY = "acid_spray", REFORM_MOVE = "reform", GLIDE = "glide",
			ACID_FLOOD = "acid_flood", GOO_CANNON = "goo_cannon";

	public static final String SQUEEZE_END = "squeeze_end", GLIDE_END = "glide_end";

	public static final float SPIT_DAMAGE = 7f, GLOB_DAMAGE = 12f, PUDDLE_DAMAGE = 2f, WHIP_DAMAGE = 13f, ENGULF_DAMAGE = 4f, SLAM_DAMAGE = 17f,
			SPRAY_DAMAGE = 12f, FLOOD_DAMAGE = 5f, CANNON_DAMAGE = 18f, REFORM_HEAL = 6f;
	public static final double WHIP_RANGE = 5, ENGULF_RANGE = 4.5, SLAM_RADIUS = 4, SPRAY_RANGE = 6, FLOOD_RADIUS = 6, GLOB_RADIUS = 2.5;
	public static final int PUDDLE_TICKS = 160, FLOOD_TICKS = 120, PULSE = 10, ENGULF_TICKS = 60, ENGULF_PULSE = 15, SQUEEZE_TICKS = 100,
			GLIDE_TICKS = 120, PROJECTOR_RETURN = 400;
	/** Physical blows are halved (a polymorph's body). */
	public static final float PHYSICAL_TAKEN = 0.5f;
	public static final float FIRE_TAKEN = 1.5f;
	/** A hit this big (before his resistances) knocks the projector off. */
	public static final float KNOCK_OFF = 8f;
	public static final double SQUEEZE_SCALE = 0.4, PUDDLE_SCALE = 0.45, SQUEEZE_SPEED = 0.2, PUDDLE_SPEED = -0.7;

	private static final ResourceLocation SQUEEZE_SCALE_ID = PowerToggles.id("omnitrix_goop_squeeze_scale");
	private static final ResourceLocation SQUEEZE_SPEED_ID = PowerToggles.id("omnitrix_goop_squeeze_speed");
	private static final ResourceLocation PUDDLE_SCALE_ID = PowerToggles.id("omnitrix_goop_puddle_scale");
	private static final ResourceLocation PUDDLE_SPEED_ID = PowerToggles.id("omnitrix_goop_puddle_speed");
	private static final ItemParticleOption SLIME = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL));

	// ---------------------------------------------------------------- per-player state
	private static final Map<UUID, List<Puddle>> PUDDLES = new ConcurrentHashMap<>();
	private static final Map<UUID, Engulfed> ENGULFED = new ConcurrentHashMap<>();
	private static final Map<UUID, Projector> PROJECTORS = new ConcurrentHashMap<>();

	private static final class Puddle {
		final ResourceKey<Level> dim;
		final Vec3 at;
		final double radius;
		final float damage;
		final long until;
		long next;
		final AlienProjectileEntity look;

		Puddle(ResourceKey<Level> dim, Vec3 at, double radius, float damage, long until, long next, AlienProjectileEntity look) {
			this.dim = dim;
			this.at = at;
			this.radius = radius;
			this.damage = damage;
			this.until = until;
			this.next = next;
			this.look = look;
		}
	}

	private static final class Engulfed {
		final LivingEntity target;
		final long until;
		long next;

		Engulfed(LivingEntity target, long until, long next) {
			this.target = target;
			this.until = until;
			this.next = next;
		}
	}

	private record Projector(ResourceKey<Level> dim, Vec3 at, long since, AlienProjectileEntity look) {
	}

	public static void register() {
		Aliens.register(new GoopKit());
		AlienProjectileEntity.onHit("goop_glob", (proj, hit) -> {
			if (proj.getOwner() instanceof ServerPlayer owner && Aliens.kit(ID) instanceof GoopKit kit) {
				Vec3 at = ground(owner, hit.getLocation());
				kit.puddle(owner, at, GLOB_RADIUS, PUDDLE_DAMAGE, PUDDLE_TICKS);
				OmnitrixSfx.play(level(owner), at, SPLAT, 1f, 1f);
			}
		});
		AlienProjectileEntity.onHit("goop_spit", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel level) {
				Vec3 at = hit.getLocation();
				level.sendParticles(SLIME, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.08);
				OmnitrixSfx.play(level, at, SIZZLE, 0.8f, 1f);
			}
		});
		AlienProjectileEntity.onHit("goop_cannon", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel level) {
				Vec3 at = hit.getLocation();
				level.sendParticles(SLIME, at.x, at.y, at.z, 30, 1.2, 0.5, 1.2, 0.12);
				OmnitrixSfx.play(level, at, SPLAT, 1.3f, 0.8f);
			}
		});
	}

	public static void clearSessionState() {
		for (List<Puddle> l : PUDDLES.values()) {
			l.forEach(pd -> pd.look.discard());
		}
		PUDDLES.clear();
		ENGULFED.clear();
		for (Projector pr : PROJECTORS.values()) {
			pr.look().discard();
		}
		PROJECTORS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(26).armor(2, 0).knockbackResist(0.3f).attack(2).safeFall(6).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(ACID_SPIT, AbilitySlot.SLOT_1, 20), AlienMove.shift(ACID_GLOB, AbilitySlot.SLOT_1, 200),
				AlienMove.tap(SLIME_WHIP, AbilitySlot.SLOT_2, 80), AlienMove.shift(ENGULF, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(BOUNCE, AbilitySlot.SLOT_3, 30), AlienMove.shift(SQUEEZE, AbilitySlot.SLOT_3, 160),
				AlienMove.tap(SLIME_SLAM, AbilitySlot.SLOT_4, 200), AlienMove.shift(ACID_SPRAY, AbilitySlot.SLOT_4, 140),
				AlienMove.tap(REFORM_MOVE, AbilitySlot.SLOT_5, 400), AlienMove.shift(GLIDE, AbilitySlot.SLOT_5, 200),
				AlienMove.tap(ACID_FLOOD, AbilitySlot.SLOT_6, 900), AlienMove.shift(GOO_CANNON, AbilitySlot.SLOT_6, 260));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		List<Puddle> l = PUDDLES.remove(id);
		if (l != null) {
			l.forEach(pd -> pd.look.discard());
		}
		Engulfed en = ENGULFED.remove(id);
		if (en != null) {
			release(p, en);
		}
		Projector pr = PROJECTORS.remove(id);
		if (pr != null) {
			pr.look().discard();
		}
		PowerToggles.clearModifier(p, Attributes.SCALE, SQUEEZE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SQUEEZE_SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.SCALE, PUDDLE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PUDDLE_SPEED_ID);
		if (flag(p, F_GLIDE)) {
			p.removeEffect(MobEffects.SLOW_FALLING);
		}
		OmnitrixState s = Omnitrix.state(p);
		if (s.kitFlags != 0) {
			OmnitrixState n = s.copy();
			n.kitFlags = 0;
			Omnitrix.save(p, n);
		}
	}

	/** Anything of {@code p}'s left behind (gametests). */
	public static int leftovers(ServerPlayer p) {
		UUID id = p.getUUID();
		return (PUDDLES.containsKey(id) ? 1 : 0) + (ENGULFED.containsKey(id) ? 1 : 0) + (PROJECTORS.containsKey(id) ? 1 : 0);
	}

	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!SQUEEZE.equals(moveId) && flag(p, F_SQUEEZE)) {
			endSqueeze(p); // any other move pops him back to full size
		}
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel level = level(p);
		UUID id = p.getUUID();
		// the projector on the ground: flow back over it (or it floats home by itself)
		Projector pr = PROJECTORS.get(id);
		if (pr != null) {
			if (pr.dim() != level.dimension() || now - pr.since() >= PROJECTOR_RETURN || p.position().distanceTo(pr.at()) < 1.5) {
				pickUp(p);
			} else if (now % 20 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
			}
		}
		if (flag(p, F_SQUEEZE) && now >= timer(p, SQUEEZE_END)) {
			endSqueeze(p);
		}
		if (flag(p, F_GLIDE) && (now >= timer(p, GLIDE_END) || (p.onGround() && now - (timer(p, GLIDE_END) - GLIDE_TICKS) > 6))) {
			setFlag(p, F_GLIDE, false);
			p.removeEffect(MobEffects.SLOW_FALLING);
		}
		// the engulfed creature, held inside him
		Engulfed en = ENGULFED.get(id);
		if (en != null) {
			if (now >= en.until || !en.target.isAlive()) {
				ENGULFED.remove(id);
				release(p, en);
			} else {
				Vec3 c = p.position().add(p.getLookAngle().multiply(1, 0, 1).scale(0.2));
				en.target.teleportTo(c.x, p.getY() + 0.1, c.z);
				en.target.setDeltaMovement(Vec3.ZERO);
				if (now >= en.next) {
					en.next = now + ENGULF_PULSE;
					en.target.invulnerableTime = 0;
					hurt(p, en.target, ENGULF_DAMAGE, false);
					OmnitrixSfx.play(level, c, SIZZLE, 0.8f, 0.9f);
				}
			}
		}
		// acid puddles
		List<Puddle> puddles = PUDDLES.get(id);
		if (puddles != null) {
			puddles.removeIf(pd -> {
				if (now >= pd.until || pd.dim != level.dimension()) {
					pd.look.discard();
					return true;
				}
				if (now >= pd.next) {
					pd.next = now + PULSE;
					puddlePulse(p, pd);
				}
				return false;
			});
			if (puddles.isEmpty()) {
				PUDDLES.remove(id);
			}
		}
		if (now % 4 == 0) {
			level.sendParticles(SLIME, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.25, 0.05, 0.25, 0.0);
		}
	}

	/** One acid pulse on everything standing in {@code pd}. Returns how many it burnt. */
	private int puddlePulse(ServerPlayer p, Puddle pd) {
		int n = 0;
		for (LivingEntity e : around(p, pd.at, pd.radius)) {
			if (Math.abs(e.getY() - pd.at.y) < 1.2) {
				e.invulnerableTime = 0;
				if (hurt(p, e, pd.damage, false)) {
					n++;
				}
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, true), p);
			}
		}
		if (n > 0) {
			OmnitrixSfx.play(level(p), pd.at, SIZZLE, 0.7f, 1f);
		}
		return n;
	}

	/** Every acid puddle of {@code p} pulses now (gametests: no waiting on the tick). Returns how many creatures it burnt. */
	public int pulsePuddlesNow(ServerPlayer p) {
		int n = 0;
		for (Puddle pd : PUDDLES.getOrDefault(p.getUUID(), List.of())) {
			n += puddlePulse(p, pd);
		}
		return n;
	}

	// ---------------------------------------------------------------- the projector

	/** Whether the projector is knocked off. Client-safe. */
	public boolean projectorOff(Player p) {
		return flag(p, F_OFF);
	}

	/** Where the knocked-off projector lies, or null. */
	@Nullable
	public static Vec3 projectorAt(ServerPlayer p) {
		Projector pr = PROJECTORS.get(p.getUUID());
		return pr == null ? null : pr.at();
	}

	/** A big hit knocks the projector off: it lands a few blocks away and he slumps into a puddle until he gets back to it. */
	public void knockOff(ServerPlayer p, @Nullable Entity from) {
		if (flag(p, F_OFF)) {
			return;
		}
		endSqueeze(p);
		Vec3 away;
		if (from != null && from != p) {
			away = p.position().subtract(from.position()).multiply(1, 0, 1);
		} else {
			double a = p.getRandom().nextDouble() * Math.PI * 2;
			away = new Vec3(Math.cos(a), 0, Math.sin(a));
		}
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
		Vec3 spot = p.position();
		for (double d = 0.5; d <= 3.0; d += 0.5) {
			Vec3 at = p.position().add(away.scale(d)).add(0, 0.5, 0);
			if (!p.level().getBlockState(net.minecraft.core.BlockPos.containing(at)).isAir()) {
				break;
			}
			spot = at;
		}
		spot = ground(p, spot);
		AlienProjectileEntity look = AlienProjectileEntity.of(p, "goop_projector");
		look.moveTo(spot.x, spot.y, spot.z, p.getYRot(), 0f);
		look.colour(0).size(0.6f).held(true).speed(0).life(PROJECTOR_RETURN + 40).damage(0f).spawn();
		PROJECTORS.put(p.getUUID(), new Projector(p.level().dimension(), spot, p.level().getGameTime(), look));
		setFlag(p, F_OFF, true);
		anim(p, ANIM_KNOCKED);
		PowerToggles.modifier(p, Attributes.SCALE, PUDDLE_SCALE_ID, PUDDLE_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, PUDDLE_SPEED_ID, PUDDLE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Engulfed en = ENGULFED.remove(p.getUUID());
		if (en != null) {
			release(p, en);
		}
		level(p).sendParticles(SLIME, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.5, 0.4, 0.5, 0.1);
		OmnitrixSfx.playOn(p, KNOCKED, 1f, 1f, SoundSource.PLAYERS);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
	}

	/** He is back under his projector. */
	public void pickUp(ServerPlayer p) {
		Projector pr = PROJECTORS.remove(p.getUUID());
		if (pr != null) {
			pr.look().discard();
		}
		setFlag(p, F_OFF, false);
		PowerToggles.clearModifier(p, Attributes.SCALE, PUDDLE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, PUDDLE_SPEED_ID);
		anim(p, ANIM_REFORM);
		OmnitrixSfx.playOn(p, PICKUP, 1f, 1f, SoundSource.PLAYERS);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_back").withStyle(ChatFormatting.GREEN), true);
	}

	private static Vec3 ground(ServerPlayer p, Vec3 at) {
		HitResult down = p.level().clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.BLOCK ? down.getLocation().add(0, 0.02, 0) : at;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)) {
			return false; // a blob lands soft and fits anywhere
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (amount >= KNOCK_OFF && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypes.STARVE)
				&& !source.is(DamageTypes.DROWN)) {
			knockOff(p, source.getEntity());
		}
		float a = amount;
		if (GhostfreakKit.physical(source)) {
			a *= PHYSICAL_TAKEN;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			a *= FIRE_TAKEN;
		}
		return a;
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_OFF)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					acidGlob(p);
				} else {
					acidSpit(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					engulf(p);
				} else {
					slimeWhip(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					squeeze(p);
				} else {
					slimeBounce(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					acidSpray(p);
				} else {
					slimeSlam(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					glide(p);
				} else {
					reform(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					gooCannon(p);
				} else {
					acidFlood(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 && flag(p, F_SQUEEZE);
	}

	/** Refuses a move while the projector is off (the moves are public for the gametests). */
	private boolean ok(ServerPlayer p) {
		if (flag(p, F_OFF)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.projector_off").withStyle(ChatFormatting.RED), true);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- moves

	public boolean acidSpit(ServerPlayer p) {
		if (!ok(p) || !use(p, ACID_SPIT)) {
			return false;
		}
		anim(p, ANIM_SPIT);
		AlienProjectileEntity.of(p, "goop_spit").colour(0x7CE03A).size(0.3f).damage(SPIT_DAMAGE).speed(1.9).gravity(0.01).life(40).spawn();
		OmnitrixSfx.playOn(p, SPIT, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	public boolean acidGlob(ServerPlayer p) {
		if (!ok(p) || !use(p, ACID_GLOB)) {
			return false;
		}
		anim(p, ANIM_GLOB);
		AlienProjectileEntity.of(p, "goop_glob").colour(0x7CE03A).size(0.55f).damage(0f).blast((float) GLOB_RADIUS, GLOB_DAMAGE).speed(1.1).gravity(0.05)
				.life(80).spawn();
		OmnitrixSfx.playOn(p, SPIT, 1f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	/** A puddle of acid at {@code at}. */
	public void puddle(ServerPlayer p, Vec3 at, double radius, float damage, int ticks) {
		long now = p.level().getGameTime();
		AlienProjectileEntity look = AlienProjectileEntity.of(p, "goop_puddle");
		look.moveTo(at.x, at.y, at.z, 0f, 0f);
		look.colour(ticks).size((float) radius).held(true).speed(0).life(ticks + 5).damage(0f).spawn();
		List<Puddle> l = PUDDLES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		while (l.size() >= 4) {
			l.remove(0).look.discard();
		}
		l.add(new Puddle(p.level().dimension(), at, radius, damage, now + ticks, now + PULSE, look));
	}

	public boolean slimeWhip(ServerPlayer p) {
		if (!ok(p) || !use(p, SLIME_WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : around(p, eye.add(look.scale(WHIP_RANGE * 0.5)), WHIP_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= WHIP_RANGE + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.75) {
				hurt(p, e, WHIP_DAMAGE, false);
				if (!AlienStatus.isBoss(e)) {
					Vec3 pull = p.position().subtract(e.position()).normalize().scale(0.6);
					e.push(pull.x, 0.2, pull.z);
					e.hurtMarked = true;
				}
			}
		}
		OmnitrixSfx.playOn(p, WHIP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	public boolean engulf(ServerPlayer p) {
		if (!ok(p)) {
			return false;
		}
		LivingEntity t = lookTarget(p, ENGULF_RANGE);
		if (t == null || AlienStatus.isBoss(t) || t.getBbHeight() > 2.3f || t.getBbWidth() > 1.5f) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.no_engulf").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, ENGULF)) {
			return false;
		}
		anim(p, ANIM_ENGULF);
		long now = p.level().getGameTime();
		ENGULFED.put(p.getUUID(), new Engulfed(t, now + ENGULF_TICKS, now + 5));
		setFlag(p, F_ENGULF, true);
		OmnitrixSfx.playOn(p, GULP, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Whether {@code p} holds a creature inside (gametests). */
	public static boolean engulfing(ServerPlayer p) {
		return ENGULFED.containsKey(p.getUUID());
	}

	private void release(ServerPlayer p, Engulfed en) {
		setFlag(p, F_ENGULF, false);
		if (en.target.isAlive()) {
			Vec3 f = p.getLookAngle().multiply(1, 0, 1);
			f = f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
			en.target.teleportTo(p.getX() + f.x * 1.2, p.getY() + 0.2, p.getZ() + f.z * 1.2);
			shove(en.target, p.position(), 0.8, 0.3);
		}
		if (p.level() instanceof ServerLevel level) {
			level.sendParticles(SLIME, p.getX(), p.getY() + 1, p.getZ(), 14, 0.4, 0.4, 0.4, 0.1);
			OmnitrixSfx.play(level, p.position(), SPLAT, 0.8f, 1.2f);
		}
	}

	public boolean slimeBounce(ServerPlayer p) {
		if (!ok(p) || !use(p, BOUNCE)) {
			return false;
		}
		anim(p, ANIM_BOUNCE);
		Vec3 f = p.getLookAngle().multiply(1, 0, 1);
		f = f.lengthSqr() < 1.0e-4 ? Vec3.ZERO : f.normalize();
		p.setDeltaMovement(f.scale(1.0).add(0, 0.85, 0));
		p.hurtMarked = true;
		p.fallDistance = 0f;
		OmnitrixSfx.playOn(p, BOING, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+X: squeezed small (x0.4 -- 0.72 tall, through any 1-block gap) for 5 s; Shift+X again pops back. */
	public boolean squeeze(ServerPlayer p) {
		if (!ok(p)) {
			return false;
		}
		if (flag(p, F_SQUEEZE)) {
			endSqueeze(p);
			return true;
		}
		if (!use(p, SQUEEZE)) {
			return false;
		}
		anim(p, ANIM_SQUEEZE);
		PowerToggles.modifier(p, Attributes.SCALE, SQUEEZE_SCALE_ID, SQUEEZE_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SQUEEZE_SPEED_ID, SQUEEZE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		setFlag(p, F_SQUEEZE, true);
		setTimer(p, SQUEEZE_END, p.level().getGameTime() + SQUEEZE_TICKS);
		OmnitrixSfx.playOn(p, SQUISH, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	private void endSqueeze(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.SCALE, SQUEEZE_SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SQUEEZE_SPEED_ID);
		if (flag(p, F_SQUEEZE)) {
			setFlag(p, F_SQUEEZE, false);
			setTimer(p, SQUEEZE_END, 0);
			OmnitrixSfx.playOn(p, SQUISH, 0.8f, 0.8f, SoundSource.PLAYERS);
		}
	}

	public boolean slimeSlam(ServerPlayer p) {
		if (!ok(p) || !use(p, SLIME_SLAM)) {
			return false;
		}
		anim(p, ANIM_SLAM);
		for (LivingEntity e : around(p, p.position().add(0, 0.5, 0), SLAM_RADIUS)) {
			hurt(p, e, SLAM_DAMAGE, false);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
			shove(e, p.position(), 0.7, 0.3);
		}
		level(p).sendParticles(SLIME, p.getX(), p.getY() + 0.3, p.getZ(), 40, 2.0, 0.3, 2.0, 0.15);
		OmnitrixSfx.playOn(p, SPLAT, 1.2f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	public boolean acidSpray(ServerPlayer p) {
		if (!ok(p) || !use(p, ACID_SPRAY)) {
			return false;
		}
		anim(p, ANIM_SPRAY);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : around(p, eye.add(look.scale(SPRAY_RANGE * 0.5)), SPRAY_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= SPRAY_RANGE + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.7) {
				hurt(p, e, SPRAY_DAMAGE, false);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), p);
			}
		}
		for (int i = 0; i < 12; i++) {
			Vec3 d = look.scale(0.6 + i * 0.4);
			level(p).sendParticles(SLIME, eye.x + d.x, eye.y + d.y - 0.2, eye.z + d.z, 2, i * 0.05, i * 0.05, i * 0.05, 0.02);
		}
		OmnitrixSfx.playOn(p, SPIT, 1.2f, 0.7f, SoundSource.PLAYERS);
		return true;
	}

	/** V: pulls himself back together -- +6 health and the poisons out. Nothing to mend: no cooldown. */
	public boolean reform(ServerPlayer p) {
		if (!ok(p)) {
			return false;
		}
		boolean hurtNow = p.getHealth() < p.getMaxHealth();
		boolean bad = p.getActiveEffects().stream().anyMatch(e -> !e.getEffect().value().isBeneficial());
		if (!hurtNow && !bad) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.goop.whole").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, REFORM_MOVE)) {
			return false;
		}
		anim(p, ANIM_REFORM);
		p.heal(REFORM_HEAL);
		List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> drop = new ArrayList<>();
		for (MobEffectInstance e : p.getActiveEffects()) {
			if (!e.getEffect().value().isBeneficial()) {
				drop.add(e.getEffect());
			}
		}
		drop.forEach(p::removeEffect);
		OmnitrixSfx.playOn(p, REFORM, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+V: flattened into a parachute -- 6 s of slow falling. */
	public boolean glide(ServerPlayer p) {
		if (!ok(p) || !use(p, GLIDE)) {
			return false;
		}
		anim(p, ANIM_GLIDE);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, GLIDE_TICKS, 0, false, false, true));
		setFlag(p, F_GLIDE, true);
		setTimer(p, GLIDE_END, p.level().getGameTime() + GLIDE_TICKS);
		OmnitrixSfx.playOn(p, SQUISH, 1f, 0.7f, SoundSource.PLAYERS);
		return true;
	}

	/** C (ULTIMATE): a lake of acid round him -- 5 every half second to everything in it for 6 s. */
	public boolean acidFlood(ServerPlayer p) {
		if (!ok(p) || !use(p, ACID_FLOOD)) {
			return false;
		}
		anim(p, ANIM_FLOOD);
		puddle(p, ground(p, p.position()), FLOOD_RADIUS, FLOOD_DAMAGE, FLOOD_TICKS);
		level(p).sendParticles(SLIME, p.getX(), p.getY() + 0.3, p.getZ(), 60, 3.0, 0.3, 3.0, 0.2);
		OmnitrixSfx.playOn(p, SPLAT, 1.5f, 0.7f, SoundSource.PLAYERS);
		return true;
	}

	public boolean gooCannon(ServerPlayer p) {
		if (!ok(p) || !use(p, GOO_CANNON)) {
			return false;
		}
		anim(p, ANIM_CANNON);
		AlienProjectileEntity.of(p, "goop_cannon").colour(0x7CE03A).size(0.9f).damage(0f).blast(3f, CANNON_DAMAGE).speed(1.4).gravity(0.02).life(60).spawn();
		OmnitrixSfx.playOn(p, CANNON, 1f, 1f, SoundSource.PLAYERS);
		return true;
	}
}
