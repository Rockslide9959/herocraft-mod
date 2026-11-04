package com.projecthero.mod.omnitrix.alien.wildvine;

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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "also create a moveset for wildvine please ... do research on his powers and look at what he does in the show ...
 * Make this another alien you unlock on the 11th mistransformation"). Wildvine, a Florauna: a walking plant whose limbs stretch like
 * vines. He lashes and grabs from far off, swings on his own vines and pulls himself along them, throws the black seed pods on his
 * back as bombs and plants them as mines, burrows into the ground and bursts back up, climbs any wall, wraps foes up and regrows what
 * he loses -- but fire and frost hit a plant hard. Spec: {@code docs/OMNITRIX_REVAMP_WILDVINE.md}.
 *
 * <p>Found like Cannonbolt: not on the prototype's dial until a mistransform lands on him ({@link Aliens#hidden}).
 */
public final class WildvineKit extends AlienKit {
	public static final String ID = "wildvine";
	public static final int COLOUR = 0x5FB043;

	// ---------------------------------------------------------------- synced flags
	public static final int F_SWING = 1;
	public static final int F_ZIP = 2;
	/** Root Burrow: under the ground (drawn as a moving mound, untouchable). */
	public static final int F_BURROW = 4;
	/** Thicket Camouflage. */
	public static final int F_CAMO = 8;
	/** In the air off one of his vines (no fall damage till he lands). */
	public static final int F_AIR = 16;

