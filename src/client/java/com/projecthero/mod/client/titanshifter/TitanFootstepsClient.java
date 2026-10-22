package com.projecthero.mod.client.titanshifter;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): "the titans should have footstep sound effects". Every foot plant of the walk, run and kneel_crawl
 * clips plays a deep, layered thud, in step with the feet.
 * <p>
 * Each client runs this for every Titan it tracks (tracking range 16 chunks), so everyone nearby hears the steps and
 * nobody hears them twice: the server plays none (it used to thud on a stride distance, out of step with the clips).
 * The clock mirrors {@code TitanFormEntity#mainPredicate}: the clip it picks (walk / run / kneel_crawl), started after
 * the main controller's 4-tick transition; whenever the Titan is drawn, {@link #observe} re-anchors the clock on the
 * clip GeckoLib actually switched to, so the sound stays locked to the visible feet. The contact times are the clips'
 * foot-plant keyframes (scratchpad/titan_gen_v0167/anims.js: walk 1.2 s and run 0.8 s plant the right foot at 0 and the
 * left at half way; kneel_crawl 1.4 s drags at 0 and 0.7).
 * <p>
 * Scale: pitch drops with the Titan's height, volume (and so range, 16 blocks per unit) rises with it, a run is louder
 * than a walk. The Female's steps are lighter and quicker, the Armoured's carry a crystal clank, and the ground's own
 * step sound is layered under each thud at low pitch. Each plant also kicks up the ground's dust and gives nearby
 * players the old footstep tremor ({@link TitanShakeClient}).
 */
public final class TitanFootstepsClient {
	private static final int NONE = 0, WALK = 1, RUN = 2, CRAWL = 3;
	/** Clip lengths (s) and foot-plant times (s), by clip id. */
	private static final float[] LENGTH = { 0.0f, 1.2f, 0.8f, 1.4f };
	private static final float[][] CONTACTS = { {}, { 0.0f, 0.6f }, { 0.0f, 0.4f }, { 0.0f, 0.7f } };
	/** The "main" controller's transition length (ticks): a clip's time 0 is this long after the switch. */
	private static final int TRANSITION = 4;

	private static final class Track {
		int clip = NONE;
		long start;
		double lastT = -1.0;
		String observed;
	}

	private static final Map<TitanFormEntity, Track> TRACKS = new WeakHashMap<>();

	private TitanFootstepsClient() {
	}

	/** The clip the main controller picks (mirrors {@code TitanFormEntity#mainPredicate}). */
	private static int clipOf(TitanFormEntity t) {
		if (t.formState() != TitanFormEntity.FORM_NORMAL) {
			return NONE;
		}
		boolean moving = t.walkAnimation.speed() > 0.04f;
		if (!moving) {
			return NONE;
		}
		if (t.isKneeling()) {
			return CRAWL;
		}
		return t.isRunning() ? RUN : WALK;
	}

	private static int clipOf(String animName) {
		if (animName == null) {
			return NONE;
		}
		if (animName.endsWith(".kneel_crawl")) {
			return CRAWL;
		}
		if (animName.endsWith(".run")) {
			return RUN;
		}
		return animName.endsWith(".walk") ? WALK : NONE;
	}

	/** Called while the Titan is drawn: the main controller's current clip (its name), to re-anchor the clock on it. */
	public static void observe(TitanFormEntity t, String mainAnim) {
		Track tr = TRACKS.computeIfAbsent(t, k -> new Track());
		if (mainAnim == null || mainAnim.equals(tr.observed)) {
			return;
		}
		tr.observed = mainAnim;
		int clip = clipOf(mainAnim);
		if (clip != NONE) {
			// GeckoLib just switched to this clip: its time 0 comes after the transition
			tr.clip = clip;
			tr.start = t.tickCount + TRANSITION;
			tr.lastT = -1.0;
		}
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			TRACKS.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof TitanFormEntity t && t.isAlive()) {
				tick(mc, level, t);
			}
		}
	}

	private static void tick(Minecraft mc, ClientLevel level, TitanFormEntity t) {
		Track tr = TRACKS.computeIfAbsent(t, k -> new Track());
		int clip = clipOf(t);
		if (clip != tr.clip) {
			tr.clip = clip;
			tr.start = t.tickCount + TRANSITION;
			tr.lastT = -1.0;
			tr.observed = null;
		}
		if (clip == NONE) {
			return;
		}
		double now = (t.tickCount - tr.start) / 20.0;
		if (now < 0.0) {
			return;
		}
		double prev = tr.lastT;
		tr.lastT = now;
		float len = LENGTH[clip];
		float[] contacts = CONTACTS[clip];
		for (int i = 0; i < contacts.length; i++) {
			double n = Math.floor((now - contacts[i]) / len);
			double at = contacts[i] + n * len;
			if (at > prev && at <= now && t.onGround() && !t.isInWaterOrBubble()) {
				step(mc, level, t, clip, i % 2 == 0);
			}
		}
	}

	/** One foot plant. {@code right}: the right foot (the clips plant it first). */
	private static void step(Minecraft mc, ClientLevel level, TitanFormEntity t, int clip, boolean right) {
		if (!TitanShifterConfig.effects().footsteps) {
			return;
		}
		TitanType type = t.titanType();
		float h = t.getBbHeight();
		float px = h / 32.0f; // blocks per model pixel
		float yaw = t.yBodyRot;
		Vec3 fwd = Vec3.directionFromRotation(0.0f, yaw);
		Vec3 side = Vec3.directionFromRotation(0.0f, yaw + 90.0f);
		double reach = clip == RUN ? 5.0 : clip == WALK ? 3.5 : 1.0; // how far ahead the planted foot lands (px)
		double lateral = (right ? 2.0 : -2.0) * px;
		double x = t.getX() + fwd.x * reach * px + side.x * lateral;
		double z = t.getZ() + fwd.z * reach * px + side.z * lateral;
		double y = t.getY();
		BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.2, z));
		RandomSource r = level.random;

		// size: a 15-block Titan is the reference; smaller sounds higher and quieter, bigger deeper and louder
		float size = h / 15.0f;
		float pitch = 0.55f / (float) Math.sqrt(Math.max(0.3f, size));
		float volume = 4.4f * (0.8f + 0.2f * size); // x16 blocks of range: ~64 at 12 blocks, ~70 at 15
		if (clip == RUN) {
			volume *= 1.3f;
		} else if (clip == CRAWL) {
			volume *= 0.7f; // a knee dragged, not a heel slammed
		}
		if (type == TitanType.FEMALE_TITAN) {
			pitch *= 1.14f; // lighter and quicker
			volume *= 0.88f;
		}
		pitch *= 0.95f + r.nextFloat() * 0.1f;

		play(level, x, y, z, SoundEvents.WARDEN_STEP, volume, pitch);
		play(level, x, y, z, SoundEvents.IRON_GOLEM_STEP, volume * 0.8f, pitch * 0.75f);
		play(level, x, y, z, SoundEvents.RAVAGER_STEP, volume * 0.6f, pitch * (type == TitanType.FEMALE_TITAN ? 1.2f : 0.9f));
		if (type == TitanType.ARMORED_TITAN) {
			// crystal plates clanking on stone
			play(level, x, y, z, SoundEvents.AMETHYST_BLOCK_HIT, volume * 0.55f, 0.5f + r.nextFloat() * 0.1f);
			play(level, x, y, z, SoundEvents.DEEPSLATE_BRICKS_STEP, volume * 0.5f, 0.55f);
		}
		if (!ground.isAir()) {
			// the ground's own step under the thud, pitched way down
			play(level, x, y, z, ground.getSoundType().getStepSound(), volume * 0.55f, Math.max(0.5f, pitch * 0.9f));
			int n = Math.round((clip == RUN ? 14 : 9) * Math.max(0.5f, size));
			for (int i = 0; i < n; i++) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x + (r.nextDouble() - 0.5) * 2.0 * size,
						y + 0.1, z + (r.nextDouble() - 0.5) * 2.0 * size, (r.nextDouble() - 0.5) * 0.3, 0.15, (r.nextDouble() - 0.5) * 0.3);
			}
		}
		for (int i = 0; i < 3; i++) {
			level.addParticle(ParticleTypes.POOF, x + (r.nextDouble() - 0.5) * 1.4, y + 0.2, z + (r.nextDouble() - 0.5) * 1.4, 0.0, 0.02, 0.0);
		}
		// the footstep tremor (what TitanCombat.shake sent from the server before)
		if (mc.player != null && TitanShifterConfig.effects().cameraShake) {
			double radius = TitanShifterConfig.effects().shakeRadius;
			double d = Math.sqrt(mc.player.distanceToSqr(x, y, z));
			if (d < radius) {
				float power = (clip == RUN ? 1.3f : clip == CRAWL ? 0.6f : 1.0f) * Math.min(1.2f, size);
				TitanShakeClient.accept(0.12f * power * (1.0f - (float) (d / radius)), 6);
			}
		}
	}

	private static void play(ClientLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(x, y, z, sound, SoundSource.HOSTILE, volume, pitch, false);
	}
}
