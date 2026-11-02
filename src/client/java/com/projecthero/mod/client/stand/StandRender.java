package com.projecthero.mod.client.stand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.StarPlatinum;
import com.projecthero.mod.stand.data.StandState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: every summoned Stand in the world -- drawn ONLY on a Stand user's client (a non-Stand user's client never even
 * receives the state; this also checks the local player is a Stand user, so a revoked player stops seeing them at once).
 * No entity: each frame the body is placed from its user's interpolated position and the synced {@link StandState}
 * (animation id + start), like Dark Shadow.
 *
 * <ul>
 *   <li>The body is the vanilla wide player model wearing the Stand's skin (Star Platinum: the user's NovaSkin), about
 *       1.15x a player, with a faint purple glow shell, floating and bobbing behind the right shoulder at rest.</li>
 *   <li>While it fights (any attack, the rush, Guard, the charge, a held thing, the inhale) it moves in front of its user --
 *       in first person far enough out to be seen.</li>
 *   <li>The ORA Rush draws blurred extra arm copies and (setting) floating "ORA" text; a summon throws up ゴゴゴ.</li>
 *   <li>A held block is drawn in its hand; faint violet motes drift off it (client particles: Stand users only).</li>
 * </ul>
 */
public final class StandRender {
	private static final float SCALE = 1.15f * 0.9375f;

	/** Per-Stand smoothing (where it floats, which way it faces). */
	private static final class Smooth {
		Vec3 pos;
		float yaw = Float.NaN;
		long lastNanos;
		long lastMote;
	}

	private static final Map<Integer, Smooth> SMOOTH = new ConcurrentHashMap<>();
	private static PlayerModel<Player> model;

	private StandRender() {
	}

