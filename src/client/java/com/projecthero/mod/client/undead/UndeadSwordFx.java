package com.projecthero.mod.client.undead;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.UndeadKitConfig;
import com.projecthero.mod.undead.UndeadParts;
import com.projecthero.mod.undead.data.UndeadState;

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
 * v0.17.3: the bone katana's effects (hold V), client-side, started from the synced move animation ({@link UndeadState#animId}
 * / {@code animStart}) so every viewer sees them:
 * <ul>
 * <li>Crimson Crescent Moon (R): the draw-cut's swoosh, then a red crescent with a white-hot edge flying out, raining drops.</li>
 * <li>Great Deep-Crimson Crescent Moon (G): blood gathering on the raised blade, the overhead swoosh, then a towering crescent
 * of blood running along the ground, its crest glowing, tearing a spray of blood up behind it.</li>
 * <li>Crimson Flash (Z): a white-red streak along the path of the draw; when the cuts open, blood bursts out all along it.</li>
 * <li>Blood Whirl (C): two rings of blood swept round him, flinging drops off their heads.</li>
 * </ul>
 * Also the hold-V draw (blood at the shoulder as the arm gives, the arm bursting round the blade) and Reform's flash of blood and
 * flesh closing. The rest of the v0.17.3 kit's effects (Vortex Bullet, Unluck Meteor, Rib Cage Guard, the shoulder sword) are
 * {@link UndeadKitFx}'s, run from here. Drawn from {@link UndeadEffectsRenderer#render} after the entities; the drops are
 * {@link BloodFx}'s.
 */
public final class UndeadSwordFx {
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

	private UndeadSwordFx() {
	}

	static void reset() {
		LIVE.clear();
		SEEN.clear();
		UndeadKitFx.reset();
	}

	// ---------------------------------------------------------------- watching

	/** From the client tick, for every Undead: a katana skill that just started starts its effect; the draw's beats. */
	static void watch(Player player, UndeadState s, long now) {
		Long seen = SEEN.put(player.getUUID(), s.animStart);
		long age = now - s.animStart;
		if (seen != null && seen != s.animStart && age < 6) {
			int a = s.animId;
			if (a >= UndeadKitConfig.ANIM_K_CRESCENT && a <= UndeadKitConfig.ANIM_K_WHIRL) {
				int hand = s.boneSword() ? UndeadBoneSword.hand(s) : Undead.mainArm(player);
				Vec3 look = player.getLookAngle();
				LIVE.add(new Fx(a, s.animStart, player.getUUID(), player.position(), look, player.getYRot(), hand == UndeadState.RIGHT_ARM ? 1f : -1f));
				started(player, s, a);
			} else if (a == UndeadState.ANIM_REFORM) {
				reformFlash(player, s);
			}
		}
		// the hold-V draw
		if (s.animId == UndeadState.ANIM_SWORD_STRUGGLE) {
			struggle(player, s, age);
		} else if (s.animId == UndeadState.ANIM_SWORD_RIP && s.boneSword() && age == UndeadConfig.BONE_SWORD_REVEAL) {
			reveal(player, s);
		} else if (s.animId == UndeadState.ANIM_SWORD_AWAY && age == 0) {
			Vec3 at = hand(player, Undead.mainArm(player), 0.45);
			BloodFx.eruption(at, new Vec3(0, 1, 0), player.getDeltaMovement(), 0.8f);
			bone(at, new Vec3(0, 0.1, 0), 10, 0.18);
		}
		UndeadKitFx.watch(player, s, now);
	}

	/** Where a hand is, about where the held sword's grip is, {@code out} along the look. */
	static Vec3 hand(Player p, int arm, double out) {
		Vec3 r = UndeadParts.right(p);
		double side = arm == UndeadState.RIGHT_ARM ? 1 : -1;
		return p.getEyePosition().add(r.scale(0.32 * side)).add(0, -0.42, 0).add(p.getLookAngle().scale(out));
	}

