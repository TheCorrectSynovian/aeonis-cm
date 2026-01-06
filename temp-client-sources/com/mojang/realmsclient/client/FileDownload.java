package com.mojang.realmsclient.client;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.WorldDownload;
import com.mojang.realmsclient.exception.RealmsDefaultUncaughtExceptionHandler;
import com.mojang.realmsclient.gui.screens.RealmsDownloadLatestWorldScreen;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelDirectory;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.validation.ContentValidationException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class FileDownload {
	private static final Logger LOGGER = LogUtils.getLogger();
	private volatile boolean cancelled;
	private volatile boolean finished;
	private volatile boolean error;
	private volatile boolean extracting;
	@Nullable
	private volatile File tempFile;
	private volatile File resourcePackPath;
	@Nullable
	private volatile CompletableFuture<?> pendingRequest;
	@Nullable
	private Thread currentThread;
	private static final String[] INVALID_FILE_NAMES = new String[]{
		"CON",
		"COM",
		"PRN",
		"AUX",
		"CLOCK$",
		"NUL",
		"COM1",
		"COM2",
		"COM3",
		"COM4",
		"COM5",
		"COM6",
		"COM7",
		"COM8",
		"COM9",
		"LPT1",
		"LPT2",
		"LPT3",
		"LPT4",
		"LPT5",
		"LPT6",
		"LPT7",
		"LPT8",
		"LPT9"
	};

	@Nullable
	private <T> T joinCancellableRequest(CompletableFuture<T> completableFuture) throws Throwable {
		this.pendingRequest = completableFuture;
		if (this.cancelled) {
			completableFuture.cancel(true);
			return null;
		} else {
			try {
				try {
					return (T)completableFuture.join();
				} catch (CompletionException var3) {
					throw var3.getCause();
				}
			} catch (CancellationException var4) {
				return null;
			}
		}
	}

	private static HttpClient createClient() {
		return HttpClient.newBuilder().executor(Util.nonCriticalIoPool()).connectTimeout(Duration.ofMinutes(2L)).build();
	}

	private static Builder createRequest(String string) {
		return HttpRequest.newBuilder(URI.create(string)).timeout(Duration.ofMinutes(2L));
	}

	@CheckReturnValue
	public static OptionalLong contentLength(String string) {
		try {
			HttpClient httpClient = createClient();

			OptionalLong var3;
			try {
				HttpResponse<Void> httpResponse = httpClient.send(createRequest(string).HEAD().build(), BodyHandlers.discarding());
				var3 = httpResponse.headers().firstValueAsLong("Content-Length");
			} catch (Throwable var5) {
				if (httpClient != null) {
					try {
						httpClient.close();
					} catch (Throwable var4) {
						var5.addSuppressed(var4);
					}
				}

				throw var5;
			}

			if (httpClient != null) {
				httpClient.close();
			}

			return var3;
		} catch (Exception var6) {
			LOGGER.error("Unable to get content length for download");
			return OptionalLong.empty();
		}
	}

	public void download(
		WorldDownload worldDownload, String string, RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus, LevelStorageSource levelStorageSource
	) {
		if (this.currentThread == null) {
			this.currentThread = new Thread(() -> {
				HttpClient httpClient = createClient();

				label205: {
					try {
						try {
							this.tempFile = File.createTempFile("backup", ".tar.gz");
							this.download(downloadStatus, httpClient, worldDownload.downloadLink(), this.tempFile);
							this.finishWorldDownload(string.trim(), this.tempFile, levelStorageSource, downloadStatus);
						} catch (Exception var23) {
							LOGGER.error("Caught exception while downloading world", (Throwable)var23);
							this.error = true;
						} finally {
							this.pendingRequest = null;
							if (this.tempFile != null) {
								this.tempFile.delete();
							}

							this.tempFile = null;
						}

						if (this.error) {
							break label205;
						}

						String string2 = worldDownload.resourcePackUrl();
						if (!string2.isEmpty() && !worldDownload.resourcePackHash().isEmpty()) {
							try {
								this.tempFile = File.createTempFile("resources", ".tar.gz");
								this.download(downloadStatus, httpClient, string2, this.tempFile);
								this.finishResourcePackDownload(downloadStatus, this.tempFile, worldDownload);
							} catch (Exception var22) {
								LOGGER.error("Caught exception while downloading resource pack", (Throwable)var22);
								this.error = true;
							} finally {
								this.pendingRequest = null;
								if (this.tempFile != null) {
									this.tempFile.delete();
								}

								this.tempFile = null;
							}
						}

						this.finished = true;
					} catch (Throwable var26) {
						if (httpClient != null) {
							try {
								httpClient.close();
							} catch (Throwable var21) {
								var26.addSuppressed(var21);
							}
						}

						throw var26;
					}

					if (httpClient != null) {
						httpClient.close();
					}

					return;
				}

				if (httpClient != null) {
					httpClient.close();
				}
			});
			this.currentThread.setUncaughtExceptionHandler(new RealmsDefaultUncaughtExceptionHandler(LOGGER));
			this.currentThread.start();
		}
	}

	private void download(RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus, HttpClient httpClient, String string, File file) throws IOException {
		HttpRequest httpRequest = createRequest(string).GET().build();

		HttpResponse<InputStream> httpResponse;
		try {
			httpResponse = this.joinCancellableRequest(httpClient.sendAsync(httpRequest, BodyHandlers.ofInputStream()));
		} catch (Error var14) {
			throw var14;
		} catch (Throwable var15) {
			LOGGER.error("Failed to download {}", string, var15);
			this.error = true;
			return;
		}

		if (httpResponse != null && !this.cancelled) {
			if (httpResponse.statusCode() != 200) {
				this.error = true;
			} else {
				downloadStatus.totalBytes = httpResponse.headers().firstValueAsLong("Content-Length").orElse(0L);
				InputStream inputStream = (InputStream)httpResponse.body();

				try {
					OutputStream outputStream = new FileOutputStream(file);

					try {
						inputStream.transferTo(new FileDownload.DownloadCountingOutputStream(outputStream, downloadStatus));
					} catch (Throwable var13) {
						try {
							outputStream.close();
						} catch (Throwable var12) {
							var13.addSuppressed(var12);
						}

						throw var13;
					}

					outputStream.close();
				} catch (Throwable var16) {
					if (inputStream != null) {
						try {
							inputStream.close();
						} catch (Throwable var11) {
							var16.addSuppressed(var11);
						}
					}

					throw var16;
				}

				if (inputStream != null) {
					inputStream.close();
				}
			}
		}
	}

	public void cancel() {
		if (this.tempFile != null) {
			this.tempFile.delete();
			this.tempFile = null;
		}

		this.cancelled = true;
		CompletableFuture<?> completableFuture = this.pendingRequest;
		if (completableFuture != null) {
			completableFuture.cancel(true);
		}
	}

	public boolean isFinished() {
		return this.finished;
	}

	public boolean isError() {
		return this.error;
	}

	public boolean isExtracting() {
		return this.extracting;
	}

	public static String findAvailableFolderName(String string) {
		string = string.replaceAll("[\\./\"]", "_");

		for (String string2 : INVALID_FILE_NAMES) {
			if (string.equalsIgnoreCase(string2)) {
				string = "_" + string + "_";
			}
		}

		return string;
	}

	private void untarGzipArchive(String string, @Nullable File file, LevelStorageSource levelStorageSource) throws IOException {
		Pattern pattern = Pattern.compile(".*-([0-9]+)$");
		int i = 1;

		for (char c : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
			string = string.replace(c, '_');
		}

		if (StringUtils.isEmpty(string)) {
			string = "Realm";
		}

		string = findAvailableFolderName(string);

		try {
			for (LevelDirectory levelDirectory : levelStorageSource.findLevelCandidates()) {
				String string2 = levelDirectory.directoryName();
				if (string2.toLowerCase(Locale.ROOT).startsWith(string.toLowerCase(Locale.ROOT))) {
					Matcher matcher = pattern.matcher(string2);
					if (matcher.matches()) {
						int j = Integer.parseInt(matcher.group(1));
						if (j > i) {
							i = j;
						}
					} else {
						i++;
					}
				}
			}
		} catch (Exception var43) {
			LOGGER.error("Error getting level list", (Throwable)var43);
			this.error = true;
			return;
		}

		String string3;
		if (levelStorageSource.isNewLevelIdAcceptable(string) && i <= 1) {
			string3 = string;
		} else {
			string3 = string + (i == 1 ? "" : "-" + i);
			if (!levelStorageSource.isNewLevelIdAcceptable(string3)) {
				boolean bl = false;

				while (!bl) {
					i++;
					string3 = string + (i == 1 ? "" : "-" + i);
					if (levelStorageSource.isNewLevelIdAcceptable(string3)) {
						bl = true;
					}
				}
			}
		}

		TarArchiveInputStream tarArchiveInputStream = null;
		File file2 = new File(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "saves");

		try {
			file2.mkdir();
			tarArchiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file))));

			for (TarArchiveEntry tarArchiveEntry = tarArchiveInputStream.getNextTarEntry();
				tarArchiveEntry != null;
				tarArchiveEntry = tarArchiveInputStream.getNextTarEntry()
			) {
				File file3 = new File(file2, tarArchiveEntry.getName().replace("world", string3));
				if (tarArchiveEntry.isDirectory()) {
					file3.mkdirs();
				} else {
					file3.createNewFile();
					FileOutputStream fileOutputStream = new FileOutputStream(file3);

					try {
						IOUtils.copy(tarArchiveInputStream, fileOutputStream);
					} catch (Throwable var37) {
						try {
							fileOutputStream.close();
						} catch (Throwable var36) {
							var37.addSuppressed(var36);
						}

						throw var37;
					}

					fileOutputStream.close();
				}
			}
		} catch (Exception var41) {
			LOGGER.error("Error extracting world", (Throwable)var41);
			this.error = true;
		} finally {
			if (tarArchiveInputStream != null) {
				tarArchiveInputStream.close();
			}

			if (file != null) {
				file.delete();
			}

			try {
				LevelStorageAccess levelStorageAccess2 = levelStorageSource.validateAndCreateAccess(string3);

				try {
					levelStorageAccess2.renameAndDropPlayer(string3);
				} catch (Throwable var38) {
					if (levelStorageAccess2 != null) {
						try {
							levelStorageAccess2.close();
						} catch (Throwable var35) {
							var38.addSuppressed(var35);
						}
					}

					throw var38;
				}

				if (levelStorageAccess2 != null) {
					levelStorageAccess2.close();
				}
			} catch (NbtException | ReportedNbtException | IOException var39) {
				LOGGER.error("Failed to modify unpacked realms level {}", string3, var39);
			} catch (ContentValidationException var40) {
				LOGGER.warn("Failed to download file", (Throwable)var40);
			}

			this.resourcePackPath = new File(file2, string3 + File.separator + "resources.zip");
		}
	}

	private void finishWorldDownload(
		String string, File file, LevelStorageSource levelStorageSource, RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus
	) {
		if (downloadStatus.bytesWritten >= downloadStatus.totalBytes && !this.cancelled && !this.error) {
			try {
				this.extracting = true;
				this.untarGzipArchive(string, file, levelStorageSource);
			} catch (IOException var6) {
				LOGGER.error("Error extracting archive", (Throwable)var6);
				this.error = true;
			}
		}
	}

	private void finishResourcePackDownload(RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus, File file, WorldDownload worldDownload) {
		if (downloadStatus.bytesWritten >= downloadStatus.totalBytes && !this.cancelled) {
			try {
				String string = Hashing.sha1().hashBytes(Files.toByteArray(file)).toString();
				if (string.equals(worldDownload.resourcePackHash())) {
					FileUtils.copyFile(file, this.resourcePackPath);
					this.finished = true;
				} else {
					LOGGER.error("Resourcepack had wrong hash (expected {}, found {}). Deleting it.", worldDownload.resourcePackHash(), string);
					FileUtils.deleteQuietly(file);
					this.error = true;
				}
			} catch (IOException var5) {
				LOGGER.error("Error copying resourcepack file: {}", var5.getMessage());
				this.error = true;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class DownloadCountingOutputStream extends CountingOutputStream {
		private final RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus;

		public DownloadCountingOutputStream(OutputStream outputStream, RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus) {
			super(outputStream);
			this.downloadStatus = downloadStatus;
		}

		@Override
		protected void afterWrite(int i) throws IOException {
			super.afterWrite(i);
			this.downloadStatus.bytesWritten = this.getByteCount();
		}
	}
}