	static void reset() {
		SMOOTH.clear();
		model = null;
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Length of a one-shot animation (ticks). */
	static int animLength(int animId) {
		return switch (animId) {
			case StandState.ANIM_SUMMON -> StandConfig.SUMMON_ANIM_TICKS;
			case StandState.ANIM_PUNCH_L, StandState.ANIM_PUNCH_R -> 8;
			case StandState.ANIM_PUNCH_HEAVY, StandState.ANIM_RUSH_FINISH -> 12;
			case StandState.ANIM_FINGER -> 14;
			case StandState.ANIM_GRAB -> 10;
			case StandState.ANIM_THROW -> 10;
			case StandState.ANIM_FLICK -> 10;
			case StandState.ANIM_LEAP, StandState.ANIM_DASH -> 10;
			case StandState.ANIM_BREAKER -> 16;
			case StandState.ANIM_THE_WORLD -> 30;
			case StandState.ANIM_INHALE -> StandConfig.INHALE_TICKS;
			case StandState.ANIM_UPPERCUT -> 12;
			case StandState.ANIM_DISMISS -> 10;
			case StandState.ANIM_CATCH -> 8;
			default -> 0;
		};
	}

	/** It is fighting: it stands in front of its user. */
	static boolean fighting(StandState s, float age) {
		if (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.grabbedBlock != 0 || s.inhaleUntil != 0L) {
			return true;
		}
		return switch (s.animId) {
			case StandState.ANIM_NONE, StandState.ANIM_SUMMON, StandState.ANIM_DISMISS, StandState.ANIM_LEAP, StandState.ANIM_DASH -> false;
			default -> age >= 0f && age < animLength(s.animId);
		};
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		if (!Stands.isStandUser(mc.player)) {
			return; // the user's rule: only a Stand user perceives a Stand
		}
		if (model == null) {
			model = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		for (Player p : mc.level.players()) {
			StandState s = Stands.peek(p);
			if (s == null || !s.isUser() || p.isSpectator() || p.distanceToSqr(cam) > 96 * 96) {
				continue;
			}
			StandType type = StandType.byId(s.standId);
			if (type == null) {
				continue;
			}
			float age = p.level().getGameTime() - s.animStart + partial;
			boolean dismissing = !s.summoned && s.animId == StandState.ANIM_DISMISS && age >= 0f && age < animLength(StandState.ANIM_DISMISS);
			if (!s.summoned && !dismissing) {
				SMOOTH.remove(p.getId());
				continue;
			}
			try {
				renderOne(ctx.matrixStack(), ctx.consumers(), p, s, type, partial, age, cam, dismissing);
			} catch (RuntimeException e) {
				ProjectHeroMod.LOGGER.warn("[ProjectHero] Stand render failed", e);
				SMOOTH.remove(p.getId());
			}
		}
	}

	private static void renderOne(PoseStack pose, MultiBufferSource buffers, Player p, StandState s, StandType type, float partial, float age,
			Vec3 cam, boolean dismissing) {
		Minecraft mc = Minecraft.getInstance();
		boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
		boolean fight = fighting(s, age);
		if (firstPerson && !fight) {
			return; // at rest it is behind you, out of your own view
		}
		long now = p.level().getGameTime();
		float t = now + partial;
		Vec3 at = p.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float headYaw = p.getViewYRot(partial);
		float pitch = p.getViewXRot(partial);
		Vec3 fwdBody = dir(bodyYaw);
		Vec3 fwdHead = dir(headYaw);

		Vec3 want;
		float wantYaw;
		if (fight) {
			// v0.17.2 (user: "in the bottom corner of the players screen, i want him to be in front of the player like he is in third
			// person"): in first person too it stands square in front of you, its back to you -- just far enough out and low enough
			// that its head sits under the crosshair
			double out = firstPerson ? 1.7 : 1.05;
			want = at.add(fwdHead.scale(out)).add(0, firstPerson ? -0.72 : 0.05, 0);
			wantYaw = headYaw;
		} else {
			want = at.subtract(fwdBody.scale(0.5)).add(StarPlatinum.rightOf(fwdBody).scale(0.75)).add(0, 0.35 + 0.06 * Mth.sin(t * 0.1f), 0);
			wantYaw = bodyYaw;
		}
		if (s.animId == StandState.ANIM_SUMMON && age < StandConfig.SUMMON_ANIM_TICKS) {
			want = want.add(0, -0.6 * (1f - ease(age / StandConfig.SUMMON_ANIM_TICKS)), 0); // it rises out of its user
		}

		Smooth sm = SMOOTH.computeIfAbsent(p.getId(), k -> new Smooth());
		long nanos = System.nanoTime();
		float dt = sm.lastNanos == 0L ? 1f : Mth.clamp((nanos - sm.lastNanos) / 5.0e7f, 0f, 3f);
		sm.lastNanos = nanos;
		if (sm.pos == null || sm.pos.distanceToSqr(want) > 10 * 10) {
			sm.pos = want;
		} else {
			// quick: it snaps out in front to fight, and drifts back to the shoulder
			float k = Mth.clamp((fight ? 0.6f : 0.25f) * dt, 0f, 1f);
			sm.pos = sm.pos.lerp(want, k);
		}
		if (Float.isNaN(sm.yaw)) {
			sm.yaw = wantYaw;
		}
		sm.yaw += Mth.wrapDegrees(wantYaw - sm.yaw) * Mth.clamp(0.35f * dt, 0f, 1f);
		Vec3 pos = sm.pos;

		float alpha = 1f;
		if (s.animId == StandState.ANIM_SUMMON && age < StandConfig.SUMMON_ANIM_TICKS) {
			alpha = Mth.clamp(age / StandConfig.SUMMON_ANIM_TICKS, 0f, 1f);
		}
		if (dismissing) {
			alpha = 1f - Mth.clamp(age / animLength(StandState.ANIM_DISMISS), 0f, 1f);
		}
		if (firstPerson) {
			alpha *= 0.8f; // see-through enough to fight past
		}
		if (alpha <= 0.02f) {
			return;
		}

		// ---- pose the body
		PlayerModel<Player> m = model;
		resetPose(m);
		float lookYaw = Mth.clamp(Mth.wrapDegrees(headYaw - sm.yaw), -70f, 70f);
		m.head.yRot = lookYaw * Mth.DEG_TO_RAD;
		m.head.xRot = Mth.clamp(pitch, -60f, 60f) * Mth.DEG_TO_RAD;
		StandPoses.apply(m, s, age, t, now);
		copyLayers(m);

		int light = light(p, pos.add(0, 1.2, 0));
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		pose.pushPose();
		pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - sm.yaw));
		pose.scale(-SCALE, -SCALE, SCALE);
		pose.translate(0f, -1.501f, 0f);
		VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucent(type.texture()));
		m.renderToBuffer(pose, body, light, OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFFFFF);
		// the rush's blurred extra arms
		if (s.rushing) {
			renderRushArms(pose, body, m, t, light, a);
		}
		// the faint purple glow shell
		pose.pushPose();
		pose.scale(1.06f, 1.04f, 1.06f);
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(type.texture()));
		int ga = (int) (a * (0.16f + 0.05f * Mth.sin(t * 0.2f)));
		m.renderToBuffer(pose, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (ga << 24) | (type.colour() & 0xFFFFFF));
		pose.popPose();
		pose.popPose();

		if (s.grabbedBlock != 0) {
			renderHeldBlock(pose, buffers, p, partial, cam, s.grabbedBlock, light);
		}
		renderText(pose, buffers, p, s, pos, age, t, cam);
		motes(p, s, pos, now, firstPerson, cam);
	}

	private static Vec3 dir(float yawDeg) {
		float r = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	private static void resetPose(PlayerModel<Player> m) {
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.leftSleeve, m.rightSleeve,
				m.leftPants, m.rightPants, m.jacket }) {
			part.resetPose();
			part.visible = true;
		}
		m.crouching = false;
		m.young = false;
	}

	/** The overlay layers follow their limbs (PlayerModel#setupAnim does this; it is never called here). */
	private static void copyLayers(PlayerModel<Player> m) {
		m.hat.copyFrom(m.head);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
	}

	/** Six ghostly fists blurring round the real two. */
	private static void renderRushArms(PoseStack pose, VertexConsumer vc, PlayerModel<Player> m, float t, int light, int a) {
		var saveR = m.rightArm.storePose();
		var saveL = m.leftArm.storePose();
		int ga = (int) (a * 0.38f);
		for (int i = 0; i < 6; i++) {
			boolean right = i % 2 == 0;
			ModelPart arm = right ? m.rightArm : m.leftArm;
			ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
			arm.loadPose(right ? saveR : saveL);
			float ph = t * 2.6f + i * 1.7f;
			arm.xRot = -Mth.HALF_PI + 0.35f * Mth.sin(ph) - 0.15f * (i / 2);
			arm.yRot = (right ? -1f : 1f) * (0.25f + 0.18f * (i / 2)) + 0.12f * Mth.cos(ph);
			arm.x += (right ? 1.5f : -1.5f) * (i / 2);
			arm.y += -1.5f + 1.5f * (i / 2) + Mth.sin(ph * 0.7f);
			sleeve.copyFrom(arm);
			arm.render(pose, vc, light, OverlayTexture.NO_OVERLAY, (ga << 24) | 0xFFFFFF);
			sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY, (ga << 24) | 0xFFFFFF);
		}
		m.rightArm.loadPose(saveR);
		m.leftArm.loadPose(saveL);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftSleeve.copyFrom(m.leftArm);
	}

	private static void renderHeldBlock(PoseStack pose, MultiBufferSource buffers, Player p, float partial, Vec3 cam, int blockId, int light) {
		BlockState state = Block.stateById(blockId);
		Vec3 eye = p.getEyePosition(partial);
		Vec3 at = eye.add(p.getViewVector(partial).scale(2.0)).subtract(0, 0.3, 0);
		pose.pushPose();
		pose.translate(at.x - cam.x - 0.4, at.y - cam.y - 0.4, at.z - cam.z - 0.4);
		pose.scale(0.8f, 0.8f, 0.8f);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** ゴゴゴ on a summon; floating ORA over the rush (its user's setting). */
	private static void renderText(PoseStack pose, MultiBufferSource buffers, Player p, StandState s, Vec3 pos, float age, float t, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		if (s.animId == StandState.ANIM_SUMMON && age < 34f) {
			float fade = 1f - Mth.clamp((age - 20f) / 14f, 0f, 1f);
			for (int i = 0; i < 3; i++) {
				Vec3 at = pos.add((i - 1) * 0.7, 2.0 + i * 0.25 + age * 0.02, 0).add(dir(p.getYRot() + 90f).scale((i - 1) * 0.6));
				drawText(pose, buffers, font, Component.literal("ゴ"), at, cam, 0.045f + 0.008f * Mth.sin(t * 0.8f + i), argb(fade, 0xB06CFF));
			}
		}
		if (s.rushing && s.oraText) {
			long slot = (long) (t / 3f);
			for (int i = 0; i < 4; i++) {
				long seed = (slot + i * 7919L) * 2654435761L;
				double ox = ((seed >> 8) & 255) / 255.0 - 0.5;
				double oy = ((seed >> 16) & 255) / 255.0;
				double oz = ((seed >> 24) & 255) / 255.0 - 0.5;
				Vec3 at = pos.add(ox * 2.2, 1.0 + oy * 1.6, oz * 2.2);
				drawText(pose, buffers, font, Component.literal("ORA").withStyle(net.minecraft.ChatFormatting.BOLD), at, cam, 0.035f,
						i % 2 == 0 ? 0xFFFFF2A0 : 0xFFE6D2FF);
			}
		}
	}

	private static int argb(float alpha, int rgb) {
		return (Mth.clamp((int) (alpha * 255f), 4, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void drawText(PoseStack pose, MultiBufferSource buffers, Font font, Component text, Vec3 at, Vec3 cam, float scale, int colour) {
		if (at.distanceToSqr(cam) < 2.4 * 2.4) {
			return; // never right in your face (your own rush, first person)
		}
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
		pose.scale(scale, -scale, scale);
		float w = font.width(text) / 2f;
		font.drawInBatch(text, -w, 0f, colour, true, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		pose.popPose();
	}

	/** Faint violet motes off its outline (client particles: only this Stand user's client spawns them). */
	private static void motes(Player p, StandState s, Vec3 pos, long now, boolean firstPerson, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		Smooth sm = SMOOTH.get(p.getId());
		if (sm == null || sm.lastMote == now || mc.isPaused() || now % 3 != 0 || TimeStopClient.frozen(p)) {
			return;
		}
		sm.lastMote = now;
		var rnd = p.getRandom();
		Vec3 m = pos.add((rnd.nextDouble() - 0.5) * 0.9, rnd.nextDouble() * 2.1, (rnd.nextDouble() - 0.5) * 0.9);
		if (firstPerson && m.distanceToSqr(cam) < 1.5 * 1.5) {
			return;
		}
		p.level().addParticle(Stands.AURA, m.x, m.y, m.z, 0, 0.01, 0);
	}

	private static int light(Player p, Vec3 at) {
		int packed = LevelRenderer.getLightColor(p.level(), BlockPos.containing(at));
		// it always shows a little in the dark: a Stand is spirit, not flesh
		int block = Math.max(LightTexture.block(packed), 8);
		return LightTexture.pack(block, LightTexture.sky(packed));
	}
}
