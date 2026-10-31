package com.projecthero.mod.omnitrix.alien.diamondhead;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Diamondhead, the Petrosapien -- a body of living green crystal with a pointed crystal head and shard ridges down
 * his back and shoulders. Heavy armour, a little slow; a share of the arrows and projectiles that reach him glance back the
 * way they came. Every construct he raises is temporary {@link DiamondheadBlocks#CRYSTAL} (through {@code TempBlocks}).
 *
 * <pre>
 *   R        Shard Volley      a fan of 5 crystal shards (4 each)             Shift+R  Crystal Spear    one huge spear (18, knockback)
 *   G        Blade Arm         toggle: the arm becomes a crystal blade (+6)  Shift+G  Spike Line       crystal spikes erupt in a 12-block line (16, launch)
 *   X        Crystal Slide     toggle: ride a crystal ramp along your look   Shift+X  Crystal Vault    a pillar shoots up under you
 *   Z        Crystal Eruption  rings of crystal spikes burst out of the ground all round you (radius 6; 12, launch, slow)
 *            Shift+Z  Diamond Barrage  ultimate: a 2 s storm of shards where you look (5 each)
 *   V        Crystal Wall      a 5x3 wall in front (8 s)                     Shift+V  Crystal Dome     a dome over you (7 s)
 *   C        Regrowth          3 s rooted: crystal grows back over the cracks (+9 health, half damage meanwhile)
 *            Shift+C  Crystal Armour   6 s: half damage, every projectile reflected
 *   (v0.17.2, user: "put diamondheads crystal wall and dome abilities on V and put the stronger move on Z and the ultimate
 *   on Shift+Z": Spike Line from Shift+G, Diamond Barrage from C, Wall / Dome from Z, Regrowth from V)
 *   (v0.17.2, user: "Make Shift+G the spike Line ability / Make Z an aoe spike ability": Spike Line back on Shift+G, the new
 *   Crystal Eruption on Z)
 *   Attack   Crystal Edge (blade: the slash sweeps)   Use on a block (empty hand)   a crystal block for 30 s
 * </pre>
 */
public final class DiamondheadKit extends AlienKit {
	public static final String ID = "diamondhead";
	public static final int COLOUR = 0x4FE0C8;
	public static final int DEEP = 0x1E9A6A;

	public static final int ANIM_VOLLEY = 1;
	public static final int ANIM_SPEAR = 2;
	public static final int ANIM_BLADE = 3;
	public static final int ANIM_SPIKES = 4;
	public static final int ANIM_SLIDE = 5;
	public static final int ANIM_VAULT = 6;
	public static final int ANIM_WALL = 7;
	public static final int ANIM_DOME = 8;
	public static final int ANIM_BARRAGE = 9;
	public static final int ANIM_ARMOUR = 10;
	public static final int ANIM_SLASH = 11;
	public static final int ANIM_PLACE = 12;

	public static final int F_BLADE = 1;
	public static final int F_ARMOUR = 2;
	public static final int F_SLIDING = 4;

	public static final String VOLLEY = "shard_volley";
	public static final String SPEAR = "crystal_spear";
	public static final String BLADE = "blade_arm";
	public static final String SPIKES = "spike_line";
	public static final String SLIDE = "crystal_slide";
	public static final String VAULT = "crystal_vault";
	public static final String WALL = "crystal_wall";
	public static final String DOME = "crystal_dome";
	public static final String BARRAGE = "diamond_barrage";
	public static final String ARMOUR = "crystal_armour";
	public static final String EDGE = "crystal_edge";
	public static final String PLACE = "crystal_block";
	/** v0.17.2: Z -- the Crystal Eruption. */
	public static final String ERUPT = "crystal_eruption";
	public static final int ANIM_ERUPT = 14;

	// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): was 3
	public static final float SHARD_DAMAGE = 4f;
	public static final float SPEAR_DAMAGE = 18f; // v0.17.2: was 12
	/** v0.17.2: the Diamond Barrage's falling shards (was SHARD_DAMAGE, 3): about 26 a target over its 2 s. */
	public static final float BARRAGE_SHARD_DAMAGE = 5f;
	public static final double BLADE_BONUS = 6.0;
	public static final float SPIKE_DAMAGE = 16f; // v0.17.2: was 6
	public static final int SPIKE_LENGTH = 12;
	/** v0.17.2: the Crystal Eruption -- damage (a Z "stronger move", on par with the elemental powers'), reach and cooldown. */
	public static final float ERUPT_DAMAGE = 12f;
	public static final double ERUPT_RADIUS = 6.0;
	/** The eruption's rings, one a tick, from {@link #ERUPT_INNER} out to {@link #ERUPT_RADIUS}. */
	public static final int ERUPT_RINGS = 5;
	public static final double ERUPT_INNER = 1.5;
	public static final int ERUPT_COOLDOWN = 180;
	/** Slowness II on whatever it throws (a boss only a quarter of it). */
	public static final int ERUPT_SLOW_TICKS = 60;
	public static final int WALL_TICKS = 160;
	public static final int DOME_TICKS = 140;
	/** v0.17.2: how long each crystal of the slide's ramp stays behind him (ticks). */
	public static final int SLIDE_TICKS = 40;
	public static final int PLACE_TICKS = 600;
	public static final int BARRAGE_TICKS = 40;
	public static final int ARMOUR_TICKS = 120;
	public static final float ARMOUR_FACTOR = 0.5f;
	/** The passive: the share of incoming projectiles that glance back. */
	public static final float REFLECT_CHANCE = 0.4f;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A crystal shard flung: a quick whoosh and a short crystal crack. */
	public static final OmnitrixSfx.Cue SND_SHARD = OmnitrixSfx.cue("diamondhead_shard")
			.layer(SoundEvents.TRIDENT_THROW, 0.28f, 1.5f)
			.layer(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.35f, 1.3f)
			.gap(3);
	/** Crystal growing: a dense crunch, no ring. */
	public static final OmnitrixSfx.Cue SND_GROW = OmnitrixSfx.cue("diamondhead_grow")
			.layer(SoundEvents.CALCITE_PLACE, 0.45f, 0.7f)
			.layer(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.25f, 0.8f)
			.layer(SoundEvents.DEEPSLATE_PLACE, 0.25f, 0.8f, 1);
	/** Crystal shattering: a short, low glass crash and a ceramic crunch. */
	public static final OmnitrixSfx.Cue SND_SHATTER = OmnitrixSfx.cue("diamondhead_shatter")
			.layer(SoundEvents.GLASS_BREAK, 0.3f, 0.8f)
			.layer(SoundEvents.DECORATED_POT_SHATTER, 0.35f, 1.0f)
			.layer(SoundEvents.CALCITE_BREAK, 0.3f, 0.9f)
			.gap(3);
	/** The arm blade forming: a crystal crunch and a swipe. */
	public static final OmnitrixSfx.Cue SND_BLADE = OmnitrixSfx.cue("diamondhead_blade")
			.layer(SoundEvents.CALCITE_PLACE, 0.35f, 0.6f)
			.layer(SoundEvents.POINTED_DRIPSTONE_BREAK, 0.3f, 0.7f)
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.25f, 1.3f);

	private static final ResourceLocation BLADE_DAMAGE_ID = PowerToggles.id("omnitrix_diamondhead_blade");
	private static final ResourceLocation BLADE_REACH_ID = PowerToggles.id("omnitrix_diamondhead_blade_reach");
	/** An entity tag: this projectile was already judged by the reflection passive (once per flight). */
	public static final String SEEN_TAG = "projecthero.diamondhead_seen";
	static final DustParticleOptions SPARKLE = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.82f), 0.9f);

	/** player -> the Spike Line running. */
	private static final Map<UUID, SpikeLine> LINES = new ConcurrentHashMap<>();
	/** player -> where the Diamond Barrage rains. */
	private static final Map<UUID, Storm> STORMS = new ConcurrentHashMap<>();
	/** player -> the Crystal Eruption spreading out from him. */
	private static final Map<UUID, Eruption> ERUPTIONS = new ConcurrentHashMap<>();

	private record SpikeLine(ResourceKey<Level> dim, Vec3 from, Vec3 dir, long start, Set<Integer> hit) {
	}

	private record Storm(ResourceKey<Level> dim, Vec3 at) {
	}

	private record Eruption(ResourceKey<Level> dim, Vec3 at, long start, Set<Integer> hit) {
	}

	private DiamondheadKit() {
	}

	public static void register() {
		DiamondheadBlocks.initialize();
		Aliens.register(new DiamondheadKit());
		AlienProjectileEntity.trail("diamondhead_shard", SPARKLE);
		AlienProjectileEntity.trail("diamondhead_spear", SPARKLE);
		AlienProjectileEntity.onHit("diamondhead_shard", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.1);
				OmnitrixSounds.play(l, at, SND_SHATTER, 0.4f, 1.6f + l.random.nextFloat() * 0.3f);
			}
		});
		AlienProjectileEntity.onHit("diamondhead_spear", (proj, hit) -> {
			if (proj.level() instanceof ServerLevel l) {
				Vec3 at = hit.getLocation();
				QuirkFx.shards(l, at).block(crystal()).size(1.6f).count(16).life(26).spawn();
				OmnitrixSounds.play(l, at, SND_SHATTER, 1.0f, 0.9f);
				if (hit instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
					shove(le, proj.position().subtract(proj.getDeltaMovement()), 1.3, 0.35);
				}
			}
		});
	}

	public static void clearSessionState() {
		LINES.clear();
		STORMS.clear();
		ERUPTIONS.clear();
	}

	static BlockState crystal() {
		return DiamondheadBlocks.CRYSTAL.defaultBlockState();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(34f).speed(0.88f).scale(1.1f).armor(12f, 4f).knockbackResist(0.5f).attack(4f).step(0.6f).safeFall(3f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(VOLLEY, AbilitySlot.SLOT_1, 24), AlienMove.shift(SPEAR, AbilitySlot.SLOT_1, 120),
				// v0.17.2 (user: "Make Shift+G the spike Line ability / Make Z an aoe spike ability")
				AlienMove.tap(BLADE, AbilitySlot.SLOT_2, 20), AlienMove.shift(SPIKES, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(SLIDE, AbilitySlot.SLOT_3, 80), AlienMove.shift(VAULT, AbilitySlot.SLOT_3, 140),
				// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): the
				// Barrage's cooldown 500 -> 600, the ultimate's 30 s
				AlienMove.tap(ERUPT, AbilitySlot.SLOT_4, ERUPT_COOLDOWN), AlienMove.shift(BARRAGE, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(WALL, AbilitySlot.SLOT_5, 200), AlienMove.shift(DOME, AbilitySlot.SLOT_5, 500),
				AlienMove.tap(REGROW, AbilitySlot.SLOT_6, 400), AlienMove.shift(ARMOUR, AbilitySlot.SLOT_6, 400),
				AlienMove.vanilla(EDGE, "Attack", 0), AlienMove.vanilla(PLACE, "Use on a block (empty hand)", 4));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 1.0f);
		QuirkFx.shards(l, p.position().add(0, 1, 0)).block(crystal()).size(1.4f).count(14).life(16).reassemble().spawn();
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		setBlade(p, false);
		endSlide(p);
		setFlag(p, F_ARMOUR, false);
		LINES.remove(p.getUUID());
		STORMS.remove(p.getUUID());
		ERUPTIONS.remove(p.getUUID());
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		tickV(p, l, now);
		// the passive: projectiles reaching him may glance back
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(2.2),
				pr -> pr.getOwner() != p && !pr.getTags().contains(SEEN_TAG))) {
			Vec3 to = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(pr.position());
			if (pr.getDeltaMovement().lengthSqr() < 0.01 || pr.getDeltaMovement().dot(to) <= 0) {
				continue;
			}
			pr.addTag(SEEN_TAG);
			tryReflect(p, pr, p.getRandom().nextFloat());
		}
		if (flag(p, F_BLADE)) {
			// keep the blade's modifiers on (a relog drops transient modifiers)
			if (now % 20 == 0) {
				applyBladeModifiers(p);
			}
		}
		long armour = timer(p, "armour_end");
		if (armour > 0) {
			if (now >= armour) {
				setTimer(p, "armour_end", 0);
				setFlag(p, F_ARMOUR, false);
				OmnitrixSounds.play(l, p.position(), SND_SHATTER, 0.7f, 1.2f);
			} else if (now % 5 == 0) {
				l.sendParticles(SPARKLE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.5, 0.8, 0.5, 0.0);
			}
		}
		slideTick(p, l, now);
		tickSpikes(p, l, now);
		tickStorm(p, l, now);
		tickEruption(p, l, now);
	}

	/** The reflection passive on one projectile: {@code roll} under the chance (all of them under Crystal Armour) sends it back. */
	public boolean tryReflect(ServerPlayer p, Projectile pr, float roll) {
		float chance = flag(p, F_ARMOUR) ? 1.0f : REFLECT_CHANCE;
		if (roll >= chance) {
			return false;
		}
		Vec3 v = pr.getDeltaMovement();
		double speed = Math.max(0.6, v.length()) * 1.1;
		Entity owner = pr.getOwner();
		Vec3 back = owner != null && owner.isAlive() && owner.distanceToSqr(p) < 48 * 48
				? owner.getEyePosition().subtract(pr.position()).normalize()
				: v.scale(-1).normalize();
		pr.setDeltaMovement(back.scale(speed));
		pr.setOwner(p);
		pr.hurtMarked = true;
		pr.hasImpulse = true;
		ServerLevel l = level(p);
		l.sendParticles(SPARKLE, pr.getX(), pr.getY(), pr.getZ(), 8, 0.15, 0.15, 0.15, 0.05);
		OmnitrixSounds.play(l, pr.position(), SND_SHARD, 0.8f, 1.8f);
		return true;
	}

	private void tickSpikes(ServerPlayer p, ServerLevel l, long now) {
		SpikeLine line = LINES.get(p.getUUID());
		if (line == null) {
			return;
		}
		int step = (int) (now - line.start());
		if (step >= SPIKE_LENGTH || line.dim() != l.dimension()) {
			LINES.remove(p.getUUID());
			return;
		}
		Vec3 at = line.from().add(line.dir().scale(1.5 + step));
		BlockPos ground = groundUnder(l, BlockPos.containing(at));
		if (ground == null) {
			return;
		}
		Vec3 base = Vec3.atBottomCenterOf(ground.above());
		QuirkFx.spike(l, base, new Vec3(0, 1, 0)).block(crystal()).colours(0xC8FFF0, COLOUR).size(1.8f + step * 0.06f).size2(0.45f).life(36).spawn();
		if (step % 2 == 0) {
			OmnitrixSounds.play(l, base, SND_GROW, 0.6f, 1.2f + step * 0.03f);
		}
		for (LivingEntity e : around(p, base.add(0, 0.8, 0), 1.6)) {
			if (line.hit().add(e.getId())) {
				e.invulnerableTime = 0;
				hurt(p, e, SPIKE_DAMAGE, false);
				e.push(0, 0.75 * Math.max(0.3, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)), 0);
				e.hurtMarked = true;
			}
		}
	}

	/** The highest solid block at or a few below / above {@code pos} with room over it, or null. */
	private static BlockPos groundUnder(ServerLevel l, BlockPos pos) {
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos b = pos.offset(0, dy, 0);
			if (!l.getBlockState(b).getCollisionShape(l, b).isEmpty() && l.getBlockState(b.above()).getCollisionShape(l, b.above()).isEmpty()) {
				return b;
			}
		}
		return null;
	}

	private void tickStorm(ServerPlayer p, ServerLevel l, long now) {
		Storm st = STORMS.get(p.getUUID());
		if (st == null) {
			return;
		}
		long end = timer(p, "storm_end");
		if (now >= end || st.dim() != l.dimension()) {
			STORMS.remove(p.getUUID());
			setTimer(p, "storm_end", 0);
			return;
		}
		for (int k = 0; k < 2; k++) {
			double a = p.getRandom().nextDouble() * Math.PI * 2;
			double r = Math.sqrt(p.getRandom().nextDouble()) * 4.0;
			Vec3 from = st.at().add(Math.cos(a) * r, 9 + p.getRandom().nextDouble() * 2, Math.sin(a) * r);
			AlienProjectileEntity shard = AlienProjectileEntity.of(p, "diamondhead_shard").colour(COLOUR).size(0.3f).damage(BARRAGE_SHARD_DAMAGE).speed(1.6)
					.life(30).direction(new Vec3((p.getRandom().nextDouble() - 0.5) * 0.2, -1, (p.getRandom().nextDouble() - 0.5) * 0.2));
			shard.moveTo(from.x, from.y, from.z);
			shard.spawn();
		}
		if (now % 6 == 0) {
			OmnitrixSounds.play(l, st.at(), SND_SHARD, 0.7f, 0.8f + p.getRandom().nextFloat() * 0.4f);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					crystalSpear(p);
				} else {
					shardVolley(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					spikeLine(p);
				} else {
					bladeArm(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					vault(p);
				} else {
					slide(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					barrage(p);
				} else {
					eruption(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					dome(p);
				} else {
					wall(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					crystalArmour(p);
				} else {
					regrow(p);
				}
			}
			default -> {
			}
		}
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 h = new Vec3(l.x, 0, l.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	public boolean shardVolley(ServerPlayer p) {
		if (!use(p, VOLLEY)) {
			return false;
		}
		anim(p, ANIM_VOLLEY);
		Vec3 look = p.getLookAngle();
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		for (int i = -2; i <= 2; i++) {
			AlienProjectileEntity.of(p, "diamondhead_shard").colour(COLOUR).size(0.28f).damage(SHARD_DAMAGE).speed(2.0).life(40)
					.direction(look.add(side.scale(i * 0.09)).add(0, Math.abs(i) * -0.015, 0)).spawn();
		}
		OmnitrixSounds.play(level(p), p.position(), SND_SHARD, 1.0f, 1.0f + p.getRandom().nextFloat() * 0.2f);
		return true;
	}

	public boolean crystalSpear(ServerPlayer p) {
		if (!use(p, SPEAR)) {
			return false;
		}
		anim(p, ANIM_SPEAR);
		AlienProjectileEntity.of(p, "diamondhead_spear").colour(0xA8FFE8).size(0.7f).damage(SPEAR_DAMAGE).speed(2.4).life(50).spawn();
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.9f, 0.7f);
		OmnitrixSounds.play(l, p.position(), SND_SHARD, 1.0f, 0.6f);
		return true;
	}

	public boolean bladeArm(ServerPlayer p) {
		if (!use(p, BLADE)) {
			return false;
		}
		boolean on = !flag(p, F_BLADE);
		setBlade(p, on);
		anim(p, ANIM_BLADE);
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), on ? SND_BLADE : SND_SHATTER, 0.9f, on ? 1.0f : 1.4f);
		l.sendParticles(SPARKLE, p.getX(), p.getY() + 1.1, p.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
		return true;
	}

	private void setBlade(ServerPlayer p, boolean on) {
		setFlag(p, F_BLADE, on);
		if (on) {
			applyBladeModifiers(p);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, BLADE_DAMAGE_ID);
			PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, BLADE_REACH_ID);
		}
	}

	private static void applyBladeModifiers(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, BLADE_DAMAGE_ID, BLADE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, BLADE_REACH_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
	}

	public boolean spikeLine(ServerPlayer p) {
		if (!use(p, SPIKES)) {
			return false;
		}
		anim(p, ANIM_SPIKES);
		LINES.put(p.getUUID(), new SpikeLine(p.level().dimension(), p.position(), flatLook(p), p.level().getGameTime(), new HashSet<>()));
		QuirkCamera.shakeAround(level(p), p.position(), 10, 0.2f, 8);
		return true;
	}

	/**
	 * Z: the Crystal Eruption -- v0.17.2 (user: "Make Z an aoe spike ability"). He slams a fist into the ground and rings of
	 * crystal spikes burst up all round him, one ring a tick, out to {@link #ERUPT_RADIUS}: whatever stands in them takes
	 * {@link #ERUPT_DAMAGE} (once), is thrown up and out and is slowed.
	 */
	public boolean eruption(ServerPlayer p) {
		if (!use(p, ERUPT)) {
			return false;
		}
		anim(p, ANIM_ERUPT);
		ServerLevel l = level(p);
		ERUPTIONS.put(p.getUUID(), new Eruption(l.dimension(), p.position(), l.getGameTime(), new HashSet<>()));
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xE0FFF6, COLOUR).size((float) ERUPT_RADIUS + 0.5f).thickness(0.7f).life(12).ground().spawn();
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.6, 0.05, 0.6, 0.25);
		OmnitrixSounds.play(l, p.position(), SND_SHATTER, 1.1f, 0.6f);
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.2f, 0.7f);
		QuirkCamera.shakeAround(l, p.position(), 12, 0.35f, 10);
		return true;
	}

	/** The radius of the eruption's {@code ring}-th ring. */
	public static double eruptionRing(int ring) {
		return ERUPT_INNER + (ERUPT_RADIUS - ERUPT_INNER) * ring / (ERUPT_RINGS - 1);
	}

	private void tickEruption(ServerPlayer p, ServerLevel l, long now) {
		Eruption er = ERUPTIONS.get(p.getUUID());
		if (er == null) {
			return;
		}
		int ring = (int) (now - er.start());
		if (ring >= ERUPT_RINGS || er.dim() != l.dimension()) {
			ERUPTIONS.remove(p.getUUID());
			return;
		}
		if (ring < 0) {
			return;
		}
		double r = eruptionRing(ring);
		int n = Math.max(6, (int) Math.round(Math.PI * 2 * r / 1.35));
		// each ring turned half a step against the last, so the spikes stagger like teeth
		double turn = (ring % 2) * Math.PI / n;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + turn;
			double jr = r + (l.random.nextDouble() - 0.5) * 0.5;
			Vec3 at = er.at().add(Math.cos(a) * jr, 0, Math.sin(a) * jr);
			BlockPos ground = groundUnder(l, BlockPos.containing(at.x, er.at().y - 0.5, at.z));
			if (ground == null) {
				continue;
			}
			Vec3 base = new Vec3(at.x, ground.getY() + 1.0, at.z);
			// leaning out from him, tallest in the middle rings
			Vec3 lean = new Vec3(Math.cos(a) * 0.38, 1, Math.sin(a) * 0.38).normalize();
			float tall = 1.5f + (float) Math.sin(Math.PI * (ring + 1) / (ERUPT_RINGS + 1)) * 1.1f + l.random.nextFloat() * 0.5f;
			QuirkFx.spike(l, base, lean).block(crystal()).colours(0xC8FFF0, COLOUR).size(tall).size2(0.42f).life(30 - ring * 2).spawn();
			if (i % 3 == 0) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), base.x, base.y + 0.1, base.z, 3, 0.2, 0.05, 0.2, 0.15);
				l.sendParticles(SPARKLE, base.x, base.y + 0.6, base.z, 1, 0.2, 0.4, 0.2, 0.0);
			}
		}
		OmnitrixSounds.play(l, er.at(), SND_GROW, 0.8f, 1.0f + ring * 0.12f);
		// this ring and everything inside it not yet thrown
		for (LivingEntity e : around(p, er.at(), r + 0.9)) {
			if (Math.abs(e.getY() - er.at().y) > 3.0 || !er.hit().add(e.getId())) {
				continue;
			}
			e.invulnerableTime = 0;
			hurt(p, e, ERUPT_DAMAGE, false);
			double resist = Math.max(0.3, 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			Vec3 out = new Vec3(e.getX() - er.at().x, 0, e.getZ() - er.at().z);
			out = out.lengthSqr() < 1.0e-4 ? Vec3.ZERO : out.normalize().scale(0.45 * resist);
			e.push(out.x, 0.7 * resist, out.z);
			e.hurtMarked = true;
			int slow = com.projecthero.mod.omnitrix.alien.AlienStatus.isBoss(e) ? ERUPT_SLOW_TICKS / 4 : ERUPT_SLOW_TICKS;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1), p);
		}
	}

	/** Steps up a block on the slide without stopping (the client drives the ride). */
	private static final ResourceLocation SLIDE_STEP = PowerToggles.id("omnitrix_diamondhead_slide_step");

	/**
	 * X: the Crystal Slide -- v0.17.2 (user: "make diamondheads crystal slide ability work similar to cryokinesis ice slide"):
	 * Cryokinesis' Ice Slide in crystal. A toggle (X again steps off): his own client rides him along his look (climbing when
	 * he looks up, riding down when he looks down -- {@code CryokinesisClient.ride}) while a 3-wide crystal ramp grows under
	 * him and back along the way he came ({@code CryokinesisHandlers.layRamp}), shattering 2 s behind him. Returns 1 when it
	 * starts, 0 otherwise.
	 */
	public int slide(ServerPlayer p) {
		if (flag(p, F_SLIDING)) {
			endSlide(p);
			return 0;
		}
		if (p.getAbilities().flying || p.isPassenger() || !use(p, SLIDE)) {
			return 0;
		}
		anim(p, ANIM_SLIDE);
		setFlag(p, F_SLIDING, true);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SLIDE_STEP, 0.65, AttributeModifier.Operation.ADD_VALUE);
		if (p.onGround()) {
			// a little hop so the first crystal goes in under him
			p.setDeltaMovement(p.getDeltaMovement().x, 0.25, p.getDeltaMovement().z);
			p.hurtMarked = true;
		}
		p.resetFallDistance();
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xE0FFF6, COLOUR).size(0.35f).size2(8f).life(16).vec(new Vec3(0, 0.1, 0)).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 0.9f, 1.3f);
		return 1;
	}

	/** Steps off the slide; 3 s of no fall damage after. */
	public void endSlide(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SLIDE_STEP);
		if (flag(p, F_SLIDING)) {
			setFlag(p, F_SLIDING, false);
			setTimer(p, "slide_grace", p.level().getGameTime() + 60);
		}
	}

	/** The ride's server half: the ramp under him, the sparkle, the crystal steps -- and no fall damage meanwhile. */
	private void slideTick(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_SLIDING)) {
			if (now < timer(p, "slide_grace")) {
				p.resetFallDistance();
			}
			return;
		}
		if (!p.isAlive() || p.getAbilities().flying || p.isPassenger()) {
			endSlide(p);
			return;
		}
		p.resetFallDistance();
		Vec3 mv = p.getKnownMovement();
		Vec3 flat = new Vec3(mv.x, 0, mv.z);
		Vec3 dir = flat.lengthSqr() > 1.0e-4 ? flat.normalize() : flatLook(p);
		// as the Ice Slide: nothing while falling, nothing where he is about to be (the server sees him a tick late), and
		// looking down rides down instead of laying crystal under him
		if (p.getXRot() <= com.projecthero.mod.hero.power.p09.CryokinesisHandlers.DESCEND_PITCH && mv.y > -0.15) {
			net.minecraft.world.phys.AABB ahead = p.getBoundingBox().expandTowards(mv.x * 3, Math.min(0, mv.y * 3), mv.z * 3).inflate(0.3, 0.0, 0.3);
			com.projecthero.mod.hero.power.p09.CryokinesisHandlers.layRamp(l, p.position(), dir, mv.length(), ahead,
					cell -> TempBlocks.place(l, cell, crystal(), SLIDE_TICKS));
		}
		if (now % 2 == 0) {
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.05);
			l.sendParticles(SPARKLE, p.getX(), p.getY() + 0.15, p.getZ(), 1, 0.3, 0.05, 0.3, 0.0);
		}
		if (now % 6 == 0) {
			l.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.3f);
		}
	}

	/** Shift+X: a crystal pillar shoots up under you. */
	public int vault(ServerPlayer p) {
		ServerLevel l = level(p);
		BlockPos base = p.blockPosition();
		if (!l.noCollision(p, p.getBoundingBox().move(0, 3.1, 0))) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.diamondhead.no_room")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return 0;
		}
		if (!use(p, VAULT)) {
			return 0;
		}
		anim(p, ANIM_VAULT);
		p.teleportTo(p.getX(), p.getY() + 3.05, p.getZ());
		p.setDeltaMovement(p.getDeltaMovement().x, 0.6, p.getDeltaMovement().z);
		p.hurtMarked = true;
		p.resetFallDistance();
		int placed = 0;
		for (int y = 0; y < 3; y++) {
			if (TempBlocks.place(l, base.above(y), crystal(), 120)) {
				placed++;
			}
		}
		QuirkFx.spike(l, Vec3.atBottomCenterOf(base), new Vec3(0, 1, 0)).block(crystal()).colours(0xC8FFF0, COLOUR).size(3.2f).size2(0.7f).life(30).spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 0.8f);
		return placed;
	}

	/** Z: a 5-wide, 3-high wall of crystal 2.5 blocks ahead. */
	public int wall(ServerPlayer p) {
		if (!use(p, WALL)) {
			return 0;
		}
		anim(p, ANIM_WALL);
		ServerLevel l = level(p);
		Vec3 f = flatLook(p);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		Vec3 c = p.position().add(f.scale(2.5));
		int placed = 0;
		for (int w = -2; w <= 2; w++) {
			for (int h = 0; h < 3; h++) {
				BlockPos at = BlockPos.containing(c.x + side.x * w, p.getY() + h + 0.01, c.z + side.z * w);
				if (TempBlocks.place(l, at, crystal(), WALL_TICKS)) {
					placed++;
				}
			}
		}
		QuirkFx.shards(l, c.add(0, 1.5, 0)).block(crystal()).size(2.2f).count(18).life(16).reassemble().spawn();
		OmnitrixSounds.play(l, c, SND_GROW, 1.1f, 0.9f);
		return placed;
	}

	/** Shift+Z: a dome of crystal (radius 3) over you. */
	public int dome(ServerPlayer p) {
		if (!use(p, DOME)) {
			return 0;
		}
		anim(p, ANIM_DOME);
		ServerLevel l = level(p);
		BlockPos c = p.blockPosition();
		int placed = 0;
		for (int x = -3; x <= 3; x++) {
			for (int y = 0; y <= 3; y++) {
				for (int z = -3; z <= 3; z++) {
					double d = Math.sqrt(x * x + y * y * 1.1 + z * z);
					if (d >= 2.5 && d < 3.5 && TempBlocks.place(l, c.offset(x, y, z), crystal(), DOME_TICKS)) {
						placed++;
					}
				}
			}
		}
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xE0FFF6, COLOUR).size(3.5f).thickness(0.6f).life(14).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.2f, 0.7f);
		return placed;
	}

	/** C: a storm of shards on where you look (up to 24 blocks away) for 2 s. */
	public boolean barrage(ServerPlayer p) {
		if (!use(p, BARRAGE)) {
			return false;
		}
		anim(p, ANIM_BARRAGE);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		var hit = l.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(p.getLookAngle().scale(24)),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		LivingEntity t = lookTarget(p, 24);
		Vec3 at = t != null ? t.position() : hit.getLocation();
		STORMS.put(p.getUUID(), new Storm(p.level().dimension(), at));
		setTimer(p, "storm_end", p.level().getGameTime() + BARRAGE_TICKS);
		QuirkFx.ring(l, at.add(0, 0.1, 0)).colours(0xE0FFF6, COLOUR).size(4.5f).thickness(0.4f).life(BARRAGE_TICKS).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 1.5f);
		return true;
	}

	public boolean crystalArmour(ServerPlayer p) {
		if (!use(p, ARMOUR)) {
			return false;
		}
		anim(p, ANIM_ARMOUR);
		setFlag(p, F_ARMOUR, true);
		setTimer(p, "armour_end", p.level().getGameTime() + ARMOUR_TICKS);
		ServerLevel l = level(p);
		QuirkFx.shards(l, p.position().add(0, 1, 0)).block(crystal()).size(1.8f).count(20).life(18).reassemble().spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 1.1f);
		return true;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (!(target instanceof LivingEntity le)) {
			return false;
		}
		anim(p, ANIM_SLASH);
		ServerLevel l = level(p);
		l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, crystal()), le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 8, 0.2, 0.3, 0.2,
				0.1);
		if (flag(p, F_BLADE)) {
			// the blade's slash sweeps what stands beside the target
			for (LivingEntity e : around(p, le.position(), 2.5)) {
				if (e != le) {
					e.invulnerableTime = 0;
					hurt(p, e, 4f, false);
				}
			}
			l.sendParticles(ParticleTypes.SWEEP_ATTACK, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 1, 0, 0, 0, 0);
			OmnitrixSounds.play(l, le.position(), SND_BLADE, 0.6f, 1.3f);
		}
		return false;
	}

	@Override
	public InteractionResult onUseBlock(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		return placeCrystal(p, hit.getBlockPos().relative(hit.getDirection())) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Use on a block: a crystal block on that face for 30 s. */
	public boolean placeCrystal(ServerPlayer p, BlockPos at) {
		ServerLevel l = level(p);
		if (!p.mayBuild() || !l.mayInteract(p, at) || !ready(p, PLACE)) {
			return false;
		}
		if (!l.isUnobstructed(crystal(), at, net.minecraft.world.phys.shapes.CollisionContext.empty())
				|| !TempBlocks.place(l, at, crystal(), PLACE_TICKS)) {
			return false;
		}
		cooldown(p, PLACE, -1);
		anim(p, ANIM_PLACE);
		p.swing(InteractionHand.MAIN_HAND, true);
		OmnitrixSounds.play(l, Vec3.atCenterOf(at), SND_GROW, 0.6f, 1.6f);
		return true;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		float a = flag(p, F_ARMOUR) ? amount * ARMOUR_FACTOR : amount;
		return flag(p, F_REGROW) ? a * REGROW_FACTOR : a; // v0.17.1: half damage while he regrows
	}

	// ---------------------------------------------------------------- v0.17.1 Crystal Regrowth (V until v0.17.2, C now)

	public static final String REGROW = "crystal_regrowth";
	public static final int ANIM_REGROW = 13;
	public static final int F_REGROW = 8;
	public static final int REGROW_TICKS = 60;
	public static final float REGROW_HEAL = 1.5f;
	public static final float REGROW_FACTOR = 0.5f;

	/**
	 * C: Crystal Regrowth -- a Petrosapien grows back. 3 s rooted to the spot while green crystal knits over every crack: 1.5
	 * health every half second (9 in all) and half damage meanwhile. Refused at full health.
	 */
	public boolean regrow(ServerPlayer p) {
		if (flag(p, F_REGROW)) {
			return false;
		}
		if (p.getHealth() >= p.getMaxHealth()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.diamondhead.whole").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, REGROW)) {
			return false;
		}
		setFlag(p, F_REGROW, true);
		setTimer(p, "regrow_end", p.level().getGameTime() + REGROW_TICKS);
		anim(p, ANIM_REGROW);
		ServerLevel l = level(p);
		QuirkFx.shards(l, p.position().add(0, 1, 0)).block(crystal()).size(1.4f).count(14).life(16).reassemble().spawn();
		OmnitrixSounds.play(l, p.position(), SND_GROW, 1.0f, 0.6f);
		return true;
	}

	private void revertV(ServerPlayer p) {
		setFlag(p, F_REGROW, false);
		setTimer(p, "regrow_end", 0);
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
		if (!flag(p, F_REGROW)) {
			return;
		}
		long end = timer(p, "regrow_end");
		if (now >= end || !p.isAlive()) {
			revertV(p);
			OmnitrixSounds.play(l, p.position(), SND_GROW, 0.8f, 1.5f);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 5, false, false, false));
		if ((end - now) % 10 == 0) {
			p.heal(REGROW_HEAL);
			QuirkFx.shards(l, p.position().add(0, 0.4 + l.random.nextDouble() * 1.4, 0)).block(crystal()).size(0.7f).count(4).life(10).reassemble().spawn();
			OmnitrixSounds.play(l, p.position(), SND_GROW, 0.4f, 1.3f + l.random.nextFloat() * 0.3f);
		}
		if (now % 2 == 0) {
			double a = now * 0.5;
			l.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + Math.cos(a) * 0.7, p.getY() + 0.2 + ((end - now) % 20) * 0.09, p.getZ() + Math.sin(a) * 0.7,
					1, 0, 0, 0, 0);
		}
	}
}
