package com.menatarli.ncmplayer;

import java.util.regex.Pattern;

/**
 * 服务端与客户端共用的点播请求校验。纯逻辑，不引用任何 Minecraft 类。
 *
 * <p>发送者名字会被写进聊天栏，所以只接受 {@code [A-Za-z0-9_]{1,16}}，
 * 挡住 {@code §} 格式码、超长名字和其他奇怪字符。
 */
public final class NcmPlayRequest {
	/** 合法的发送者名字：1-16 位 ASCII 字母、数字、下划线。 */
	private static final Pattern SENDER_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");

	/** 名字不合法时统一显示成这个。 */
	public static final String FALLBACK_SENDER = "管理员";

	private NcmPlayRequest() {
	}

	/** 只认单曲和歌单。 */
	public static boolean isValidType(String type) {
		return NcmPlayer.TYPE_SONG.equals(type) || NcmPlayer.TYPE_PLAYLIST.equals(type);
	}

	/** 符合 {@code [A-Za-z0-9_]{1,16}} 就原样返回，否则返回 {@link #FALLBACK_SENDER}。 */
	public static String sanitizeSender(String sender) {
		if (sender != null && SENDER_PATTERN.matcher(sender).matches()) {
			return sender;
		}
		return FALLBACK_SENDER;
	}
}
