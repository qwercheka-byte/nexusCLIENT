package dev.nexus.launcher.game;

import com.google.gson.JsonElement;

import java.util.List;

/**
 * A version with its whole {@code inheritsFrom} chain merged: what is actually launched.
 *
 * @param id          id of the most specific version (e.g. fabric-loader-0.17.2-1.21.11); becomes ${version_name}
 * @param baseId      id of the root (vanilla) version that owns the client jar
 * @param libraries   libraries of the whole chain, child entries first, de-duplicated by group:artifact:classifier
 * @param legacyArgs  pre-1.13 "minecraftArguments" (null when the root version uses the modern format);
 *                    modern {@code gameArgs} of loader profiles are appended to it
 * @param modernJvm   true when the root version declares its own JVM arguments (1.13+); otherwise the
 *                    launcher supplies the classic defaults (-Djava.library.path, -cp)
 */
public record ResolvedVersion(
        String id,
        String baseId,
        String type,
        String mainClass,
        String assetsId,
        VersionJson.AssetIndexRef assetIndex,
        VersionJson.Download client,
        VersionJson.JavaVersion javaVersion,
        VersionJson.Logging logging,
        List<Library> libraries,
        List<JsonElement> gameArgs,
        List<JsonElement> jvmArgs,
        String legacyArgs,
        boolean modernJvm,
        boolean inherited) {
}
