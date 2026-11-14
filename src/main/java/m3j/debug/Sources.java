// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds the source files of units: the name of a file, as the class of a
 * unit gives it (its SourceFile, a path as the compiler saw it, or the
 * generic unit of an instance), is looked up below the roots, in their
 * order; the first file of that name wins.
 */
public final class Sources {
    private final List<Path> roots;
    private final Path cwd;
    private Map<String, Path> index;

    public Sources(List<Path> roots, Path cwd) {
        this.roots = List.copyOf(roots);
        this.cwd = cwd;
    }

    /** The last part of the path p, with / or \ as separators. */
    public static String baseName(String p) {
        int k = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return p.substring(k + 1);
    }

    /** The file of the source name, or null. */
    public synchronized Path find(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            try {
                Path p = cwd.resolve(name);
                if (Files.isRegularFile(p)) {
                    return p.toAbsolutePath().normalize();
                }
            } catch (RuntimeException e) {
                // not a path of this system
            }
        }
        if (index == null) {
            index = new HashMap<>();
            for (Path r : roots) {
                scan(r);
            }
        }
        return index.get(baseName(name));
    }

    private void scan(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> {
                String n = p.getFileName() == null ? "" : p.getFileName().toString();
                return n.endsWith(".m3") || n.endsWith(".i3") || n.endsWith(".mg") || n.endsWith(".ig");
            }).filter(p -> !hidden(root, p)).forEach(files::add);
        } catch (IOException | RuntimeException e) {
            // a part of the tree that cannot be read
        }
        files.sort(null);
        for (Path p : files) {
            index.putIfAbsent(p.getFileName().toString(), p.toAbsolutePath().normalize());
        }
    }

    /** Whether p is below a directory that holds no sources (.git, node_modules). */
    private static boolean hidden(Path root, Path p) {
        for (Path part : root.relativize(p)) {
            String n = part.toString();
            if (n.startsWith(".") || n.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }
}
