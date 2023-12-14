package dev.ftb.mods.ftbessentials.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ftb.mods.ftbessentials.FTBEssentialsPlatform;
import dev.ftb.mods.ftbessentials.config.FTBEConfig;
import dev.ftb.mods.ftbessentials.util.DurationInfo;
import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import dev.ftb.mods.ftbessentials.util.FTBEWorldData;
import dev.ftb.mods.ftbessentials.util.OtherPlayerInventory;
import dev.ftb.mods.ftblibrary.util.PlayerDisplayNameUtil;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * @author LatvianModder
 */
public class CheatCommands {
	private static final UUID ESSENTIALS_SPEED_UUID = UUID.fromString("3a8a9187-94ab-4272-99c0-ca764a19f8f1");

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        /*
		killall
		dumpchunkloaders
		 */

		if (FTBEConfig.HEAL.isEnabled()) {
			dispatcher.register(literal("heal")
					.requires(FTBEConfig.HEAL.enabledAndOp())
					.executes(context -> heal(context.getSource().getPlayerOrException()))
					.then(argument("player", EntityArgument.player())
							.executes(context -> heal(EntityArgument.getPlayer(context, "player")))
					)
			);
		}
		if (FTBEConfig.FEED.isEnabled()) {
			dispatcher.register(literal("feed")
					.requires(FTBEConfig.FEED.enabledAndOp())
					.executes(context -> feed(context.getSource().getPlayerOrException()))
					.then(argument("player", EntityArgument.player())
							.executes(context -> feed(EntityArgument.getPlayer(context, "player")))
					)
			);
		}
		if (FTBEConfig.EXTINGUISH.isEnabled()) {
			dispatcher.register(literal("extinguish")
					.requires(FTBEConfig.EXTINGUISH.enabledAndOp())
					.executes(context -> extinguish(context.getSource().getPlayerOrException()))
					.then(argument("player", EntityArgument.player())
							.executes(context -> extinguish(EntityArgument.getPlayer(context, "player")))
					)
			);
		}

		if (FTBEConfig.FLY.isEnabled()) {
			dispatcher.register(literal("fly")
					.requires(FTBEConfig.FLY.enabledAndOp())
					.executes(context -> fly(context.getSource().getPlayerOrException()))
					.then(argument("player", EntityArgument.player())
							.executes(context -> fly(EntityArgument.getPlayer(context, "player")))
					)
			);
		}

