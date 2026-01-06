package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public interface ClientAsset {
	Identifier id();

	public record DownloadedTexture(Identifier texturePath, String url) implements ClientAsset.Texture {
		@Override
		public Identifier id() {
			return this.texturePath;
		}
	}

	public record ResourceTexture(Identifier id, Identifier texturePath) implements ClientAsset.Texture {
		public static final Codec<ClientAsset.ResourceTexture> CODEC = Identifier.CODEC.xmap(ClientAsset.ResourceTexture::new, ClientAsset.ResourceTexture::id);
		public static final MapCodec<ClientAsset.ResourceTexture> DEFAULT_FIELD_CODEC = CODEC.fieldOf("asset_id");
		public static final StreamCodec<ByteBuf, ClientAsset.ResourceTexture> STREAM_CODEC = Identifier.STREAM_CODEC
			.map(ClientAsset.ResourceTexture::new, ClientAsset.ResourceTexture::id);

		public ResourceTexture(Identifier identifier) {
			this(identifier, identifier.withPath((UnaryOperator<String>)(string -> "textures/" + string + ".png")));
		}
	}

	public interface Texture extends ClientAsset {
		Identifier texturePath();
	}
}
