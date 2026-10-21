package com.menatarli.ncmplayer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * 服务端 → 客户端的点播请求包。main 源集，专用服务器和客户端都会加载。
 *
 * <p>只有这一个方向：客户端从不主动发 C2S 包，所以服务端没装模组时也不会出现未知包断线。
 */
public final class NcmNetworking {
	/** 包 id：{@code carryingspeaker:play}。 */
	public static final Identifier PLAY_PAYLOAD_ID = Identifier.fromNamespaceAndPath("carryingspeaker", "play");

	private NcmNetworking() {
	}

	/**
	 * 点播请求：类型 + id + 发送者名字。字段长度都带上限，超长的包直接解不出来。
	 *
	 * <p>第一个组件叫 {@code playType} 而不是 {@code type}：record 的存取方法会和
	 * {@link CustomPacketPayload#type()} 撞名，接口要求它返回 {@code Type<?>}。
	 */
	public record PlayNcmPayload(String playType, String id, String sender) implements CustomPacketPayload {
		public static final Type<PlayNcmPayload> TYPE = new Type<>(PLAY_PAYLOAD_ID);

		public static final StreamCodec<RegistryFriendlyByteBuf, PlayNcmPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(16), PlayNcmPayload::playType,
				ByteBufCodecs.stringUtf8(20), PlayNcmPayload::id,
				ByteBufCodecs.stringUtf8(16), PlayNcmPayload::sender,
				PlayNcmPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 注册 S2C payload。在通用初始化阶段调用。 */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(PlayNcmPayload.TYPE, PlayNcmPayload.CODEC);
	}
}
