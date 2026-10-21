package com.menatarli.ncmplayer;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 网易云的进程识别与「只在被抢时」的窗口接管。
 *
 * <p><b>只处理一种情况</b>：上一次前台是 MC、这一次被网易云抢走（说明是我们的指令把网易云喊到了前台）。
 * 用户开着网易云窗口、或用户自己切过去，一律不接管——由 {@link NcmStealDetector} 判断。
 *
 * <p><b>窗口操作只有 {@code SW_HIDE} 一种</b>，且只对当前前台那一个窗口。不用 {@code SW_MINIMIZE}
 * （它会把托盘里的网易云变成任务栏最小化窗口，网易云不认这个状态）。
 *
 * <p>新增的 Win32 调用全部是只读查询：EnumWindows / GetWindow / GetWindowText /
 * IsWindowVisible / GetForegroundWindow / GetWindowThreadProcessId。不 AttachThreadInput、不发按键。
 */
public final class NcmWindowHelper {
	/** 网易云主程序文件名，由 orpheus 协议注册表查得。 */
	public static final String EXPECTED_EXE = "cloudmusic.exe";

	/** ShowWindow 的 SW_HIDE。 */
	private static final int SW_HIDE = 0;
	/** GetWindow 的 GW_OWNER：有 owner 的窗口属于被拥有的子窗口/对话框，不算用户独立开着的窗口。 */
	private static final int GW_OWNER = 4;

	private static final long POLL_INTERVAL_MS = 100L;
	/** 第一次命中之前最多等多久。 */
	private static final long TIMEOUT_MS = 10_000L;
	/** 第一次命中之后再守多久。 */
	private static final long GUARD_AFTER_HIT_MS = 5_000L;
	/** 守护期间最多再接管几次。 */
	private static final int MAX_GUARD_RESUPPRESS = 3;
	/** 冷启动阶段最多接管几次。 */
	public static final int MAX_COLDSTART_SUPPRESS = 5;
	/** 回焦之后等多久再做焦点验证。 */
	private static final long VERIFY_DELAY_MS = 300L;

	private static final Logger LOGGER = LoggerFactory.getLogger("carryingspeaker");

	private static final AtomicInteger PAUSE_CLOSES = new AtomicInteger();
	private static final AtomicInteger FOCUS_FAILS = new AtomicInteger();

	/** MC 的 Win32 窗口句柄，由主线程在开始一轮处理前缓存，供后台线程分类前台。 */
	private static volatile long cachedMcHwnd;

	private NcmWindowHelper() {
	}

	/** 焦点验证失败的累计次数。 */
	public static int focusFailureCount() {
		return FOCUS_FAILS.get();
	}

	/** 在主线程调用：缓存 MC 的 Win32 窗口句柄，供后台线程判断「前台是不是 MC」。 */
	public static void captureMcWindow(Minecraft client) {
		try {
			cachedMcHwnd = GLFWNativeWin32.glfwGetWin32Window(client.getWindow().handle());
		} catch (Throwable t) {
			cachedMcHwnd = 0L;
			LOGGER.warn("carryingspeaker：取 MC 窗口句柄失败，本次不做窗口接管", t);
		}
	}

	/** 前台窗口的分类，供调用方喂给 {@link NcmStealDetector}。 */
	public static NcmStealDetector.Fg classifyForeground() {
		try {
			WinDef.HWND foreground = User32.INSTANCE.GetForegroundWindow();
			if (foreground == null) {
				return NcmStealDetector.Fg.OTHER;
			}
			long fgValue = Pointer.nativeValue(foreground.getPointer());
			long mcHwnd = cachedMcHwnd;
			if (mcHwnd != 0L && fgValue == mcHwnd) {
				return NcmStealDetector.Fg.MC;
			}
			IntByReference pidRef = new IntByReference();
			User32.INSTANCE.GetWindowThreadProcessId(foreground, pidRef);
			int pid = pidRef.getValue();
			if (pid > 0 && isCloudMusicProcess(pid)) {
				return NcmStealDetector.Fg.CLOUDMUSIC;
			}
			return NcmStealDetector.Fg.OTHER;
		} catch (Throwable t) {
			LOGGER.warn("carryingspeaker：分类前台窗口失败", t);
			return NcmStealDetector.Fg.OTHER;
		}
	}

