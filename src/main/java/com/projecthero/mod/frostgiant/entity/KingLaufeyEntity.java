package com.projecthero.mod.frostgiant.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAnims.Clip;
import com.projecthero.mod.frostgiant.FrostGiantAssault;
import com.projecthero.mod.frostgiant.FrostGiantConfig;
import com.projecthero.mod.frostgiant.FrostGiantDialogue;
import com.projecthero.mod.frostgiant.FrostGiantEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>King Laufey</b> of Jotunheim -- 14 blocks tall, gaunt and regal, an ice crown fused to his skull, a beard of
 * icicles, the Casket of Ancient Winters in his left hand and an ice spear-staff in his right. 2,500 health + 600 per
 * extra fighter (cap 6,000), 14 armour / 8 toughness, his own boss bar, the shared {@link BossThreat} table.
 *
 * <ul>
 *   <li><b>Phase 1 -- The King's Court</b> (100-66%): Spear of Jotunheim (three spears in a fan), Royal Guard (two Jotun
 *       Raiders from the rift, not while 4+ giants stand), Frozen Grasp (the top-threat fighter is ringed, then encased in
 *       an ice prison -- a team mechanic, softened solo).</li>
 *   <li><b>Phase 2 -- The Casket Opens</b> (66-33%): the blizzard doubles, every brazier loses 30%, the sky darkens.
 *       Winter's Breath (a 3 s cone from the Casket that freezes water and puts out braziers -- hide behind ice), Ice
 *       Pillars (six columns that burst up and block line of sight for 20 s), Avalanche Slam (a 10-block shockwave).</li>
 *   <li><b>Phase 3 -- Ymir's Wrath</b> (33-0%): Ymir reaches through the rift (the assault drives him), Laufey's cooldowns
 *       x0.6, and the Casket floats above him ({@link IceCasketEntity}). <b>The Casket can only be hurt while he is
 *       staggered</b> -- 5 s, from lightning, a Frozen Grasp prison broken in under 1.5 s, or 15% of his health lost in
 *       6 s. Break it and he is drained (x2 damage, no more summons). Until then he refuses to fall: at 1 health he kneels,
 *       invulnerable, and the Casket stays exposed.</li>
 *   <li><b>Death</b>: a 4 s collapse -- he kneels, the crown cracks, ice creeps up from his feet and he shatters.</li>
 * </ul>
 */
public class KingLaufeyEntity extends FrostGiantEntity {
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(KingLaufeyEntity.class, EntityDataSerializers.BYTE);
	public static final byte FLAG_CASKET_FLOATING = 1, FLAG_STAGGERED = 2, FLAG_DRAINED = 4, FLAG_HOLDING = 8;

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.king_laufey"),
			BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
	private final BossThreat threat = new BossThreat(this).range(80).filter(e -> e instanceof Player && FrostCombat.canTarget(e)).holdWhile(this::busy);

	private int phase = 1;
	private boolean casketOpened;
	private boolean drained;
	private boolean holding;
	private boolean lastHurtBypasses;
	private int staggerTicks;
	private int staggerGrace;
	private UUID casketId;
	private long lastLine = -10000;
	private boolean introduced;
	private Clip lastMove;
	private final Deque<float[]> recentDamage = new ArrayDeque<>();
	// move state
	private Vec3 lockedDir = Vec3.ZERO;
	private UUID graspVictim;
	private Vec3 graspAt;
	private final List<BlockPos> pillarSpots = new ArrayList<>();

	public KingLaufeyEntity(EntityType<? extends KingLaufeyEntity> type, Level level) {
		super(type, level);
		this.xpReward = 500;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		FrostGiantConfig.Laufey cfg = FrostGiantConfig.laufey();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
				.add(Attributes.ATTACK_DAMAGE, cfg.slamDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 80.0)
				.add(Attributes.STEP_HEIGHT, 3.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLAGS, (byte) 0);
	}

	public void configure(int fighters) {
		double hp = FrostGiantConfig.laufeyHealthFor(fighters);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
	}

	public BossThreat threat() {
		return threat;
	}

	@Override
	public String model() {
		return FrostGiantAnims.LAUFEY;
	}

	@Override
	protected Clip idleClip() {
		return FrostGiantAnims.LAUFEY_IDLE;
	}

	@Override
	protected Clip walkClip() {
		return FrostGiantAnims.LAUFEY_WALK;
	}