		if (FTBEConfig.SPEED.isEnabled()) {
			dispatcher.register(literal("speed")
					.executes(context -> speed(context.getSource(), Attributes.MOVEMENT_SPEED, context.getSource().getPlayerOrException()))
					.then(argument("boost_percent", IntegerArgumentType.integer(-100, 2000))
							.requires(FTBEConfig.SPEED.enabledAndOp())
							.executes(context -> speed(context.getSource(), Attributes.MOVEMENT_SPEED, context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "boost_percent")))
							.then(argument("player", EntityArgument.player())
									.executes(context -> speed(context.getSource(), Attributes.MOVEMENT_SPEED, EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "boost_percent")))
							)
					)
			);
		}

		if (FTBEConfig.GOD.isEnabled()) {
			dispatcher.register(literal("god")
					.requires(FTBEConfig.GOD.enabledAndOp())
					.executes(context -> god(context.getSource().getPlayerOrException()))
					.then(argument("player", EntityArgument.player())
							.executes(context -> god(EntityArgument.getPlayer(context, "player")))
					)
			);
		}

		if (FTBEConfig.INVSEE.isEnabled()) {
			dispatcher.register(literal("invsee")
					.requires(FTBEConfig.INVSEE.enabledAndOp())
					.then(argument("player", EntityArgument.player())
							.executes(context -> viewInventory(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))
					)
			);
		}

		if (FTBEConfig.NICK.isEnabled()) {
			dispatcher.register(literal("nicknamefor")
					.requires(FTBEConfig.NICK.enabledAndOp())
					.then(argument("player", EntityArgument.player())
							.requires(source -> source.hasPermission(2))
							.executes(context -> nicknameFor(context.getSource(), EntityArgument.getPlayer(context, "player"), ""))
							.then(argument("nickname", StringArgumentType.greedyString())
									.requires(source -> source.hasPermission(2))
									.executes(context -> nicknameFor(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "nickname")))
							)
					)
			);
		}

		if (FTBEConfig.MUTE.isEnabled()) {
			dispatcher.register(literal("mute")
					.requires(FTBEConfig.MUTE.enabledAndOp())
					.then(argument("player", EntityArgument.player())
							.executes(context -> mute(context.getSource(), EntityArgument.getPlayer(context, "player"), ""))
							.then(argument("until", StringArgumentType.greedyString())
									.suggests((context, builder) -> FTBEssentialsCommands.suggestDurations(builder))
									.executes(context -> mute(context.getSource(), EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "until")))
							)
					)
			);
			dispatcher.register(literal("unmute")
					.requires(FTBEConfig.MUTE.enabledAndOp())
					.then(argument("player", EntityArgument.player())
							.executes(context -> unmute(context.getSource(), EntityArgument.getPlayer(context, "player")))
					)
			);
		}

		if (FTBEConfig.TP_OFFLINE.isEnabled()) {
			dispatcher.register(literal("tp_offline")
					.requires(FTBEConfig.TP_OFFLINE.enabledAndOp())
					.then(literal("name")
							.then(argument("player", StringArgumentType.word())
									.then(argument("pos", Vec3Argument.vec3())
											.executes(ctx -> tpOffline(ctx.getSource(), StringArgumentType.getString(ctx,"player"), ctx.getSource().getLevel(), Vec3Argument.getCoordinates(ctx, "pos")))
									)
							)
					)
					.then(literal("id")
							.then(argument("player_id", UuidArgument.uuid())
									.then(argument("pos", Vec3Argument.vec3())
											.executes(ctx -> tpOffline(ctx.getSource(), UuidArgument.getUuid(ctx,"player_id"), ctx.getSource().getLevel(), Vec3Argument.getCoordinates(ctx, "pos")))
									)
							)
					)
			);
		}
	}

	private static int speed(CommandSourceStack source, Attribute attr, ServerPlayer player) {
		AttributeInstance attrInstance = player.getAttribute(attr);

		showSpeed(source, player, attrInstance);

		return 1;
	}

	private static int speed(CommandSourceStack source, Attribute attr, ServerPlayer target, int boostPct) {
		AttributeInstance attrInstance = target.getAttribute(attr);

		if (attrInstance != null) {
			float speedMult = boostPct / 100f;
			attrInstance.removeModifier(ESSENTIALS_SPEED_UUID);
			if (speedMult != 0f) {
				attrInstance.addPermanentModifier(new AttributeModifier(ESSENTIALS_SPEED_UUID,
						"FTB Essentials speed boost", speedMult, AttributeModifier.Operation.MULTIPLY_BASE
				));
			}
			showSpeed(source, target, attrInstance);
		}

		return 1;
	}

	private static void showSpeed(CommandSourceStack source, ServerPlayer target, AttributeInstance attrInstance) {
		Component msg;
		if (attrInstance != null && attrInstance.getModifier(ESSENTIALS_SPEED_UUID) != null) {
			double speedMult = attrInstance.getModifier(ESSENTIALS_SPEED_UUID).getAmount();
			int boostPct = (int) (speedMult * 100);
			msg = Component.literal("Speed boost for ")
					.append(target.getDisplayName())
					.append(" (").append(Component.translatable(attrInstance.getAttribute().getDescriptionId())).append(") = " + boostPct + "%");
		} else {
			msg = Component.literal("No speed boost for ").append(target.getDisplayName());
		}
		source.sendSuccess(() -> msg, false);
		if (!source.isPlayer() || source.getPlayer() != target) {
			target.displayClientMessage(msg, false);
		}
	}

	public static int heal(ServerPlayer player) {
		player.setHealth(player.getMaxHealth());
		player.getFoodData().eat(40, 40F);
		player.clearFire();
		FTBEssentialsPlatform.curePotionEffects(player);
		return 1;
	}

	public static int feed(ServerPlayer player) {
		player.getFoodData().eat(40, 40F);
		return 1;
	}

	public static int extinguish(ServerPlayer player) {
		player.clearFire();
		return 1;
	}

	public static int fly(ServerPlayer player) {
		return FTBEPlayerData.getOrCreate(player).map(data -> {
			var abilities = player.getAbilities();

			if (data.canFly()) {
				data.setCanFly(false);
				abilities.mayfly = false;
				abilities.flying = false;
				player.displayClientMessage(Component.literal("Flight disabled"), true);
			} else {
				data.setCanFly(true);
				abilities.mayfly = true;
				player.displayClientMessage(Component.literal("Flight enabled"), true);
			}

			player.onUpdateAbilities();
			return 1;
		}).orElse(0);
	}

	public static int god(ServerPlayer player) {
		return FTBEPlayerData.getOrCreate(player).map(data -> {
			var abilities = player.getAbilities();

			if (data.isGod()) {
				data.setGod(false);
				abilities.invulnerable = false;
				player.displayClientMessage(Component.literal("God mode disabled"), true);
			} else {
				data.setGod(true);
				abilities.invulnerable = true;
				player.displayClientMessage(Component.literal("God mode enabled"), true);
			}

			player.onUpdateAbilities();
			return 1;
		}).orElse(0);
	}

	public static int viewInventory(ServerPlayer source, ServerPlayer player) {
		source.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return player.getDisplayName();
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player p) {
				return new ChestMenu(MenuType.GENERIC_9x5, id, playerInventory, new OtherPlayerInventory(player), 5);
			}
		});

		return 1;
	}

	public static int nicknameFor(CommandSourceStack source, ServerPlayer player, String nick) {
		if (nick.length() > 30) {
			player.displayClientMessage(Component.literal("Nickname too long!"), false);
			return 0;
		}

		return FTBEPlayerData.getOrCreate(player).map(data -> {
			data.setNick(nick.trim());
			data.markDirty();
			PlayerDisplayNameUtil.refreshDisplayName(player);

			if (data.getNick().isEmpty()) {
				source.sendSuccess(() -> Component.literal("Nickname reset!"), true);
			} else {
				source.sendSuccess(() -> Component.literal("Nickname changed to '" + data.getNick() + "'"), true);
			}

			data.sendTabName(source.getServer());
			return 1;
		}).orElse(0);
	}

	public static int mute(CommandSourceStack source, ServerPlayer player, String duration) {
		return FTBEPlayerData.getOrCreate(player).map(data -> {
			try {
				DurationInfo info = DurationInfo.fromString(duration);
				data.setMuted(true);
				FTBEWorldData.instance.setMuteTimeout(player, info.until());

				MutableComponent msg = player.getDisplayName().copy()
						.append(" has been muted by ")
						.append(source.getDisplayName())
						.append(", ")
						.append(info.desc());
				notifyMuting(source, player, msg);

				return 1;
			} catch (IllegalArgumentException e) {
				source.sendFailure(Component.literal("Invalid duration syntax: '" + duration + "': " + e.getMessage()));
				return 0;
			}
		}).orElse(0);
	}

	public static int unmute(CommandSourceStack source, ServerPlayer player) {
		return FTBEPlayerData.getOrCreate(player).map(data -> {
			data.setMuted(false);
			FTBEWorldData.instance.setMuteTimeout(player, -1);

			MutableComponent msg = player.getDisplayName().copy()
					.append(" has been unmuted by ")
					.append(source.getDisplayName());
			notifyMuting(source, player, msg);

			return 1;
		}).orElse(0);
	}

	private static void notifyMuting(CommandSourceStack source, Player target, Component msg) {
		// notify any online ops, plus the player being (un)muted
		source.getServer().getPlayerList().getPlayers().forEach(p -> {
			if (p.hasPermissions(2) || p == target) {
				p.displayClientMessage(msg, false);
			}
		});
		// notify command sender if not actually a player
		if (!source.isPlayer()) {
			source.sendSuccess(() -> msg, true);
		}
	}

	private static int tpOffline(CommandSourceStack source, String playerName, ServerLevel level, Coordinates dest) {
		source.getServer().getProfileCache().getAsync(playerName).whenComplete((profileOpt, throwable) -> {
			source.getServer().executeIfPossible(() ->
					profileOpt.ifPresentOrElse(profile -> tpOffline(source, profile.getId(), level, dest),
							() -> source.sendFailure(Component.literal("Unknown player: " + playerName))
					)
			);
		});

		return 1;
	}

	private static int tpOffline(CommandSourceStack source, UUID playerId, ServerLevel level, Coordinates dest) {
		MinecraftServer server = source.getServer();

		Path playerDir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
		Path datFile = playerDir.resolve(playerId + ".dat");

		if (server.getPlayerList().getPlayer(playerId) != null) {
			source.sendFailure(Component.literal("Player is online! Use regular /tp command instead"));
			return 0;
		}

		try {
			CompoundTag tag = NbtIo.readCompressed(datFile, NbtAccounter.unlimitedHeap());

			Vec3 vec = dest.getPosition(source);
			ListTag newPos = new ListTag();
			newPos.add(DoubleTag.valueOf(vec.x));
			newPos.add(DoubleTag.valueOf(vec.y));
			newPos.add(DoubleTag.valueOf(vec.z));
			tag.put("Pos", newPos);

			tag.putString("Dimension", level.dimension().location().toString());

			Path tempFile = File.createTempFile(playerId + "-", ".dat", playerDir.toFile()).toPath();
			NbtIo.writeCompressed(tag, tempFile);
			Path backupFile = playerDir.resolve(playerId + ".dat_old");
			Util.safeReplaceFile(datFile, tempFile, backupFile);

			source.sendSuccess(() -> Component.literal(String.format("Offline player %s moved to [%.2f,%.2f,%.2f] in %s",
					playerId, vec.x, vec.y, vec.z, source.getLevel().dimension().location())), false);
			return 1;
		} catch (IOException e) {
			source.sendFailure(Component.literal("Can't update dat file: " + e.getMessage()));
			return 0;
		}
	}
}