	/**
	 * 当前是否已经有「用户开着」的网易云窗口。
	 *
	 * <p>条件：可见、没有 owner、所属进程是 cloudmusic.exe。最小化在任务栏的也算开着。
	 */
	public static boolean hasUserVisibleCloudMusicWindow() {
		final boolean[] found = {false};
		try {
			User32 user32 = User32.INSTANCE;
			user32.EnumWindows((hwnd, lParam) -> {
				if (!user32.IsWindowVisible(hwnd)) {
					return true;
				}
				if (user32.GetWindow(hwnd, new WinDef.DWORD(GW_OWNER)) != null) {
					return true;
				}
				IntByReference pidRef = new IntByReference();
				user32.GetWindowThreadProcessId(hwnd, pidRef);
				int pid = pidRef.getValue();
				if (pid <= 0 || !isCloudMusicProcess(pid)) {
					return true;
				}
				char[] title = new char[256];
				user32.GetWindowText(hwnd, title, title.length);
				// JNA 的 User32 没有 IsIconic，这里只记 hwnd 和标题（最小化的窗口同样是可见的，会被算作「用户开着」）。
				LOGGER.debug("carryingspeaker：用户开着网易云窗口 hwnd=0x{} title={}",
						Long.toHexString(Pointer.nativeValue(hwnd.getPointer())),
						new String(title).trim());
				found[0] = true;
				return false;
			}, null);
		} catch (Throwable t) {
			LOGGER.warn("carryingspeaker：枚举网易云窗口失败，按「没有」处理", t);
			return false;
		}
		return found[0];
	}

	/**
	 * 在调用线程里等网易云窗口：最多 10 秒等第一次命中，命中后再守护一段时间。
	 *
	 * <p>只有「网易云从 MC 手里抢走前台」才接管；用户主动切过去会立刻结束本轮。
	 */
	public static void minimizeCloudMusicAndFocus(Minecraft client) {
		try {
			pollAndGuard(client);
		} catch (UnsatisfiedLinkError e) {
			LOGGER.warn("carryingspeaker：JNA/Win32 不可用，跳过窗口接管", e);
		} catch (NoClassDefFoundError e) {
			LOGGER.warn("carryingspeaker：找不到 JNA 类，跳过窗口接管", e);
		} catch (Throwable t) {
			LOGGER.warn("carryingspeaker：窗口接管失败", t);
		}
	}

	private static void pollAndGuard(Minecraft client) {
		final long waitDeadline = System.currentTimeMillis() + TIMEOUT_MS;
		NcmStealDetector detector = new NcmStealDetector(classifyForeground());
		boolean hit = false;
		while (System.currentTimeMillis() < waitDeadline) {
			NcmStealDetector.Action action = detector.feed(classifyForeground());
			if (action == NcmStealDetector.Action.STOP) {
				LOGGER.info("carryingspeaker：检测到用户主动切到网易云，本轮不再接管窗口");
				return;
			}
			if (action == NcmStealDetector.Action.ACT && suppressForegroundCloudMusic(client)) {
				hit = true;
				LOGGER.info("carryingspeaker：网易云抢到前台，已收回托盘并把焦点还给 MC");
				break;
			}
			if (!sleepQuietly(POLL_INTERVAL_MS)) {
				return;
			}
		}
		if (!hit) {
			LOGGER.info("carryingspeaker：10 秒内没等到网易云窗口，未做窗口操作");
			return;
		}

		// 命中之后继续守一段时间：网易云有时会自己把窗口提回前台。
		final long guardDeadline = System.currentTimeMillis() + GUARD_AFTER_HIT_MS;
		final int pauseBefore = PAUSE_CLOSES.get();
		final int failsBefore = FOCUS_FAILS.get();
		NcmStealDetector guardDetector = new NcmStealDetector(classifyForeground());
		int extra = 0;
		boolean capped = false;
		while (System.currentTimeMillis() < guardDeadline) {
			NcmStealDetector.Action action = guardDetector.feed(classifyForeground());
			if (action == NcmStealDetector.Action.STOP) {
				LOGGER.info("carryingspeaker：检测到用户主动切到网易云，本轮不再接管窗口");
				break;
			}
			if (action == NcmStealDetector.Action.ACT && suppressForegroundCloudMusic(client)) {
				extra++;
				LOGGER.debug("carryingspeaker：守护期间再次把网易云收回托盘");
				if (extra >= MAX_GUARD_RESUPPRESS) {
					capped = true;
					break;
				}
			}
			if (!sleepQuietly(POLL_INTERVAL_MS)) {
				return;
			}
		}

		final int closed = PAUSE_CLOSES.get() - pauseBefore;
		final int focusFails = FOCUS_FAILS.get() - failsBefore;
		if (capped) {
			LOGGER.info("carryingspeaker：网易云反复抢占前台（≥{} 次），停止守护，不再争夺焦点；关闭暂停菜单 {} 次，焦点验证失败 {} 次",
					MAX_GUARD_RESUPPRESS, closed, focusFails);
		} else {
			LOGGER.info("carryingspeaker：守护结束，期间再夺回焦点 {} 次，关闭暂停菜单 {} 次，焦点验证失败 {} 次",
					extra, closed, focusFails);
		}
	}

