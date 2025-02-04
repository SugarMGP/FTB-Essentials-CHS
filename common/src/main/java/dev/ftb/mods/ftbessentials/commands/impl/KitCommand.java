package dev.ftb.mods.ftbessentials.commands.impl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.ftb.mods.ftbessentials.commands.CommandUtils;
import dev.ftb.mods.ftbessentials.commands.FTBCommand;
import dev.ftb.mods.ftbessentials.config.FTBEConfig;
import dev.ftb.mods.ftbessentials.kit.Kit;
import dev.ftb.mods.ftbessentials.kit.KitManager;
import dev.ftb.mods.ftbessentials.util.BlockUtil;
import dev.ftb.mods.ftbessentials.util.DurationInfo;
import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import dev.ftb.mods.ftbessentials.util.InventoryUtil;
import dev.ftb.mods.ftblibrary.util.TimeUtils;
import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class KitCommand implements FTBCommand {
    @Override
    public boolean enabled() {
        return FTBEConfig.KIT.isEnabled();
    }

    @Override
    public List<LiteralArgumentBuilder<CommandSourceStack>> register() {
        return Collections.singletonList(literal("kit")
                .requires(CommandUtils.isGamemaster())
                .then(literal("create_from_player_inv")
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> createKitFromPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "", false))
                                .then(argument("cooldown", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> CommandUtils.suggestCooldowns(builder))
                                        .executes(ctx -> createKitFromPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "cooldown"), false))
                                )
                        )
                )
                .then(literal("create_from_player_hotbar")
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> createKitFromPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name"), "", true))
                                .then(argument("cooldown", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> CommandUtils.suggestCooldowns(builder))
                                        .executes(ctx -> createKitFromPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "cooldown"), true))
                                )
                        )
                )
                .then(literal("create_from_block_inv")
                        .then(argument("name", StringArgumentType.word())
                                .executes(ctx -> createKitFromBlock(ctx.getSource(), StringArgumentType.getString(ctx, "name"), ""))
                                .then(argument("cooldown", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> CommandUtils.suggestCooldowns(builder))
                                        .executes(ctx -> createKitFromBlock(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "cooldown")))
                                )
                        )
                )
                .then(literal("delete")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestKits(builder))
                                .executes(ctx -> deleteKit(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )
                .then(literal("list").executes(ctx -> listKits(ctx.getSource())))
                .then(literal("show")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestKits(builder))
                                .executes(ctx -> showKit(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )
                .then(literal("give")
                        .then(argument("players", EntityArgument.players())
                                .then(argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestKits(builder))
                                        .executes(ctx -> giveKit(ctx.getSource(), StringArgumentType.getString(ctx, "name"), EntityArgument.getPlayers(ctx, "players")))
                                )
                        )
                )
                .then(literal("put_in_block_inv")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestKits(builder))
                                .executes(ctx -> putKitInBlockInv(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )
                .then(literal("cooldown")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestKits(builder))
                                .then(argument("cooldown", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> CommandUtils.suggestCooldowns(builder))
                                        .executes(ctx -> modifyCooldown(ctx.getSource(), StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "cooldown")))
                                )
                        )
                )
                .then(literal("reset_cooldown")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestKits(builder))
                                .executes(ctx -> resetCooldowns(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> resetCooldowns(ctx.getSource(), StringArgumentType.getString(ctx, "name"), EntityArgument.getPlayer(ctx, "player")))
                                )
                                .then(argument("id", UuidArgument.uuid())
                                        .executes(ctx -> resetCooldowns(ctx.getSource(), StringArgumentType.getString(ctx, "name"), UuidArgument.getUuid(ctx, "id")))
                                )
                        )
                )
                .then(literal("set_autogrant")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestKits(builder))
                                .then(argument("grant", BoolArgumentType.bool())
                                        .executes(ctx -> modifyAutogrant(ctx.getSource(), StringArgumentType.getString(ctx, "name"), BoolArgumentType.getBool(ctx, "grant")))
                                )
                        )
                ));
    }

    private static int putKitInBlockInv(CommandSourceStack source, String kitName) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            BlockHitResult res = BlockUtil.getFocusedBlock(player, 5.5d).orElseThrow(() -> new IllegalArgumentException("没有看向方块"));
            Kit kit = KitManager.getInstance().get(kitName).orElseThrow(() -> new IllegalArgumentException("没有该套件: " + kitName));
            if (!InventoryUtil.putItemsInInventory(kit.getItems(), player.level(), res.getBlockPos(), res.getDirection())) {
                throw new RuntimeException("没有足够空间");
            }
            source.sendSuccess(() -> Component.literal("已将套件 '" + kitName + "' 中的物品添加到对应背包").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法将套件存入背包: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestKits(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(KitManager.getInstance().allKits().stream().map(Kit::getKitName).toList(), builder);
    }

    private static int createKitFromPlayer(CommandSourceStack source, String name, String cooldown, boolean hotbarOnly) {
        try {
            long secs = DurationInfo.getSeconds(cooldown);
            KitManager.getInstance().createFromPlayerInv(name, source.getPlayerOrException(), secs, hotbarOnly);
            source.sendSuccess(() -> Component.literal("套件 '" + name + "' 已创建").withStyle(ChatFormatting.YELLOW), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法创建套件: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int createKitFromBlock(CommandSourceStack source, String name, String cooldown) {
        try {
            long secs = DurationInfo.getSeconds(cooldown);
            ServerPlayer player = source.getPlayerOrException();
            BlockHitResult res = BlockUtil.getFocusedBlock(player, 5.5d).orElseThrow(() -> new IllegalArgumentException("没有看向方块"));

            KitManager.getInstance().createFromBlockInv(name, player.level(), res.getBlockPos(), res.getDirection(), secs);
            source.sendSuccess(() -> Component.literal("套件 '" + name + "' 已创建").withStyle(ChatFormatting.YELLOW), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法创建套件: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int giveKit(CommandSourceStack source, String name, Collection<ServerPlayer> players) {
        try {
            players.forEach(player -> KitManager.getInstance().giveKitToPlayer(name, player));
            source.sendSuccess(() -> Component.literal("套件 '" + name + "' 已经给予 " + players.size() + " 个玩家").withStyle(ChatFormatting.YELLOW), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法将套件给予玩家: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int listKits(CommandSourceStack source) {
        Collection<Kit> kits = KitManager.getInstance().allKits();

        source.sendSuccess(() -> Component.literal(kits.size() + " 个套件").withStyle(ChatFormatting.AQUA), false);
        kits.stream().sorted(Comparator.comparing(Kit::getKitName))
                .forEach(kit -> source.sendSuccess(() -> Component.literal("• " + kit.getKitName()).withStyle(Style.EMPTY
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/kit show " + kit.getKitName()))
                ), false));
        return 1;
    }

    private static int showKit(CommandSourceStack source, String kitName) {
        KitManager.getInstance().get(kitName).ifPresentOrElse(kit -> {
            source.sendSuccess(() -> Component.literal(Strings.repeat('-', 40)).withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> Component.literal("套件名称: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(kit.getKitName()).withStyle(ChatFormatting.YELLOW)), false);
            if (kit.getCooldown() > 0L) {
                source.sendSuccess(() -> Component.literal("  冷却: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(TimeUtils.prettyTimeString(kit.getCooldown())).withStyle(ChatFormatting.YELLOW)), false);
            } else if (kit.getCooldown() == 0L) {
                source.sendSuccess(() -> Component.literal("  没有冷却").withStyle(ChatFormatting.AQUA), false);
            } else {
                source.sendSuccess(() -> Component.literal("  一次性使用").withStyle(ChatFormatting.AQUA), false);
            }
            if (kit.isAutoGrant()) {
                source.sendSuccess(() -> Component.literal("  玩家登录时自动授予").withStyle(ChatFormatting.AQUA), false);
            }
            source.sendSuccess(() -> Component.literal("  物品:").withStyle(ChatFormatting.AQUA), false);
            for (ItemStack stack : kit.getItems()) {
                source.sendSuccess(()-> Component.literal("  • ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(stack.getCount() + " x ").withStyle(ChatFormatting.WHITE))
                        .append(stack.getDisplayName()), false);
            }
        }, () -> source.sendFailure(Component.literal("没有该套件: " + kitName).withStyle(ChatFormatting.RED)));
        return 1;
    }

    private static int deleteKit(CommandSourceStack source, String kitName) {
        try {
            KitManager.getInstance().deleteKit(kitName);
            source.sendSuccess(() -> Component.literal("套件 '" + kitName + "' 已删除").withStyle(ChatFormatting.YELLOW), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法删除套件 '" + kitName + "': " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private static int modifyAutogrant(CommandSourceStack source, String kitName, boolean grant) {
        KitManager.getInstance().get(kitName).ifPresentOrElse(kit -> {
            KitManager.getInstance().addKit(kit.withAutoGrant(grant), true);
            source.sendSuccess(() -> Component.literal("套件 '" + kitName + "' 自动授予已修改: " + grant).withStyle(ChatFormatting.YELLOW), false);
        }, () -> source.sendFailure(Component.literal("没有该套件: " + kitName).withStyle(ChatFormatting.RED)));

        return 1;
    }

    private static int modifyCooldown(CommandSourceStack source, String kitName, String cooldown) {
        KitManager.getInstance().get(kitName).ifPresentOrElse(kit -> {
            long secs = DurationInfo.getSeconds(cooldown);
            KitManager.getInstance().addKit(kit.withCooldown(secs), true);
            String newTime = secs < 0 ? "一次性使用" : TimeUtils.prettyTimeString(secs);
            source.sendSuccess(() -> Component.literal("套件 '" + kitName + "' 冷却已修改: " + newTime).withStyle(ChatFormatting.YELLOW), false);
        }, () -> source.sendFailure(Component.literal("没有该套件: " + kitName).withStyle(ChatFormatting.RED)));

        return 1;
    }


    private static int resetCooldowns(CommandSourceStack source, String name, ServerPlayer player) {
        return resetCooldowns(source, name, player.getUUID());
    }

    private static int resetCooldowns(CommandSourceStack source, String name, UUID playerId) {
        if (KitManager.getInstance().get(name).isEmpty()) {
            source.sendFailure(Component.literal("未知的套件: " + name).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!FTBEPlayerData.playerExists(playerId)) {
            source.sendFailure(Component.literal("未知的玩家ID: " + playerId).withStyle(ChatFormatting.RED));
            return 0;
        }

        return FTBEPlayerData.getOrCreate(source.getServer(), playerId)
                .map(data -> {
                    data.setLastKitUseTime(name, 0L);
                    source.sendSuccess(() -> Component.literal("套件 '" + name + "' 的冷却已重置, 玩家UUID: " + playerId).withStyle(ChatFormatting.YELLOW), false);
                    return 1;
                }).orElse(0);
    }

    private static int resetCooldowns(CommandSourceStack source, String name) {
        if (KitManager.getInstance().get(name).isPresent()) {
            FTBEPlayerData.cleanupKitCooldowns(name);
            source.sendSuccess(() -> Component.literal("所有玩家的 '" + name + "' 套件冷却已重置").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendFailure(Component.literal("未知的套件: " + name).withStyle(ChatFormatting.RED));

        return 0;
    }
}
