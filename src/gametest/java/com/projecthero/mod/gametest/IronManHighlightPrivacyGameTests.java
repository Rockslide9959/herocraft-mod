package com.projecthero.mod.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManHighlight;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.ability.IronManMark6;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.network.IronManLockPayload;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;

/**
 * v0.15.4 -- THE IRON MAN HIGHLIGHT IS PRIVATE (the user has had to ask for this repeatedly; see {@link IronManHighlight}).
 * Using the suit's highlight, JARVIS scan, flares and the targeting lock on a mob must never give it the vanilla
 * {@code GLOWING} effect, the glowing tag or the shared glowing flag (all of which reach every tracking player), must not
 * make the suit's owner glow either, and must send nothing about it to anyone else -- a squad mate standing right there
 * gets no lock payload, no glowing entity data and not even the highlight's on/off state.
 */
public class IronManHighlightPrivacyGameTests implements FabricGameTest {
	private static final int GLOWING_FLAG = 6;

	private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0f, 0f);
		m.setNoAi(true);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	/** The raw shared-flags byte (data slot 0) -- what clients read for "is this entity outlined". */
	@SuppressWarnings("unchecked")
	private static boolean sharedGlowFlag(Entity e) {
		try {
			Field f = Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
			f.setAccessible(true);
			var accessor = (net.minecraft.network.syncher.EntityDataAccessor<Byte>) f.get(null);
			return (e.getEntityData().get(accessor) & (1 << GLOWING_FLAG)) != 0;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("cannot read the shared flags", ex);
		}
	}

	/** The mock player's embedded channel, whose outbound queue holds every packet the server sent them. */
	private static io.netty.channel.embedded.EmbeddedChannel channel(ServerPlayer p) {
		try {
			Field cf = net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
			cf.setAccessible(true);
			Connection c = (Connection) cf.get(p.connection);
			Field chf = Connection.class.getDeclaredField("channel");
			chf.setAccessible(true);
			return (io.netty.channel.embedded.EmbeddedChannel) chf.get(c);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("cannot reach the mock connection", ex);
		}
	}

	/** Everything sent to {@code p} since the last drain (bundles flattened). */
	private static List<Packet<?>> drain(ServerPlayer p) {
		io.netty.channel.embedded.EmbeddedChannel ch = channel(p);
		ch.flushOutbound();
		List<Packet<?>> out = new ArrayList<>();
		Object o;
		while ((o = ch.readOutbound()) != null) {
			if (o instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(out::add);
			} else if (o instanceof Packet<?> pk) {
				out.add(pk);
			}
		}
		return out;
	}

	private static boolean containsLockPayload(List<Packet<?>> packets) {
		return packets.stream().anyMatch(pk -> pk instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof IronManLockPayload);
	}

	/** Any entity-data packet for {@code e} that switches its glowing bit on. */
	private static boolean sentGlowFor(List<Packet<?>> packets, Entity e) {
		for (Packet<?> pk : packets) {
			if (pk instanceof ClientboundSetEntityDataPacket d && d.id() == e.getId()) {
				for (SynchedEntityData.DataValue<?> v : d.packedItems()) {
					if (v.id() == 0 && v.value() instanceof Byte b && (b & (1 << GLOWING_FLAG)) != 0) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void assertNotGlowing(GameTestHelper h, LivingEntity e, String what) {
		h.assertFalse(e.hasEffect(MobEffects.GLOWING), what + " must never get the vanilla Glowing effect");
		h.assertFalse(e.hasGlowingTag(), what + " must never get the glowing tag");
		h.assertFalse(e.isCurrentlyGlowing(), what + " must not be glowing server-side");
		h.assertFalse(sharedGlowFlag(e), what + " must not carry the shared glowing flag (it is synced to everyone)");
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ironManHighlightNeverLeavesTheWearer(GameTestHelper h) {
		ServerPlayer owner = IronManMk67V0154GameTests.suited(h, IronManMark6.SUIT_ID);
		ServerPlayer mate = h.makeMockServerPlayerInLevel();
		mate.teleportTo(owner.getX() + 1.5, owner.getY(), owner.getZ());
		SquadManager squads = SquadManager.get(h.getLevel().getServer());
		Squad squad = squads.create("hl" + owner.getUUID().toString().substring(0, 8), owner.getUUID());
		squads.addMember(squad, mate.getUUID());

		Zombie zombie = mob(h, EntityType.ZOMBIE, owner, 0.0, 5.0); // straight under the owner's crosshair
		Cow cow = mob(h, EntityType.COW, owner, 3.0, 4.0);
		h.assertFalse(owner.hasEffect(MobEffects.GLOWING), "being granted the power no longer makes the owner glow");
		// start capturing now: the server's own player tick runs the suit ticker too, so the first lock can land on the
		// very next tick -- everything either player is sent from here on is checked at the end
		drain(owner);
		drain(mate);

		h.runAfterDelay(2, () -> {
			// the suit's systems: auto highlight + targeting lock (ticker), JARVIS scan, flares
			for (int i = 0; i < 6; i++) {
				IronManSuitTicker.tick(owner);
			}
			owner.setShiftKeyDown(true);
			IronManAbilityManager.handle(owner, AbilitySlot.SLOT_3, true); // Shift+X: JARVIS scan
			IronManAbilityManager.handle(owner, AbilitySlot.SLOT_3, false);
			owner.setShiftKeyDown(false);
			IronManAbilityManager.handle(owner, AbilitySlot.SLOT_3, true); // X: flares
			IronManAbilityManager.handle(owner, AbilitySlot.SLOT_3, false);
			IronManSuitTicker.tick(owner);

			TonyStarkState st = TonyStark.state(owner);
			h.assertTrue(st.mobHighlightOn, "precondition: the owner's highlight is on");
			h.assertTrue(IronManTargeting.locked(owner) == zombie, "precondition: the owner has the zombie locked");
			h.assertTrue(owner.getAttachedOrElse(ModAttachments.IRON_MAN_HIGHLIGHT_ON, false),
					"the owner is told their own highlight is on (wearer-only attachment)");
			// the owner's OWN view would outline both; nobody else's ever does
			h.assertTrue(IronManHighlight.outlines(owner, zombie) && IronManHighlight.outlines(owner, cow),
					"the owner's own client view outlines the mobs");
			h.assertFalse(IronManHighlight.outlines(mate, zombie) || IronManHighlight.outlines(mate, cow),
					"a squad mate's view never outlines them");
			h.assertTrue(IronManHighlight.decision(mate, zombie) == null, "the squad mate's view leaves the zombie to vanilla");
			// and nothing answers for a SERVER-side entity -- the integrated-server leak
			h.assertFalse(IronManHighlight.mayDecide(zombie) || IronManHighlight.mayDecide(cow),
					"the highlight never decides for a server-side entity (that copy's glow flag is broadcast)");
		});

		// let the mobs tick (vanilla re-derives the shared glowing flag in tickEffects) and the trackers flush
		h.runAfterDelay(8, () -> {
			assertNotGlowing(h, zombie, "the scanned / locked / flared zombie");
			assertNotGlowing(h, cow, "the highlighted cow");
			h.assertFalse(owner.hasEffect(MobEffects.GLOWING), "the owner never glows");
			h.assertFalse(mate.hasEffect(MobEffects.GLOWING), "the squad mate never glows");

			List<Packet<?>> toOwner = drain(owner);
			List<Packet<?>> toMate = drain(mate);
			h.assertTrue(containsLockPayload(toOwner), "control: the capture sees the owner's own lock payload");
			h.assertFalse(containsLockPayload(toMate), "the squad mate is never sent the owner's targeting lock");
			h.assertFalse(sentGlowFor(toMate, zombie) || sentGlowFor(toMate, cow), "the squad mate is never sent a glowing mob");
			h.assertFalse(sentGlowFor(toOwner, zombie) || sentGlowFor(toOwner, cow),
					"not even the owner is sent a server-side glow (their outline is purely client-side)");
			// the all-players state sync never carries the highlight; the mate's copy of the owner reads it as off
			TonyStarkState synced = TonyStarkState.forSync(TonyStark.state(owner));
			h.assertFalse(synced.mobHighlightOn, "the state everyone else receives has the highlight masked off");
			h.assertTrue(TonyStark.state(owner).mobHighlightOn, "while the owner's server state keeps it on");
			h.assertFalse(mate.getAttachedOrElse(ModAttachments.IRON_MAN_HIGHLIGHT_ON, false), "the mate has no highlight of their own");

			squads.disband(squad);
			zombie.discard();
			cow.discard();
			h.getLevel().getServer().getPlayerList().remove(owner);
			h.getLevel().getServer().getPlayerList().remove(mate);
			h.succeed();
		});
	}
}
