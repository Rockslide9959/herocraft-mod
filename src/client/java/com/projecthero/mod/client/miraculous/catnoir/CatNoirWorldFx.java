package com.projecthero.mod.client.miraculous.catnoir;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.catnoir.CatNoir;
import com.projecthero.mod.miraculous.catnoir.CatNoirConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.6: the long baton, drawn in the world (it reaches far past the player's model): <b>Baton Extend</b> shooting
 * out up to 15 blocks from the hand along the direction it was cast, and the <b>Pole Vault</b> pole from where it is
 * planted up to his hands as it carries him into the air -- both telescoping tube by tube ({@link CatNoirBaton}). Also
 * the CATACLYSM hand's drifting motes (client particles, every viewer; the hand itself is {@link CataclysmFx}).
 */
public final class CatNoirWorldFx {
	private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.04f, 0.02f, 0.06f), 0.45f);
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.16f, 0.34f, 0.08f), 0.35f);

	private CatNoirWorldFx() {
	}

	public static void render(WorldRenderContext ctx) {
		ClientLevel level = ctx.world();
		if (level == null || ctx.consumers() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		for (Player p : level.players()) {
			if (!CatNoir.active(p) || p.isInvisible()) {
				continue;
			}
			MiraculousState s = Miraculous.peek(p);
			if (s == null) {
				continue;
			}
			float age = level.getGameTime() - s.animStart + partial;
			boolean rotted = CatNoir.rotted(p);
			if (s.animId == CatNoir.ANIM_EXTEND && age >= 0f && age < CatNoirPose.length(CatNoir.ANIM_EXTEND)) {
				Vec3 dir = Miraculous.vec(s, CatNoir.VEC_EXTEND_DIR);
				long len100 = s.val(CatNoir.V_EXTEND_LENGTH);
				if (dir != null && len100 > 0L) {
					float reach = len100 / 100f;
					float out = CatNoirConfig.EXTEND_OUT_TICKS, hold = CatNoirConfig.EXTEND_HOLD_TICKS, back = CatNoirConfig.EXTEND_BACK_TICKS;
					float k = age < out ? age / out : age < out + hold ? 1f : 1f - (age - out - hold) / back;
					k = Mth.clamp(k, 0f, 1f);
					Vec3 from = hand(p, partial, dir, p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson());
					float front = CatNoirBaton.COLLAPSED + (reach - CatNoirBaton.COLLAPSED) * k;
					draw(pose, buffers, level, cam, from, dir, front, reach, 0.35f, rotted);
				}
			}
			if (s.animId == CatNoir.ANIM_VAULT && age >= 0f && age < CatNoirPose.length(CatNoir.ANIM_VAULT)) {
				Vec3 base = Miraculous.vec(s, CatNoir.VEC_VAULT_BASE);
				if (base != null) {
					Vec3 at = p.getPosition(partial);
					Vec3 flat = Vec3.directionFromRotation(0f, p.getYRot());
					Vec3 grip = at.add(0, 1.05, 0).add(flat.scale(0.32));
					Vec3 up = grip.subtract(base);
					float l = (float) up.length();
					if (l > 0.2f) {
						// planted at the foot: the front end reaches up to the hands (the grip is held at the top)
						Vec3 dir = up.normalize();
						draw(pose, buffers, level, cam, grip, dir.scale(-1), l, (float) (CatNoirConfig.VAULT_HEIGHT + 2.5), 0.3f, rotted);
					}
				}
			}
		}
	}

	/** Roughly where his main hand is, at the shoulder pushed along {@code dir} (first person: just under the view). */
	private static Vec3 hand(Player p, float partial, Vec3 dir, boolean firstPerson) {
		Vec3 eye = p.getEyePosition(partial);
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		if (p.getMainArm() == HumanoidArm.LEFT) {
			right = right.scale(-1);
		}
		if (firstPerson) {
			return eye.add(right.scale(0.32)).add(0, -0.28, 0).add(dir.scale(0.5));
		}
		return eye.add(right.scale(0.36)).add(0, -0.25, 0).add(dir.scale(0.55));
	}

	/** A baton gripped at {@code grip}, reaching {@code front} along {@code dir} (of at most {@code max}) and {@code back} behind. */
	private static void draw(PoseStack pose, MultiBufferSource buffers, ClientLevel level, Vec3 cam, Vec3 grip, Vec3 dir, float front, float max,
			float back, boolean rotted) {
		pose.pushPose();
		pose.translate(grip.x - cam.x, grip.y - cam.y, grip.z - cam.z);
		Vec3 d = dir.normalize();
		pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) d.x, (float) d.y, (float) d.z));
		int light = LevelRenderer.getLightColor(level, BlockPos.containing(grip.add(d.scale(Math.min(front, 3f) * 0.5))));
		CatNoirBaton.render(pose, buffers, light, front, max, back, back, rotted);
		pose.popPose();
	}

	/** Each client tick: faint green-black motes drift off the charged hands (every viewer). */
	public static void tickParticles(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		ClientLevel level = mc.level;
		for (Player p : level.players()) {
			if (!CatNoir.charged(p) || p.isInvisible()) {
				continue;
			}
			boolean first = p == mc.player && mc.options.getCameraType().isFirstPerson();
			float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
			Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(p.getMainArm() == HumanoidArm.LEFT ? -1 : 1);
			Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			Vec3 hand = first
					? p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(right.scale(0.45)).add(0, -0.35, 0)
					: p.position().add(right.scale(0.36)).add(fwd.scale(0.55)).add(0, 1.55, 0);
			if (first) {
				// his own view: only the odd faint green mote, never black blots over the screen
				if (level.random.nextInt(4) == 0) {
					level.addParticle(GREEN, hand.x, hand.y, hand.z, 0, 0.02, 0);
				}
				continue;
			}
			if (level.random.nextInt(2) == 0) {
				double ox = (level.random.nextDouble() - 0.5) * 0.4, oy = (level.random.nextDouble() - 0.5) * 0.4, oz = (level.random.nextDouble() - 0.5) * 0.4;
				level.addParticle(level.random.nextInt(3) == 0 ? GREEN : BLACK, hand.x + ox, hand.y + oy, hand.z + oz, 0, 0.025, 0);
			}
			if (level.random.nextInt(8) == 0) {
				level.addParticle(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, (level.random.nextDouble() - 0.5) * 0.04, 0.03,
						(level.random.nextDouble() - 0.5) * 0.04);
			}
		}
	}
}