	private static Vec3 shoulder(Player p, int arm) {
		return UndeadParts.centre(p, arm).add(0, 0.28, 0);
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
	 * A bone shard (an old Splinter Volley shard, or a thrown sword) spent where it hit -- small pale bone bits and a few drops,
	 * the mod's own droplets instead of vanilla's big item-crack particles.
	 */
	public static void shardHit(Vec3 at, boolean sword) {
		bone(at, new Vec3(0, 0.06, 0), sword ? 12 : 4, sword ? 0.12 : 0.08);
		BloodFx.spurt(at, new Vec3(0, 1, 0), sword ? 10 : 3, 0.15, 0.6, Vec3.ZERO);
	}

	/**
	 * Tap V, Reform: the regrowth's bone, flesh and skin compressed into one beat -- on every part that just closed, blood, flesh
	 * and bone bits rush in from all round and vanish into it, with a puff of mist where it shut.
	 */
	private static void reformFlash(Player p, UndeadState s) {
		long mask = s.ex(UndeadState.X_REFORMED);
		Vec3 carry = p.getDeltaMovement();
		if (mask == 0L) {
			BloodFx.mist(p.position().add(0, 1.1, 0), 2, 0.25f, carry);
			return;
		}
		for (int part = 0; part < UndeadState.PARTS; part++) {
			if ((mask & (1L << part)) == 0L) {
				continue;
			}
			Vec3 c = UndeadParts.centre(p, part);
			boolean big = part == UndeadState.BODY;
			int n = big ? 30 : 18;
			for (int k = 0; k < n; k++) {
				Vec3 d = new Vec3(RND.nextGaussian(), RND.nextGaussian(), RND.nextGaussian());
				if (d.lengthSqr() < 1.0e-4) {
					continue;
				}
				d = d.normalize();
				double r = (big ? 0.55 : 0.4) + RND.nextDouble() * 0.2;
				int life = 3 + RND.nextInt(3);
				// in to the centre over its life (drag 0.97, gravity 0.045 a tick lifted off)
				double speed = r / (life * 0.97);
				Vec3 from = c.add(d.scale(r));
				int kind = k % 6;
				int rgb = kind == 0 ? (RND.nextBoolean() ? BONE : BONE_SHADE) : kind < 3 ? BloodFx.FLESH[RND.nextInt(BloodFx.FLESH.length)]
						: BloodFx.shade(RND.nextFloat());
				float size = kind == 0 ? 0.025f + RND.nextFloat() * 0.02f : kind < 3 ? 0.035f + RND.nextFloat() * 0.025f : 0.025f + RND.nextFloat() * 0.03f;
				BloodFx.drop(from.x, from.y, from.z, carry.x - d.x * speed, carry.y - d.y * speed + 0.045 * (life + 1) * 0.5, carry.z - d.z * speed,
						size, rgb, life, 3);
			}
			BloodFx.mist(c, big ? 3 : 2, 0.22f, carry);
		}
	}

	/** The first tick of a skill: its one-off bursts. */
	private static void started(Player p, UndeadState s, int anim) {
		Vec3 look = p.getLookAngle();
		Vec3 carry = p.getDeltaMovement();
		int hand = s.boneSword() ? UndeadBoneSword.hand(s) : Undead.mainArm(p);
		switch (anim) {
			case UndeadKitConfig.ANIM_K_CRESCENT -> BloodFx.spurt(hand(p, hand, 1.0), look, 6, 0.4, 0.3, carry);
			case UndeadKitConfig.ANIM_K_FLASH -> {
				// the draw: a flick of blood off the blade where he stood
				Vec3 at = p.position().add(0, 1.0, 0);
				BloodFx.spurt(at, look.scale(-1).add(0, 0.4, 0), 8, 0.3, 0.4, Vec3.ZERO);
				BloodFx.mist(at, 2, 0.3f, Vec3.ZERO);
			}
			default -> {
			}
		}
	}

	/** Hold V: as the arm gives, blood seeps and spurts at the shoulder on each wrench. */
	private static void struggle(Player p, UndeadState s, long age) {
		int arm = Undead.otherArm(Undead.mainArm(p));
		if (s.broken(arm) || age < 12) {
			return;
		}
		Vec3 at = shoulder(p, arm);
		float pull = UndeadBoneSwordRender.strugglePull(age);
		if (pull > 0.75f && RND.nextFloat() < 0.6f) {
			Vec3 out = UndeadParts.right(p).scale(arm == UndeadState.RIGHT_ARM ? 1 : -1).add(0, 0.5, 0);
			BloodFx.spurt(at, out, 2 + (int) (age / 12), 0.12, 0.35, p.getDeltaMovement());
		} else if (age % 4 == 0) {
			BloodFx.drip(at);
		}
	}

	/** The torn arm bursts in his grip: a blast of blood, bone and flesh round the blade that is left. */
	private static void reveal(Player p, UndeadState s) {
		Vec3 at = hand(p, UndeadBoneSword.hand(s), 0.6);
		Vec3 carry = p.getDeltaMovement();
		BloodFx.eruption(at, new Vec3(0, 1, 0), carry, 1.4f);
		BloodFx.eruption(at, p.getLookAngle(), carry, 0.8f);
		bone(at, new Vec3(0, 0.12, 0), 16, 0.22);
		BloodFx.flesh(at, carry.add(0, 0.1, 0), 10);
		BloodFx.mist(at, 5, 0.45f, carry);
	}

	// ---------------------------------------------------------------- ticking

	/** From the client tick: each running effect's drops; the flash's path; the finished ones go. Then the rest of the kit. */
	static void tick(long now) {
		Minecraft mc = Minecraft.getInstance();
		UndeadKitFx.tick(now);
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
				case UndeadKitConfig.ANIM_K_CRESCENT, UndeadKitConfig.ANIM_K_GREAT -> {
					boolean great = fx.anim == UndeadKitConfig.ANIM_K_GREAT;
					float d = waveDistance(fx.anim, age + 0.5f);
					if (great && age < UndeadKitConfig.K_GREAT_WINDUP && p != null) {
						// blood gathering on the raised blade
						Vec3 at = p.getEyePosition().add(0, 0.6 + age * 0.05, 0);
						BloodFx.spurt(at, new Vec3(0, -1, 0), 2, 0.05, 0.6, p.getDeltaMovement());
						BloodFx.mist(at, 1, 0.18f, p.getDeltaMovement());
					}
					if (d >= 0f) {
						Vec3 dir = waveDir(fx);
						Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
						Vec3 front = waveOrigin(fx).add(dir.scale(d));
						int n = great ? 9 : 4;
						double w = great ? UndeadKitConfig.K_GREAT_WIDTH : UndeadKitConfig.K_CRESCENT_WIDTH;
						for (int k = 0; k < n; k++) {
							double s = (RND.nextDouble() - 0.5) * 2;
							Vec3 at;
							if (great) {
								double th = (s + 1) * 0.5 * Math.PI;
								at = front.add(side.scale(Math.cos(th) * w)).add(0, Math.sin(th) * UndeadKitConfig.K_GREAT_HEIGHT, 0)
										.add(dir.scale(-(1 - Math.sin(th)) * 1.2));
							} else {
								at = front.add(side.scale(s * w)).add(dir.scale(-s * s * 0.95));
							}
							BloodFx.drop(at.x, at.y, at.z, dir.x * 0.25 + (RND.nextDouble() - 0.5) * 0.1, great ? 0.1 + RND.nextDouble() * 0.15 : -0.05,
									dir.z * 0.25 + (RND.nextDouble() - 0.5) * 0.1, 0.025f + RND.nextFloat() * 0.035f, BloodFx.shade(RND.nextFloat()),
									26 + RND.nextInt(14), 0);
						}
						if (great && age % 2 == 0) {
							BloodFx.mist(front.add(0, 0.3, 0), 1, 0.5f, Vec3.ZERO);
						}
					}
				}
				case UndeadKitConfig.ANIM_K_FLASH -> {
					if (p != null && age <= UndeadKitConfig.K_FLASH_TICKS + 1) {
						fx.trail.add(p.position());
					}
					if (age == UndeadKitConfig.K_FLASH_TICKS + UndeadKitConfig.K_FLASH_DELAY && fx.trail.size() >= 2) {
						// the cuts open: blood bursts out all along the line
						Vec3 a = fx.trail.get(0), b = fx.trail.get(fx.trail.size() - 1);
						for (int k = 0; k <= 10; k++) {
							Vec3 at = a.lerp(b, k / 10.0).add(0, 1.0, 0);
							BloodFx.spurt(at, new Vec3(0, 1, 0), 4, 0.28, 0.8, Vec3.ZERO);
						}
						BloodFx.mist(a.lerp(b, 0.5).add(0, 1.0, 0), 3, 0.45f, Vec3.ZERO);
					}
				}
				case UndeadKitConfig.ANIM_K_WHIRL -> {
					if (p != null && age <= UndeadKitConfig.K_WHIRL_TICKS) {
						// drops flung off the head of the ring
						float a = whirlAngle(fx, age + 0.5f);
						Vec3 c = p.position().add(0, 1.05, 0);
						Vec3 out = new Vec3(Mth.cos(a), 0, Mth.sin(a));
						Vec3 tangent = new Vec3(-Mth.sin(a), 0, Mth.cos(a)).scale(fx.side);
						BloodFx.spurt(c.add(out.scale(2.3)), tangent.add(out.scale(0.5)).add(0, 0.2, 0), 5, 0.35, 0.2, p.getDeltaMovement());
					}
				}
				default -> {
				}
			}
		}
	}

	private static int life(int anim) {
		return switch (anim) {
			case UndeadKitConfig.ANIM_K_CRESCENT -> 16;
			case UndeadKitConfig.ANIM_K_GREAT -> UndeadKitConfig.K_GREAT_WINDUP + 24;
			case UndeadKitConfig.ANIM_K_FLASH -> UndeadKitConfig.K_FLASH_TICKS + UndeadKitConfig.K_FLASH_DELAY + 12;
			case UndeadKitConfig.ANIM_K_WHIRL -> UndeadKitConfig.K_WHIRL_TICKS + 6;
			default -> 14;
		};
	}

	/** How far a crescent has run at {@code age} (as the server runs it); -1 before it sets off or once it is spent. */
	private static float waveDistance(int anim, float age) {
		boolean great = anim == UndeadKitConfig.ANIM_K_GREAT;
		float a = age - (great ? UndeadKitConfig.K_GREAT_WINDUP : 2);
		if (a < 0f) {
			return -1f;
		}
		float d = 1f + a * (float) (great ? UndeadKitConfig.K_GREAT_SPEED : UndeadKitConfig.K_CRESCENT_SPEED);
		return d > (great ? UndeadKitConfig.K_GREAT_RANGE : UndeadKitConfig.K_CRESCENT_RANGE) ? -1f : d;
	}

	private static Vec3 waveDir(Fx fx) {
		if (fx.anim == UndeadKitConfig.ANIM_K_GREAT) {
			return flat(fx.look);
		}
		Vec3 d = new Vec3(fx.look.x, Mth.clamp(fx.look.y, -0.35, 0.35), fx.look.z);
		return d.lengthSqr() < 1.0e-4 ? flat(fx.look) : d.normalize();
	}

	private static Vec3 waveOrigin(Fx fx) {
		return fx.origin.add(0, fx.anim == UndeadKitConfig.ANIM_K_GREAT ? 0.2 : 1.0, 0);
	}

	/** Blood Whirl: the ring's head at {@code age} (radians round him, from where he faced), two full turns. */
	private static float whirlAngle(Fx fx, float age) {
		float t = Mth.clamp(age / (UndeadKitConfig.K_WHIRL_TICKS - 1f), 0f, 1f);
		float base = (fx.yaw + 90f) * Mth.DEG_TO_RAD; // his facing as an angle in x/z
		return base + fx.side * (1f - (1f - t) * (1f - t)) * Mth.TWO_PI * 2f;
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0, v.z);
		return f.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : f.normalize();
	}

	// ---------------------------------------------------------------- the slash geometry

	/** One cut's arc: round a centre, from one side to the other, tilted along the cut. */
	static final class Slash {
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

	private static Slash slashFrom(Fx fx, float span, float tilt, float radius, float sweep) {
		Slash s = new Slash();
		float yaw = fx.yaw * Mth.DEG_TO_RAD;
		s.f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		s.r = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		s.up = new Vec3(0, 1, 0);
		double pitchY = Mth.clamp((float) fx.look.y, -0.6f, 0.6f);
		s.f = s.f.add(0, pitchY * 0.8, 0).normalize();
		s.span = span;
		s.tilt = tilt * fx.side;
		s.dir = fx.side;
		s.radius = radius;
		s.sweep = sweep;
		return s;
	}

	// ---------------------------------------------------------------- drawing

	static void render(WorldRenderContext context, PoseStack pose, Vec3 cam, float now) {
		UndeadKitFx.render(context, pose, cam, now);
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
				case UndeadKitConfig.ANIM_K_CRESCENT -> {
					if (p != null && age < 8f) {
						// the draw-cut: a flat swoosh across in front
						drawSlash(body, glow, buffers, m, slashFrom(fx, 1.3f, 0.25f, 2.0f, 3f), p.getEyePosition(partial).add(0, -0.3, 0).subtract(cam), age, 0.8f);
					}
					float d = waveDistance(fx.anim, age);
					if (d >= 0f) {
						float fade = Mth.clamp((float) (UndeadKitConfig.K_CRESCENT_RANGE - d) / 2.5f, 0f, 1f) * Mth.clamp((d - 0.8f) / 0.8f, 0f, 1f);
						Vec3 dir = waveDir(fx);
						Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
						Vec3 up = side.cross(dir).normalize().scale(-1);
						drawCrescent(body, glow, buffers, m, waveOrigin(fx).add(dir.scale(d)).subtract(cam), dir, side, up, (float) UndeadKitConfig.K_CRESCENT_WIDTH,
								0.62f, fade);
					}
				}
				case UndeadKitConfig.ANIM_K_GREAT -> {
					if (p != null && age >= UndeadKitConfig.K_GREAT_WINDUP - 1 && age < UndeadKitConfig.K_GREAT_WINDUP + 8) {
						// the overhead cut: a tall swoosh down in front, in the plane of forward and up
						Slash sl = slashFrom(fx, 1.35f, 0f, 2.2f, 3f);
						sl.f = flat(fx.look);
						sl.r = new Vec3(0, -1, 0);
						sl.up = new Vec3(-sl.f.z, 0, sl.f.x);
						sl.dir = 1f;
						drawSlash(body, glow, buffers, m, sl, p.getPosition(partial).add(0, 1.5, 0).subtract(cam), age - (UndeadKitConfig.K_GREAT_WINDUP - 1), 1.1f);
					}
					float d = waveDistance(fx.anim, age);
					if (d >= 0f) {
						float fade = Mth.clamp((float) (UndeadKitConfig.K_GREAT_RANGE - d) / 4f, 0f, 1f) * Mth.clamp((d - 0.6f) / 1.2f, 0f, 1f);
						drawGreat(body, glow, buffers, m, waveOrigin(fx).add(waveDir(fx).scale(d)).subtract(cam), waveDir(fx), fade, now);
					}
				}
				case UndeadKitConfig.ANIM_K_FLASH -> drawFlash(body, glow, buffers, m, fx, cam, age);
				case UndeadKitConfig.ANIM_K_WHIRL -> {
					if (p != null) {
						drawWhirl(body, glow, buffers, m, fx, p.getPosition(partial).add(0, 1.05, 0).subtract(cam), age);
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
	static void drawSlash(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Slash sl, Vec3 c, float age, float width) {
		if (age < 0f) {
			return;
		}
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

	/**
	 * A flying crescent of blood: its middle at {@code front}, its horns swept back along {@code dir} to either {@code side}, its
	 * thickness across {@code up}; dark at the back, bright red toward its glowing front edge.
	 */
	static void drawCrescent(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 front, Vec3 dir, Vec3 side, Vec3 up,
			float width, float thick, float fade) {
		if (fade <= 0.01f) {
			return;
		}
		int n = 22;
		Vec3[] lo = new Vec3[n + 1], hi = new Vec3[n + 1], e0 = new Vec3[n + 1], e1 = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			float s = i / (float) n * 2f - 1f;
			float th = thick * (1f - s * s) + 0.04f;
			Vec3 mid = front.add(side.scale(s * width)).add(dir.scale(-s * s * width * 0.55f));
			Vec3 back = dir.scale(-th * 0.9);
			lo[i] = mid.add(up.scale(-th * 0.5)).add(back);
			hi[i] = mid.add(up.scale(th * 0.5)).add(back);
			e0[i] = mid.add(up.scale(-th * 0.2));
			e1[i] = mid.add(up.scale(th * 0.2));
		}
		for (int i = 0; i < n; i++) {
			band(body, m, lo[i], lo[i + 1], e0[i + 1], e0[i], 0.38f, 0.0f, 0.03f, 0.7f * fade, 0.38f, 0.0f, 0.03f, 0.7f * fade, 0.9f, 0.06f, 0.08f,
					0.95f * fade, 0.9f, 0.06f, 0.08f, 0.95f * fade);
			band(body, m, e1[i], e1[i + 1], hi[i + 1], hi[i], 0.9f, 0.06f, 0.08f, 0.95f * fade, 0.9f, 0.06f, 0.08f, 0.95f * fade, 0.38f, 0.0f, 0.03f,
					0.7f * fade, 0.38f, 0.0f, 0.03f, 0.7f * fade);
			band(body, m, e0[i], e0[i + 1], e1[i + 1], e1[i], 0.95f, 0.1f, 0.1f, 0.95f * fade, 0.95f, 0.1f, 0.1f, 0.95f * fade, 0.95f, 0.1f, 0.1f,
					0.95f * fade, 0.95f, 0.1f, 0.1f, 0.95f * fade);
		}
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int i = 0; i < n; i++) {
				float s0 = i / (float) n * 2f - 1f, s1 = (i + 1) / (float) n * 2f - 1f;
				float a0 = (1f - s0 * s0) * fade, a1 = (1f - s1 * s1) * fade;
				Vec3 f0 = e0[i].add(e1[i]).scale(0.5).add(dir.scale(0.05)), f1 = e0[i + 1].add(e1[i + 1]).scale(0.5).add(dir.scale(0.05));
				Vec3 u0 = up.scale(0.09 * thick / 0.6), u1 = u0;
				band(add, m, f0.subtract(u0), f1.subtract(u1), f1.add(u1), f0.add(u0), 1f, 0.7f, 0.6f, a0, 1f, 0.7f, 0.6f, a1, 1f, 0.7f, 0.6f, a0, 1f,
						0.7f, 0.6f, a1);
			}
		});
	}

	/**
	 * The Great Deep-Crimson Crescent Moon in flight: an upright crescent of blood standing on the ground at {@code base}, arching
	 * from one side over the top to the other, its feet trailing back; the front face bright red, a glowing crest along its
	 * outer edge, a darker inner rim, a churning ripple through it.
	 */
	private static void drawGreat(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 base, Vec3 dir, float fade,
			float now) {
		if (fade <= 0.01f) {
			return;
		}
		Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
		float w = (float) UndeadKitConfig.K_GREAT_WIDTH, h = (float) UndeadKitConfig.K_GREAT_HEIGHT;
		int n = 28;
		Vec3[] in = new Vec3[n + 1], out = new Vec3[n + 1], crest = new Vec3[n + 1], backIn = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			float th = i / (float) n * Mth.PI;
			float sn = Mth.sin(th), cs = Mth.cos(th);
			float thick = 0.25f + 1.05f * sn; // fattest at the top
			float ripple = Mth.sin(now * 0.6f + i * 0.7f) * 0.08f;
			Vec3 trail = dir.scale(-(1f - sn) * 1.6f); // the feet sweep back
			Vec3 radial = side.scale(cs).add(0, sn, 0);
			Vec3 p = base.add(side.scale(cs * w)).add(0, sn * h, 0).add(trail);
			out[i] = p.add(radial.scale(ripple));
			in[i] = p.subtract(radial.scale(thick)).add(dir.scale(-0.5 * sn));
			crest[i] = p.add(radial.scale(0.12f + ripple)).add(dir.scale(0.08));
			backIn[i] = in[i].add(dir.scale(-0.6));
		}
		for (int i = 0; i < n; i++) {
			// the face: bright at the outer edge, dark toward the inner
			band(body, m, out[i], out[i + 1], in[i + 1], in[i], 0.92f, 0.08f, 0.09f, 0.95f * fade, 0.92f, 0.08f, 0.09f, 0.95f * fade, 0.42f, 0.0f, 0.03f,
					0.85f * fade, 0.42f, 0.0f, 0.03f, 0.85f * fade);
			// a darker body behind it, so it has depth seen from the side
			band(body, m, in[i], in[i + 1], backIn[i + 1], backIn[i], 0.42f, 0.0f, 0.03f, 0.85f * fade, 0.42f, 0.0f, 0.03f, 0.85f * fade, 0.25f, 0.0f,
					0.02f, 0.0f, 0.25f, 0.0f, 0.02f, 0.0f);
		}
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int i = 0; i < n; i++) {
				float t0 = i / (float) n, t1 = (i + 1) / (float) n;
				float a0 = (0.35f + 0.65f * Mth.sin(t0 * Mth.PI)) * fade, a1 = (0.35f + 0.65f * Mth.sin(t1 * Mth.PI)) * fade;
				band(add, m, out[i], out[i + 1], crest[i + 1], crest[i], 1f, 0.55f, 0.45f, a0, 1f, 0.55f, 0.45f, a1, 1f, 0.9f, 0.82f, a0, 1f, 0.9f, 0.82f, a1);
			}
		});
	}

	/** Crimson Flash: a bright line at the blade's height along the draw, fading; it flares red as the cuts open. */
	private static void drawFlash(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Fx fx, Vec3 cam, float age) {
		List<Vec3> t = fx.trail;
		if (t.size() < 2) {
			return;
		}
		Vec3 a = t.get(0).subtract(cam).add(0, 1.0, 0), b = t.get(t.size() - 1).subtract(cam).add(0, 1.0, 0);
		float open = UndeadKitConfig.K_FLASH_TICKS + UndeadKitConfig.K_FLASH_DELAY;
		float line = Mth.clamp(1f - (age - open) / 6f, 0f, 1f);
		if (line <= 0f) {
			return;
		}
		float flare = age >= open ? Mth.clamp(1f - (age - open) / 4f, 0f, 1f) : 0f;
		Vec3 d = b.subtract(a);
		Vec3 sideV = new Vec3(-d.z, 0, d.x);
		sideV = sideV.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : sideV.normalize();
		final Vec3 sv = sideV;
		// the afterimage: a thin vertical sheet of red mist along the path
		float sheet = 0.55f * line;
		band(body, m, a.add(0, -0.6, 0), b.add(0, -0.6, 0), b.add(0, 0.6, 0), a.add(0, 0.6, 0), 0.5f, 0.02f, 0.04f, 0f, 0.5f, 0.02f, 0.04f, 0f, 0.7f,
				0.04f, 0.06f, sheet * 0.4f, 0.7f, 0.04f, 0.06f, sheet * 0.4f);
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			double w = 0.035 + 0.12 * flare;
			for (Vec3 o : new Vec3[] { new Vec3(0, w, 0), sv.scale(w) }) {
				band(add, m, a.subtract(o), b.subtract(o), b.add(o), a.add(o), 1f, 0.9f - 0.6f * flare, 0.85f - 0.65f * flare, line, 1f, 0.9f - 0.6f * flare,
						0.85f - 0.65f * flare, line, 1f, 0.9f - 0.6f * flare, 0.85f - 0.65f * flare, line, 1f, 0.9f - 0.6f * flare, 0.85f - 0.65f * flare, line);
			}
		});
	}

	/** Blood Whirl: a ring of blood swept round him twice, its head bright, its tail thinning out behind. */
	private static void drawWhirl(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Fx fx, Vec3 c, float age) {
		if (age > UndeadKitConfig.K_WHIRL_TICKS + 4) {
			return;
		}
		float fade = Mth.clamp(1f - (age - UndeadKitConfig.K_WHIRL_TICKS) / 4f, 0f, 1f);
		float head = whirlAngle(fx, age);
		float tail = 3.2f * fade;
		int n = 30;
		float r = 2.3f;
		Vec3[] lo = new Vec3[n + 1], hi = new Vec3[n + 1], e = new Vec3[n + 1];
		float[] u = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			float k = i / (float) n;
			float ang = head - fx.side * tail * (1f - k);
			Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
			float w = 0.15f + 0.6f * k * k;
			float wob = Mth.sin(ang * 2f) * 0.12f;
			lo[i] = c.add(out.scale(r - w * 0.4)).add(0, -w * 0.5 + wob, 0);
			hi[i] = c.add(out.scale(r + w * 0.25)).add(0, w * 0.5 + wob, 0);
			e[i] = c.add(out.scale(r + w * 0.15)).add(0, w * 0.2 + wob, 0);
			u[i] = k;
		}
		for (int i = 0; i < n; i++) {
			float a0 = u[i] * fade, a1 = u[i + 1] * fade;
			band(body, m, lo[i], lo[i + 1], hi[i + 1], hi[i], 0.35f, 0.0f, 0.03f, 0.85f * a0, 0.35f, 0.0f, 0.03f, 0.85f * a1, 0.88f, 0.08f, 0.1f,
					0.95f * a0, 0.88f, 0.08f, 0.1f, 0.95f * a1);
		}
		glow.add(() -> {
			VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			for (int i = 0; i < n; i++) {
				float a0 = u[i] * u[i] * fade, a1 = u[i + 1] * u[i + 1] * fade;
				band(add, m, e[i], e[i + 1], hi[i + 1], hi[i], 1f, 0.5f, 0.4f, a0, 1f, 0.5f, 0.4f, a1, 1f, 0.9f, 0.8f, a0, 1f, 0.9f, 0.8f, a1);
			}
		});
	}

	/** A flat ring in the plane of {@code a} and {@code b} round {@code c}: dark red, a pale glowing inner rim. */
	static void ringAt(VertexConsumer body, List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Vec3 c, Vec3 a, Vec3 b, float r, float w, float alpha) {
		int n = 32;
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

	/** A four-pointed star flash facing the camera. */
	static void star(List<Runnable> glow, MultiBufferSource buffers, Matrix4f m, Quaternionf face, Vec3 at, float age, float size) {
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
	static void band(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float r0, float g0, float b0, float a0, float r1, float g1,
			float b1, float a1, float r3, float g3, float b3, float a3, float r2, float g2, float b2, float a2) {
		v(vc, m, p0, r0, g0, b0, a0);
		v(vc, m, p1, r1, g1, b1, a1);
		v(vc, m, p2, r2, g2, b2, a2);
		v(vc, m, p3, r3, g3, b3, a3);
		v(vc, m, p3, r3, g3, b3, a3);
		v(vc, m, p2, r2, g2, b2, a2);
		v(vc, m, p1, r1, g1, b1, a1);
		v(vc, m, p0, r0, g0, b0, a0);
	}

	/** One colour for a whole quad. */
	static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float r, float g, float b, float a) {
		band(vc, m, p0, p1, p2, p3, r, g, b, a, r, g, b, a, r, g, b, a, r, g, b, a);
	}

	private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
	}
}
