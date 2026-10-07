package com.projecthero.mod.gametest;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.ironman.IronManPassives;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManGroundPound;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManSuitCompare;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.11 Iron Man round: Haste by mark, the faceplate shutting on a melee hit, no more death recovery, the Mark 1
 * recipes built around iron armour, the full-sphere mob highlight, exact flight speeds, and the Mark 1 Strong Punch
 * splash + Shift+R Ground Pound.
 */
public class IronManV01511GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer suited(GameTestHelper h, String suitId, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getInventory().clearContent();
		BlockPos pos = h.absolutePos(rel);
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		p.setYRot(0f);
		p.setYHeadRot(0f);
		p.setXRot(0f);
		TonyStark.grant(p);
		wear(p, suitId);
		return p;
	}

	private static void wear(ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(suitId, t)));
		}
		TonyStark.setActiveSuit(p, suitId);
		IronManEnergy.setEnergy(p, suitId, IronManEnergy.capacity(suitId));
		IronManEnergy.setIntegrity(p, suitId, IronManEnergy.maxIntegrity(suitId));
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		h.getLevel().getServer().execute(() -> p.discard());
	}

	private static Zombie zombieAt(GameTestHelper h, Vec3 at) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie z = EntityType.ZOMBIE.create(h.getLevel());
		z.moveTo(at.x, at.y, at.z);
		z.setNoAi(true);
		// daylight sets a zombie alight even with no AI -- the burn read as a stray hit in CI (v0.15.13)
		z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
		h.getLevel().addFreshEntity(z);
		return z;
	}

	// ------------------------------------------------------------------ 1. Haste

	@GameTest(template = EMPTY_STRUCTURE)
	public void fullSuitGivesHasteByMark(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1", new BlockPos(1, 1, 1));
		String[] ids = { "mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii", "mark_8" };
		for (String id : ids) {
			wear(p, id);
			IronManPassives.tick(p);
			MobEffectInstance haste = p.getEffect(MobEffects.DIG_SPEED);
			int want = IronManSuits.byId(id).markNumber() <= 3 ? 0 : 1;
			h.assertTrue(haste != null && haste.getAmplifier() == want,
					id + ": Haste " + (want + 1) + " expected, got " + (haste == null ? "none" : "amp " + haste.getAmplifier()));
			h.assertFalse(haste.isVisible(), id + ": the Haste has no particles");
		}
		h.assertTrue(IronManPassives.hasteAmplifier(IronManSuits.MARK_2) == 0
				&& IronManPassives.hasteAmplifier(IronManSuits.MARK_III) == 0
				&& IronManPassives.hasteAmplifier(IronManSuits.MARK_4) == 1
				&& IronManPassives.hasteAmplifier(IronManSuits.MARK_8) == 1, "Mark 1-3 Haste I, Mark 4+ Haste II");
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); // no longer a full suit
		IronManPassives.tick(p);
		h.assertFalse(p.hasEffect(MobEffects.DIG_SPEED), "Haste goes the moment the suit is not complete");
		// someone else's Haste (a beacon, a potion) is never stripped
		wear(p, "mark_8");
		IronManPassives.tick(p);
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), ItemStack.EMPTY);
		}
		p.removeEffect(MobEffects.DIG_SPEED);
		p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 0));
		IronManPassives.tick(p);
		h.assertTrue(p.hasEffect(MobEffects.DIG_SPEED), "a potion's Haste survives the suit-off cleanup");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 2. faceplate shuts on a melee hit

	@GameTest(template = EMPTY_STRUCTURE)
	public void faceplateClosesOnRegularMeleeHit(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii", new BlockPos(1, 1, 1));
		Zombie z = zombieAt(h, p.position().add(0, 0, 2));
		p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
		ArmorStand stand = new ArmorStand(h.getLevel(), p.getX(), p.getY(), p.getZ() + 1);
		h.assertFalse(IronManFaceplate.onMeleeHit(p, stand), "hitting an armour stand does not count");
		h.assertTrue(IronManFaceplate.isOpen(p), "so the visor stays up");
		h.assertTrue(IronManFaceplate.onMeleeHit(p, z), "a melee hit on a mob closes it");
		h.assertFalse(IronManFaceplate.isOpen(p), "the faceplate is shut");
		h.assertFalse(IronManFaceplate.onMeleeHit(p, z), "already shut: nothing to do");
		// no Iron Man armour -> nothing
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), ItemStack.EMPTY);
		}
		p.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
		h.assertFalse(IronManFaceplate.onMeleeHit(p, z), "without the armour the hook does nothing");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 3. death no longer sends the suit home

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathNoLongerFliesTheSuitHome(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii", new BlockPos(5, 1, 5));
		h.getLevel().getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
				.set(false, h.getLevel().getServer());
		h.setBlock(new BlockPos(2, 2, 2), IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 2));
		be.bindTo(p.getUUID());
		boolean allowed = ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(p, p.damageSources().generic(), 100f);
		h.assertTrue(allowed, "nothing in the Iron Man kit cancels an ordinary death");
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"),
				"the suit stays on the body (it drops with the rest of the gear) -- it no longer flies itself home");
		h.assertTrue(be.isEmptyPlatform(), "and nothing was docked on the platform");
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 4. Mark 1 recipes need the iron armour

	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneRecipesNeedTheMatchingIronArmour(GameTestHelper h) {
		Item[] iron = { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS };
		for (int i = 0; i < TYPES.length; i++) {
			String id = "iron_man_mark_1_" + TYPES[i].getName();
			Recipe<?> r = h.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(id)).map(x -> (Recipe<?>) x.value()).orElse(null);
			h.assertTrue(r != null, id + " exists");
			h.assertTrue(r.getResultItem(h.getLevel().registryAccess()).is(IronManItems.armor("mark_1", TYPES[i])), id + " result");
			Set<Item> armourIn = new HashSet<>();
			int ironPieces = 0;
			for (Ingredient ing : r.getIngredients()) {
				for (ItemStack opt : ing.getItems()) {
					for (Item piece : iron) {
						if (opt.is(piece)) {
							armourIn.add(piece);
							ironPieces++;
						}
					}
				}
			}
			h.assertTrue(armourIn.size() == 1 && armourIn.contains(iron[i]) && ironPieces == 1,
					id + " needs exactly one " + iron[i] + ", got " + armourIn);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ 5. full-sphere highlight

	@GameTest(template = EMPTY_STRUCTURE)
	public void mobHighlightCoversAFullSphere(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2", new BlockPos(1, 1, 1)); // 25-block highlight
		p.teleportTo(p.getX(), p.getY() + 30, p.getZ()); // room for mobs 24 blocks below without leaving the world
		TonyStarkState s = TonyStark.state(p).copy();
		s.mobHighlightOn = true;
		p.setAttached(ModAttachments.TONY_STARK_STATE, s);
		double range = IronManSuits.MARK_2.targetScanRange();
		Vec3 c = IronManHighlight.sphereCentre(p);
		Zombie above = zombieAt(h, c.add(0, 22, 0));
		Zombie below = zombieAt(h, c.add(0, -24, 0));
		Zombie diagonal = zombieAt(h, c.add(12, 12, 12)); // ~20.8 blocks out, up and to the side
		Zombie tooFar = zombieAt(h, c.add(16, 16, 16)); // ~27.7 blocks out (body ~26.5)
		Zombie farAbove = zombieAt(h, c.add(0, 27, 0));
		for (Zombie z : new Zombie[] { above, below, diagonal }) {
			h.assertTrue(IronManHighlight.outlines(p, z), "outlined at " + z.position().subtract(c) + " (range " + range + ")");
		}
		h.assertFalse(IronManHighlight.outlines(p, tooFar), "a mob outside the sphere is not outlined");
		h.assertFalse(IronManHighlight.outlines(p, farAbove), "a mob 27 blocks straight up is outside the 25-block sphere");
		var listed = IronManHighlight.outlinedAround(p, range);
		h.assertTrue(listed.contains(above) && listed.contains(below) && listed.contains(diagonal)
				&& !listed.contains(tooFar) && !listed.contains(farAbove), "the cube search + sphere test finds the same set");
		for (Zombie z : new Zombie[] { above, below, diagonal, tooFar, farAbove }) {
			z.discard();
		}
		leave(h, p);
		h.succeed();
	}

	// ------------------------------------------------------------------ 6. exact flight speeds

	/** Fly the client's directional-flight model level along +Z for 4 s and measure the last second (blocks/s). */
	private static double measuredSpeed(IronManSuit suit, boolean sprint) {
		boolean sprintFly = sprint && suit.sprintFlight();
		DirectionalFlightModel.Tune tune = DirectionalFlightModel.ironManSuit(suit.flightSpeed(), suit.flightAcceleration(),
				suit.maxFlightSpeedMps(), suit.flightCruiseFor(sprintFly), sprintFly, false, 1.0);
		Vec3 look = new Vec3(0, 0, 1);
		Vec3 v = Vec3.ZERO;
		Vec3 pos = Vec3.ZERO;
		Vec3 at60 = null;
		for (int tick = 1; tick <= 80; tick++) {
			Vec3 wanted = DirectionalFlightModel.wantedVelocity(look, 0f, 1f, 0f, 0, tune);
			v = DirectionalFlightModel.step(v, wanted, true, tune, false);
			pos = pos.add(v); // the client moves the player by exactly this velocity each tick
			if (tick == 60) {
				at60 = pos;
			}
		}
		return pos.subtract(at60).length(); // 20 ticks = one second
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightSpeedsAreExactBlocksPerSecond(GameTestHelper h) {
		check(h, IronManSuits.MARK_2, 15.0, 30.0);
		for (IronManSuit s : new IronManSuit[] { IronManSuits.MARK_III, IronManSuits.MARK_4, IronManSuits.MARK_V,
				IronManSuits.MARK_6, IronManSuits.MARK_VII, IronManSuits.MARK_8 }) {
			check(h, s, 18.0, 35.0);
		}
		// the Mark 1 burst is untouched: 8 b/s, no sprint flight
		h.assertTrue(Math.abs(measuredSpeed(IronManSuits.MARK_1, true) - 8.0) < 0.05, "Mark 1 burst still 8 b/s");
		h.succeed();
	}

	private static void check(GameTestHelper h, IronManSuit s, double normal, double sprint) {
		double n = measuredSpeed(s, false);
		double sp = measuredSpeed(s, true);
		h.assertTrue(Math.abs(n - normal) < 0.05, s.id() + " flies " + n + " b/s, want " + normal);
		h.assertTrue(Math.abs(sp - sprint) < 0.05, s.id() + " sprint-flies " + sp + " b/s, want " + sprint);
		h.assertTrue(Math.abs(IronManSuitCompare.flightSpeed(s) - sprint) < 0.05,
				s.id() + " compare panel top speed " + IronManSuitCompare.flightSpeed(s));
	}

	// ------------------------------------------------------------------ 7. Strong Punch reach + splash

	@GameTest(template = EMPTY_STRUCTURE)
	public void strongPunchReachesFiveBlocksAndSplashes(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1", new BlockPos(3, 1, 0));
		Vec3 eyeAhead = new Vec3(p.getX(), p.getY(), p.getZ());
		Zombie target = zombieAt(h, eyeAhead.add(0, 0, 4.6)); // past the old 4-block reach
		Zombie beside = zombieAt(h, eyeAhead.add(1.4, 0, 4.6)); // 1.4 blocks from the impact
		Zombie away = zombieAt(h, eyeAhead.add(3.6, 0, 6.0)); // well outside the 2-block splash
		h.runAfterDelay(2, () -> {
			float hp = target.getMaxHealth();
			IronManAbilities.strongPunch(p, IronManSuits.MARK_1);
			h.assertTrue(target.getHealth() <= hp - 14f, // 15 less the zombie's 2 armour points
					"the punch lands at 4.6 blocks, health " + target.getHealth());
			float splash = IronManAbilities.PUNCH_DAMAGE * IronManAbilities.PUNCH_SPLASH_FRACTION;
			h.assertTrue(Math.abs(beside.getHealth() - (hp - splash)) < 0.6f, // armour shaves ~0.1
					"the zombie beside it takes the splash (" + splash + "), health " + beside.getHealth());
			h.assertTrue(beside.getDeltaMovement().horizontalDistance() > 0.05, "and is knocked away");
			h.assertTrue(away.getHealth() == hp, "a zombie outside the splash is untouched");
			h.assertTrue(IronManAbilities.PUNCH_RANGE == 5.0 && IronManAbilities.PUNCH_SPLASH_RADIUS == 2.0,
					"reach 5, splash radius 2");
			target.discard();
			beside.discard();
			away.discard();
			leave(h, p);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ 8. Shift+R Ground Pound

	@GameTest(template = EMPTY_STRUCTURE)
	public void groundPoundHitsFiveBlocksAroundAndPops(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		ServerPlayer p = suited(h, "mark_1", new BlockPos(1, 1, 1));
		Vec3 at = p.position();
		Zombie near = zombieAt(h, at.add(1.5, 0, 1.5));
		Zombie edge = zombieAt(h, at.add(0, 0, 4.4));
		Zombie out = zombieAt(h, at.add(5.6, 0, 5.6)); // ~7.9 blocks away
		h.runAfterDelay(2, () -> {
			float hp = near.getMaxHealth();
			float e0 = IronManEnergy.energy(p, "mark_1");
			p.setShiftKeyDown(true);
			IronManAbilities.trigger(p, IronManSuits.MARK_1, 1, true); // Shift + R
			p.setShiftKeyDown(false);
			h.assertTrue(Math.abs(e0 - IronManEnergy.energy(p, "mark_1") - IronManGroundPound.ENERGY_COST) < 0.01f,
					"the pound costs " + IronManGroundPound.ENERGY_COST + " (not the punch's 10)");
			h.assertFalse(TonyStark.abilityReady(p, "mark_1", IronManGroundPound.ABILITY_ID), "the pound is on cooldown");
			h.assertTrue(TonyStark.abilityReady(p, "mark_1", IronManAbilities.STRONG_PUNCH), "the punch is not");
			h.assertTrue(near.getHealth() < hp - 8f, "the close zombie takes most of the 12, health " + near.getHealth());
			h.assertTrue(edge.getHealth() < hp - 5f && edge.getHealth() > near.getHealth(),
					"the zombie at the edge takes less, health " + edge.getHealth());
			h.assertTrue(near.getDeltaMovement().y > 0.3 && edge.getDeltaMovement().y > 0.3, "both are popped up");
			Vec3 push = near.getDeltaMovement();
			h.assertTrue(push.x > 0.2 && push.z > 0.2, "and thrown outward, got " + push);
			h.assertTrue(out.getHealth() == hp && out.getLastHurtByMob() != p,
					"a zombie 8 blocks away is untouched, health " + out.getHealth() + ", fire " + out.getRemainingFireTicks());
			float e1 = IronManEnergy.energy(p, "mark_1");
			p.setShiftKeyDown(true);
			IronManAbilities.trigger(p, IronManSuits.MARK_1, 1, true);
			p.setShiftKeyDown(false);
			h.assertTrue(IronManEnergy.energy(p, "mark_1") == e1, "a second pound on cooldown costs nothing");
			near.discard();
			edge.discard();
			out.discard();
			leave(h, p);
			h.succeed();
		});
	}
}