	@Override
	protected Clip runClip() {
		return FrostGiantAnims.LAUFEY_RUN;
	}

	@Override
	protected Clip deathClip() {
		return FrostGiantAnims.LAUFEY_DEATH;
	}

	@Override
	protected Clip hurtClip() {
		return null;
	}

	@Override
	protected double runAbove() {
		return 0.3;
	}

	// ---------------------------------------------------------------- state

	private boolean flag(byte f) {
		return (entityData.get(DATA_FLAGS) & f) != 0;
	}

	private void setFlag(byte f, boolean on) {
		byte b = entityData.get(DATA_FLAGS);
		byte nb = (byte) (on ? b | f : b & ~f);
		if (nb != b) {
			entityData.set(DATA_FLAGS, nb);
		}
	}

	public int phase() {
		return phase;
	}

	public boolean isStaggered() {
		return level().isClientSide() ? flag(FLAG_STAGGERED) : staggerTicks > 0;
	}

	public boolean isDrained() {
		return level().isClientSide() ? flag(FLAG_DRAINED) : drained;
	}

	public boolean isHoldingAtOne() {
		return level().isClientSide() ? flag(FLAG_HOLDING) : holding;
	}

	/** The Casket floats above him (phase 3): the held one in his hand is hidden. */
	public boolean casketFloating() {
		return flag(FLAG_CASKET_FLOATING);
	}

	/** Can the floating Casket be hurt right now? */
	public boolean exposesCasket() {
		return staggerTicks > 0 || holding;
	}

	public boolean casketOpened() {
		return casketOpened;
	}

	public UUID casketId() {
		return casketId;
	}

	private FrostGiantAssault myAssault() {
		return assault();
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (staggerGrace > 0) {
			staggerGrace--;
		}
		if (phase >= 2 && !casketOpened && move != FrostGiantAnims.LAUFEY_CASKET_OPEN) {
			// the opening clip was cut short (or he can't move): the Casket opens anyway
			FrostGiantAssault a = myAssault();
			if (a != null) {
				a.onCasketOpened(server);
			}
			casketOpened = true;
		}
		if (phase >= 3 && !drained && tickCount % 20 == 0 && (casketId == null || !(server.getEntity(casketId) instanceof IceCasketEntity))) {
			spawnCasket(server); // reloaded, or it went missing: it floats back
		}
	}

	@Override
	protected LivingEntity chooseTarget(ServerLevel level) {
		LivingEntity hot = threat.tick();
		if (hot != null) {
			return hot;
		}
		LivingEntity current = getTarget();
		if (current != null && FrostCombat.canTarget(current) && current instanceof Player && current.distanceToSqr(this) < 80 * 80) {
			if (tickCount % 100 != 0 || !threat.isEmpty()) {
				return current;
			}
		}
		Player p = FrostCombat.nearestPlayer(level, position(), 72);
		setTarget(p);
		return p;
	}

