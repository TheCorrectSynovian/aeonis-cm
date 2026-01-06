package net.minecraft.stats;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class ServerStatsCounter extends StatsCounter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Codec<Map<Stat<?>, Integer>> STATS_CODEC = Codec.dispatchedMap(
			BuiltInRegistries.STAT_TYPE.byNameCodec(), Util.memoize(ServerStatsCounter::createTypedStatsCodec)
		)
		.xmap(map -> {
			Map<Stat<?>, Integer> map2 = new HashMap();
			map.forEach((statType, map2x) -> map2.putAll(map2x));
			return map2;
		}, map -> (Map)map.entrySet().stream().collect(Collectors.groupingBy(entry -> ((Stat)entry.getKey()).getType(), Util.toMap())));
	private final Path file;
	private final Set<Stat<?>> dirty = Sets.<Stat<?>>newHashSet();

	private static <T> Codec<Map<Stat<?>, Integer>> createTypedStatsCodec(StatType<T> statType) {
		Codec<T> codec = statType.getRegistry().byNameCodec();
		Codec<Stat<?>> codec2 = codec.flatComapMap(
			statType::get,
			stat -> stat.getType() == statType
				? DataResult.success(stat.getValue())
				: DataResult.error(() -> "Expected type " + statType + ", but got " + stat.getType())
		);
		return Codec.unboundedMap(codec2, Codec.INT);
	}

	public ServerStatsCounter(MinecraftServer minecraftServer, Path path) {
		this.file = path;
		if (Files.isRegularFile(path, new LinkOption[0])) {
			try {
				Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);

				try {
					JsonElement jsonElement = StrictJsonParser.parse(reader);
					this.parse(minecraftServer.getFixerUpper(), jsonElement);
				} catch (Throwable var7) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var6) {
							var7.addSuppressed(var6);
						}
					}

					throw var7;
				}

				if (reader != null) {
					reader.close();
				}
			} catch (IOException var8) {
				LOGGER.error("Couldn't read statistics file {}", path, var8);
			} catch (JsonParseException var9) {
				LOGGER.error("Couldn't parse statistics file {}", path, var9);
			}
		}
	}

	public void save() {
		try {
			FileUtil.createDirectoriesSafe(this.file.getParent());
			Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8);

			try {
				GSON.toJson(this.toJson(), GSON.newJsonWriter(writer));
			} catch (Throwable var5) {
				if (writer != null) {
					try {
						writer.close();
					} catch (Throwable var4) {
						var5.addSuppressed(var4);
					}
				}

				throw var5;
			}

			if (writer != null) {
				writer.close();
			}
		} catch (JsonIOException | IOException var6) {
			LOGGER.error("Couldn't save stats to {}", this.file, var6);
		}
	}

	@Override
	public void setValue(Player player, Stat<?> stat, int i) {
		super.setValue(player, stat, i);
		this.dirty.add(stat);
	}

	private Set<Stat<?>> getDirty() {
		Set<Stat<?>> set = Sets.<Stat<?>>newHashSet(this.dirty);
		this.dirty.clear();
		return set;
	}

	public void parse(DataFixer dataFixer, JsonElement jsonElement) {
		Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonElement);
		dynamic = DataFixTypes.STATS.updateToCurrentVersion(dataFixer, dynamic, NbtUtils.getDataVersion(dynamic, 1343));
		this.stats
			.putAll(
				(Map)STATS_CODEC.parse(dynamic.get("stats").orElseEmptyMap())
					.resultOrPartial(string -> LOGGER.error("Failed to parse statistics for {}: {}", this.file, string))
					.orElse(Map.of())
			);
	}

	protected JsonElement toJson() {
		JsonObject jsonObject = new JsonObject();
		jsonObject.add("stats", STATS_CODEC.encodeStart(JsonOps.INSTANCE, this.stats).getOrThrow());
		jsonObject.addProperty("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		return jsonObject;
	}

	public void markAllDirty() {
		this.dirty.addAll(this.stats.keySet());
	}

	public void sendStats(ServerPlayer serverPlayer) {
		Object2IntMap<Stat<?>> object2IntMap = new Object2IntOpenHashMap<>();

		for (Stat<?> stat : this.getDirty()) {
			object2IntMap.put(stat, this.getValue(stat));
		}

		serverPlayer.connection.send(new ClientboundAwardStatsPacket(object2IntMap));
	}
}
