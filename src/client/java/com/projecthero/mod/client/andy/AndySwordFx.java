package com.projecthero.mod.client.andy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyBoneSword;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.AndyParts;
import com.projecthero.mod.andy.data.AndyState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3 (user: "Give the sword attacks actual vfx"): the Bone Sword kit's effects, client-side, started from the synced move
 * animation ({@link AndyState#animId} / {@code animStart}) so every viewer sees them:
 * <ul>
 * <li>Bone Slash (and the parry's counter): a crescent swoosh round him -- a curtain of blood with a white-hot leading edge,
 * sweeping across along the cut (forehand, backhand, then a wide flat finisher) and flinging drops off its head.</li>
 * <li>Marrow Thrust: a spear streak out along the aim, a bone-white core in a red sheath, and a shock ring at its tip.</li>
 * <li>Blood-jet Dash: a curtain of blood left along the path and the stump's jet.</li>
 * <li>Blood Edge Wave: a flying crescent of blood with a glowing edge, raining drops as it goes.</li>
 * <li>Rib Cage Guard: a ring bursting out along the ground, bone splinters and blood off the chest; bone crumbling at the end.</li>
 * <li>Bone Splinter Volley: a muzzle cone of bone dust and blood.</li>
 * <li>Impale Throw: an overhead swoosh.</li>
 * <li>Parry: a white star flash on the blade; a landed parry flares bigger, throws sparks and cuts back.</li>
 * </ul>
 * Also the hold-V draw (blood at the shoulder as the arm gives, the arm bursting round the blade) and Tear Off's eruption.
 * Drawn from {@link AndyEffectsRenderer#render} after the entities; the drops are {@link BloodFx}'s.
 */
public final class AndySwordFx {
	private static final RandomSource RND = RandomSource.create();

	/** One running effect: its move, when it started, whose, where he was and which way he faced, which hand. */
	private static final class Fx {
		final int anim;
		final long start;
		final UUID who;
		final Vec3 origin;
		final Vec3 look;
		final float yaw;
		final float side;
		final List<Vec3> trail = new ArrayList<>();

		Fx(int anim, long start, UUID who, Vec3 origin, Vec3 look, float yaw, float side) {
			this.anim = anim;
			this.start = start;
			this.who = who;
			this.origin = origin;
			this.look = look;
			this.yaw = yaw;
			this.side = side;
		}
	}

	private static final List<Fx> LIVE = new ArrayList<>();
	private static final Map<UUID, Long> SEEN = new HashMap<>();

	static final int BONE = 0xE6DFC8, BONE_SHADE = 0xCFC4A6;

	private AndySwordFx() {
	}

	static void reset() {
		LIVE.clear();
		SEEN.clear();
	}

	// ---------------------------------------------------------------- watching

	/** From the client tick, for every Andy: a sword move that just started starts its effect; the draw's beats. */
	static void watch(Player player, AndyState s, long now) {
		Long seen = SEEN.put(player.getUUID(), s.animStart);
		long age = now - s.animStart;
		if (seen != null && seen != s.animStart && age < 6) {
			int a = s.animId;
			if (a >= AndyState.ANIM_SWORD_SLASH1 && a <= AndyState.ANIM_SWORD_COUNTER || a == AndyState.ANIM_TEAR_OFF) {
				int hand = s.boneSword() ? AndyBoneSword.hand(s) : Andy.mainArm(player);
				LIVE.add(new Fx(a, s.animStart, player.getUUID(), player.position(), player.getLookAngle(), player.getYRot(),
						hand == AndyState.RIGHT_ARM ? 1f : -1f));
				started(player, s, a);
			}
		}
		// the hold-V draw
		if (s.animId == AndyState.ANIM_SWORD_STRUGGLE) {
			struggle(player, s, age);
		} else if (s.animId == AndyState.ANIM_SWORD_RIP && s.boneSword() && age == AndyConfig.BONE_SWORD_REVEAL) {
			reveal(player, s);
		} else if (s.animId == AndyState.ANIM_SWORD_AWAY && age == 0) {
			Vec3 at = hand(player, Andy.mainArm(player), 0.45);
			BloodFx.eruption(at, new Vec3(0, 1, 0), player.getDeltaMovement(), 0.8f);
			bone(at, new Vec3(0, 0.1, 0), 10, 0.18);
		}
	}

	/** Where a hand is, about where the held sword's grip is, {@code out} along the look. */
	static Vec3 hand(Player p, int arm, double out) {
		Vec3 r = AndyParts.right(p);
		double side = arm == AndyState.RIGHT_ARM ? 1 : -1;
		return p.getEyePosition().add(r.scale(0.32 * side)).add(0, -0.42, 0).add(p.getLookAngle().scale(out));
	}

	private static Vec3 shoulder(Player p, int arm) {
		return AndyParts.centre(p, arm).add(0, 0.28, 0);
	}

	/** Bone splinters and dust: pale drops that fall and splat. */
	static void bone(Vec3 at, Vec3 vel, int count, double spread) {
		for (int k = 0; k < count; k++) {
			BloodFx.drop(at.x, at.y, at.z, vel.x + (RND.nextDouble() - 0.5) * spread * 2, vel.y + RND.nextDouble() * spread,
					vel.z + (RND.nextDouble() - 0.5) * spread * 2, 0.03f + RND.nextFloat() * 0.035f, RND.nextBoolean() ? BONE : BONE_SHADE,
					26 + RND.nextInt(20), 0);
		}
	}

	/**
	 * v0.17.3: a Splinter Volley shard (or the thrown sword) spent where it hit -- small pale bone bits and a few drops, the mod's
	 * own droplets (they fade out right in front of the camera) instead of vanilla's big item-crack particles.
	 */
	public static void shardHit(Vec3 at, boolean sword) {
		bone(at, new Vec3(0, 0.06, 0), sword ? 12 : 4, sword ? 0.12 : 0.08);
		BloodFx.spurt(at, new Vec3(0, 1, 0), sword ? 10 : 3, 0.15, 0.6, Vec3.ZERO);
	}

	/** The first tick of a move: its one-off bursts. */
	private static void started(Player p, AndyState s, int anim) {
		Vec3 look = p.getLookAngle();
		Vec3 carry = p.getDeltaMovement();
		int hand = s.boneSword() ? AndyBoneSword.hand(s) : Andy.mainArm(p);
		switch (anim) {
			case AndyState.ANIM_SWORD_GUARD -> {
				// the ribs burst out of his chest: blood and bone flung all round
				Vec3 c = p.position().add(0, 1.15, 0);
				for (int k = 0; k < 14; k++) {
					double ang = k / 14.0 * Math.PI * 2;
					Vec3 out = new Vec3(Math.cos(ang), 0.25, Math.sin(ang));
					BloodFx.spurt(c.add(out.scale(0.3)), out, 2, 0.28, 0.25, carry);
				}
				bone(c, new Vec3(0, 0.12, 0), 14, 0.22);
				BloodFx.mist(c, 4, 0.35f, carry);
			}
			case AndyState.ANIM_SWORD_SPLINTER -> {
				Vec3 at = hand(p, hand, 1.0);
				BloodFx.spurt(at, look, 12, 0.55, 0.22, carry);
				bone(at, look.scale(0.35), 12, 0.12);
				BloodFx.mist(at.add(look.scale(0.4)), 3, 0.3f, carry);
			}
			case AndyState.ANIM_SWORD_THRUST -> BloodFx.spurt(hand(p, hand, 1.4), look, 8, 0.6, 0.12, carry);
			case AndyState.ANIM_SWORD_COUNTER -> {
				Vec3 at = p.getEyePosition().add(look.scale(0.9)).add(0, -0.3, 0);
				for (int k = 0; k < 16; k++) {
					// sparks off the blade
					Vec3 v = new Vec3(RND.nextGaussian(), RND.nextGaussian() * 0.6 + 0.4, RND.nextGaussian()).normalize().scale(0.25 + RND.nextDouble() * 0.2);
					BloodFx.drop(at.x, at.y, at.z, v.x, v.y, v.z, 0.02f + RND.nextFloat() * 0.015f, RND.nextBoolean() ? 0xFFF6D8 : 0xFFD070, 8 + RND.nextInt(8), 2);
				}
				BloodFx.spurt(at.add(look.scale(0.6)), look, 10, 0.4, 0.4, carry);
			}
			case AndyState.ANIM_SWORD_PARRY -> {
				Vec3 at = p.getEyePosition().add(look.scale(0.9)).add(0, -0.3, 0);
				for (int k = 0; k < 6; k++) {
					Vec3 v = new Vec3(RND.nextGaussian(), RND.nextGaussian() * 0.5 + 0.3, RND.nextGaussian()).normalize().scale(0.15);
					BloodFx.drop(at.x, at.y, at.z, v.x, v.y, v.z, 0.018f, 0xFFF6D8, 6 + RND.nextInt(5), 2);
				}
			}
			case AndyState.ANIM_TEAR_OFF -> {
				int limb = (int) s.ex(AndyState.X_TORN);
				if (limb >= AndyState.RIGHT_ARM && limb <= AndyState.LEFT_LEG) {
					boolean arm = limb == AndyState.RIGHT_ARM || limb == AndyState.LEFT_ARM;
					Vec3 at = arm ? shoulder(p, limb) : AndyParts.centre(p, limb).add(0, 0.3, 0);
					Vec3 out = AndyParts.right(p).scale(limb == AndyState.RIGHT_ARM || limb == AndyState.RIGHT_LEG ? 1 : -1);
					BloodFx.eruption(at, arm ? out.add(0, 0.3, 0) : new Vec3(0, -1, 0).add(out.scale(0.3)), carry, 1.1f);
				}
			}
			default -> {
			}
		}
	}

	/** Hold V: as the arm gives, blood seeps and spurts at the shoulder on each wrench. */
	private static void struggle(Player p, AndyState s, long age) {
		int arm = Andy.otherArm(Andy.mainArm(p));
		if (s.broken(arm) || age < 12) {
			return;
		}
		Vec3 at = shoulder(p, arm);
		float pull = AndyBoneSwordRender.strugglePull(age);
		if (pull > 0.75f && RND.nextFloat() < 0.6f) {
			Vec3 out = AndyParts.right(p).scale(arm == AndyState.RIGHT_ARM ? 1 : -1).add(0, 0.5, 0);
			BloodFx.spurt(at, out, 2 + (int) (age / 12), 0.12, 0.35, p.getDeltaMovement());
		} else if (age % 4 == 0) {
			BloodFx.drip(at);
		}
	}

	/** The torn arm bursts in his grip: a blast of blood, bone and flesh round the blade that is left. */
	private static void reveal(Player p, AndyState s) {
		Vec3 at = hand(p, AndyBoneSword.hand(s), 0.6);
		Vec3 carry = p.getDeltaMovement();
		BloodFx.eruption(at, new Vec3(0, 1, 0), carry, 1.4f);
		BloodFx.eruption(at, p.getLookAngle(), carry, 0.8f);
		bone(at, new Vec3(0, 0.12, 0), 16, 0.22);
		BloodFx.flesh(at, carry.add(0, 0.1, 0), 10);
		BloodFx.mist(at, 5, 0.45f, carry);
	}

	// ---------------------------------------------------------------- ticking

	/** From the client tick: each running effect's drops; the dash's path; the finished ones go. */
	static void tick(long now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			LIVE.clear();
			return;
		}
		for (Iterator<Fx> it = LIVE.iterator(); it.hasNext();) {
			Fx fx = it.next();
			long age = now - fx.start;
			if (age > life(fx.anim)) {
				it.remove();
				continue;
			}
			Player p = mc.level.getPlayerByUUID(fx.who);
			switch (fx.anim) {
				case AndyState.ANIM_SWORD_SLASH1, AndyState.ANIM_SWORD_SLASH2, AndyState.ANIM_SWORD_SLASH3, AndyState.ANIM_SWORD_COUNTER -> {
					if (p != null && age <= sweepTicks(fx.anim)) {
						// drops flung off the head of the cut
						Slash sl = slash(fx);
						float a = sl.head(age + 0.5f);
						Vec3 c = centre(p, 1f);
						Vec3 at = sl.point(c, a, sl.radius);
						Vec3 tangent = sl.point(c, a + sl.dir * 0.2f, sl.radius).subtract(at).normalize();
						BloodFx.spurt(at, tangent.add(0, 0.25, 0), fx.anim == AndyState.ANIM_SWORD_SLASH3 ? 6 : 4, 0.32, 0.18, p.getDeltaMovement());
					}
				}
				case AndyState.ANIM_SWORD_DASH -> {
					if (p != null && age <= AndyBoneSword.DASH_TICKS + 2) {
						fx.trail.add(p.position());
						// the stump's jet, blowing back along the path
						AndyState s = Andy.peek(p);
						if (s != null && s.boneSword()) {
							Vec3 st = shoulder(p, s.boneSwordArm());
							BloodFx.jet(st, fx.look.scale(-1).add(0, 0.15, 0).normalize(), p.getDeltaMovement().scale(0.3), 1.6f);
						}
						BloodFx.spurt(p.position().add(0, 1.0, 0), fx.look.scale(-1).add(0, 0.4, 0), 3, 0.2, 0.4, Vec3.ZERO);
					}
				}
				case AndyState.ANIM_SWORD_EDGE -> {
					float d = edgeDistance(age + 0.5f);
					if (d >= 0f) {
						Vec3 dir = flat(fx.look);
						Vec3 side = new Vec3(-dir.z, 0, dir.x);
						Vec3 front = fx.origin.add(0, 1.0, 0).add(dir.scale(d));
						for (int k = 0; k < 5; k++) {
							double s = (RND.nextDouble() - 0.5) * 2;
							Vec3 at = front.add(side.scale(s * 1.6)).add(dir.scale(-s * s * 0.9));
							BloodFx.drop(at.x, at.y, at.z, dir.x * 0.25, -0.05, dir.z * 0.25, 0.025f + RND.nextFloat() * 0.03f, BloodFx.shade(RND.nextFloat()),
									30 + RND.nextInt(10), 0);
						}
					}
				}
				case AndyState.ANIM_SWORD_GUARD -> {
					if (p != null && age == AndyBoneSword.GUARD_TICKS - 4) {
						bone(p.position().add(0, 1.1, 0), new Vec3(0, 0.02, 0), 18, 0.12); // the ribs crumble
					} else if (p != null && age < AndyBoneSword.GUARD_TICKS && age % 5 == 0) {
						BloodFx.drip(p.position().add(RND.nextGaussian() * 0.3, 1.0, RND.nextGaussian() * 0.3));
					}
				}
				default -> {
				}
			}
		}
	}

	private static int life(int anim) {
		return switch (anim) {
			case AndyState.ANIM_SWORD_EDGE -> 18;
			case AndyState.ANIM_SWORD_GUARD -> AndyBoneSword.GUARD_TICKS + 2;
			case AndyState.ANIM_SWORD_DASH -> AndyBoneSword.DASH_TICKS + 10;
			default -> 14;
		};
	}

	/** How far the Edge Wave has run at {@code age} (as the server runs it: three ticks of swing, then 1.1 a tick); -1 before. */
	private static float edgeDistance(float age) {
		float a = age - 3f;
		if (a < 0f) {
			return -1f;
		}
		float d = 1f + a * (float) AndyBoneSword.EDGE_SPEED;
		return d > AndyBoneSword.EDGE_RANGE ? -1f : d;
	}

	// ---------------------------------------------------------------- the slash geometry

	private static int sweepTicks(int anim) {
		return anim == AndyState.ANIM_SWORD_SLASH3 ? 6 : anim == AndyState.ANIM_SWORD_COUNTER ? 3 : 4;
	}

	/** One cut's arc: round the player, from one side to the other, tilted along the cut. */
	private static final class Slash {
		Vec3 f, r, up;
		float span, tilt, dir, radius, sweep;

		/** The arc's angle (0 straight ahead, + to his right) at {@code age}. */
		float head(float age) {
			float t = Mth.clamp(age / sweep, 0f, 1f);
			t = 1f - (1f - t) * (1f - t); // fast out of the wind-up
			return dir * Mth.lerp(t, span, -span);
		}

		Vec3 point(Vec3 c, float a, float rad) {
			return c.add(f.scale(Mth.cos(a) * rad)).add(r.scale(Mth.sin(a) * rad)).add(up.scale(Mth.sin(a) * tilt * rad * 0.5f));
		}
	}

	private static Slash slash(Fx fx) {
		Slash s = new Slash();
		float yaw = fx.yaw * Mth.DEG_TO_RAD;
		s.f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		s.r = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		s.up = new Vec3(0, 1, 0);
		// tip it a little with where he looked (a cut at something below him goes down)
		double pitchY = Mth.clamp((float) fx.look.y, -0.6f, 0.6f);
		s.f = s.f.add(0, pitchY * 0.8, 0).normalize();
		s.sweep = sweepTicks(fx.anim);
		switch (fx.anim) {
			case AndyState.ANIM_SWORD_SLASH2 -> {
				s.span = 1.15f;
				s.tilt = -0.55f;
				s.dir = -fx.side;
				s.radius = 2.1f;
			}
			case AndyState.ANIM_SWORD_SLASH3 -> {
				s.span = 1.95f;
				s.tilt = 0.1f;
				s.dir = fx.side;
				s.radius = 2.6f;
			}
			case AndyState.ANIM_SWORD_COUNTER -> {
				s.span = 1.0f;
				s.tilt = 0.9f;
				s.dir = fx.side;
				s.radius = 2.0f;
			}
			default -> {
				s.span = 1.15f;
				s.tilt = 0.6f;
				s.dir = fx.side;
				s.radius = 2.1f;
			}
		}
		s.tilt *= fx.side;
		return s;
	}

	/** The cut's centre: a little below his eyes (so it crosses the view in first person). */
	private static Vec3 centre(Player p, float partial) {
		return p.getEyePosition(partial).add(0, -0.25, 0);
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	// ---------------------------------------------------------------- drawing

	static void render(WorldRenderContext context, PoseStack pose, Vec3 cam, float now) {
		if (LIVE.isEmpty() || context.consumers() == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		MultiBufferSource buffers = context.consumers();
		VertexConsumer body = buffers.getBuffer(RenderType.debugQuads());
		Matrix4f m = pose.last().pose();
		List<Runnable> glow = new ArrayList<>();
		for (Fx fx : LIVE) {
			float age = now - fx.start;
			if (age < 0f) {
				continue;
			}
			Player p = mc.level == null ? null : mc.level.getPlayerByUUID(fx.who);
			switch (fx.anim) {
				case AndyState.ANIM_SWORD_SLASH1, AndyState.ANIM_SWORD_SLASH2, AndyState.ANIM_SWORD_SLASH3, AndyState.ANIM_SWORD_COUNTER -> {
					if (p != null) {
						drawSlash(body, glow, buffers, m, slash(fx), centre(p, partial).subtract(cam), age, fx.anim == AndyState.ANIM_SWORD_SLASH3 ? 1.1f : 0.85f);
					}
					if (fx.anim == AndyState.ANIM_SWORD_COUNTER && p != null) {
						Vec3 at = p.getEyePosition(partial).add(fx.look.scale(0.9)).add(0, -0.3, 0).subtract(cam);
						star(glow, buffers, m, context.camera().rotation(), at, age, 1.6f);
					}
				}
				case AndyState.ANIM_SWORD_PARRY -> {
					if (p != null) {
						Vec3 at = p.getEyePosition(partial).add(p.getViewVector(partial).scale(0.9)).add(0, -0.3, 0).subtract(cam);
						star(glow, buffers, m, context.camera().rotation(), at, age, 0.8f);
					}
				}
				case AndyState.ANIM_SWORD_THRUST -> {
					if (p != null) {
						Vec3 from = hand(p, Andy.peek(p) != null && Andy.peek(p).boneSword() ? AndyBoneSword.hand(Andy.peek(p)) : Andy.mainArm(p), 0.9);
						from = from.add(p.getPosition(partial).subtract(p.position()));
						drawThrust(body, glow, buffers, m, from.subtract(cam), fx.look, age);
					}
				}
				case AndyState.ANIM_SWORD_DASH -> drawDash(body, glow, buffers, m, fx, cam, age);
				case AndyState.ANIM_SWORD_EDGE -> drawEdge(body, glow, buffers, m, fx, cam, age);
				case AndyState.ANIM_SWORD_GUARD -> {
					if (p != null) {
						drawRing(body, glow, buffers, m, p.getPosition(partial).add(0, 0.05, 0).subtract(cam), age, 0.5f, 3.0f, 7f);
					}
				}
				case AndyState.ANIM_SWORD_SPLINTER -> {
					if (p != null) {
						AndyState s = Andy.peek(p);
						Vec3 from = hand(p, s != null && s.boneSword() ? AndyBoneSword.hand(s) : Andy.mainArm(p), 0.9);
						from = from.add(p.getPosition(partial).subtract(p.position()));
						drawCone(body, glow, buffers, m, from.subtract(cam), fx.look, age);
					}
				}
				case AndyState.ANIM_SWORD_IMPALE -> {
					if (p != null) {
						Slash sl = new Slash();
						float yaw = fx.yaw * Mth.DEG_TO_RAD;
						Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
						Vec3 r = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
						// an overhead arc: the plane of forward and up, offset to the throwing side
						sl.f = f;
						sl.r = new Vec3(0, 1, 0).scale(-1);
						sl.up = r;
						sl.span = 1.3f;
						sl.tilt = 0f;
						sl.dir = 1f;
						sl.radius = 1.4f;
						sl.sweep = 4f;
						drawSlash(body, glow, buffers, m, sl, p.getEyePosition(partial).add(r.scale(0.35 * fx.side)).subtract(cam), age, 0.4f);
					}
				}
				default -> {
				}
			}
		}
		// the glowing parts after the blood, so they sit on it
		for (Runnable r : glow) {
			r.run();
		}
	}

	/**
	 * A cut's swoosh: a curtain of blood along the arc from its tail to its head, thickest and brightest at the head, a white-hot
	 * line along its leading edge; after the sweep the tail catches the head up and it is gone.
	 */
	private static void drawSlash(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Slash sl, Vec3 c, float age,
			float width) {
		float headA = sl.head(age);
		float fade = Mth.clamp(1f - (age - sl.sweep) / 5f, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		float startA = sl.dir * sl.span;
		float trail = (1.6f + sl.span * 0.4f) * Mth.clamp(age / sl.sweep, 0.2f, 1f) * fade;
		// the tail: never back past where the cut started
		float tailA = headA + sl.dir * trail;
		if ((tailA - startA) * sl.dir > 0) {
			tailA = startA;
		}
		int n = 22;
		Vec3 spread = sl.up.subtract(sl.r.scale(sl.tilt * 0.5f)).normalize();
		Vec3[] lo = new Vec3[n + 1], hi = new Vec3[n + 1], edge = new Vec3[n + 1];
		float[] u = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			float k = i / (float) n; // 0 tail .. 1 head
			float a = Mth.lerp(k, tailA, headA);
			float w = width * (0.15f + 0.85f * k * k) * (0.6f + 0.4f * fade);
			Vec3 mid = sl.point(c, a, sl.radius);
			// the curtain stands across the arc, bowed out a little at its top edge
			Vec3 radial = sl.point(Vec3.ZERO, a, 1f).normalize();
			lo[i] = mid.subtract(spread.scale(w * 0.55f)).subtract(radial.scale(w * 0.15f));
			hi[i] = mid.add(spread.scale(w * 0.45f)).add(radial.scale(w * 0.25f));
			edge[i] = mid.add(spread.scale(w * 0.32f)).add(radial.scale(w * 0.18f));
			u[i] = k;
		}
		for (int i = 0; i < n; i++) {
			float a0 = (0.25f + 0.75f * u[i]) * u[i] * fade, a1 = (0.25f + 0.75f * u[i + 1]) * u[i + 1] * fade;
			// dark red root to bright red crest, more opaque toward the head
			band(body, m, lo[i], lo[i + 1], hi[i + 1], hi[i], 0.32f, 0.01f, 0.03f, 0.9f * a0, 0.32f, 0.01f, 0.03f, 0.9f * a1, 0.86f, 0.1f, 0.1f,
					0.95f * a0, 0.86f, 0.1f, 0.1f, 0.95f * a1);
		}
		final Vec3[] e = edge, h = hi;
		final float f = fade;
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int i = 0; i < n; i++) {
				float a0 = u[i] * u[i] * u[i] * f, a1 = u[i + 1] * u[i + 1] * u[i + 1] * f;
				// the white-hot edge, cooling to red back along the trail
				band(add, m, e[i], e[i + 1], h[i + 1], h[i], 1f, 0.35f + 0.6f * u[i], 0.3f + 0.55f * u[i], a0, 1f, 0.35f + 0.6f * u[i + 1],
						0.3f + 0.55f * u[i + 1], a1, 1f, 0.9f, 0.82f, a0, 1f, 0.9f, 0.82f, a1);
			}
		});
	}

	/** Marrow Thrust: a streak along the aim that shoots out and fades, and a shock ring at its tip. */
	private static void drawThrust(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 from, Vec3 dir, float age) {
		float len = 4.2f * Mth.clamp(age / 2f, 0f, 1f);
		float fade = Mth.clamp(1f - (age - 3f) / 5f, 0f, 1f);
		if (fade <= 0f || len <= 0.01f) {
			return;
		}
		Vec3 d = dir.normalize();
		Vec3 a = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 b = d.cross(a).normalize();
		Vec3 tip = from.add(d.scale(len));
		float w = 0.22f * fade;
		for (Vec3 s : new Vec3[] { a, b }) {
			band(body, m, from.subtract(s.scale(w)), tip, tip, from.add(s.scale(w)), 0.6f, 0.03f, 0.05f, 0.85f * fade, 0.8f, 0.08f, 0.08f, 0.0f,
					0.6f, 0.03f, 0.05f, 0.85f * fade, 0.8f, 0.08f, 0.08f, 0.0f);
		}
		final float ww = w * 0.45f;
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (Vec3 s : new Vec3[] { a, b }) {
				band(add, m, from.subtract(s.scale(ww)), tip, tip, from.add(s.scale(ww)), 1f, 0.92f, 0.85f, fade, 1f, 0.6f, 0.5f, 0.2f * fade, 1f, 0.92f,
						0.85f, fade, 1f, 0.6f, 0.5f, 0.2f * fade);
			}
		});
		// the shock ring where the point stops
		if (age >= 1.5f) {
			float rt = Mth.clamp((age - 1.5f) / 6f, 0f, 1f);
			ringAt(body, glow, buffers, m, from.add(d.scale(4.2f)), a, b, 0.2f + rt * 1.1f, 0.1f * (1f - rt) + 0.03f, (1f - rt) * 0.8f);
		}
	}

	/** Blood-jet Dash: a curtain of blood along his path, fading behind him, a bright line through it at the blade's height. */
	private static void drawDash(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Fx fx, Vec3 cam, float age) {
		List<Vec3> t = fx.trail;
		if (t.size() < 2) {
			return;
		}
		List<Vec3[]> lines = new ArrayList<>();
		for (int i = 0; i + 1 < t.size(); i++) {
			float a0 = Mth.clamp(1f - (age - i) / 8f, 0f, 1f), a1 = Mth.clamp(1f - (age - i - 1) / 8f, 0f, 1f);
			Vec3 p0 = t.get(i).subtract(cam), p1 = t.get(i + 1).subtract(cam);
			band(body, m, p0.add(0, 0.35, 0), p1.add(0, 0.35, 0), p1.add(0, 1.55, 0), p0.add(0, 1.55, 0), 0.35f, 0.01f, 0.03f, 0.0f, 0.35f, 0.01f, 0.03f,
					0.0f, 0.75f, 0.06f, 0.08f, 0.8f * a0, 0.75f, 0.06f, 0.08f, 0.8f * a1);
			lines.add(new Vec3[] { p0.add(0, 1.05, 0), p1.add(0, 1.05, 0), new Vec3(a0, a1, 0) });
		}
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (Vec3[] l : lines) {
				float a0 = (float) l[2].x, a1 = (float) l[2].y;
				band(add, m, l[0].add(0, -0.06, 0), l[1].add(0, -0.06, 0), l[1].add(0, 0.06, 0), l[0].add(0, 0.06, 0), 1f, 0.8f, 0.7f, a0, 1f, 0.8f, 0.7f,
						a1, 1f, 0.8f, 0.7f, a0, 1f, 0.8f, 0.7f, a1);
			}
		});
	}

	/** Blood Edge Wave: a crescent of blood flying out, wide and flat, its front edge glowing, its horns swept back. */
	private static void drawEdge(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Fx fx, Vec3 cam, float age) {
		float d = edgeDistance(age);
		if (d < 0f) {
			return;
		}
		float fade = Mth.clamp((float) (AndyBoneSword.EDGE_RANGE - d) / 2.5f, 0f, 1f) * Mth.clamp((d - 0.8f) / 0.8f, 0f, 1f);
		Vec3 dir = flat(fx.look);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 front = fx.origin.add(0, 1.0, 0).add(dir.scale(d)).subtract(cam);
		int n = 18;
		float width = 1.75f;
		Vec3[] lo = new Vec3[n + 1], hi = new Vec3[n + 1], e0 = new Vec3[n + 1], e1 = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			float s = i / (float) n * 2f - 1f;
			float th = 0.55f * (1f - s * s) + 0.04f;
			Vec3 mid = front.add(side.scale(s * width)).add(dir.scale(-s * s * 0.95f));
			lo[i] = mid.add(0, -th * 0.5, 0).add(dir.scale(-th * 0.6));
			hi[i] = mid.add(0, th * 0.5, 0).add(dir.scale(-th * 0.6));
			e0[i] = mid.add(0, -th * 0.18, 0);
			e1[i] = mid.add(0, th * 0.18, 0);
		}
		for (int i = 0; i < n; i++) {
			// the body: from the back (dark, thin) to the front edge (bright, thick)
			band(body, m, lo[i], lo[i + 1], e0[i + 1], e0[i], 0.4f, 0.01f, 0.03f, 0.75f * fade, 0.4f, 0.01f, 0.03f, 0.75f * fade, 0.85f, 0.08f, 0.1f,
					0.95f * fade, 0.85f, 0.08f, 0.1f, 0.95f * fade);
			band(body, m, e1[i], e1[i + 1], hi[i + 1], hi[i], 0.85f, 0.08f, 0.1f, 0.95f * fade, 0.85f, 0.08f, 0.1f, 0.95f * fade, 0.4f, 0.01f, 0.03f,
					0.75f * fade, 0.4f, 0.01f, 0.03f, 0.75f * fade);
			band(body, m, e0[i], e0[i + 1], e1[i + 1], e1[i], 0.85f, 0.08f, 0.1f, 0.95f * fade, 0.85f, 0.08f, 0.1f, 0.95f * fade, 0.85f, 0.08f, 0.1f,
					0.95f * fade, 0.85f, 0.08f, 0.1f, 0.95f * fade);
		}
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int i = 0; i < n; i++) {
				float s0 = i / (float) n * 2f - 1f, s1 = (i + 1) / (float) n * 2f - 1f;
				float a0 = (1f - s0 * s0) * fade, a1 = (1f - s1 * s1) * fade;
				Vec3 f0 = e0[i].add(e1[i]).scale(0.5).add(dir.scale(0.05)), f1 = e0[i + 1].add(e1[i + 1]).scale(0.5).add(dir.scale(0.05));
				band(add, m, f0.add(0, -0.07, 0), f1.add(0, -0.07, 0), f1.add(0, 0.07, 0), f0.add(0, 0.07, 0), 1f, 0.75f, 0.65f, a0, 1f, 0.75f, 0.65f, a1,
						1f, 0.75f, 0.65f, a0, 1f, 0.75f, 0.65f, a1);
			}
		});
	}

	/** Rib Cage Guard: a ring of blood and bone dust bursting out along the ground. */
	private static void drawRing(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 c, float age, float r0, float r1,
			float len) {
		float t = Mth.clamp(age / len, 0f, 1f);
		if (t >= 1f) {
			return;
		}
		float e = 1f - (1f - t) * (1f - t);
		ringAt(body, glow, buffers, m, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), Mth.lerp(e, r0, r1), 0.35f * (1f - t) + 0.05f, 1f - t);
	}

	/** A flat ring in the plane of {@code a} and {@code b} round {@code c}: dark red, a pale glowing inner rim. */
	private static void ringAt(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 c, Vec3 a, Vec3 b, float r, float w,
			float alpha) {
		int n = 28;
		Vec3[] in = new Vec3[n + 1], out = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			float ang = i / (float) n * Mth.TWO_PI;
			Vec3 dir = a.scale(Mth.cos(ang)).add(b.scale(Mth.sin(ang)));
			in[i] = c.add(dir.scale(Math.max(0.01f, r - w)));
			out[i] = c.add(dir.scale(r));
		}
		for (int i = 0; i < n; i++) {
			band(body, m, in[i], in[i + 1], out[i + 1], out[i], 0.85f, 0.1f, 0.1f, 0.85f * alpha, 0.85f, 0.1f, 0.1f, 0.85f * alpha, 0.35f, 0.01f, 0.03f,
					0.0f, 0.35f, 0.01f, 0.03f, 0.0f);
		}
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int i = 0; i < n; i++) {
				Vec3 m0 = in[i].add(out[i].subtract(in[i]).scale(0.35)), m1 = in[i + 1].add(out[i + 1].subtract(in[i + 1]).scale(0.35));
				band(add, m, in[i], in[i + 1], m1, m0, 1f, 0.9f, 0.8f, 0.7f * alpha, 1f, 0.9f, 0.8f, 0.7f * alpha, 1f, 0.5f, 0.4f, 0f, 1f, 0.5f, 0.4f, 0f);
			}
		});
	}

	/** Bone Splinter Volley: a short muzzle cone of bone-white dust and blood out of the blade. */
	private static void drawCone(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 from, Vec3 dir, float age) {
		float t = Mth.clamp(age / 5f, 0f, 1f);
		if (t >= 1f) {
			return;
		}
		Vec3 d = dir.normalize();
		Vec3 a = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 b = d.cross(a).normalize();
		float len = 0.6f + 1.4f * t, rad = 0.12f + 0.5f * t;
		ringAt(body, glow, buffers, m, from.add(d.scale(len)), a, b, rad, rad * 0.5f, (1f - t));
		int n = 10;
		for (int i = 0; i < n; i++) {
			float g0 = i / (float) n * Mth.TWO_PI, g1 = (i + 1) / (float) n * Mth.TWO_PI;
			Vec3 r0 = a.scale(Mth.cos(g0) * rad).add(b.scale(Mth.sin(g0) * rad)), r1 = a.scale(Mth.cos(g1) * rad).add(b.scale(Mth.sin(g1) * rad));
			Vec3 tip = from.add(d.scale(len));
			band(body, m, from, from, tip.add(r1), tip.add(r0), 0.95f, 0.88f, 0.75f, 0.7f * (1f - t), 0.95f, 0.88f, 0.75f, 0.7f * (1f - t), 0.7f, 0.05f,
					0.07f, 0f, 0.7f, 0.05f, 0.07f, 0f);
		}
	}

	/** A four-pointed star flash facing the camera: the parry's ring of steel, here of bone. */
	private static void star(List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Quaternionf face, Vec3 at, float age, float size) {
		float t = age / 7f;
		if (t >= 1f) {
			return;
		}
		float pop = Mth.clamp(age / 1.5f, 0f, 1f);
		float s = size * pop * (1f - 0.4f * t);
		float alpha = 1f - t;
		float spin = age * 0.08f;
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int k = 0; k < 4; k++) {
				float ang = spin + k * Mth.HALF_PI * 0.5f;
				float len = (k % 2 == 0 ? 1f : 0.55f) * s, w = 0.06f * s;
				Vector3f ax = new Vector3f(Mth.cos(ang), Mth.sin(ang), 0).rotate(face);
				Vector3f wx = new Vector3f(-Mth.sin(ang), Mth.cos(ang), 0).rotate(face);
				Vec3 A = new Vec3(ax.x, ax.y, ax.z), W = new Vec3(wx.x, wx.y, wx.z);
				band(add, m, at.subtract(A.scale(len)), at.subtract(W.scale(w)), at.add(A.scale(len)), at.add(W.scale(w)), 1f, 0.95f, 0.9f, 0f, 1f, 0.95f,
						0.9f, alpha, 1f, 0.95f, 0.9f, alpha, 1f, 0.95f, 0.9f, 0f);
			}
			// the soft core
			Vector3f x = new Vector3f(1, 0, 0).rotate(face).mul(0.25f * s), y = new Vector3f(0, 1, 0).rotate(face).mul(0.25f * s);
			Vec3 X = new Vec3(x.x, x.y, x.z), Y = new Vec3(y.x, y.y, y.z);
			band(add, m, at.subtract(X).subtract(Y), at.add(X).subtract(Y), at.add(X).add(Y), at.subtract(X).add(Y), 1f, 0.4f, 0.35f, 0.5f * alpha, 1f, 0.4f,
					0.35f, 0.5f * alpha, 1f, 0.4f, 0.35f, 0.5f * alpha, 1f, 0.4f, 0.35f, 0.5f * alpha);
		});
	}

	/**
	 * A quad p0 p1 p2 p3 (p0-p1 one edge, p3-p2 the other), double-sided, each corner its own colour: (r,g,b,a) at p0, p1, then
	 * p2's and p3's as {@code c2}/{@code c3} -- given as (edge A at p0, p1), (edge B at p3, p2).
	 */
	private static void band(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float r0, float g0, float b0, float a0, float r1,
			float g1, float b1, float a1, float r3, float g3, float b3, float a3, float r2, float g2, float b2, float a2) {
		v(vc, m, p0, r0, g0, b0, a0);
		v(vc, m, p1, r1, g1, b1, a1);
		v(vc, m, p2, r2, g2, b2, a2);
		v(vc, m, p3, r3, g3, b3, a3);
		v(vc, m, p3, r3, g3, b3, a3);
		v(vc, m, p2, r2, g2, b2, a2);
		v(vc, m, p1, r1, g1, b1, a1);
		v(vc, m, p0, r0, g0, b0, a0);
	}

	private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
	}
}
