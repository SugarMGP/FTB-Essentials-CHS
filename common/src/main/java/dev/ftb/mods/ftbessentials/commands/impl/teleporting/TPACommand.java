package dev.ftb.mods.ftbessentials.commands.impl.teleporting;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ftb.mods.ftbessentials.api.records.TPARequest;
import dev.ftb.mods.ftbessentials.commands.FTBCommand;
import dev.ftb.mods.ftbessentials.config.FTBEConfig;
import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import dev.ftb.mods.ftbessentials.util.TeleportPos;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class TPACommand implements FTBCommand {
    private static final HashMap<UUID, TPARequest> REQUESTS = new HashMap<>();

    @Override
    public boolean enabled() {
        return FTBEConfig.TPA.isEnabled();
    }

    @Override
    public List<LiteralArgumentBuilder<CommandSourceStack>> register() {
        return List.of(
                Commands.literal("tpa")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> tpa(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "target"), false))
                        ),
                Commands.literal("tpahere")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> tpa(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "target"), true))
                        ),
                Commands.literal("tpaccept")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> tpaccept(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "id")))
                        ),
                Commands.literal("tpdeny")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(context -> tpdeny(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "id")))
                        )
        );
    }

    public int tpa(ServerPlayer player, ServerPlayer target, boolean here) {
        FTBEPlayerData dataSource = FTBEPlayerData.getOrCreate(player).orElse(null);
        FTBEPlayerData dataTarget = FTBEPlayerData.getOrCreate(target).orElse(null);

        if (dataSource == null || dataTarget == null) {
            return 0;
        }

        if (REQUESTS.values().stream().anyMatch(r -> r.source() == dataSource && r.target() == dataTarget)) {
            player.displayClientMessage(Component.literal("请求已经发送！"), false);
            return 0;
        }

        TeleportPos.TeleportResult result = here ?
                dataTarget.tpaTeleporter.checkCooldown(target) :
                dataSource.tpaTeleporter.checkCooldown(player);

        if (!result.isSuccess()) {
            return result.runCommand(player);
        }

        TPARequest request = create(dataSource, dataTarget, here);

        MutableComponent component = Component.literal("TPA 请求! [ ");
        component.append((here ? target : player).getDisplayName().copy().withStyle(ChatFormatting.YELLOW));
        component.append(" ➡ ");
        component.append((here ? player : target).getDisplayName().copy().withStyle(ChatFormatting.YELLOW));
        component.append(" ]");

        MutableComponent component2 = Component.literal("点击其中之一：");
        component2.append(Component.literal("接受 ✔").setStyle(Style.EMPTY
                .applyFormat(ChatFormatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept " + request.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击以接受")))
        ));

        component2.append(" | ");

        component2.append(Component.literal("拒绝 ❌").setStyle(Style.EMPTY
                .applyFormat(ChatFormatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny " + request.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击以拒绝")))
        ));

        component2.append(" |");

        target.displayClientMessage(component, false);
        target.displayClientMessage(component2, false);

        player.displayClientMessage(Component.literal("请求已发送！"), false);
        return 1;
    }

    public int tpaccept(ServerPlayer player, String id) {
        var uuid = attemptUuid(id);
        if (uuid == null) {
            player.displayClientMessage(Component.literal("无效的请求！"), false);
            return 0;
        }

        TPARequest request = REQUESTS.get(uuid);

        if (request == null) {
            player.displayClientMessage(Component.literal("无效的请求！"), false);
            return 0;
        }

        ServerPlayer sourcePlayer = player.server.getPlayerList().getPlayer(request.source().getUuid());

        if (sourcePlayer == null) {
            player.displayClientMessage(Component.literal("玩家已下线！"), false);
            return 0;
        }

        TeleportPos.TeleportResult result = request.here() ?
                request.target().tpaTeleporter.teleport(player, p -> new TeleportPos(sourcePlayer)) :
                request.source().tpaTeleporter.teleport(sourcePlayer, p -> new TeleportPos(player));

        if (result.isSuccess()) {
            REQUESTS.remove(request.id());
        }

        return result.runCommand(player);
    }

    public int tpdeny(ServerPlayer player, String id) {
        var uuid = attemptUuid(id);
        if (uuid == null) {
            player.displayClientMessage(Component.literal("无效的请求！"), false);
            return 0;
        }

        TPARequest request = REQUESTS.get(uuid);
        if (request == null) {
            player.displayClientMessage(Component.literal("无效的请求！"), false);
            return 0;
        }

        REQUESTS.remove(request.id());

        player.displayClientMessage(Component.literal("请求已拒绝！"), false);

        ServerPlayer player2 = player.server.getPlayerList().getPlayer(request.target().getUuid());

        if (player2 != null) {
            player2.displayClientMessage(Component.literal("请求已拒绝！"), false);
        }

        return 1;
    }

    public static TPARequest create(FTBEPlayerData source, FTBEPlayerData target, boolean here) {
        var uuid = UUID.randomUUID();
        TPARequest r = new TPARequest(uuid, source, target, here, System.currentTimeMillis());
        REQUESTS.put(uuid, r);
        return r;
    }

    public static void clearRequests() {
        REQUESTS.clear();
    }

    @Nullable
    private UUID attemptUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static HashMap<UUID, TPARequest> requests() {
        return REQUESTS;
    }
}
