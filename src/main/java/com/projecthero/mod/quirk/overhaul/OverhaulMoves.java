package com.projecthero.mod.quirk.overhaul;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkSounds;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Overhaul revamp ({@code docs/QUIRKS_REVAMP_PROPOSAL.md} 3.2): his moves and the per-player sessions of the ones
 * that play out over a few ticks (the spikes rolling forward, the Thorn Forest's waves, the wall rising, the pits' lids, the
 * Strike dash, Ground Shift, Restore's wave, Total Disassembly's three waves). Every public move is called by
 * {@link OverhaulKit#handle} once its cooldown is ready, and by the gametests directly; each returns whether it happened
 * (refusals say why on the action bar and spend nothing).
 *
 * <p><b>Matter</b> ({@link Quirks#meter}) is what he builds with: spikes, walls and Fusion spend it, the disassembling moves
 * gain it, and his feet draw it from the ground. Its band sets the size of what he builds ({@link #constructScale}).
 * <b>The gloves</b> are on until a move tugs them off ({@link Overhaul#V_BARE}, 8 s); bare, a punch disassembles too.
 *
 * <p>The spikes are toolkit meshes (no blocks: nothing to put back). The walls, the pillar, the pits, the surf's wake and the
 * ground Total Disassembly lifts are real temporary blocks through the Geokinesis ledger ({@link OverhaulTerrain} /
 * {@link GeoTerrain}): the terrain-damage config, build rights, claims and spawn protection ({@link GeoTerrain#canEdit}), and
 * always put back exactly on their own clock (and on server stop).
 */
public final class OverhaulMoves {
	private record Job(long at, Runnable run) {
	}

	static final class Session {
		final List<Job> jobs = new ArrayList<>();
		long lastSpend = -1000L;
		/** The Overhaul Strike dash: its direction, and whether it has touched someone yet. */
		Vec3 strikeDir = Vec3.ZERO;
		boolean strikeHit;
		/** Every temporary block he placed (gametests / debugging). */
		final List<BlockPos> placed = new ArrayList<>();
		/** Terrain Surf: when its wave last threw each creature aside (entity id -> game time). */
		final Map<Integer, Long> surfHit = new java.util.HashMap<>();
		/** No fall damage until (Terrain Surf / Pillar Launch / Ground Shift). */
		long safeFallUntil;
		/** Germophobe: a mob hit him; his next move hits harder until. */
		long germUntil;
		/** The held key (tap on release, the hold move past its threshold). */
		@Nullable
		AbilitySlot holdSlot;
		long holdAt;
		boolean holdFired;
		/** Ground Shift's direction. */
		Vec3 shiftDir = Vec3.ZERO;
		/** Thorn Forest: where it stands (cover) until. */
		Vec3 forestAt = Vec3.ZERO;
		long forestUntil;
		/** Disassembly mining's own short cooldown (the punch repeats). */
		long minedAt = -1000L;
		/** Fusion was started by Total Disassembly (no cooldown when it ends). */
		boolean ultFusion;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	/** The Fusion size modifier on Attributes.SCALE. */
	public static final ResourceLocation SCALE_ID = PowerToggles.id("quirk_overhaul_fusion_scale");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("quirk_overhaul_fusion_attack");
	private static final ResourceLocation ARMOR_ID = PowerToggles.id("quirk_overhaul_fusion_armor");
	private static final ResourceLocation TOUGH_ID = PowerToggles.id("quirk_overhaul_fusion_toughness");
	private static final ResourceLocation KB_ID = PowerToggles.id("quirk_overhaul_fusion_knockback");
	private static final ResourceLocation REACH_ID = PowerToggles.id("quirk_overhaul_fusion_reach");
	private static final ResourceLocation BLOCK_REACH_ID = PowerToggles.id("quirk_overhaul_fusion_block_reach");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("quirk_overhaul_fusion_speed");
	/** v0.17.1: Total Disassembly can't be knocked back (on top of Fusion's 0.6). */
	private static final ResourceLocation ULT_KB_ID = PowerToggles.id("quirk_overhaul_ult_knockback");
	/** v0.16.7 (user): Terrain Surf's 2-block step assist on Attributes.STEP_HEIGHT (transient; off when the surf ends). */
	public static final ResourceLocation SURF_STEP_ID = PowerToggles.id("quirk_overhaul_surf_step");

	private OverhaulMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
		OverhaulRestore.clearSessionState();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static void later(ServerPlayer p, int delay, Runnable r) {
		session(p).jobs.add(new Job(now(p) + delay, r));
	}

	/** Moves still waiting to play out (gametests). */
	public static int pendingJobs(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.jobs.size();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void sound(ServerPlayer p, Holder<SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** The hand-touch: a sharp clap of bare fingers and the crackle of matter coming apart. */
	private static void touchSound(ServerPlayer p) {
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1.55f);
		sound(p, OverhaulSounds.DISASSEMBLE, 0.9f, 0.95f + p.getRandom().nextFloat() * 0.15f);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	// ================================================================ the rules every move shares

	/** v0.17.1: a move tugs the gloves off (8 s): bare-hand punches disassemble meanwhile. */
	static void tugGloves(ServerPlayer p) {
		boolean was = Overhaul.gloved(p);
		Quirks.setVal(p, Overhaul.V_BARE, now(p) + OverhaulConfig.BARE_TICKS);
		if (was) {
			Vec3 h = AbilityHelpers.handPosition(p);
			p.serverLevel().sendParticles(OverhaulCombat.RED, h.x, h.y, h.z, 5, 0.12, 0.12, 0.12, 0.0);
			sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 0.6f, 1.3f);
		}
	}

	/** Enough Matter for {@code cost} (says so if not). */
	static boolean hasMatter(ServerPlayer p, float cost) {
		if (Quirks.meter(p) + 0.01f >= cost) {
			return true;
		}
		Quirks.say(p, "message.projecthero.overhaul.matter", ChatFormatting.RED, (int) Math.ceil(cost));
		return false;
	}

	private static boolean canStart(ServerPlayer p, float cost) {
		return hasMatter(p, cost);
	}

	/** Spends Matter (a deliberate cost: raw, not paced). */
	static void spend(ServerPlayer p, float cost) {
		Quirks.addMeter(p, -cost);
		session(p).lastSpend = now(p);
	}

	/** Gains Matter (what he took apart). */
	static void gain(ServerPlayer p, float amount) {
		Quirks.addMeter(p, amount);
	}

	/** v0.17.1: the size of what he builds from the band he builds it in -- Starved small, Overflowing x1.5. */
	public static float constructScale(ServerPlayer p) {
		return constructScale(Quirks.meter(p));
	}

	public static float constructScale(float matter) {
		return matter < 25f ? OverhaulConfig.STARVED_SCALE : matter >= 80f ? OverhaulConfig.OVERFLOWING_SCALE : 1f;
	}

	/** Germophobe: a mob hit him in the last 3 s (his next move hits harder). */
	public static boolean germophobe(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.germUntil > now(p);
	}

	private static void done(ServerPlayer p, String id, int cooldown, int anim) {
		Quirks.setCooldown(p, id, cooldown);
		Quirks.setAnim(p, anim);
		tugGloves(p);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.germUntil = 0L; // the Germophobe bonus is spent on this move
		}
	}

	private static @Nullable BlockPos groundUnder(ServerPlayer p) {
		return OverhaulTerrain.groundCell(p.serverLevel(), Mth.floor(p.getX()), Mth.floor(p.getY() + 0.2), Mth.floor(p.getZ()));
	}

	private static boolean needGround(ServerPlayer p, @Nullable BlockPos cell) {
		if (cell != null) {
			return true;
		}
		Quirks.say(p, "message.projecthero.overhaul.no_ground", ChatFormatting.RED);
		return false;
	}

	/** One temporary block, remembered for debugging / tests. */
	private static boolean place(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		if (!OverhaulTerrain.place(p, level, pos, state, ttl)) {
			return false;
		}
		remember(p, pos);
		return true;
	}

	private static void remember(ServerPlayer p, BlockPos pos) {
		List<BlockPos> list = session(p).placed;
		list.add(pos.immutable());
		if (list.size() > 512) {
			list.subList(0, list.size() - 512).clear();
		}
	}

	/** A palm pressed to the ground: the floor cracks under it and a ring of its fragments jumps. */
	private static void palmToGround(ServerPlayer p, BlockPos cell) {
		ServerLevel level = p.serverLevel();
		BlockState g = OverhaulFx.groundAt(level, cell);
		Vec3 c = Vec3.atBottomCenterOf(cell);
		OverhaulFx.crack(level, c, 1.6f, 60);
		OverhaulFx.groundShards(level, c.add(0, 0.2, 0), g, 0.9f, 10, 14, false);
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			level.sendParticles(OverhaulCombat.RED, c.x + Math.cos(a) * 0.9, c.y + 0.05, c.z + Math.sin(a) * 0.9, 1, 0.05, 0.0, 0.05, 0.0);
		}
		level.playSound(null, cell, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
	}

	// ================================================================ holds (the key routing, from OverhaulKit)

	/** The hold threshold of a key with a hold move (0: none). */
	public static int holdTicks(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2, SLOT_3 -> OverhaulConfig.HOLD_TICKS;
			case SLOT_5 -> OverhaulConfig.RESTORE_HOLD_TICKS;
			default -> 0;
		};
	}

	/** A plain (no Shift) press of a key with a hold move: the tap waits for the release, the hold for the threshold. */
	static void holdDown(ServerPlayer p, AbilitySlot slot) {
		Session s = session(p);
		s.holdSlot = slot;
		s.holdAt = now(p);
		s.holdFired = false;
		Quirks.setVal(p, Overhaul.V_HOLD_SLOT, slot.ordinal() + 1L);
		Quirks.setVal(p, Overhaul.V_HOLD_AT, now(p));
		if (slot == AbilitySlot.SLOT_2) {
			Quirks.setAnim(p, Overhaul.ANIM_THORN); // the palm goes down at once: the tap's slap and the hold's kneel both start low
		}
	}

	/** The key came up: the tap move (released before the threshold), or the end of the hold move. */
	static void holdUp(ServerPlayer p, AbilitySlot slot) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.holdSlot != slot) {
			return;
		}
		boolean fired = s.holdFired;
		clearHold(p, s);
		if (fired) {
			switch (slot) {
				case SLOT_2 -> thornFire(p);
				case SLOT_3 -> endSurf(p);
				default -> {
				}
			}
			return;
		}
		switch (slot) {
			case SLOT_2 -> {
				if (Quirks.ready(p, OverhaulConfig.SPIKE_WAVE)) {
					spikeWave(p);
				} else {
					Quirks.setAnim(p, QuirkState.ANIM_NONE);
				}
			}
			case SLOT_3 -> {
				if (Quirks.ready(p, OverhaulConfig.GROUND_SHIFT)) {
					groundShift(p);
				}
			}
			case SLOT_5 -> {
				if (Quirks.ready(p, OverhaulConfig.REASSEMBLE)) {
					reassemble(p);
				}
			}
			default -> {
			}
		}
	}

	private static void clearHold(ServerPlayer p, Session s) {
		s.holdSlot = null;
		s.holdFired = false;
		if (Quirks.state(p).val(Overhaul.V_HOLD_SLOT) != 0L) {
			Quirks.setVal(p, Overhaul.V_HOLD_SLOT, 0L);
			Quirks.setVal(p, Overhaul.V_HOLD_AT, 0L);
		}
	}

	private static void tickHold(ServerPlayer p, Session s, long now) {
		if (s.holdSlot == null) {
			return;
		}
		long held = now - s.holdAt;
		if (!s.holdFired && held >= holdTicks(s.holdSlot)) {
			s.holdFired = true;
			switch (s.holdSlot) {
				case SLOT_2 -> {
					if (!Quirks.ready(p, OverhaulConfig.THORN) || !thornAimStart(p)) {
						Quirks.setAnim(p, QuirkState.ANIM_NONE);
					}
				}
				case SLOT_3 -> {
					if (Quirks.ready(p, OverhaulConfig.SURF)) {
						surf(p);
					}
				}
				case SLOT_5 -> {
					if (Quirks.ready(p, OverhaulConfig.RESTORE)) {
						restore(p);
					}
				}
				default -> {
				}
			}
		}
		if (s.holdFired && s.holdSlot == AbilitySlot.SLOT_2 && Quirks.state(p).val(Overhaul.V_THORN_AIM) != 0L) {
			tickThornAim(p, now);
		}
	}

	// ================================================================ R: Disassemble / Shift+R: Disarm

	/**
	 * A bare-hand touch: {@link OverhaulConfig#DISASSEMBLE_DAMAGE}; the body cracks with red lines and bursts apart, and one it
	 * kills bursts on everyone near. Gains {@link OverhaulConfig#DISASSEMBLE_GAIN} Matter.
	 */
	public static boolean disassemble(ServerPlayer p) {
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulCombat.reach(p), 20.0);
		if (t == null) {
			Quirks.say(p, "message.projecthero.overhaul.no_target", ChatFormatting.GRAY);
			return false;
		}
		touchSound(p);
		ServerLevel level = p.serverLevel();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.55, 0);
		OverhaulFx.bodyCracks(level, t);
		boolean landed = OverhaulCombat.strike(p, t, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.DISASSEMBLE_DAMAGE),
				OverhaulConfig.DISASSEMBLE_KNOCKBACK, 0.1);
		if (landed) {
			gain(p, OverhaulConfig.DISASSEMBLE_GAIN);
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		if (!t.isAlive()) {
			// the body comes apart: its pieces hit everything around it
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(OverhaulConfig.DISASSEMBLE_BURST_RADIUS),
					e -> e != t && OverhaulCombat.isTarget(p, e) && e.distanceTo(t) <= OverhaulConfig.DISASSEMBLE_BURST_RADIUS)) {
				QuirkFx.ribbon(level, c, e.position().add(0, e.getBbHeight() * 0.5, 0)).style(QuirkFx.RIBBON_LIGHT).colours(0xFF6060, OverhaulFx.RED)
						.size(0.08f).life(6).spawn();
				OverhaulCombat.strike(p, e, c, OverhaulCombat.scaled(p, OverhaulConfig.DISASSEMBLE_BURST_DAMAGE), 0.6, 0.15);
			}
			level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.5f);
			QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
		} else {
			final int id = t.getId();
			later(p, OverhaulConfig.DISASSEMBLE_CRACK_TICKS, () -> {
				if (level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
					OverhaulFx.burst(level, le, false);
				}
			});
		}
		done(p, OverhaulConfig.DISASSEMBLE, OverhaulConfig.DISASSEMBLE_COOLDOWN, Overhaul.ANIM_DISASSEMBLE);
		return true;
	}

	private static boolean ally(ServerPlayer p, LivingEntity e) {
		return e != p && e.isAlive() && (Squads.areAllies(p, e) || HeroTargets.isOwnedBy(e, p) || HeroTargets.isFriendlyPet(p, e));
	}

	/** Takes a body apart and puts it back whole: full health, harmful effects and fire gone. */
	static void restoreBody(ServerLevel level, LivingEntity e) {
		e.setHealth(e.getMaxHealth());
		e.clearFire();
		cleanse(e);
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
		OverhaulFx.reassemble(level, c, Math.max(1.2f, e.getBbHeight() * 0.8f));
		level.sendParticles(OverhaulCombat.MEND, c.x, c.y, c.z, 14, e.getBbWidth() * 0.4, e.getBbHeight() * 0.35, e.getBbWidth() * 0.4, 0.0);
		QuirkSounds.play(level, c, OverhaulSounds.REASSEMBLE, 1.1f, 1.0f);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	/** Harmful effects gone. Returns how many. */
	static int cleanse(LivingEntity e) {
		List<Holder<MobEffect>> bad = new ArrayList<>();
		for (MobEffectInstance inst : e.getActiveEffects()) {
			if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
					&& !inst.getEffect().equals(com.projecthero.mod.hero.PowerSuppression.ErasedEffect.HOLDER)) {
				bad.add(inst.getEffect());
			}
		}
		bad.forEach(e::removeEffect);
		return bad.size();
	}

	/**
	 * Touch what a creature holds -- or, empty-handed, one piece of its armour -- and it comes apart: destroyed outright on
	 * a mob, broken out of a player's grip (dropped) so nothing of theirs is lost for good. Not bosses. Gains a little Matter.
	 * Returns what was taken (empty: nothing).
	 */
	public static ItemStack disarm(ServerPlayer p) {
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulCombat.reach(p) + 0.5, 20.0);
		if (t == null || OverhaulCombat.isBoss(t)) {
			Quirks.say(p, t == null ? "message.projecthero.overhaul.no_target" : "message.projecthero.overhaul.too_big", ChatFormatting.GRAY);
			Quirks.setCooldown(p, OverhaulConfig.DISARM, OverhaulConfig.DISARM_MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		EquipmentSlot slot = null;
		for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
				EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!t.getItemBySlot(s).isEmpty()) {
				slot = s;
				break;
			}
		}
		if (slot == null) {
			Quirks.say(p, "message.projecthero.overhaul.nothing_held", ChatFormatting.GRAY);
			Quirks.setCooldown(p, OverhaulConfig.DISARM, OverhaulConfig.DISARM_MISS_COOLDOWN);
			return ItemStack.EMPTY;
		}
		ItemStack taken = t.getItemBySlot(slot).copy();
		t.setItemSlot(slot, ItemStack.EMPTY);
		if (t.isUsingItem()) {
			t.stopUsingItem();
		}
		ServerLevel level = p.serverLevel();
		Vec3 at = t.position().add(0, slot == EquipmentSlot.HEAD ? t.getBbHeight() * 0.9 : t.getBbHeight() * 0.55, 0);
		if (t instanceof Player) {
			ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, taken);
			drop.setPickUpDelay(40);
			level.addFreshEntity(drop);
		} else if (t instanceof Mob m) {
			m.setDropChance(slot, 0f);
		}
		gain(p, OverhaulConfig.DISARM_GAIN);
		// the item comes apart in his fingers
		level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, taken), at.x, at.y, at.z, 24, 0.25, 0.25, 0.25, 0.12);
		QuirkFx.shards(level, at).colours(0xB8B8C0, 0x4A4A55).size(0.9f).count(16).life(18).spawn();
		touchSound(p);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		done(p, OverhaulConfig.DISARM, OverhaulConfig.DISARM_COOLDOWN, Overhaul.ANIM_DISARM);
		return taken;
	}

	// ================================================================ G: Ground Spikes / Shift+G: Spike Field / hold G: Thorn Forest

	/** Hits every valid target within {@code radius} (flat) of {@code at} not yet in {@code hit}. */
	private static void spikeHits(ServerPlayer p, Vec3 at, double radius, float damage, double lift, Set<Integer> hit) {
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 2.5, radius + 1),
				e -> OverhaulCombat.isTarget(p, e) && !hit.contains(e.getId()))) {
			double dx = e.getX() - at.x, dz = e.getZ() - at.z;
			if (dx * dx + dz * dz > (radius + e.getBbWidth() * 0.5) * (radius + e.getBbWidth() * 0.5) || Math.abs(e.getY() - at.y) > 2.5) {
				continue;
			}
			hit.add(e.getId());
			OverhaulCombat.strike(p, e, at.subtract(0, 1, 0), damage, 0.25, lift);
		}
	}

	/** He slaps the floor: a jagged line of stone spike meshes erupts toward the target (or the way he looks), one a tick. */
	public static boolean spikeWave(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.SPIKE_WAVE_COST)) {
			return false;
		}
		BlockPos start = groundUnder(p);
		if (!needGround(p, start)) {
			return false;
		}
		float scale = constructScale(p);
		spend(p, OverhaulConfig.SPIKE_WAVE_COST);
		LivingEntity aim = OverhaulCombat.aimed(p, 18.0, 25.0);
		Vec3 dir = aim == null ? flatLook(p) : new Vec3(aim.getX() - p.getX(), 0, aim.getZ() - p.getZ());
		dir = dir.lengthSqr() < 1.0e-4 ? flatLook(p) : dir.normalize();
		final Vec3 d = dir;
		final int[] y = { start.getY() };
		final boolean[] stopped = { false };
		final Set<Integer> hit = new HashSet<>();
		// v0.17.1 combo route "Buried Alive": Disassemble Terrain, then Ground Spikes through the pits
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.SPIKE_WAVE_DAMAGE) * QuirkCombos.mult(QuirkCombos.used(p, OverhaulConfig.SPIKE_WAVE));
		palmToGround(p, start);
		touchSound(p);
		// the palm lands on tick 3 of the pose; the ground answers from tick 4
		for (int i = 1; i <= OverhaulConfig.SPIKE_WAVE_LENGTH; i++) {
			final int step = i;
			later(p, 2 + i, () -> {
				if (stopped[0]) {
					return;
				}
				ServerLevel level = p.serverLevel();
				BlockPos cell = OverhaulTerrain.groundCell(level, Mth.floor(start.getX() + 0.5 + d.x * step), y[0],
						Mth.floor(start.getZ() + 0.5 + d.z * step));
				if (cell == null || Math.abs(cell.getY() - y[0]) > 2) {
					stopped[0] = true;
					return;
				}
				y[0] = cell.getY();
				spikeHits(p, Vec3.atBottomCenterOf(cell), OverhaulConfig.SPIKE_HIT_RADIUS * scale, dmg, OverhaulConfig.SPIKE_LIFT, hit);
				OverhaulFx.spike(level, cell, d, (step % 3 == 0 ? 2.8f : 2.0f) * scale, 0.46f * scale, OverhaulConfig.SPIKE_TTL);
				// a flanking spike on alternate sides every other step (a jagged line, as in the anime)
				if (step % 2 == 0) {
					Vec3 side = new Vec3(-d.z, 0, d.x).scale((step / 2) % 2 == 0 ? 1.0 : -1.0);
					BlockPos flank = OverhaulTerrain.groundCell(level, Mth.floor(start.getX() + 0.5 + d.x * step + side.x),
							y[0], Mth.floor(start.getZ() + 0.5 + d.z * step + side.z));
					if (flank != null && Math.abs(flank.getY() - y[0]) <= 1) {
						OverhaulFx.spike(level, flank, side.add(d), 1.5f * scale, 0.36f * scale, OverhaulConfig.SPIKE_TTL - 6);
					}
				}
				if (step % 3 == 1) {
					OverhaulFx.crack(level, Vec3.atBottomCenterOf(cell), 1.2f * scale, 50);
					level.playSound(null, cell, OverhaulSounds.SPIKE, SoundSource.PLAYERS, 0.9f, 0.85f + level.random.nextFloat() * 0.3f);
				}
			});
		}
		done(p, OverhaulConfig.SPIKE_WAVE, OverhaulConfig.SPIKE_WAVE_COOLDOWN, Overhaul.ANIM_SPIKE_WAVE);
		return true;
	}

	/** Two rings of spike meshes erupt all around him. */
	public static boolean spikeField(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.SPIKE_FIELD_COST)) {
			return false;
		}
		BlockPos center = groundUnder(p);
		if (!needGround(p, center)) {
			return false;
		}
		float scale = constructScale(p);
		spend(p, OverhaulConfig.SPIKE_FIELD_COST);
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.SPIKE_FIELD_DAMAGE);
		Set<Integer> hit = new HashSet<>();
		Vec3 c = Vec3.atBottomCenterOf(center);
		palmToGround(p, center);
		touchSound(p);
		ServerLevel level = p.serverLevel();
		OverhaulFx.crack(level, c, 4.5f * scale, 70);
		later(p, 4, () -> ring(p, center, OverhaulConfig.SPIKE_FIELD_INNER * scale, 2.0f * scale, 1.5));
		later(p, 4, () -> spikeHitsRing(p, c, 0.8, OverhaulConfig.SPIKE_FIELD_OUTER * scale + 0.6, dmg, hit));
		later(p, 7, () -> ring(p, center, OverhaulConfig.SPIKE_FIELD_OUTER * scale, 2.8f * scale, 1.5));
		later(p, 7, () -> sound(p, OverhaulSounds.SPIKE, 1.2f, 0.7f));
		later(p, 4, () -> sound(p, OverhaulSounds.SPIKE, 1.0f, 0.9f));
		done(p, OverhaulConfig.SPIKE_FIELD, OverhaulConfig.SPIKE_FIELD_COOLDOWN, Overhaul.ANIM_SPIKE_FIELD);
		return true;
	}

	/** Every valid target between {@code inner} and {@code outer} (flat) from {@code c}, knocked outward and up. */
	private static void spikeHitsRing(ServerPlayer p, Vec3 c, double inner, double outer, float dmg, Set<Integer> hit) {
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(outer + 1, 3, outer + 1),
				e -> OverhaulCombat.isTarget(p, e) && !hit.contains(e.getId()))) {
			double dist = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
			if (dist < inner || dist > outer + e.getBbWidth() * 0.5 || Math.abs(e.getY() - c.y) > 3) {
				continue;
			}
			hit.add(e.getId());
			OverhaulCombat.strike(p, e, c, dmg, 0.7, OverhaulConfig.SPIKE_LIFT);
		}
	}

	/** One ring of spike meshes of {@code height} around {@code center}, about one every {@code spacing} blocks, leaning out. */
	private static void ring(ServerPlayer p, BlockPos center, double radius, float height, double spacing) {
		ServerLevel level = p.serverLevel();
		int n = Math.max(6, (int) Math.round(2 * Math.PI * radius / spacing));
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n;
			int x = Mth.floor(center.getX() + 0.5 + Math.cos(a) * radius);
			int z = Mth.floor(center.getZ() + 0.5 + Math.sin(a) * radius);
			BlockPos cell = OverhaulTerrain.groundCell(level, x, center.getY(), z);
			if (cell == null || Math.abs(cell.getY() - center.getY()) > 2 || !seen.add(cell.asLong())) {
				continue;
			}
			OverhaulFx.spike(level, cell, new Vec3(Math.cos(a), 0, Math.sin(a)), height, 0.44f * Math.max(0.8f, height / 2.4f), OverhaulConfig.SPIKE_TTL);
		}
	}

	/** Where the Thorn Forest is aimed: the ground cell he looks at (or under the creature he aims at), or null. */
	public static @Nullable BlockPos thornTarget(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulConfig.THORN_RANGE, 8.0);
		Vec3 at;
		if (t != null) {
			at = t.position();
		} else {
			BlockHitResult hr = AbilityHelpers.raycastBlock(p, OverhaulConfig.THORN_RANGE);
			if (hr == null || hr.getType() != HitResult.Type.BLOCK) {
				return null;
			}
			at = hr.getLocation().add(0, 0.5, 0);
		}
		return OverhaulTerrain.groundCell(level, Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
	}

	/** Hold G past the tap: kneeling, palms pressed to the ground, he aims the forest (a crack marker where it will rise). */
	static boolean thornAimStart(ServerPlayer p) {
		if (!hasMatter(p, OverhaulConfig.THORN_COST)) {
			return false;
		}
		Quirks.setVal(p, Overhaul.V_THORN_AIM, now(p));
		Quirks.setAnim(p, Overhaul.ANIM_THORN);
		sound(p, SoundEvents.DEEPSLATE_BREAK, 0.7f, 0.5f);
		sound(p, OverhaulSounds.DISASSEMBLE, 0.5f, 0.6f);
		return true;
	}

	private static void tickThornAim(ServerPlayer p, long now) {
		long since = Quirks.state(p).val(Overhaul.V_THORN_AIM);
		if (now - since >= OverhaulConfig.THORN_MAX_AIM) {
			thornFire(p);
			Session s = SESSIONS.get(p.getUUID());
			if (s != null) {
				clearHold(p, s);
			}
			return;
		}
		if ((now - since) % 4 == 0) {
			BlockPos cell = thornTarget(p);
			if (cell != null) {
				ServerLevel level = p.serverLevel();
				Vec3 c = Vec3.atBottomCenterOf(cell);
				QuirkFx.decal(level, c).style(QuirkFx.DECAL_CRACK).colour(0x6A0A12).size((OverhaulConfig.THORN_HALF + 0.5f) * constructScale(p)).life(7).spawn();
				level.sendParticles(OverhaulCombat.RED, c.x, c.y + 0.1, c.z, 3, 1.5, 0.02, 1.5, 0.0);
			}
		}
		if ((now - since) % 10 == 0) {
			Vec3 h = p.position();
			p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, OverhaulFx.groundAt(p.serverLevel(), p.blockPosition())),
					h.x, h.y + 0.1, h.z, 6, 0.4, 0.02, 0.4, 0.05);
		}
	}

	/** The aim is released: the forest rises (or nothing, if it never found ground). */
	static void thornFire(ServerPlayer p) {
		if (Quirks.state(p).val(Overhaul.V_THORN_AIM) == 0L) {
			return;
		}
		Quirks.setVal(p, Overhaul.V_THORN_AIM, 0L);
		BlockPos cell = thornTarget(p);
		if (cell == null || !thornForest(p, cell)) {
			Quirks.setAnim(p, QuirkState.ANIM_NONE);
		}
	}

	/**
	 * Thorn Forest at {@code centre}: a 7x7 forest of 6-block spikes rises in three waves (centre, middle, edge), impaling what
	 * stands there ({@link OverhaulConfig#THORN_DAMAGE}, a toss), then stands 4 s as cover: what walks in is pushed out and
	 * arrows break on it. False if refused (Matter).
	 */
	public static boolean thornForest(ServerPlayer p, BlockPos centre) {
		if (!canStart(p, OverhaulConfig.THORN_COST)) {
			return false;
		}
		float scale = constructScale(p);
		spend(p, OverhaulConfig.THORN_COST);
		ServerLevel level = p.serverLevel();
		Vec3 c = Vec3.atBottomCenterOf(centre);
		float dmg = OverhaulCombat.scaled(p, OverhaulConfig.THORN_DAMAGE);
		Set<Integer> hit = new HashSet<>();
		int half = OverhaulConfig.THORN_HALF;
		OverhaulFx.crack(level, c, (half + 1.5f) * scale, OverhaulConfig.THORN_STAND_TICKS + 40);
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				int wave = ring <= 1 ? 0 : ring == 2 ? 1 : 2;
				final int fx = dx, fz = dz;
				later(p, wave * OverhaulConfig.THORN_WAVE_GAP, () -> {
					double ox = fx * 1.05 * scale, oz = fz * 1.05 * scale;
					BlockPos cell = OverhaulTerrain.groundCell(level, Mth.floor(c.x + ox), centre.getY(), Mth.floor(c.z + oz));
					if (cell == null || Math.abs(cell.getY() - centre.getY()) > 3) {
						return;
					}
					float h = OverhaulConfig.THORN_HEIGHT * scale * (ring == 0 ? 1.15f : 1f - 0.08f * ring);
					OverhaulFx.spike(level, cell, new Vec3(fx, 0, fz).scale(0.25), h, 0.58f * scale,
							OverhaulConfig.THORN_STAND_TICKS + 20 + (2 - Math.min(2, ring)) * OverhaulConfig.THORN_WAVE_GAP);
					spikeHits(p, Vec3.atBottomCenterOf(cell), 0.75 * scale, dmg, OverhaulConfig.THORN_LIFT, hit);
				});
			}
		}
		for (int w = 0; w < 3; w++) {
			final int ww = w;
			later(p, w * OverhaulConfig.THORN_WAVE_GAP, () -> {
				QuirkSounds.play(level, c, OverhaulSounds.SPIKE, 1.4f, 0.65f + 0.1f * ww);
				QuirkCamera.shakeAround(level, c, 18, 0.35f, 8);
			});
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(half + 1, 3, half + 1), e -> OverhaulCombat.isTarget(p, e))) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
		}
		Session s = session(p);
		s.forestAt = c;
		s.forestUntil = now(p) + 2L * OverhaulConfig.THORN_WAVE_GAP + OverhaulConfig.THORN_STAND_TICKS;
		touchSound(p);
		done(p, OverhaulConfig.THORN, OverhaulConfig.THORN_COOLDOWN, Overhaul.ANIM_THORN);
		Quirks.setAnim(p, Overhaul.ANIM_SPIKE_FIELD); // up off his knees as it rises
		return true;
	}

	/** The forest stands: creatures inside are pushed out, projectiles not his break on it. */
	private static void tickForest(ServerPlayer p, Session s, long now) {
		if (s.forestUntil <= now) {
			return;
		}
		ServerLevel level = p.serverLevel();
		double r = OverhaulConfig.THORN_HALF + 0.8;
		AABB box = new AABB(s.forestAt, s.forestAt).inflate(r, 0, r).expandTowards(0, 6, 0);
		if (now % 2 == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> OverhaulCombat.isTarget(p, e) && !OverhaulCombat.isBoss(e))) {
				Vec3 out = new Vec3(e.getX() - s.forestAt.x, 0, e.getZ() - s.forestAt.z);
				out = out.lengthSqr() < 1.0e-3 ? flatLook(p) : out.normalize();
				AbilityHelpers.push(e, out.scale(0.35));
			}
		}
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box, pr -> pr.getOwner() != p)) {
			Vec3 at = pr.position();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.05);
			pr.discard();
		}
	}

	// ================================================================ C: Shrapnel Burst / Shift+C: Wall Burst

	/**
	 * The ground ahead is taken apart and flung as a cone of stone shards ({@link OverhaulConfig#SHRAPNEL_RANGE} long): 3 a
	 * shard, five shards on a body point blank (fewer further out). Gains {@link OverhaulConfig#SHRAPNEL_GAIN} Matter. Returns
	 * how many it hit (-1 refused).
	 */
	public static int shrapnel(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		BlockPos under = groundUnder(p);
		BlockState mat = under == null ? Blocks.STONE.defaultBlockState() : OverhaulFx.groundAt(level, under);
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.25, 0.45), look.z).normalize();
		Vec3 palm = p.position().add(0, 0.6, 0).add(flatLook(p).scale(0.6));
		double cos = Math.cos(Math.toRadians(OverhaulConfig.SHRAPNEL_HALF_ANGLE));
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(OverhaulConfig.SHRAPNEL_RANGE + 1),
				e -> OverhaulCombat.isTarget(p, e))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(palm);
			double d = to.length();
			if (d > OverhaulConfig.SHRAPNEL_RANGE + e.getBbWidth() * 0.5 || d < 0.1 || to.normalize().dot(dir) < cos || !p.hasLineOfSight(e)) {
				continue;
			}
			int shards = d <= 3.0 ? OverhaulConfig.SHRAPNEL_SHARDS : d <= 6.0 ? 3 : 2;
			if (OverhaulCombat.strike(p, e, palm, OverhaulCombat.scaled(p, OverhaulConfig.SHRAPNEL_SHARD_DAMAGE * shards), 0.55, 0.2)) {
				n++;
			}
		}
		gain(p, OverhaulConfig.SHRAPNEL_GAIN);
		// the floor in front breaks and the stone flies
		if (under != null) {
			OverhaulFx.crack(level, Vec3.atBottomCenterOf(under).add(flatLook(p).scale(1.6)), 2.2f, 60);
		}
		QuirkFx.cone(level, palm, dir).style(QuirkFx.CONE_DEBRIS).colours(0xA8A29A, 0x5A544C).size(2.6f).length((float) OverhaulConfig.SHRAPNEL_RANGE)
				.life(12).spawn();
		for (int i = 0; i < 4; i++) {
			final int k = i;
			later(p, i, () -> OverhaulFx.groundShards(level, palm.add(dir.scale(1.5 + k * 1.8)), mat, 0.9f + 0.35f * k, 10, 16, false));
		}
		touchSound(p);
		sound(p, SoundEvents.DRIPSTONE_BLOCK_BREAK, 1.0f, 0.6f);
		sound(p, OverhaulSounds.SPIKE, 0.9f, 1.25f);
		if (n > 0) {
			QuirkCamera.preset(p, QuirkCamera.Preset.LIGHT_HIT);
		}
		done(p, OverhaulConfig.SHRAPNEL, OverhaulConfig.SHRAPNEL_COOLDOWN, Overhaul.ANIM_SHRAPNEL);
		return n;
	}

	/**
	 * The ground ahead bursts up into a wall ({@link OverhaulConfig#WALL_WIDTH} x {@link OverhaulConfig#WALL_HEIGHT}, x1.5
	 * Overflowing, smaller Starved), row by row, assembling out of flying fragments.
	 */
	public static boolean wall(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.WALL_COST)) {
			return false;
		}
		BlockPos under = groundUnder(p);
		if (!needGround(p, under)) {
			return false;
		}
		float scale = constructScale(p);
		int width = Math.max(3, (int) (OverhaulConfig.WALL_WIDTH * scale)) | 1;
		int height = Math.max(2, (int) (OverhaulConfig.WALL_HEIGHT * scale));
		ServerLevel level = p.serverLevel();
		Direction facing = p.getDirection();
		Direction right = facing.getClockWise();
		BlockPos base = under.relative(facing, OverhaulConfig.WALL_DISTANCE);
		List<BlockPos> columns = new ArrayList<>();
		int half = width / 2;
		for (int j = -half; j <= half; j++) {
			BlockPos col = base.relative(right, j);
			BlockPos cell = OverhaulTerrain.groundCell(level, col.getX(), under.getY(), col.getZ());
			if (cell != null && Math.abs(cell.getY() - under.getY()) <= 2) {
				columns.add(cell);
			}
		}
		if (!needGround(p, columns.isEmpty() ? null : under)) {
			return false;
		}
		spend(p, OverhaulConfig.WALL_COST);
		palmToGround(p, under);
		touchSound(p);
		// whatever stands where the wall bursts up is thrown back and hurt (the wall then rises around it)
		Vec3 wallMid = Vec3.atBottomCenterOf(base);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(wallMid, wallMid).inflate(half + 0.6, 2.5, 1.0),
				e -> OverhaulCombat.isTarget(p, e))) {
			Vec3 away = Vec3.atLowerCornerOf(facing.getNormal());
			OverhaulCombat.strike(p, e, e.position().subtract(away), OverhaulCombat.scaled(p, OverhaulConfig.WALL_BURST_DAMAGE), 0.9, 0.35);
		}
		// the wall assembles from flying fragments: the ground's pieces gather where it will stand, then the rows rise
		for (BlockPos cell : columns) {
			OverhaulFx.groundShards(level, Vec3.atBottomCenterOf(cell).add(0, height * 0.5, 0), OverhaulFx.groundAt(level, cell), height * 0.55f, 12,
					10 + height * 2, true);
		}
		for (int h = 0; h < height; h++) {
			final int row = h;
			later(p, 3 + h * 2, () -> {
				for (BlockPos cell : columns) {
					BlockState ground = level.getBlockState(cell.below());
					BlockPos at = cell.above(row);
					OverhaulTerrain.burstAt(level, at, OverhaulTerrain.material(ground), 4);
					if (row == 0 || level.getBlockState(at.below()).isSolid()) {
						place(p, level, at, OverhaulTerrain.material(ground), OverhaulConfig.WALL_TTL - row * 2);
					}
				}
				level.playSound(null, base, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f + row * 0.1f);
				level.playSound(null, base, OverhaulSounds.REASSEMBLE, SoundSource.PLAYERS, 0.5f, 1.3f + row * 0.1f);
			});
		}
		Quirks.setVal(p, Overhaul.V_WALL, now(p) + OverhaulConfig.WALL_TTL);
		done(p, OverhaulConfig.WALL, OverhaulConfig.WALL_COOLDOWN, Overhaul.ANIM_WALL);
		return true;
	}

	// ================================================================ Z: Overhaul Strike / Shift+Z: Disassemble Terrain

	/** Where Disassemble Terrain lands: the creature he aims at, else the block he looks at. */
	private static @Nullable Vec3 pitCentre(ServerPlayer p) {
		LivingEntity t = OverhaulCombat.aimed(p, OverhaulConfig.PIT_RANGE, 12.0);
		if (t != null) {
			return t.position();
		}
		BlockHitResult hr = AbilityHelpers.raycastBlock(p, OverhaulConfig.PIT_RANGE);
		return hr == null || hr.getType() != HitResult.Type.BLOCK ? null : hr.getLocation();
	}

	/** One creature's pit: the floor under it drops {@link OverhaulConfig#PIT_DEPTH} blocks, then a lid closes over it. */
	private static boolean dropIntoPit(ServerPlayer p, LivingEntity t) {
		if (OverhaulCombat.isBoss(t) || t.getBbWidth() > OverhaulConfig.PIT_MAX_WIDTH || t.getBbHeight() > OverhaulConfig.PIT_MAX_HEIGHT) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		BlockPos feet = BlockPos.containing(t.getX(), t.getY() + 0.2, t.getZ());
		List<BlockPos> hole = new ArrayList<>();
		for (int i = 1; i <= OverhaulConfig.PIT_DEPTH; i++) {
			hole.add(feet.below(i));
		}
		BlockPos floor = feet.below(OverhaulConfig.PIT_DEPTH + 1);
		BlockState feetState = level.getBlockState(feet);
		boolean ok = (t.onGround() || t.getY() - feet.getY() < 0.25) && (feetState.isAir() || feetState.canBeReplaced()) && feetState.getFluidState().isEmpty()
				&& level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && GeoTerrain.canEdit(p, level, feet);
		for (BlockPos h : hole) {
			ok &= GeoTerrain.canEdit(p, level, h) && GeoTerrain.diggable(level, h) && !GeoTerrain.isTemp(level, h);
		}
		if (!ok) {
			return false;
		}
		BlockState lid = OverhaulTerrain.material(level.getBlockState(hole.get(0)));
		OverhaulFx.groundShards(level, Vec3.atBottomCenterOf(feet), level.getBlockState(hole.get(0)), 1.1f, 14, 16, false);
		for (BlockPos h : hole) {
			OverhaulTerrain.burstAt(level, h, level.getBlockState(h), 8);
			GeoTerrain.pocket(level, h, t, OverhaulConfig.PIT_TICKS + 4, 10);
			remember(p, h);
		}
		BlockPos bottom = hole.get(hole.size() - 1);
		t.teleportTo(bottom.getX() + 0.5, bottom.getY(), bottom.getZ() + 0.5);
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		OverhaulCombat.strike(p, t, t.position(), OverhaulCombat.scaled(p, OverhaulConfig.PIT_DAMAGE), 0.0, 0.0);
		level.playSound(null, feet, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		later(p, 4, () -> {
			OverhaulFx.groundShards(level, Vec3.atBottomCenterOf(feet).add(0, 0.5, 0), lid, 0.9f, 10, 10, true);
			place(p, level, feet, lid, OverhaulConfig.PIT_TICKS - 4);
			level.playSound(null, feet, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
		});
		return true;
	}

	/**
	 * The floor breaks out from under every creature near where he aims (bosses and anything too big for a 1x1x2 hole
	 * stay up): each drops into its own pit, which closes over it, and later the ground closes back up and pushes it out.
	 * Gains {@link OverhaulConfig#PIT_GAIN} Matter. Returns the creatures caught (empty: refused).
	 */
	public static List<LivingEntity> pit(ServerPlayer p) {
		Vec3 centre = pitCentre(p);
		if (centre == null) {
			Quirks.say(p, "message.projecthero.overhaul.no_target", ChatFormatting.GRAY);
			return List.of();
		}
		ServerLevel level = p.serverLevel();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(OverhaulConfig.PIT_AREA_RADIUS, 2.5,
				OverhaulConfig.PIT_AREA_RADIUS), e -> OverhaulCombat.isTarget(p, e));
		near.sort((a, b) -> Double.compare(a.position().distanceToSqr(centre), b.position().distanceToSqr(centre)));
		List<LivingEntity> caught = new ArrayList<>();
		for (LivingEntity e : near) {
			if (caught.size() >= OverhaulConfig.PIT_MAX_TARGETS) {
				break;
			}
			if (dropIntoPit(p, e)) {
				caught.add(e);
			}
		}
		if (caught.isEmpty()) {
			Quirks.say(p, near.isEmpty() ? "message.projecthero.overhaul.no_target" : "message.projecthero.overhaul.too_big", ChatFormatting.GRAY);
			return List.of();
		}
		gain(p, OverhaulConfig.PIT_GAIN);
		QuirkCombos.used(p, OverhaulConfig.PIT);
		BlockPos under = groundUnder(p);
		if (under != null) {
			palmToGround(p, under);
		}
		// a crack runs from his palm to the pits
		Vec3 from = p.position().add(0, 0.05, 0);
		for (int i = 1; i <= 4; i++) {
			OverhaulFx.crack(level, from.lerp(centre, i / 4.0), 1.4f, 60);
		}
		touchSound(p);
		sound(p, SoundEvents.GENERIC_EXPLODE, 0.4f, 1.6f);
		Quirks.setVal(p, Overhaul.V_PIT, now(p) + OverhaulConfig.PIT_TICKS);
		done(p, OverhaulConfig.PIT, OverhaulConfig.PIT_COOLDOWN, Overhaul.ANIM_PIT);
		return caught;
	}

	/** A low dash along the ground, his bare hand reaching out: the first enemy it touches is disassembled (free; +3 on a touch). */
	public static boolean strike(ServerPlayer p) {
		Session s = session(p);
		s.strikeDir = flatLook(p);
		s.strikeHit = false;
		Quirks.setVal(p, Overhaul.V_STRIKE, now(p) + OverhaulConfig.STRIKE_TICKS);
		AbilityHelpers.launchSelf(p, s.strikeDir.scale(OverhaulConfig.STRIKE_SPEED).add(0, 0.12, 0));
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
		sound(p, QuirkSounds.WHOOSH, 0.8f, 0.8f);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		done(p, OverhaulConfig.STRIKE, OverhaulConfig.STRIKE_COOLDOWN, Overhaul.ANIM_STRIKE);
		// he may already be in reach
		tickStrike(p, s, true);
		return true;
	}

	private static void tickStrike(ServerPlayer p, Session s, boolean first) {
		long until = Quirks.state(p).val(Overhaul.V_STRIKE);
		if (until == 0L) {
			return;
		}
		if (now(p) >= until || s.strikeHit) {
			Quirks.setVal(p, Overhaul.V_STRIKE, 0L);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 hand = p.position().add(0, p.getBbHeight() * 0.6, 0).add(s.strikeDir.scale(0.9));
		// keep the dash going (vanilla friction eats it on the ground)
		Vec3 v = p.getDeltaMovement();
		if (!first && p.onGround() && v.horizontalDistanceSqr() < OverhaulConfig.STRIKE_SPEED * OverhaulConfig.STRIKE_SPEED * 0.5) {
			AbilityHelpers.launchSelf(p, s.strikeDir.scale(OverhaulConfig.STRIKE_SPEED * 0.8).add(0, Math.max(v.y, 0.0), 0));
		}
		level.sendParticles(OverhaulCombat.RED, hand.x, hand.y, hand.z, 2, 0.1, 0.1, 0.1, 0.0);
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		BlockState g = level.getBlockState(under);
		if (!g.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, g), p.getX(), p.getY() + 0.05, p.getZ(), 4, 0.25, 0.02, 0.25, 0.1);
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(OverhaulConfig.STRIKE_HIT_RADIUS),
				e -> OverhaulCombat.isTarget(p, e))) {
			Vec3 to = e.position().subtract(p.position());
			if (new Vec3(to.x, 0, to.z).dot(s.strikeDir) < -0.3) {
				continue; // behind him
			}
			double d = to.lengthSqr();
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best != null) {
			s.strikeHit = true;
			OverhaulFx.bodyCracks(level, best);
			if (OverhaulCombat.strike(p, best, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.STRIKE_DAMAGE), 1.0, 0.25)) {
				gain(p, OverhaulConfig.STRIKE_GAIN);
				QuirkCamera.preset(p, QuirkCamera.Preset.HEAVY_HIT);
			}
			touchSound(p);
			// he stops dead on the touch
			Vec3 vv = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(vv.x * 0.15, Math.max(vv.y, 0.0), vv.z * 0.15));
			Quirks.setVal(p, Overhaul.V_STRIKE, 0L);
		}
	}

	// ================================================================ V: Reassemble / Shift+V: Fusion or Mend / hold V: Restore

	/**
	 * Reassembles himself -- or the ally he looks at -- to full health, debuffs gone (an ally he saves from near death feeds
	 * Plus Ultra). Once per 45 s. Returns who (null refused).
	 */
	public static @Nullable LivingEntity reassemble(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.REASSEMBLE_COST)) {
			return null;
		}
		LivingEntity looked = AbilityHelpers.raycastEntity(p, OverhaulConfig.REASSEMBLE_RANGE);
		LivingEntity body = looked != null && ally(p, looked) ? looked : p;
		boolean saved = body != p && body.getHealth() < body.getMaxHealth() * 0.3f;
		spend(p, OverhaulConfig.REASSEMBLE_COST);
		restoreBody(p.serverLevel(), body);
		if (saved) {
			PlusUltra.add(p, PlusUltra.ALLY_SAVE);
		}
		touchSound(p);
		Quirks.say(p, "message.projecthero.overhaul.reassembled", ChatFormatting.AQUA, body.getDisplayName());
		done(p, OverhaulConfig.REASSEMBLE, OverhaulConfig.REASSEMBLE_COOLDOWN, Overhaul.ANIM_REASSEMBLE);
		return body;
	}

	/** The off-hand item Mend would work on (a damaged tool or armour piece), or empty. */
	public static ItemStack mendable(ServerPlayer p) {
		ItemStack off = p.getOffhandItem();
		return !off.isEmpty() && off.isDamageableItem() && off.isDamaged() ? off : ItemStack.EMPTY;
	}

	/** Mend: the damaged tool / armour piece in his off hand comes apart and goes back together, +100 durability. */
	public static boolean mend(ServerPlayer p) {
		ItemStack it = mendable(p);
		if (it.isEmpty()) {
			Quirks.say(p, "message.projecthero.overhaul.nothing_to_mend", ChatFormatting.GRAY);
			return false;
		}
		if (!canStart(p, OverhaulConfig.MEND_COST)) {
			return false;
		}
		spend(p, OverhaulConfig.MEND_COST);
		it.setDamageValue(Math.max(0, it.getDamageValue() - OverhaulConfig.MEND_DURABILITY));
		Vec3 h = p.position().add(0, 1.1, 0).add(flatLook(p).scale(0.5));
		ServerLevel level = p.serverLevel();
		level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, it), h.x, h.y, h.z, 12, 0.15, 0.15, 0.15, 0.08);
		OverhaulFx.reassemble(level, h, 0.7f);
		QuirkSounds.play(level, h, OverhaulSounds.REASSEMBLE, 1.0f, 1.25f);
		sound(p, SoundEvents.ANVIL_USE, 0.35f, 1.6f);
		Quirks.say(p, "message.projecthero.overhaul.mended", ChatFormatting.AQUA, it.getHoverName());
		done(p, OverhaulConfig.MEND, OverhaulConfig.MEND_COOLDOWN, Overhaul.ANIM_MEND);
		return true;
	}

	/** Fusion on (absorbing the ground around him) -- or, pressed again, off early. */
	public static boolean fusion(ServerPlayer p) {
		if (Overhaul.fused(p)) {
			endFusion(p);
			return true;
		}
		if (!canStart(p, OverhaulConfig.FUSION_COST)) {
			return false;
		}
		spend(p, OverhaulConfig.FUSION_COST);
		startFusion(p, OverhaulConfig.FUSION_TICKS);
		Quirks.setAnim(p, Overhaul.ANIM_FUSION);
		tugGloves(p);
		return true;
	}

	private static void startFusion(ServerPlayer p, int ticks) {
		Quirks.setVal(p, Overhaul.V_FUSION, now(p) + ticks);
		applyFusion(p);
		ServerLevel level = p.serverLevel();
		// the ground around him breaks into fragments that swirl in and close over him as plates
		Vec3 c = p.position().add(0, 1.0, 0);
		BlockState g = GeoTerrain.groundBlock(p);
		QuirkFx.shards(level, c).block(g).size((float) OverhaulConfig.FUSION_ABSORB_RADIUS * 0.7f).count(48).life(22).reassemble().spawn();
		QuirkFx.ring(level, p.position().add(0, 0.05, 0)).colours(0xD8D0C8, 0x6A6258).size((float) OverhaulConfig.FUSION_ABSORB_RADIUS).thickness(0.7f)
				.life(16).ground().ease(QuirkFx.Ease.OUT_EXPO).spawn();
		OverhaulFx.crack(level, p.position(), 3.5f, 80);
		level.sendParticles(OverhaulCombat.RED_BIG, c.x, c.y, c.z, 16, 0.5, 0.8, 0.5, 0.0);
		touchSound(p);
		sound(p, OverhaulSounds.REASSEMBLE, 1.2f, 0.6f);
		sound(p, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
		sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 0.7f);
		QuirkCamera.shake(p, 0.4f, 10);
	}

	private static void applyFusion(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, ATTACK_ID, OverhaulConfig.FUSION_ATTACK_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR, ARMOR_ID, OverhaulConfig.FUSION_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR_TOUGHNESS, TOUGH_ID, OverhaulConfig.FUSION_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, KB_ID, OverhaulConfig.FUSION_KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, OverhaulConfig.FUSION_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, OverhaulConfig.FUSION_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, OverhaulConfig.FUSION_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void clearFusionModifiers(ServerPlayer p, boolean scaleToo) {
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, ATTACK_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR, ARMOR_ID);
		PowerToggles.clearModifier(p, Attributes.ARMOR_TOUGHNESS, TOUGH_ID);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, KB_ID);
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
		PowerToggles.clearModifier(p, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ULT_KB_ID);
		if (scaleToo) {
			PowerToggles.clearModifier(p, Attributes.SCALE, SCALE_ID);
		}
	}

	/** Fusion ends: the stone falls away (the body shrinks back over a few ticks); the cooldown starts now (not after the ultimate). */
	public static void endFusion(ServerPlayer p) {
		if (Quirks.state(p).val(Overhaul.V_FUSION) == 0L) {
			return;
		}
		Quirks.setVal(p, Overhaul.V_FUSION, 0L);
		clearFusionModifiers(p, false);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		QuirkFx.shards(level, c).block(GeoTerrain.groundBlock(p)).size(1.8f).count(36).life(26).spawn();
		sound(p, SoundEvents.STONE_BREAK, 1.2f, 0.7f);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.ultFusion) {
			s.ultFusion = false;
		} else {
			Quirks.setCooldown(p, OverhaulConfig.FUSION, OverhaulConfig.FUSION_COOLDOWN);
		}
		Quirks.setAnim(p, Overhaul.ANIM_FUSION_END);
	}

	/** The body eases toward its Fusion size (or back), waiting if the bigger body would not fit. */
	private static void tickScale(ServerPlayer p, boolean fused) {
		AttributeInstance inst = p.getAttribute(Attributes.SCALE);
		if (inst == null) {
			return;
		}
		AttributeModifier m = inst.getModifier(SCALE_ID);
		double cur = m == null ? 0.0 : m.amount();
		double target = fused ? OverhaulConfig.FUSION_SCALE : 0.0;
		if (Math.abs(cur - target) < 1.0e-4) {
			return;
		}
		double step = OverhaulConfig.FUSION_SCALE / Math.max(1, OverhaulConfig.FUSION_GROW_TICKS);
		double next = cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
		next = Math.round(next * 1000.0) / 1000.0;
		if (next > cur) {
			double w = 0.6 * (1.0 + next);
			double h = 1.8 * (1.0 + next);
			AABB box = new AABB(p.getX() - w / 2, p.getY(), p.getZ() - w / 2, p.getX() + w / 2, p.getY() + h, p.getZ() + w / 2);
			if (!p.level().noCollision(p, box)) {
				return;
			}
		}
		if (next <= 1.0e-4) {
			PowerToggles.clearModifier(p, Attributes.SCALE, SCALE_ID);
		} else {
			PowerToggles.modifier(p, Attributes.SCALE, SCALE_ID, next, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	/** Hold V: Restore -- the blocks explosions broke near him rebuild as a wave ({@link #restoreGround}). Returns how many. */
	public static int restore(ServerPlayer p) {
		int n = restoreGround(p);
		if (n == 0) {
			Quirks.say(p, "message.projecthero.overhaul.nothing_broken", ChatFormatting.GRAY);
		}
		OverhaulFx.reformRipple(p.serverLevel(), p.position().add(0, 0.05, 0), (float) OverhaulConfig.RESTORE_RADIUS, 22);
		done(p, OverhaulConfig.RESTORE, OverhaulConfig.RESTORE_COOLDOWN, Overhaul.ANIM_RESTORE);
		return n;
	}

	/**
	 * Restore: the broken ground around him puts itself back -- every block an explosion broke within
	 * {@link OverhaulConfig#RESTORE_RADIUS} in the last five minutes is rebuilt, nearest first, as a wave (the scattered
	 * debris -- those blocks' dropped items -- is drawn back in with it). Returns the number of blocks it will rebuild.
	 */
	public static int restoreGround(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		List<OverhaulRestore.Broken> broken = OverhaulRestore.take(level, p.position(), OverhaulConfig.RESTORE_RADIUS, OverhaulConfig.RESTORE_MAX_BLOCKS);
		int n = broken.size();
		if (n == 0) {
			return 0;
		}
		sound(p, OverhaulSounds.REASSEMBLE, 1.3f, 0.8f);
		for (int i = 0; i < n; i++) {
			OverhaulRestore.Broken b = broken.get(i);
			int delay = 4 + (int) ((long) i * OverhaulConfig.RESTORE_WAVE_TICKS / Math.max(1, n));
			later(p, delay, () -> rebuild(p, level, b));
		}
		later(p, 4 + OverhaulConfig.RESTORE_WAVE_TICKS, () -> sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.1f));
		Quirks.say(p, "message.projecthero.overhaul.restored", ChatFormatting.AQUA, n);
		return n;
	}

	private static void rebuild(ServerPlayer p, ServerLevel level, OverhaulRestore.Broken b) {
		BlockPos pos = b.pos();
		BlockState now = level.getBlockState(pos);
		if (!level.hasChunkAt(pos) || !(now.isAir() || now.canBeReplaced()) || now.hasBlockEntity()
				|| !p.mayBuild() || !level.mayInteract(p, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return;
		}
		// never into a living thing
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
			return;
		}
		level.setBlock(pos, b.state(), Block.UPDATE_ALL);
		// the debris it left is drawn back in: one dropped item of that block, if one lies near
		var item = b.state().getBlock().asItem();
		if (item != Items.AIR) {
			for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8.0), e -> e.getItem().is(item))) {
				ie.getItem().shrink(1);
				if (ie.getItem().isEmpty()) {
					ie.discard();
				}
				AbilityHelpers.line(level, ie.position(), Vec3.atCenterOf(pos), new BlockParticleOption(ParticleTypes.BLOCK, b.state()), 2.0);
				break;
			}
		}
		Vec3 c = Vec3.atCenterOf(pos);
		if (level.random.nextInt(3) == 0) {
			OverhaulFx.groundShards(level, c, b.state(), 0.7f, 6, 10, true);
		}
		level.sendParticles(OverhaulCombat.MEND, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.0);
		if (level.random.nextInt(4) == 0) {
			level.playSound(null, pos, b.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 0.9f);
		}
	}

	// ================================================================ X: Ground Shift / Shift+X: Pillar Launch / hold X: Terrain Surf

	/** Synced {@link Overhaul#V_SURF} while the wave runs: it runs for as long as X is held, so it has no end time. */
	public static final long SURF_HELD = Long.MAX_VALUE;

	/** Server: the wave is running. */
	public static boolean surfing(ServerPlayer p) {
		return Quirks.state(p).val(Overhaul.V_SURF) != 0L;
	}

	/**
	 * Ground Shift (tap X): the floor itself slides him {@link OverhaulConfig#GROUND_SHIFT_DISTANCE} blocks the way he looks
	 * as a rippling wave -- he stands still and is carried. False if refused.
	 */
	public static boolean groundShift(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.GROUND_SHIFT_COST)) {
			return false;
		}
		BlockPos under = groundUnder(p);
		if (!needGround(p, under) || !p.onGround() && Math.abs(p.getY() - under.getY()) > 0.6) {
			if (under != null) {
				Quirks.say(p, "message.projecthero.overhaul.no_ground", ChatFormatting.RED);
			}
			return false;
		}
		spend(p, OverhaulConfig.GROUND_SHIFT_COST);
		Session s = session(p);
		s.shiftDir = flatLook(p);
		s.safeFallUntil = Math.max(s.safeFallUntil, now(p) + OverhaulConfig.GROUND_SHIFT_TICKS + 20);
		Quirks.setVal(p, Overhaul.V_SHIFT, now(p) + OverhaulConfig.GROUND_SHIFT_TICKS);
		palmToGround(p, under);
		sound(p, SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.6f);
		sound(p, OverhaulSounds.DISASSEMBLE, 0.7f, 0.7f);
		done(p, OverhaulConfig.GROUND_SHIFT, OverhaulConfig.GROUND_SHIFT_COOLDOWN, Overhaul.ANIM_SHIFT);
		tickShift(p, s);
		return true;
	}

	private static void tickShift(ServerPlayer p, Session s) {
		long until = Quirks.state(p).val(Overhaul.V_SHIFT);
		if (until == 0L) {
			return;
		}
		ServerLevel level = p.serverLevel();
		if (now(p) >= until) {
			Quirks.setVal(p, Overhaul.V_SHIFT, 0L);
			Vec3 v = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.25, Math.max(v.y, 0.0), v.z * 0.25));
			return;
		}
		double speed = OverhaulConfig.GROUND_SHIFT_DISTANCE / OverhaulConfig.GROUND_SHIFT_TICKS;
		BlockPos cell = groundUnder(p);
		double dy = cell == null ? -0.3 : cell.getY() - p.getY();
		double vy = cell == null ? -0.2 : dy > 0.05 ? Mth.clamp(dy * 0.6, 0.1, 0.6) : Mth.clamp(dy * 0.5, -0.3, -0.04);
		AbilityHelpers.launchSelf(p, new Vec3(s.shiftDir.x * speed, vy, s.shiftDir.z * speed));
		p.resetFallDistance();
		if (cell != null) {
			// the ripple: the floor lifts under him and a step ahead, and settles behind
			OverhaulFx.ripple(level, cell, 0.7f);
			BlockPos ahead = OverhaulTerrain.groundCell(level, Mth.floor(p.getX() + s.shiftDir.x * 1.5), cell.getY(), Mth.floor(p.getZ() + s.shiftDir.z * 1.5));
			if (ahead != null) {
				OverhaulFx.ripple(level, ahead, 0.9f);
			}
			if (now(p) % 2 == 0) {
				level.playSound(null, cell, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 0.7f, 0.7f);
			}
		}
	}

	/**
	 * Hold X: the ground under him comes apart and reforms a step ahead, over and over: a wave of earth carries him along the
	 * way he looks for as long as X is held (v0.16.7, user) -- letting go, X again, water, or Matter running dry
	 * ({@link OverhaulConfig#SURF_DRAIN_PER_TICK}) ends it -- stepping him up 2-block rises and throwing aside what it runs
	 * over. Returns true when it started (false: refused, or it was running and has stopped).
	 */
	public static boolean surf(ServerPlayer p) {
		if (surfing(p)) {
			endSurf(p);
			return false;
		}
		if (!canStart(p, OverhaulConfig.SURF_COST)) {
			return false;
		}
		BlockPos under = groundUnder(p);
		if (!needGround(p, under)) {
			return false;
		}
		spend(p, OverhaulConfig.SURF_COST);
		Session s = session(p);
		s.surfHit.clear();
		s.safeFallUntil = Long.MAX_VALUE; // set to SURF_SAFE_TICKS after the end by endSurf
		Quirks.setVal(p, Overhaul.V_SURF, SURF_HELD);
		Quirks.setVal(p, Overhaul.V_SHIFT, 0L);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SURF_STEP_ID, OverhaulConfig.SURF_STEP_HEIGHT - 0.6,
				AttributeModifier.Operation.ADD_VALUE);
		palmToGround(p, under);
		touchSound(p);
		sound(p, SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.6f);
		Quirks.setAnim(p, Overhaul.ANIM_SURF);
		tugGloves(p);
		return true;
	}

	/** Off the wave: the step assist goes, a short no-fall window stays, and the (short) cooldown starts now. */
	public static void endSurf(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SURF_STEP_ID);
		if (!surfing(p)) {
			return;
		}
		Quirks.setVal(p, Overhaul.V_SURF, 0L);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.safeFallUntil = now(p) + OverhaulConfig.SURF_SAFE_TICKS;
		}
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.4, Math.max(v.y, 0.0), v.z * 0.4));
		Quirks.setCooldown(p, OverhaulConfig.SURF, OverhaulConfig.SURF_COOLDOWN);
		if (Quirks.state(p).animId == Overhaul.ANIM_SURF) {
			Quirks.setAnim(p, QuirkState.ANIM_NONE);
		}
	}

	private static void tickSurf(ServerPlayer p, Session s) {
		if (!surfing(p)) {
			return;
		}
		if (p.isInWater() || p.isPassenger() || !p.isAlive()) {
			endSurf(p);
			return;
		}
		// the ride costs Matter as it goes; dry, he drops off the wave
		spend(p, OverhaulConfig.SURF_DRAIN_PER_TICK);
		if (Quirks.meter(p) < 1f) {
			endSurf(p);
			return;
		}
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, SURF_STEP_ID, OverhaulConfig.SURF_STEP_HEIGHT - 0.6,
				AttributeModifier.Operation.ADD_VALUE);
		ServerLevel level = p.serverLevel();
		Vec3 dir = flatLook(p);
		BlockPos cell = groundUnder(p);
		// ride the ground: forward, hugging the terrain. On the level he is pressed gently down onto it -- a downward move
		// that lands keeps him "on the ground", which is what lets the 2-block step assist carry him up a rise in the
		// same move (vanilla only steps up from the ground)
		double dy = cell == null ? -0.5 : cell.getY() - p.getY();
		double vy = cell == null ? -0.25 : dy > 0.05 ? Mth.clamp(dy * 0.5, 0.1, 0.55) : Mth.clamp(dy * 0.5, -0.35, -0.08);
		AbilityHelpers.launchSelf(p, new Vec3(dir.x * OverhaulConfig.SURF_SPEED, vy, dir.z * OverhaulConfig.SURF_SPEED));
		p.resetFallDistance();
		if (cell != null) {
			BlockState g = level.getBlockState(cell.below());
			// the wave: the ground bursting up under him and a short-lived ridge of it rising in his wake
			OverhaulTerrain.burstAt(level, cell, g, 4);
			if (now(p) % 2 == 0) {
				OverhaulFx.ripple(level, cell, 0.8f);
			}
			level.sendParticles(OverhaulCombat.RED, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.3, 0.05, 0.3, 0.0);
			BlockPos wake = OverhaulTerrain.groundCell(level, Mth.floor(p.getX() - dir.x * 2.2), cell.getY(), Mth.floor(p.getZ() - dir.z * 2.2));
			if (wake != null && Math.abs(wake.getY() - cell.getY()) <= 1 && now(p) % 2 == 0) {
				place(p, level, wake, OverhaulTerrain.material(level.getBlockState(wake.below())), OverhaulConfig.SURF_WAKE_TTL);
			}
			if (now(p) % 4 == 0) {
				level.playSound(null, cell, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f + level.random.nextFloat() * 0.2f);
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(0.8, 0.2, 0.8).move(dir.scale(0.8)),
				e -> OverhaulCombat.isTarget(p, e) && now(p) - s.surfHit.getOrDefault(e.getId(), -1000L) >= OverhaulConfig.SURF_REHIT_TICKS)) {
			s.surfHit.put(e.getId(), now(p));
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			Vec3 from = e.position().subtract(side.scale(Math.signum(side.dot(e.position().subtract(p.position())) + 1.0e-3)));
			OverhaulCombat.strike(p, e, from, OverhaulCombat.scaled(p, OverhaulConfig.SURF_DAMAGE), 0.9, 0.45);
		}
	}

	/** The ground under him shoots up as a stone pillar and flings him high (no fall damage for a while). */
	public static boolean pillar(ServerPlayer p) {
		if (!canStart(p, OverhaulConfig.PILLAR_COST)) {
			return false;
		}
		BlockPos base = groundUnder(p);
		if (!needGround(p, base)) {
			return false;
		}
		spend(p, OverhaulConfig.PILLAR_COST);
		endSurf(p);
		ServerLevel level = p.serverLevel();
		BlockState mat = OverhaulTerrain.material(level.getBlockState(base.below()));
		Vec3 f = flatLook(p);
		AbilityHelpers.launchSelf(p, new Vec3(f.x * OverhaulConfig.PILLAR_FORWARD, OverhaulConfig.PILLAR_UP, f.z * OverhaulConfig.PILLAR_FORWARD));
		session(p).safeFallUntil = now(p) + OverhaulConfig.PILLAR_SAFE_TICKS;
		palmToGround(p, base);
		touchSound(p);
		sound(p, SoundEvents.PISTON_EXTEND, 1.0f, 0.5f);
		sound(p, OverhaulSounds.SPIKE, 1.0f, 0.6f);
		QuirkCamera.preset(p, QuirkCamera.Preset.DASH);
		OverhaulFx.groundShards(level, Vec3.atBottomCenterOf(base).add(0, 1.2, 0), mat, 1.4f, 16, 16, true);
		for (int h = 0; h < OverhaulConfig.PILLAR_HEIGHT; h++) {
			final int row = h;
			later(p, 3 + h, () -> { // he has risen clear of the cell by then (server-side position lags a tick or two)
				BlockPos at = base.above(row);
				OverhaulTerrain.burstAt(level, at, mat, 6);
				if (row == 0 || level.getBlockState(at.below()).isSolid()) {
					place(p, level, at, mat, OverhaulConfig.PILLAR_TTL - row * 2);
				}
				level.playSound(null, at, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f + row * 0.15f);
			});
		}
		done(p, OverhaulConfig.PILLAR, OverhaulConfig.PILLAR_COOLDOWN, Overhaul.ANIM_PILLAR);
		return true;
	}

	/** No fall damage while surfing and for a while after a surf / pillar launch; Germophobe; no knockback in the ultimate. */
	static float modifyIncomingDamage(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source, float amount) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.safeFallUntil > now(p) && source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
			return 0f;
		}
		if (source.getEntity() instanceof Mob && amount > 0f) {
			// Germophobe: he hates being touched -- the next move within 3 s hits 20% harder
			session(p).germUntil = now(p) + OverhaulConfig.GERMOPHOBE_TICKS;
		}
		return amount;
	}

	// ================================================================ survival: Disassembly mining

	/**
	 * Sneak + a bare-hand punch on stone or earth: the 3x3 of it in that face comes apart into its drops (as a pickaxe would
	 * break it), for {@link OverhaulConfig#MINING_COST} Matter. Only natural ground, only where he may build (build rights,
	 * claims, spawn protection, the world border -- {@link GeoTerrain#canEdit}). Returns the blocks broken (0 = nothing).
	 */
	public static int mine(ServerPlayer p, BlockPos hit, Direction face) {
		Session s = session(p);
		if (now(p) - s.minedAt < OverhaulConfig.MINING_COOLDOWN && now(p) >= s.minedAt) {
			return 0;
		}
		ServerLevel level = p.serverLevel();
		if (!GeoTerrain.natural(level.getBlockState(hit)) || !hasMatter(p, OverhaulConfig.MINING_COST)) {
			return 0;
		}
		Direction.Axis axis = face.getAxis();
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		int n = 0;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				BlockPos pos = switch (axis) {
					case X -> hit.offset(0, a, b);
					case Y -> hit.offset(a, 0, b);
					default -> hit.offset(a, b, 0);
				};
				BlockState st = level.getBlockState(pos);
				if (!GeoTerrain.natural(st) || !GeoTerrain.diggable(level, pos) || GeoTerrain.isTemp(level, pos) || !mayMine(p, level, pos)) {
					continue;
				}
				Block.dropResources(st, level, pos, null, p, pick);
				level.destroyBlock(pos, false, p);
				if (n % 3 == 0) {
					OverhaulFx.groundShards(level, Vec3.atCenterOf(pos), st, 0.8f, 8, 12, false);
				}
				n++;
			}
		}
		if (n == 0) {
			return 0;
		}
		s.minedAt = now(p);
		spend(p, OverhaulConfig.MINING_COST);
		tugGloves(p);
		Quirks.setAnim(p, Overhaul.ANIM_TOUCH);
		QuirkSounds.play(level, Vec3.atCenterOf(hit), OverhaulSounds.DISASSEMBLE, 1.0f, 1.1f);
		return n;
	}

	/**
	 * He may break {@code pos} for good (mining is his own digging, so the ability terrain-damage switch doesn't apply): build
	 * rights, adventure mode, claims and spawn protection ({@code mayInteract}), the world border, a loaded chunk.
	 */
	public static boolean mayMine(ServerPlayer p, ServerLevel level, BlockPos pos) {
		return p.mayBuild() && !p.blockActionRestricted(level, pos, p.gameMode.getGameModeForPlayer()) && level.mayInteract(p, pos)
				&& level.getWorldBorder().isWithinBounds(pos) && level.isInWorldBounds(pos) && level.hasChunkAt(pos);
	}

	// ================================================================ passive: the bare-hand touch

	/**
	 * A bare-handed melee hit with the gloves off (from {@code OverhaulMod}'s attack hook): the touch disassembles a
	 * little too -- {@link OverhaulConfig#TOUCH_PASSIVE_DAMAGE} a tick later (after vanilla's own hit).
	 */
	public static void bareTouch(ServerPlayer p, LivingEntity t) {
		if (Overhaul.gloved(p) || !p.getMainHandItem().isEmpty() || !OverhaulCombat.isTarget(p, t) || p.getAttackStrengthScale(0.5f) < 0.9f) {
			return;
		}
		later(p, 1, () -> {
			if (t.isAlive()) {
				OverhaulFx.bodyCracks(p.serverLevel(), t);
				OverhaulCombat.strike(p, t, p.position(), OverhaulCombat.scaled(p, OverhaulConfig.TOUCH_PASSIVE_DAMAGE), 0.0, 0.0);
			}
		});
		Quirks.setAnim(p, Overhaul.ANIM_TOUCH);
	}

	// ================================================================ Plus Ultra: Total Disassembly

	/**
	 * Overhaul -- Total Disassembly: both palms on the ground. The ground for {@link OverhaulConfig#ULT_RADIUS} blocks breaks
	 * apart and rises as a sea of spikes rolling outward in three waves ({@link OverhaulConfig#ULT_DAMAGE} each and a toss);
	 * behind each wave the ground it lifted reassembles perfectly (ledger-restored) in a white-teal ripple. Meanwhile he is
	 * fused (x1.5) and can't be knocked back. The drawback: every scrap of Matter goes into it.
	 */
	public static void ultimate(ServerPlayer p) {
		ultimate(p, OverhaulConfig.ULT_RADIUS);
	}

	/** Total Disassembly out to {@code radius} (the gametests keep it inside their own box). */
	public static void ultimate(ServerPlayer p, double radius) {
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		endSurf(p);
		Quirks.setVal(p, Overhaul.V_SHIFT, 0L);
		Quirks.setVal(p, Overhaul.V_THORN_AIM, 0L);
		clearHold(p, s);
		long until = now(p) + OverhaulConfig.ULT_TICKS;
		Quirks.setVal(p, Overhaul.V_ULT, until);
		if (!Overhaul.fused(p)) {
			s.ultFusion = true;
			startFusion(p, OverhaulConfig.ULT_TICKS);
		} else if (Quirks.state(p).val(Overhaul.V_FUSION) < until) {
			Quirks.setVal(p, Overhaul.V_FUSION, until);
		}
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ULT_KB_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
		Quirks.setAnim(p, Overhaul.ANIM_ULTIMATE);
		tugGloves(p);
		Vec3 c = p.position();
		BlockPos centre = BlockPos.containing(c.x, c.y + 0.2, c.z);
		// the crack web out to the edge
		QuirkFx.decal(level, c.add(0, 0.05, 0)).style(QuirkFx.DECAL_CRACK).colour(0x2A1410).size((float) radius).life(150)
				.yaw(level.random.nextFloat() * 360f).spawn();
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + level.random.nextDouble() * 0.5;
			OverhaulFx.crack(level, c.add(Math.cos(a) * 7, 0.05, Math.sin(a) * 7), 4.5f, 140);
		}
		QuirkSounds.play(level, c, OverhaulSounds.DISASSEMBLE, 2.0f, 0.5f);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.6f);
		QuirkCamera.shakeAround(level, c, 32, 0.6f, 20);
		final List<Set<Integer>> hits = new ArrayList<>();
		for (int w = 0; w < OverhaulConfig.ULT_WAVES.length; w++) {
			hits.add(new HashSet<>());
			final int wave = w;
			for (int k = 0; k < OverhaulConfig.ULT_WAVE_TICKS; k++) {
				final double r0 = 2.0 + (radius - 2.0) * k / OverhaulConfig.ULT_WAVE_TICKS;
				final double r1 = 2.0 + (radius - 2.0) * (k + 1) / OverhaulConfig.ULT_WAVE_TICKS;
				later(p, OverhaulConfig.ULT_WAVES[w] + k, () -> ultBand(p, c, centre, r0, r1, wave, hits.get(wave)));
			}
			later(p, OverhaulConfig.ULT_WAVES[w], () -> {
				QuirkSounds.play(level, c, OverhaulSounds.SPIKE, 2.0f, 0.55f + 0.1f * wave);
				QuirkCamera.shakeAround(level, c, 32, 0.45f, 10);
			});
			later(p, OverhaulConfig.ULT_WAVES[w] + OverhaulConfig.ULT_WAVE_TICKS + OverhaulConfig.ULT_REASSEMBLE_TICKS / 2, () -> {
				OverhaulFx.reformRipple(level, c.add(0, 0.06, 0), (float) radius, 20);
				QuirkSounds.play(level, c, OverhaulSounds.REASSEMBLE, 1.6f, 0.8f + 0.1f * wave);
			});
		}
		later(p, OverhaulConfig.ULT_TICKS, () -> ultimateEnds(p));
	}

	/** One tick of one wave: the band between {@code r0} and {@code r1} rises as spikes, and lifts, and hits. */
	private static void ultBand(ServerPlayer p, Vec3 c, BlockPos centre, double r0, double r1, int wave, Set<Integer> hit) {
		ServerLevel level = p.serverLevel();
		double r = (r0 + r1) * 0.5;
		double spacing = 2.4 - 0.3 * wave;
		int n = Math.max(6, (int) Math.round(2 * Math.PI * r / spacing));
		double off = level.random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double a = off + i * 2 * Math.PI / n;
			int x = Mth.floor(c.x + Math.cos(a) * r), z = Mth.floor(c.z + Math.sin(a) * r);
			BlockPos cell = OverhaulTerrain.groundCell(level, x, centre.getY(), z);
			if (cell == null || Math.abs(cell.getY() - centre.getY()) > 4) {
				continue;
			}
			Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
			float h = (2.2f + 1.4f * level.random.nextFloat()) * (1f + 0.15f * wave);
			OverhaulFx.spike(level, cell, out, h, 0.5f + 0.08f * wave, 18);
			// every other spike takes the ground it rose from apart for a moment; it reassembles behind the wave
			if (i % 2 == 0) {
				liftAndReassemble(p, level, cell.below());
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r1 + 1, 4, r1 + 1),
				e -> OverhaulCombat.isTarget(p, e) && !hit.contains(e.getId()))) {
			double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
			if (d < r0 - 0.8 || d > r1 + 0.8 || Math.abs(e.getY() - c.y) > 4) {
				continue;
			}
			hit.add(e.getId());
			OverhaulFx.bodyCracks(level, e);
			OverhaulCombat.strike(p, e, c, OverhaulCombat.scaled(p, OverhaulConfig.ULT_DAMAGE), 0.5, OverhaulConfig.ULT_LIFT);
		}
	}

	/** The block at {@code ground} breaks apart into rising fragments and is put back {@link OverhaulConfig#ULT_REASSEMBLE_TICKS} later. */
	private static void liftAndReassemble(ServerPlayer p, ServerLevel level, BlockPos ground) {
		BlockState st = level.getBlockState(ground);
		if (!GeoTerrain.canEdit(p, level, ground) || !GeoTerrain.diggable(level, ground) || GeoTerrain.isTemp(level, ground)) {
			return;
		}
		// never open the ground under a player
		AABB above = new AABB(ground.above()).inflate(0.2, 0.5, 0.2);
		if (!level.getEntitiesOfClass(Player.class, above, e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
			return;
		}
		if (!GeoTerrain.lift(level, ground, OverhaulConfig.ULT_REASSEMBLE_TICKS + 20)) {
			return;
		}
		remember(p, ground);
		OverhaulFx.groundShards(level, Vec3.atCenterOf(ground).add(0, 0.6, 0), st, 1.1f, 8, OverhaulConfig.ULT_REASSEMBLE_TICKS + 8, true);
		later(p, OverhaulConfig.ULT_REASSEMBLE_TICKS, () -> {
			if (GeoTerrain.isTemp(level, ground)) {
				GeoTerrain.restoreNow(level, ground);
				Vec3 at = Vec3.atCenterOf(ground).add(0, 0.55, 0);
				level.sendParticles(OverhaulCombat.MEND, at.x, at.y, at.z, 2, 0.3, 0.05, 0.3, 0.0);
			}
		});
	}

	private static void ultimateEnds(ServerPlayer p) {
		if (Quirks.state(p).val(Overhaul.V_ULT) == 0L) {
			return;
		}
		Quirks.setVal(p, Overhaul.V_ULT, 0L);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, ULT_KB_ID);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.ultFusion) {
			endFusion(p);
		}
		// the drawback: every scrap of matter went into it
		Quirks.setMeter(p, OverhaulConfig.ULT_MATTER_AFTER);
		session(p).lastSpend = now(p);
		Quirks.say(p, "message.projecthero.overhaul.ultimate_spent", ChatFormatting.GRAY);
	}

	// ================================================================ the tick / lifecycle

	/** Matter refilled a tick: 10 a second on stone or earth, 3 on wood or in the air; x0.5 in the Fusion form. */
	public static float matterRegenPerTick(boolean onEarth, boolean fused) {
		return (onEarth ? OverhaulConfig.MATTER_GROUND_PER_SECOND : OverhaulConfig.MATTER_AIR_PER_SECOND) / 20f
				* (fused ? OverhaulConfig.FUSION_REGEN_MULT : 1f);
	}

	/** He stands on stone or earth (his feet draw Matter from it). */
	public static boolean onEarth(ServerPlayer p) {
		return p.onGround() && GeoTerrain.natural(p.level().getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ())));
	}

	static void tick(ServerPlayer p) {
		long now = now(p);
		QuirkState st = Quirks.state(p);
		if (st.val(Overhaul.V_INIT) == 0L) {
			Quirks.setMeter(p, 100f);
			Quirks.setVal(p, Overhaul.V_INIT, 1L);
			st = Quirks.state(p);
		}
		if (st.vals.containsKey(Overhaul.V_GLOVES)) {
			Quirks.edit(p, n -> n.vals.remove(Overhaul.V_GLOVES)); // the v0.16.7 Gloves toggle is gone
			st = Quirks.state(p);
		}
		Session s = session(p);
		long fusionUntil = st.val(Overhaul.V_FUSION);
		boolean fused = fusionUntil > now;
		if (fusionUntil != 0L && !fused) {
			endFusion(p);
		}
		if (fused) {
			applyFusion(p);
			if (now % 8 == 0) {
				Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
				p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
						c.x, c.y, c.z, 2, 0.4, 0.6, 0.4, 0.0);
			}
		}
		// v0.17.1: Matter from the ground under his feet (none while the surf drains it or the ultimate burns it)
		if (st.val(Overhaul.V_SURF) == 0L && st.val(Overhaul.V_ULT) <= now && (now - s.lastSpend >= OverhaulConfig.MATTER_REGEN_DELAY || now < s.lastSpend)) {
			Quirks.addMeterPaced(p, matterRegenPerTick(onEarth(p), fused));
		}
		// the full costume: the plague mask filters his own dust and any poison (it never takes hold)
		if (now % 10 == 0 && p.hasEffect(MobEffects.POISON) && com.projecthero.mod.quirk.QuirkPerks.fullSet(p)) {
			p.removeEffect(MobEffects.POISON);
		}
		tickScale(p, fused);
		tickStrike(p, s, false);
		tickShift(p, s);
		tickSurf(p, s);
		tickForest(p, s, now);
		tickHold(p, s, now);
		if (s.safeFallUntil > now && !p.onGround()) {
			p.resetFallDistance();
		}
		if (!s.jobs.isEmpty()) {
			List<Job> due = new ArrayList<>();
			Iterator<Job> it = s.jobs.iterator();
			while (it.hasNext()) {
				Job j = it.next();
				if (j.at <= now) {
					due.add(j);
					it.remove();
				}
			}
			for (Job j : due) {
				j.run.run();
			}
		}
	}

	/**
	 * The quirk stops (death, logout, world change, Erased, revoke): Fusion ends at once, waiting moves are dropped (the ground
	 * Total Disassembly had lifted is put straight back).
	 */
	static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.jobs.clear();
			s.forestUntil = 0L;
			s.holdSlot = null;
			for (BlockPos pos : List.copyOf(s.placed)) {
				if (GeoTerrain.isTemp(p.serverLevel(), pos) && p.serverLevel().getBlockState(pos).isAir()) {
					GeoTerrain.restoreNow(p.serverLevel(), pos); // lifted ground never waits out its clock
				}
			}
		}
		QuirkState st = Quirks.state(p);
		for (String k : new String[] { Overhaul.V_STRIKE, Overhaul.V_SURF, Overhaul.V_SHIFT, Overhaul.V_THORN_AIM, Overhaul.V_HOLD_SLOT, Overhaul.V_ULT }) {
			if (st.val(k) != 0L) {
				Quirks.setVal(p, k, 0L);
			}
		}
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, SURF_STEP_ID);
		if (Quirks.state(p).val(Overhaul.V_FUSION) != 0L) {
			endFusion(p);
		}
		clearFusionModifiers(p, true);
	}

	/** True while Overhaul holds the quirk (used by the kit's checks). */
	static boolean holds(ServerPlayer p) {
		return Quirks.holds(p, Quirk.OVERHAUL);
	}
}
