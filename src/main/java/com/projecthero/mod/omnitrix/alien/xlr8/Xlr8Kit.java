package com.projecthero.mod.omnitrix.alien.xlr8;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: XLR8, the Kineceleran -- a sleek blue-and-black velociraptor of a speedster with a visor helmet, a tail and ball
 * feet. Very fast (faster still sprinting), fast hands, light armour. Shift+X turns on wall and water running.
 *
 * <pre>
 *   R        Dash Flurry     a lunge and a blur of 6 hits in front (2 each)     Shift+R  Tornado Run    3 s of running rings: a twister lifts what is near
 *   G        Sonic Punch     close the gap to your target in a blink: 8          Shift+G  Snatch         grab the item out of a mob's hand (2)
 *   X        Blur Dash       a 10-block dash leaving afterimages (hits for 3)    Shift+X  Wall Run       toggle: run up walls and across water
 *   Z        Whirlwind       spin: everything within 6 is blown away (4)         Shift+Z  Kick Barrage   8 kicks in 0.8 s (1.5 each), the last launches
 *   C        Time Burst      everything within 12 slows to a crawl for 5 s       Shift+C  Speed Mine     strip-mine a 1x2 tunnel 16 blocks ahead in a blink
 *   Attack   a double jab (+1.5)          Use (empty hand)  sweep up every dropped item within 14 blocks
 * </pre>
 */
public final class Xlr8Kit extends AlienKit {
	public static final String ID = "xlr8";
	public static final int COLOUR = 0x3A7BD5;
	public static final int GLOW = 0x9FD8FF;

	public static final int ANIM_FLURRY = 1;
	public static final int ANIM_TORNADO = 2;
	public static final int ANIM_PUNCH = 3;
	public static final int ANIM_SNATCH = 4;
	public static final int ANIM_DASH = 5;
	public static final int ANIM_WALLRUN = 6;
	public static final int ANIM_WHIRL = 7;
	public static final int ANIM_KICKS = 8;
	public static final int ANIM_BURST = 9;
	public static final int ANIM_MINE = 10;
	public static final int ANIM_JAB = 11;
	public static final int ANIM_SWEEP = 12;

	public static final int F_WALLRUN = 1;
	public static final int F_TORNADO = 2;
	public static final int F_DASH = 4;

	public static final String FLURRY = "dash_flurry";
	public static final String TORNADO = "tornado_run";
	public static final String PUNCH = "sonic_punch";
	public static final String SNATCH = "snatch";
	public static final String DASH = "blur_dash";
	public static final String WALLRUN = "wall_run";
	public static final String WHIRL = "whirlwind";
	public static final String KICKS = "kick_barrage";
	public static final String BURST = "time_burst";
	public static final String MINE = "speed_mine";
	public static final String JAB = "double_jab";
	public static final String SWEEP = "item_sweep";

	public static final float FLURRY_HIT = 2f;
	public static final int FLURRY_TICKS = 12;
	public static final float TORNADO_HIT = 1.5f;
	public static final float TORNADO_END = 4f;
	public static final int TORNADO_TICKS = 60;
	public static final double TORNADO_RADIUS = 6;
	public static final float PUNCH_DAMAGE = 8f;
	public static final float DASH_DAMAGE = 3f;
	public static final float WHIRL_DAMAGE = 4f;
	public static final double WHIRL_RADIUS = 6;
	public static final float KICK_HIT = 1.5f;
	public static final int KICK_TICKS = 16;
	public static final double BURST_RADIUS = 12;
	public static final int BURST_TICKS = 100;
	public static final int MINE_LENGTH = 16;
	public static final float JAB_EXTRA = 1.5f;
	public static final double SPRINT_BONUS = 0.5;

	public static final SoundEvent SND_DASH = sound("xlr8_dash");
	public static final SoundEvent SND_HIT = sound("xlr8_hit");
	public static final SoundEvent SND_TORNADO = sound("xlr8_tornado");
	public static final SoundEvent SND_BURST = sound("xlr8_burst");
	public static final SoundEvent SND_VISOR = sound("xlr8_visor");

	private static final ResourceLocation SPRINT_ID = PowerToggles.id("omnitrix_xlr8_sprint");
	private static final ResourceLocation ATTACK_SPEED_ID = PowerToggles.id("omnitrix_xlr8_attack_speed");

