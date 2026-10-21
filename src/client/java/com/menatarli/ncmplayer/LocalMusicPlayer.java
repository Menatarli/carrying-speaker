package com.menatarli.ncmplayer;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.player.advanced.AdvancedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地音乐播放器：随机播 {@code musicDir} 下的 mp3 / wav，一首放完接着随机下一首，直到被 stop。
 *
 * <p>现在也支持「指定曲目、从第 N 秒起播、只播一遍」——由 {@link PlayRequest} 描述。
 * 起播偏移只作用于本次请求的第一首；循环续播的后续曲目都从头开始。
 *
 * <p>播放跑在单独的 daemon 线程里，同一时间最多一个播放线程。扫描目录、启停、close、join 这些可能阻塞的
 * 操作都排在控制线程里执行，对外只暴露异步接口，主线程不会被阻塞。
 */
public final class LocalMusicPlayer {
	/** 播放线程名。 */
	public static final String THREAD_NAME = "carryingspeaker-local";
	/** 控制线程名：所有可能阻塞的播放器操作都在这里串行执行。 */
	public static final String CONTROL_THREAD_NAME = "carryingspeaker-control";

	private static final String EXT_MP3 = ".mp3";
	private static final String EXT_WAV = ".wav";
	/** wav 用 Clip + 轮询，这个间隔决定 stop 的响应速度。 */
	private static final long WAV_POLL_INTERVAL_MS = 50L;
	private static final long STOP_JOIN_TIMEOUT_MS = 2_000L;

	private static final Logger LOGGER = LoggerFactory.getLogger("carryingspeaker");

	private final Path musicDir;
	private final Object stateLock = new Object();
	/** 单线程执行器：扫描目录、启停、close、join 都排进这里，按提交顺序执行。 */
	private final ExecutorService control;

	/** 是否应该继续播（volatile：工作线程轮询它）。 */
	private volatile boolean running;
	private Thread worker;
	private AdvancedPlayer mp3Player;
	private Clip wavClip;

