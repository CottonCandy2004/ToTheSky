package com.fst.tothesky.command;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.contact.LetterScheduler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /tothesky} 命令树。
 *
 * <p>{@code /tothesky reloadletters}：重读 {@code config/tothesky/letters} 并立刻投递一轮。
 * 配置不会自动热重载（见 {@link LetterScheduler}），所以「改完 json」到「生效」之间就差这条命令；
 * 顺便它也会马上把当天该发的信投出去，省掉等下一轮 60 秒检查。
 *
 * <p><b>今天的信会重发</b>：这条命令会把「本应在今天投递」的信重新武装并再发一次——
 * 哪怕它今天已经投过了。所以连跑两次，收件人就会收到两份；这是给「改完 json 想立刻看到效果 /
 * 手工补发」用的，日常的 60 秒检查不重发（见 {@link LetterScheduler#reload}）。
 *
 * <p>权限等级 2（与 {@code /reload} 同级）：重载会改存档里的信件排期。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class ToTheSkyCommands {
    /** 重载节日信/生日信配置所需的权限等级（与 /reload 同级） */
    private static final int RELOAD_PERMISSION = 2;

    private ToTheSkyCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(ToTheSky.MODID)
                .then(Commands.literal("reloadletters")
                        .requires(source -> source.hasPermission(RELOAD_PERMISSION))
                        .executes(ToTheSkyCommands::reloadLetters)));
    }

    private static int reloadLetters(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        LetterScheduler.ReloadResult result = LetterScheduler.reload(server);

        source.sendSuccess(() -> Component.literal("[节日信] 重载完成：启用 " + result.loaded() + " 封"), true);
        if (!result.contactAvailable()) {
            source.sendSuccess(() -> Component.literal("[节日信] 未安装 Contact，本次未投递（排期保留）"), false);
        } else if (result.delivered() == 0) {
            source.sendSuccess(() -> Component.literal("[节日信] 当前没有到期的信"), false);
        } else {
            source.sendSuccess(() -> Component.literal("[节日信] 已投递 " + result.delivered() + " 封"), true);
        }
        return Command.SINGLE_SUCCESS;
    }
}
