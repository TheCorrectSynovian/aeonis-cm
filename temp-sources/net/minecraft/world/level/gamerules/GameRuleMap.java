package net.minecraft.world.level.gamerules;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jspecify.annotations.Nullable;

public final class GameRuleMap {
	public static final Codec<GameRuleMap> CODEC = Codec.dispatchedMap(BuiltInRegistries.GAME_RULE.byNameCodec(), GameRule::valueCodec)
		.xmap(GameRuleMap::ofTrusted, GameRuleMap::map);
	private final Reference2ObjectMap<GameRule<?>, Object> map;

	GameRuleMap(Reference2ObjectMap<GameRule<?>, Object> reference2ObjectMap) {
		this.map = reference2ObjectMap;
	}

	private static GameRuleMap ofTrusted(Map<GameRule<?>, Object> map) {
		return new GameRuleMap(new Reference2ObjectOpenHashMap<>(map));
	}

	public static GameRuleMap of() {
		return new GameRuleMap(new Reference2ObjectOpenHashMap<>());
	}

	public static GameRuleMap of(Stream<GameRule<?>> stream) {
		Reference2ObjectOpenHashMap<GameRule<?>, Object> reference2ObjectOpenHashMap = new Reference2ObjectOpenHashMap<>();
		stream.forEach(gameRule -> reference2ObjectOpenHashMap.put(gameRule, gameRule.defaultValue()));
		return new GameRuleMap(reference2ObjectOpenHashMap);
	}

	public static GameRuleMap copyOf(GameRuleMap gameRuleMap) {
		return new GameRuleMap(new Reference2ObjectOpenHashMap<>(gameRuleMap.map));
	}

	public boolean has(GameRule<?> gameRule) {
		return this.map.containsKey(gameRule);
	}

	@Nullable
	public <T> T get(GameRule<T> gameRule) {
		return (T)this.map.get(gameRule);
	}

	public <T> void set(GameRule<T> gameRule, T object) {
		this.map.put(gameRule, object);
	}

	@Nullable
	public <T> T remove(GameRule<T> gameRule) {
		return (T)this.map.remove(gameRule);
	}

	public Set<GameRule<?>> keySet() {
		return this.map.keySet();
	}

	public int size() {
		return this.map.size();
	}

	public String toString() {
		return this.map.toString();
	}

	public GameRuleMap withOther(GameRuleMap gameRuleMap) {
		GameRuleMap gameRuleMap2 = copyOf(this);
		gameRuleMap2.setFromIf(gameRuleMap, gameRule -> true);
		return gameRuleMap2;
	}

	public void setFromIf(GameRuleMap gameRuleMap, Predicate<GameRule<?>> predicate) {
		for (GameRule<?> gameRule : gameRuleMap.keySet()) {
			if (predicate.test(gameRule)) {
				setGameRule(gameRuleMap, gameRule, this);
			}
		}
	}

	private static <T> void setGameRule(GameRuleMap gameRuleMap, GameRule<T> gameRule, GameRuleMap gameRuleMap2) {
		gameRuleMap2.set(gameRule, (T)Objects.requireNonNull(gameRuleMap.get(gameRule)));
	}

	private Reference2ObjectMap<GameRule<?>, Object> map() {
		return this.map;
	}

	public boolean equals(Object object) {
		if (object == this) {
			return true;
		} else if (object != null && object.getClass() == this.getClass()) {
			GameRuleMap gameRuleMap = (GameRuleMap)object;
			return Objects.equals(this.map, gameRuleMap.map);
		} else {
			return false;
		}
	}

	public int hashCode() {
		return Objects.hash(new Object[]{this.map});
	}

	public static class Builder {
		final Reference2ObjectMap<GameRule<?>, Object> map = new Reference2ObjectOpenHashMap<>();

		public <T> GameRuleMap.Builder set(GameRule<T> gameRule, T object) {
			this.map.put(gameRule, object);
			return this;
		}

		public GameRuleMap build() {
			return new GameRuleMap(this.map);
		}
	}
}
