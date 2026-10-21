package com.menatarli.ncmplayer;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 待确认的远程点播请求。纯逻辑，不引用任何 Minecraft 类。
 *
 * <p>token 只在客户端生成，服务端从头到尾不知道它存在。请求 60 秒过期，最多同时保存 20 条，
 * 满了淘汰最旧的一条。同一个 token 取过一次就作废。
 */
public final class NcmPendingRequests {
	/** 请求有效期。 */
	public static final long TTL_MILLIS = 60_000L;
	/** 同时保存的最大条数。 */
	public static final int MAX_SIZE = 20;
	/** token 长度。 */
	public static final int TOKEN_LENGTH = 8;

	/** 一条待确认的点播请求。 */
	public record Request(String type, String id, String sender, long createdAt) {
		/** 是否已经过期。 */
		public boolean expired(long nowMillis) {
			return nowMillis - createdAt > TTL_MILLIS;
		}
	}

	private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	/** 按插入顺序保存，方便淘汰最旧的。 */
	private final Map<String, Request> pending = new LinkedHashMap<>();

	/** 存一条请求，返回新生成的 token（8 位 [a-z0-9]）。 */
	public String add(String type, String id, String sender, long nowMillis) {
		evictExpired(nowMillis);
		while (pending.size() >= MAX_SIZE) {
			Iterator<String> iterator = pending.keySet().iterator();
			iterator.next();
			iterator.remove();
		}
		String token = newToken();
		pending.put(token, new Request(type, id, sender, nowMillis));
		return token;
	}

	/** 取出并删除一条请求。不存在、已用过、已过期都返回空。 */
	public Optional<Request> take(String token, long nowMillis) {
		if (token == null) {
			return Optional.empty();
		}
		Request request = pending.remove(token);
		if (request == null || request.expired(nowMillis)) {
			return Optional.empty();
		}
		return Optional.of(request);
	}

	/** 当前保存的条数（测试用）。 */
	public int size() {
		return pending.size();
	}

	private void evictExpired(long nowMillis) {
		pending.entrySet().removeIf(entry -> entry.getValue().expired(nowMillis));
	}

	private static String newToken() {
		char[] buffer = new char[TOKEN_LENGTH];
		for (int i = 0; i < buffer.length; i++) {
			buffer[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
		}
		return new String(buffer);
	}
}
