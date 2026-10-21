package com.menatarli.ncmplayer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：读配置、注册 /ncm 客户端命令、每 tick 检查低血量。
 */
public class NcmClientMod implements ClientModInitializer {
	public static final String MOD_ID = "carryingspeaker";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private Path configDir;
	private NcmConfig config;
	private LocalMusicPlayer musicPlayer;
	private SoundEventTable eventTable;
	private SoundEventArbiter arbiter;
	private final NcmPendingRequests pendingRequests = new NcmPendingRequests();

	@Override
	public void onInitializeClient() {
		Path configRoot = FabricLoader.getInstance().getConfigDir();
		// 先迁移旧配置，再按新路径加载。
		ConfigMigration.migrate(configRoot);
		configDir = configRoot.resolve(ConfigMigration.NEW_DIR_NAME);
		config = NcmConfig.load(configDir);
		musicPlayer = new LocalMusicPlayer(config.resolveMusicDirectory(configDir));
		// 内置曲释放到用户配置的音乐目录（musicDir 可能被改过）。
		BuiltinMusic.releaseAsync(config.resolveMusicDirectory(configDir), configDir);
		eventTable = SoundEventTable.load(configDir.resolve(SoundEventTable.FILE_NAME));
		arbiter = new SoundEventArbiter(eventTable);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> registerCommands(dispatcher));
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
		ClientPlayNetworking.registerGlobalReceiver(NcmNetworking.PlayNcmPayload.TYPE, this::onRemotePlayRequest);

