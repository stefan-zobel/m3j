// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.OS;

/**
 * The interface OSConfig of libm3: the host and the user, as cm3's
 * OSConfigPosix or OSConfigWin32 reports them, after the system that the
 * program runs on (m3j.rt.OS). The constants of the interface (ArcSep,
 * PathSep, LineSep) are those of the system that the compiler ran on.
 */
public final class OSConfig {
    private OSConfig() {}

    public static final boolean M3$PROVIDED = true;

    public static String HostName() {
        String h = OS.POSIX ? null : System.getenv("COMPUTERNAME");
        if (h == null) {
            try {
                h = java.net.InetAddress.getLocalHost().getHostName();
            } catch (java.net.UnknownHostException e) {
                h = "<unknown>";
            }
        }
        return h;
    }

    /** As uname's machine on POSIX ("x86_64", "aarch64", "arm64" on macOS). */
    public static String HostArchitecture() {
        String a = System.getProperty("os.arch", "");
        if (OS.POSIX) {
            return switch (a) {
                case "amd64", "x86_64" -> "x86_64";
                case "aarch64", "arm64" -> macOS() ? "arm64" : "aarch64";
                default -> a;
            };
        }
        return switch (a) {
            case "amd64", "x86_64" -> "AMD64";
            case "x86" -> "Intel x86";
            default -> "<unknown>";
        };
    }

    /** As uname's sysname on POSIX ("Linux", "Darwin"). */
    public static String OSName() {
        if (OS.POSIX) {
            return macOS() ? "Darwin" : System.getProperty("os.name", "");
        }
        return "Windows";
    }

    public static String OSVersion() {
        return System.getProperty("os.version", "");
    }

    public static String UserName() {
        if (OS.POSIX) {
            String u = System.getenv("USER");
            return u != null ? u : "<unknown user>";
        }
        return System.getProperty("user.name", "");
    }

    public static String UserHome() {
        String h = System.getenv("HOME");
        if (h == null && !OS.POSIX) {
            h = System.getenv("HOMESHARE");
            if (h == null) {
                h = System.getenv("USERPROFILE");
            }
            if (h == null) {
                String drive = System.getenv("HOMEDRIVE");
                String path = System.getenv("HOMEPATH");
                if (drive != null && path != null) {
                    h = drive + path;
                }
            }
        }
        if (h == null) {
            h = System.getProperty("user.dir");
        }
        if (h == null) {
            h = OS.POSIX ? "/tmp" : "C:\\";
        }
        return h;
    }

    private static boolean macOS() {
        return System.getProperty("os.name", "").startsWith("Mac");
    }
}
