package com.projecthero.mod.hideout.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.7: every hideout mini-boss's <b>second phase</b>, as a component. Below {@link #threshold} of its health the boss
 * enters it once (saved): an announcement, its boss bar turns red, +20% speed, and a new telegraphed attack every
 * {@link #cooldownTicks} while it has a target and is not busy with one of its own moves:
 * <ul>
 * <li>{@link Kind#PLASMA_MINES} (Forge Overseer, "Overclock") -- five mines round the target blink for 1.5 s, then blow:
 * 9 + Slowness II.</li>
 * <li>{@link Kind#FIREBOMB_RUN} (Parademon Commander, "Air Raid") -- six marks in a line through the target ignite one
 * after another: 9 + 4 s of fire.</li>
 * <li>{@link Kind#BIOMASS_ERUPTION} (Hive Mother, "Frenzy") -- a ring under every player within 16 (max 4); 1.25 s later
 * black spikes burst up: 10, a launch and Wither. Entering it also births two infested spiders.</li>
 * <li>{@link Kind#ICE_SPIKES} (Frost Giant Jarl, "Winter's Wrath") -- a line of ice spikes marches from the Jarl to the
 * target: 10 + freeze + Slowness II; plus a blizzard aura (Slowness I and frost within 12 every 2 s).</li>
 * <li>{@link Kind#REQUIEM_RINGS} (Crypt Lord, "Requiem") -- three rings of soul-fire graves round him (3, 6, 9 blocks)
 * erupt one after another, 0.4 s apart: 9 + Wither + Slowness III. Entering it raises three Risen.</li>
 * <li>{@link Kind#CRIMSON_SPIKES} (Brood Spawn of Carnage, "Bloodbath") -- four lines of red spikes burst out from it in a
 * cross, then four more in an X: 9, a launch and Wither. Entering it births three of Carnage's brood.</li>
 * <li>{@link Kind#JUDGEMENT_BLADES} (Oathbound Champion, "Oath Broken") -- three lines of spectral blades fall in a fan
 * toward the target, marching outwards: 10 + soul fire. Entering it calls two Oath-bound Knights to his side.</li>
 * <li>{@link Kind#NONE} (Kingpin's Enforcer, "Untouchable") -- the threshold, announcement and buff only; his Bull Rush
 * runs in his own move system.</li>
 * </ul>
 * The shared threat table is untouched (the boss keeps choosing targets through it). Strikes are transient.
 */
public final class HideoutPhase {
	public enum Kind { NONE, PLASMA_MINES, FIREBOMB_RUN, BIOMASS_ERUPTION, ICE_SPIKES, REQUIEM_RINGS, CRIMSON_SPIKES, JUDGEMENT_BLADES }

	/** One telegraphed spot: goes off at {@code fireAt} (game time). */
	public record Strike(Vec3 pos, long fireAt, float radius, float damage) {
	}

	private static final ResourceLocation SPEED = com.projecthero.mod.ProjectHeroMod.id("hideout_phase2_speed");

	private final Mob boss;
	private final HideoutFaction faction;
	private final Kind kind;
	public final float threshold;
	public final int cooldownTicks;
	private boolean active;
	private int cooldown = 40;
	private final List<Strike> strikes = new ArrayList<>();

	public HideoutPhase(Mob boss, HideoutFaction faction, Kind kind, float threshold, int cooldownTicks) {
		this.boss = boss;
		this.faction = faction;
		this.kind = kind;
		this.threshold = threshold;
		this.cooldownTicks = cooldownTicks;
	}

	public boolean active() {
		return active;
	}

	public List<Strike> strikes() {
		return strikes;
	}

	/** Call after a hit landed: enters the phase once health drops below the threshold. */
	public void hurtCheck(ServerLevel level) {
		if (!active && boss.isAlive() && boss.getHealth() < boss.getMaxHealth() * threshold) {
			enter(level);
		}
	}

	/** Enters phase 2 now (test hook too). */
	public void enter(ServerLevel level) {
		if (active) {
			return;
		}
		active = true;
		cooldown = 30;
		AttributeInstance sp = boss.getAttribute(Attributes.MOVEMENT_SPEED);
		if (sp != null && !sp.hasModifier(SPEED)) {
			sp.addTransientModifier(new AttributeModifier(SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (boss instanceof HideoutBoss hb) {
			hb.hideoutLink().bar().setColor(BossEvent.BossBarColor.RED);
			hb.hideoutLink().bar().setName(Component.translatable("projecthero.hideout.phase2.bar", faction.bossName()));
		}
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.7f);
		level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(), 3, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(dust(faction.colour(), 2.0f), boss.getX(), boss.getY() + 1, boss.getZ(), 60, 1.0, 1.2, 1.0, 0.05);
		Component msg = Component.translatable("projecthero.hideout.phase2." + faction.getSerializedName(), faction.bossName())
				.withStyle(s -> s.withColor(faction.colour()).withBold(true));
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(boss) < 48 * 48)) {
			p.displayClientMessage(msg, false);
		}
		// v0.17.7: the three new bosses call help as they turn
		com.projecthero.mod.hideout.HideoutGuards.Role help = switch (kind) {
			case REQUIEM_RINGS -> com.projecthero.mod.hideout.HideoutGuards.Role.RISEN;
			case CRIMSON_SPIKES -> com.projecthero.mod.hideout.HideoutGuards.Role.BROODLING;
			case JUDGEMENT_BLADES -> com.projecthero.mod.hideout.HideoutGuards.Role.KNIGHT;
			default -> null;
		};
		if (help != null) {
			int n = kind == Kind.JUDGEMENT_BLADES ? 2 : 3;
			for (int i = 0; i < n; i++) {
				BlockPos spot = com.projecthero.mod.hideout.HideoutGuards.findSpot(level, boss.blockPosition(), 2, 6, boss.getRandom());
				if (spot != null) {
					Mob m = com.projecthero.mod.hideout.HideoutGuards.reinforcementOf(level, faction, help, spot, boss.getRandom());
					if (m != null && boss.getTarget() != null) {
						m.setTarget(boss.getTarget());
					}
				}
			}
		}
		if (kind == Kind.BIOMASS_ERUPTION) {
			for (int i = 0; i < 2; i++) {
				BlockPos spot = com.projecthero.mod.hideout.HideoutGuards.findSpot(level, boss.blockPosition(), 2, 5, boss.getRandom());
				if (spot != null) {
					Mob m = com.projecthero.mod.hideout.HideoutGuards.spawn(level, com.projecthero.mod.hideout.HideoutGuards.Role.HOST_SPIDER, spot,
							boss.getRandom().nextFloat() * 360f, 12);
					if (m != null && boss.getTarget() != null) {
						m.setTarget(boss.getTarget());
					}
				}
			}
		}
	}

	/** Once per server tick. {@code busy}: the boss is in one of its own moves (no phase attack starts). */
	public void tick(ServerLevel level, boolean busy) {
		tickStrikes(level);
		if (!active || !boss.isAlive()) {
			return;
		}
		if (kind == Kind.ICE_SPIKES && level.getGameTime() % 40 == 0) {
			blizzard(level);
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		LivingEntity t = boss.getTarget();
		if (busy || kind == Kind.NONE || t == null || !t.isAlive() || t.distanceToSqr(boss) > 24 * 24) {
			return;
		}
		startAttack(level);
	}

	/** Starts the phase attack now (test hook too). Returns how many strikes it placed. */
	public int startAttack(ServerLevel level) {
		cooldown = cooldownTicks;
		LivingEntity t = boss.getTarget();
		Vec3 at = t != null ? t.position() : boss.position().add(boss.getLookAngle().scale(4));
		long now = level.getGameTime();
		int before = strikes.size();
		switch (kind) {
			case PLASMA_MINES -> {
				strikes.add(new Strike(at, now + 30, 2.2f, 9f));
				for (int i = 0; i < 4; i++) {
					double a = i * Math.PI / 2 + boss.getRandom().nextDouble() * 0.6;
					strikes.add(new Strike(ground(level, at.add(Math.cos(a) * 3, 0, Math.sin(a) * 3)), now + 30 + i * 2, 2.2f, 9f));
				}
				level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.8f);
			}
			case FIREBOMB_RUN -> {
				Vec3 dir = at.subtract(boss.position()).multiply(1, 0, 1);
				dir = dir.lengthSqr() < 1.0e-3 ? boss.getLookAngle().multiply(1, 0, 1).normalize() : dir.normalize();
				Vec3 start = at.subtract(dir.scale(5));
				for (int i = 0; i < 6; i++) {
					strikes.add(new Strike(ground(level, start.add(dir.scale(i * 2))), now + 24 + i * 3, 2.0f, 9f));
				}
				level.playSound(null, boss.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.6f);
			}
			case BIOMASS_ERUPTION -> {
				int n = 0;
				for (Player p : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(16), p -> victim(p))) {
					strikes.add(new Strike(ground(level, p.position()), now + 25, 1.8f, 10f));
					if (++n >= 4) {
						break;
					}
				}
				if (n == 0) {
					strikes.add(new Strike(ground(level, at), now + 25, 1.8f, 10f));
				}
				level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.5f, 1.2f);
			}
			case ICE_SPIKES -> {
				Vec3 dir = at.subtract(boss.position()).multiply(1, 0, 1);
				dir = dir.lengthSqr() < 1.0e-3 ? boss.getLookAngle().multiply(1, 0, 1).normalize() : dir.normalize();
				for (int i = 1; i <= 10; i++) {
					strikes.add(new Strike(ground(level, boss.position().add(dir.scale(i * 1.6))), now + 16 + i * 2, 1.4f, 10f));
				}
				level.playSound(null, boss.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
			}
			case REQUIEM_RINGS -> {
				Vec3 c = boss.position();
				int[] counts = { 8, 14, 20 };
				for (int r = 0; r < 3; r++) {
					double radius = 3 * (r + 1);
					double off = boss.getRandom().nextDouble() * Math.PI;
					for (int i = 0; i < counts[r]; i++) {
						double a = off + i * Mth.TWO_PI / counts[r];
						strikes.add(new Strike(ground(level, c.add(Math.cos(a) * radius, 0, Math.sin(a) * radius)), now + 26 + r * 8, 1.3f, 9f));
					}
				}
				level.playSound(null, boss.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0f, 0.5f);
			}
			case CRIMSON_SPIKES -> {
				Vec3 c = boss.position();
				double base = Math.atan2(at.z - c.z, at.x - c.x);
				for (int wave = 0; wave < 2; wave++) {
					for (int arm = 0; arm < 4; arm++) {
						double a = base + arm * Math.PI / 2 + wave * Math.PI / 4;
						for (int i = 1; i <= 7; i++) {
							strikes.add(new Strike(ground(level, c.add(Math.cos(a) * i * 1.5, 0, Math.sin(a) * i * 1.5)), now + 22 + wave * 18 + i * 2, 1.1f, 9f));
						}
					}
				}
				level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.6f, 1.5f);
			}
			case JUDGEMENT_BLADES -> {
				Vec3 c = boss.position();
				double base = Math.atan2(at.z - c.z, at.x - c.x);
				for (int line = -1; line <= 1; line++) {
					double a = base + line * Math.toRadians(22);
					for (int i = 1; i <= 7; i++) {
						strikes.add(new Strike(ground(level, c.add(Math.cos(a) * i * 2.0, 0, Math.sin(a) * i * 2.0)), now + 24 + i * 3, 1.4f, 10f));
					}
				}
				level.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 1.2f, 1.4f);
			}
			default -> {
			}
		}
		return strikes.size() - before;
	}

	private static Vec3 ground(ServerLevel level, Vec3 p) {
		BlockPos b = BlockPos.containing(p);
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos q = b.offset(0, dy, 0);
			if (!level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty() && level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
				return new Vec3(p.x, q.getY(), p.z);
			}
		}
		return p;
	}

	private void tickStrikes(ServerLevel level) {
		if (strikes.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		Iterator<Strike> it = strikes.iterator();
		while (it.hasNext()) {
			Strike s = it.next();
			if (now >= s.fireAt()) {
				detonate(level, s);
				it.remove();
			} else if (now % 4 == 0) {
				telegraph(level, s);
			}
		}
	}

	private ParticleOptions markParticle() {
		return switch (kind) {
			case PLASMA_MINES -> ParticleTypes.ELECTRIC_SPARK;
			case FIREBOMB_RUN -> ParticleTypes.FLAME;
			case BIOMASS_ERUPTION -> ParticleTypes.SQUID_INK;
			case ICE_SPIKES -> ParticleTypes.SNOWFLAKE;
			case REQUIEM_RINGS -> ParticleTypes.SOUL;
			case CRIMSON_SPIKES -> ParticleTypes.CRIMSON_SPORE;
			case JUDGEMENT_BLADES -> ParticleTypes.SOUL_FIRE_FLAME;
			default -> ParticleTypes.CRIT;
		};
	}

	private void telegraph(ServerLevel level, Strike s) {
		ParticleOptions p = markParticle();
		int n = Math.max(8, (int) (s.radius() * 8));
		for (int i = 0; i < n; i++) {
			double a = i * Mth.TWO_PI / n;
			level.sendParticles(p, s.pos().x + Math.cos(a) * s.radius(), s.pos().y + 0.1, s.pos().z + Math.sin(a) * s.radius(), 1, 0, 0.01, 0, 0);
		}
		level.sendParticles(dust(faction.colour(), 1.2f), s.pos().x, s.pos().y + 0.15, s.pos().z, 3, 0.2, 0.0, 0.2, 0);
	}

	private void detonate(ServerLevel level, Strike s) {
		Vec3 c = s.pos();
		switch (kind) {
			case PLASMA_MINES -> {
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
				level.sendParticles(dust(0xFF3020, 1.6f), c.x, c.y + 0.5, c.z, 20, 0.8, 0.4, 0.8, 0.05);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.9f, 1.4f);
			}
			case FIREBOMB_RUN -> {
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.4, c.z, 30, 0.8, 0.5, 0.8, 0.05);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.8, c.z, 6, 0.5, 0.3, 0.5, 0.02);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.8f);
			}
			case BIOMASS_ERUPTION -> {
				for (int i = 0; i < 6; i++) {
					level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + i * 0.4, c.z, 6, 0.3, 0.1, 0.3, 0.02);
				}
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE.defaultBlockState()), c.x, c.y + 0.5, c.z, 20, 0.5, 0.6, 0.5, 0.2);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.8f, 1.6f);
			}
			case ICE_SPIKES -> {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), c.x, c.y + 0.8, c.z, 24, 0.4, 0.8, 0.4, 0.2);
				level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1.0, c.z, 12, 0.4, 0.8, 0.4, 0.05);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f);
			}
			case REQUIEM_RINGS -> {
				for (int i = 0; i < 5; i++) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + i * 0.45, c.z, 5, 0.25, 0.1, 0.25, 0.02);
				}
				level.sendParticles(ParticleTypes.SOUL, c.x, c.y + 1.2, c.z, 3, 0.2, 0.4, 0.2, 0.03);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SOUL_SOIL.defaultBlockState()), c.x, c.y + 0.3, c.z, 10, 0.4, 0.2, 0.4, 0.15);
				if (level.random.nextInt(3) == 0) {
					level.playSound(null, c.x, c.y, c.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.9f, 0.8f);
				}
			}
			case CRIMSON_SPIKES -> {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_CONCRETE.defaultBlockState()), c.x, c.y + 0.8, c.z, 16, 0.3, 0.8, 0.3, 0.2);
				for (int i = 0; i < 4; i++) {
					level.sendParticles(dust(0xC01018, 1.4f), c.x, c.y + i * 0.5, c.z, 4, 0.12, 0.1, 0.12, 0.0);
				}
				if (level.random.nextInt(3) == 0) {
					level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.0f, 0.5f);
				}
			}
			case JUDGEMENT_BLADES -> {
				for (int i = 0; i < 8; i++) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 4.0 - i * 0.5, c.z, 2, 0.05, 0.05, 0.05, 0.0);
				}
				level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.4, c.z, 12, 0.5, 0.2, 0.5, 0.3);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.2, c.z, 14, 0.6, 0.05, 0.6, 0.04);
				if (level.random.nextInt(2) == 0) {
					level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5f, 1.6f);
				}
			}
			default -> {
			}
		}
		AABB area = new AABB(c.x - s.radius(), c.y - 0.5, c.z - s.radius(), c.x + s.radius(), c.y + 2.5, c.z + s.radius());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, this::victim)) {
			if (horizontalDistSq(e.position(), c) > s.radius() * s.radius()) {
				continue;
			}
			e.hurt(boss.damageSources().mobAttack(boss), s.damage());
			switch (kind) {
				case PLASMA_MINES -> e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
				case FIREBOMB_RUN -> e.igniteForSeconds(4f);
				case BIOMASS_ERUPTION -> {
					e.push(0, 0.9, 0);
					e.hurtMarked = true;
					e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
				}
				case ICE_SPIKES -> {
					e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 100, e.getTicksFrozen() + 120));
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
					e.push(0, 0.5, 0);
					e.hurtMarked = true;
				}
				case REQUIEM_RINGS -> {
					e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
				}
				case CRIMSON_SPIKES -> {
					e.push(0, 0.8, 0);
					e.hurtMarked = true;
					e.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0));
				}
				case JUDGEMENT_BLADES -> e.igniteForSeconds(3f);
				default -> {
				}
			}
		}
	}

	private void blizzard(ServerLevel level) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + 3, boss.getZ(), 60, 8, 2, 8, 0.05);
		for (Player p : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(12), this::victim)) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
			p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 40, p.getTicksFrozen() + 50));
		}
	}

	private static double horizontalDistSq(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/** Who a phase attack hurts: not the boss, other bosses, guards, creative / spectator players. */
	boolean victim(LivingEntity e) {
		if (e == boss || !e.isAlive() || e instanceof HideoutBoss) {
			return false;
		}
		if (e instanceof Mob m && AlarmGuards.isGuard(m)) {
			return false;
		}
		return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	private static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	public void save(CompoundTag tag) {
		tag.putBoolean("Phase2", active);
	}

	/** Restores the phase (re-applies the bar colour and speed without the announcement). */
	public void load(CompoundTag tag) {
		if (tag.getBoolean("Phase2") && !active) {
			active = true;
			AttributeInstance sp = boss.getAttribute(Attributes.MOVEMENT_SPEED);
			if (sp != null && !sp.hasModifier(SPEED)) {
				sp.addTransientModifier(new AttributeModifier(SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (boss instanceof HideoutBoss hb) {
				hb.hideoutLink().bar().setColor(BossEvent.BossBarColor.RED);
				hb.hideoutLink().bar().setName(Component.translatable("projecthero.hideout.phase2.bar", faction.bossName()));
			}
		}
	}
}