	// ---------------------------------------------------------------- poses
	public static final int ANIM_LASH = 1, ANIM_GRAB = 2, ANIM_THROW = 3, ANIM_PLANT = 4, ANIM_SWING = 5, ANIM_WRAP = 6, ANIM_SNARE = 7,
			ANIM_REGROW = 8, ANIM_OVERGROWTH = 9, ANIM_EMERGE = 10, ANIM_PUNCH = 11;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.wildvine.<id>)
	public static final String LASH = "vine_lash", GRAB = "vine_grab", SEED_BOMB = "seed_bomb", SEED_MINES = "seed_mines", SWING = "vine_swing",
			BURROW = "root_burrow", CONSTRICT = "constrict", SNARE = "vine_snare", REGROWTH = "regrowth", CAMO = "thicket_camouflage",
			OVERGROWTH = "overgrowth", ARMS = "stretch_arms", CLIMB = "creeper_climb", SUN = "photosynthesis";

	// ---------------------------------------------------------------- numbers (the alien damage scale: quick ~9-14, normal 14-22, big 22-28)
	public static final float LASH_DAMAGE = 12f, GRAB_DAMAGE = 10f, SEED_DIRECT = 4f, SEED_DAMAGE = 18f, MINE_DAMAGE = 16f, EMERGE_DAMAGE = 14f,
			SQUEEZE_DAMAGE = 4f, SNARE_DAMAGE = 8f, OVERGROWTH_DAMAGE = 22f, REGROW_HEAL = 10f;
	public static final double LASH_RANGE = 9, GRAB_RANGE = 16, ZIP_RANGE = 24, SWING_RANGE = 30, CONSTRICT_RANGE = 12, SEED_RADIUS = 3.0,
			MINE_TRIGGER = 1.6, MINE_RADIUS = 2.5, EMERGE_RADIUS = 3.0, SNARE_RADIUS = 5.0, OVERGROWTH_RADIUS = 7.0;
	public static final int MINES = 3, MINE_ARM = 20, MINE_LIFE = 400, BURROW_TICKS = 60, CONSTRICT_TICKS = 60, SQUEEZE_EVERY = 10, SNARE_ROOT = 40,
			OVERGROWTH_ROOT = 60, CAMO_TICKS = 120, SWING_MAX = 400, ZIP_MAX = 40;
	/** The zip's pull (blocks a tick) and where it lets go (blocks from the hook) -- Spidermonkey's / Ladybug's zip line. */
	public static final double ZIP_SPEED = 1.8, ZIP_ARRIVE = 1.8, SWING_SLACK_BREAK = 12.0;
	/** v0.17.2: the pod moves' cooldowns -- a thrown / planted pod regrows on his back over them (WildvineFx.backPods). */
	public static final int SEED_BOMB_COOLDOWN = 60, SEED_MINES_COOLDOWN = 300;
	/** Weaknesses: fire and frost hurt a plant this much more. */
	public static final float FIRE_WEAKNESS = 1.75f, FROST_WEAKNESS = 1.75f;

	private static final ResourceLocation BURROW_SPEED = PowerToggles.id("omnitrix_wildvine_burrow");

	// the line he swings / zips on (Spidermonkey's synced keys: the owning client moves him)
	public static final String LINE = "line", LINE_AT = "line_at", HOOK_X = "hook_x", HOOK_Y = "hook_y", HOOK_Z = "hook_z", ROPE = "rope";
	private static final String[] LINE_KEYS = { LINE, LINE_AT, HOOK_X, HOOK_Y, HOOK_Z, ROPE };
	private static final String BURROW_END = "burrow_end", CAMO_END = "camo_end", AIR_AT = "air_at";

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_LASH = OmnitrixSfx.cue("wildvine_lash")
			.layer(SoundEvents.FISHING_BOBBER_THROW, 0.7f, 0.7f)
			.layer(SoundEvents.CAVE_VINES_PLACE, 0.6f, 0.9f);
	public static final OmnitrixSfx.Cue SND_SNAP = OmnitrixSfx.cue("wildvine_snap")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.3f)
			.layer(SoundEvents.BIG_DRIPLEAF_TILT_DOWN, 0.6f, 0.8f);
	public static final OmnitrixSfx.Cue SND_GROW = OmnitrixSfx.cue("wildvine_grow")
			.layer(SoundEvents.CAVE_VINES_PLACE, 0.7f, 0.6f)
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.6f, 0.7f, 2)
			.layer(SoundEvents.AZALEA_LEAVES_PLACE, 0.5f, 0.8f, 4);
	public static final OmnitrixSfx.Cue SND_POD = OmnitrixSfx.cue("wildvine_pod")
			.layer(SoundEvents.BIG_DRIPLEAF_PLACE, 0.6f, 1.4f)
			.layer(SoundEvents.SNIFFER_EGG_PLOP, 0.5f, 1.2f);
	public static final OmnitrixSfx.Cue SND_BURST = OmnitrixSfx.cue("wildvine_burst")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.45f, 1.4f)
			.layer(SoundEvents.SLIME_SQUISH, 0.7f, 0.6f);
	public static final OmnitrixSfx.Cue SND_SQUEEZE = OmnitrixSfx.cue("wildvine_squeeze")
			.layer(SoundEvents.BAMBOO_BREAK, 0.6f, 0.6f)
			.layer(SoundEvents.CAVE_VINES_STEP, 0.6f, 0.7f);
	public static final OmnitrixSfx.Cue SND_DIG = OmnitrixSfx.cue("wildvine_dig")
			.layer(SoundEvents.ROOTED_DIRT_BREAK, 0.7f, 0.7f)
			.layer(SoundEvents.GRAVEL_BREAK, 0.5f, 0.8f);

	// ---------------------------------------------------------------- server-only move state
	/** A swing (mode 1) or zip (mode 2) line: the hook, the block it is stuck to, the rope, when, its drawn vine. */
	private record Line(int mode, Vec3 hook, BlockPos block, double rope, long start, AlienProjectileEntity vine) {
	}

	/** A creature in his coils (Constrict): squeezed every {@link #SQUEEZE_EVERY} ticks till {@code end}. */
	private record Coil(LivingEntity target, long start, long end, AlienProjectileEntity vine) {
	}

	private static final Map<UUID, Line> LINES = new ConcurrentHashMap<>();
	private static final Map<UUID, Coil> COILS = new ConcurrentHashMap<>();
	/** His planted seed mines (each a held projectile, armed {@link #MINE_ARM} ticks after planting). */
	private static final Map<UUID, List<AlienProjectileEntity>> MINE_LIST = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private WildvineKit() {
	}

	public static void register() {
		Aliens.register(new WildvineKit());
		// the seed bomb bursts into a cloud of splinters and spores where it lands
		AlienProjectileEntity.onHit("wildvine_seed", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				burstFx(l, hit.getLocation(), (float) SEED_RADIUS);
			}
		});
	}

	public static void clearSessionState() {
		LINES.clear();
		COILS.clear();
		MINE_LIST.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// a tall, light, wiry plant: quick on his feet, long reach, not much armour -- his vines do the work
		return AlienStats.builder().health(30f).speed(1.05f).scale(1.1f).armor(4f, 0f).attack(5f).step(0.6f).jump(0.05f).safeFall(6f).reach(2.0f)
				.build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(LASH, AbilitySlot.SLOT_1, 20), AlienMove.shift(GRAB, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(SEED_BOMB, AbilitySlot.SLOT_2, SEED_BOMB_COOLDOWN), AlienMove.shift(SEED_MINES, AbilitySlot.SLOT_2, SEED_MINES_COOLDOWN),
				AlienMove.tap(SWING, AbilitySlot.SLOT_3, 30), AlienMove.shift(BURROW, AbilitySlot.SLOT_3, 300),
				AlienMove.tap(CONSTRICT, AbilitySlot.SLOT_4, 200), AlienMove.shift(SNARE, AbilitySlot.SLOT_4, 260),
				AlienMove.tap(REGROWTH, AbilitySlot.SLOT_5, 400), AlienMove.shift(CAMO, AbilitySlot.SLOT_5, 500),
				AlienMove.tap(OVERGROWTH, AbilitySlot.SLOT_6, 800),
				AlienMove.vanilla(ARMS, "Attack", 0), AlienMove.vanilla(CLIMB, "Passive", 0), AlienMove.vanilla(SUN, "Passive", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_SWING) || flag(p, F_ZIP) || flag(p, F_BURROW);
			case SLOT_5 -> flag(p, F_CAMO);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the body

	@Override
	public boolean climbs(Player p) {
		// the show: his vines grip any surface -- he climbs a wall he is pressed against like a creeper plant
		return p.horizontalCollision && !flag(p, F_BURROW);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xC8F080, COLOUR).size(3f).thickness(0.6f).life(14).ground().spawn();
		leaves(l, p.position().add(0, 1, 0), 16, 0.5);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		endLine(p, false);
		if (flag(p, F_BURROW)) {
			surface(p, false);
		}
		endCamo(p);
		Coil c = COILS.remove(p.getUUID());
		if (c != null) {
			c.vine().discard();
		}
		List<AlienProjectileEntity> mines = MINE_LIST.remove(p.getUUID());
		if (mines != null) {
			mines.forEach(Entity::discard);
		}
		for (int f : new int[] { F_SWING, F_ZIP, F_BURROW, F_CAMO, F_AIR }) {
			setFlag(p, f, false);
		}
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, BURROW_SPEED);
		LAST_TICK.remove(p.getUUID());
	}

	/** Any other move pulls him out of his camouflage; any move but the burrow's own key brings him up first. */
	@Override
	protected void onUse(ServerPlayer p, String moveId) {
		if (!moveId.equals(CAMO)) {
			endCamo(p);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_BURROW)) {
			// underground only the burrow's key does anything: it brings him up early
			if (slot == AbilitySlot.SLOT_3) {
				surface(p, true);
			}
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					vineGrab(p);
				} else {
					vineLash(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					seedMines(p);
				} else {
					seedBomb(p);
				}
			}
			case SLOT_3 -> {
				// either X again lets go of a line (Spidermonkey's / Ladybug's X)
				if (LINES.containsKey(p.getUUID())) {
					endLine(p, true);
				} else if (shift) {
					rootBurrow(p);
				} else {
					vineSwing(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					vineSnare(p);
				} else {
					constrict(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					camouflage(p);
				} else {
					regrowth(p);
				}
			}
			case SLOT_6 -> {
				if (!shift) {
					overgrowth(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		// the swing runs while X is held; letting go flings him off the end of the vine (a zip runs to the end)
		if (slot == AbilitySlot.SLOT_3 && !shift && swinging(p)) {
			endLine(p, true);
		}
	}

	/** Left-click: a stretched vine-arm punch (his reach is the body's {@code reach} stat). Nothing from underground. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_BURROW)) {
			return true;
		}
		endCamo(p);
		anim(p, ANIM_PUNCH);
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: an arm whips out as a vine -- 12 to the first creature along his look within 9 blocks, jerked a little toward him. */
	public boolean vineLash(ServerPlayer p) {
		if (!use(p, LASH)) {
			return false;
		}
		anim(p, ANIM_LASH);
		ServerLevel l = level(p);
		LivingEntity t = lookTarget(p, LASH_RANGE);
		Vec3 end;
		if (t != null) {
			end = t.position().add(0, t.getBbHeight() * 0.55, 0);
			if (hurt(p, t, LASH_DAMAGE, false)) {
				Vec3 to = p.position().subtract(t.position());
				Vec3 h = new Vec3(to.x, 0, to.z);
				if (h.lengthSqr() > 1.0e-4 && !AlienStatus.isBoss(t)) {
					h = h.normalize().scale(0.45 * (1.0 - Math.min(1.0, t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE))));
					t.push(h.x, 0.2, h.z);
					t.hurtMarked = true;
				}
			}
			l.sendParticles(ParticleTypes.SWEEP_ATTACK, end.x, end.y, end.z, 1, 0, 0, 0, 0);
			OmnitrixSounds.play(l, end, SND_SNAP, 1.0f, 1.0f);
		} else {
			BlockHitResult b = blockAlong(p, p.getLookAngle(), LASH_RANGE);
			end = b != null ? b.getLocation() : p.getEyePosition().add(p.getLookAngle().scale(LASH_RANGE));
		}
		vine(p, end, t, 7);
		OmnitrixSounds.play(l, p.position(), SND_LASH, 0.9f, 1.2f);
		return true;
	}

	/**
	 * Shift+R: his arm stretches up to 16 blocks and grabs -- a creature under the crosshair takes 10 and is reeled in to him (a boss
	 * only takes the hit); with no creature but a block within 24, he pulls himself along the vine to it (a zip). False (no cooldown) with
	 * nothing to grab.
	 */
	public boolean vineGrab(ServerPlayer p) {
		LivingEntity t = lookTarget(p, GRAB_RANGE);
		ServerLevel l = level(p);
		if (t != null) {
			if (!use(p, GRAB)) {
				return false;
			}
			anim(p, ANIM_GRAB);
			hurt(p, t, GRAB_DAMAGE, false);
			if (!AlienStatus.isBoss(t)) {
				Vec3 to = p.position().add(p.getLookAngle().multiply(1, 0, 1).scale(1.4)).subtract(t.position());
				double d = to.length();
				if (d > 1.0) {
					Vec3 v = to.scale(Math.min(1.6, 0.18 * d + 0.4) / d);
					t.setDeltaMovement(v.x, Math.max(0.35, v.y + 0.25), v.z);
					t.hurtMarked = true;
				}
			}
			vine(p, null, t, 12);
			OmnitrixSounds.play(l, t.position(), SND_SNAP, 1.0f, 0.8f);
			return true;
		}
		BlockHitResult hit = blockAlong(p, p.getLookAngle(), ZIP_RANGE);
		if (hit == null) {
			say(p, "message.projecthero.omnitrix.wildvine.nothing_to_grab");
			return false;
		}
		if (!use(p, GRAB)) {
			return false;
		}
		anim(p, ANIM_GRAB);
		Vec3 at = hit.getLocation();
		startLine(p, 2, at, hit.getBlockPos(), at.distanceTo(p.getEyePosition()), ZIP_MAX + 10);
		OmnitrixSounds.play(l, p.position(), SND_LASH, 1.0f, 0.8f);
		return true;
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** G: he plucks a seed pod off his back and throws it -- it arcs and bursts where it lands: up to 18 within 3 blocks. */
	public boolean seedBomb(ServerPlayer p) {
		if (!use(p, SEED_BOMB)) {
			return false;
		}
		anim(p, ANIM_THROW);
		AlienProjectileEntity.of(p, "wildvine_seed").colour(0x07030F).size(0.5f).damage(SEED_DIRECT).blast((float) SEED_RADIUS, SEED_DAMAGE)
				.gravity(0.05).speed(1.35).life(80).spawn();
		OmnitrixSounds.play(level(p), p.position(), SND_POD, 1.0f, 1.0f);
		return true;
	}

	/**
	 * Shift+G: he plants three seed pods in the ground around him. Each arms after 1 s and bursts when a hostile creature comes within
	 * 1.6 blocks: 16 within 2.5. They last 20 s; planting more replaces the old ones.
	 */
	public boolean seedMines(ServerPlayer p) {
		if (!use(p, SEED_MINES)) {
			return false;
		}
		anim(p, ANIM_PLANT);
		ServerLevel l = level(p);
		List<AlienProjectileEntity> old = MINE_LIST.remove(p.getUUID());
		if (old != null) {
			old.forEach(Entity::discard);
		}
		List<AlienProjectileEntity> mines = new ArrayList<>();
		Vec3 fwd = flat(p);
		for (int i = 0; i < MINES; i++) {
			double a = Math.toRadians(-50 + 50 * i);
			Vec3 dir = new Vec3(fwd.x * Math.cos(a) - fwd.z * Math.sin(a), 0, fwd.x * Math.sin(a) + fwd.z * Math.cos(a));
			Vec3 at = ground(l, p.position().add(dir.scale(2.5)));
			if (at == null) {
				continue;
			}
			AlienProjectileEntity m = AlienProjectileEntity.of(p, "wildvine_mine");
			m.moveTo(at.x, at.y, at.z, p.getYRot() + i * 47f, 0f);
			m.colour(0x07030F).size(0.6f).held(true).speed(0).life(MINE_LIFE).damage(0f).spawn();
			mines.add(m);
			BlockState under = l.getBlockState(BlockPos.containing(at).below());
			if (!under.isAir()) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 6, 0.15, 0.05, 0.15, 0.05);
			}
		}
		MINE_LIST.put(p.getUUID(), mines);
		OmnitrixSounds.play(l, p.position(), SND_DIG, 0.9f, 1.2f);
		return true;
	}

	/** The top of the ground at {@code at} (searching 3 blocks up and down), or null over a drop. */
	@Nullable
	private static Vec3 ground(ServerLevel l, Vec3 at) {
		BlockHitResult hit = l.clip(new ClipContext(at.add(0, 1.5, 0), at.add(0, -3.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == net.minecraft.core.Direction.UP ? hit.getLocation() : null;
	}

	// ---------------------------------------------------------------- X / Shift+X

	/**
	 * X (held): Vine Swing -- a vine from his hand to the block under the crosshair (30 blocks; with nothing up there, a point ahead and
	 * above), and he swings on it while X is held (Spider-Man's rope, run on his own client: Jump reels in, Sneak pays out). Letting go
	 * in the air flings him on.
	 */
	public boolean vineSwing(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		BlockHitResult hit = blockAlong(p, look, SWING_RANGE);
		if (hit == null || hit.getLocation().y < p.getY() + 1.0) {
			Vec3 flat = look.multiply(1, 0, 1);
			flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
			BlockHitResult up = blockAlong(p, flat.add(0, Math.max(1.0, look.y * 2), 0).normalize(), SWING_RANGE);
			if (up != null && up.getLocation().y >= p.getY() + 1.5) {
				hit = up;
			}
		}
		if (hit == null || hit.getLocation().y < p.getY() + 1.0) {
			say(p, "message.projecthero.omnitrix.wildvine.no_anchor");
			return false;
		}
		if (!use(p, SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		Vec3 at = hit.getLocation();
		double rope = Math.max(3.0, Math.min(SWING_RANGE + 2.0, at.distanceTo(p.getEyePosition())));
		startLine(p, 1, at, hit.getBlockPos(), rope, SWING_MAX + 10);
		OmnitrixSounds.play(level(p), p.position(), SND_LASH, 1.0f, 0.9f);
		return true;
	}

	/**
	 * Shift+X: Root Burrow -- he sinks into the ground and moves under it as a mound for 3 s (faster, untouchable, regrowing 2 health a
	 * second, creatures lose track of him), then bursts up: 14 within 3 blocks, thrown up. Shift+X / X again comes up early. Needs
	 * ground under him.
	 */
	public boolean rootBurrow(ServerPlayer p) {
		if (!p.onGround() || p.isInWater()) {
			say(p, "message.projecthero.omnitrix.wildvine.no_ground");
			return false;
		}
		if (!use(p, BURROW)) {
			return false;
		}
		endLine(p, false);
		ServerLevel l = level(p);
		setFlag(p, F_BURROW, true);
		setTimer(p, BURROW_END, p.level().getGameTime() + BURROW_TICKS);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, BURROW_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// (no invisibility: it would hide the mound he moves as -- the creatures hunting him lose track instead, every 0.5 s)
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32))) {
			AlienStatus.loseTrack(m, p);
		}
		dirt(l, p.position(), 24);
		OmnitrixSounds.play(l, p.position(), SND_DIG, 1.1f, 0.8f);
		return true;
	}

	/** Up out of the ground: the burst ({@code burst}: 14 within 3 blocks, thrown up), the flag and the speed gone. */
	private void surface(ServerPlayer p, boolean burst) {
		setFlag(p, F_BURROW, false);
		setTimer(p, BURROW_END, 0);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, BURROW_SPEED);
		if (!burst) {
			return;
		}
		ServerLevel l = level(p);
		anim(p, ANIM_EMERGE);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, EMERGE_RADIUS)) {
			hurt(p, e, EMERGE_DAMAGE, false);
			shove(e, c, 0.4, 0.8);
		}
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x, 0.55, v.z);
		p.hurtMarked = true;
		dirt(l, c, 36);
		leaves(l, c.add(0, 0.6, 0), 14, 0.8);
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0x8A6A40, COLOUR).size(4f).thickness(0.7f).life(12).ground().spawn();
		QuirkCamera.shakeAround(l, c, 8, 0.3f, 8);
		OmnitrixSounds.play(l, c, SND_DIG, 1.2f, 0.6f);
		OmnitrixSounds.play(l, c, SND_GROW, 1.0f, 1.2f);
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/**
	 * Z: Constrict -- his vines shoot out and coil round the creature under the crosshair (12 blocks): rooted 3 s and squeezed every
	 * 0.5 s for 4 (24 in all). A boss is held only briefly but still squeezed.
	 */
	public boolean constrict(ServerPlayer p) {
		LivingEntity t = lookTarget(p, CONSTRICT_RANGE);
		if (t == null) {
			say(p, "message.projecthero.omnitrix.wildvine.no_target");
			return false;
		}
		if (!use(p, CONSTRICT)) {
			return false;
		}
		anim(p, ANIM_WRAP);
		Coil old = COILS.remove(p.getUUID());
		if (old != null) {
			old.vine().discard();
		}
		long now = p.level().getGameTime();
		AlienStatus.root(p, t, CONSTRICT_TICKS, 20);
		wrap(p, t, CONSTRICT_TICKS);
		COILS.put(p.getUUID(), new Coil(t, now, now + CONSTRICT_TICKS, vine(p, null, t, CONSTRICT_TICKS)));
		OmnitrixSounds.play(level(p), t.position(), SND_LASH, 1.0f, 0.7f);
		return true;
	}

	/** Shift+Z: vines whip out of the ground all round him -- everything within 5 blocks: 8, and rooted 2 s. */
	public boolean vineSnare(ServerPlayer p) {
		if (!use(p, SNARE)) {
			return false;
		}
		anim(p, ANIM_SNARE);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, SNARE_RADIUS)) {
			hurt(p, e, SNARE_DAMAGE, false);
			AlienStatus.root(p, e, SNARE_ROOT, 20);
			wrap(p, e, SNARE_ROOT);
			leaves(l, e.position().add(0, 0.4, 0), 6, 0.3);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xC8F080, COLOUR).size((float) SNARE_RADIUS + 1f).thickness(0.7f).life(14).ground().spawn();
		dirt(l, c, 20);
		OmnitrixSounds.play(l, c, SND_GROW, 1.0f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- V / Shift+V

	/**
	 * V: Regrowth -- the show's Florauna regrow what they lose: 10 health back at once, and slowness, poison, wither and burning shed
	 * with the old growth.
	 */
	public boolean regrowth(ServerPlayer p) {
		if (!use(p, REGROWTH)) {
			return false;
		}
		anim(p, ANIM_REGROW);
		p.heal(REGROW_HEAL);
		for (var eff : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS)) {
			p.removeEffect(eff);
		}
		p.clearFire();
		p.setTicksFrozen(0);
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.7, 0.4, 0.05);
		leaves(l, p.position().add(0, 1.0, 0), 12, 0.5);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.9f, 1.4f);
		return true;
	}

	/** Shift+V: Thicket Camouflage -- he blends into the green for 6 s: invisible, and creatures hunting him lose track (any move ends it). */
	public boolean camouflage(ServerPlayer p) {
		if (!use(p, CAMO)) {
			return false;
		}
		ServerLevel l = level(p);
		setFlag(p, F_CAMO, true);
		setTimer(p, CAMO_END, p.level().getGameTime() + CAMO_TICKS);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, CAMO_TICKS, 0, false, false, true));
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
			AlienStatus.loseTrack(m, p);
		}
		leaves(l, p.position().add(0, 1.0, 0), 20, 0.5);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.7f, 1.6f);
		return true;
	}

	private void endCamo(ServerPlayer p) {
		if (!flag(p, F_CAMO)) {
			return;
		}
		setFlag(p, F_CAMO, false);
		setTimer(p, CAMO_END, 0);
		p.removeEffect(MobEffects.INVISIBILITY);
	}

	// ---------------------------------------------------------------- C

	/**
	 * C (ultimate): Overgrowth -- he drives his roots into the ground and a thicket of vines erupts all round him: everything within 7
	 * blocks takes 22, is thrown up and is wrapped (rooted 3 s).
	 */
	public boolean overgrowth(ServerPlayer p) {
		if (!use(p, OVERGROWTH)) {
			return false;
		}
		anim(p, ANIM_OVERGROWTH);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		for (LivingEntity e : around(p, c, OVERGROWTH_RADIUS)) {
			hurt(p, e, OVERGROWTH_DAMAGE, false);
			shove(e, c, 0.3, 0.6);
			AlienStatus.root(p, e, OVERGROWTH_ROOT, 30);
			wrap(p, e, OVERGROWTH_ROOT);
		}
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0xC8F080, COLOUR).size((float) OVERGROWTH_RADIUS + 1f).thickness(1.0f).life(18).ground().spawn();
		QuirkFx.ring(l, c.add(0, 0.05, 0)).colours(0x8A6A40, 0x3F7A2A).size((float) OVERGROWTH_RADIUS * 0.6f).thickness(0.8f).life(14).ground().spawn();
		AlienProjectileEntity g = AlienProjectileEntity.of(p, "wildvine_thicket");
		g.moveTo(c.x, c.y, c.z, 0f, 0f);
		g.size((float) OVERGROWTH_RADIUS).held(true).speed(0).life(30).damage(0f).spawn();
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2 / 40, r = 1.5 + p.getRandom().nextDouble() * (OVERGROWTH_RADIUS - 1.5);
			Vec3 at = c.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
			leaves(l, at, 2, 0.2);
		}
		dirt(l, c, 50);
		QuirkCamera.shakeAround(l, c, 14, 0.45f, 12);
		OmnitrixSounds.play(l, c, SND_GROW, 1.4f, 0.6f);
		OmnitrixSounds.play(l, c, SND_DIG, 1.2f, 0.5f);
		return true;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		tickLine(p, now);
		tickCoil(p, l, now);
		tickMines(p, l, now);
		if (flag(p, F_BURROW)) {
			long end = timer(p, BURROW_END);
			if (end > 0 && now >= end || p.isInWater()) {
				surface(p, true);
			} else {
				if (now % 10 == 0) {
					p.heal(1.0f); // regrowing: 2 health a second underground
				}
				if (now % 2 == 0) {
					dirt(l, p.position(), 3);
				}
				if (now % 10 == 0) {
					for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24))) {
						AlienStatus.loseTrack(m, p); // nothing to see but a moving mound
					}
				}
				if (now % 8 == 0) {
					OmnitrixSounds.play(l, p.position(), SND_DIG, 0.4f, 0.9f);
				}
			}
		}
		long camo = timer(p, CAMO_END);
		if (camo > 0 && now >= camo) {
			endCamo(p);
		}
		if (flag(p, F_AIR) && !LINES.containsKey(p.getUUID()) && now - timer(p, AIR_AT) > 4 && (p.onGround() || p.isInWater())) {
			setFlag(p, F_AIR, false);
		}
		// Photosynthesis: in daylight under open sky (or standing in greenery) he slowly heals
		if (now % 60 == 0 && p.getHealth() < p.getMaxHealth() && sunlit(p, l)) {
			p.heal(1.0f);
			l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.3, 0.4, 0.3, 0);
		}
	}

	/** In daylight under open sky, or standing on grass / moss / leaves. */
	public static boolean sunlit(ServerPlayer p, ServerLevel l) {
		BlockState under = l.getBlockState(p.blockPosition().below());
		boolean green = under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.MOSS_BLOCK) || under.is(net.minecraft.tags.BlockTags.LEAVES);
		return green || l.isDay() && !l.isRaining() && l.canSeeSky(BlockPos.containing(p.getEyePosition()));
	}

	private void tickCoil(ServerPlayer p, ServerLevel l, long now) {
		Coil c = COILS.get(p.getUUID());
		if (c == null) {
			return;
		}
		LivingEntity t = c.target();
		if (now >= c.end() || !t.isAlive() || t.level() != p.level() || t.distanceToSqr(p) > (CONSTRICT_RANGE + 6) * (CONSTRICT_RANGE + 6)
				|| t.isOnFire()) {
			// a burning foe burns through his vines (fire weakness)
			COILS.remove(p.getUUID());
			c.vine().discard();
			return;
		}
		if ((now - c.start()) % SQUEEZE_EVERY == SQUEEZE_EVERY - 1) {
			if (hurt(p, t, SQUEEZE_DAMAGE, false)) {
				OmnitrixSounds.play(l, t.position(), SND_SQUEEZE, 0.8f, 0.9f + (now - c.start()) * 0.005f);
				leaves(l, t.position().add(0, t.getBbHeight() * 0.5, 0), 4, 0.3);
			}
		}
	}

	private void tickMines(ServerPlayer p, ServerLevel l, long now) {
		List<AlienProjectileEntity> mines = MINE_LIST.get(p.getUUID());
		if (mines == null) {
			return;
		}
		mines.removeIf(m -> !m.isAlive() || m.level() != p.level());
		for (AlienProjectileEntity m : new ArrayList<>(mines)) {
			if (m.tickCount < MINE_ARM) {
				continue;
			}
			if (m.tickCount % 20 == 0) {
				l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, m.getX(), m.getY() + 0.3, m.getZ(), 1, 0.1, 0.1, 0.1, 0);
			}
			Vec3 at = m.position().add(0, 0.3, 0);
			boolean tripped = false;
			for (LivingEntity e : around(p, at, MINE_TRIGGER)) {
				if (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == p || e instanceof Player) {
					tripped = true;
					break;
				}
			}
			if (tripped) {
				detonate(p, l, at, MINE_RADIUS, MINE_DAMAGE);
				m.discard();
				mines.remove(m);
			}
		}
		if (mines.isEmpty()) {
			MINE_LIST.remove(p.getUUID());
		}
	}

	/** A seed pod bursting at {@code at}: up to {@code damage} within {@code radius} (half at the edge), knocked back. */
	private static void detonate(ServerPlayer p, ServerLevel l, Vec3 at, double radius, float damage) {
		for (LivingEntity e : around(p, at, radius)) {
			double d = Math.sqrt(e.distanceToSqr(at));
			hurt(p, e, (float) (damage * (1.0 - 0.5 * Math.min(1.0, d / radius))), false);
			shove(e, at, 0.6, 0.35);
		}
		burstFx(l, at, (float) radius);
	}

	private static void burstFx(ServerLevel l, Vec3 at, float radius) {
		l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
		l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 0.4, at.z, 30, radius * 0.4, 0.4, radius * 0.4, 0.02);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()), at.x, at.y + 0.4, at.z, 24, radius * 0.3,
				0.3, radius * 0.3, 0.15);
		l.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 12, 0.4, 0.3, 0.4, 0.03);
		OmnitrixSounds.play(l, at, SND_BURST, 1.0f, 1.0f);
	}

	// ---------------------------------------------------------------- the line (Spidermonkey's / Ladybug's swing and zip)

	/** Whether {@code p} hangs on a swing vine (gametests). */
	public static boolean swinging(ServerPlayer p) {
		Line l = LINES.get(p.getUUID());
		return l != null && l.mode() == 1;
	}

	/** Whether {@code p} is being pulled along a zip vine (gametests). */
	public static boolean zipping(ServerPlayer p) {
		Line l = LINES.get(p.getUUID());
		return l != null && l.mode() == 2;
	}

	private static long synced(Player p, String name) {
		return Omnitrix.state(p).readyAt(ID + "." + name);
	}

	/** The vine he is on, client-safe: 0 none, 1 swing, 2 zip. */
	public static int lineMode(Player p) {
		return (int) synced(p, LINE);
	}

	/** When the current vine was cast (game time), client-safe -- a new value is a new vine. */
	public static long lineAt(Player p) {
		return synced(p, LINE_AT);
	}

	/** Where the current vine is stuck, client-safe; null without one. */
	@Nullable
	public static Vec3 hook(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		Long x = s.timers.get(ID + "." + HOOK_X), y = s.timers.get(ID + "." + HOOK_Y), z = s.timers.get(ID + "." + HOOK_Z);
		return x == null || y == null || z == null ? null : new Vec3(x / 1000.0, y / 1000.0, z / 1000.0);
	}

	/** The swing vine's rope length when cast, client-safe. */
	public static double rope(Player p) {
		return synced(p, ROPE) / 1000.0;
	}

	/** The zip's pull this tick from {@code eye} toward {@code hook} (his client runs it, the gametests check it). */
	public static Vec3 zipVelocity(Vec3 eye, Vec3 hook) {
		Vec3 to = hook.subtract(eye);
		double d = to.length();
		return d <= ZIP_ARRIVE || d < 1.0e-6 ? Vec3.ZERO : to.scale(Math.min(ZIP_SPEED, d * 0.6) / d);
	}

	private void startLine(ServerPlayer p, int mode, Vec3 at, BlockPos block, double rope, int life) {
		endLine(p, false);
		long now = p.level().getGameTime();
		AlienProjectileEntity v = vine(p, at, null, life);
		LINES.put(p.getUUID(), new Line(mode, at, block.immutable(), rope, now, v));
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(LINE), (long) mode);
		n.timers.put(key(LINE_AT), now);
		n.timers.put(key(HOOK_X), Math.round(at.x * 1000.0));
		n.timers.put(key(HOOK_Y), Math.round(at.y * 1000.0));
		n.timers.put(key(HOOK_Z), Math.round(at.z * 1000.0));
		n.timers.put(key(ROPE), Math.round(rope * 1000.0));
		n.timers.put(key(AIR_AT), now);
		n.kitFlags = (n.kitFlags & ~(F_SWING | F_ZIP)) | (mode == 1 ? F_SWING : F_ZIP) | F_AIR;
		Omnitrix.save(p, n);
		floatTolerance(p, true);
		p.resetFallDistance();
	}

	private void tickLine(ServerPlayer p, long now) {
		Line l = LINES.get(p.getUUID());
		if (l == null) {
			return;
		}
		if (!l.vine().isAlive() || l.vine().level() != p.level()) {
			endLine(p, false);
			return;
		}
		long age = now - l.start();
		if (age % 5 == 0 && p.level().getBlockState(l.block()).getCollisionShape(p.level(), l.block()).isEmpty()) {
			endLine(p, true);
			return;
		}
		double dist = l.hook().distanceTo(p.getEyePosition());
		if (l.mode() == 2) {
			if (dist < ZIP_ARRIVE + 0.4 || age > ZIP_MAX) {
				endLine(p, false);
				return;
			}
		} else if (dist > l.rope() + SWING_SLACK_BREAK || age > SWING_MAX || age > 10 && p.onGround()) {
			endLine(p, age <= 10 || !p.onGround());
			return;
		}
		p.resetFallDistance();
		floatTolerance(p, true);
	}

	/** Lets go of the vine (the fling is his client's: it carries the arc's speed on). */
	public void endLine(ServerPlayer p, boolean sound) {
		Line l = LINES.remove(p.getUUID());
		if (l == null) {
			return;
		}
		l.vine().discard();
		floatTolerance(p, false);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : LINE_KEYS) {
			n.timers.remove(key(k));
		}
		n.kitFlags &= ~(F_SWING | F_ZIP);
		n.kitFlags |= F_AIR; // no fall damage till he lands
		n.timers.put(key(AIR_AT), now);
		Omnitrix.save(p, n);
		p.resetFallDistance();
		if (sound) {
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CAVE_VINES_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
		}
	}

	/** Vanilla kicks a player airborne with no downward motion ("flying is not enabled") -- a swing looks just like that (Spidermonkey's fix). */
	private static void floatTolerance(ServerPlayer p, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.getAbilities().mayfly || p.getAbilities().flying) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
			return;
		}
		if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p) && !com.projecthero.mod.power.ThorPowers.isFlying(p)
				&& !p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_FLYING, false)
				&& !p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * A vine from his hand for {@code life} ticks: to the fixed point {@code at}, or, {@code to} given, to that creature wherever it goes
	 * (colour = its id + 1). Drawn by {@code WildvineClient}.
	 */
	private static AlienProjectileEntity vine(ServerPlayer p, @Nullable Vec3 at, @Nullable LivingEntity to, int life) {
		AlienProjectileEntity s = AlienProjectileEntity.of(p, "wildvine_vine");
		Vec3 end = to == null ? at : to.position().add(0, to.getBbHeight() * 0.55, 0);
		s.moveTo(end.x, end.y, end.z, 0f, 0f);
		s.colour(to == null ? 0 : to.getId() + 1).size((float) Math.max(0.6, p.position().distanceTo(end) / 3.0)).held(true).speed(0).life(life)
				.damage(0f).spawn();
		return s;
	}

	/** Vines coiled round {@code e} for {@code ticks} (a boss a quarter, like its root). */
	private static void wrap(ServerPlayer p, LivingEntity e, int ticks) {
		int t = AlienStatus.isBoss(e) ? Math.max(5, ticks / 4) : ticks;
		AlienProjectileEntity w = AlienProjectileEntity.of(p, "wildvine_wrap");
		w.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
		w.colour(e.getId() + 1).size(Math.max(0.6f, e.getBbHeight() * 0.6f)).held(true).speed(0).life(t).damage(0f).spawn();
	}

	@Nullable
	private static BlockHitResult blockAlong(ServerPlayer p, Vec3 dir, double range) {
		Vec3 eye = p.getEyePosition();
		HitResult hit = p.level().clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.BLOCK ? (BlockHitResult) hit : null;
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	private static Vec3 flat(Player p) {
		return Vec3.directionFromRotation(0, p.getYRot());
	}

	private static void leaves(ServerLevel l, Vec3 at, int n, double spread) {
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()), at.x, at.y, at.z, n, spread, spread, spread,
				0.1);
	}

	private static void dirt(ServerLevel l, Vec3 c, int n) {
		BlockState under = l.getBlockState(BlockPos.containing(c).below());
		BlockState st = under.isAir() ? Blocks.DIRT.defaultBlockState() : under;
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), c.x, c.y + 0.1, c.z, n, 0.5, 0.1, 0.5, 0.15);
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) && (flag(p, F_AIR) || flag(p, F_SWING) || flag(p, F_ZIP))) {
			return false; // off his own vines
		}
		if (flag(p, F_BURROW) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_FIRE)) {
			return false; // under the ground (fire still finds a plant)
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return amount * FIRE_WEAKNESS; // a plant burns
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2)); // frost stiffens his vines
			return amount * FROST_WEAKNESS;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return amount * 0.5f; // a pliant body
		}
		return amount;
	}

	/** Gametests: whether a Constrict coil is running for {@code p}. */
	public static boolean coiling(ServerPlayer p) {
		return COILS.containsKey(p.getUUID());
	}

	/** Gametests: how many seed mines {@code p} has planted and still live. */
	public static int mineCount(ServerPlayer p) {
		List<AlienProjectileEntity> m = MINE_LIST.get(p.getUUID());
		return m == null ? 0 : (int) m.stream().filter(Entity::isAlive).count();
	}

	/** Gametests: the seed mines (to step a creature onto one). */
	public static List<AlienProjectileEntity> mines(ServerPlayer p) {
		List<AlienProjectileEntity> m = MINE_LIST.get(p.getUUID());
		return m == null ? List.of() : List.copyOf(m);
	}

	/** Gametests: pull the burrow's timer in so the next tick brings him up. */
	public void endBurrowNow(ServerPlayer p) {
		setTimer(p, BURROW_END, p.level().getGameTime());
	}
}