	/** player -> the Tornado Run's eye. */
	private static final Map<UUID, Twister> TWISTERS = new ConcurrentHashMap<>();
	/** player -> creatures the current Blur Dash already hit. */
	private static final Map<UUID, Set<Integer>> DASH_HITS = new ConcurrentHashMap<>();

	private record Twister(ResourceKey<Level> dim, Vec3 at) {
	}

	private Xlr8Kit() {
	}

	private static SoundEvent sound(String path) {
		ResourceLocation id = ProjectHeroMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void register() {
		Aliens.register(new Xlr8Kit());
	}

	public static void clearSessionState() {
		TWISTERS.clear();
		DASH_HITS.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(22f).speed(1.9f).scale(0.95f).armor(2f, 0f).attack(1f).step(0.65f).jump(0.06f).safeFall(6f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(FLURRY, AbilitySlot.SLOT_1, 50), AlienMove.shift(TORNADO, AbilitySlot.SLOT_1, 300),
				AlienMove.tap(PUNCH, AbilitySlot.SLOT_2, 80), AlienMove.shift(SNATCH, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(DASH, AbilitySlot.SLOT_3, 40), AlienMove.shift(WALLRUN, AbilitySlot.SLOT_3, 10),
				AlienMove.tap(WHIRL, AbilitySlot.SLOT_4, 200), AlienMove.shift(KICKS, AbilitySlot.SLOT_4, 140),
				AlienMove.tap(BURST, AbilitySlot.SLOT_6, 600), AlienMove.shift(MINE, AbilitySlot.SLOT_6, 300),
				AlienMove.vanilla(JAB, "Attack", 0), AlienMove.vanilla(SWEEP, "Use (empty hand)", 60));
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		ServerLevel l = level(p);
		OmnitrixSounds.play(l, p.position(), SND_VISOR, 1.0f, 1.0f);
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, COLOUR).size(3f).thickness(0.4f).life(12).ground().spawn();
		PowerToggles.modifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 2.0, AttributeModifier.Operation.ADD_VALUE);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		setFlag(p, F_WALLRUN | F_TORNADO | F_DASH, false);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
		TWISTERS.remove(p.getUUID());
		DASH_HITS.remove(p.getUUID());
	}

	/** Wall Run on: pressed into a wall (and not sneaking) climbs it like a ladder. */
	@Override
	public boolean climbs(Player p) {
		return flag(p, F_WALLRUN) && p.horizontalCollision && !p.isShiftKeyDown();
	}

