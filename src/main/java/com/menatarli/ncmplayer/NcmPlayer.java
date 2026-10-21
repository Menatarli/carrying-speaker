package com.menatarli.ncmplayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 网易云音乐客户端的 orpheus 协议拉起逻辑。
 *
 * <p>只做三件事：校验 id、把 JSON 编码成 {@code orpheus://<base64>}、在 Windows 上把它交给系统处理程序。
 * 不联网、不登录、不解析任何网易云 API。
 */
public final class NcmPlayer {
	/** orpheus 协议前缀。 */
	public static final String URL_PREFIX = "orpheus://";
	/** 单曲。 */
	public static final String TYPE_SONG = "song";
	/** 歌单。 */
	public static final String TYPE_PLAYLIST = "playlist";

	/** 1-20 位纯 ASCII 数字。 */
	private static final Pattern ID_PATTERN = Pattern.compile("[0-9]{1,20}");

	private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

	private NcmPlayer() {
	}

	/** 只接受 1-20 位纯数字，其余（null、空串、字母、空格、引号、&、超长）都返回 false。 */
	public static boolean isValidId(String id) {
		return id != null && ID_PATTERN.matcher(id).matches();
	}

	/**
	 * 拼出拉起网易云的 URL。JSON 固定为 {@code {"type":<type>,"id":<id>,"cmd":"play"}}（无空格、字段顺序固定），
	 * 再用 UTF-8 + 标准 Base64 编码。
	 *
	 * @throws IllegalArgumentException type 不是 song / playlist，或 id 不合法
	 */
	public static String buildUrl(String type, String id) {
		if (!TYPE_SONG.equals(type) && !TYPE_PLAYLIST.equals(type)) {
			throw new IllegalArgumentException("不支持的 type：" + type + "（只接受 song / playlist）");
		}
		if (!isValidId(id)) {
			throw new IllegalArgumentException("不合法的 id：" + id + "（只接受 1-20 位纯数字）");
		}
		String json = "{\"type\":\"" + type + "\",\"id\":\"" + id + "\",\"cmd\":\"play\"}";
		return URL_PREFIX + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	/** 当前系统是不是 Windows。 */
	public static boolean isWindows() {
		return OS_NAME.startsWith("windows");
	}

	/**
	 * 交给系统打开 URL。非 Windows 直接抛 {@link UnsupportedOperationException}。
	 *
	 * <p>用参数数组形式调 ProcessBuilder，绝不拼命令行字符串；不等进程结束。
	 *
	 * @throws IOException 拉起 rundll32 失败
	 * @throws UnsupportedOperationException 非 Windows 系统
	 */
	public static void open(String url) throws IOException {
		if (!isWindows()) {
			throw new UnsupportedOperationException("拉起网易云客户端只在 Windows 上可用");
		}
		new ProcessBuilder("rundll32.exe", "url.dll,FileProtocolHandler", url).start();
	}
}
