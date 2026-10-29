package com.projecthero.mod.omnitrix.alien.greymatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.network.MoonKnightScholarSightPayload;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Grey Matter, the Galvan -- a tiny grey frog-like genius (a third of a player: a 0.6-block-tall hitbox that squeezes
 * through one-block gaps, eyes at 0.53) with a big head and big black eyes. Very fragile (8 health) and quick; mobs keep losing
 * sight of him. He wins with his brain: pressure points, weak spots, gadgets and repairs.
 *
 * <pre>
 *   R        Nerve Pinch    stuns the creature in front of you for 3 s       Shift+R  Weak-Spot Strike  a precise crit (12)
 *   G        Analyze        reads a creature (health, armour, weakness) and  Shift+G  Decoy            a ticking gadget: mobs go after it instead (5 s)
 *                           marks it: +35% damage from everyone for 10 s
 *   X        Big Leap       a huge leap for something so small (no fall)     Shift+X  Scurry           5 s of top speed; hunters lose you
 *   Z        Snare Trap     quick-craft a tripwire snare (roots 4 s; 3 max)  Shift+Z  Smoke Bomb       blinds around you, everyone loses track
 *   V        Pocket Workshop  a crafting grid anywhere: he builds what he needs from his pockets
 *   C        Genius Repair  fix a block (anvils, cracked bricks) or boost a  Shift+C  Mind Map         ores, chests and spawners outlined through walls (8 s)
 *                           furnace / brewing stand; else repair held gear
 *   Attack   precision jab (+2 on an analyzed target)      Use on a creature   a quick read of its health
 * </pre>
 */
public final class GreyMatterKit extends AlienKit {
	public static final String ID = "grey_matter";
	public static final int COLOUR = 0xA6ACB4;

