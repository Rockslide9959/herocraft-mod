package com.projecthero.mod.omnitrix.alien.buzzshock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "Also build buzzshock please heres the skin: https://www.planetminecraft.com/skin/buzzshock-ben-10-5321154/" ... "do
 * research on his powers and look at what he does in the show ... Make this another alien you unlock on the 11th mistransformation").
 * Buzzshock, a Nosedeenian (prototype Omnitrix, a hidden alien): a tiny creature of living electricity. He throws lightning, zips about
 * as a spark and flies, rides power lines and wires, overloads machines -- and, like every Nosedeenian, splits into more of himself.
 * His shocks hit machines (Upgrade's "machine" targets: golems, mechanical mobs) for half again. Water shorts him out. Spec:
 * {@code docs/OMNITRIX_REVAMP_BUZZSHOCK.md}.
 *
 * <pre>
 *   R          Lightning Bolt     a bolt at the crosshair (24): 10
 *   Shift+R    Chain Lightning    14 to the creature you look at (20), then jumps to 4 more within 7: 10 each
 *   G          Static Touch       a point-blank discharge ahead (3 blocks, 120 deg): 14, stunned 1 s
 *   Shift+G    Spark Bomb         a lobbed ball of electricity: bursts for 18 within 3.5
 *   X          Spark Zip          a spark streak up to 12 blocks where you look, through creatures (8 each); no fall damage 1.5 s
 *   Shift+X    Spark Flight       hold: flies where you look as a crackling bolt (26 b/s, 40 sprinting) until you let go
 *   Z          Megavolt           every copy dives onto the creature you look at (16) and they discharge as one: 16, +3 a copy
 *                                 (6 at most), stunned 2 s; the copies are spent
 *   Shift+Z    Megawatt Swarm     ULTIMATE: 8 copies at once for 10 s, each shocking its own creature within 12
 *   V          Split              a copy splits off (4 at most; 4 health each), shocks whatever you fight once a second (3)
 *   Shift+V    Merge              the copies arc back into you: +2 health each
 *   C          Power Line Ride    through the nearest conductor along your look (48: redstone, copper, rails, rods, iron bars) and
 *                                 out at its end with a jolt (8 within 2.5)
 *   Shift+C    Overload           an electric pulse, 12 within 6 (machines 24 and knocked offline 3 s), others stunned 1 s
 *   Attack     Static Fists       +4 shock punches
 *   Passives   Living Electricity (lightning heals him, never hurts); Machine Killer (x1.5 to machines); tiny (x0.4); water shorts him
 * </pre>
 */
public final class BuzzshockKit extends AlienKit {
	public static final String ID = "buzzshock";
	/** His electricity: lime, like the skin's lights (the classic series' green). */
	public static final int COLOUR = 0x9CFF30;
	public static final int WHITE = 0xF4FFE8;

	public static EntityType<BuzzshockCopyEntity> COPY;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_ZIP = 1, F_FLY = 2, F_SWARM = 4, F_WET = 8, F_LANDING = 16;
	public static final int ANIM_BOLT = 1, ANIM_CHAIN = 2, ANIM_TOUCH = 3, ANIM_BOMB = 4, ANIM_ZIP = 5, ANIM_OVERLOAD = 6, ANIM_LINE = 7,
			ANIM_SPLIT = 8, ANIM_MERGE = 9, ANIM_SWARM = 10, ANIM_MEGAVOLT = 11, ANIM_PUNCH = 12, ANIM_TAKEOFF = 13, ANIM_LAND = 14;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.buzzshock.<id>)
	public static final String BOLT = "lightning_bolt", CHAIN = "chain_lightning", TOUCH = "static_touch", BOMB = "spark_bomb", ZIP = "spark_zip",
			FLIGHT = "spark_flight", OVERLOAD = "overload", LINE = "power_line_ride", SPLIT = "split", MERGE = "merge", SWARM = "megawatt_swarm",
			MEGAVOLT = "megavolt", FISTS = "static_fists", LIVING = "living_electricity", MACHINES = "machine_killer", WATER = "short_circuit";

	// ---------------------------------------------------------------- numbers (the prototype's scale: quick ~9-14, normal 14-22, big 22-28)
	public static final float BOLT_DAMAGE = 10f, CHAIN_FIRST = 14f, CHAIN_DAMAGE = 10f, TOUCH_DAMAGE = 14f, BOMB_DAMAGE = 18f, ZIP_DAMAGE = 8f,
			OVERLOAD_DAMAGE = 12f, LINE_DAMAGE = 8f, COPY_ZAP = 3f, MEGAVOLT_DAMAGE = 16f, MEGAVOLT_PER_COPY = 3f, FIST_DAMAGE = 4f;
	/** v0.17.2 (the task: "Electric shocks against Upgrade's machine targets can deal extra damage"): shocks on a machine. */
	public static final float MACHINE_BONUS = 1.5f;
	/** Overload's pulse on a machine is worse still: twice the damage, and it goes offline. */
	public static final float OVERLOAD_MACHINE = 2f;
	public static final float MERGE_HEAL = 2f, LIGHTNING_HEAL = 8f;
	public static final int MAX_COPIES = 4, SWARM_COPIES = 8, COPY_HIT_CAP = 6, CHAIN_JUMPS = 4;
	public static final int TOUCH_STUN = 20, OVERLOAD_STUN = 20, OVERLOAD_MACHINE_STUN = 60, MEGAVOLT_STUN = 40, MEGAVOLT_DELAY = 8, ZIP_TICKS = 30,
			SWARM_TICKS = 200, WET_EVERY = 20;
	public static final double BOLT_RANGE = 24, CHAIN_RANGE = 20, CHAIN_HOP = 7, TOUCH_RANGE = 3, ZIP_RANGE = 12, OVERLOAD_RADIUS = 6,
			LINE_RANGE = 48, LINE_REACH = 1.6, MEGAVOLT_RANGE = 16, SWARM_RANGE = 12;
	public static final float FLY_SPEED = 0.07f;

	public static final String ZIP_UNTIL = "zip_until", SWARM_UNTIL = "swarm_until";

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_ZAP = OmnitrixSfx.cue("buzzshock_zap")
			.layer(SoundEvents.TRIDENT_THUNDER, 0.1f, 2.0f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.3f, 1.9f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.15f, 1.9f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_BIG = OmnitrixSfx.cue("buzzshock_big")
			.layer(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35f, 1.6f)
			.layer(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.35f, 1.5f)
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.12f, 1.8f, 1)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_ZIP = OmnitrixSfx.cue("buzzshock_zip")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.25f, 2.0f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.25f, 1.8f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_SPLIT = OmnitrixSfx.cue("buzzshock_split")
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.8f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.15f, 2.0f);
	/** Spark Flight's crackle, over and over while the keys are down. */
	public static final OmnitrixSfx.Cue SND_CRACKLE = OmnitrixSfx.cue("buzzshock_crackle")
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.2f, 1.9f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.1f, 2.0f)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_FIZZ = OmnitrixSfx.cue("buzzshock_fizz")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.35f, 1.7f)
			.gap(8);

	// ---------------------------------------------------------------- server-only state
	private record Megavolt(LivingEntity target, long when) {
	}

	private static final Map<UUID, List<BuzzshockCopyEntity>> COPIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Megavolt> MEGAVOLTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_LIGHTNING = new ConcurrentHashMap<>();

	private BuzzshockKit() {
	}

	public static void register() {
		COPY = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("buzzshock_copy")),
				EntityType.Builder.<BuzzshockCopyEntity>of(BuzzshockCopyEntity::new, MobCategory.MISC).sized(0.26f, 0.72f).noSave().clientTrackingRange(8)
						.updateInterval(1).build("buzzshock_copy"));
		Aliens.register(new BuzzshockKit());
	}

	public static void clearSessionState() {
		COPIES.clear();
		MEGAVOLTS.clear();
		LAST_TICK.clear();
		LAST_LIGHTNING.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is flying on a held Shift+X right now (client-safe: a synced flag). */
	public static boolean holdFlying(Player p) {
		return isBuzzshock(p) && (Omnitrix.state(p).kitFlags & F_FLY) != 0;
	}

	/** {@code p} is Buzzshock right now. */
	public static boolean isBuzzshock(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// tiny (about Ben's shin), quick, light -- all his punch is in his electricity
		return AlienStats.builder().health(18f).speed(1.35f).scale(0.4f).armor(6f, 0f).attack(1f).jump(0.1f).safeFall(8f).reach(0.6f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BOLT, AbilitySlot.SLOT_1, 15), AlienMove.shift(CHAIN, AbilitySlot.SLOT_1, 160),
				AlienMove.tap(TOUCH, AbilitySlot.SLOT_2, 50), AlienMove.shift(BOMB, AbilitySlot.SLOT_2, 200),
				AlienMove.tap(ZIP, AbilitySlot.SLOT_3, 30), AlienMove.shift(FLIGHT, AbilitySlot.SLOT_3, 20),
				// v0.17.2 round 3 (user: "Make power line ride c and megawatt storm shift+Z", "make megavolt z and overload shift+C please")
				AlienMove.tap(MEGAVOLT, AbilitySlot.SLOT_4, 300), AlienMove.shift(SWARM, AbilitySlot.SLOT_4, 1200),
				AlienMove.tap(SPLIT, AbilitySlot.SLOT_5, 10), AlienMove.shift(MERGE, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(LINE, AbilitySlot.SLOT_6, 160), AlienMove.shift(OVERLOAD, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(LIVING, "Passive", 0), AlienMove.vanilla(MACHINES, "Passive", 0),
				AlienMove.vanilla(WATER, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_FLY);
			case SLOT_4 -> flag(p, F_SWARM);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- shocks

	/** A machine to his electricity: Upgrade's machines (golems, mechanical mobs). */
	public static boolean machine(LivingEntity e) {
		return UpgradeKit.machine(e);
	}

	/** An electric hit on {@code e}: {@code amount}, half again on a machine. */
	public static boolean shock(ServerPlayer p, LivingEntity e, float amount) {
		return hurt(p, e, machine(e) ? amount * MACHINE_BONUS : amount, false);
	}

	/** One copy's shock on {@code e}: the arc from the copy and {@link #COPY_ZAP}. */
	public static void copyZap(ServerPlayer p, BuzzshockCopyEntity c, LivingEntity e) {
		shock(p, e, COPY_ZAP);
		arc(p, centre(e), c, 5, false);
		OmnitrixSfx.play(level(p), c.position(), SND_ZAP, 0.5f, 1.2f);
	}

	/** A bolt drawn to {@code end}: from his hand (from null), or from entity {@code from}. Lasts {@code life} ticks. */
	public static void arc(ServerPlayer p, Vec3 end, @Nullable Entity from, int life, boolean big) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, big ? "buzzshock_big" : "buzzshock_arc");
		a.moveTo(end.x, end.y, end.z, 0f, 0f);
		double len = from == null ? p.getEyePosition().distanceTo(end) : from.position().distanceTo(end);
		a.colour(from == null ? 0 : from.getId() + 1).size((float) Math.max(0.6, len / 3.0)).held(true).speed(0).life(life).damage(0f).spawn();
	}

	private static Vec3 centre(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	private static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.buzzshock." + key).withStyle(ChatFormatting.GRAY), true);
	}

	/** Water shorts his electricity out: true (and the fizz) if {@code p} is in water right now. */
	private boolean shorted(ServerPlayer p) {
		if (!p.isInWater()) {
			return false;
		}
		say(p, "shorted");
		OmnitrixSfx.play(level(p), p.position(), SND_FIZZ, 0.8f, 1f);
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
		return true;
	}

	// ---------------------------------------------------------------- copies

	/** His live copies (removed ones dropped first). */
	public static List<BuzzshockCopyEntity> copies(ServerPlayer p) {
		List<BuzzshockCopyEntity> l = COPIES.get(p.getUUID());
		if (l == null) {
			return List.of();
		}
		l.removeIf(Entity::isRemoved);
		return List.copyOf(l);
	}

	/** How many copies he may have right now (more during the Megawatt Swarm). */
	public int cap(ServerPlayer p) {
		return flag(p, F_SWARM) ? SWARM_COPIES : MAX_COPIES;
	}

	/** Splits off one copy at {@code at} if there is room; null if he is at his cap. */
	@Nullable
	public BuzzshockCopyEntity addCopy(ServerPlayer p, Vec3 at) {
		List<BuzzshockCopyEntity> l = COPIES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		l.removeIf(Entity::isRemoved);
		if (l.size() >= cap(p)) {
			return null;
		}
		Set<Integer> used = new LinkedHashSet<>();
		for (BuzzshockCopyEntity c : l) {
			used.add(c.slot());
		}
		int slot = 0;
		while (used.contains(slot)) {
			slot++;
		}
		BuzzshockCopyEntity c = BuzzshockCopyEntity.spawn(p, slot, at);
		l.add(c);
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 8, 0.1, 0.2, 0.1, 0.1);
		QuirkFx.ring(level(p), at.add(0, 0.3, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(0.8f).thickness(0.12f).life(7).spawn();
		return c;
	}

	/** A spot beside him, at his shoulders, for a new copy ({@code i} goes round him). */
	private static Vec3 beside(ServerPlayer p, int i, double r) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD + Mth.PI * 0.5f + i * 2.1f;
		Vec3 at = p.position().add(-Mth.sin(yaw) * r, p.getBbHeight() * 0.6, Mth.cos(yaw) * r);
		return p.level().noCollision(COPY.getDimensions().makeBoundingBox(at)) ? at : p.position().add(0, p.getBbHeight(), 0);
	}

	private List<BuzzshockCopyEntity> atLeast(ServerPlayer p, int n) {
		for (int i = copies(p).size(); i < n; i++) {
			if (addCopy(p, beside(p, i, 0.8)) == null) {
				break;
			}
		}
		return copies(p);
	}

	private static void removeCopies(ServerPlayer p) {
		List<BuzzshockCopyEntity> l = COPIES.remove(p.getUUID());
		if (l != null) {
			for (BuzzshockCopyEntity c : l) {
				c.fizzle();
			}
		}
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 0.8f, 1.2f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		removeCopies(p);
		UUID id = p.getUUID();
		MEGAVOLTS.remove(id);
		LAST_TICK.remove(id);
		LAST_LIGHTNING.remove(id);
		if (flag(p, F_FLY)) {
			RecalKitMath.groundWings(p);
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(ZIP_UNTIL));
		n.timers.remove(key(SWARM_UNTIL));
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.LIGHTNING_BOLT)) {
			// v0.17.2 (the show: a Nosedeenian feeds on electricity): a lightning strike charges him up instead
			long now = p.level().getGameTime();
			Long last = LAST_LIGHTNING.get(p.getUUID());
			if (last == null || now - last > 20) {
				LAST_LIGHTNING.put(p.getUUID(), now);
				p.heal(LIGHTNING_HEAL);
				OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 1f, 0.8f);
			}
			p.clearFire();
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL) && (flag(p, F_ZIP) || flag(p, F_FLY) || flag(p, F_LANDING))) {
			return false;
		}
		return true;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		if (flag(p, F_ZIP) && now >= timer(p, ZIP_UNTIL)) {
			setFlag(p, F_ZIP, false);
		}
		if (flag(p, F_LANDING) && (p.onGround() || p.isInWater())) {
			setFlag(p, F_LANDING, false);
		}
		boolean flying = flag(p, F_FLY);
		if (flying) {
			flyTick(p, l, now);
			flying = flag(p, F_FLY);
		}
		// v0.17.2 weakness (the show: water and grounding short him out): in water he sparks and takes 1 a second, and his
		// electric moves fizzle; flight drops
		boolean wet = p.isInWater();
		if (wet != flag(p, F_WET)) {
			setFlag(p, F_WET, wet);
		}
		if (wet) {
			if (flying) {
				endFlight(p);
			}
			if (now % WET_EVERY == 0) {
				int keep = p.invulnerableTime;
				p.invulnerableTime = 0;
				p.hurt(p.damageSources().magic(), 1f);
				p.invulnerableTime = keep;
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 6, 0.3, 0.1, 0.3, 0.15);
				OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.6f, 1f);
			}
		}
		// Megavolt: the copies have reached it
		Megavolt mv = MEGAVOLTS.get(p.getUUID());
		if (mv != null && now >= mv.when) {
			MEGAVOLTS.remove(p.getUUID());
			megavoltHit(p, mv.target);
		}
		if (flag(p, F_SWARM) && now >= timer(p, SWARM_UNTIL)) {
			setFlag(p, F_SWARM, false);
			List<BuzzshockCopyEntity> l2 = new ArrayList<>(copies(p));
			l2.sort(Comparator.comparingInt(BuzzshockCopyEntity::slot));
			for (int i = MAX_COPIES; i < l2.size(); i++) {
				l2.get(i).fizzle();
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					chainLightning(p);
				} else {
					lightningBolt(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					sparkBomb(p);
				} else {
					staticTouch(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					flight(p);
				} else {
					zip(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					swarm(p);
				} else {
					megavolt(p);
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
					overload(p);
				} else {
					lineRide(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_3 && shift) {
			endFlight(p); // Spark Flight lasts as long as the keys
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (!(target instanceof LivingEntity e) || p.isInWater()) {
			return false; // a wet fist is just a (tiny) fist
		}
		float k = p.getAttackStrengthScale(0.5f);
		p.resetAttackStrengthTicker();
		anim(p, ANIM_PUNCH);
		if (shock(p, e, FIST_DAMAGE * (0.2f + 0.8f * k * k))) {
			Vec3 d = e.position().subtract(p.position());
			e.knockback(0.25, -d.x, -d.z);
		}
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 5, 0.15, 0.2, 0.15, 0.1);
		OmnitrixSfx.play(level(p), e.position(), SND_ZAP, 0.6f, 1.4f);
		return true;
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: a bolt to the creature under the crosshair, else to where it points. */
	public boolean lightningBolt(ServerPlayer p) {
		if (shorted(p) || !use(p, BOLT)) {
			return false;
		}
		anim(p, ANIM_BOLT);
		LivingEntity t = lookTarget(p, BOLT_RANGE);
		if (t != null) {
			shock(p, t, BOLT_DAMAGE);
			arc(p, centre(t), null, 6, false);
		} else {
			arc(p, aimPoint(p, BOLT_RANGE), null, 6, false);
		}
		OmnitrixSfx.playOn(p, SND_ZAP, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: the bolt jumps from creature to creature. False (no cooldown) without a creature in the crosshair. */
	public boolean chainLightning(ServerPlayer p) {
		LivingEntity first = lookTarget(p, CHAIN_RANGE);
		if (first == null) {
			say(p, "no_target");
			return false;
		}
		if (shorted(p) || !use(p, CHAIN)) {
			return false;
		}
		anim(p, ANIM_CHAIN);
		shock(p, first, CHAIN_FIRST);
		arc(p, centre(first), null, 8, false);
		List<LivingEntity> hit = new ArrayList<>(List.of(first));
		LivingEntity cur = first;
		for (int i = 0; i < CHAIN_JUMPS; i++) {
			LivingEntity next = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : around(p, cur.position(), CHAIN_HOP)) {
				if (!hit.contains(e) && e.distanceToSqr(cur) < best) {
					best = e.distanceToSqr(cur);
					next = e;
				}
			}
			if (next == null) {
				break;
			}
			next.invulnerableTime = 0;
			shock(p, next, CHAIN_DAMAGE);
			arc(p, centre(next), cur, 8, false);
			hit.add(next);
			cur = next;
		}
		OmnitrixSfx.playOn(p, SND_BIG, 0.9f, 1.1f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** G: a point-blank discharge ahead of him. */
	public boolean staticTouch(ServerPlayer p) {
		if (shorted(p) || !use(p, TOUCH)) {
			return false;
		}
		anim(p, ANIM_TOUCH);
		for (LivingEntity e : RecalKitMath.cone(p, TOUCH_RANGE, 60)) {
			if (shock(p, e, TOUCH_DAMAGE)) {
				AlienStatus.stun(p, e, TOUCH_STUN);
				shove(e, p.position(), 0.5, 0.15);
			}
			arc(p, centre(e), null, 5, false);
		}
		Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(1.0));
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z, 18, 0.4, 0.3, 0.4, 0.25);
		QuirkFx.ring(level(p), front).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(1.6f)
				.thickness(0.2f).life(6).spawn();
		OmnitrixSfx.playOn(p, SND_BIG, 0.7f, 1.4f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+G: a lobbed ball of electricity that bursts where it lands. */
	public boolean sparkBomb(ServerPlayer p) {
		if (shorted(p) || !use(p, BOMB)) {
			return false;
		}
		anim(p, ANIM_BOMB);
		AlienProjectileEntity.of(p, "buzzshock_ball").size(0.55f).speed(0.9).gravity(0.035).blast(3.5f, BOMB_DAMAGE).life(80).damage(0f).spawn();
		OmnitrixSfx.playOn(p, SND_ZIP, 0.8f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	/** The Spark Bomb's burst (the projectile's own blast does the damage). */
	static void bombBurst(AlienProjectileEntity e, HitResult hit) {
		if (!(e.level() instanceof ServerLevel l)) {
			return;
		}
		Vec3 at = hit.getLocation();
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 40, 1.2, 0.6, 1.2, 0.4);
		l.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		QuirkFx.ring(l, at.add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(3.5f).thickness(0.4f).life(10).ground().spawn();
		OmnitrixSfx.play(l, at, SND_BIG, 1.2f, 1f);
		if (e.getOwner() instanceof ServerPlayer p) {
			for (LivingEntity t : around(p, at, 3.5)) {
				arc(p, centre(t), e, 6, false);
			}
		}
	}

	// ---------------------------------------------------------------- X / Shift+X

	/** X: a spark streak where he looks, through creatures, stopping short of walls. */
	public boolean zip(ServerPlayer p) {
		if (!use(p, ZIP)) {
			return false;
		}
		anim(p, ANIM_ZIP);
		Vec3 look = p.getLookAngle();
		Vec3 start = p.position();
		Vec3 eye = p.getEyePosition();
		double eyeH = eye.y - start.y;
		HitResult hit = p.level().clip(new ClipContext(eye, eye.add(look.scale(ZIP_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double dist = hit.getType() == HitResult.Type.MISS ? ZIP_RANGE : Math.max(0, hit.getLocation().distanceTo(eye) - 0.5);
		Vec3 end = start;
		for (double d = dist; d > 0; d -= 0.5) {
			Vec3 at = eye.add(look.scale(d)).subtract(0, eyeH, 0);
			if (p.level().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(at))) {
				end = at;
				dist = d;
				break;
			}
		}
		for (LivingEntity e : RecalKitMath.line(p, look, dist, 0.9)) {
			e.invulnerableTime = 0;
			shock(p, e, ZIP_DAMAGE);
		}
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, start.add(0, p.getBbHeight() * 0.5, 0), end.add(0, p.getBbHeight() * 0.5, 0)).style(QuirkFx.RIBBON_LIGHT).colours(WHITE, COLOUR)
				.size(0.22f).life(8).spawn();
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, start.x, start.y + 0.2, start.z, 10, 0.15, 0.2, 0.15, 0.15);
		p.teleportTo(end.x, end.y, end.z);
		p.setDeltaMovement(look.scale(0.3));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, ZIP_UNTIL, p.level().getGameTime() + ZIP_TICKS);
		setFlag(p, F_ZIP, true);
		arc(p, start.add(0, 0.2, 0), null, 6, false); // the streak's lightning, from him back to where he was
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 0.2, end.z, 10, 0.15, 0.2, 0.15, 0.15);
		for (BuzzshockCopyEntity c : copies(p)) {
			c.dashTo(end.add(c.swarmPos(p).subtract(p.position())), 16);
		}
		OmnitrixSfx.play(l, end, SND_ZIP, 1f, 1.2f);
		return true;
	}

	/**
	 * Shift+X down: v0.17.2 round 3 (user: "upgrade his spark flight please, as long as he holds SHift+X he flies in that direction with
	 * his crackling lightning vfx please"). He takes off and {@code DirectionalFlight} drives him along his look until the keys come up
	 * ({@link #release}); the cooldown starts when he lets go. The bolts round him are {@code BuzzshockFx}, drawn from the synced flag.
	 */
	public boolean flight(ServerPlayer p) {
		if (flag(p, F_FLY) || shorted(p)) {
			return false;
		}
		if (!ready(p, FLIGHT)) {
			return use(p, FLIGHT); // the usual "not ready" line
		}
		setFlag(p, F_FLY, true);
		setFlag(p, F_LANDING, false);
		RecalKitMath.wings(p, true, FLY_SPEED);
		anim(p, ANIM_TAKEOFF);
		p.setDeltaMovement(p.getDeltaMovement().add(0, p.onGround() ? 0.35 : 0.1, 0));
		p.hurtMarked = true;
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 14, 0.2, 0.05, 0.2, 0.18);
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(1.4f).thickness(0.14f).life(7).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_ZIP, 0.8f, 1.4f);
		return true;
	}

	/** One tick of Spark Flight: both keys still down, still flying, still crackling. */
	private void flyTick(ServerPlayer p, ServerLevel l, long now) {
		if (!p.isShiftKeyDown()) {
			endFlight(p); // "as long as he holds Shift+X": the Shift half came up first
			return;
		}
		if (!p.getAbilities().flying) {
			RecalKitMath.wings(p, true, FLY_SPEED); // the client landed him for a tick (a scrape along the floor)
		}
		p.resetFallDistance();
		if (now % 2 == 0) {
			Vec3 tail = p.position().subtract(p.getLookAngle().scale(0.6));
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, tail.x, tail.y + p.getBbHeight() * 0.4, tail.z, 2, 0.1, 0.1, 0.1, 0.04);
		}
		if (now % 8 == 0) {
			OmnitrixSfx.play(l, p.position(), SND_CRACKLE, 0.7f, 1f + (now % 3) * 0.1f);
		}
	}

	/** Spark Flight stops: the wings go, the cooldown starts, and no fall can hurt him until he lands. */
	private void endFlight(ServerPlayer p) {
		if (!flag(p, F_FLY)) {
			return;
		}
		setFlag(p, F_FLY, false);
		setFlag(p, F_LANDING, !p.onGround());
		RecalKitMath.groundWings(p);
		anim(p, ANIM_LAND);
		cooldown(p, FLIGHT, -1);
	}

	// ---------------------------------------------------------------- Shift+C

	/** Shift+C: an electric pulse round him; machines take it worst and go offline. */
	public boolean overload(ServerPlayer p) {
		if (shorted(p) || !use(p, OVERLOAD)) {
			return false;
		}
		anim(p, ANIM_OVERLOAD);
		ServerLevel l = level(p);
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0);
		int arcs = 0;
		for (LivingEntity e : around(p, at, OVERLOAD_RADIUS)) {
			boolean m = machine(e);
			if (hurt(p, e, m ? OVERLOAD_DAMAGE * OVERLOAD_MACHINE : OVERLOAD_DAMAGE, false)) {
				AlienStatus.stun(p, e, m ? OVERLOAD_MACHINE_STUN : OVERLOAD_STUN);
				if (m) {
					l.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
				}
			}
			if (arcs++ < 8) {
				arc(p, centre(e), null, 7, false);
			}
		}
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 1.5, 0.6, 1.5, 0.4);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size((float) OVERLOAD_RADIUS).thickness(0.5f).life(12)
				.ground().spawn();
		OmnitrixSfx.play(l, at, SND_BIG, 1.4f, 0.9f);
		return true;
	}

	/** Whether {@code s} carries his electricity (Power Line Ride): anything giving a redstone signal, wire, copper, rails, rods, bars. */
	public static boolean conductor(BlockState s) {
		if (s.isAir()) {
			return false;
		}
		if (s.isSignalSource()) {
			return true;
		}
		String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return path.contains("copper") || path.contains("redstone") || path.contains("rail") || path.equals("lightning_rod") || path.equals("iron_bars")
				|| path.equals("chain") || path.contains("repeater") || path.contains("comparator") || path.equals("observer");
	}

	/** The first conductor along {@code p}'s look within {@link #LINE_RANGE} (a block's reach either side of the ray), or null. */
	@Nullable
	public static BlockPos conductorAhead(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (double d = 1.5; d <= LINE_RANGE; d += 0.75) {
			Vec3 at = eye.add(look.scale(d));
			int r = (int) Math.ceil(LINE_REACH);
			BlockPos best = null;
			double bestD = Double.MAX_VALUE;
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						m.set(Mth.floor(at.x) + dx, Mth.floor(at.y) + dy, Mth.floor(at.z) + dz);
						double dd = Vec3.atCenterOf(m).distanceToSqr(at);
						if (dd <= LINE_REACH * LINE_REACH * 1.5 && dd < bestD && conductor(p.level().getBlockState(m))) {
							bestD = dd;
							best = m.immutable();
						}
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	/** Where he comes out of conductor {@code c}: on it (a wire, a rail) or on top of it (a copper block), with room for him; or null. */
	@Nullable
	private static Vec3 exitAt(ServerPlayer p, BlockPos c) {
		for (int up = 0; up <= 2; up++) {
			Vec3 at = Vec3.atBottomCenterOf(c.above(up));
			if (p.level().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(at))) {
				return at;
			}
		}
		return null;
	}

	/** C: into the conductor he looks at and out of it, wherever it is (within 48). */
	public boolean lineRide(ServerPlayer p) {
		BlockPos c = conductorAhead(p);
		Vec3 out = c == null ? null : exitAt(p, c);
		if (out == null) {
			say(p, "no_conductor");
			return false;
		}
		if (shorted(p) || !use(p, LINE)) {
			return false;
		}
		anim(p, ANIM_LINE);
		ServerLevel l = level(p);
		Vec3 from = p.position();
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y + 0.2, from.z, 16, 0.15, 0.25, 0.15, 0.2);
		QuirkFx.ribbon(l, from.add(0, 0.2, 0), out.add(0, 0.2, 0)).style(QuirkFx.RIBBON_LIGHT).colours(WHITE, COLOUR).size(0.14f).life(10).spawn();
		p.teleportTo(out.x, out.y, out.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, ZIP_UNTIL, p.level().getGameTime() + ZIP_TICKS);
		setFlag(p, F_ZIP, true);
		arc(p, from.add(0, 0.2, 0), null, 6, false);
		for (LivingEntity e : around(p, out.add(0, 0.4, 0), 2.5)) {
			if (shock(p, e, LINE_DAMAGE)) {
				shove(e, out, 0.5, 0.2);
			}
			arc(p, centre(e), null, 5, false);
		}
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, out.x, out.y + 0.2, out.z, 24, 0.4, 0.3, 0.4, 0.3);
		QuirkFx.ring(l, out.add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(2.5f).thickness(0.3f).life(8).ground().spawn();
		for (BuzzshockCopyEntity cp : copies(p)) {
			Vec3 s = cp.swarmPos(p);
			cp.setPos(s.x, s.y, s.z); // the swarm rides the line with him
		}
		OmnitrixSfx.play(l, out, SND_ZIP, 1f, 0.9f);
		return true;
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** V: one copy splits off. */
	public boolean split(ServerPlayer p) {
		if (copies(p).size() >= cap(p)) {
			say(p, "max_copies");
			return false;
		}
		if (!use(p, SPLIT)) {
			return false;
		}
		anim(p, ANIM_SPLIT);
		addCopy(p, beside(p, copies(p).size(), 0.8));
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 1f, 1f);
		return true;
	}

	/** Shift+V: the copies arc back into him (+2 health each). */
	public boolean merge(ServerPlayer p) {
		List<BuzzshockCopyEntity> l = copies(p);
		if (l.isEmpty()) {
			say(p, "no_copies");
			return false;
		}
		if (!use(p, MERGE)) {
			return false;
		}
		anim(p, ANIM_MERGE);
		for (BuzzshockCopyEntity c : l) {
			arc(p, c.position().add(0, 0.3, 0), null, 6, false);
			c.fizzle();
		}
		COPIES.remove(p.getUUID());
		p.heal(MERGE_HEAL * l.size());
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 1f, 0.7f);
		return true;
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Shift+Z (ultimate): a swarm of copies at once, each shocking its own creature. */
	public boolean swarm(ServerPlayer p) {
		if (flag(p, F_SWARM)) {
			return false;
		}
		if (shorted(p) || !use(p, SWARM)) {
			return false;
		}
		anim(p, ANIM_SWARM);
		setFlag(p, F_SWARM, true);
		setTimer(p, SWARM_UNTIL, p.level().getGameTime() + SWARM_TICKS);
		int i = copies(p).size();
		while (addCopy(p, beside(p, i, 0.7 + (i % 3) * 0.4)) != null) {
			i++;
		}
		List<LivingEntity> foes = new ArrayList<>(around(p, p.position(), SWARM_RANGE));
		foes.removeIf(e -> e instanceof Player); // players only once they start it (the swarm's own foe)
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		List<BuzzshockCopyEntity> l = copies(p);
		if (!foes.isEmpty()) {
			for (int k = 0; k < l.size(); k++) {
				l.get(k).attack(foes.get(k % foes.size()), SWARM_TICKS);
			}
		}
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(5f).thickness(0.5f).life(14).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_BIG, 1.4f, 1.2f);
		return true;
	}

	/** Z: every copy dives onto the creature he looks at, and they discharge as one. */
	public boolean megavolt(ServerPlayer p) {
		LivingEntity t = lookTarget(p, MEGAVOLT_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (shorted(p) || !use(p, MEGAVOLT)) {
			return false;
		}
		anim(p, ANIM_MEGAVOLT);
		for (BuzzshockCopyEntity c : atLeast(p, 2)) {
			c.dashTo(centre(t), MEGAVOLT_DELAY + 6);
		}
		MEGAVOLTS.put(p.getUUID(), new Megavolt(t, p.level().getGameTime() + MEGAVOLT_DELAY));
		OmnitrixSfx.play(level(p), p.position(), SND_ZIP, 1f, 0.7f);
		return true;
	}

	private void megavoltHit(ServerPlayer p, LivingEntity t) {
		List<BuzzshockCopyEntity> l = copies(p);
		if (!t.isAlive() || t.isRemoved()) {
			return;
		}
		ServerLevel lv = level(p);
		int n = Math.min(l.size(), COPY_HIT_CAP);
		t.invulnerableTime = 0;
		if (shock(p, t, MEGAVOLT_DAMAGE + MEGAVOLT_PER_COPY * n)) {
			AlienStatus.stun(p, t, MEGAVOLT_STUN);
		}
		arc(p, centre(t), null, 10, true);
		for (BuzzshockCopyEntity c : l) {
			arc(p, centre(t), c, 8, false);
		}
		for (BuzzshockCopyEntity c : l) {
			c.fizzle(); // spent in the discharge
		}
		COPIES.remove(p.getUUID());
		Vec3 c = centre(t);
		lv.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 50, 0.8, 0.8, 0.8, 0.5);
		QuirkFx.blast(lv, c).colours(WHITE, COLOUR).size(2.8f).life(10).spawn();
		OmnitrixSfx.play(lv, c, SND_BIG, 1.6f, 0.8f);
	}

	/** Test hook: whether a Megavolt is on its way. */
	public static boolean megavoltPending(ServerPlayer p) {
		return MEGAVOLTS.containsKey(p.getUUID());
	}

	static {
		AlienProjectileEntity.onHit("buzzshock_ball", BuzzshockKit::bombBurst);
		AlienProjectileEntity.trail("buzzshock_ball", ParticleTypes.ELECTRIC_SPARK);
	}
}
