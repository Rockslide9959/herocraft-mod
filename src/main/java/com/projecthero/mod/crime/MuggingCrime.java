package com.projecthero.mod.crime;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.syndicate.SyndicateEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: a mugging. 1-3 thugs corner a villager. Each of their hits wears the victim down (a hit counter, not real
 * health: the villager flinches, panics and runs, but the thugs can never kill it). Put every thug down before the
 * counter runs out and the crime is stopped; let it run out and the villager is knocked out -- it can't move for
 * {@code mugging.knockoutSeconds}, then gets up -- and the thugs slip away. Thugs ignore players until one hits them or
 * gets within 6 blocks.
 */
public class MuggingCrime extends CrimeEvent {
	private UUID victimId;
	private int hitsLeft;
	private int hitsMax;

	public MuggingCrime(UUID id) {
		super(id);
	}

	@Override
	public CrimeType crimeType() {
		return CrimeType.MUGGING;
	}

	public void setVictim(Villager victim) {
		this.victimId = victim.getUUID();
	}

	public UUID victimId() {
		return victimId;
	}

	public int hitsLeft() {
		return hitsLeft;
	}

	public int hitsMax() {
		return hitsMax;
	}

	/** Tests: wear the victim down to {@code left} hits. */
	public void setHitsLeftForTest(int left) {
		hitsLeft = left;
	}

	private Villager victim(ServerLevel level) {
		return victimId != null && level.getEntity(victimId) instanceof Villager v && v.isAlive() ? v : null;
	}

	@Override
	protected int timeLimitTicks() {
		return CrimeConfig.mugging().timeLimitSeconds * 20;
	}

	@Override
	protected void begin(ServerLevel level) {
		Villager v = victim(level);
		if (v == null) {
			List<Villager> near = level.getEntitiesOfClass(Villager.class, new AABB(center()).inflate(24.0), Villager::isAlive);
			v = near.isEmpty() ? null : near.get(level.random.nextInt(near.size()));
		}
		if (v == null) {
			failCrime(level, "event.projecthero.crime.fizzled");
			return;
		}
		victimId = v.getUUID();
		v.addTag(CrimeAI.VICTIM_TAG);
		CrimeConfig.Mugging cfg = CrimeConfig.mugging();
		int lo = Math.max(1, Math.min(cfg.minThugs, cfg.maxThugs));
		int hi = Math.max(lo, cfg.maxThugs);
		int n = lo + level.random.nextInt(hi - lo + 1);
		hitsMax = Math.max(1, cfg.victimHits + cfg.victimHitsPerThug * n);
		hitsLeft = hitsMax;
		BlockPos at = v.blockPosition();
		for (int i = 0; i < n; i++) {
			BlockPos spot = CrimeVillages.spotNear(level, at, 2.5, 5.0, level.random);
			Villager target = v;
			spawnCrook(level, SyndicateEntityTypes.THUG, spot == null ? at : spot, CrimeAI.Role.MUGGER, m -> m.setTarget(target));
		}
	}

	@Override
	protected void tickCrime(ServerLevel level) {
		Villager v = victim(level);
		if (v == null) {
			failCrime(level, "event.projecthero.crime.mugging.lost");
			return;
		}
		List<Mob> thugs = crooks(level);
		if (thugs.isEmpty()) {
			succeed(level, 1.0);
			return;
		}
		for (Mob m : thugs) {
			LivingEntity t = m.getTarget();
			if (t == null || !t.isAlive() || !(t instanceof Player) && t != v || t instanceof Player p && (p.isCreative() || p.isSpectator())) {
				m.setTarget(v);
			}
		}
	}

	/**
	 * The damage hook for the victim. A crook's hit is turned into one step of the hit counter (flinch, sound, knockback,
	 * panic) and the real damage is cancelled; anything else hurts as normal. Returns whether the real damage happens.
	 */
	boolean onVictimHurt(ServerLevel level, Villager v, DamageSource source) {
		if (!(source.getEntity() instanceof Mob attacker) || !owns(attacker.getUUID())) {
			return true;
		}
		if (state().finished() || hitsLeft <= 0) {
			return false;
		}
		hitsLeft--;
		level.broadcastDamageEvent(v, source);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.2f, 1.0f + level.random.nextFloat() * 0.2f);
		v.knockback(0.35, attacker.getX() - v.getX(), attacker.getZ() - v.getZ());
		v.getBrain().setMemory(MemoryModuleType.HURT_BY, source);
		v.getBrain().setMemory(MemoryModuleType.HURT_BY_ENTITY, attacker);
		if (hitsLeft <= 0) {
			knockOut(level, v);
		}
		return false;
	}

	private void knockOut(ServerLevel level, Villager v) {
		int ticks = Math.max(20, CrimeConfig.mugging().knockoutSeconds * 20);
		v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false, true));
		v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false, true));
		v.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, false, true));
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getY() + v.getBbHeight() + 0.3, v.getZ(), 4, 0.3, 0.1, 0.3, 0.0);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_DEATH, SoundSource.NEUTRAL, 1.0f, 1.3f);
		failCrime(level, "event.projecthero.crime.mugging.knocked_out");
	}

	@Override
	protected void release(ServerLevel level) {
		Villager v = victim(level);
		if (v != null) {
			v.removeTag(CrimeAI.VICTIM_TAG);
		}
	}

	@Override
	protected Vec3 scene(ServerLevel level) {
		Villager v = victim(level);
		return v != null ? v.position() : Vec3.atBottomCenterOf(center());
	}

	@Override
	protected Component status(ServerLevel level) {
		return Component.translatable("event.projecthero.crime.mugging.status", crooks(level).size(),
				Mth.ceil(100.0 * hitsLeft / Math.max(1, hitsMax))).withStyle(ChatFormatting.WHITE);
	}

	@Override
	protected float progress(ServerLevel level) {
		return hitsMax <= 0 ? 1f : hitsLeft / (float) hitsMax;
	}

	@Override
	protected void onCrookDown(ServerLevel level, Mob crook, ServerPlayer killer) {
		Villager v = victim(level);
		if (v != null && killer != null) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + v.getBbHeight(), v.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		}
	}

	@Override
	protected void saveCrime(CompoundTag tag) {
		if (victimId != null) {
			tag.putUUID("MugVictim", victimId);
		}
		tag.putInt("MugHits", hitsLeft);
		tag.putInt("MugHitsMax", hitsMax);
	}

	@Override
	protected void loadCrime(CompoundTag tag) {
		victimId = tag.hasUUID("MugVictim") ? tag.getUUID("MugVictim") : null;
		hitsLeft = tag.getInt("MugHits");
		hitsMax = tag.getInt("MugHitsMax");
	}
}
