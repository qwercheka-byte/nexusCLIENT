package dev.nexus.launcher.update;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Installs the native starter bundled in the already verified app package, without removing the old exe first. */
final class WindowsStarter {
    static final String EXE_NAME = "Nexus Launcher.exe";
    static final String BUNDLED_EXE = "dev/nexus/launcher/update/native/" + EXE_NAME;

    private WindowsStarter() {}

    /** Returns false if the starter is absent, locked, or cannot safely be replaced; next startup can retry. */
    static boolean refresh(Path app, int attempts, long delayMillis) throws IOException, InterruptedException {
        Path dir = app.getParent();
        if (dir == null) return false;
        Path source = app.resolve(BUNDLED_EXE);
        Path target = dir.resolve(EXE_NAME);
        if (!Files.isRegularFile(source) || !Files.isRegularFile(target)) return false;

        // The lock file is intentionally retained: deleting it would let two processes lock different files.
        try (FileChannel channel = FileChannel.open(dir.resolve(".nexus-starter.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                return false;
            }
            if (lock == null) return false;
            try (lock) {
                if (Files.mismatch(source, target) == -1) return true;
                Path temp = Files.createTempFile(dir, ".nexus-starter-", ".tmp");
                try {
                    Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
                    IOException last = null;
                    for (int i = 0; i < attempts; i++) {
                        try {
                            // On Windows the old starter can be locked briefly after starting javaw.
                            // An atomic replace keeps the old executable intact on failure.
                            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                            return true;
                        } catch (AtomicMoveNotSupportedException e) {
                            throw e; // never delete the old starter to work around a failed atomic move
                        } catch (IOException e) {
                            last = e;
                            if (i + 1 < attempts) Thread.sleep(delayMillis);
                        }
                    }
                    if (last != null) throw last;
                    return false;
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
        }
    }
}
