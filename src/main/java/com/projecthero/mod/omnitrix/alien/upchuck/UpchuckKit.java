package com.projecthero.mod.omnitrix.alien.upchuck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeMachines;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "heres upchucks skin please build him: https://www.planetminecraft.com/skin/upchuck/ ... remove the pixel omnitrix
 * from his skin model and replace it with our omnitrix badge please. do research on his powers and look at what he does in the show
 * ... Make this another alien you unlock on the 11th mistransformation"). Upchuck, a Perk Gourmand (prototype Omnitrix, a hidden
 * alien): a small green glutton with three long, sticky tongues and a stomach that holds almost anything. Everything he eats -- blocks,
 * items, creatures, even shots fired at him -- fills his <b>Gut</b> (0-100); his spits ("loogies") spend it and hit harder the fuller
 * it is, and harder still when what he ate was rich (ore, metal, energy). Spec: {@code docs/OMNITRIX_REVAMP_UPCHUCK.md}.
 *
 * <pre>
 *   R          Gut Bomb         an explosive loogie (15 Gut): 9-22 by Gut, blast 2-3.5; with under 15 a Slime Spit (5, slowed)
 *   Shift+R    Curveball        a loogie that curves onto the creature you look at (24; 20 Gut): 12-20
 *   G          Tongue Grab      three tongues latch onto a creature (14) and reel it in for a chomp (10, +6 Gut); a big one reels HIM in
 *   Shift+G    Swallow Whole    gulps a small creature (5, 40 health at most): digested 4 s (2/s, +4 Gut/s), then spat out (8)
 *   X          Vomit Rocket     a blast of vomit launches him where he looks (8 Gut, else a hop); 5 behind him, no fall damage
 *   Shift+X    Tongue Zip       the tongues stick to a block (24) and haul him to it
 *   Z          Devour           eats the block he looks at and the four round it (6): no drops, Gut by what it was
 *   Shift+Z    Gobble           mouth wide 3 s: eats dropped items, XP and every shot that comes near (shots can't hurt him)
 *   V          Digest           20 Gut into 6 health; clears poison, hunger and nausea
 *   Shift+V    Slime Slick      spits a slick of slime (12): 4 on landing, everything in it slowed 5 s; melts snow and ice
 *   C          Mega Loogie      ULTIMATE (40+ Gut, spends it all): one huge loogie, 18-32, blast 3-6
 *   Shift+C    Feeding Frenzy   10 s: double Gut from everything, Devour and Tongue Grab need no wait
 *   Attack     Chomp            his bite (+4), +3 Gut a bite
 *   Passives   Iron Stomach (no poison, hunger or nausea); Full Cheeks (over 75 Gut he is slowed); Gut slowly digests (-1 / 5 s)
 * </pre>
 */
public final class UpchuckKit extends AlienKit {
	public static final String ID = "upchuck";
	public static final int COLOUR = 0x7CC83A;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_GOBBLE = 1, F_FRENZY = 2, F_ROCKET = 4, F_ZIP = 8, F_FULL = 16, F_GULPED = 32;
	public static final int ANIM_SPIT = 1, ANIM_TONGUE = 2, ANIM_GULP = 3, ANIM_ROCKET = 4, ANIM_EAT = 5, ANIM_GOBBLE = 6, ANIM_DIGEST = 7,
			ANIM_MEGA = 8, ANIM_FRENZY = 9, ANIM_CHOMP = 10;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.upchuck.<id>)
	public static final String BOMB = "gut_bomb", CURVE = "curveball", GRAB = "tongue_grab", SWALLOW = "swallow_whole", ROCKET = "vomit_rocket",
			ZIP = "tongue_zip", DEVOUR = "devour", GOBBLE = "gobble", DIGEST = "digest", SLICK = "slime_slick", MEGA = "mega_loogie",
			FRENZY = "feeding_frenzy", CHOMP = "chomp", STOMACH = "iron_stomach", CHEEKS = "full_cheeks";

	// ---------------------------------------------------------------- the Gut
	public static final int MAX_GUT = 100, FULL_CHEEKS = 75;
	public static final String GUT = "gut", RICH = "rich", GOBBLE_UNTIL = "gobble_until", FRENZY_UNTIL = "frenzy_until", ROCKET_UNTIL = "rocket_until";
	public static final int BOMB_COST = 15, CURVE_COST = 20, ROCKET_COST = 8, DIGEST_COST = 20, MEGA_MIN = 40;
	/** Gut digests on its own: 1 point every this many ticks. */
	public static final int DECAY_TICKS = 100;

	// ---------------------------------------------------------------- numbers (the prototype's scale: quick ~9-14, normal 14-22, big 22-28)
	public static final float BOMB_MIN = 9f, BOMB_MAX = 22f, SLIME_SPIT_DAMAGE = 5f, CURVE_MIN = 12f, CURVE_MAX = 20f, CHOMP_DAMAGE = 10f,
			GULP_DIGEST = 2f, GULP_SPIT_DAMAGE = 8f, ROCKET_DAMAGE = 5f, SLICK_DAMAGE = 4f, MEGA_MIN_DAMAGE = 18f, MEGA_MAX_DAMAGE = 32f,
			DIGEST_HEAL = 6f;
	/** Rich food (ore, metal, energy) makes loogies up to this much stronger. */
	public static final float RICH_BONUS = 0.2f;
	public static final double GRAB_RANGE = 14, SWALLOW_RANGE = 5, CURVE_RANGE = 24, ZIP_RANGE = 24, DEVOUR_RANGE = 6, GOBBLE_RADIUS = 5,
			SLICK_RANGE = 12, SLICK_RADIUS = 2.5;
	public static final float SWALLOW_MAX_HEALTH = 40f, GRAB_MAX_HEALTH = 100f;
	public static final int GRAB_TICKS = 10, GULP_TICKS = 80, ZIP_TICKS = 30, GOBBLE_TICKS = 60, FRENZY_TICKS = 200, ROCKET_SAFE = 40,
			SLICK_TICKS = 100;

	// ---------------------------------------------------------------- sounds
	public static final OmnitrixSfx.Cue SND_SPIT = OmnitrixSfx.cue("upchuck_spit")
			.layer(SoundEvents.LLAMA_SPIT, 0.7f, 0.7f)
			.layer(SoundEvents.SLIME_SQUISH, 0.4f, 0.8f);
	public static final OmnitrixSfx.Cue SND_BOOM = OmnitrixSfx.cue("upchuck_boom")
			.layer(SoundEvents.GENERIC_EXPLODE, 0.45f, 1.5f)
			.layer(SoundEvents.SLIME_SQUISH, 0.6f, 0.6f);
	public static final OmnitrixSfx.Cue SND_TONGUE = OmnitrixSfx.cue("upchuck_tongue")
			.layer(SoundEvents.FROG_TONGUE, 0.75f, 0.8f)
			.layer(SoundEvents.FROG_TONGUE, 0.6f, 1.1f, 2);
	public static final OmnitrixSfx.Cue SND_GULP = OmnitrixSfx.cue("upchuck_gulp")
			.layer(SoundEvents.GENERIC_DRINK, 0.7f, 0.5f)
			.layer(SoundEvents.FROG_EAT, 0.75f, 0.7f, 2);
	public static final OmnitrixSfx.Cue SND_CHEW = OmnitrixSfx.cue("upchuck_chew")
			.layer(SoundEvents.GENERIC_EAT, 0.6f, 0.8f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_BURP = OmnitrixSfx.cue("upchuck_burp")
			.layer(SoundEvents.PLAYER_BURP, 0.75f, 0.6f);
	public static final OmnitrixSfx.Cue SND_MEGA = OmnitrixSfx.cue("upchuck_mega")
			.layer(SoundEvents.PLAYER_BURP, 0.75f, 0.5f)
			.layer(SoundEvents.LLAMA_SPIT, 0.75f, 0.5f, 2)
			.layer(SoundEvents.SLIME_SQUISH, 0.7f, 0.5f, 3);

	// ---------------------------------------------------------------- server-only state
	/** Tongue Grab: three tongues on {@code target}; {@code reelSelf} for a creature too big to pull (it reels HIM in). */
	private record Grab(LivingEntity target, long start, Vec3 from, boolean reelSelf, List<AlienProjectileEntity> tongues) {
	}

	private record Gulp(LivingEntity prey, long start) {
	}

	private record Zip(Vec3 to, long start, List<AlienProjectileEntity> tongues) {
	}

	private record Curve(AlienProjectileEntity ball, LivingEntity target) {
	}

	private record Slick(Vec3 at, long until) {
	}

	private static final Map<UUID, Grab> GRABS = new ConcurrentHashMap<>();
	private static final Map<UUID, Gulp> GULPS = new ConcurrentHashMap<>();
	private static final Map<UUID, Zip> ZIPS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Curve>> CURVES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Slick>> SLICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private UpchuckKit() {
	}

	public static void register() {
		Aliens.register(new UpchuckKit());
		AlienProjectileEntity.trail("upchuck_spit", new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)));
		AlienProjectileEntity.trail("upchuck_mega", ParticleTypes.ITEM_SLIME);
		AlienProjectileEntity.trail("upchuck_curve", ParticleTypes.ITEM_SLIME);
		AlienProjectileEntity.onHit("upchuck_spit", (proj, hit) -> splat(proj, 1f));
		AlienProjectileEntity.onHit("upchuck_curve", (proj, hit) -> splat(proj, 1f));
		AlienProjectileEntity.onHit("upchuck_mega", (proj, hit) -> splat(proj, 2.2f));
		AlienProjectileEntity.trail("upchuck_slime_spit", ParticleTypes.ITEM_SLIME);
		AlienProjectileEntity.onHit("upchuck_slime_spit", (proj, hit) -> {
			if (hit instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof LivingEntity e) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true, true));
			}
			if (proj.level() instanceof ServerLevel l) {
				l.sendParticles(ParticleTypes.ITEM_SLIME, proj.getX(), proj.getY(), proj.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
			}
		});
		AlienProjectileEntity.trail("upchuck_slime", ParticleTypes.ITEM_SLIME);
		AlienProjectileEntity.onHit("upchuck_slime", (proj, hit) -> {
			if (proj.getOwner() instanceof ServerPlayer p && Aliens.kit(ID) instanceof UpchuckKit k) {
				k.slickLand(p, hit.getLocation());
			}
		});
	}

	public static void clearSessionState() {
		GRABS.clear();
		GULPS.clear();
		ZIPS.clear();
		CURVES.clear();
		SLICKS.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Upchuck right now. */
	public static boolean isUpchuck(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// small (about Ben's chest, like Ditto), tough for his size (the show: shrugs off a car), a strong bite, good jumper
		return AlienStats.builder().health(24f).speed(1.05f).scale(0.6f).armor(8f, 1f).knockbackResist(0.2f).attack(4f).jump(0.15f).safeFall(5f)
				.reach(0.6f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BOMB, AbilitySlot.SLOT_1, 20), AlienMove.shift(CURVE, AbilitySlot.SLOT_1, 60),
				AlienMove.tap(GRAB, AbilitySlot.SLOT_2, 80), AlienMove.shift(SWALLOW, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(ROCKET, AbilitySlot.SLOT_3, 40), AlienMove.shift(ZIP, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(DEVOUR, AbilitySlot.SLOT_4, 10), AlienMove.shift(GOBBLE, AbilitySlot.SLOT_4, 300),
				AlienMove.tap(DIGEST, AbilitySlot.SLOT_5, 100), AlienMove.shift(SLICK, AbilitySlot.SLOT_5, 200),
				AlienMove.tap(MEGA, AbilitySlot.SLOT_6, 1200), AlienMove.shift(FRENZY, AbilitySlot.SLOT_6, 900),
				AlienMove.vanilla(CHOMP, "Attack", 0), AlienMove.vanilla(STOMACH, "Passive", 0), AlienMove.vanilla(CHEEKS, "Passive", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_4 -> flag(p, F_GOBBLE);
			case SLOT_6 -> flag(p, F_FRENZY);
			case SLOT_3 -> flag(p, F_ZIP);
			case SLOT_2 -> flag(p, F_GULPED);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the Gut

	/** His Gut, 0..100 (client-safe: synced timers). */
	public static int gut(Player p) {
		return (int) Mth.clamp(Omnitrix.state(p).readyAt(ID + "." + GUT), 0, MAX_GUT);
	}

	/** How much of his Gut is rich food (ore, metal, energy), 0..gut. */
	public static int rich(Player p) {
		return (int) Mth.clamp(Omnitrix.state(p).readyAt(ID + "." + RICH), 0, gut(p));
	}

	/** 1 .. 1 + {@link #RICH_BONUS}: how much rich food in his Gut adds to a loogie. */
	public static float richness(Player p) {
		int g = gut(p);
		return g <= 0 ? 1f : 1f + RICH_BONUS * rich(p) / (float) g;
	}

	private void setGut(ServerPlayer p, int gut, int rich) {
		gut = Mth.clamp(gut, 0, MAX_GUT);
		rich = Mth.clamp(rich, 0, gut);
		OmnitrixState n = Omnitrix.state(p).copy();
		if (gut <= 0) {
			n.timers.remove(key(GUT));
		} else {
			n.timers.put(key(GUT), (long) gut);
		}
		if (rich <= 0) {
			n.timers.remove(key(RICH));
		} else {
			n.timers.put(key(RICH), (long) rich);
		}
		Omnitrix.save(p, n);
	}

	/** Test hook / the tune command: sets his Gut outright. */
	public void fill(ServerPlayer p, int gut, int rich) {
		setGut(p, gut, rich);
	}

	/** He ate something worth {@code points} ({@code rich}: ore, metal, energy). Doubled in a Feeding Frenzy. */
	public void eat(ServerPlayer p, int points, boolean isRich) {
		if (points <= 0) {
			return;
		}
		if (flag(p, F_FRENZY)) {
			points *= 2;
		}
		int g = gut(p), r = rich(p);
		int add = Math.min(points, MAX_GUT - g);
		setGut(p, g + add, isRich ? r + add : r);
	}

	/** Spends {@code points} of Gut; what is rich goes in proportion. */
	private void spend(ServerPlayer p, int points) {
		int g = gut(p), r = rich(p);
		int left = Math.max(0, g - points);
		setGut(p, left, g <= 0 ? 0 : Math.round(r * (left / (float) g)));
	}

	/** What a block is worth: soft things a little, stone more, ore and metal a lot (and rich). {points, rich ? 1 : 0}. */
	public static int[] worth(BlockState s) {
		String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		boolean rich = richName(path);
		if (rich) {
			return new int[] { path.endsWith("_block") || path.contains("netherite") || path.contains("ancient_debris") ? 15 : 8, 1 };
		}
		if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)) {
			return new int[] { 10, 0 };
		}
		if (s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS) || s.is(BlockTags.WOOL)) {
			return new int[] { 3, 0 }; // tasty
		}
		return new int[] { 2, 0 };
	}

	private static boolean richName(String path) {
		return path.contains("ore") || path.contains("iron") || path.contains("gold") || path.contains("copper") || path.contains("diamond")
				|| path.contains("emerald") || path.contains("netherite") || path.contains("debris") || path.contains("redstone")
				|| path.contains("glowstone") || path.contains("tnt") || path.contains("lapis") || path.contains("amethyst") || path.contains("quartz")
				|| path.contains("blaze") || path.contains("gunpowder") || path.contains("fire_charge") || path.contains("magma");
	}

	/** What a dropped stack is worth: food by its nutrition, anything else a point an item (8 a stack at most). */
	public static int[] worth(ItemStack s) {
		var food = s.get(net.minecraft.core.component.DataComponents.FOOD);
		String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		int each = food != null ? Math.max(1, food.nutrition()) : 1;
		return new int[] { Math.min(12, each * Math.min(s.getCount(), 8)), richName(path) ? 1 : 0 };
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_BURP, 0.8f, 1.2f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		Grab g = GRABS.remove(id);
		if (g != null) {
			discardAll(g.tongues);
		}
		Zip z = ZIPS.remove(id);
		if (z != null) {
			discardAll(z.tongues);
		}
		Gulp gulp = GULPS.remove(id);
		if (gulp != null) {
			release(p, gulp.prey, false); // whatever he swallowed comes back out, alive
		}
		CURVES.remove(id);
		SLICKS.remove(id);
		LAST_TICK.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { GUT, RICH, GOBBLE_UNTIL, FRENZY_UNTIL, ROCKET_UNTIL }) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
	}

	private static void discardAll(List<AlienProjectileEntity> l) {
		for (AlienProjectileEntity e : l) {
			if (!e.isRemoved()) {
				e.discard();
			}
		}
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (flag(p, F_ROCKET) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		// Gobble: whatever is shot at him goes down his throat
		return !(flag(p, F_GOBBLE) && source.is(DamageTypeTags.IS_PROJECTILE));
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity) {
			anim(p, ANIM_CHOMP);
			eat(p, 3, false);
			OmnitrixSfx.play(level(p), p.position(), SND_CHEW, 0.6f, 1.1f);
		}
		return false; // vanilla's hit, with his bite's +4
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		// Iron Stomach
		if (p.hasEffect(MobEffects.POISON)) {
			p.removeEffect(MobEffects.POISON);
		}
		if (p.hasEffect(MobEffects.HUNGER)) {
			p.removeEffect(MobEffects.HUNGER);
		}
		if (p.hasEffect(MobEffects.CONFUSION)) {
			p.removeEffect(MobEffects.CONFUSION);
		}
		// the Gut digests slowly
		if (now % DECAY_TICKS == 0 && gut(p) > 0 && !GULPS.containsKey(p.getUUID())) {
			spend(p, 1);
		}
		// Full Cheeks: stuffed, he waddles
		boolean full = gut(p) > FULL_CHEEKS || GULPS.containsKey(p.getUUID());
		if (full != flag(p, F_FULL)) {
			setFlag(p, F_FULL, full);
		}
		if (full && now % 10 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 0, false, false, false));
		}
		if (flag(p, F_ROCKET) && (now >= timer(p, ROCKET_UNTIL) || (p.onGround() && now >= timer(p, ROCKET_UNTIL) - ROCKET_SAFE + 6))) {
			setFlag(p, F_ROCKET, false);
			p.fallDistance = 0f;
		}
		if (flag(p, F_GOBBLE)) {
			if (now >= timer(p, GOBBLE_UNTIL)) {
				setFlag(p, F_GOBBLE, false);
			} else {
				gobbleTick(p, l);
			}
		}
		if (flag(p, F_FRENZY) && now >= timer(p, FRENZY_UNTIL)) {
			setFlag(p, F_FRENZY, false);
		}
		tickGrab(p, l, now);
		tickZip(p, now);
		tickGulp(p, l, now);
		tickCurves(p);
		tickSlicks(p, l, now);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					curveball(p);
				} else {
					gutBomb(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					swallow(p);
				} else {
					tongueGrab(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					zip(p);
				} else {
					rocket(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					gobble(p);
				} else {
					devour(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					slick(p);
				} else {
					digest(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					frenzy(p);
				} else {
					mega(p);
				}
			}
			default -> {
			}
		}
	}

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.upchuck." + key, args).withStyle(ChatFormatting.GRAY), true);
	}

	/** The loogie's colour: lime, going yellow-gold the richer his Gut. */
	private static int spitColour(Player p) {
		float k = (richness(p) - 1f) / RICH_BONUS;
		int r = (int) Mth.lerp(k, 0x8C, 0xF0), g = (int) Mth.lerp(k, 0xFF, 0xE0), b = (int) Mth.lerp(k, 0x3A, 0x40);
		return (r << 16) | (g << 8) | b;
	}

	/** A loogie bursting: green slime everywhere. */
	private static void splat(AlienProjectileEntity proj, float scale) {
		if (!(proj.level() instanceof ServerLevel l)) {
			return;
		}
		Vec3 at = proj.position();
		l.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, (int) (14 * scale), 0.5 * scale, 0.4 * scale, 0.5 * scale, 0.15);
		l.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)), at.x, at.y, at.z, (int) (10 * scale), 0.3 * scale,
				0.3 * scale, 0.3 * scale, 0.2);
		l.sendParticles(scale > 1.5f ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		QuirkFx.ring(l, at).colours(0xD8FF8A, COLOUR).size(2.2f * scale).thickness(0.35f * scale).life(10).spawn();
		OmnitrixSfx.play(l, at, SND_BOOM, Math.min(2f, 0.8f * scale), scale > 1.5f ? 0.7f : 1.1f);
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** The Gut Bomb's direct damage for a Gut of {@code gut} (0..100) and his richness. */
	public static float bombDamage(int gut, float richness) {
		return Math.min(BOMB_MAX * (1f + RICH_BONUS), Mth.lerp(gut / (float) MAX_GUT, BOMB_MIN, BOMB_MAX) * richness);
	}

	/** R: an explosive loogie, stronger the fuller he is; with too little Gut a plain Slime Spit. */
	public boolean gutBomb(ServerPlayer p) {
		if (!use(p, BOMB)) {
			return false;
		}
		anim(p, ANIM_SPIT);
		int g = gut(p);
		if (g < BOMB_COST) {
			AlienProjectileEntity.of(p, "upchuck_slime_spit").colour(0x9BE05A).size(0.25f).damage(SLIME_SPIT_DAMAGE).gravity(0.03).speed(1.4).life(40)
					.spawn();
			say(p, "hungry");
			OmnitrixSfx.play(level(p), p.position(), SND_SPIT, 0.7f, 1.4f);
			return true;
		}
		float rich = richness(p);
		float dmg = bombDamage(g, rich);
		float frac = g / (float) MAX_GUT;
		AlienProjectileEntity.of(p, "upchuck_spit").colour(spitColour(p)).size(0.3f + 0.25f * frac).damage(dmg).blast(2f + 1.5f * frac, dmg * 0.7f)
				.gravity(0.025).speed(1.6).life(60).spawn();
		spend(p, BOMB_COST);
		OmnitrixSfx.play(level(p), p.position(), SND_SPIT, 1f, 1f);
		return true;
	}

	/** Shift+R: a loogie thrown like a frisbee -- it curves onto the creature he looks at. */
	public boolean curveball(ServerPlayer p) {
		LivingEntity t = lookTarget(p, CURVE_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (gut(p) < CURVE_COST) {
			say(p, "need_gut", CURVE_COST);
			return false;
		}
		if (!use(p, CURVE)) {
			return false;
		}
		anim(p, ANIM_SPIT);
		int g = gut(p);
		float dmg = Math.min(CURVE_MAX * (1f + RICH_BONUS), Mth.lerp(g / (float) MAX_GUT, CURVE_MIN, CURVE_MAX) * richness(p));
		// thrown off to one side, it swings back round onto the target
		Vec3 look = p.getLookAngle();
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 dir = look.add(side.scale(p.getRandom().nextBoolean() ? 0.7 : -0.7)).add(0, 0.15, 0).normalize();
		AlienProjectileEntity ball = AlienProjectileEntity.of(p, "upchuck_curve").colour(spitColour(p)).size(0.35f).damage(dmg).blast(1.8f, dmg * 0.5f)
				.speed(1.1).life(70).direction(dir).spawn();
		CURVES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Curve(ball, t));
		spend(p, CURVE_COST);
		OmnitrixSfx.play(level(p), p.position(), SND_SPIT, 1f, 1.3f);
		return true;
	}

	private void tickCurves(ServerPlayer p) {
		List<Curve> l = CURVES.get(p.getUUID());
		if (l == null) {
			return;
		}
		l.removeIf(c -> c.ball.isRemoved());
		for (Curve c : l) {
			if (!c.target.isAlive()) {
				continue;
			}
			Vec3 to = c.target.position().add(0, c.target.getBbHeight() * 0.5, 0).subtract(c.ball.position());
			Vec3 v = c.ball.getDeltaMovement();
			double speed = Math.max(0.9, v.length());
			Vec3 turn = v.normalize().lerp(to.normalize(), 0.22).normalize().scale(speed);
			c.ball.setDeltaMovement(turn);
		}
		if (l.isEmpty()) {
			CURVES.remove(p.getUUID());
		}
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** His mouth: low on his big face. */
	public static Vec3 mouth(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0).add(flat(p.getLookAngle()).scale(p.getBbWidth() * 0.5));
	}

	private static Vec3 flat(Vec3 look) {
		Vec3 h = new Vec3(look.x, 0, look.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	/** His three tongues to a creature ({@code to}) or a point, each a held projectile the client draws from his mouth. */
	private static List<AlienProjectileEntity> tongues(ServerPlayer p, @Nullable LivingEntity to, Vec3 at, int life) {
		List<AlienProjectileEntity> out = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			AlienProjectileEntity t = AlienProjectileEntity.of(p, "upchuck_tongue");
			Vec3 end = to == null ? at : to.position().add(0, to.getBbHeight() * 0.55, 0);
			t.moveTo(end.x, end.y, end.z, i * 120f, 0f); // the yaw tells the client which of the three it is
			t.colour(to == null ? 0 : to.getId() + 1).size(i).held(true).speed(0).life(life).damage(0f).spawn();
			out.add(t);
		}
		return out;
	}

	/** G: three tongues latch onto the creature he looks at and reel it in for a chomp (a big one reels him in instead). */
	public boolean tongueGrab(ServerPlayer p) {
		if (GRABS.containsKey(p.getUUID())) {
			return false;
		}
		LivingEntity t = lookTarget(p, GRAB_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (!use(p, GRAB)) {
			return false;
		}
		if (flag(p, F_FRENZY)) {
			cooldown(p, GRAB, 10);
		}
		anim(p, ANIM_TONGUE);
		boolean big = AlienStatus.isBoss(t) || t.getMaxHealth() > GRAB_MAX_HEALTH;
		GRABS.put(p.getUUID(), new Grab(t, p.level().getGameTime(), t.position(), big, tongues(p, t, t.position(), GRAB_TICKS + 4)));
		OmnitrixSfx.play(level(p), p.position(), SND_TONGUE, 1f, 1f);
		return true;
	}

	private void tickGrab(ServerPlayer p, ServerLevel l, long now) {
		Grab g = GRABS.get(p.getUUID());
		if (g == null) {
			return;
		}
		long t = now - g.start;
		if (!g.target.isAlive() || g.target.isRemoved() || g.target.level() != p.level()) {
			GRABS.remove(p.getUUID());
			discardAll(g.tongues);
			return;
		}
		Vec3 front = p.position().add(flat(p.getLookAngle()).scale(0.6 + g.target.getBbWidth() * 0.5));
		double k = Math.min(1.0, (t + 1) / (double) GRAB_TICKS);
		if (g.reelSelf) {
			// too heavy: the tongues haul HIM onto it
			Vec3 to = g.target.position().subtract(p.position());
			if (to.length() > 1.4 + g.target.getBbWidth() * 0.5) {
				p.setDeltaMovement(to.normalize().scale(0.9).add(0, 0.08, 0));
				p.hurtMarked = true;
				p.fallDistance = 0f;
			}
		} else {
			Vec3 at = g.from.lerp(front, k * k);
			g.target.teleportTo(at.x, Math.max(at.y, front.y), at.z);
			g.target.setDeltaMovement(Vec3.ZERO);
			g.target.fallDistance = 0f;
		}
		if (t >= GRAB_TICKS) {
			GRABS.remove(p.getUUID());
			discardAll(g.tongues);
			anim(p, ANIM_CHOMP);
			g.target.invulnerableTime = 0;
			if (hurt(p, g.target, CHOMP_DAMAGE, false)) {
				eat(p, 6, false);
			}
			l.sendParticles(ParticleTypes.ITEM_SLIME, g.target.getX(), g.target.getY() + g.target.getBbHeight() * 0.5, g.target.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
			OmnitrixSfx.play(l, p.position(), SND_CHEW, 1f, 0.9f);
		}
	}

	/** Shift+G: gulps a small creature whole -- it is digested a while, then spat back out. */
	public boolean swallow(ServerPlayer p) {
		if (GULPS.containsKey(p.getUUID())) {
			say(p, "mouth_full");
			return false;
		}
		LivingEntity t = lookTarget(p, SWALLOW_RANGE);
		if (t == null) {
			Grab g = GRABS.get(p.getUUID());
			t = g == null ? null : g.target; // the one on his tongues
		}
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (t instanceof Player || AlienStatus.isBoss(t) || t.getMaxHealth() > SWALLOW_MAX_HEALTH) {
			say(p, "too_big");
			return false;
		}
		if (!use(p, SWALLOW)) {
			return false;
		}
		Grab g = GRABS.remove(p.getUUID());
		if (g != null) {
			discardAll(g.tongues);
		}
		anim(p, ANIM_GULP);
		if (t.isPassenger()) {
			t.stopRiding();
		}
		t.ejectPassengers();
		GULPS.put(p.getUUID(), new Gulp(t, p.level().getGameTime()));
		setFlag(p, F_GULPED, true);
		holdInside(p, t);
		OmnitrixSfx.play(level(p), p.position(), SND_GULP, 1f, 1f);
		return true;
	}

	/** Keeps the prey in his stomach: on his spot (so neither pushes the other), unseen, unable to fight back. */
	private static void holdInside(ServerPlayer p, LivingEntity t) {
		t.teleportTo(p.getX(), p.getY() + 0.05, p.getZ());
		t.setDeltaMovement(Vec3.ZERO);
		t.fallDistance = 0f;
		t.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 9, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
		if (t instanceof Mob m) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
	}

	/** Test hook: what he has swallowed, or null. */
	@Nullable
	public static LivingEntity swallowed(ServerPlayer p) {
		Gulp g = GULPS.get(p.getUUID());
		return g == null ? null : g.prey;
	}

	private void tickGulp(ServerPlayer p, ServerLevel l, long now) {
		Gulp g = GULPS.get(p.getUUID());
		if (g == null) {
			return;
		}
		LivingEntity t = g.prey;
		if (t.isRemoved() || !t.isAlive() || t.level() != p.level()) {
			GULPS.remove(p.getUUID());
			setFlag(p, F_GULPED, false);
			if (!t.isAlive()) {
				// fully digested: a hearty meal
				eat(p, Math.min(20, (int) (t.getMaxHealth() / 2)), false);
				OmnitrixSfx.play(l, p.position(), SND_BURP, 1f, 0.8f);
				l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(), 10, 0.3, 0.2, 0.3, 0.05);
			}
			return;
		}
		holdInside(p, t);
		long age = now - g.start;
		if (age > 0 && age % 20 == 0) {
			// digesting: the damage is his own (not the framework's hurt, which would skip a creature sitting where he stands)
			t.invulnerableTime = 0;
			if (t.hurt(OmnitrixDamage.alienStrike(p), GULP_DIGEST)) {
				eat(p, 4, false);
			}
			OmnitrixSfx.play(l, p.position(), SND_CHEW, 0.5f, 0.7f);
		}
		if (age >= GULP_TICKS) {
			GULPS.remove(p.getUUID());
			setFlag(p, F_GULPED, false);
			release(p, t, true);
		}
	}

	/** Spits what he swallowed back out ({@code hard}: hurled out where he looks, with a hit). */
	private static void release(ServerPlayer p, LivingEntity t, boolean hard) {
		if (!t.isAlive() || t.isRemoved()) {
			return;
		}
		Vec3 out = mouth(p).add(flat(p.getLookAngle()).scale(0.4 + t.getBbWidth() * 0.5));
		t.removeEffect(MobEffects.INVISIBILITY);
		t.teleportTo(out.x, p.getY() + 0.2, out.z);
		if (hard) {
			Vec3 h = flat(p.getLookAngle());
			t.invulnerableTime = 0;
			t.hurt(OmnitrixDamage.alienStrike(p), GULP_SPIT_DAMAGE);
			t.setDeltaMovement(h.x * 1.3, 0.45, h.z * 1.3);
			t.hurtMarked = true;
			p.level().playSound(null, p.blockPosition(), SoundEvents.LLAMA_SPIT, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.6f);
		}
		((ServerLevel) p.level()).sendParticles(ParticleTypes.ITEM_SLIME, out.x, out.y + 0.4, out.z, 16, 0.3, 0.3, 0.3, 0.1);
	}

	// ---------------------------------------------------------------- X / Shift+X

	/** X: a blast of vomit launches him where he looks; it splashes what is behind him. */
	public boolean rocket(ServerPlayer p) {
		if (!use(p, ROCKET)) {
			return false;
		}
		anim(p, ANIM_ROCKET);
		boolean fuel = gut(p) >= ROCKET_COST;
		Vec3 look = p.getLookAngle();
		double power = fuel ? 1.7 : 0.8;
		p.setDeltaMovement(look.x * power, Math.max(fuel ? 0.55 : 0.45, look.y * power + 0.35), look.z * power);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		setTimer(p, ROCKET_UNTIL, p.level().getGameTime() + ROCKET_SAFE);
		setFlag(p, F_ROCKET, true);
		Vec3 behind = p.position().subtract(look.scale(1.0)).add(0, 0.3, 0);
		if (fuel) {
			spend(p, ROCKET_COST);
			for (LivingEntity e : around(p, behind, 2.0)) {
				if (hurt(p, e, ROCKET_DAMAGE, false)) {
					shove(e, p.position(), 0.6, 0.2);
				}
			}
			level(p).sendParticles(ParticleTypes.ITEM_SLIME, behind.x, behind.y, behind.z, 20, 0.4, 0.3, 0.4, 0.15);
			level(p).sendParticles(ParticleTypes.EXPLOSION, behind.x, behind.y, behind.z, 1, 0, 0, 0, 0);
		} else {
			say(p, "hungry");
		}
		OmnitrixSfx.play(level(p), p.position(), fuel ? SND_BOOM : SND_SPIT, 0.9f, 1.3f);
		return true;
	}

	/** Shift+X: the tongues stick to the block he looks at and haul him to it. */
	public boolean zip(ServerPlayer p) {
		if (ZIPS.containsKey(p.getUUID())) {
			return false;
		}
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(ZIP_RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK) {
			say(p, "no_block");
			return false;
		}
		if (!use(p, ZIP)) {
			return false;
		}
		anim(p, ANIM_TONGUE);
		Vec3 at = hit.getLocation();
		ZIPS.put(p.getUUID(), new Zip(at, p.level().getGameTime(), tongues(p, null, at, ZIP_TICKS + 2)));
		setFlag(p, F_ZIP, true);
		OmnitrixSfx.play(level(p), p.position(), SND_TONGUE, 1f, 1.2f);
		return true;
	}

	private void tickZip(ServerPlayer p, long now) {
		Zip z = ZIPS.get(p.getUUID());
		if (z == null) {
			return;
		}
		Vec3 to = z.to.subtract(p.position().add(0, p.getBbHeight() * 0.5, 0));
		if (to.length() < 1.2 || now - z.start > ZIP_TICKS) {
			ZIPS.remove(p.getUUID());
			discardAll(z.tongues);
			setFlag(p, F_ZIP, false);
			p.setDeltaMovement(p.getDeltaMovement().scale(0.3).add(0, 0.25, 0));
			p.hurtMarked = true;
			p.fallDistance = 0f;
			return;
		}
		p.setDeltaMovement(to.normalize().scale(Math.min(1.4, 0.5 + to.length() * 0.15)));
		p.hurtMarked = true;
		p.fallDistance = 0f;
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Whether he may eat the block at {@code pos}: Upgrade's rules (build rights, spawn protection, adventure, unbreakable), no fluids, no containers. */
	public static boolean edible(ServerPlayer p, ServerLevel l, BlockPos pos) {
		BlockState s = l.getBlockState(pos);
		if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
			return false;
		}
		if (l.getBlockEntity(pos) != null) {
			return false; // a chest, a furnace...: never swallow someone's things
		}
		return UpgradeMachines.mayTake(p, l, pos);
	}

	/** Z: the tongues snatch the block he looks at and the four round it on that face; eaten whole, nothing drops. */
	public boolean devour(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		ServerLevel l = level(p);
		BlockHitResult hit = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(DEVOUR_RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
				p));
		if (hit.getType() != HitResult.Type.BLOCK) {
			say(p, "no_block");
			return false;
		}
		BlockPos c = hit.getBlockPos();
		if (!edible(p, l, c)) {
			say(p, "cant_eat");
			return false;
		}
		if (!use(p, DEVOUR)) {
			return false;
		}
		if (flag(p, F_FRENZY)) {
			cooldown(p, DEVOUR, 0);
		}
		anim(p, ANIM_EAT);
		List<BlockPos> bites = new ArrayList<>();
		bites.add(c);
		Direction face = hit.getDirection();
		for (Direction d : Direction.values()) {
			if (d.getAxis() != face.getAxis()) {
				bites.add(c.relative(d));
			}
		}
		int points = 0;
		boolean anyRich = false;
		int eaten = 0;
		for (BlockPos b : bites) {
			if (!edible(p, l, b)) {
				continue;
			}
			BlockState s = l.getBlockState(b);
			int[] w = worth(s);
			points += w[0];
			anyRich |= w[1] == 1;
			eaten++;
			Vec3 bc = Vec3.atCenterOf(b);
			l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), bc.x, bc.y, bc.z, 10, 0.3, 0.3, 0.3, 0.1);
			l.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			l.gameEvent(p, net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, b);
		}
		eat(p, points, anyRich);
		tongues(p, null, hit.getLocation(), 5);
		OmnitrixSfx.play(l, p.position(), SND_TONGUE, 0.8f, 1.3f);
		OmnitrixSfx.play(l, p.position(), SND_CHEW, 1f, eaten > 3 ? 0.8f : 1f);
		return true;
	}

	/** Shift+Z: mouth wide open -- items, XP and shots near him go down. */
	public boolean gobble(ServerPlayer p) {
		if (!use(p, GOBBLE)) {
			return false;
		}
		anim(p, ANIM_GOBBLE);
		setTimer(p, GOBBLE_UNTIL, p.level().getGameTime() + GOBBLE_TICKS);
		setFlag(p, F_GOBBLE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_GULP, 1f, 1.3f);
		return true;
	}

	private void gobbleTick(ServerPlayer p, ServerLevel l) {
		Vec3 m = mouth(p);
		AABB box = new AABB(m, m).inflate(GOBBLE_RADIUS);
		for (Entity e : l.getEntities(p, box)) {
			if (e.isRemoved() || e.distanceToSqr(m) > GOBBLE_RADIUS * GOBBLE_RADIUS) {
				continue;
			}
			if (e instanceof ItemEntity it) {
				int[] w = worth(it.getItem());
				eat(p, w[0], w[1] == 1);
				gulpFx(l, e);
				it.discard();
			} else if (e instanceof ExperienceOrb orb) {
				p.giveExperiencePoints(orb.getValue()); // XP is still his: it just goes in by the mouth
				eat(p, 1, true);
				gulpFx(l, e);
				orb.discard();
			} else if (e instanceof Projectile pr && !(pr instanceof ThrownTrident) && pr.getOwner() != p && !(pr instanceof AlienProjectileEntity ap
					&& ap.held())) {
				boolean energy = pr instanceof AbstractHurtingProjectile; // fireballs, wither skulls, wind charges: energy is rich
				eat(p, energy ? 10 : 5, energy);
				gulpFx(l, e);
				pr.discard();
			}
		}
	}

	private static void gulpFx(ServerLevel l, Entity e) {
		l.sendParticles(ParticleTypes.ITEM_SLIME, e.getX(), e.getY() + 0.2, e.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
		l.playSound(null, e.blockPosition(), SoundEvents.FROG_EAT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** V: turns Gut into health and settles his stomach. */
	public boolean digest(ServerPlayer p) {
		if (gut(p) < DIGEST_COST) {
			say(p, "need_gut", DIGEST_COST);
			return false;
		}
		if (!use(p, DIGEST)) {
			return false;
		}
		anim(p, ANIM_DIGEST);
		spend(p, DIGEST_COST);
		p.heal(DIGEST_HEAL);
		p.removeEffect(MobEffects.POISON);
		p.removeEffect(MobEffects.HUNGER);
		p.removeEffect(MobEffects.CONFUSION);
		level(p).sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_BURP, 1f, 1f);
		return true;
	}

	/** Shift+V: a gob of slime that spreads into a slick where it lands. */
	public boolean slick(ServerPlayer p) {
		if (!use(p, SLICK)) {
			return false;
		}
		anim(p, ANIM_SPIT);
		AlienProjectileEntity.of(p, "upchuck_slime").colour(0x9BE05A).size(0.35f).damage(SLICK_DAMAGE).gravity(0.05).speed(1.2)
				.life(40).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_SPIT, 1f, 0.8f);
		return true;
	}

	/** The slime gob landed: a slick, and snow and ice under it melt (where he could build). */
	void slickLand(ServerPlayer p, Vec3 at) {
		ServerLevel l = level(p);
		SLICKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Slick(at, l.getGameTime() + SLICK_TICKS));
		BlockPos c = BlockPos.containing(at);
		for (BlockPos b : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
			BlockState s = l.getBlockState(b);
			if (b.distSqr(c) > 6.5 || !(s.is(BlockTags.ICE) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW))
					|| !UpgradeMachines.mayTake(p, l, b)) {
				continue;
			}
			l.setBlock(b, s.is(Blocks.ICE) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		l.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.1, at.z, 30, 1.2, 0.05, 1.2, 0.05);
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xC8FF8A, COLOUR).size((float) SLICK_RADIUS).thickness(0.5f).life(14).ground().spawn();
		OmnitrixSfx.play(l, at, SND_BOOM, 0.5f, 1.6f);
	}

	private void tickSlicks(ServerPlayer p, ServerLevel l, long now) {
		List<Slick> list = SLICKS.get(p.getUUID());
		if (list == null) {
			return;
		}
		list.removeIf(s -> now >= s.until);
		if (list.isEmpty()) {
			SLICKS.remove(p.getUUID());
			return;
		}
		for (Slick s : list) {
			if (now % 4 == 0) {
				l.sendParticles(ParticleTypes.ITEM_SLIME, s.at.x, s.at.y + 0.05, s.at.z, 4, SLICK_RADIUS * 0.5, 0.02, SLICK_RADIUS * 0.5, 0.0);
			}
			for (LivingEntity e : around(p, s.at, SLICK_RADIUS)) {
				if (Math.abs(e.getY() - s.at.y) > 1.5) {
					continue;
				}
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, true, true), p);
				if (now % 10 == 0 && e.onGround()) {
					// it's slippery: they slide about
					e.push((e.getRandom().nextDouble() - 0.5) * 0.4, 0, (e.getRandom().nextDouble() - 0.5) * 0.4);
					e.hurtMarked = true;
				}
			}
		}
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** The Mega Loogie's damage for a Gut of {@code gut} and his richness. */
	public static float megaDamage(int gut, float richness) {
		return Math.min(MEGA_MAX_DAMAGE * (1f + RICH_BONUS), Mth.lerp(gut / (float) MAX_GUT, MEGA_MIN_DAMAGE, MEGA_MAX_DAMAGE) * richness);
	}

	/** C (ultimate): everything in his Gut at once, one huge loogie. */
	public boolean mega(ServerPlayer p) {
		int g = gut(p);
		if (g < MEGA_MIN) {
			say(p, "need_gut", MEGA_MIN);
			return false;
		}
		if (!use(p, MEGA)) {
			return false;
		}
		anim(p, ANIM_MEGA);
		float frac = g / (float) MAX_GUT;
		float dmg = megaDamage(g, richness(p));
		AlienProjectileEntity.of(p, "upchuck_mega").colour(spitColour(p)).size(0.7f + 0.6f * frac).damage(dmg).blast(3f + 3f * frac, dmg * 0.8f)
				.gravity(0.02).speed(1.3).life(80).spawn();
		setGut(p, 0, 0);
		Vec3 back = p.getLookAngle().scale(-0.35);
		p.push(back.x, 0.1, back.z); // the recoil
		p.hurtMarked = true;
		OmnitrixSfx.play(level(p), p.position(), SND_MEGA, 1.5f, 1f);
		return true;
	}

	/** Shift+C: a Feeding Frenzy -- double Gut from everything, Devour and Tongue Grab with no wait. */
	public boolean frenzy(ServerPlayer p) {
		if (flag(p, F_FRENZY) || !use(p, FRENZY)) {
			return false;
		}
		anim(p, ANIM_FRENZY);
		setTimer(p, FRENZY_UNTIL, p.level().getGameTime() + FRENZY_TICKS);
		setFlag(p, F_FRENZY, true);
		cooldown(p, DEVOUR, 0);
		cooldown(p, GRAB, 0);
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).colours(0xD8FF8A, COLOUR).size(3f).thickness(0.4f).life(12).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_BURP, 1.2f, 0.7f);
		return true;
	}

	/** Test hook: whether a Tongue Grab is running. */
	public static boolean grabbing(ServerPlayer p) {
		return GRABS.containsKey(p.getUUID());
	}
}
