package com.projecthero.mod.omnitrix.alien.wildvine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Wildvine, the Florauna -- a green plant body with a pink flower-bulb head (a yellow centre with two eyes in it) and
 * long vine limbs. He grapples, binds and seeds the battlefield, heals in the sun, and can stand still among the leaves until
 * mobs forget him.
 *
 * <pre>
 *   R        Seed Bomb      a lobbed seed that bursts (7, no block damage)    Shift+R  Thorn Whip      a wide lash in front (6, knockback)
 *   G        Vine Bind      roots the creature you look at for 4 s (18 r)    Shift+G  Pollen Burst    sneezing pollen around you (nausea, slow, weak)
 *   X        Vine Grapple   whips a vine to a block and reels you in (28 r)   Shift+X  Stretch Climb   stretch up the wall ahead (12 blocks)
 *   Z        Root Wall      a wall of roots for 8 s: pushes mobs, stops shots Shift+Z  Regrowth        10 s healing for you and allies (fast in sun)
 *   C        Overgrowth     an 8 s field (7 r): vines entangle every mob     Shift+C  Root Eruption   roots burst up in a line ahead (7, launch)
 *   Attack   vine lash (+1.5 reach)      Use on a block (empty hand)   bonemeal touch
 *   Sneak    hold still: hide as a bush (mobs lose track of you)
 * </pre>
 */
