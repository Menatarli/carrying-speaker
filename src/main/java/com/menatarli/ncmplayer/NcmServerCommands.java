package com.menatarli.ncmplayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 服务端命令 {@code /ncmop}：OP 让其他玩家点播网易云。
 *
 * <p>根名用 {@code ncmop} 而不是 {@code ncm}，因为客户端命令在同名根节点上优先，
 * 服务端命令会永远收不到（见 #26 调研）。
 *
 * <p>权限：要求权限等级 GAMEMASTERS（旧体系的 OP 等级 2）。非 OP 用不了，他们用客户端
 * {@code /ncm} 对自己点播即可。
 */
public final class NcmServerCommands {
	/** 提示里最多列几个被跳过的玩家名。 */
	private static final int MAX_SKIPPED_NAMES = 5;

	private NcmServerCommands() {
	}

	/** 注册 /ncmop。在通用初始化阶段调用。 */
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("ncmop")
						.requires(source -> source.permissions().hasPermission(
								new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
						.then(Commands.literal("play")
								.then(Commands.argument("targets", EntityArgument.players())
										.then(Commands.argument("link", StringArgumentType.greedyString())
												.executes(NcmServerCommands::playLink))))
						.then(Commands.literal("song")
								.then(Commands.argument("targets", EntityArgument.players())
										.then(Commands.argument("id", StringArgumentType.word())
												.executes(context -> playId(context, NcmPlayer.TYPE_SONG)))))
						.then(Commands.literal("playlist")
								.then(Commands.argument("targets", EntityArgument.players())
										.then(Commands.argument("id", StringArgumentType.word())
												.executes(context -> playId(context, NcmPlayer.TYPE_PLAYLIST)))))));
	}

	private static int playLink(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String link = StringArgumentType.getString(context, "link");
		Optional<NcmLinkParser.Link> parsed = NcmLinkParser.parse(link);
		if (parsed.isEmpty()) {
			context.getSource().sendFailure(Component.literal(
					"无法识别的链接，请粘贴网易云的单曲或歌单分享链接（music.163.com/song?id=… 或 /playlist?id=…）"));
			return 0;
		}
		NcmLinkParser.Link target = parsed.get();
		return sendToTargets(context, target.type(), target.id());
	}

	private static int playId(CommandContext<CommandSourceStack> context, String type)
			throws CommandSyntaxException {
		String id = StringArgumentType.getString(context, "id");
		if (!NcmPlayer.isValidId(id)) {
			context.getSource().sendFailure(Component.literal("ID 不合法：「" + id + "」，只接受 1-20 位纯数字"));
			return 0;
		}
		return sendToTargets(context, type, id);
	}

	private static int sendToTargets(CommandContext<CommandSourceStack> context, String type, String id)
			throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
		String sender = NcmPlayRequest.sanitizeSender(context.getSource().getTextName());
		NcmNetworking.PlayNcmPayload payload = new NcmNetworking.PlayNcmPayload(type, id, sender);

		List<String> skipped = new ArrayList<>();
		int sent = 0;
		for (ServerPlayer player : targets) {
			if (ServerPlayNetworking.canSend(player, NcmNetworking.PlayNcmPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			} else {
				skipped.add(player.getScoreboardName());
			}
		}

		String label = NcmPlayer.TYPE_PLAYLIST.equals(type) ? "歌单" : "单曲";
		StringBuilder message = new StringBuilder("已向 ").append(sent)
				.append(" 名玩家发送点播请求（需对方点击确认）").append(label).append(" ").append(id);
		if (!skipped.isEmpty()) {
			message.append("；").append(skipped.size()).append(" 名玩家没装本模组，已跳过：");
			for (int i = 0; i < Math.min(skipped.size(), MAX_SKIPPED_NAMES); i++) {
				if (i > 0) {
					message.append(", ");
				}
				message.append(skipped.get(i));
			}
			if (skipped.size() > MAX_SKIPPED_NAMES) {
				message.append(" 等");
			}
		}
		context.getSource().sendSuccess(() -> Component.literal(message.toString()), false);
		return sent;
	}
}