	/** Wall Run on: water holds you up (Shift to sink in). */
	@Override
	public boolean standsOnFluid(Player p, FluidState fluid) {
		return flag(p, F_WALLRUN) && fluid.is(FluidTags.WATER) && !p.isShiftKeyDown() && !p.isUnderWater();
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		ServerLevel l = level(p);
		// sprinting: faster still
		boolean sprint = p.isSprinting();
		var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		boolean has = speed != null && speed.getModifier(SPRINT_ID) != null;
		if (sprint && !has) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID, SPRINT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else if (!sprint && has) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		}
		if (now % 20 == 0) {
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 2.0, AttributeModifier.Operation.ADD_VALUE);
		}
		if (sprint && now % 2 == 0) {
			l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.15, 0.02, 0.15, 0.0);
		}
		if (flag(p, F_WALLRUN)) {
			p.resetFallDistance();
			if (p.horizontalCollision && now % 3 == 0) {
				l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
			}
		}
		tickFlurry(p, l, now);
		tickTornado(p, l, now);
		tickDash(p, l, now);
		tickKicks(p, l, now);
	}

	private void tickFlurry(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "flurry_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "flurry_end", 0);
			return;
		}
		if ((end - now) % 2 == 0) {
			boolean any = false;
			for (LivingEntity e : cone(p, 3.8, 70)) {
				e.invulnerableTime = 0;
				hurt(p, e, FLURRY_HIT, false);
				shove(e, p.position(), 0.08, 0.02);
				l.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.25, 0.25, 0.25, 0.3);
				any = true;
			}
			OmnitrixSounds.play(l, p.position(), SND_HIT, any ? 0.7f : 0.35f, 1.2f + p.getRandom().nextFloat() * 0.3f);
		}
	}

	private void tickTornado(ServerPlayer p, ServerLevel l, long now) {
		Twister t = TWISTERS.get(p.getUUID());
		if (t == null) {
			return;
		}
		long end = timer(p, "tornado_end");
		if (now >= end || t.dim() != l.dimension()) {
			TWISTERS.remove(p.getUUID());
			setTimer(p, "tornado_end", 0);
			setFlag(p, F_TORNADO, false);
			for (LivingEntity e : around(p, t.at(), TORNADO_RADIUS + 1)) {
				e.invulnerableTime = 0;
				hurt(p, e, TORNADO_END, false);
				shove(e, t.at(), 1.1, 0.5);
			}
			QuirkFx.ring(l, t.at().add(0, 0.1, 0)).colours(0xFFFFFF, GLOW).size((float) TORNADO_RADIUS + 2).thickness(0.6f).life(14).ground().spawn();
			return;
		}
		int k = (int) (end - now);
		// XLR8 runs rings round the eye (if there is room)
		double a = k * 0.9;
		Vec3 ring = t.at().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
		if (l.noCollision(p, p.getBoundingBox().move(ring.subtract(p.position())))) {
			p.teleportTo(ring.x, ring.y, ring.z);
		}
		p.resetFallDistance();
		for (LivingEntity e : around(p, t.at(), TORNADO_RADIUS)) {
			Vec3 to = t.at().subtract(e.position());
			Vec3 in = new Vec3(to.x, 0, to.z);
			if (in.lengthSqr() < 1.0e-4) {
				in = new Vec3(1, 0, 0);
			}
			in = in.normalize();
			Vec3 tangent = new Vec3(-in.z, 0, in.x);
			double res = 1.0 - Math.min(1.0, e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			double up = e.getY() > t.at().y + 5 ? -0.02 : 0.16;
			e.setDeltaMovement(tangent.scale(0.45 * res).add(in.scale(0.12 * res)).add(0, up * Math.max(0.3, res), 0));
			e.hurtMarked = true;
			e.resetFallDistance();
			if (k % 10 == 0) {
				e.invulnerableTime = 0;
				hurt(p, e, TORNADO_HIT, false);
			}
		}
		// the twister: a spiral of cloud
		for (int i = 0; i < 6; i++) {
			double h = i * 0.9;
			double aa = a * 1.4 + i * 1.1;
			double r = 0.6 + h * 0.35;
			l.sendParticles(ParticleTypes.CLOUD, t.at().x + Math.cos(aa) * r, t.at().y + h, t.at().z + Math.sin(aa) * r, 1, 0, 0.02, 0, 0.0);
		}
		if (k % 20 == 0) {
			OmnitrixSounds.play(l, t.at(), SND_TORNADO, 0.8f, 1.1f);
		}
	}

	private void tickDash(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "dash_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "dash_end", 0);
			setFlag(p, F_DASH, false);
			DASH_HITS.remove(p.getUUID());
			return;
		}
		Set<Integer> hits = DASH_HITS.computeIfAbsent(p.getUUID(), x -> new HashSet<>());
		for (LivingEntity e : around(p, p.position().add(0, 1, 0), 1.8)) {
			if (hits.add(e.getId())) {
				hurt(p, e, DASH_DAMAGE, false);
				shove(e, p.position(), 0.5, 0.2);
			}
		}
		l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
	}

	private void tickKicks(ServerPlayer p, ServerLevel l, long now) {
		long end = timer(p, "kicks_end");
		if (end <= 0) {
			return;
		}
		if (now >= end) {
			setTimer(p, "kicks_end", 0);
			return;
		}
		long left = end - now;
		if (left % 2 == 0) {
			boolean last = left <= 2;
			boolean any = false;
			for (LivingEntity e : cone(p, 3.6, 55)) {
				e.invulnerableTime = 0;
				hurt(p, e, KICK_HIT, false);
				shove(e, p.position(), last ? 1.4 : 0.05, last ? 0.5 : 0.02);
				l.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.4, e.getZ(), 1, 0, 0, 0, 0);
				any = true;
			}
			OmnitrixSounds.play(l, p.position(), SND_HIT, any ? 0.6f : 0.3f, 0.9f + (left % 4) * 0.1f);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					tornadoRun(p);
				} else {
					dashFlurry(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					snatch(p, lookTarget(p, 10));
				} else {
					sonicPunch(p, lookTarget(p, 14));
				}
			}
			case SLOT_3 -> {
				if (shift) {
					toggleWallRun(p);
				} else {
					blurDash(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					kickBarrage(p);
				} else {
					whirlwind(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					speedMine(p);
				} else {
					timeBurst(p);
				}
			}
			default -> {
			}
		}
	}

	/** Living creatures in a cone of {@code deg} degrees, {@code range} blocks in front. */
	public static List<LivingEntity> cone(ServerPlayer p, double range, double deg) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		double cos = Math.cos(Math.toRadians(deg / 2));
		for (LivingEntity e : around(p, eye.add(look.scale(range / 2)), range)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d <= range + e.getBbWidth() * 0.5 && (d < 1.0 || to.normalize().dot(look) >= cos)) {
				out.add(e);
			}
		}
		return out;
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 h = new Vec3(l.x, 0, l.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	public boolean dashFlurry(ServerPlayer p) {
		if (!use(p, FLURRY)) {
			return false;
		}
		anim(p, ANIM_FLURRY);
		Vec3 f = flatLook(p);
		p.setDeltaMovement(f.scale(0.9).add(0, 0.05, 0));
		p.hurtMarked = true;
		setTimer(p, "flurry_end", p.level().getGameTime() + FLURRY_TICKS);
		ServerLevel l = level(p);
		QuirkFx.cone(l, p.getEyePosition().add(0, -0.4, 0), p.getLookAngle()).colours(0xFFFFFF, GLOW).size(1.4f).length(3.6f).life(FLURRY_TICKS)
				.style(QuirkFx.CONE_DEBRIS).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.8f, 1.4f);
		return true;
	}

	public boolean tornadoRun(ServerPlayer p) {
		if (!use(p, TORNADO)) {
			return false;
		}
		anim(p, ANIM_TORNADO);
		long now = p.level().getGameTime();
		TWISTERS.put(p.getUUID(), new Twister(p.level().dimension(), p.position()));
		setTimer(p, "tornado_end", now + TORNADO_TICKS);
		setFlag(p, F_TORNADO, true);
		ServerLevel l = level(p);
		for (int i = 0; i < 3; i++) {
			QuirkFx.ring(l, p.position().add(0, 0.4 + i * 1.6, 0)).colours(0xFFFFFF, GLOW).size(2.5f + i).thickness(0.35f).life(TORNADO_TICKS)
					.style(QuirkFx.RING_SOFT).spawn();
		}
		OmnitrixSounds.play(l, p.position(), SND_TORNADO, 1.2f, 0.9f);
		return true;
	}

	public boolean sonicPunch(ServerPlayer p, LivingEntity target) {
		if (target == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.no_target").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, PUNCH)) {
			return false;
		}
		anim(p, ANIM_PUNCH);
		ServerLevel l = level(p);
		Vec3 from = p.position();
		Vec3 dir = target.position().subtract(from);
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		flat = flat.lengthSqr() < 1.0e-4 ? flatLook(p) : flat.normalize();
		Vec3 to = target.position().subtract(flat.scale(target.getBbWidth() * 0.5 + 0.9));
		if (from.distanceTo(to) > 1.0 && l.noCollision(p, p.getBoundingBox().move(to.subtract(from)))) {
			p.teleportTo(to.x, to.y, to.z);
		}
		QuirkFx.ribbon(l, from.add(0, 1, 0), p.position().add(0, 1, 0)).colours(0xFFFFFF, GLOW).size(0.6f).life(10).style(QuirkFx.RIBBON_LIGHT).spawn();
		target.invulnerableTime = 0;
		hurt(p, target, PUNCH_DAMAGE, false);
		shove(target, p.position(), 1.4, 0.4);
		l.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
		OmnitrixSounds.play(l, p.position(), SND_DASH, 1.0f, 1.0f);
		OmnitrixSounds.play(l, target.position(), SND_HIT, 1.0f, 0.7f);
		return true;
	}

	/** Shift+G: the item in a mob's hand ends up in yours. */
	public boolean snatch(ServerPlayer p, LivingEntity target) {
		if (!(target instanceof Mob mob) || mob.getMainHandItem().isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.xlr8.nothing_to_snatch").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, SNATCH)) {
			return false;
		}
		anim(p, ANIM_SNATCH);
		ItemStack item = mob.getMainHandItem().copy();
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
		if (!p.getInventory().add(item)) {
			p.drop(item, false);
		}
		target.invulnerableTime = 0;
		hurt(p, target, 2f, false);
		ServerLevel l = level(p);
		QuirkFx.ribbon(l, p.position().add(0, 1, 0), target.position().add(0, target.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, GLOW).size(0.3f).life(8)
				.style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.7f, 1.6f);
		return true;
	}

	public boolean blurDash(ServerPlayer p) {
		if (!use(p, DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		Vec3 f = flatLook(p);
		p.setDeltaMovement(f.scale(2.4).add(0, Math.max(0.05, p.getLookAngle().y * 0.6), 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, "dash_end", p.level().getGameTime() + 8);
		setFlag(p, F_DASH, true);
		DASH_HITS.put(p.getUUID(), new HashSet<>());
		ServerLevel l = level(p);
		QuirkFx.trail(l, p).colours(0xFFFFFF, COLOUR).size(0.45f).size2(10f).life(18).vec(new Vec3(0, 0.9, 0)).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 1.0f, 1.0f);
		return true;
	}

	public boolean toggleWallRun(ServerPlayer p) {
		if (!use(p, WALLRUN)) {
			return false;
		}
		boolean on = !flag(p, F_WALLRUN);
		setFlag(p, F_WALLRUN, on);
		anim(p, ANIM_WALLRUN);
		p.displayClientMessage(Component.translatable(on ? "message.projecthero.omnitrix.xlr8.wall_run_on" : "message.projecthero.omnitrix.xlr8.wall_run_off")
				.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
		OmnitrixSounds.play(level(p), p.position(), SND_VISOR, 0.6f, on ? 1.4f : 0.9f);
		return true;
	}

	public boolean whirlwind(ServerPlayer p) {
		if (!use(p, WHIRL)) {
			return false;
		}
		anim(p, ANIM_WHIRL);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1, 0);
		p.clearFire();
		for (LivingEntity e : around(p, c, WHIRL_RADIUS)) {
			e.invulnerableTime = 0;
			hurt(p, e, WHIRL_DAMAGE, false);
			shove(e, c, 1.6, 0.7);
			e.clearFire();
		}
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(5), pr -> pr.getOwner() != p)) {
			Vec3 away = pr.position().subtract(c).normalize();
			pr.setDeltaMovement(away.scale(Math.max(0.6, pr.getDeltaMovement().length())));
			pr.hurtMarked = true;
		}
		for (int i = 0; i < 2; i++) {
			QuirkFx.ring(l, p.position().add(0, 0.2 + i * 1.2, 0)).colours(0xFFFFFF, GLOW).size((float) WHIRL_RADIUS).thickness(0.5f).life(12)
					.style(QuirkFx.RING_SOFT).spawn();
		}
		for (int k = 0; k < 24; k++) {
			double a = k * Math.PI / 12;
			l.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * 1.2, c.y - 0.6 + (k % 4) * 0.5, c.z + Math.sin(a) * 1.2, 0, Math.cos(a + 1.4), 0.1,
					Math.sin(a + 1.4), 0.6);
		}
		OmnitrixSounds.play(l, c, SND_TORNADO, 1.0f, 1.4f);
		QuirkCamera.shakeAround(l, c, 10, 0.2f, 6);
		return true;
	}

	public boolean kickBarrage(ServerPlayer p) {
		if (!use(p, KICKS)) {
			return false;
		}
		anim(p, ANIM_KICKS);
		setTimer(p, "kicks_end", p.level().getGameTime() + KICK_TICKS);
		OmnitrixSounds.play(level(p), p.position(), SND_DASH, 0.6f, 1.6f);
		return true;
	}

	/** C: everything near slows to a crawl (bosses less, and their threat table hears of it). */
	public boolean timeBurst(ServerPlayer p) {
		if (!use(p, BURST)) {
			return false;
		}
		anim(p, ANIM_BURST);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1, 0);
		for (LivingEntity e : around(p, c, BURST_RADIUS)) {
			boolean boss = QuirkThreat.add(e, p, 8f) || e.getMaxHealth() >= 150f;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BURST_TICKS, boss ? 2 : 5));
			e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, BURST_TICKS, boss ? 1 : 3));
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BURST_TICKS, boss ? 0 : 1));
			if (!boss) {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.1));
				e.hurtMarked = true;
			}
			l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
		}
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(BURST_RADIUS), pr -> pr.getOwner() != p)) {
			pr.setDeltaMovement(pr.getDeltaMovement().scale(0.08));
			pr.hurtMarked = true;
		}
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).colours(0xFFFFFF, GLOW).size((float) BURST_RADIUS).thickness(0.3f).life(20).ground().spawn();
		QuirkFx.blast(l, c).colours(0xFFFFFF, GLOW).size(2.0f).life(12).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		QuirkCamera.vignette(p, GLOW, 0.5f, 40);
		OmnitrixSounds.play(l, c, SND_BURST, 1.2f, 1.0f);
		return true;
	}

	/** Shift+C: a 1x2 tunnel 16 blocks ahead, mined in a blink; the drops land in your pockets and you shoot down it. */
	public int speedMine(ServerPlayer p) {
		ServerLevel l = level(p);
		if (!p.mayBuild()) {
			return 0;
		}
		if (!use(p, MINE)) {
			return 0;
		}
		anim(p, ANIM_MINE);
		Direction dir = Direction.fromYRot(p.getYRot());
		BlockPos start = p.blockPosition();
		ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
		int mined = 0;
		int reach = 0;
		for (int i = 1; i <= MINE_LENGTH; i++) {
			boolean stop = false;
			for (int up = 0; up <= 1; up++) {
				BlockPos pos = start.relative(dir, i).above(up);
				BlockState s = l.getBlockState(pos);
				if (s.isAir() || !s.getFluidState().isEmpty() && s.getCollisionShape(l, pos).isEmpty()) {
					continue;
				}
				float hard = s.getDestroySpeed(l, pos);
				if (hard < 0f || hard >= 50f || l.getBlockEntity(pos) != null || s.is(Blocks.BEDROCK) || !l.mayInteract(p, pos)) {
					stop = true;
					break;
				}
				for (ItemStack drop : Block.getDrops(s, l, pos, null, p, tool)) {
					if (!p.getInventory().add(drop)) {
						p.drop(drop, false);
					}
				}
				l.destroyBlock(pos, false, p);
				mined++;
			}
			if (stop) {
				break;
			}
			reach = i;
		}
		Vec3 f = Vec3.atLowerCornerOf(dir.getNormal());
		p.setDeltaMovement(f.scale(Math.min(2.2, 0.4 + reach * 0.12)));
		p.hurtMarked = true;
		QuirkFx.trail(l, p).colours(0xFFFFFF, COLOUR).size(0.4f).size2(10f).life(16).vec(new Vec3(0, 0.9, 0)).style(QuirkFx.RIBBON_LIGHT).spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 1.0f, 0.8f);
		OmnitrixSounds.play(l, p.position(), SND_HIT, 0.8f, 0.6f);
		return mined;
	}

	// ---------------------------------------------------------------- vanilla keys

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (target instanceof LivingEntity le && ready(p, JAB)) {
			anim(p, ANIM_JAB);
			le.invulnerableTime = 0;
			hurt(p, le, JAB_EXTRA, false);
			le.invulnerableTime = 0; // the fist itself still lands this tick
			level(p).sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 5, 0.2, 0.3, 0.2, 0.3);
		}
		return false;
	}

	@Override
	public InteractionResult onUse(ServerPlayer p, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		return itemSweep(p) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Use (empty hand): every dropped item and XP orb within 14 blocks is at your feet in a blink. */
	public int itemSweep(ServerPlayer p) {
		ServerLevel l = level(p);
		List<ItemEntity> items = l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(14), ItemEntity::isAlive);
		List<ExperienceOrb> orbs = l.getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(14), ExperienceOrb::isAlive);
		if (items.isEmpty() && orbs.isEmpty()) {
			return 0;
		}
		if (!use(p, SWEEP)) {
			return 0;
		}
		anim(p, ANIM_SWEEP);
		for (ItemEntity it : items) {
			l.sendParticles(ParticleTypes.CLOUD, it.getX(), it.getY() + 0.2, it.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
			it.teleportTo(p.getX(), p.getY() + 0.2, p.getZ());
			it.setNoPickUpDelay();
		}
		for (ExperienceOrb o : orbs) {
			o.teleportTo(p.getX(), p.getY() + 0.2, p.getZ());
		}
		QuirkFx.ring(l, p.position().add(0, 0.05, 0)).colours(0xFFFFFF, GLOW).size(3f).thickness(0.3f).life(10).ground().spawn();
		OmnitrixSounds.play(l, p.position(), SND_DASH, 0.7f, 1.8f);
		return items.size() + orbs.size();
	}
}
