package dev.nexus.launcher.core;

import java.io.File;
import java.util.Locale;

/** Operating system / architecture detection in the vocabulary used by Mojang's version files. */
public final class OS {
    public enum Type { WINDOWS, MACOS, LINUX, OTHER }

    public static final Type TYPE;
    /** Normalised architecture: x86_64, x86, arm64 or arm32 (anything else is passed through). */
    public static final String ARCH;
    public static final String VERSION = System.getProperty("os.version", "");

    static {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) TYPE = Type.WINDOWS;
        else if (os.contains("mac") || os.contains("darwin")) TYPE = Type.MACOS;
        else if (os.contains("linux") || os.contains("nix") || os.contains("bsd")) TYPE = Type.LINUX;
        else TYPE = Type.OTHER;

        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        ARCH = switch (arch) {
            case "amd64", "x86_64", "x64" -> "x86_64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "aarch64", "arm64" -> "arm64";
            case "arm", "armv7", "armv7l", "aarch32" -> "arm32";
            default -> arch;
        };
    }

    private OS() {}

    /** Name used in "os.name" rules of version JSON files. */
    public static String ruleName() {
        return switch (TYPE) {
            case WINDOWS -> "windows";
            case MACOS -> "osx";
            case LINUX -> "linux";
            default -> "unknown";
        };
    }

    public static boolean is64Bit() {
        return ARCH.equals("x86_64") || ARCH.equals("arm64");
    }

    /** Replacement for ${arch} in legacy native classifiers ("natives-windows-${arch}"). */
    public static String archBits() {
        return is64Bit() ? "64" : "32";
    }

    /**
     * Platform key of Mojang's Java runtime index, or {@code null} when Mojang does not publish runtimes
     * for this machine (for example Linux on ARM).
     */
    public static String runtimePlatform() {
        return switch (TYPE) {
            case WINDOWS -> switch (ARCH) {
                case "arm64" -> "windows-arm64";
                case "x86" -> "windows-x86";
                default -> "windows-x64";
            };
            case MACOS -> ARCH.equals("arm64") ? "mac-os-arm64" : "mac-os";
            case LINUX -> switch (ARCH) {
                case "x86_64" -> "linux";
                case "x86" -> "linux-i386";
                default -> null;
            };
            default -> null;
        };
    }

    public static String classpathSeparator() {
        return File.pathSeparator;
    }

    public static boolean isWindows() { return TYPE == Type.WINDOWS; }

    public static boolean isMac() { return TYPE == Type.MACOS; }

    /** Total physical memory in MiB, or -1 when it cannot be determined. */
    public static long totalMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize() / (1024 * 1024);
            }
        } catch (Throwable ignored) {
            // jdk.management not available: fall through
        }
        return -1;
    }
}