public final class WildvineKit extends AlienKit {
	public static final String ID = "wildvine";
	public static final int COLOUR = 0x4A9A2A;
	public static final int PINK = 0xF070B8;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A vine lash: a leafy creak and a whip. */
	public static final OmnitrixSfx.Cue VINE = OmnitrixSfx.cue("wildvine_vine")
			.layer(SoundEvents.CAVE_VINES_PLACE, 0.45f, 0.8f)
			.layer(SoundEvents.FISHING_BOBBER_THROW, 0.3f, 0.6f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 0.8f);
	/** A seed thrown: a soft rustle and a pop. */
	public static final OmnitrixSfx.Cue SEED = OmnitrixSfx.cue("wildvine_seed")
			.layer(SoundEvents.CROP_PLANTED, 0.4f, 1.4f)
			.layer(SoundEvents.CHICKEN_EGG, 0.35f, 1.1f, 1);
	/** A seed pod bursting: a leafy pop. */
	public static final OmnitrixSfx.Cue BURST = OmnitrixSfx.cue("wildvine_burst")
			.layer(SoundEvents.AZALEA_LEAVES_BREAK, 0.5f, 0.8f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.18f, 1.7f)
			.layer(SoundEvents.CROP_PLANTED, 0.3f, 1.0f);
	/** Roots erupting: cracking wood and torn earth. */
	public static final OmnitrixSfx.Cue ROOTS = OmnitrixSfx.cue("wildvine_roots")
			.layer(SoundEvents.ROOTS_BREAK, 0.5f, 0.7f)
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.45f, 0.6f)
			.layer(SoundEvents.BAMBOO_BREAK, 0.3f, 0.6f, 1);
	/** A bloom: soft leafy unfurling. */
	public static final OmnitrixSfx.Cue BLOOM = OmnitrixSfx.cue("wildvine_bloom")
			.layer(SoundEvents.FLOWERING_AZALEA_PLACE, 0.4f, 1.1f)
			.layer(SoundEvents.SPORE_BLOSSOM_PLACE, 0.35f, 1.0f)
			.layer(SoundEvents.BIG_DRIPLEAF_TILT_UP, 0.25f, 0.9f);
	/** Leaves rustling (the bush disguise). */
	public static final OmnitrixSfx.Cue RUSTLE = OmnitrixSfx.cue("wildvine_rustle")
			.layer(SoundEvents.AZALEA_LEAVES_STEP, 0.4f, 1.0f)
			.layer(SoundEvents.GRASS_STEP, 0.3f, 0.9f)
			.gap(8);

	public static final int ANIM_SEED = 1;
	public static final int ANIM_WHIP = 2;
	public static final int ANIM_BIND = 3;
	public static final int ANIM_POLLEN = 4;
	public static final int ANIM_GRAPPLE = 5;
	public static final int ANIM_CLIMB = 6;
	public static final int ANIM_WALL = 7;
	public static final int ANIM_REGROW = 8;
	public static final int ANIM_OVERGROW = 9;
	public static final int ANIM_ERUPT = 10;
	public static final int ANIM_LASH = 11;
	public static final int ANIM_TOUCH = 12;

	public static final int F_BUSH = 1;
	public static final int F_GRAPPLE = 2;
	public static final int F_REGROW = 4;
	public static final int F_CLIMB = 8;

	public static final String SEED_BOMB = "seed_bomb";
	public static final String THORN_WHIP = "thorn_whip";
	public static final String VINE_BIND = "vine_bind";
	public static final String POLLEN = "pollen_burst";
	public static final String GRAPPLE = "vine_grapple";
	public static final String CLIMB = "stretch_climb";
	public static final String ROOT_WALL = "root_wall";
	public static final String REGROWTH = "regrowth";
	public static final String OVERGROWTH = "overgrowth";
	public static final String ERUPTION = "root_eruption";
	public static final String LASH = "vine_lash";
	public static final String BONEMEAL = "bonemeal_touch";
	public static final String BUSH = "bush_hide";

	public static final float SEED_DAMAGE = 7f;
	public static final float SEED_RADIUS = 3f;
	public static final float WHIP_DAMAGE = 6f;
	public static final float BIND_RANGE = 18f;
	public static final int BIND_TICKS = 80;
	public static final float POLLEN_RADIUS = 5f;
	public static final double GRAPPLE_RANGE = 28.0;
	public static final int GRAPPLE_TICKS = 40;
	public static final int CLIMB_MAX = 12;
	public static final int WALL_TICKS = 160;
	public static final float WALL_HALF = 3f;
	public static final int REGROW_TICKS = 200;
	public static final float REGROW_RADIUS = 8f;
	public static final float REGROW_SUN = 2f;
	public static final float REGROW_SHADE = 0.5f;
	public static final float FIELD_RADIUS = 7f;
	public static final int FIELD_TICKS = 160;
	public static final float ERUPT_DAMAGE = 7f;
	public static final int BUSH_DELAY = 15;
	public static final float FIRE_WEAKNESS = 1.35f;

	private static final DustParticleOptions VINE_DUST = new DustParticleOptions(new Vector3f(0.29f, 0.6f, 0.16f), 1.0f);
	private static final DustParticleOptions POLLEN_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.88f, 0.3f), 1.4f);
	/** Grapple anchors and climb targets ride in the synced kit timers, which must be positive: block-coordinate * 16 + this. */
	private static final long ENC = 1L << 40;

	private static final Map<UUID, Field> FIELDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Wall> WALLS = new ConcurrentHashMap<>();
	/** player -> where they stood last tick (the bush hide needs "standing still"). */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

	private record Field(ResourceKey<Level> dim, Vec3 at, long until) {
	}

	/** A root wall: its centre on the ground, the way it faces (away from Wildvine), its length direction, and its end. */
	private record Wall(ResourceKey<Level> dim, Vec3 at, Vec3 normal, Vec3 along, long until) {
	}

	private WildvineKit() {
	}

	public static void register() {
		Aliens.register(new WildvineKit());
		AlienProjectileEntity.onHit("wildvine_seed", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				QuirkFx.blast(l, at).colours(0xFFF0A0, 0x6AB82A).size(SEED_RADIUS * 0.8f).life(14).flags(QuirkFx.F_NO_SMOKE).spawn();
				QuirkFx.decal(l, at.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A4A12).size(2.4f).life(160).spawn();
				l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.3, at.z, 24, 1.2, 0.5, 1.2, 0.05);
				l.sendParticles(VINE_DUST, at.x, at.y + 0.3, at.z, 16, 1.0, 0.4, 1.0, 0);
				OmnitrixSounds.play(l, at, BURST, 1.0f, 0.9f + l.random.nextFloat() * 0.2f);
				QuirkCamera.shakeAround(l, at, 8, 0.25f, 6);
			}
		});
	}

	public static void clearSessionState() {
		FIELDS.clear();
		WALLS.clear();
		LAST_POS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(28f).speed(1.0f).scale(1.1f).armor(3f, 0f).attack(2f).knockbackResist(0.2f).reach(1.5f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(SEED_BOMB, AbilitySlot.SLOT_1, 30), AlienMove.shift(THORN_WHIP, AbilitySlot.SLOT_1, 40),
				AlienMove.tap(VINE_BIND, AbilitySlot.SLOT_2, 160), AlienMove.shift(POLLEN, AbilitySlot.SLOT_2, 260),
				AlienMove.tap(GRAPPLE, AbilitySlot.SLOT_3, 40), AlienMove.shift(CLIMB, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(ROOT_WALL, AbilitySlot.SLOT_4, 320), AlienMove.shift(REGROWTH, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(OVERGROWTH, AbilitySlot.SLOT_6, 700), AlienMove.shift(ERUPTION, AbilitySlot.SLOT_6, 400),
				AlienMove.vanilla(LASH, "Attack", 0), AlienMove.vanilla(BONEMEAL, "Use on a block", 10),
				AlienMove.vanilla(BUSH, "Sneak (hold still)", 0));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), BLOOM, 1.0f, 1.0f);
		l.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 1.6, p.getZ(), 6, 0.5, 0.4, 0.5, 0.02);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		FIELDS.remove(p.getUUID());
		WALLS.remove(p.getUUID());
		LAST_POS.remove(p.getUUID());
		setFlag(p, F_BUSH | F_GRAPPLE | F_REGROW | F_CLIMB, false);
		for (String t : new String[] { "gx", "gy", "gz", "grapple_end", "climb_y", "climb_end", "regrow_end", "bush_at" }) {
			if (timer(p, t) > 0) {
				setTimer(p, t, 0);
			}
		}
		p.resetFallDistance();
	}

	private static long enc(double v) {
		return Math.round(v * 16.0) + ENC;
	}

	private static double dec(long v) {
		return (v - ENC) / 16.0;
	}

	/** The grapple anchor (null when not grappling). */
	public Vec3 anchor(ServerPlayer p) {
		if (!flag(p, F_GRAPPLE)) {
			return null;
		}
		return new Vec3(dec(timer(p, "gx")), dec(timer(p, "gy")), dec(timer(p, "gz")));
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		if (p.tickCount % 30 == 0) {
			l.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + p.getBbHeight(), p.getZ(), 1, 0.25, 0.1, 0.25, 0);
		}
		bush(p, l, now);
		// the grapple
		if (flag(p, F_GRAPPLE)) {
			Vec3 a = anchor(p);
			Vec3 to = a.subtract(p.position().add(0, p.getBbHeight() * 0.5, 0));
			double d = to.length();
			if (d < 1.6 || now >= timer(p, "grapple_end")) {
				endGrapple(p, true);
			} else {
				double speed = Math.min(1.25, 0.45 + d * 0.1);
				p.setDeltaMovement(p.getDeltaMovement().scale(0.25).add(to.normalize().scale(speed)));
				p.hurtMarked = true;
				p.resetFallDistance();
				if (now % 2 == 0) {
					Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.4)).add(0, -0.4, 0);
					Vec3 line = a.subtract(hand);
					int n = (int) Math.min(40, line.length() * 2);
					for (int k = 0; k <= n; k++) {
						Vec3 q = hand.add(line.scale(k / (double) Math.max(1, n)));
						l.sendParticles(VINE_DUST, q.x, q.y, q.z, 1, 0, 0, 0, 0);
					}
				}
			}
		}
		// the stretch climb
		if (flag(p, F_CLIMB)) {
			double top = dec(timer(p, "climb_y"));
			if (now >= timer(p, "climb_end") || p.getY() >= top + 0.1) {
				setFlag(p, F_CLIMB, false);
				setTimer(p, "climb_end", 0);
				Vec3 f = horizontal(p.getLookAngle());
				p.setDeltaMovement(f.x * 0.45, 0.25, f.z * 0.45);
				p.hurtMarked = true;
			} else {
				Vec3 f = horizontal(p.getLookAngle());
				p.setDeltaMovement(f.x * 0.06, 0.7, f.z * 0.06);
				p.hurtMarked = true;
				p.resetFallDistance();
				if (now % 2 == 0) {
					l.sendParticles(VINE_DUST, p.getX() + f.x * 0.4, p.getY() + 0.3, p.getZ() + f.z * 0.4, 3, 0.15, 0.4, 0.15, 0);
				}
			}
		}
		// regrowth
		long regrow = timer(p, "regrow_end");
		if (regrow > 0) {
			if (now >= regrow) {
				setTimer(p, "regrow_end", 0);
				setFlag(p, F_REGROW, false);
			} else if (now % 20 == 0) {
				float amount = inSun(p) ? REGROW_SUN : REGROW_SHADE;
				for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(REGROW_RADIUS),
						e -> e.isAlive() && (e == p || com.projecthero.mod.squad.Squads.areAllies(p, e)))) {
					if (e.getHealth() < e.getMaxHealth()) {
						e.heal(amount);
					}
					l.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.3, 0.4, 0.3, 0);
				}
				l.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + p.getBbHeight(), p.getZ(), 6, 0.6, 0.3, 0.6, 0.01);
			}
		}
		// the root wall
		Wall w = WALLS.get(p.getUUID());
		if (w != null) {
			if (now >= w.until() || w.dim() != l.dimension()) {
				WALLS.remove(p.getUUID());
			} else {
				AABB box = new AABB(w.at(), w.at()).inflate(WALL_HALF + 1.5, 0, WALL_HALF + 1.5).expandTowards(0, 3.2, 0);
				for (Entity e : l.getEntities(p, box, e -> (e instanceof Projectile pr && pr.getOwner() != p)
						|| (e instanceof LivingEntity le && le.isAlive() && !com.projecthero.mod.squad.Squads.areAllies(p, le)))) {
					Vec3 rel = e.position().subtract(w.at());
					double across = rel.dot(w.normal());
					double along = rel.dot(w.along());
					if (Math.abs(along) > WALL_HALF + 0.3 || Math.abs(across) > 1.0 || rel.y > 3.2 || rel.y < -1.0) {
						continue;
					}
					if (e instanceof Projectile) {
						l.sendParticles(VINE_DUST, e.getX(), e.getY(), e.getZ(), 6, 0.1, 0.1, 0.1, 0);
						e.discard(); // shots stick in the roots
					} else {
						double side = across >= 0 ? 1 : -1;
						e.setDeltaMovement(w.normal().scale(side * 0.45).add(0, 0.05, 0));
						e.hurtMarked = true;
					}
				}
			}
		}
		// overgrowth
		Field f = FIELDS.get(p.getUUID());
		if (f != null) {
			if (now >= f.until() || f.dim() != l.dimension()) {
				FIELDS.remove(p.getUUID());
			} else {
				if (now % 4 == 0) {
					for (int k = 0; k < 3; k++) {
						double a = l.random.nextDouble() * Math.PI * 2, r = Math.sqrt(l.random.nextDouble()) * FIELD_RADIUS;
						l.sendParticles(VINE_DUST, f.at().x + Math.cos(a) * r, f.at().y + 0.15, f.at().z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0);
					}
				}
				if (now % 10 == 0) {
					for (LivingEntity e : around(p, f.at().add(0, 1, 0), FIELD_RADIUS)) {
						if (!e.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || now % 40 == 0) {
							QuirkFx.spike(l, e.position(), new Vec3(0, 1, 0)).block(Blocks.MANGROVE_ROOTS.defaultBlockState()).size(1.6f).size2(0.25f).life(20)
									.spawn();
						}
						AlienStatus.root(p, e, 16, 0);
						if (now % 20 == 0) {
							hurt(p, e, 1f, false);
						}
					}
				}
			}
		}
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	/** Sun on the leaves: daytime, open sky, no rain. */
	public static boolean inSun(ServerPlayer p) {
		Level l = p.level();
		BlockPos head = BlockPos.containing(p.getEyePosition());
		return l.isDay() && !l.isRainingAt(head) && l.canSeeSky(head);
	}

	// ---------------------------------------------------------------- the bush

	private void bush(ServerPlayer p, ServerLevel l, long now) {
		Vec3 last = LAST_POS.put(p.getUUID(), p.position());
		boolean still = last == null || last.distanceToSqr(p.position()) < 0.0009;
		boolean want = p.isShiftKeyDown() && still && (p.onGround() || p.isPassenger() || p.getDeltaMovement().lengthSqr() < 0.01)
				&& !flag(p, F_GRAPPLE) && !flag(p, F_CLIMB);
		if (!want) {
			if (timer(p, "bush_at") > 0) {
				setTimer(p, "bush_at", 0);
			}
			if (flag(p, F_BUSH)) {
				setFlag(p, F_BUSH, false);
				OmnitrixSounds.play(l, p.position(), RUSTLE, 0.7f, 1.2f);
				l.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 0.8, p.getZ(), 10, 0.5, 0.5, 0.5, 0.02);
			}
			return;
		}
		if (!flag(p, F_BUSH)) {
			long at = timer(p, "bush_at");
			if (at == 0) {
				setTimer(p, "bush_at", now + BUSH_DELAY);
				return;
			}
			if (now < at) {
				return;
			}
			setTimer(p, "bush_at", 0);
			setFlag(p, F_BUSH, true);
			OmnitrixSounds.play(l, p.position(), RUSTLE, 1.0f, 0.9f);
			l.sendParticles(VINE_DUST, p.getX(), p.getY() + 0.8, p.getZ(), 20, 0.5, 0.5, 0.5, 0);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildvine.hidden").withStyle(ChatFormatting.GREEN), true);
		}
		// hidden: whatever was hunting him loses the trail
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) {
			if (AlienStatus.loseTrack(m, p) && now % 10 == 0) {
				l.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getEyeY() + 0.3, m.getZ(), 2, 0.1, 0.1, 0.1, 0);
			}
		}
	}

	public boolean hidden(ServerPlayer p) {
		return flag(p, F_BUSH);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					thornWhip(p);
				} else {
					seedBomb(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					pollenBurst(p);
				} else {
					vineBind(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					stretchClimb(p);
				} else {
					grapple(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					regrowth(p);
				} else {
					rootWall(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					rootEruption(p);
				} else {
					overgrowth(p);
				}
			}
			default -> {
			}
		}
	}

	private Vec3 hand(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.5)).add(0, -0.35, 0);
	}

	public boolean seedBomb(ServerPlayer p) {
		if (!use(p, SEED_BOMB)) {
			return false;
		}
		anim(p, ANIM_SEED);
		AlienProjectileEntity.of(p, "wildvine_seed").colour(0x9A7A2A).size(0.38f).damage(3f).blast(SEED_RADIUS, SEED_DAMAGE).gravity(0.05).speed(1.2)
				.life(70).spawn();
		OmnitrixSounds.play(level(p), p.position(), SEED, 1.0f, 1.0f);
		return true;
	}

	public boolean thornWhip(ServerPlayer p) {
		if (!use(p, THORN_WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 h = hand(p);
		for (int k = -2; k <= 2; k++) {
			Vec3 tip = eye.add(look.yRot((float) Math.toRadians(k * 18)).scale(5.0)).add(0, -0.3, 0);
			QuirkFx.ribbon(l, h, tip).colours(0x8AD84A, 0x2A6A12).size(0.22f).life(6 + Math.abs(k)).spawn();
		}
		OmnitrixSounds.play(l, p.position(), VINE, 1.0f, 1.2f);
		for (LivingEntity e : around(p, eye.add(look.scale(2.8)), 4.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			if (to.length() <= 6.0 && to.normalize().dot(look) > Math.cos(Math.toRadians(45))) {
				hurt(p, e, WHIP_DAMAGE, false);
				shove(e, p.position(), 0.9, 0.3);
				l.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
			}
		}
		return true;
	}

	public boolean vineBind(ServerPlayer p) {
		LivingEntity t = lookTarget(p, BIND_RANGE);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildvine.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, VINE_BIND)) {
			return false;
		}
		anim(p, ANIM_BIND);
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, hand(p), t.position().add(0, t.getBbHeight() * 0.5, 0)).colours(0x8AD84A, 0x2A6A12).size(0.25f).life(12).spawn();
		QuirkFx.spike(l, t.position(), new Vec3(0, 1, 0)).block(Blocks.MANGROVE_ROOTS.defaultBlockState()).size(Math.max(1.4f, t.getBbHeight() * 0.9f))
				.size2(0.3f).life(BIND_TICKS).spawn();
		AlienStatus.root(p, t, BIND_TICKS, 40);
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), p);
		hurt(p, t, 2f, false);
		l.sendParticles(VINE_DUST, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 20, 0.3, t.getBbHeight() * 0.4, 0.3, 0);
		OmnitrixSounds.play(l, t.position(), ROOTS, 1.0f, 1.2f);
		OmnitrixSounds.play(l, p.position(), VINE, 0.8f, 0.9f);
		return true;
	}

	public boolean pollenBurst(ServerPlayer p) {
		if (!use(p, POLLEN)) {
			return false;
		}
		anim(p, ANIM_POLLEN);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.2, 0);
		QuirkFx.blast(l, c).colours(0xFFF8C0, 0xF0C040).size(POLLEN_RADIUS * 0.7f).life(18).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFF0A0, PINK).style(QuirkFx.RING_SOFT).size(POLLEN_RADIUS).thickness(0.6f).life(16).ground()
				.spawn();
		l.sendParticles(POLLEN_DUST, c.x, c.y, c.z, 60, POLLEN_RADIUS * 0.5, 0.8, POLLEN_RADIUS * 0.5, 0.02);
		l.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, c.x, c.y + 1, c.z, 30, POLLEN_RADIUS * 0.5, 0.6, POLLEN_RADIUS * 0.5, 0);
		OmnitrixSounds.play(l, c, BLOOM, 1.0f, 1.4f);
		for (LivingEntity e : around(p, c, POLLEN_RADIUS)) {
			hurt(p, e, 2f, false);
			e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0), p);
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), p);
		}
		return true;
	}

	/** X: a vine whipped to the block you look at (28 blocks), reeling you in. X again lets go. */
	public boolean grapple(ServerPlayer p) {
		if (flag(p, F_GRAPPLE)) {
			endGrapple(p, false);
			return true;
		}
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(GRAPPLE_RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildvine.no_anchor").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, GRAPPLE)) {
			return false;
		}
		anim(p, ANIM_GRAPPLE);
		Vec3 a = hit.getLocation();
		long now = l.getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key("gx"), enc(a.x));
		n.timers.put(key("gy"), enc(a.y));
		n.timers.put(key("gz"), enc(a.z));
		n.timers.put(key("grapple_end"), now + GRAPPLE_TICKS);
		n.kitFlags |= F_GRAPPLE;
		n.kitFlags &= ~(F_BUSH | F_CLIMB);
		Omnitrix.save(p, n);
		Vec3 to = a.subtract(p.position()).normalize();
		p.setDeltaMovement(to.scale(0.6).add(0, 0.25, 0));
		p.hurtMarked = true;
		QuirkFx.ribbon(l, hand(p), a).colours(0x8AD84A, 0x2A6A12).size(0.2f).life(8).spawn();
		l.sendParticles(VINE_DUST, a.x, a.y, a.z, 12, 0.2, 0.2, 0.2, 0);
		OmnitrixSounds.play(l, p.position(), VINE, 1.0f, 1.0f);
		return true;
	}

	private void endGrapple(ServerPlayer p, boolean arrived) {
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { "gx", "gy", "gz", "grapple_end" }) {
			n.timers.remove(key(t));
		}
		n.kitFlags &= ~F_GRAPPLE;
		Omnitrix.save(p, n);
		if (arrived) {
			p.setDeltaMovement(p.getDeltaMovement().scale(0.3).add(0, 0.35, 0));
			p.hurtMarked = true;
		}
		p.resetFallDistance();
	}

	/** Shift+X: stretch up the wall in front (to its top, at most 12 blocks); with no wall, a tall stretch straight up. */
	public boolean stretchClimb(ServerPlayer p) {
		if (!use(p, CLIMB)) {
			return false;
		}
		ServerLevel l = level(p);
		Vec3 f = horizontal(p.getLookAngle());
		BlockPos feet = p.blockPosition();
		BlockPos ahead = BlockPos.containing(p.getX() + f.x * 0.9, p.getY() + 0.5, p.getZ() + f.z * 0.9);
		int top = 6;
		if (!l.getBlockState(ahead).getCollisionShape(l, ahead).isEmpty() || !l.getBlockState(ahead.above())
				.getCollisionShape(l, ahead.above()).isEmpty()) {
			top = CLIMB_MAX;
			for (int h = 1; h <= CLIMB_MAX; h++) {
				BlockPos q = ahead.above(h);
				if (l.getBlockState(q).getCollisionShape(l, q).isEmpty() && l.getBlockState(q.above()).getCollisionShape(l, q.above()).isEmpty()) {
					top = h;
					break;
				}
			}
		}
		anim(p, ANIM_CLIMB);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key("climb_y"), enc(feet.getY() + top + 0.05));
		n.timers.put(key("climb_end"), l.getGameTime() + 10 + top * 3L);
		n.kitFlags |= F_CLIMB;
		n.kitFlags &= ~F_BUSH;
		Omnitrix.save(p, n);
		p.setDeltaMovement(0, 0.7, 0);
		p.hurtMarked = true;
		OmnitrixSounds.play(l, p.position(), VINE, 1.0f, 0.7f);
		return true;
	}

	public boolean rootWall(ServerPlayer p) {
		if (!use(p, ROOT_WALL)) {
			return false;
		}
		anim(p, ANIM_WALL);
		ServerLevel l = level(p);
		Vec3 f = horizontal(p.getLookAngle());
		Vec3 along = new Vec3(-f.z, 0, f.x);
		Vec3 c = p.position().add(f.scale(2.5));
		WALLS.put(p.getUUID(), new Wall(l.dimension(), c, f, along, l.getGameTime() + WALL_TICKS));
		for (int k = -3; k <= 3; k++) {
			Vec3 at = c.add(along.scale(k * 0.95));
			QuirkFx.spike(l, at, new Vec3(f.x * 0.15, 1, f.z * 0.15)).block(Blocks.MANGROVE_ROOTS.defaultBlockState()).size(2.6f + (k % 2 == 0 ? 0.5f : 0f))
					.size2(0.5f).life(WALL_TICKS).spawn();
		}
		QuirkCamera.shakeAround(l, c, 10, 0.3f, 8);
		OmnitrixSounds.play(l, c, ROOTS, 1.2f, 0.8f);
		return true;
	}

	public boolean regrowth(ServerPlayer p) {
		if (!use(p, REGROWTH)) {
			return false;
		}
		anim(p, ANIM_REGROW);
		ServerLevel l = level(p);
		setTimer(p, "regrow_end", l.getGameTime() + REGROW_TICKS);
		setFlag(p, F_REGROW, true);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xF0FFC0, 0x6AD83A).style(QuirkFx.RING_SOFT).size(REGROW_RADIUS).thickness(0.4f).life(20)
				.ground().spawn();
		OmnitrixSounds.play(l, p.position(), BLOOM, 1.0f, 1.0f);
		if (!inSun(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.wildvine.shade").withStyle(ChatFormatting.GRAY), true);
		}
		return true;
	}

	public boolean overgrowth(ServerPlayer p) {
		if (!use(p, OVERGROWTH)) {
			return false;
		}
		anim(p, ANIM_OVERGROW);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		FIELDS.put(p.getUUID(), new Field(l.dimension(), c, l.getGameTime() + FIELD_TICKS));
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xC0FF80, COLOUR).size(FIELD_RADIUS).thickness(0.8f).life(20).ground().spawn();
		QuirkFx.decal(l, c.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x1E3A0E).size(FIELD_RADIUS * 1.6f).life(FIELD_TICKS + 60).spawn();
		for (int k = 0; k < 10; k++) {
			double a = k * Math.PI * 2 / 10;
			Vec3 at = c.add(Math.cos(a) * FIELD_RADIUS * 0.75, 0, Math.sin(a) * FIELD_RADIUS * 0.75);
			QuirkFx.spike(l, at, new Vec3(0, 1, 0)).block(Blocks.MANGROVE_ROOTS.defaultBlockState()).size(1.8f).size2(0.3f).life(FIELD_TICKS).spawn();
		}
		QuirkCamera.shakeAround(l, c, 12, 0.3f, 10);
		OmnitrixSounds.play(l, c, ROOTS, 1.4f, 0.6f);
		return true;
	}

	public boolean rootEruption(ServerPlayer p) {
		if (!use(p, ERUPTION)) {
			return false;
		}
		anim(p, ANIM_ERUPT);
		ServerLevel l = level(p);
		Vec3 f = horizontal(p.getLookAngle());
		java.util.Set<Integer> hit = new java.util.HashSet<>();
		for (int k = 1; k <= 6; k++) {
			Vec3 at = p.position().add(f.scale(k * 2.0));
			var down = l.clip(new ClipContext(at.add(0, 2, 0), at.add(0, -3, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (down.getType() == HitResult.Type.BLOCK) {
				at = down.getLocation();
			}
			QuirkFx.spike(l, at, new Vec3(0, 1, 0)).block(Blocks.MANGROVE_ROOTS.defaultBlockState()).size(2.2f + k * 0.15f).size2(0.45f).life(30 + k * 2)
					.spawn();
			for (LivingEntity e : around(p, at.add(0, 0.8, 0), 1.7)) {
				if (hit.add(e.getId())) {
					hurt(p, e, ERUPT_DAMAGE, false);
					e.push(0, 0.75, 0);
					e.hurtMarked = true;
				}
			}
		}
		QuirkCamera.shakeAround(l, p.position(), 14, 0.4f, 10);
		OmnitrixSounds.play(l, p.position(), ROOTS, 1.3f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity) {
			anim(p, ANIM_LASH);
			ServerLevel l = level(p);
			QuirkFx.ribbon(l, hand(p), target.position().add(0, target.getBbHeight() * 0.6, 0)).colours(0x8AD84A, 0x2A6A12).size(0.15f).life(5).spawn();
		}
		return false;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		return bonemeal(p, hit.getBlockPos(), hit.getDirection()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Use on a block with an empty hand: it grows as if bonemealed. */
	public boolean bonemeal(ServerPlayer p, BlockPos pos, Direction face) {
		ServerLevel l = level(p);
		if (!p.mayInteract(l, pos) || cooldownLeft(p, BONEMEAL) > 0) {
			return false;
		}
		ItemStack meal = new ItemStack(Items.BONE_MEAL);
		boolean grew = BoneMealItem.growCrop(meal, l, pos);
		if (!grew && l.getBlockState(pos).isFaceSturdy(l, pos, face)) {
			grew = BoneMealItem.growWaterPlant(meal, l, pos.relative(face), face);
		}
		if (!grew) {
			return false;
		}
		cooldown(p, BONEMEAL, -1);
		anim(p, ANIM_TOUCH);
		l.levelEvent(1505, pos, 15);
		l.sendParticles(ParticleTypes.CHERRY_LEAVES, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0);
		OmnitrixSounds.play(l, Vec3.atCenterOf(pos), BLOOM, 0.6f, 1.6f);
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ---------------------------------------------------------------- damage

	/** A plant: fire hurts him more. */
	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		return source.is(DamageTypeTags.IS_FIRE) ? amount * FIRE_WEAKNESS : amount;
	}
}
