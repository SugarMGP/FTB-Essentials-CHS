package dev.ftb.mods.ftbessentials.commands.impl.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ftb.mods.ftbessentials.commands.FTBCommand;
import dev.ftb.mods.ftbessentials.config.FTBEConfig;
import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import dev.ftb.mods.ftblibrary.util.PlayerDisplayNameUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class NicknameCommand implements FTBCommand {
    @Override
    public boolean enabled() {
        return FTBEConfig.NICK.isEnabled();
    }

    @Override
    public List<LiteralArgumentBuilder<CommandSourceStack>> register() {
        return Collections.singletonList(literal("nickname")
                .requires(FTBEConfig.NICK)
                .executes(context -> nickname(context.getSource().getPlayerOrException(), ""))
                .then(argument("nickname", StringArgumentType.greedyString())
                        .executes(context -> nickname(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "nickname")))
                ));
    }

    public int nickname(ServerPlayer player, String nick) {
        if (nick.length() > 30) {
            player.displayClientMessage(Component.literal("昵称太长！"), false); // 提示昵称过长
            return 0;
        }

        return FTBEPlayerData.getOrCreate(player).map(data -> {
            data.setNick(nick.trim());
            PlayerDisplayNameUtil.refreshDisplayName(player);

            if (data.getNick().isEmpty()) {
                player.displayClientMessage(Component.literal("昵称已重置！"), false); // 提示昵称已重置
            } else {
                player.displayClientMessage(Component.literal("昵称已更改为 '" + data.getNick() + "'"), false); // 提示昵称已更改
            }

            data.sendTabName(player.server);
            return 1;
        }).orElse(0);
    }
}
