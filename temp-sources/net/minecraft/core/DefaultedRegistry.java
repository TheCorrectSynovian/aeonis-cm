package net.minecraft.core;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface DefaultedRegistry<T> extends Registry<T> {
	@NonNull
	@Override
	Identifier getKey(T object);

	@NonNull
	@Override
	T getValue(@Nullable Identifier identifier);

	@NonNull
	@Override
	T byId(int i);

	Identifier getDefaultKey();
}