	public LocalMusicPlayer(Path musicDir) {
		this.musicDir = musicDir;
		this.control = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, CONTROL_THREAD_NAME);
			thread.setDaemon(true);
			return thread;
		});
	}

	public Path getMusicDir() {
		return musicDir;
	}

	/** 异步开始随机循环播放（等价于 {@code startAsync(PlayRequest.randomLoop(), onResult)}）。 */
	public void startAsync(Consumer<Boolean> onResult) {
		startAsync(PlayRequest.randomLoop(), onResult);
	}

	/**
	 * 异步按请求开始播放：扫描目录、停掉旧的、启动新的都在控制线程里做，主线程立即返回。
	 *
	 * @param onResult 在控制线程回调，true = 已开始播放，false = 没有可播的内容（或指定曲目不存在）
	 */
	public void startAsync(PlayRequest request, Consumer<Boolean> onResult) {
		submit(() -> {
			boolean started = startInternal(request);
			if (onResult != null) {
				try {
					onResult.accept(started);
				} catch (RuntimeException e) {
					LOGGER.warn("carryingspeaker：播放启动回调出错", e);
				}
			}
		});
	}

	/** 异步停下。可以重复调用。 */
	public void stopAsync() {
		stopAsync(null);
	}

	/** 异步停下，停完后在控制线程里执行 onStopped（可为 null）。 */
	public void stopAsync(Runnable onStopped) {
		submit(() -> {
			stopInternal();
			if (onStopped != null) {
				try {
					onStopped.run();
				} catch (RuntimeException e) {
					LOGGER.warn("carryingspeaker：停止回调出错", e);
				}
			}
		});
	}

	/** 关闭控制线程。已提交的任务会执行完再退出，关闭后不再接受新任务。 */
	public void shutdown() {
		control.shutdown();
	}

	private void submit(Runnable task) {
		try {
			control.execute(task);
		} catch (RejectedExecutionException e) {
			LOGGER.warn("carryingspeaker：控制线程已关闭，忽略这次播放器操作", e);
		}
	}

	private boolean startInternal(PlayRequest request) {
		final PlayRequest effective = request == null ? PlayRequest.randomLoop() : request;
		Path firstTrack = null;
		if (!effective.soundFile().isEmpty()) {
			firstTrack = musicDir == null ? null : musicDir.resolve(effective.soundFile());
			if (firstTrack == null || !Files.isRegularFile(firstTrack)) {
				LOGGER.warn("指定曲目不存在：{}", effective.soundFile());
				return false;
			}
		} else if (listTracks().isEmpty()) {
			return false;
		}
		stopInternal();
		synchronized (stateLock) {
			running = true;
			final Path first = firstTrack;
			Thread thread = new Thread(() -> runLoop(effective, first), THREAD_NAME);
			thread.setDaemon(true);
			worker = thread;
			thread.start();
		}
		return true;
	}

	private void stopInternal() {
		AdvancedPlayer mp3;
		Clip clip;
		Thread thread;
		synchronized (stateLock) {
			running = false;
			mp3 = mp3Player;
			mp3Player = null;
			clip = wavClip;
			wavClip = null;
			thread = worker;
		}
		if (mp3 != null) {
			try {
				mp3.close();
			} catch (RuntimeException e) {
				LOGGER.warn("关闭 mp3 播放器时出错", e);
			}
		}
		if (clip != null) {
			try {
				if (clip.isOpen()) {
					clip.stop();
				}
			} catch (RuntimeException e) {
				LOGGER.warn("停止 wav 播放时出错", e);
			}
		}
		if (thread != null && thread != Thread.currentThread()) {
			try {
				thread.join(STOP_JOIN_TIMEOUT_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/** 当前是否在播（含两首之间的收尾）。 */
	public boolean isPlaying() {
		return running;
	}

	/** {@code musicDir} 下所有 .mp3 / .wav（扩展名不区分大小写），按路径排序；读不到就返回空表。 */
	public List<Path> listTracks() {
		if (musicDir == null || !Files.isDirectory(musicDir)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(musicDir)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(LocalMusicPlayer::isSupported)
					.sorted()
					.toList();
		} catch (Exception e) {
			LOGGER.warn("扫描音乐目录 {} 失败", musicDir, e);
			return List.of();
		}
	}

	private static boolean isSupported(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(EXT_MP3) || name.endsWith(EXT_WAV);
	}

	private void runLoop(PlayRequest request, Path firstTrack) {
		try {
			Path current = firstTrack;
			boolean first = true;
			while (running) {
				final Path track;
				if (first && current != null) {
					track = current;
				} else {
					List<Path> tracks = listTracks();
					if (tracks.isEmpty()) {
						if (running) {
							LOGGER.warn("音乐目录 {} 里已经没有可播放的 mp3/wav，停止播放", musicDir);
						}
						return;
					}
					track = tracks.get(ThreadLocalRandom.current().nextInt(tracks.size()));
				}
				final double offset = first ? request.startOffsetSeconds() : 0.0;
				playTrack(track, offset);
				first = false;
				if (!request.loop()) {
					// 只播一遍：第一首放完就结束。
					return;
				}
			}
		} catch (Exception e) {
			// 解码 / 播放出错只打 warn 并停止，不崩游戏；正在 stop 时抛出的异常属于正常收尾，不刷日志。
			if (running) {
				LOGGER.warn("本地音乐播放出错，已停止播放（目录：{}）", musicDir, e);
			}
		} finally {
			// 只有当前线程还是「现任」worker 时才收尾，避免 join 超时后新旧线程互相踩。
			synchronized (stateLock) {
				if (worker == Thread.currentThread()) {
					running = false;
					mp3Player = null;
					wavClip = null;
				}
			}
		}
	}

	private void playTrack(Path track, double offsetSeconds) throws Exception {
		String name = track.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(EXT_MP3)) {
			playMp3(track, offsetSeconds);
		} else if (name.endsWith(EXT_WAV)) {
			playWav(track, offsetSeconds);
		}
	}

	/**
	 * 播一首 mp3。offsetSeconds > 0 时用 {@link AdvancedPlayer#play(int, int)} 跳过前面的帧——
	 * 它内部调的是 {@code skipFrame()}，只读帧、不解码不播音（见 #36 的字节码证据）。
	 */
	private void playMp3(Path track, double offsetSeconds) throws Exception {
		int startFrames = 0;
		if (offsetSeconds > 0.0) {
			float msPerFrame = readMsPerFrame(track);
			startFrames = PlayRequest.offsetToFrames(offsetSeconds, msPerFrame);
			if (startFrames <= 0 && !(msPerFrame > 0.0F)) {
				LOGGER.warn("读不到 mp3 帧长，{} 从头播放", track.getFileName());
			}
		}
		try (InputStream raw = Files.newInputStream(track);
				InputStream buffered = new BufferedInputStream(raw)) {
			AdvancedPlayer player = new AdvancedPlayer(buffered);
			synchronized (stateLock) {
				if (!running) {
					player.close();
					return;
				}
				mp3Player = player;
			}
			try {
				if (startFrames > 0) {
					player.play(startFrames, Integer.MAX_VALUE);
				} else {
					player.play();
				}
			} finally {
				synchronized (stateLock) {
					if (mp3Player == player) {
						mp3Player = null;
					}
				}
				player.close();
			}
		}
	}

	/** 读第一帧拿每帧毫秒数；读不到返回 0，调用方按「从头播」处理。用独立流，不影响正式播放。 */
	private static float readMsPerFrame(Path track) {
		try (InputStream raw = Files.newInputStream(track);
				InputStream buffered = new BufferedInputStream(raw)) {
			Bitstream bitstream = new Bitstream(buffered);
			try {
				Header header = bitstream.readFrame();
				if (header == null) {
					return 0.0F;
				}
				return header.ms_per_frame();
			} finally {
				bitstream.close();
			}
		} catch (Exception e) {
			LOGGER.warn("读取 mp3 帧长失败：{}", track, e);
			return 0.0F;
		}
	}

	/** 播一首 wav。offsetSeconds > 0 时用 Clip.setMicrosecondPosition 定位；超过长度按「立刻放完」处理。 */
	private void playWav(Path track, double offsetSeconds) throws Exception {
		try (AudioInputStream stream = AudioSystem.getAudioInputStream(track.toFile())) {
			Clip clip = AudioSystem.getClip();
			synchronized (stateLock) {
				if (!running) {
					clip.close();
					return;
				}
				wavClip = clip;
			}
			try {
				clip.open(stream);
				if (!running) {
					return;
				}
				if (offsetSeconds > 0.0) {
					long micros = (long) (offsetSeconds * 1_000_000L);
					if (micros >= clip.getMicrosecondLength()) {
						// 偏移超过曲目总长：等价于立刻放完。
						return;
					}
					clip.setMicrosecondPosition(micros);
				}
				clip.start();
				while (running && clip.isRunning()) {
					Thread.sleep(WAV_POLL_INTERVAL_MS);
				}
			} finally {
				synchronized (stateLock) {
					if (wavClip == clip) {
						wavClip = null;
					}
				}
				try {
					clip.stop();
				} catch (RuntimeException ignored) {
					// Clip 已经关了，stop 会抛 IllegalStateException，收尾时可以忽略
				}
				clip.close();
			}
		}
	}
}