	// v0.17.1 sound rework: OmnitrixSfx recipes from vanilla samples (see docs/OMNITRIX_SOUNDS_0171.md)
	/** A tiny, quick pinch / bite. */
	public static final OmnitrixSfx.Cue PINCH = OmnitrixSfx.cue("grey_matter_pinch")
			.layer(SoundEvents.PLAYER_ATTACK_WEAK, 0.35f, 1.6f)
			.layer(SoundEvents.SILVERFISH_HURT, 0.22f, 1.4f);
	/** Sizing something up: a click, a page flick and a light zap. */
	public static final OmnitrixSfx.Cue SCAN = OmnitrixSfx.cue("grey_matter_scan")
			.layer(SoundEvents.LEVER_CLICK, 0.2f, 1.9f)
			.layer(SoundEvents.BOOK_PAGE_TURN, 0.25f, 1.5f)
			.layer(OmnitrixSounds.SWEEP, 0.12f, 2.0f, 1);
	/** A gadget snapping together, a light zap. */
	public static final OmnitrixSfx.Cue GADGET = OmnitrixSfx.cue("grey_matter_gadget")
			.layer(SoundEvents.CRAFTER_CRAFT, 0.35f, 1.5f)
			.layer(SoundEvents.LEVER_CLICK, 0.2f, 1.6f)
			.layer(OmnitrixSounds.SWEEP, 0.15f, 1.9f, 2)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.25f, 1.4f, 2);
	/** A little leap: tiny quick scurries. */
	public static final OmnitrixSfx.Cue LEAP = OmnitrixSfx.cue("grey_matter_leap")
			.layer(SoundEvents.SILVERFISH_STEP, 0.35f, 1.6f)
			.layer(SoundEvents.RABBIT_JUMP, 0.35f, 1.4f);
	/** Tinkering a repair: small clinks, a click, a spark. */
	public static final OmnitrixSfx.Cue REPAIR = OmnitrixSfx.cue("grey_matter_repair")
			.layer(SoundEvents.SMITHING_TABLE_USE, 0.22f, 1.7f)
			.layer(SoundEvents.CRAFTER_CRAFT, 0.25f, 1.8f, 3)
			.layer(SoundEvents.COPPER_BULB_TURN_ON, 0.2f, 1.5f, 5);
	/** Thinking: a squeak, a click, a page. */
	public static final OmnitrixSfx.Cue THINK = OmnitrixSfx.cue("grey_matter_think")
			.layer(SoundEvents.SILVERFISH_AMBIENT, 0.28f, 1.5f)
			.layer(SoundEvents.LEVER_CLICK, 0.15f, 2.0f)
			.layer(SoundEvents.BOOK_PAGE_TURN, 0.2f, 1.6f, 3);
	/** The decoy's lure (every half second): a soft tick and a squeak (was a note-block bit beep). */
	public static final OmnitrixSfx.Cue DECOY_SOUND = OmnitrixSfx.cue("grey_matter_decoy")
			.layer(SoundEvents.TRIPWIRE_CLICK_ON, 0.16f, 1.7f)
			.layer(SoundEvents.SILVERFISH_AMBIENT, 0.12f, 1.6f)
			.gap(8);

	public static final int ANIM_PINCH = 1;
	public static final int ANIM_STRIKE = 2;
	public static final int ANIM_ANALYZE = 3;
	public static final int ANIM_DECOY = 4;
	public static final int ANIM_LEAP = 5;
	public static final int ANIM_SCURRY = 6;
	public static final int ANIM_TRAP = 7;
	public static final int ANIM_SMOKE = 8;
	public static final int ANIM_REPAIR = 9;
	public static final int ANIM_MIND = 10;
	public static final int ANIM_JAB = 11;
	public static final int ANIM_READ = 12;

	public static final int F_LEAP = 1;
	public static final int F_SCURRY = 2;

	public static final String NERVE_PINCH = "nerve_pinch";
	public static final String WEAK_SPOT = "weak_spot_strike";
	public static final String ANALYZE = "analyze";
	public static final String DECOY = "decoy";
	public static final String BIG_LEAP = "big_leap";
	public static final String SCURRY = "scurry";
	public static final String SNARE = "snare_trap";
	public static final String SMOKE_BOMB = "smoke_bomb";
	public static final String REPAIR_MOVE = "genius_repair";
	public static final String MIND_MAP = "mind_map";
	public static final String JAB = "precision_jab";
	public static final String QUICK_READ = "quick_read";

	public static final float SCALE = 0.33f;
	public static final int STUN_TICKS = 60;
	public static final float STRIKE_DAMAGE = 12f;
	public static final float MARK_FACTOR = 1.35f;
	public static final int MARK_TICKS = 200;
	public static final int SCURRY_TICKS = 100;
	public static final int TRAP_LIFE = 1200;
	public static final int TRAP_ROOT = 80;
	public static final int MAX_TRAPS = 3;
	public static final int DECOY_TICKS = 100;
	public static final float REPAIR_FRACTION = 0.3f;
	public static final int BOOST_TICKS = 200;
	public static final int MIND_TICKS = 160;
	public static final int MIND_RANGE = 20;

	private static final ResourceLocation SCURRY_SPEED = PowerToggles.id("omnitrix_grey_matter_scurry");
	private static final DustParticleOptions GADGET_DUST = new DustParticleOptions(new Vector3f(0.6f, 0.65f, 0.7f), 0.8f);
	private static final DustParticleOptions LIGHT_DUST = new DustParticleOptions(new Vector3f(1f, 0.25f, 0.2f), 0.6f);
	private static final DustParticleOptions THINK_DUST = new DustParticleOptions(new Vector3f(0.5f, 0.9f, 1f), 0.7f);

	/** a marked creature -> who marked it and until when (it takes +35% damage from everyone). */
	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
	/** player -> their snares. */
	private static final Map<UUID, List<Trap>> TRAPS = new ConcurrentHashMap<>();
	/** player -> their decoy. */
	private static final Map<UUID, Decoy> DECOYS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private record Mark(UUID owner, long until) {
	}

	private record Trap(ResourceKey<Level> dim, Vec3 at, long until) {
	}

	private record Decoy(ResourceKey<Level> dim, Vec3 at, long until) {
	}

	private GreyMatterKit() {
	}

	public static void register() {
		Aliens.register(new GreyMatterKit());
		// Analyze's mark: a marked creature takes more from every source (re-issued behind a guard, the OmnitrixDamage pattern)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (REENTRANT.get() || amount <= 0f || MARKS.isEmpty() || entity.level().isClientSide()) {
				return true;
			}
			Mark m = MARKS.get(entity.getUUID());
			if (m == null) {
				return true;
			}
			if (m.until() <= entity.level().getGameTime()) {
				MARKS.remove(entity.getUUID());
				return true;
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			float more = amount * MARK_FACTOR;
			if (entity instanceof net.minecraft.world.entity.player.Player && source.scalesWithDifficulty()) {
				// a player's hurt scales by difficulty again on the re-issue: undo it (the OmnitrixDamage correction)
				more = switch (entity.level().getDifficulty()) {
					case HARD -> more / 1.5f;
					case EASY -> more >= 2f ? 2f * (more - 1f) : more;
					default -> more;
				};
			}
			REENTRANT.set(true);
			try {
				entity.hurt(source, more);
			} finally {
				REENTRANT.set(false);
			}
			return false;
		});
	}

	public static void clearSessionState() {
		MARKS.clear();
		TRAPS.clear();
		DECOYS.clear();
	}

	/** Whether Analyze has {@code e} marked right now. */
	public static boolean marked(LivingEntity e) {
		Mark m = MARKS.get(e.getUUID());
		return m != null && m.until() > e.level().getGameTime();
	}

	public static int traps(ServerPlayer p) {
		List<Trap> t = TRAPS.get(p.getUUID());
		return t == null ? 0 : t.size();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(8f).speed(1.35f).scale(SCALE).attack(0f).jump(0.05f).safeFall(1f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(NERVE_PINCH, AbilitySlot.SLOT_1, 120), AlienMove.shift(WEAK_SPOT, AbilitySlot.SLOT_1, 100),
				AlienMove.tap(ANALYZE, AbilitySlot.SLOT_2, 200), AlienMove.shift(DECOY, AbilitySlot.SLOT_2, 300),
				AlienMove.tap(BIG_LEAP, AbilitySlot.SLOT_3, 30), AlienMove.shift(SCURRY, AbilitySlot.SLOT_3, 240),
				AlienMove.tap(SNARE, AbilitySlot.SLOT_4, 160), AlienMove.shift(SMOKE_BOMB, AbilitySlot.SLOT_4, 320),
				AlienMove.tap(WORKSHOP, AbilitySlot.SLOT_5, 20),
				AlienMove.tap(REPAIR_MOVE, AbilitySlot.SLOT_6, 600), AlienMove.shift(MIND_MAP, AbilitySlot.SLOT_6, 900),
				AlienMove.vanilla(JAB, "Attack", 0), AlienMove.vanilla(QUICK_READ, "Use on a creature", 10));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), THINK, 1.0f, 1.4f);
		l.sendParticles(THINK_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.2, 0.2, 0.2, 0);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		revertV(p);
		UUID me = p.getUUID();
		MARKS.values().removeIf(m -> m.owner().equals(me));
		TRAPS.remove(me);
		DECOYS.remove(me);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SCURRY_SPEED);
		setFlag(p, F_LEAP | F_SCURRY, false);
		if (timer(p, "scurry_end") > 0) {
			setTimer(p, "scurry_end", 0);
		}
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		tickV(p, l, now);
		// so small mobs keep losing sight of him
		if (now % 20 == 0) {
			for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) {
				if (m.distanceToSqr(p) > 2.5 * 2.5 && l.random.nextFloat() < 0.3f && AlienStatus.loseTrack(m, p)) {
					l.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getEyeY() + 0.4, m.getZ(), 3, 0.1, 0.1, 0.1, 0);
				}
			}
		}
		if (flag(p, F_LEAP) && (p.onGround() || p.isInWaterOrBubble())) {
			setFlag(p, F_LEAP, false);
			p.resetFallDistance();
		}
		long sc = timer(p, "scurry_end");
		if (sc > 0) {
			if (now >= sc) {
				setTimer(p, "scurry_end", 0);
				setFlag(p, F_SCURRY, false);
				PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SCURRY_SPEED);
			} else if (now % 3 == 0) {
				l.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.05, p.getZ(), 1, 0.05, 0, 0.05, 0);
			}
		}
		// the snares
		List<Trap> traps = TRAPS.get(p.getUUID());
		if (traps != null && !traps.isEmpty()) {
			for (Trap t : traps) {
				if (now >= t.until() || t.dim() != l.dimension()) {
					traps.remove(t);
					continue;
				}
				if (now % 10 == 0) {
					l.sendParticles((now / 10) % 2 == 0 ? LIGHT_DUST : GADGET_DUST, t.at().x, t.at().y + 0.15, t.at().z, 1, 0, 0, 0, 0);
				}
				if (now % 2 == 0) {
					AABB box = new AABB(t.at(), t.at()).inflate(0.6, 0.1, 0.6).expandTowards(0, 0.8, 0);
					for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator()
							&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !com.projecthero.mod.squad.Squads.areAllies(p, e))) {
						spring(p, l, t, e);
						traps.remove(t);
						break;
					}
				}
			}
		}
		// the decoy
		Decoy d = DECOYS.get(p.getUUID());
		if (d != null) {
			if (now >= d.until() || d.dim() != l.dimension()) {
				DECOYS.remove(p.getUUID());
				l.sendParticles(ParticleTypes.POOF, d.at().x, d.at().y + 0.2, d.at().z, 8, 0.1, 0.1, 0.1, 0.02);
			} else {
				if (now % 10 == 0) {
					l.sendParticles(LIGHT_DUST, d.at().x, d.at().y + 0.25, d.at().z, 2, 0.05, 0.05, 0.05, 0);
					OmnitrixSounds.play(l, d.at(), DECOY_SOUND, 1.0f, (now / 10) % 2 == 0 ? 1.15f : 0.9f);
				}
				if (now % 5 == 0) {
					for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(d.at(), d.at()).inflate(14), m -> m.isAlive() && !AlienStatus.isBoss(m))) {
						if (m.getTarget() == p) {
							AlienStatus.loseTrack(m, p);
						}
						if (m.getTarget() == null) {
							m.getNavigation().moveTo(d.at().x, d.at().y, d.at().z, 1.1);
							m.getLookControl().setLookAt(d.at());
						}
					}
				}
			}
		}
	}

	private void spring(ServerPlayer p, ServerLevel l, Trap t, LivingEntity e) {
		AlienStatus.root(p, e, TRAP_ROOT, 40);
		hurt(p, e, 2f, false);
		QuirkFx.ring(l, t.at().add(0, 0.05, 0)).colours(0xFFFFFF, 0x8A9AA8).size(1.4f).thickness(0.25f).life(8).ground().spawn();
		QuirkFx.spike(l, t.at(), new Vec3(0, 1, 0)).block(Blocks.TRIPWIRE_HOOK.defaultBlockState()).colours(0xA0A8B0, 0x606870).size(1.0f).size2(0.15f)
				.life(TRAP_ROOT).spawn();
		l.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 0.3, e.getZ(), 12, 0.3, 0.2, 0.3, 0.2);
		OmnitrixSounds.play(l, t.at(), GADGET, 1.2f, 0.7f);
		l.playSound(null, t.at().x, t.at().y, t.at().z, net.minecraft.sounds.SoundEvents.TRIPWIRE_CLICK_ON, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					weakSpot(p);
				} else {
					nervePinch(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					decoy(p);
				} else {
					analyze(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					scurry(p);
				} else {
					bigLeap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					smokeBomb(p);
				} else {
					snare(p);
				}
			}
			case SLOT_5 -> vKey(p, shift); // v0.17.1 (user: "add abilities to the V key for every alien")
			case SLOT_6 -> {
				if (shift) {
					mindMap(p);
				} else {
					geniusRepair(p);
				}
			}
			default -> {
			}
		}
	}

	@Nullable
	private LivingEntity closeTarget(ServerPlayer p, double range) {
		LivingEntity t = lookTarget(p, range);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.no_target").withStyle(ChatFormatting.GRAY), true);
		}
		return t;
	}

	public boolean nervePinch(ServerPlayer p) {
		LivingEntity t = closeTarget(p, 3.0);
		if (t == null || !use(p, NERVE_PINCH)) {
			return false;
		}
		anim(p, ANIM_PINCH);
		ServerLevel l = level(p);
		AlienStatus.stun(p, t, STUN_TICKS);
		hurt(p, t, 1f, false);
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.85, t.getZ(), 14, 0.2, 0.15, 0.2, 0.05);
		QuirkFx.ring(l, t.position().add(0, t.getBbHeight() + 0.3, 0)).colours(0xFFFFFF, 0xF0E060).size(0.7f).thickness(0.12f).life(STUN_TICKS / 2).spawn();
		OmnitrixSounds.play(l, t.position(), PINCH, 1.0f, 1.0f);
		return true;
	}

	public boolean weakSpot(ServerPlayer p) {
		LivingEntity t = closeTarget(p, 3.25);
		if (t == null || !use(p, WEAK_SPOT)) {
			return false;
		}
		anim(p, ANIM_STRIKE);
		ServerLevel l = level(p);
		hurt(p, t, STRIKE_DAMAGE, false);
		l.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 20, 0.3, 0.3, 0.3, 0.4);
		l.sendParticles(ParticleTypes.ENCHANTED_HIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
		QuirkFx.blast(l, t.position().add(0, t.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, 0xC0D8FF).size(0.8f).life(6)
				.flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		OmnitrixSounds.play(l, t.position(), PINCH, 1.2f, 0.6f);
		return true;
	}

	/** G: reads the creature you look at (24 blocks) and marks it: glowing, +35% damage from everyone for 10 s. */
	public boolean analyze(ServerPlayer p) {
		LivingEntity t = closeTarget(p, 24.0);
		if (t == null || !use(p, ANALYZE)) {
			return false;
		}
		anim(p, ANIM_ANALYZE);
		ServerLevel l = level(p);
		long now = l.getGameTime();
		MARKS.put(t.getUUID(), new Mark(p.getUUID(), now + MARK_TICKS));
		t.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false, false), p);
		QuirkThreat.add(t, p, 2f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.analysis", t.getDisplayName(),
				String.format(java.util.Locale.ROOT, "%.0f", t.getHealth()), String.format(java.util.Locale.ROOT, "%.0f", t.getMaxHealth()), t.getArmorValue(),
				Component.translatable("message.projecthero.omnitrix.grey_matter.weak." + weakness(t))).withStyle(ChatFormatting.AQUA), false);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.marked").withStyle(ChatFormatting.AQUA), true);
		Vec3 eye = p.getEyePosition();
		QuirkFx.ribbon(l, eye, t.position().add(0, t.getBbHeight() * 0.5, 0)).colours(0xE0F8FF, 0x5AB8F0).size(0.08f).life(10).spawn();
		QuirkFx.ring(l, t.position().add(0, 0.05, 0)).colours(0xE0F8FF, 0x5AB8F0).size(Math.max(1f, t.getBbWidth() * 1.4f)).thickness(0.12f).life(30).ground()
				.spawn();
		l.sendParticles(THINK_DUST, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 16, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4,
				t.getBbWidth() * 0.5, 0);
		OmnitrixSounds.play(l, p.position(), SCAN, 1.0f, 1.0f);
		return true;
	}

	/** The analysis' weakness line (lang {@code message.projecthero.omnitrix.grey_matter.weak.<id>}). */
	public static String weakness(LivingEntity t) {
		if (t instanceof EnderMan) {
			return "water";
		}
		if (t.getType().is(EntityTypeTags.UNDEAD)) {
			return "undead";
		}
		if (t.getType().is(EntityTypeTags.ARTHROPOD)) {
			return "arthropod";
		}
		if (t.getType().is(EntityTypeTags.AQUATIC)) {
			return "aquatic";
		}
		if (t.fireImmune()) {
			return "fireproof";
		}
		if (t.getType().is(EntityTypeTags.ILLAGER)) {
			return "illager";
		}
		if (AlienStatus.isBoss(t)) {
			return "boss";
		}
		return "general";
	}

	public boolean decoy(ServerPlayer p) {
		if (!use(p, DECOY)) {
			return false;
		}
		anim(p, ANIM_DECOY);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition();
		var hit = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(10)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(p.getLookAngle().scale(0.3)) : eye.add(p.getLookAngle().scale(8));
		at = ground(l, at);
		DECOYS.put(p.getUUID(), new Decoy(l.dimension(), at, l.getGameTime() + DECOY_TICKS));
		QuirkFx.ribbon(l, p.position().add(0, p.getBbHeight() * 0.6, 0), at).colours(0xFFFFFF, 0x8A9AA8).size(0.05f).life(6).spawn();
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, 0xF04030).size(1.2f).thickness(0.15f).life(12).ground().spawn();
		OmnitrixSounds.play(l, at, GADGET, 1.0f, 1.3f);
		return true;
	}

	private static Vec3 ground(ServerLevel l, Vec3 at) {
		var down = l.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return down.getType() == HitResult.Type.BLOCK ? down.getLocation() : at;
	}

	public boolean bigLeap(ServerPlayer p) {
		if (!use(p, BIG_LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z).normalize();
		p.setDeltaMovement(f.x * 1.05, 0.85 + Math.max(0, look.y) * 0.4, f.z * 1.05);
		p.hurtMarked = true;
		p.resetFallDistance();
		setFlag(p, F_LEAP, true);
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.05, p.getZ(), 6, 0.1, 0, 0.1, 0.02);
		OmnitrixSounds.play(l, p.position(), LEAP, 1.0f, 1.0f);
		return true;
	}

	public boolean scurry(ServerPlayer p) {
		if (!use(p, SCURRY)) {
			return false;
		}
		anim(p, ANIM_SCURRY);
		ServerLevel l = level(p);
		setTimer(p, "scurry_end", l.getGameTime() + SCURRY_TICKS);
		setFlag(p, F_SCURRY, true);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SCURRY_SPEED, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
			AlienStatus.loseTrack(m, p);
		}
		OmnitrixSounds.play(l, p.position(), LEAP, 0.8f, 1.6f);
		return true;
	}

	/** Z: quick-crafts a tripwire snare on the ground just ahead (at most three; a fourth replaces the oldest). */
	public boolean snare(ServerPlayer p) {
		if (!use(p, SNARE)) {
			return false;
		}
		anim(p, ANIM_TRAP);
		ServerLevel l = level(p);
		Vec3 f = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
		Vec3 at = ground(l, p.position().add(f.scale(1.2)).add(0, 0.6, 0));
		List<Trap> list = TRAPS.computeIfAbsent(p.getUUID(), k -> new CopyOnWriteArrayList<>());
		while (list.size() >= MAX_TRAPS) {
			list.remove(0);
		}
		list.add(new Trap(l.dimension(), at, l.getGameTime() + TRAP_LIFE));
		QuirkFx.decal(l, at.add(0, 0.03, 0)).style(QuirkFx.DECAL_PRINT).colour(0x50585E).size(0.9f).life(TRAP_LIFE).spawn();
		QuirkFx.ring(l, at.add(0, 0.05, 0)).colours(0xFFFFFF, 0x8A9AA8).size(0.9f).thickness(0.12f).life(10).ground().spawn();
		l.sendParticles(GADGET_DUST, at.x, at.y + 0.2, at.z, 10, 0.2, 0.1, 0.2, 0);
		OmnitrixSounds.play(l, at, GADGET, 1.0f, 1.0f);
		return true;
	}

	public boolean smokeBomb(ServerPlayer p) {
		if (!use(p, SMOKE_BOMB)) {
			return false;
		}
		anim(p, ANIM_SMOKE);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 0.4, 0);
		QuirkFx.flipbook(l, c.add(0, 0.6, 0)).style(QuirkFx.BOOK_SMOKE).colours(0xD0D4D8, 0x707478).size(3.5f).life(30).spawn();
		l.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 40, 1.5, 0.8, 1.5, 0.02);
		l.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 20, 1.0, 0.4, 1.0, 0.05);
		OmnitrixSounds.play(l, c, GADGET, 1.0f, 0.5f);
		for (LivingEntity e : around(p, c, 5f)) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
		}
		for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
			AlienStatus.loseTrack(m, p);
		}
		return true;
	}

	/**
	 * C: the block you look at if he can fix or boost it (an anvil, cracked bricks; a furnace, smoker, blast furnace or brewing
	 * stand gets 10 s of work done at once), otherwise the gear in your hands (30% of its durability each). Nothing to fix: no
	 * cooldown.
	 */
	public boolean geniusRepair(ServerPlayer p) {
		if (cooldownLeft(p, REPAIR_MOVE) > 0) {
			return use(p, REPAIR_MOVE);
		}
		ServerLevel l = level(p);
		BlockHitResult hit = l.clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(4.5)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, p));
		boolean fixed = false;
		if (hit.getType() == HitResult.Type.BLOCK && p.mayInteract(l, hit.getBlockPos())) {
			fixed = fixBlock(p, l, hit.getBlockPos());
		}
		if (!fixed) {
			fixed = repairHeld(p);
		}
		if (!fixed) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.nothing_to_fix").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		use(p, REPAIR_MOVE);
		anim(p, ANIM_REPAIR);
		OmnitrixSounds.play(l, p.position(), REPAIR, 1.0f, 1.0f);
		return true;
	}

	/** A broken or slow block he can fix or boost (true if he did). */
	public boolean fixBlock(ServerPlayer p, ServerLevel l, BlockPos pos) {
		BlockState s = l.getBlockState(pos);
		BlockState into = null;
		if (s.is(Blocks.CHIPPED_ANVIL) || s.is(Blocks.DAMAGED_ANVIL)) {
			into = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, s.getValue(AnvilBlock.FACING));
		} else if (s.is(Blocks.CRACKED_STONE_BRICKS)) {
			into = Blocks.STONE_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
			into = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_DEEPSLATE_TILES)) {
			into = Blocks.DEEPSLATE_TILES.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_NETHER_BRICKS)) {
			into = Blocks.NETHER_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)) {
			into = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		} else if (s.is(Blocks.INFESTED_CRACKED_STONE_BRICKS)) {
			into = Blocks.STONE_BRICKS.defaultBlockState();
		}
		Vec3 c = Vec3.atCenterOf(pos);
		if (into != null) {
			l.setBlock(pos, into, Block.UPDATE_ALL);
			sparks(l, c);
			return true;
		}
		BlockEntity be = l.getBlockEntity(pos);
		if (be instanceof AbstractFurnaceBlockEntity f) {
			for (int i = 0; i < BOOST_TICKS; i++) {
				AbstractFurnaceBlockEntity.serverTick(l, pos, l.getBlockState(pos), f);
			}
			sparks(l, c);
			l.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 8, 0.3, 0.2, 0.3, 0.02);
			return true;
		}
		if (be instanceof BrewingStandBlockEntity b) {
			for (int i = 0; i < BOOST_TICKS; i++) {
				BrewingStandBlockEntity.serverTick(l, pos, l.getBlockState(pos), b);
			}
			sparks(l, c);
			return true;
		}
		return false;
	}

	private static void sparks(ServerLevel l, Vec3 c) {
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 16, 0.35, 0.3, 0.35, 0.1);
		l.sendParticles(ParticleTypes.WAX_OFF, c.x, c.y + 0.5, c.z, 10, 0.35, 0.3, 0.35, 0.1);
		QuirkFx.ring(l, c.add(0, -0.45, 0)).colours(0xE0F8FF, 0x5AB8F0).size(1.2f).thickness(0.15f).life(12).ground().spawn();
	}

	/** Repairs the gear in both hands by 30% of its durability. True if anything was damaged. */
	public boolean repairHeld(ServerPlayer p) {
		boolean any = false;
		for (InteractionHand h : InteractionHand.values()) {
			ItemStack st = p.getItemInHand(h);
			if (!st.isEmpty() && st.isDamageableItem() && st.getDamageValue() > 0) {
				st.setDamageValue(Math.max(0, st.getDamageValue() - Math.max(1, Math.round(st.getMaxDamage() * REPAIR_FRACTION))));
				any = true;
			}
		}
		if (any) {
			ServerLevel l = level(p);
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(), 12, 0.2, 0.2, 0.2, 0.08);
			l.sendParticles(ParticleTypes.WAX_OFF, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
		}
		return any;
	}

	/** Shift+C: everything valuable within 20 blocks outlined through the walls for 8 s (Moon Knight's Scholar's Sight renderer). */
	public boolean mindMap(ServerPlayer p) {
		if (!use(p, MIND_MAP)) {
			return false;
		}
		anim(p, ANIM_MIND);
		ServerLevel l = level(p);
		BlockPos c = p.blockPosition();
		List<long[]> found = new ArrayList<>();
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-MIND_RANGE, -12, -MIND_RANGE), c.offset(MIND_RANGE, 12, MIND_RANGE))) {
			BlockState st = l.getBlockState(q);
			int col = mapColour(st);
			if (col != 0) {
				found.add(new long[] { q.asLong(), (long) c.distSqr(q), col });
			}
		}
		found.sort((a, b) -> Long.compare(a[1], b[1]));
		int n = Math.min(256, found.size());
		long[] pos = new long[n];
		int[] cols = new int[n];
		for (int i = 0; i < n; i++) {
			pos[i] = found.get(i)[0];
			cols[i] = (int) found.get(i)[2];
		}
		ServerPlayNetworking.send(p, new MoonKnightScholarSightPayload(pos, cols, MIND_TICKS));
		// a ripple of thought outwards (particles: a ring effect would tower over something this small)
		for (int k = 0; k < 24; k++) {
			double a = k * Math.PI * 2 / 24;
			l.sendParticles(THINK_DUST, p.getX() + Math.cos(a) * 1.2, p.getY() + 0.1, p.getZ() + Math.sin(a) * 1.2, 1, 0, 0, 0, 0);
		}
		l.sendParticles(THINK_DUST, p.getX(), p.getEyeY() + 0.2, p.getZ(), 20, 0.3, 0.2, 0.3, 0.02);
		OmnitrixSounds.play(l, p.position(), THINK, 1.0f, 1.0f);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.mind_map", n).withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/** The Mind Map's outline colour for a block (0 = not shown). */
	public static int mapColour(BlockState st) {
		if (st.is(ConventionalBlockTags.ORES)) {
			if (st.is(ConventionalBlockTags.DIAMOND_ORES)) {
				return 0x5AF0F0;
			}
			if (st.is(ConventionalBlockTags.GOLD_ORES)) {
				return 0xF0D040;
			}
			if (st.is(ConventionalBlockTags.REDSTONE_ORES)) {
				return 0xF03030;
			}
			if (st.is(ConventionalBlockTags.EMERALD_ORES)) {
				return 0x40F070;
			}
			if (st.is(ConventionalBlockTags.LAPIS_ORES)) {
				return 0x3A60F0;
			}
			if (st.is(ConventionalBlockTags.IRON_ORES)) {
				return 0xE0B090;
			}
			return 0xC0C0C0;
		}
		if (st.is(Blocks.SPAWNER) || st.is(Blocks.TRIAL_SPAWNER) || st.is(Blocks.VAULT)) {
			return 0xC040F0;
		}
		if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST) || st.is(Blocks.BARREL) || st.is(Blocks.ENDER_CHEST) || st.is(Blocks.SUSPICIOUS_SAND)
				|| st.is(Blocks.SUSPICIOUS_GRAVEL)) {
			return 0xF0A040;
		}
		return 0;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le) {
			anim(p, ANIM_JAB);
			if (marked(le)) {
				le.invulnerableTime = 0;
				hurt(p, le, 2f, false);
				level(p).sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
				le.invulnerableTime = 0; // the fist itself still lands this tick
			}
		}
		return false;
	}

	@Override
	public InteractionResult onUseEntity(ServerPlayer p, InteractionHand hand, Entity target) {
		if (hand != InteractionHand.MAIN_HAND || !(target instanceof LivingEntity le) || cooldownLeft(p, QUICK_READ) > 0) {
			return InteractionResult.PASS;
		}
		cooldown(p, QUICK_READ, -1);
		anim(p, ANIM_READ);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.grey_matter.read", le.getDisplayName(),
				String.format(java.util.Locale.ROOT, "%.1f", le.getHealth()), String.format(java.util.Locale.ROOT, "%.0f", le.getMaxHealth()))
				.withStyle(ChatFormatting.AQUA), true);
		OmnitrixSounds.play(level(p), p.position(), SCAN, 0.5f, 1.6f);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !(flag(p, F_LEAP) && source.is(DamageTypeTags.IS_FALL));
	}

	// ---------------------------------------------------------------- v0.17.1 (user: "add abilities to the V key for every alien"): V Pocket Workshop

	public static final String WORKSHOP = "pocket_workshop";
	public static final int ANIM_WORKSHOP = 13;

	private void vKey(ServerPlayer p, boolean shift) {
		pocketWorkshop(p);
	}

	/** V: Pocket Workshop -- a Galvan needs no table: a crafting grid opens wherever he stands (what is left in it comes back). */
	public boolean pocketWorkshop(ServerPlayer p) {
		if (!use(p, WORKSHOP)) {
			return false;
		}
		anim(p, ANIM_WORKSHOP);
		ServerLevel l = level(p);
		net.minecraft.world.inventory.ContainerLevelAccess access = net.minecraft.world.inventory.ContainerLevelAccess.create(l, p.blockPosition());
		p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, who) -> new net.minecraft.world.inventory.CraftingMenu(id, inv, access) {
			@Override
			public boolean stillValid(net.minecraft.world.entity.player.Player player) {
				return player.isAlive(); // no table to stand next to
			}
		}, Component.translatable("container.projecthero.omnitrix.pocket_workshop")));
		l.sendParticles(THINK_DUST, p.getX(), p.getY() + 0.5, p.getZ(), 10, 0.2, 0.2, 0.2, 0);
		OmnitrixSounds.play(l, p.position(), THINK, 0.8f, 1.6f);
		return true;
	}

	private void revertV(ServerPlayer p) {
		if (p.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu) {
			p.closeContainer(); // the grid goes with the brain
		}
	}

	private void tickV(ServerPlayer p, ServerLevel l, long now) {
	}
}
