package test.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FileUtils {
    public static void main(String[] args) throws IOException {
        Predicate<Path> isXml = path -> hasExtension(path, "xml");
        Predicate<Path> isNotExcluded = path -> !"versioncheck.xml".equals(path.getFileName().toString())
                && !"iTrac_params.xml".equals(path.getFileName().toString());
        final Predicate<Path> filter = isXml.and(isNotExcluded);
    }

    public static void printNames(String path) throws IOException {
        Stream<Path> stream = Files.walk(Paths.get(path));
        stream.forEach(p -> System.out.println(p.getFileName()));
        stream.close();
    }

    public static void mergeFiles(String path, Predicate<Path> filter) throws IOException {
        File container = new File("container.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(container.toPath())) {
            Files.walkFileTree(Paths.get(path), new FileVisitor<Path>() {
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) {
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                    if (filter.test(path)) {
                        writer.write(path.getFileName() + "\n\r");
                        try (BufferedReader reader = Files.newBufferedReader(path)) {
                            while (reader.ready()) {
                                writer.write(reader.readLine() + "\n");
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFileFailed(Path path, IOException e) {
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory(Path path, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static void lookForOccurrence(String f, Predicate<Path> filter, String object, boolean reportProblems) throws IOException {
        final int[] stats = {0, 0};
        Files.walkFileTree(new File(f).toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                if (filter.test(path)) {
                    try (Stream<String> lines = Files.lines(path)) {
                        if (lines.anyMatch(l -> l.contains(object))) {
                            System.out.println("Found occurrence in " + path.toAbsolutePath());
                            stats[0]++;
                        }
                    } catch (Throwable e) {
                        stats[1]++;
                        if (reportProblems) {
                            System.out.println("Something went wrong");
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException e) {
                if (reportProblems) {
                    System.out.println("Failed visit " + path.toAbsolutePath());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("Found " + stats[0] + " files with occurrences");
        System.out.println("Got in trouble with " + stats[1] + " files");
    }

    public static void sortFilesByExtension(File f) throws IOException {
        Objects.requireNonNull(f);
        if (!f.isDirectory()) {
            throw new IllegalArgumentException("parameter should be a directory");
        }

        final Map<String, Set<Path>> extPath = new HashMap<>();

        Files.walkFileTree(f.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                final String extension = getExtension(path);
                final Set<Path> paths = extPath.getOrDefault(extension, new HashSet<>());
                paths.add(path);
                extPath.put(extension, paths);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException e) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });

        extPath.forEach((ext, pathSet) -> {
            final Path targetDir = Paths.get(f + "/" + ext + "/");
            throttleException(() -> Files.createDirectory(targetDir));
            pathSet.forEach(p -> throttleException(() -> {
                Path targetFile = Paths.get(targetDir.toString() + "/" + p.getFileName());
                try {
                    Files.copy(p, targetFile);
                } catch (FileAlreadyExistsException e) {
                    final String s = targetFile.toString();
                    final String newFileName = s.substring(0, s.lastIndexOf(".")) + UUID.randomUUID() + getExtension(Paths.get(s));
                    Files.copy(p, Paths.get(newFileName));
                }
                return null;
            }));
        });
    }

    private static void throttleException(Callable<?> c) {
        try {
            c.call();
        } catch (Throwable ignore) {
            System.out.println(ignore);
        }
    }

    public static boolean hasExtension(File f, String extension) {
        Objects.requireNonNull(f);
        Objects.requireNonNull(extension);
        return f.getName().endsWith(extension);
    }

    public static boolean hasExtension(Path p, String extension) {
        return hasExtension(p.toFile(), extension);
    }

    public static boolean hasExtension(String s, String extension) {
        return hasExtension(new File(s), extension);
    }

    public static String getExtension(Path p) {
        final String fileName = p.getFileName().toString();
        return fileName.substring(fileName.lastIndexOf("."));
    }


}
