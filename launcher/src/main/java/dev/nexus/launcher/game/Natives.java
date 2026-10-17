package dev.nexus.launcher.game;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extracts native libraries (.dll/.so/.dylib) from library jars into the natives directory of a launch. */
public final class Natives {
    private Natives() {}

    /**
     * @param flattenNatives place native files directly in {@code dir} (modern "natives-xxx" jars keep them in
     *                       sub-folders such as linux/x64/org/lwjgl/, but java.library.path is not recursive)
     */
    public static void extract(Path jar, Path dir, List<String> exclude, boolean flattenNatives) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory() || isExcluded(name, exclude)) continue;
                boolean nativeFile = isNative(name);
                if (flattenNatives && !nativeFile) continue;
                String rel = flattenNatives ? name.substring(name.lastIndexOf('/') + 1) : name;
                Path target = dir.resolve(rel).normalize();
                if (!target.startsWith(dir)) continue; // zip-slip protection
                if (Files.exists(target) && Files.size(target) == e.getSize()) continue;
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    // On Windows a running game keeps its DLLs locked; an identical file is already in place.
                    if (!Files.exists(target)) throw ex;
                }
            }
        }
    }

    private static boolean isExcluded(String name, List<String> exclude) {
        if (name.startsWith("META-INF/")) return true;
        if (exclude != null) for (String ex : exclude) if (name.startsWith(ex)) return true;
        return false;
    }

    private static boolean isNative(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".dll") || n.endsWith(".so") || n.endsWith(".dylib") || n.endsWith(".jnilib");
    }
}
