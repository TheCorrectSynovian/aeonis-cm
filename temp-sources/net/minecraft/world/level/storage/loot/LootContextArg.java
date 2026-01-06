package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface LootContextArg<R> {
	Codec<LootContextArg<Object>> ENTITY_OR_BLOCK = createArgCodec(
		argCodecBuilder -> argCodecBuilder.anyOf(LootContext.EntityTarget.values()).anyOf(LootContext.BlockEntityTarget.values())
	);

	@Nullable
	R get(LootContext lootContext);

	ContextKey<?> contextParam();

	static <U> LootContextArg<U> cast(LootContextArg<? extends U> lootContextArg) {
		return (LootContextArg<U>)lootContextArg;
	}

	static <R> Codec<LootContextArg<R>> createArgCodec(UnaryOperator<LootContextArg.ArgCodecBuilder<R>> unaryOperator) {
		return ((LootContextArg.ArgCodecBuilder)unaryOperator.apply(new LootContextArg.ArgCodecBuilder())).build();
	}

	public static final class ArgCodecBuilder<R> {
		private final ExtraCodecs.LateBoundIdMapper<String, LootContextArg<R>> sources = new ExtraCodecs.LateBoundIdMapper<>();

		ArgCodecBuilder() {
		}

		public <T> LootContextArg.ArgCodecBuilder<R> anyOf(T[] objects, Function<T, String> function, Function<T, ? extends LootContextArg<R>> function2) {
			for (T object : objects) {
				this.sources.put((String)function.apply(object), (LootContextArg<R>)function2.apply(object));
			}

			return this;
		}

		public <T extends StringRepresentable> LootContextArg.ArgCodecBuilder<R> anyOf(T[] stringRepresentables, Function<T, ? extends LootContextArg<R>> function) {
			return this.anyOf(stringRepresentables, StringRepresentable::getSerializedName, function);
		}

		public <T extends StringRepresentable & LootContextArg<? extends R>> LootContextArg.ArgCodecBuilder<R> anyOf(T[] stringRepresentables) {
			return this.anyOf(stringRepresentables, object -> LootContextArg.cast((LootContextArg)object));
		}

		public LootContextArg.ArgCodecBuilder<R> anyEntity(Function<? super ContextKey<? extends Entity>, ? extends LootContextArg<R>> function) {
			return this.anyOf(LootContext.EntityTarget.values(), entityTarget -> (LootContextArg)function.apply(entityTarget.contextParam()));
		}

		public LootContextArg.ArgCodecBuilder<R> anyBlockEntity(Function<? super ContextKey<? extends BlockEntity>, ? extends LootContextArg<R>> function) {
			return this.anyOf(LootContext.BlockEntityTarget.values(), blockEntityTarget -> (LootContextArg)function.apply(blockEntityTarget.contextParam()));
		}

		public LootContextArg.ArgCodecBuilder<R> anyItemStack(Function<? super ContextKey<? extends ItemStack>, ? extends LootContextArg<R>> function) {
			return this.anyOf(LootContext.ItemStackTarget.values(), itemStackTarget -> (LootContextArg)function.apply(itemStackTarget.contextParam()));
		}

		Codec<LootContextArg<R>> build() {
			return this.sources.codec(Codec.STRING);
		}
	}

	public interface Getter<T, R> extends LootContextArg<R> {
		@Nullable
		R get(T object);

		@Override
		ContextKey<? extends T> contextParam();

		@Nullable
		@Override
		default R get(LootContext lootContext) {
			T object = lootContext.getOptionalParameter((ContextKey<T>)this.contextParam());
			return object != null ? this.get(object) : null;
		}
	}

	public interface SimpleGetter<T> extends LootContextArg<T> {
		@Override
		ContextKey<? extends T> contextParam();

		@Nullable
		@Override
		default T get(LootContext lootContext) {
			return lootContext.getOptionalParameter((ContextKey<T>)this.contextParam());
		}
	}
}
