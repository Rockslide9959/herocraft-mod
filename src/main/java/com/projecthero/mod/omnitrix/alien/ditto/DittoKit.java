package com.projecthero.mod.omnitrix.alien.ditto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkFx;

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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "also create a moveset for ditto please. Use this skin: https://www.planetminecraft.com/skin/ditto-ben-10-5303343/
 * ... do research on his powers and look at what he does in the show ... Make this another alien you unlock on the 11th
 * mistransformation"). Ditto, a Splixson (prototype Omnitrix, a hidden alien): a small, quick, agile climber who splits into
 * identical copies of himself. Alone he is weak; his copies are his whole game -- they swarm, pile on, carry and throw together, and
 * merge back into him. As in the show, what a copy feels he feels: half the damage a copy takes reaches him. Spec:
 * {@code docs/OMNITRIX_REVAMP_DITTO.md}.
 *
 * <pre>
 *   R          Swarm Strike    every copy rushes the creature you look at (20) and fights it 8 s (3 a punch, once a second each)
 *   Shift+R    Dogpile         every copy leaps onto the creature you look at (16): 4 each (6 at most), pinned 2.5 s
 *   G          Pinwheel Kick   a spinning kick, 10 to everything within 2.5; every copy kicks round itself too (4, 2 blocks)
 *   Shift+G    Split Burst     bursts into 3 copies at once: 8 to everything within 3, thrown back
 *   X          Splixson Leap   a long, agile leap; the copies leap after you; no fall damage 1.5 s
 *   Shift+X    Copy Toss       the nearest copy (6) hurls you where you look; the landing hits 9 within 2.5
 *   Z          Pile-Up         the copies dive onto the spot you look at (16): 14 within 3, +3 for each copy there (6 at most), slowed
 *   Shift+Z    Shell Game      swap places with a random copy; creatures hunting you lose you; invisible 3 s
 *   V          Split           a copy splits off (6 at most; 6 health each)
 *   Shift+V    Merge           the copies run back into you: +2 health each
 *   C          Ditto Army      ULTIMATE: 12 copies at once for 10 s, each going for its own creature within 12
 *   Shift+C    Copy Carry      two copies lift the creature you look at (12) and hurl it where you look: 16 (a big one is only slammed: 10)
 *   Attack     Splixson Scrap  his fists (+3); every copy joins in on whatever he hits, and on whatever hits him
 *   Passives   Felt by All (half a copy's damage reaches him); Climber (press into a wall to climb); quick and small (x0.55)
 * </pre>
 */
public final class DittoKit extends AlienKit {
	public static final String ID = Aliens.DITTO.id();
	public static final int COLOUR = 0xE6EEF8;

	public static EntityType<DittoCopyEntity> COPY;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_LEAP = 1, F_ARMY = 2, F_CARRY = 4, F_TOSS = 8;
	public static final int ANIM_PUNCH = 1, ANIM_KICK = 2, ANIM_SPLIT = 3, ANIM_MERGE = 4, ANIM_LEAP = 5, ANIM_POINT = 6, ANIM_BURST = 7,
			ANIM_ARMY = 8, ANIM_TOSS = 9, ANIM_SHUFFLE = 10, ANIM_CARRY = 11;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.ditto.<id>)
	public static final String SWARM = "swarm_strike", DOGPILE = "dogpile", KICK = "pinwheel_kick", BURST = "split_burst", LEAP = "splixson_leap",
			TOSS = "copy_toss", PILE = "pile_up", SHUFFLE = "shell_game", SPLIT = "split", MERGE = "merge", ARMY = "ditto_army", CARRY = "copy_carry",
			SCRAP = "splixson_scrap", FELT = "felt_by_all", CLIMB = "wall_crawl";

	// ---------------------------------------------------------------- numbers (the prototype's scale: quick ~9-14, normal 14-22, big 22-28;
	// his own hits are light, the pack's add up)
	public static final int MAX_COPIES = 6, ARMY_COPIES = 12;
	public static final float COPY_PUNCH = 3f, DOGPILE_DAMAGE = 4f, KICK_DAMAGE = 10f, KICK_COPY_DAMAGE = 4f, BURST_DAMAGE = 8f, TOSS_DAMAGE = 9f,
			PILE_DAMAGE = 14f, PILE_COPY_DAMAGE = 3f, CARRY_DAMAGE = 16f, CARRY_SLAM_DAMAGE = 10f;
	/** v0.17.2 (the show: damage to one is felt by all): the part of a copy's damage that reaches him. */
	public static final float FEEDBACK = 0.5f;
	public static final float MERGE_HEAL = 2f;
	/** Most copies that add to one creature in one move (a full army stays fair). */
	public static final int COPY_HIT_CAP = 6;
	public static final int SWARM_TICKS = 160, DOGPILE_STUN = 50, LEAP_TICKS = 30, TOSS_TICKS = 60, PILE_DELAY = 10, SHUFFLE_INVIS = 60,
			ARMY_TICKS = 200, CARRY_LIFT = 20, FOE_MEMORY = 100;
	public static final double SWARM_RANGE = 20, DOGPILE_RANGE = 16, PILE_RANGE = 16, CARRY_RANGE = 12, TOSS_REACH = 6, ARMY_RANGE = 12;
	/** Creatures heavier than this (max health) are too big to lift: Copy Carry slams them instead. */
	public static final float CARRY_MAX_HEALTH = 100f;

	public static final String LEAP_UNTIL = "leap_until", ARMY_UNTIL = "army_until";

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_SPLIT = OmnitrixSfx.cue("ditto_split")
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.5f, 1.5f)
			.layer(SoundEvents.ITEM_PICKUP, 0.25f, 0.7f);
	public static final OmnitrixSfx.Cue SND_MERGE = OmnitrixSfx.cue("ditto_merge")
			.layer(SoundEvents.SLIME_SQUISH, 0.4f, 1.6f)
			.layer(SoundEvents.ITEM_PICKUP, 0.3f, 1.4f, 2);
	public static final OmnitrixSfx.Cue SND_KICK = OmnitrixSfx.cue("ditto_kick")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 1.4f);
	public static final OmnitrixSfx.Cue SND_PILE = OmnitrixSfx.cue("ditto_pile")
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5f, 1.2f)
			.layer(SoundEvents.GENERIC_SMALL_FALL, 0.5f, 0.8f, 1)
			.layer(SoundEvents.SLIME_SQUISH, 0.3f, 1.3f, 2);
	public static final OmnitrixSfx.Cue SND_LEAP = OmnitrixSfx.cue("ditto_leap")
			.layer(SoundEvents.SLIME_JUMP_SMALL, 0.4f, 1.5f);
	public static final OmnitrixSfx.Cue SND_ARMY = OmnitrixSfx.cue("ditto_army")
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.6f, 1.2f)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.6f, 1.6f, 2)
			.layer(SoundEvents.SLIME_SQUISH_SMALL, 0.6f, 1.9f, 4)
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.4f, 1.3f, 5);
	public static final OmnitrixSfx.Cue SND_PUNCH = OmnitrixSfx.cue("ditto_copy_punch")
			.layer(SoundEvents.PLAYER_ATTACK_WEAK, 0.3f, 1.5f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_OUCH = OmnitrixSfx.cue("ditto_feedback")
			.layer(SoundEvents.PLAYER_HURT, 0.25f, 1.7f)
			.gap(6);

	// ---------------------------------------------------------------- server-only state
	private record Pile(Vec3 at, long when) {
	}

	private static final class Carry {
		final LivingEntity target;
		final List<DittoCopyEntity> holders;
		final Vec3 from;
		final long start;
		final boolean big;

		Carry(LivingEntity target, List<DittoCopyEntity> holders, Vec3 from, long start, boolean big) {
			this.target = target;
			this.holders = holders;
			this.from = from;
			this.start = start;
			this.big = big;
		}
	}

	private record Foe(long tick, @Nullable LivingEntity foe) {
	}

	private static final Map<UUID, List<DittoCopyEntity>> COPIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Pile> PILES = new ConcurrentHashMap<>();
	private static final Map<UUID, Carry> CARRIES = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> TOSSED = new ConcurrentHashMap<>();
	private static final Map<UUID, Foe> FOES = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private DittoKit() {
	}

	public static void register() {
		COPY = Registry.register(BuiltInRegistries.ENTITY_TYPE, ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("ditto_copy")),
				EntityType.Builder.<DittoCopyEntity>of(DittoCopyEntity::new, MobCategory.MISC).sized(0.34f, 1.0f).noSave().clientTrackingRange(8)
						.updateInterval(1).build("ditto_copy"));
		Aliens.register(new DittoKit());
	}

	public static void clearSessionState() {
		COPIES.clear();
		PILES.clear();
		CARRIES.clear();
		TOSSED.clear();
		FOES.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Ditto right now. */
	public static boolean isDitto(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// small (about Ben's waist), quick and nimble, light fists -- weak alone (the show)
		return AlienStats.builder().health(18f).speed(1.3f).scale(0.55f).armor(6f, 0f).attack(3f).jump(0.1f).safeFall(6f).reach(0.8f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(SWARM, AbilitySlot.SLOT_1, 100), AlienMove.shift(DOGPILE, AbilitySlot.SLOT_1, 200),
				AlienMove.tap(KICK, AbilitySlot.SLOT_2, 30), AlienMove.shift(BURST, AbilitySlot.SLOT_2, 240),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 20), AlienMove.shift(TOSS, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(PILE, AbilitySlot.SLOT_4, 160), AlienMove.shift(SHUFFLE, AbilitySlot.SLOT_4, 600),
				AlienMove.tap(SPLIT, AbilitySlot.SLOT_5, 10), AlienMove.shift(MERGE, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(ARMY, AbilitySlot.SLOT_6, 1200), AlienMove.shift(CARRY, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(SCRAP, "Attack", 0), AlienMove.vanilla(FELT, "Passive", 0), AlienMove.vanilla(CLIMB, "Passive (press into a wall)", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_6 -> flag(p, F_ARMY) || flag(p, F_CARRY);
			case SLOT_3 -> flag(p, F_TOSS);
			default -> false;
		};
	}

	@Override
	public boolean climbs(Player p) {
		return p.horizontalCollision; // the show: a great climber
	}

	// ---------------------------------------------------------------- copies

	/** His live copies (removed ones dropped first). */
	public static List<DittoCopyEntity> copies(ServerPlayer p) {
		List<DittoCopyEntity> l = COPIES.get(p.getUUID());
		if (l == null) {
			return List.of();
		}
		l.removeIf(c -> c.isRemoved());
		return List.copyOf(l);
	}

	/** How many copies he may have right now (more during the Ditto Army). */
	public int cap(ServerPlayer p) {
		return flag(p, F_ARMY) ? ARMY_COPIES : MAX_COPIES;
	}

	/** Splits off one copy at {@code at} if there is room; null if he is at his cap. */
	@Nullable
	public DittoCopyEntity addCopy(ServerPlayer p, Vec3 at) {
		List<DittoCopyEntity> l = COPIES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		l.removeIf(c -> c.isRemoved());
		if (l.size() >= cap(p)) {
			return null;
		}
		Set<Integer> used = new LinkedHashSet<>();
		for (DittoCopyEntity c : l) {
			used.add(c.slot());
		}
		int slot = 0;
		while (used.contains(slot)) {
			slot++;
		}
		DittoCopyEntity c = DittoCopyEntity.spawn(p, slot, at);
		l.add(c);
		level(p).sendParticles(ParticleTypes.POOF, at.x, at.y + 0.4, at.z, 4, 0.1, 0.2, 0.1, 0.01);
		QuirkFx.ring(level(p), at.add(0, 0.3, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, COLOUR).size(0.9f).thickness(0.15f).life(8).spawn();
		return c;
	}

	/** A spot beside him for a new copy ({@code i} goes round him). */
	private static Vec3 beside(ServerPlayer p, int i, double r) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD + Mth.PI * 0.5f + i * 2.1f;
		Vec3 at = p.position().add(-Mth.sin(yaw) * r, 0, Mth.cos(yaw) * r);
		// never inside a wall: fall back to his own spot
		return p.level().noCollision(COPY.getDimensions().makeBoundingBox(at)) ? at : p.position();
	}

	private static void removeCopies(ServerPlayer p) {
		List<DittoCopyEntity> l = COPIES.remove(p.getUUID());
		if (l != null) {
			for (DittoCopyEntity c : l) {
				c.pop();
			}
		}
	}

	/**
	 * v0.17.2 (the show: damage to one is felt by all): a copy took {@code taken} damage -- {@link #FEEDBACK} of it reaches him. It
	 * never gives him hurt-immunity frames (the next real hit still lands).
	 */
	public static void feedback(ServerPlayer p, float taken) {
		float d = taken * FEEDBACK;
		if (d <= 0f || !isDitto(p) || !p.isAlive()) {
			return;
		}
		int keep = p.invulnerableTime;
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), d);
		p.invulnerableTime = keep;
		OmnitrixSfx.play(level(p), p.position(), SND_OUCH, 0.6f, 1f);
	}

	/** One copy's punch on {@code e}. */
	public static void copyHit(ServerPlayer p, DittoCopyEntity c, LivingEntity e) {
		if (hurt(p, e, COPY_PUNCH, false)) {
			Vec3 d = e.position().subtract(c.position());
			e.knockback(0.15, -d.x, -d.z);
		}
		OmnitrixSfx.play(level(p), c.position(), SND_PUNCH, 0.7f, 1f);
	}

	/**
	 * Whatever his pack goes for on its own: what he just hit, what just hit him, or a monster hunting him (within 12 blocks) --
	 * cached once a tick.
	 */
	@Nullable
	public static LivingEntity foe(ServerPlayer p) {
		long now = p.level().getGameTime();
		Foe f = FOES.get(p.getUUID());
		if (f != null && f.tick == now) {
			return f.foe;
		}
		LivingEntity out = null;
		LivingEntity hit = p.getLastHurtMob();
		if (fair(p, hit) && p.tickCount - p.getLastHurtMobTimestamp() < FOE_MEMORY) {
			out = hit;
		}
		LivingEntity by = p.getLastHurtByMob();
		if (out == null && fair(p, by) && p.tickCount - p.getLastHurtByMobTimestamp() < FOE_MEMORY) {
			out = by;
		}
		if (out == null) {
			double best = Double.MAX_VALUE;
			for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12))) {
				if (m.getTarget() == p && fair(p, m) && m.distanceToSqr(p) < best) {
					best = m.distanceToSqr(p);
					out = m;
				}
			}
		}
		FOES.put(p.getUUID(), new Foe(now, out));
		return out;
	}

	private static boolean fair(ServerPlayer p, @Nullable LivingEntity e) {
		return e != null && e != p && e.isAlive() && !e.isRemoved() && e.level() == p.level() && e.distanceToSqr(p) < 16 * 16
				&& !com.projecthero.mod.omnitrix.OmnitrixRiding.partners(p, e)
				&& !(com.projecthero.mod.squad.Squads.areAllies(p, e) && !com.projecthero.mod.squad.Squads.friendlyFireOn(p));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 0.8f, 1f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		removeCopies(p);
		UUID id = p.getUUID();
		PILES.remove(id);
		Carry c = CARRIES.remove(id);
		if (c != null) {
			for (DittoCopyEntity h : c.holders) {
				h.setHolding(false);
			}
		}
		TOSSED.remove(id);
		FOES.remove(id);
		LAST_TICK.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(LEAP_UNTIL));
		n.timers.remove(key(ARMY_UNTIL));
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !((flag(p, F_LEAP) || flag(p, F_TOSS)) && source.is(DamageTypeTags.IS_FALL));
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		if (flag(p, F_LEAP) && now >= timer(p, LEAP_UNTIL)) {
			setFlag(p, F_LEAP, false);
		}
		// Copy Toss: the landing
		Long tossed = TOSSED.get(p.getUUID());
		if (tossed != null) {
			if (now - tossed >= 4 && p.onGround()) {
				TOSSED.remove(p.getUUID());
				setFlag(p, F_TOSS, false);
				land(p);
			} else if (now - tossed > TOSS_TICKS) {
				TOSSED.remove(p.getUUID());
				setFlag(p, F_TOSS, false);
			}
		}
		// Pile-Up: the copies land
		Pile pile = PILES.get(p.getUUID());
		if (pile != null && now >= pile.when) {
			PILES.remove(p.getUUID());
			pileLand(p, pile.at);
		}
		tickCarry(p, l, now);
		if (flag(p, F_ARMY) && now >= timer(p, ARMY_UNTIL)) {
			setFlag(p, F_ARMY, false);
			// the army's extras merge back: he keeps his usual six
			List<DittoCopyEntity> l2 = new ArrayList<>(copies(p));
			l2.sort(Comparator.comparingInt(DittoCopyEntity::slot));
			for (int i = MAX_COPIES; i < l2.size(); i++) {
				l2.get(i).pop();
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CARRY) && slot == AbilitySlot.SLOT_6) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					dogpile(p);
				} else {
					swarm(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					burst(p);
				} else {
					kick(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					toss(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					shuffle(p);
				} else {
					pile(p);
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
					carry(p);
				} else {
					army(p);
				}
			}
			default -> {
			}
		}
	}

	private static void say(ServerPlayer p, String key) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ditto." + key).withStyle(ChatFormatting.GRAY), true);
	}

	/** At least {@code n} copies, splitting the missing ones off for free (a move that needs a pack brings its own). */
	private List<DittoCopyEntity> atLeast(ServerPlayer p, int n) {
		for (int i = copies(p).size(); i < n; i++) {
			if (addCopy(p, beside(p, i, 0.9)) == null) {
				break;
			}
		}
		return copies(p);
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: every copy rushes the creature he looks at and fights it. */
	public boolean swarm(ServerPlayer p) {
		LivingEntity t = lookTarget(p, SWARM_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (!use(p, SWARM)) {
			return false;
		}
		anim(p, ANIM_POINT);
		List<DittoCopyEntity> l = atLeast(p, 2);
		for (DittoCopyEntity c : l) {
			c.attack(t, SWARM_TICKS);
		}
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 0.8f, 1.3f);
		return true;
	}

	/** Shift+R: every copy leaps onto the creature he looks at and pins it. */
	public boolean dogpile(ServerPlayer p) {
		LivingEntity t = lookTarget(p, DOGPILE_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (!use(p, DOGPILE)) {
			return false;
		}
		anim(p, ANIM_POINT);
		List<DittoCopyEntity> l = atLeast(p, 1);
		int n = 0;
		for (DittoCopyEntity c : l) {
			float a = n * 2.4f;
			double r = t.getBbWidth() * 0.5 + 0.2;
			c.setPos(t.getX() + Mth.cos(a) * r, t.getY() + Math.min(t.getBbHeight() * 0.6, 1.2), t.getZ() + Mth.sin(a) * r);
			c.setDeltaMovement(Vec3.ZERO);
			c.attack(t, 100);
			if (n < COPY_HIT_CAP) {
				t.invulnerableTime = 0;
				hurt(p, t, DOGPILE_DAMAGE, false);
			}
			n++;
		}
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOGPILE_STUN, 3, false, true, true), p);
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DOGPILE_STUN, 0, false, true, true), p);
		level(p).sendParticles(ParticleTypes.POOF, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
		OmnitrixSfx.play(level(p), t.position(), SND_PILE, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** G: a spinning kick round him, and round every copy. */
	public boolean kick(ServerPlayer p) {
		if (!use(p, KICK)) {
			return false;
		}
		anim(p, ANIM_KICK);
		Vec3 at = p.position();
		for (LivingEntity e : around(p, at.add(0, 0.4, 0), 2.5)) {
			if (hurt(p, e, KICK_DAMAGE, false)) {
				shove(e, at, 0.6, 0.15);
			}
		}
		Map<Integer, Integer> counts = new HashMap<>();
		for (DittoCopyEntity c : copies(p)) {
			for (LivingEntity e : around(p, c.position().add(0, 0.4, 0), 2.0)) {
				if (counts.merge(e.getId(), 1, Integer::sum) <= COPY_HIT_CAP) {
					e.invulnerableTime = 0;
					if (hurt(p, e, KICK_COPY_DAMAGE, false)) {
						shove(e, c.position(), 0.3, 0.1);
					}
				}
			}
		}
		QuirkFx.ring(level(p), at.add(0, 0.35, 0)).colours(0xFFFFFF, COLOUR).size(2.5f).thickness(0.25f).life(6).spawn();
		OmnitrixSfx.play(level(p), at, SND_KICK, 1f, 1f);
		return true;
	}

	/** Shift+G: bursts into three copies at once, throwing everything near back. */
	public boolean burst(ServerPlayer p) {
		if (!use(p, BURST)) {
			return false;
		}
		anim(p, ANIM_BURST);
		Vec3 at = p.position();
		int base = copies(p).size();
		for (int i = 0; i < 3; i++) {
			if (addCopy(p, beside(p, base + i, 1.4)) == null) {
				break;
			}
		}
		for (LivingEntity e : around(p, at.add(0, 0.4, 0), 3.0)) {
			if (hurt(p, e, BURST_DAMAGE, false)) {
				shove(e, at, 1.0, 0.3);
			}
		}
		QuirkFx.ring(level(p), at.add(0, 0.2, 0)).colours(0xFFFFFF, COLOUR).size(3.5f).thickness(0.4f).life(10).ground().spawn();
		OmnitrixSfx.play(level(p), at, SND_ARMY, 0.8f, 1.1f);
		return true;
	}

	// ---------------------------------------------------------------- X / Shift+X

	private static Vec3 flat(Vec3 look) {
		Vec3 h = new Vec3(look.x, 0, look.z);
		return h.lengthSqr() < 1.0e-4 ? Vec3.ZERO : h.normalize();
	}

	/** X: a long, agile leap; the copies bound after him. */
	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 h = flat(p.getLookAngle());
		p.setDeltaMovement(h.x * 0.95, 0.7, h.z * 0.95);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		Vec3 land = p.position().add(h.scale(6));
		for (DittoCopyEntity c : copies(p)) {
			c.dashTo(land.add(c.packPos(p).subtract(p.position())), 24);
			c.setDeltaMovement(c.getDeltaMovement().x, 0.55, c.getDeltaMovement().z);
		}
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 1f, 1f);
		return true;
	}

	/** Shift+X: the nearest copy hurls him where he looks; the landing hits. */
	public boolean toss(ServerPlayer p) {
		DittoCopyEntity thrower = null;
		for (DittoCopyEntity c : copies(p)) {
			if (c.distanceToSqr(p) <= TOSS_REACH * TOSS_REACH && (thrower == null || c.distanceToSqr(p) < thrower.distanceToSqr(p))) {
				thrower = c;
			}
		}
		if (thrower == null) {
			say(p, "no_copy_near");
			return false;
		}
		if (!use(p, TOSS)) {
			return false;
		}
		anim(p, ANIM_TOSS);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.x * 1.7, Math.max(0.45, look.y * 1.2 + 0.45), look.z * 1.7);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		thrower.level().broadcastEntityEvent(thrower, (byte) 4);
		TOSSED.put(p.getUUID(), p.level().getGameTime());
		setFlag(p, F_TOSS, true);
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 1f, 0.8f);
		return true;
	}

	private void land(ServerPlayer p) {
		Vec3 at = p.position();
		for (LivingEntity e : around(p, at.add(0, 0.4, 0), 2.5)) {
			if (hurt(p, e, TOSS_DAMAGE, false)) {
				shove(e, at, 0.7, 0.25);
			}
		}
		p.fallDistance = 0f;
		level(p).sendParticles(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 8, 0.6, 0.05, 0.6, 0.02);
		OmnitrixSfx.play(level(p), at, SND_PILE, 0.8f, 1.2f);
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Where he looks: the creature, else the block, within {@code range}; else the end of the look. */
	private static Vec3 spot(ServerPlayer p, double range) {
		LivingEntity t = lookTarget(p, range);
		if (t != null) {
			return t.position();
		}
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** Z: the copies dive onto the spot he looks at. */
	public boolean pile(ServerPlayer p) {
		if (!use(p, PILE)) {
			return false;
		}
		anim(p, ANIM_POINT);
		Vec3 at = spot(p, PILE_RANGE);
		List<DittoCopyEntity> l = atLeast(p, 2);
		for (DittoCopyEntity c : l) {
			c.dashTo(at, PILE_DELAY + 4);
			c.setDeltaMovement(c.getDeltaMovement().x, 0.6, c.getDeltaMovement().z);
		}
		PILES.put(p.getUUID(), new Pile(at, p.level().getGameTime() + PILE_DELAY));
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 1f, 1.2f);
		return true;
	}

	private void pileLand(ServerPlayer p, Vec3 at) {
		int there = 0;
		for (DittoCopyEntity c : copies(p)) {
			if (c.position().distanceToSqr(at) < 3.5 * 3.5) {
				there++;
			}
		}
		float dmg = PILE_DAMAGE + PILE_COPY_DAMAGE * Math.min(there, COPY_HIT_CAP);
		for (LivingEntity e : around(p, at.add(0, 0.4, 0), 3.0)) {
			if (hurt(p, e, dmg, false)) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true, true), p);
				shove(e, at, 0.4, 0.2);
			}
		}
		level(p).sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 14, 0.8, 0.2, 0.8, 0.03);
		QuirkFx.ring(level(p), at.add(0, 0.1, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.4f).life(10).ground().spawn();
		OmnitrixSfx.play(level(p), at, SND_PILE, 1.2f, 0.9f);
	}

	/** Shift+Z: swaps places with a random copy; whatever hunted him loses him; a moment unseen. */
	public boolean shuffle(ServerPlayer p) {
		if (!use(p, SHUFFLE)) {
			return false;
		}
		anim(p, ANIM_SHUFFLE);
		List<DittoCopyEntity> l = copies(p);
		if (!l.isEmpty()) {
			DittoCopyEntity c = l.get(p.getRandom().nextInt(l.size()));
			Vec3 me = p.position(), it = c.position();
			p.teleportTo(it.x, it.y, it.z);
			c.setPos(me.x, me.y, me.z);
			p.fallDistance = 0f;
		}
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(24))) {
			if (m.getTarget() == p) {
				m.setTarget(null);
				if (m.getLastHurtByMob() == p) {
					m.setLastHurtByMob(null);
				}
			}
		}
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SHUFFLE_INVIS, 0, false, false, true));
		level(p).sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.4, p.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_MERGE, 0.8f, 1.2f);
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
		addCopy(p, beside(p, copies(p).size(), 0.9));
		OmnitrixSfx.play(level(p), p.position(), SND_SPLIT, 1f, 1f);
		return true;
	}

	/** Shift+V: the copies run back into him (+2 health each). */
	public boolean merge(ServerPlayer p) {
		List<DittoCopyEntity> l = copies(p);
		if (l.isEmpty()) {
			say(p, "no_copies");
			return false;
		}
		if (!use(p, MERGE)) {
			return false;
		}
		anim(p, ANIM_MERGE);
		for (DittoCopyEntity c : l) {
			QuirkFx.ribbon(level(p), c.position().add(0, 0.4, 0), p.position().add(0, 0.4, 0)).style(QuirkFx.RIBBON_LIGHT).colours(0xFFFFFF, COLOUR)
					.size(0.15f).life(6).spawn();
			c.pop();
		}
		COPIES.remove(p.getUUID());
		p.heal(MERGE_HEAL * l.size());
		OmnitrixSfx.play(level(p), p.position(), SND_MERGE, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C (ultimate): an army of copies at once, each going for its own creature. */
	public boolean army(ServerPlayer p) {
		if (flag(p, F_ARMY)) {
			return false;
		}
		if (!use(p, ARMY)) {
			return false;
		}
		anim(p, ANIM_ARMY);
		setFlag(p, F_ARMY, true);
		setTimer(p, ARMY_UNTIL, p.level().getGameTime() + ARMY_TICKS);
		int i = copies(p).size();
		while (addCopy(p, beside(p, i, 0.8 + (i % 3) * 0.5)) != null) {
			i++;
		}
		List<LivingEntity> foes = new ArrayList<>(around(p, p.position(), ARMY_RANGE));
		foes.removeIf(e -> e instanceof Player && !(e instanceof Mob)); // players only when they started it (the pack's own foe)
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		List<DittoCopyEntity> l = copies(p);
		if (!foes.isEmpty()) {
			for (int k = 0; k < l.size(); k++) {
				l.get(k).attack(foes.get(k % foes.size()), ARMY_TICKS);
			}
		}
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).colours(0xFFFFFF, COLOUR).size(5f).thickness(0.5f).life(14).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_ARMY, 1.4f, 1f);
		return true;
	}

	/** Shift+C: two copies lift the creature he looks at and hurl it. */
	public boolean carry(ServerPlayer p) {
		LivingEntity t = lookTarget(p, CARRY_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (copies(p).size() < 2) {
			say(p, "need_two");
			return false;
		}
		if (!use(p, CARRY)) {
			return false;
		}
		anim(p, ANIM_CARRY);
		List<DittoCopyEntity> l = new ArrayList<>(copies(p));
		l.sort(Comparator.comparingDouble(c -> c.distanceToSqr(t)));
		List<DittoCopyEntity> holders = List.of(l.get(0), l.get(1));
		boolean big = t.getMaxHealth() > CARRY_MAX_HEALTH;
		for (DittoCopyEntity c : holders) {
			c.setHolding(true);
		}
		CARRIES.put(p.getUUID(), new Carry(t, holders, t.position(), p.level().getGameTime(), big));
		setFlag(p, F_CARRY, true);
		OmnitrixSfx.play(level(p), t.position(), SND_SPLIT, 1f, 0.8f);
		return true;
	}

	private void tickCarry(ServerPlayer p, ServerLevel l, long now) {
		Carry c = CARRIES.get(p.getUUID());
		if (c == null) {
			return;
		}
		long t = now - c.start;
		boolean gone = !c.target.isAlive() || c.target.isRemoved() || c.holders.stream().anyMatch(h -> h.isRemoved());
		if (gone || t >= CARRY_LIFT) {
			CARRIES.remove(p.getUUID());
			setFlag(p, F_CARRY, false);
			for (DittoCopyEntity h : c.holders) {
				h.setHolding(false);
			}
			if (gone) {
				return;
			}
			c.target.invulnerableTime = 0;
			if (c.big) {
				hurt(p, c.target, CARRY_SLAM_DAMAGE, false); // too heavy to throw: slammed back down
			} else {
				Vec3 h = flat(p.getLookAngle());
				hurt(p, c.target, CARRY_DAMAGE, false);
				c.target.setDeltaMovement(h.x * 1.8, 0.5, h.z * 1.8);
				c.target.hurtMarked = true;
			}
			l.sendParticles(ParticleTypes.POOF, c.target.getX(), c.target.getY() + 0.3, c.target.getZ(), 8, 0.4, 0.2, 0.4, 0.02);
			OmnitrixSfx.play(l, c.target.position(), SND_PILE, 1f, 1f);
			return;
		}
		// lifting onto their raised hands, just over their heads (a big one barely leaves the ground)
		double lift = (c.big ? 0.4 : COPY.getHeight() * 1.15) * Math.min(1.0, t / 8.0);
		Vec3 at = c.from.add(0, lift, 0);
		c.target.teleportTo(at.x, at.y, at.z);
		c.target.setDeltaMovement(Vec3.ZERO);
		c.target.fallDistance = 0f;
		int i = 0;
		for (DittoCopyEntity h : c.holders) {
			double side = (i++ == 0 ? -1 : 1) * (c.target.getBbWidth() * 0.5 + 0.2);
			float yaw = p.getYRot() * Mth.DEG_TO_RAD;
			h.setPos(at.x + Mth.cos(yaw) * side, c.from.y, at.z + Mth.sin(yaw) * side); // standing under it
			h.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** Test hook: whether a carry is running. */
	public static boolean carrying(ServerPlayer p) {
		return CARRIES.containsKey(p.getUUID());
	}
}