		LOGGER.info("carryingspeaker 已加载：音乐目录={}", musicPlayer.getMusicDir().toAbsolutePath());
		LOGGER.info("低血量参数已改由 events.json 管理");
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("ncm")
				.then(ClientCommands.literal("play")
						.then(ClientCommands.argument("link", StringArgumentType.greedyString())
								.executes(context -> playLink(context.getSource(),
										StringArgumentType.getString(context, "link")))))
				.then(ClientCommands.literal("song")
						.then(ClientCommands.argument("id", StringArgumentType.word())
								.executes(context -> openNcmFromCommand(context.getSource(), NcmPlayer.TYPE_SONG,
										StringArgumentType.getString(context, "id")))))
				.then(ClientCommands.literal("playlist")
						.then(ClientCommands.argument("id", StringArgumentType.word())
								.executes(context -> openNcmFromCommand(context.getSource(), NcmPlayer.TYPE_PLAYLIST,
										StringArgumentType.getString(context, "id")))))
				.then(ClientCommands.literal("local")
						.then(ClientCommands.literal("play")
								.executes(context -> playLocal(context.getSource())))
						.then(ClientCommands.literal("stop")
								.executes(context -> stopLocal(context.getSource()))))
				.then(ClientCommands.literal("accept")
						.then(ClientCommands.argument("token", StringArgumentType.word())
								.executes(context -> acceptRequest(context.getSource(),
										StringArgumentType.getString(context, "token")))))
				.then(ClientCommands.literal("reload")
						.executes(context -> reload(context.getSource()))));
	}

	/** /ncm play &lt;分享链接&gt;：从链接里解析类型和 id，再走现有的播放流程。 */
	private int playLink(FabricClientCommandSource source, String link) {
		Optional<NcmLinkParser.Link> parsed = NcmLinkParser.parse(link);
		if (parsed.isEmpty()) {
			source.sendError(Component.literal(
					"无法识别的链接，请粘贴网易云的单曲或歌单分享链接（music.163.com/song?id=… 或 /playlist?id=…）"));
			return 0;
		}
		NcmLinkParser.Link target = parsed.get();
		return openNcmFromCommand(source, target.type(), target.id());
	}

	/** 客户端命令入口：把 source 的两个反馈通道包成 Consumer 交给 openNcm。 */
	private int openNcmFromCommand(FabricClientCommandSource source, String type, String id) {
		return openNcm(source.getClient(), type, id, source::sendFeedback, source::sendError);
	}

	/** 收到服务端的点播请求：校验后挂起，等玩家点击 [播放] 才真正执行。 */
	private void onRemotePlayRequest(NcmNetworking.PlayNcmPayload payload, ClientPlayNetworking.Context context) {
		String type = payload.playType();
		String id = payload.id();
		if (!NcmPlayRequest.isValidType(type) || !NcmPlayer.isValidId(id)) {
			LOGGER.warn("carryingspeaker：收到不合法的点播请求 type={} id={}，已丢弃", type, id);
			return;
		}
		final LocalPlayer player = context.player();
		if (player == null) {
			return;
		}
		final String sender = NcmPlayRequest.sanitizeSender(payload.sender());
		final String label = NcmPlayer.TYPE_PLAYLIST.equals(type) ? "歌单" : "单曲";
		final String token = pendingRequests.add(type, id, sender, System.currentTimeMillis());
		Component playButton = Component.literal("[播放]").withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/ncm accept " + token))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("点击后用网易云播放，60 秒内有效"))));
		player.sendSystemMessage(Component.literal(sender + " 为你点播了" + label + " " + id + " ").append(playButton));
	}

	/** /ncm accept &lt;token&gt;：玩家点了 [播放] 才会走到这里。 */
	private int acceptRequest(FabricClientCommandSource source, String token) {
		Optional<NcmPendingRequests.Request> taken = pendingRequests.take(token, System.currentTimeMillis());
		if (taken.isEmpty()) {
			source.sendError(Component.literal("点播请求不存在或已过期"));
			return 0;
		}
		NcmPendingRequests.Request request = taken.get();
		return openNcmFromCommand(source, request.type(), request.id());
	}

	/**
	 * 后台线程拉网易云，成功/失败都通过 feedback / error 反馈。
	 *
	 * <p>客户端命令传 {@code source::sendFeedback} / {@code source::sendError}；
	 * 服务端点播传 {@code component -> player.sendSystemMessage(component)}。两条路径共用这套流程。
	 */
	private int openNcm(Minecraft client, String type, String id,
			Consumer<Component> feedback, Consumer<Component> error) {
		// 主线程缓存 MC 的窗口句柄，后台线程分类前台时要用。
		NcmWindowHelper.captureMcWindow(client);
		if (!NcmPlayer.isValidId(id)) {
			error.accept(Component.literal("ID 不合法：「" + id + "」，只接受 1-20 位纯数字"));
			return 0;
		}
		final String url;
		try {
			url = NcmPlayer.buildUrl(type, id);
		} catch (IllegalArgumentException e) {
			error.accept(Component.literal("参数不对：" + e.getMessage()));
			return 0;
		}

		final String label = NcmPlayer.TYPE_PLAYLIST.equals(type) ? "歌单" : "单曲";
		final boolean background = config.isNcmBackground();
		final int coldStartDelay = config.getColdStartDelaySeconds();
		Thread opener = new Thread(() -> {
			try {
				if (NcmWindowHelper.findCloudMusicPid() > 0) {
					warmStart(client, url, label, id, background, feedback, error);
				} else {
					coldStart(client, url, label, id, background, coldStartDelay, feedback, error);
				}
			} catch (Exception e) {
				LOGGER.warn("拉起网易云失败：{}", url, e);
				String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				client.execute(() -> error.accept(Component.literal("拉起网易云失败：" + reason)));
			}
		}, "carryingspeaker-open");
		opener.setDaemon(true);
		opener.start();
		return 1;
	}

	/** 网易云已经在跑：把链接交给现有实例。用户自己开着窗口时不做任何窗口处理。 */
	private void warmStart(Minecraft client, String url, String label, String id, boolean background,
			Consumer<Component> feedback, Consumer<Component> error) throws IOException {
		final boolean userWindow = NcmWindowHelper.hasUserVisibleCloudMusicWindow();
		NcmPlayer.open(url);
		final String message = (background ? "已让网易云在后台播放" : "已让网易云播放") + label + " " + id;
		client.execute(() -> feedback.accept(Component.literal(message)));
		if (userWindow) {
			LOGGER.info("carryingspeaker：网易云窗口由用户打开着，不做窗口处理");
			return;
		}
		if (background) {
			// 提示先发出去，再去做可能最长 10 秒的窗口轮询。
			NcmWindowHelper.minimizeCloudMusicAndFocus(client);
		}
	}

	/**
	 * 网易云没在跑：先拉起客户端，等它准备好，再补发一次链接。
	 *
	 * <p>冷启动时 --webcmd 会被还没初始化完的客户端丢掉，所以第一次 open 只负责拉起，
	 * 进程出现并等 {@code delaySeconds} 秒之后再发一次才会真正开始播放。
	 */
	private void coldStart(Minecraft client, String url, String label, String id, boolean background,
			int delaySeconds, Consumer<Component> feedback, Consumer<Component> error) throws IOException {
		client.execute(() -> feedback.accept(Component.literal(
				"网易云未启动，正在启动，约 " + delaySeconds + " 秒后发送播放指令（歌单较大时网易云加载会更久）")));

		// 第一次 open 只用来拉起客户端，播放指令可能会丢。
		NcmPlayer.open(url);

		if (!waitForCloudMusicProcess(30_000L)) {
			LOGGER.warn("carryingspeaker：等网易云启动超时");
			client.execute(() -> error.accept(Component.literal("网易云启动超时")));
			return;
		}

		if (background) {
			suppressFor(delaySeconds * 1000L, client);
		} else {
			sleepQuietly(delaySeconds * 1000L);
		}

		// 第二次 open 会转交给已经起来的实例，这一次才会真正开始播放。
		NcmPlayer.open(url);

		final String message = (background ? "已让网易云在后台播放" : "已让网易云播放") + label + " " + id;
		client.execute(() -> feedback.accept(Component.literal(message)));
		if (background) {
			NcmWindowHelper.minimizeCloudMusicAndFocus(client);
		}
	}

	/** 轮询等 cloudmusic.exe 出现，最多 timeoutMs 毫秒。 */
	private static boolean waitForCloudMusicProcess(long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (NcmWindowHelper.findCloudMusicPid() > 0) {
				return true;
			}
			if (!sleepQuietly(200L)) {
				return NcmWindowHelper.findCloudMusicPid() > 0;
			}
		}
		return false;
	}

	/**
	 * 在指定时长内持续压制网易云窗口：每 200ms 看一次前台窗口，属于网易云就最小化并抢回焦点。
	 *
	 * <p>压制失败不打断流程，只打一条 warn 然后放弃压制（后续播放补发照常进行）。
	 */
	private void suppressFor(long durationMs, Minecraft client) {
		final long deadline = System.currentTimeMillis() + durationMs;
		NcmStealDetector detector = new NcmStealDetector(NcmWindowHelper.classifyForeground());
		int suppressed = 0;
		while (System.currentTimeMillis() < deadline) {
			NcmStealDetector.Action action = detector.feed(NcmWindowHelper.classifyForeground());
			if (action == NcmStealDetector.Action.STOP) {
				LOGGER.info("carryingspeaker：检测到用户主动切到网易云，本轮不再接管窗口");
				return;
			}
			if (action == NcmStealDetector.Action.ACT) {
				try {
					if (NcmWindowHelper.suppressForegroundCloudMusic(client)) {
						suppressed++;
						LOGGER.info("carryingspeaker：冷启动期间把网易云收回托盘并还焦点给 MC 一次（焦点验证失败累计 {} 次）",
								NcmWindowHelper.focusFailureCount());
						if (suppressed >= NcmWindowHelper.MAX_COLDSTART_SUPPRESS) {
							// 到顶就收手，后面的补发 open 照常进行。
							LOGGER.info("carryingspeaker：冷启动期间已夺回焦点 {} 次，停止（播放指令仍会补发）",
									NcmWindowHelper.MAX_COLDSTART_SUPPRESS);
							return;
						}
					}
				} catch (Throwable t) {
					LOGGER.warn("carryingspeaker：冷启动期间接管窗口失败，放弃接管", t);
					return;
				}
			}
			// 轮询间隔从 200ms 收到 100ms，免得漏掉用户切换时那个中间状态。
			if (!sleepQuietly(100L)) {
				return;
			}
		}
	}

	private static boolean sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** /ncm local play：立刻随机播一首（扫描与启动在控制线程里做，不阻塞主线程）。 */
	private int playLocal(FabricClientCommandSource source) {
		final LocalMusicPlayer player = musicPlayer;
		final Path dir = player.getMusicDir();
		final Minecraft client = source.getClient();
		player.startAsync(started -> {
			if (started) {
				client.execute(() -> source.sendFeedback(
						Component.literal("开始随机播放本地音乐（目录：" + dir.toAbsolutePath() + "）")));
			} else {
				client.execute(() -> source.sendError(
						Component.literal("本地音乐目录里没有 mp3/wav 文件，请把音乐放到：" + dir.toAbsolutePath())));
			}
		});
		return 1;
	}

	/** /ncm local stop。 */
	private int stopLocal(FabricClientCommandSource source) {
		arbiter.reset();
		musicPlayer.stopAsync();
		source.sendFeedback(Component.literal("已停止本地音乐"));
		return 1;
	}

	/** /ncm reload：重读配置与事件表，重建播放器和仲裁器。 */
	private int reload(FabricClientCommandSource source) {
		LocalMusicPlayer old = musicPlayer;
		arbiter.reset();
		old.stopAsync();
		old.shutdown();
		config = NcmConfig.load(configDir);
		musicPlayer = new LocalMusicPlayer(config.resolveMusicDirectory(configDir));
		eventTable = SoundEventTable.load(configDir.resolve(SoundEventTable.FILE_NAME));
		arbiter = new SoundEventArbiter(eventTable);
		source.sendFeedback(Component.literal("肩扛音响 配置已重新读取："
				+ describeEvents()
				+ "，冷启动延迟=" + config.getColdStartDelaySeconds() + "s"
				+ "，音乐目录=" + musicPlayer.getMusicDir().toAbsolutePath()
				+ "，后台播放=" + (config.isNcmBackground() ? "开" : "关")));
		return 1;
	}

	/** 事件表的可读摘要，reload 反馈用。 */
	private String describeEvents() {
		SoundEventConfig low = eventTable.get(SoundEventConfig.EventType.LOW_HEALTH)
				.orElse(SoundEventConfig.defaults(SoundEventConfig.EventType.LOW_HEALTH));
		SoundEventConfig death = eventTable.get(SoundEventConfig.EventType.DEATH)
				.orElse(SoundEventConfig.defaults(SoundEventConfig.EventType.DEATH));
		return "事件表：低血量=" + (low.enabled() ? "开" : "关") + " " + low.minHealth() + "-" + low.exitHealth()
				+ "，最短" + low.minPlaySeconds() + "s；死亡=" + (death.enabled() ? "开" : "关")
				+ " " + death.soundFile() + " 起播" + death.startOffsetSeconds() + "s 最短" + death.minPlaySeconds() + "s";
	}

	private void onEndClientTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			// 不在世界里：清掉状态并停下，免得下次进世界还带着上一次的激活状态。
			if (arbiter.reset()) {
				musicPlayer.stopAsync();
			}
			return;
		}

		// tick 里只做纯逻辑判断，不做任何 IO、不解析 JSON。
		SoundEventArbiter.Decision decision = arbiter.update(
				!player.isDeadOrDying(), player.getHealth(), player.getMaxHealth(), System.nanoTime());
		if (decision.stop()) {
			musicPlayer.stopAsync();
		}
		if (decision.start() != null) {
			startEvent(client, player, decision.start());
		}
	}

	/** 事件触发：按配置发起播放请求；失败时清掉仲裁器状态并提示玩家。 */
	private void startEvent(Minecraft client, LocalPlayer player, SoundEventConfig cfg) {
		final LocalMusicPlayer music = musicPlayer;
		final Path dir = music.getMusicDir();
		music.startAsync(new PlayRequest(cfg.soundFile(), cfg.startOffsetSeconds(), cfg.loop()), started -> {
			if (started) {
				return;
			}
			client.execute(() -> {
				arbiter.onStartFailed(cfg.eventType());
				Component message = cfg.eventType() == SoundEventConfig.EventType.DEATH
						? Component.literal("死亡音乐 " + cfg.soundFile() + " 不存在")
						: Component.literal("血量偏低，但没有本地音乐可播。请把 mp3/wav 文件放到："
								+ dir.toAbsolutePath());
				player.sendSystemMessage(message);
			});
		});
	}
}