	@Override
	protected void tickCombat(ServerLevel level, LivingEntity target) {
		FrostGiantConfig.Laufey cfg = FrostGiantConfig.laufey();
		if (!introduced && target != null) {
			introduced = true;
			say(level, "intro", true);
			startMove(FrostGiantAnims.LAUFEY_WAR_CRY, target);
			return;
		}
		if (holding) {
			getNavigation().stop();
			if (tickCount % 10 == 0) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 3, getZ(), 6, 1.2, 1.5, 1.2, 0.02);
			}
			return;
		}
		if (staggerTicks > 0) {
			getNavigation().stop();
			if (tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.7, getZ(), 4, 1.0, 1.5, 1.0, 0.1);
			}
			if (--staggerTicks == 0) {
				staggerGrace = cfg.staggerGraceTicks;
				setFlag(FLAG_STAGGERED, false);
				clearAnim();
				bossBar.setColor(BossEvent.BossBarColor.BLUE);
				cooldown = 20;
			}
			return;
		}
		if (move != null) {
			tickMove(level, target, cfg);
			return;
		}
		if (target == null) {
			getNavigation().stop();
			return;
		}
		double dist = distanceTo(target);
		if (cooldown <= 0) {
			Clip next = pickMove(level, target, dist);
			if (next != null) {
				beginMove(level, next, target);
				return;
			}
		}
		if (dist > 18) {
			approach(target, 1.0);
		} else if (dist < 7) {
			retreatFrom(target, 1.0);
		} else {
			getNavigation().stop();
			getLookControl().setLookAt(target, 20f, 20f);
		}
	}

	private Clip pickMove(ServerLevel level, LivingEntity target, double dist) {
		if (com.projecthero.mod.quirk.erasure.ErasureMobs.specialsHeld(this)) { // v0.16.7: Erased
			return null;
		}
		FrostGiantConfig.Laufey cfg = FrostGiantConfig.laufey();
		List<Clip> options = new ArrayList<>();
		options.add(FrostGiantAnims.LAUFEY_SPEAR_FAN);
		FrostGiantAssault a = myAssault();
		int giants = a != null ? a.giantsAlive(level) : level.getEntitiesOfClass(FrostGiantEntity.class, getBoundingBox().inflate(48),
				g -> g != this && g.isAlive()).size();
		if (!drained && giants < cfg.guardCap) {
			options.add(FrostGiantAnims.LAUFEY_SUMMON);
		}
		LivingEntity top = threat.strongest();
		if ((top instanceof Player || target instanceof Player) && !IcePrisonEntity.isImprisoned(top != null ? top : target)) {
			options.add(FrostGiantAnims.LAUFEY_FROZEN_GRASP);
		}
		if (phase >= 2) {
			options.add(FrostGiantAnims.LAUFEY_WINTERS_BREATH);
			options.add(FrostGiantAnims.LAUFEY_ICE_PILLARS);
			if (dist <= cfg.slamRadius) {
				options.add(FrostGiantAnims.LAUFEY_AVALANCHE_SLAM);
				options.add(FrostGiantAnims.LAUFEY_AVALANCHE_SLAM);
			}
		}
		if (options.size() > 1) {
			options.remove(lastMove);
		}
		return options.get(random.nextInt(options.size()));
	}

	private void beginMove(ServerLevel level, Clip clip, LivingEntity target) {
		lastMove = clip;
		LivingEntity victim = target;
		if (clip == FrostGiantAnims.LAUFEY_FROZEN_GRASP) {
			LivingEntity top = threat.strongest();
			victim = top instanceof Player ? top : target;
			graspVictim = victim.getUUID();
			graspAt = victim.position();
			say(level, "grasp", false);
		} else if (clip == FrostGiantAnims.LAUFEY_SPEAR_FAN) {
			say(level, "spear", false);
		} else if (clip == FrostGiantAnims.LAUFEY_SUMMON) {
			say(level, "guard", false);
		} else if (clip == FrostGiantAnims.LAUFEY_WINTERS_BREATH) {
			say(level, "breath", false);
			lockedDir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
		} else if (clip == FrostGiantAnims.LAUFEY_ICE_PILLARS) {
			say(level, "pillars", false);
			pickPillarSpots(level);
		} else if (clip == FrostGiantAnims.LAUFEY_AVALANCHE_SLAM) {
			say(level, "slam", false);
		}
		startMove(clip, victim);
	}

	private int cooldownTicks() {
		FrostGiantConfig.Laufey cfg = FrostGiantConfig.laufey();
		double c = cfg.attackCooldownTicks * (phase >= 3 && !drained ? cfg.enragedCooldown : 1.0);
		return Math.max(10, (int) c);
	}

	private Vec3 casketPos() {
		Vec3 side = Vec3.directionFromRotation(0, yBodyRot + 90f).scale(getBbWidth() * 0.7);
		return position().add(side).add(Vec3.directionFromRotation(0, yBodyRot).scale(1.5)).add(0, getBbHeight() * 0.42, 0);
	}

	private Vec3 spearHand() {
		Vec3 side = Vec3.directionFromRotation(0, yBodyRot - 90f).scale(getBbWidth() * 0.7);
		return position().add(side).add(0, getBbHeight() * 0.95, 0);
	}

	private void tickMove(ServerLevel level, LivingEntity target, FrostGiantConfig.Laufey cfg) {
		int hit = move.hit();
		if (move != FrostGiantAnims.LAUFEY_WINTERS_BREATH && move != FrostGiantAnims.LAUFEY_CASKET_OPEN && target != null) {
			face(target.position());
		}
		if (move == FrostGiantAnims.LAUFEY_WAR_CRY && moveTick == hit) {
			warCrySound(level);
		} else if (move == FrostGiantAnims.LAUFEY_SPEAR_FAN) {
			for (int f : FrostGiantAnims.LAUFEY_SPEAR_FORM_TICKS) {
				if (moveTick == f) {
					Vec3 h = spearHand().add(0, 1.5 + f * 0.05, 0);
					level.sendParticles(FrostFx.ICE, h.x, h.y, h.z, 10, 0.2, 0.6, 0.2, 0.05);
					level.sendParticles(ParticleTypes.SNOWFLAKE, h.x, h.y, h.z, 6, 0.3, 0.6, 0.3, 0.02);
					level.playSound(null, h.x, h.y, h.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
			}
			if (moveTick == hit && target != null) {
				Vec3 from = spearHand().add(0, 1.0, 0);
				Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from);
				double dist = aim.length();
				for (int i = -1; i <= 1; i++) {
					Vec3 v = aim.normalize().yRot(i * 12f * Mth.DEG_TO_RAD).scale(1.9).add(0, dist * 0.004, 0);
					IceJavelinEntity.throwFrom(level, this, from, v, cfg.spearDamage, cfg.spearFreezeTicks);
				}
			}
		} else if (move == FrostGiantAnims.LAUFEY_SUMMON && moveTick == hit) {
			FrostGiantAssault a = myAssault();
			if (a != null) {
				a.summonRoyalGuard(level, cfg.guardRaiders);
			} else {
				for (int i = 0; i < cfg.guardRaiders; i++) {
					JotunRaiderEntity r = FrostGiantEntityTypes.RAIDER.create(level);
					if (r != null) {
						Vec3 at = position().add(Vec3.directionFromRotation(0, getYRot() + (i == 0 ? 60 : -60)).scale(6));
						r.moveTo(at.x, getY(), at.z, getYRot(), 0f);
						r.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
						level.addFreshEntity(r);
					}
				}
			}
			warCrySound(level);
		} else if (move == FrostGiantAnims.LAUFEY_FROZEN_GRASP) {
			tickGrasp(level, cfg, hit);
		} else if (move == FrostGiantAnims.LAUFEY_CASKET_OPEN) {
			Vec3 c = casketPos();
			if (moveTick % 3 == 0) {
				level.sendParticles(FrostFx.WHITE, c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0.05);
			}
			if (moveTick == hit) {
				FrostFx.forced(level, ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				FrostFx.forced(level, ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 80, 3, 3, 3, 0.3);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.0f, 0.5f);
				FrostGiantAssault a = myAssault();
				if (a != null) {
					a.onCasketOpened(level);
				}
			}
		} else if (move == FrostGiantAnims.LAUFEY_WINTERS_BREATH) {
			tickBreath(level, cfg, hit, target);
		} else if (move == FrostGiantAnims.LAUFEY_ICE_PILLARS) {
			tickPillars(level, cfg, hit);
		} else if (move == FrostGiantAnims.LAUFEY_AVALANCHE_SLAM) {
			if (moveTick < hit && moveTick % 4 == 0) {
				level.sendParticles(FrostFx.FROST, spearHand().x, spearHand().y + 2, spearHand().z, 6, 0.3, 0.6, 0.3, 0.02);
			}
			if (moveTick == hit) {
				Vec3 c = position().add(0, 0.3, 0);
				FrostFx.shake(level, c, 0.9f, 18, 32);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
				for (int r = 2; r <= (int) cfg.slamRadius; r += 2) {
					FrostFx.ring(level, FrostFx.SNOW, c, r, 12 + r * 4, true);
				}
				for (LivingEntity e : FrostCombat.targetsNear(level, position(), cfg.slamRadius + getBbWidth() * 0.5)) {
					if (e instanceof RuneBrazierEntity) {
						continue;
					}
					if (FrostCombat.hit(level, this, e, cfg.slamDamage, 60)) {
						Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
						push = push.lengthSqr() > 1.0e-3 ? push.normalize().scale(0.8) : Vec3.ZERO;
						e.push(push.x, 1.0, push.z);
						e.hurtMarked = true;
					}
				}
			}
		}
		if (move != null && moveTick >= move.length()) {
			if (move == FrostGiantAnims.LAUFEY_CASKET_OPEN || move == FrostGiantAnims.LAUFEY_WAR_CRY) {
				endMove(20);
			} else {
				endMove(cooldownTicks());
			}
		}
	}

	private void tickGrasp(ServerLevel level, FrostGiantConfig.Laufey cfg, int hit) {
		LivingEntity victim = graspVictim != null && level.getEntity(graspVictim) instanceof LivingEntity l ? l : null;
		if (victim == null || !victim.isAlive()) {
			return;
		}
		// the ring follows them for the first second, then locks where it is -- a quick dash gets out
		if (moveTick <= hit - 10) {
			graspAt = victim.position();
		}
		if (moveTick < hit && moveTick % 2 == 0) {
			double r = 2.4 - 1.2 * (moveTick / (double) hit);
			FrostFx.ring(level, FrostFx.FROST, graspAt.add(0, 0.15, 0), r, 20, true);
			FrostFx.ring(level, FrostFx.RUNE, graspAt.add(0, 0.15, 0), 2.6, 12, true);
		}
		if (moveTick == hit) {
			if (victim.distanceToSqr(graspAt.x, victim.getY(), graspAt.z) <= 2.6 * 2.6 && FrostCombat.canTarget(victim)) {
				FrostGiantAssault a = myAssault();
				boolean solo = a == null || a.fighterCount() <= 1;
				IcePrisonEntity.encase(level, victim, IcePrisonEntity.PRISON, solo ? cfg.prisonTicksSolo : cfg.prisonTicks,
						solo ? cfg.prisonHitsSolo : cfg.prisonHits, assaultId(), true);
			} else {
				FrostFx.shatter(level, graspAt.add(0, 1, 0), 0.8);
			}
		}
	}

	private void tickBreath(ServerLevel level, FrostGiantConfig.Laufey cfg, int hit, LivingEntity target) {
		Vec3 c = casketPos();
		if (moveTick < hit) {
			// frost gathering in the lid; the aim settles half a second before it fires
			if (moveTick <= hit - 10 && target != null) {
				Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
				if (d.lengthSqr() > 1.0e-3) {
					lockedDir = d.normalize();
				}
			}
			face(position().add(lockedDir));
			if (moveTick % 2 == 0) {
				level.sendParticles(FrostFx.WHITE, c.x, c.y, c.z, 4, 0.5, 0.5, 0.5, 0.0);
				level.sendParticles(ParticleTypes.SNOWFLAKE, c.x + lockedDir.x * 2, c.y, c.z + lockedDir.z * 2, 3, 0.6, 0.3, 0.6, -0.05);
			}
			if (moveTick == hit - 20) {
				level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 0.5f);
			}
			return;
		}
		face(position().add(lockedDir));
		Vec3 dir = lockedDir.add(0, -0.18, 0).normalize();
		if (moveTick % 2 == 0) {
			// v0.16.7 (perf): one batched payload per viewer, not two forced packets per step
			com.projecthero.mod.network.ParticleBatch batch = new com.projecthero.mod.network.ParticleBatch();
			for (double r = 1.5; r <= cfg.breathRange; r += 1.6) {
				double spread = Math.tan(Math.toRadians(cfg.breathHalfAngle)) * r * 0.6;
				Vec3 p = c.add(dir.scale(r));
				batch.addForced(ParticleTypes.SNOWFLAKE, FrostFx.FORCED_RANGE, p.x, p.y, p.z, 3, spread, spread * 0.5, spread, 0.05);
				batch.addForced(FrostFx.WHITE, FrostFx.FORCED_RANGE, p.x, p.y, p.z, 2, spread, spread * 0.5, spread, 0.02);
			}
			batch.send(level);
		}
		if (moveTick == hit) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 2.0f, 0.4f);
		}
		if ((moveTick - hit) % 5 == 0) {
			for (LivingEntity e : FrostCombat.inCone(level, this, c, dir, cfg.breathRange, cfg.breathHalfAngle)) {
				if (e instanceof RuneBrazierEntity brazier) {
					brazier.goOut(level);
					continue;
				}
				if (FrostCombat.hit(level, this, e, cfg.breathDamage, 0)) {
					FrostCombat.freeze(e, 400);
				}
			}
			freezeWater(level, c, dir, cfg.breathRange);
		}
	}

	/** Water in the breath's path freezes (temporary ice, tracked like the rest). */
	private void freezeWater(ServerLevel level, Vec3 from, Vec3 dir, double range) {
		FrostGiantAssault a = myAssault();
		for (double r = 3; r <= range; r += 2) {
			Vec3 p = from.add(dir.scale(r));
			BlockPos col = BlockPos.containing(p.x, p.y, p.z);
			if (!level.isLoaded(col)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, col.getX(), col.getZ()) - 1;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos w = new BlockPos(col.getX() + dx, top, col.getZ() + dz);
					BlockState st = level.getBlockState(w);
					if (st.is(Blocks.WATER) && st.getFluidState().isSourceOfType(Fluids.WATER)) {
						FrostGiantAssault.placeTempIce(level, a, w, Blocks.ICE.defaultBlockState(), 600);
					}
				}
			}
		}
	}

	private void pickPillarSpots(ServerLevel level) {
		FrostGiantConfig.Laufey cfg = FrostGiantConfig.laufey();
		pillarSpots.clear();
		FrostGiantAssault a = myAssault();
		Vec3 centre = a != null ? Vec3.atBottomCenterOf(a.center()) : position();
		double radius = a != null ? FrostGiantAssault.arena() - 8 : 20;
		List<Player> fighters = FrostCombat.playersNear(level, centre, radius + 8);
		int n = Math.max(1, cfg.pillarCount);
		for (int i = 0; i < n; i++) {
			Vec3 at;
			if (i < fighters.size() && i < n / 2) {
				Player p = fighters.get(i);
				at = p.position().add((random.nextDouble() - 0.5) * 4, 0, (random.nextDouble() - 0.5) * 4);
			} else {
				double ang = random.nextDouble() * Math.PI * 2;
				double r = 6 + random.nextDouble() * Math.max(1, radius - 6);
				at = centre.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
			}
			BlockPos col = BlockPos.containing(at.x, at.y, at.z);
			if (!level.isLoaded(col)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
			pillarSpots.add(new BlockPos(col.getX(), y, col.getZ()));
		}
	}

	private void tickPillars(ServerLevel level, FrostGiantConfig.Laufey cfg, int hit) {
		if (moveTick < hit && moveTick % 3 == 0) {
			for (BlockPos s : pillarSpots) {
				FrostFx.forced(level, FrostFx.SHADOW, s.getX() + 1, s.getY() + 0.1, s.getZ() + 1, 4, 0.7, 0.0, 0.7, 0);
				FrostFx.forced(level, FrostFx.ICE, s.getX() + 1, s.getY() + 0.2, s.getZ() + 1, 2, 0.6, 0.05, 0.6, 0.05);
			}
		}
		if (moveTick != hit) {
			return;
		}
		FrostGiantAssault a = myAssault();
		FrostFx.shake(level, position(), 0.6f, 12, 48);
		for (BlockPos s : pillarSpots) {
			Vec3 mid = new Vec3(s.getX() + 1, s.getY(), s.getZ() + 1);
			for (LivingEntity e : FrostCombat.targetsNear(level, mid.add(0, 1, 0), 1.9)) {
				if (e instanceof RuneBrazierEntity) {
					continue;
				}
				if (FrostCombat.hit(level, this, e, cfg.pillarDamage, 40)) {
					e.push(0, 1.1, 0);
					e.hurtMarked = true;
				}
			}
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					for (int dy = 0; dy < cfg.pillarHeight; dy++) {
						FrostGiantAssault.placeTempIce(level, a, s.offset(dx, dy, dz),
								(dy + dx + dz) % 3 == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState(), cfg.pillarTicks);
					}
				}
			}
			FrostFx.forced(level, FrostFx.ICE, mid.x, mid.y + 2, mid.z, 20, 0.8, 2.0, 0.8, 0.2);
			level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.6f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- phases

	private void checkPhase(ServerLevel level) {
		float f = getHealth() / getMaxHealth();
		if (phase == 1 && f <= 0.66f) {
			phase = 2;
			say(level, "casket_open", true);
			move = null;
			startMove(FrostGiantAnims.LAUFEY_CASKET_OPEN, getTarget());
			bossBar.setName(Component.translatable("event.projecthero.frost_giants.bar.laufey_casket"));
		}
		if (phase == 2 && f <= 0.33f) {
			phase = 3;
			if (!casketOpened) {
				FrostGiantAssault a = myAssault();
				if (a != null) {
					a.onCasketOpened(level);
				}
			}
			casketOpened = true;
			say(level, "ymir", true);
			bossBar.setName(Component.translatable("event.projecthero.frost_giants.bar.laufey_ymir"));
			bossBar.setColor(BossEvent.BossBarColor.PURPLE);
			spawnCasket(level);
			FrostGiantAssault a = myAssault();
			if (a != null) {
				a.onYmirWakes(level);
			}
			if (!holding) {
				move = null;
				startMove(FrostGiantAnims.LAUFEY_WAR_CRY, getTarget());
			}
		}
	}

	/** The assault says the Casket is open (phase 2). */
	public void markCasketOpened() {
		casketOpened = true;
	}

	private void spawnCasket(ServerLevel level) {
		if (drained) {
			return;
		}
		IceCasketEntity casket = FrostGiantEntityTypes.ICE_CASKET.create(level);
		if (casket == null) {
			return;
		}
		FrostGiantAssault a = myAssault();
		int fighters = a == null ? 1 : a.fighterCount();
		casket.moveTo(getX(), getY() + getBbHeight() + IceCasketEntity.FLOAT_ABOVE, getZ(), 0f, 0f);
		casket.configure(getUUID(), assaultId(), FrostGiantConfig.casketHealthFor(fighters));
		if (a != null) {
			a.adopt(casket);
		}
		level.addFreshEntity(casket);
		casketId = casket.getUUID();
		setFlag(FLAG_CASKET_FLOATING, true);
	}

	/**
	 * Staggers him for {@link FrostGiantConfig.Laufey#staggerTicks} if he can be (phase 3, the Casket intact, not already
	 * staggered, not in the grace after one). Returns whether he staggered.
	 */
	public boolean tryStagger(ServerLevel level, String why) {
		FrostGiantConfig.Laufey cfg = FrostGiantConfig.laufey();
		if (phase < 3 || drained || staggerTicks > 0 || staggerGrace > 0 || holding || isDeadOrDying()) {
			return false;
		}
		staggerTicks = cfg.staggerTicks;
		recentDamage.clear();
		move = null;
		moveTarget = null;
		setAnim(FrostGiantAnims.LAUFEY_STAGGER);
		setFlag(FLAG_STAGGERED, true);
		bossBar.setColor(BossEvent.BossBarColor.WHITE);
		say(level, "stagger", true);
		FrostFx.shake(level, position(), 0.5f, 10, 24);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.5f, 0.4f);
		FrostGiantAssault a = myAssault();
		if (a != null) {
			a.broadcastStagger(level, why);
		}
		return true;
	}

	/** The Casket broke: drained -- x2 damage, no more summons; a kneeling Laufey can now fall. */
	public void onCasketBroken(ServerLevel level) {
		drained = true;
		staggerTicks = 0;
		casketId = null;
		setFlag(FLAG_DRAINED, true);
		setFlag(FLAG_STAGGERED, false);
		setFlag(FLAG_CASKET_FLOATING, false);
		bossBar.setColor(BossEvent.BossBarColor.RED);
		bossBar.setName(Component.translatable("event.projecthero.frost_giants.bar.laufey_drained"));
		say(level, "casket_broken", true);
		if (!holding) {
			clearAnim();
			move = null;
			cooldown = 30;
		}
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide() && holding && !drained && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // kneeling, rebuilding -- only the Casket matters now
		}
		lastHurtBypasses = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		try {
			return super.hurt(source, amount);
		} finally {
			lastHurtBypasses = false;
		}
	}

	/** He refuses to fall while the Casket stands: health never drops below 1 (except to /kill-type damage). */
	@Override
	public void setHealth(float health) {
		if (!level().isClientSide() && health < 1.0f && !drained && !lastHurtBypasses && isAlive() && getMaxHealth() > 0) {
			super.setHealth(1.0f);
			if (!holding) {
				holding = true;
				setFlag(FLAG_HOLDING, true);
				move = null;
				moveTarget = null;
				staggerTicks = 0;
				setFlag(FLAG_STAGGERED, false);
				setAnim(FrostGiantAnims.LAUFEY_KNEEL_HOLD);
				bossBar.setColor(BossEvent.BossBarColor.WHITE);
				if (level() instanceof ServerLevel server) {
					say(server, "refuse", true);
					checkPhase(server);
				}
			}
			return;
		}
		super.setHealth(health);
	}

	@Override
	protected float damageTakenMultiplier(DamageSource source) {
		return drained ? (float) FrostGiantConfig.laufey().drainedDamageMultiplier : 1.0f;
	}

	@Override
	protected void onHurt(ServerLevel server, DamageSource source, float amount) {
		threat.record(source, amount);
		checkPhase(server);
		FrostGiantConfig.Laufey cfg = FrostGiantConfig.laufey();
		if (phase >= 3 && !drained) {
			if (FrostCombat.isLightning(source)) {
				tryStagger(server, "lightning");
				return;
			}
			long now = tickCount;
			recentDamage.addLast(new float[] { now, amount });
			float sum = 0f;
			for (var it = recentDamage.iterator(); it.hasNext();) {
				float[] d = it.next();
				if (now - d[0] > cfg.staggerWindowTicks) {
					it.remove();
				} else {
					sum += d[1];
				}
			}
			if (sum >= getMaxHealth() * cfg.staggerBurst) {
				tryStagger(server, "burst");
			}
		}
	}

	// ---------------------------------------------------------------- death

	@Override
	public void die(DamageSource source) {
		super.die(source);
		bossBar.removeAllPlayers();
		if (level() instanceof ServerLevel server) {
			say(server, "death", true);
			holding = false;
			setFlag(FLAG_HOLDING, false);
		}
	}

	@Override
	protected void tickDeath() {
		if (level() instanceof ServerLevel server && !isRemoved()) {
			int t = deathTime + 1;
			int total = FrostGiantAnims.LAUFEY_DEATH.length();
			if (t == 30 || t == 50) {
				Vec3 head = position().add(0, getBbHeight() * 0.92, 0);
				FrostFx.forced(server, FrostFx.ICE, head.x, head.y, head.z, 30, 0.8, 0.4, 0.8, 0.2);
				server.playSound(null, head.x, head.y, head.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f + t * 0.004f);
			}
			if (t % 3 == 0) {
				double h = getBbHeight() * Math.min(1.0, t / (double) total);
				server.sendParticles(FrostFx.WHITE, getX(), getY() + h, getZ(), 8, getBbWidth() * 0.5, 0.3, getBbWidth() * 0.5, 0.01);
			}
			if (t == total) {
				FrostFx.shatter(server, position().add(0, getBbHeight() * 0.5, 0), 5.0);
				FrostFx.shake(server, position(), 1.0f, 20, 48);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4.0f, 0.3f);
			}
		}
		super.tickDeath();
	}

	private void say(ServerLevel level, String key, boolean force) {
		lastLine = FrostGiantDialogue.say(level, this, key, force, lastLine);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("LaufeyPhase", phase);
		tag.putBoolean("LaufeyCasketOpened", casketOpened);
		tag.putBoolean("LaufeyDrained", drained);
		tag.putBoolean("LaufeyHolding", holding);
		tag.putInt("LaufeyStagger", staggerTicks);
		tag.putInt("LaufeyStaggerGrace", staggerGrace);
		tag.putBoolean("LaufeyIntroduced", introduced);
		if (casketId != null) {
			tag.putUUID("LaufeyCasket", casketId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		phase = Math.max(1, tag.getInt("LaufeyPhase"));
		casketOpened = tag.getBoolean("LaufeyCasketOpened");
		drained = tag.getBoolean("LaufeyDrained");
		holding = tag.getBoolean("LaufeyHolding");
		staggerTicks = tag.getInt("LaufeyStagger");
		staggerGrace = tag.getInt("LaufeyStaggerGrace");
		introduced = tag.getBoolean("LaufeyIntroduced");
		casketId = tag.hasUUID("LaufeyCasket") ? tag.getUUID("LaufeyCasket") : null;
		setFlag(FLAG_DRAINED, drained);
		setFlag(FLAG_HOLDING, holding);
		setFlag(FLAG_STAGGERED, staggerTicks > 0);
		setFlag(FLAG_CASKET_FLOATING, phase >= 3 && !drained);
		if (holding) {
			setAnim(FrostGiantAnims.LAUFEY_KNEEL_HOLD);
		} else if (staggerTicks > 0) {
			setAnim(FrostGiantAnims.LAUFEY_STAGGER);
		}
		if (phase >= 2) {
			bossBar.setName(Component.translatable(drained ? "event.projecthero.frost_giants.bar.laufey_drained"
					: phase >= 3 ? "event.projecthero.frost_giants.bar.laufey_ymir" : "event.projecthero.frost_giants.bar.laufey_casket"));
		}
	}

	/** Test hook: force the phase (and its arrival effects) now. */
	public void forcePhase(ServerLevel level, int target) {
		if (target >= 2 && phase < 2) {
			setHealth(getMaxHealth() * 0.6f);
			checkPhase(level);
		}
		if (target >= 3 && phase < 3) {
			setHealth(getMaxHealth() * 0.3f);
			checkPhase(level);
		}
	}
}
