package com.projecthero.mod.crime;

import java.util.UUID;

import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: a getaway. A crook loads up with the village's goods at the bell square for {@code getaway.lootSeconds}
 * while 1-2 pistol gunmen stand guard, then sprints for the edge of the village with the loot sack. Put him down
 * (anywhere, any time) and the goods spill and the crime is stopped; the gunmen scatter. He gets
 * {@code getaway.escapeRadius} blocks from the bell and he got away. Hitting him while he loads up starts the run early.
 */
public class GetawayCrime extends CrimeEvent {
	private UUID runnerId;
	private boolean running;
	private long runStart;

	public GetawayCrime(UUID id) {
		super(id);
	}

	@Override
	public CrimeType crimeType() {
		return CrimeType.GETAWAY;
	}

	public UUID runnerId() {
		return runnerId;
	}

	public boolean running() {
		return running;
	}

	@Override
	protected int timeLimitTicks() {
		return CrimeConfig.getaway().timeLimitSeconds * 20;
	}

	private Mob runner(ServerLevel level) {
		return runnerId != null && level.getEntity(runnerId) instanceof Mob m && m.isAlive() ? m : null;
	}

	@Override
	protected void begin(ServerLevel level) {
		BlockPos spot = CrimeVillages.spotNear(level, village, 3, 8, level.random);
		BlockPos at = spot == null ? center() : spot;
		Mob runner = spawnCrook(level, SyndicateEntityTypes.THUG, at, CrimeAI.Role.RUNNER, CrimeEvent::giveSack);
		if (runner == null) {
			failCrime(level, "event.projecthero.crime.fizzled");
			return;
		}
		runnerId = runner.getUUID();
		CrimeConfig.Getaway cfg = CrimeConfig.getaway();
		int lo = Math.max(0, Math.min(cfg.minGunmen, cfg.maxGunmen));
		int hi = Math.max(lo, cfg.maxGunmen);
		int n = lo + level.random.nextInt(hi - lo + 1);
		for (int i = 0; i < n; i++) {
			BlockPos g = CrimeVillages.spotNear(level, at, 2, 4, level.random);
			spawnCrook(level, SyndicateEntityTypes.GUNMAN, g == null ? at : g, CrimeAI.Role.GUARD,
					m -> m.setKind(SyndicateGunman.Kind.PISTOL));
		}
	}

	@Override
	protected void tickCrime(ServerLevel level) {
		Mob runner = runner(level);
		if (runner == null) {
			// not dead by our hook (that ends the crime) -- he has gone missing: treat it as a getaway
			failCrime(level, "event.projecthero.crime.getaway.escaped");
			return;
		}
		CrimeConfig.Getaway cfg = CrimeConfig.getaway();
		long now = level.getGameTime();
		if (!running) {
			if (runTicks >= cfg.lootSeconds * 20L || recentlyHitByPlayer(runner)) {
				startRun(level, runner, now);
			} else {
				if (runner.getNavigation().isDone() && runner.distanceToSqr(Vec3.atBottomCenterOf(village)) > 9) {
					runner.getNavigation().moveTo(village.getX() + 0.5, village.getY(), village.getZ() + 0.5, 0.8);
				}
				if (runTicks % 30 == 0) {
					level.playSound(null, runner.getX(), runner.getY(), runner.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
							SoundSource.HOSTILE, 0.9f, 0.8f);
				}
			}
		} else {
			if (horizontalFromVillage(runner.position()) >= cfg.escapeRadius) {
				vanish(level, runner);
				failCrime(level, "event.projecthero.crime.getaway.escaped");
				return;
			}
			runAway(level, runner, cfg.runnerSpeed);
		}
		// the gunmen cover him: anyone without a target keeps up with the runner
		for (Mob m : crooks(level)) {
			if (m == runner) {
				continue;
			}
			LivingEntity t = m.getTarget();
			if ((t == null || !t.isAlive()) && m.distanceToSqr(runner) > 16) {
				m.getNavigation().moveTo(runner, running ? 1.2 : 0.9);
			}
		}
	}

	private void startRun(ServerLevel level, Mob runner, long now) {
		running = true;
		runStart = now;
		runner.getNavigation().stop();
		level.playSound(null, runner.getX(), runner.getY(), runner.getZ(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 1.5f, 1.1f);
		tellNearby(level, Component.translatable("event.projecthero.crime.getaway.run").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
	}

	/** Tests: start the run now. */
	public void runForTest(ServerLevel level) {
		Mob r = runner(level);
		if (r != null) {
			startRun(level, r, level.getGameTime());
		}
	}

	@Override
	protected void onCrookDown(ServerLevel level, Mob crook, ServerPlayer killer) {
		if (crook.getUUID().equals(runnerId)) {
			spillSack(level, crook);
			succeed(level, 1.0);
		}
	}

	@Override
	protected Vec3 scene(ServerLevel level) {
		Mob r = runner(level);
		return r != null ? r.position() : Vec3.atBottomCenterOf(center());
	}

	@Override
	protected Component status(ServerLevel level) {
		CrimeConfig.Getaway cfg = CrimeConfig.getaway();
		if (!running) {
			int left = Math.max(0, (int) ((cfg.lootSeconds * 20L - runTicks) / 20));
			return Component.translatable("event.projecthero.crime.getaway.loading", left).withStyle(ChatFormatting.WHITE);
		}
		Mob r = runner(level);
		int toGo = r == null ? 0 : Math.max(0, Mth.ceil(cfg.escapeRadius - horizontalFromVillage(r.position())));
		return Component.translatable("event.projecthero.crime.getaway.status", toGo).withStyle(ChatFormatting.WHITE);
	}

	@Override
	protected float progress(ServerLevel level) {
		CrimeConfig.Getaway cfg = CrimeConfig.getaway();
		if (!running) {
			return 1f - Mth.clamp(runTicks / (float) Math.max(1, cfg.lootSeconds * 20), 0f, 1f) * 0.25f;
		}
		Mob r = runner(level);
		return r == null ? 0f : Mth.clamp(1f - (float) (horizontalFromVillage(r.position()) / cfg.escapeRadius), 0f, 0.75f);
	}

	@Override
	protected void saveCrime(CompoundTag tag) {
		if (runnerId != null) {
			tag.putUUID("GetawayRunner", runnerId);
		}
		tag.putBoolean("GetawayRunning", running);
		tag.putLong("GetawayRunStart", runStart);
	}

	@Override
	protected void loadCrime(CompoundTag tag) {
		runnerId = tag.hasUUID("GetawayRunner") ? tag.getUUID("GetawayRunner") : null;
		running = tag.getBoolean("GetawayRunning");
		runStart = tag.getLong("GetawayRunStart");
	}
}