	/**
	 * 当前前台是网易云就把它藏回托盘，并把焦点还给 MC。只对这一个窗口操作。
	 *
	 * @return true = 这一次确实接管了
	 */
	public static boolean suppressForegroundCloudMusic(Minecraft client) {
		User32 user32 = User32.INSTANCE;
		WinDef.HWND foreground = user32.GetForegroundWindow();
		if (foreground == null) {
			return false;
		}
		IntByReference pidRef = new IntByReference();
		user32.GetWindowThreadProcessId(foreground, pidRef);
		int pid = pidRef.getValue();
		if (pid <= 0 || !isCloudMusicProcess(pid)) {
			return false;
		}
		if (!user32.IsWindowVisible(foreground)) {
			return false;
		}
		user32.ShowWindow(foreground, SW_HIDE);
		focusMinecraft(client);
		return true;
	}

	/** 进程命令行以 cloudmusic.exe 结尾（不区分大小写）就算网易云。 */
	public static boolean isCloudMusicProcess(int pid) {
		return ProcessHandle.of(pid)
				.flatMap(handle -> handle.info().command())
				.map(path -> path.toLowerCase(Locale.ROOT).endsWith(EXPECTED_EXE))
				.orElse(false);
	}

	/** 找当前是否已经有网易云在跑。返回 pid，没有（或枚举失败）返回 -1。 */
	public static int findCloudMusicPid() {
		try {
			long pid = ProcessHandle.allProcesses()
					.filter(handle -> handle.info().command()
							.map(path -> path.toLowerCase(Locale.ROOT).endsWith(EXPECTED_EXE))
							.orElse(false))
					.mapToLong(ProcessHandle::pid)
					.findFirst()
					.orElse(-1L);
			return pid > 0 ? (int) pid : -1;
		} catch (Throwable t) {
			LOGGER.warn("carryingspeaker：枚举进程失败", t);
			return -1;
		}
	}

	/** 把焦点还给 MC，顺手关掉失焦时弹出的暂停菜单；焦点验证延后做。 */
	private static void focusMinecraft(Minecraft client) {
		final long mcHandle = client.getWindow().handle();
		client.execute(() -> {
			boolean iconified = GLFW.glfwGetWindowAttrib(mcHandle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
			boolean focused = GLFW.glfwGetWindowAttrib(mcHandle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
			boolean restored = false;
			if (iconified) {
				// 全屏 MC 失焦会被 AUTO_ICONIFY 最小化，最小化的窗口 glfwFocusWindow 不会还原。
				GLFW.glfwRestoreWindow(mcHandle);
				restored = true;
			}
			GLFW.glfwFocusWindow(mcHandle);
			closePauseScreenIfAny(client);
			LOGGER.debug("carryingspeaker：回焦前 iconified={} focused={}，已 restore={}", iconified, focused, restored);
		});
		Thread verifier = new Thread(() -> {
			if (!sleepQuietly(VERIFY_DELAY_MS)) {
				return;
			}
			client.execute(() -> verifyFocus(mcHandle));
		}, "carryingspeaker-focus-verify");
		verifier.setDaemon(true);
		verifier.start();
	}

	private static void closePauseScreenIfAny(Minecraft client) {
		try {
			if (client.gui.screen() instanceof PauseScreen) {
				client.gui.setScreen(null);
				PAUSE_CLOSES.incrementAndGet();
				LOGGER.debug("carryingspeaker：关掉了失焦时弹出的暂停菜单");
			}
		} catch (Throwable t) {
			LOGGER.warn("carryingspeaker：关闭暂停菜单失败", t);
		}
	}

	private static void verifyFocus(long mcHandle) {
		try {
			int iconified = GLFW.glfwGetWindowAttrib(mcHandle, GLFW.GLFW_ICONIFIED);
			int focused = GLFW.glfwGetWindowAttrib(mcHandle, GLFW.GLFW_FOCUSED);
			long mcHwnd = GLFWNativeWin32.glfwGetWin32Window(mcHandle);
			WinDef.HWND foreground = User32.INSTANCE.GetForegroundWindow();
			long fgValue = foreground == null ? 0L : Pointer.nativeValue(foreground.getPointer());
			boolean sameHwnd = mcHwnd != 0L && fgValue == mcHwnd;
			if (!sameHwnd) {
				FOCUS_FAILS.incrementAndGet();
			}
			LOGGER.debug("carryingspeaker：焦点验证 iconified={} focused={} 前台是MC={}（mc=0x{} fg=0x{}）",
					iconified, focused, sameHwnd, Long.toHexString(mcHwnd), Long.toHexString(fgValue));
		} catch (Throwable t) {
			LOGGER.debug("carryingspeaker：焦点验证跳过", t);
		}
	}

	/** sleep，被中断时返回 false 并保留中断标记。 */
	private static boolean sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
