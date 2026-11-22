package eapdf;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Resource allowances and private disk scratch for a single validation worker. */
final class ValidationRuntime {
    private static final String CONTROLLER = "eapdf.validator.controller";
    private static final String JOB_PREFIX = "eapdf-validator-job-";

    static String controllerArgument() throws IOException {
        ProcessHandle process = ProcessHandle.current();
        return "-D" + CONTROLLER + "=" + process.pid() + "@" + start(process);
    }

    private static Instant start(ProcessHandle process) throws IOException {
        return process.info().startInstant().orElseThrow(() -> new IOException("Cannot establish validator process ownership"));
    }

    static void watchController() throws IOException {
        String identity = System.getProperty(CONTROLLER);
        if (identity == null) return;
        ProcessHandle parent = ProcessHandle.current().parent().orElseThrow(() -> new IOException("Validation controller exited"));
        if (!identity.equals(parent.pid() + "@" + start(parent))) throw new IOException("Validation controller exited");
        Thread watcher = new Thread(() -> {
            parent.onExit().join();
            Runtime.getRuntime().halt(2);
        }, "validation-controller-watchdog");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** Stable locks also serialize overlapping main, summary and progress path sets. */
    static final class ReportSet implements AutoCloseable {
        private static final String LOCK_DIRECTORY = ".eapdf-validator-locks";
        private static final java.util.concurrent.locks.ReentrantLock IN_PROCESS = new java.util.concurrent.locks.ReentrantLock();
        final Path report;
        private final Path locks;
        private final List<FileChannel> channels = new java.util.ArrayList<>();
        private final List<java.nio.channels.FileLock> held = new java.util.ArrayList<>();
        private final Map<String, Revision> mirrored = new LinkedHashMap<>();
        private List<Path> protectedInputs = List.of();
        void protectInputs(List<Path> inputs) { protectedInputs = List.copyOf(inputs); }
        private static final List<String> SUFFIXES = List.of(".progress.json", ".summary.json", "");
        private record Revision(Object key, java.nio.file.attribute.FileTime modified, long size) {}

        ReportSet(Path requested, long timeLimit) throws IOException, InterruptedException {
            Path absolute = requested.toAbsolutePath();
            Path parent = absolute.getParent().toRealPath();
            report = parent.resolve(absolute.getFileName());
            for (Path part : report) if (LOCK_DIRECTORY.equalsIgnoreCase(part.toString()))
                throw new IOException("The report path uses the reserved validator lock directory.");
            locks = parent.resolve(LOCK_DIRECTORY);
            if (Files.getFileStore(parent).supportsFileAttributeView("posix")) {
                Files.createDirectories(locks, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
            } else Files.createDirectories(locks);
            if (!Files.isDirectory(locks, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid validator report lock directory.");
            var names = new TreeSet<String>();
            for (String suffix : SUFFIXES) names.add(lockName(report, suffix));
            long started = System.nanoTime();
            if (!IN_PROCESS.tryLock(timeLimit, TimeUnit.NANOSECONDS)) throw new IOException("Another validation owns the report destination.");
            try {
                for (String name : names) {
                    FileChannel channel = FileChannel.open(locks.resolve(name + ".lock"), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    channels.add(channel);
                    while (true) {
                        if (Thread.interrupted()) throw new InterruptedException("Validation cancelled while waiting for its report.");
                        try {
                            var lock = channel.tryLock();
                            if (lock != null) { held.add(lock); break; }
                        }
                        catch (OverlappingFileLockException busy) { /* Another run in this JVM owns the report. */ }
                        if (System.nanoTime() - started >= timeLimit) throw new IOException("Another validation owns the report destination.");
                        TimeUnit.MILLISECONDS.sleep(100);
                    }
                    Files.deleteIfExists(locks.resolve(name + ".pending"));
                }
            } catch (IOException | InterruptedException | RuntimeException error) {
                try { close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
        }

        private static String lockName(Path base, String suffix) {
            String name = java.text.Normalizer.normalize((base.getFileName() + suffix).toLowerCase(Locale.ROOT),
                java.text.Normalizer.Form.NFC);
            return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
        }

        void mirror(Path workerReport, boolean complete) throws IOException {
            if (!complete) mirrorFile(workerReport, ".progress.json", false);
            else for (String suffix : SUFFIXES) mirrorFile(workerReport, suffix, true);
        }

        private void mirrorFile(Path workerReport, String suffix, boolean complete) throws IOException {
            Path source = workerReport.resolveSibling(workerReport.getFileName() + suffix);
            BasicFileAttributes attributes;
            try { attributes = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException missing) { return; }
            if (!attributes.isRegularFile() || attributes.size() > workFileLimit())
                throw new IOException("Validation report exceeds its file allowance.");
            var revision = new Revision(attributes.fileKey(), attributes.lastModifiedTime(), attributes.size());
            if (!complete && revision.equals(mirrored.get(suffix))) return;
            if (!complete && finishedProgress(source)) return;
            Path pending = locks.resolve(lockName(report, suffix) + ".pending");
            try {
                Files.copy(source, pending);
                if (Files.size(pending) > workFileLimit()) throw new IOException("Validation report exceeds its file allowance.");
                // The worker can replace the source between the initial check and the copy.
                if (!complete && finishedProgress(pending)) return;
                Path output = report.resolveSibling(report.getFileName() + suffix);
                ValidationOutputs.protect(output, protectedInputs);
                if (!suffix.isEmpty()) {
                    try (var reader = Files.newBufferedReader(pending)) {
                        var sidecar = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        sidecar.addProperty("report", report.toString());
                        Files.writeString(pending, JavaValidation.JSON.toJson(sidecar));
                    }
                }
                ValidationSession.replace(pending, output);
                mirrored.put(suffix, revision);
            } finally { Files.deleteIfExists(pending); }
        }

        private static boolean finishedProgress(Path snapshot) throws IOException {
            try (var input = Files.newBufferedReader(snapshot)) {
                var progress = com.google.gson.JsonParser.parseReader(input).getAsJsonObject();
                return "Finished".equals(JavaValidation.text(progress, "stage"));
            }
        }

        @Override public void close() throws IOException {
            IOException failure = null;
            try {
                for (var channel : channels.reversed()) {
                    try { channel.close(); }
                    catch (IOException error) {
                        if (failure == null) failure = error;
                        else failure.addSuppressed(error);
                    }
                }
            } finally { held.clear(); IN_PROCESS.unlock(); }
            if (failure != null) throw failure;
        }
    }

    static long setting(String name, long fallback) {
        String value = System.getProperty(name, System.getenv(name));
        return value == null || value.isBlank() ? fallback : Math.max(1, Long.parseLong(value));
    }
    // veraPDF can retain a scratch stream per decoded PDF stream. A small
    // fixed descriptor cap rejects otherwise valid multi-page mail archives.
    static long openFileLimit() {
        long inherited = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.UnixOperatingSystemMXBean os
            ? os.getMaxFileDescriptorCount() : 16384;
        long requested = setting("VALIDATOR_MAX_OPEN_FILES", Long.MAX_VALUE);
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/limits"))) if (line.startsWith("Max open files")) {
                String[] columns = line.substring("Max open files".length()).strip().split("\\s+");
                if (columns.length > 1 && !columns[1].equals("unlimited")) requested = Math.min(requested, Long.parseLong(columns[1]));
                break;
            }
        } catch (IOException | NumberFormatException ignored) { }
        return requested == Long.MAX_VALUE ? inherited : requested;
    }
    private static final class DiskBudget {
        static final long FILE = availableDiskShare();
    }
    private static long availableDiskShare() {
        try {
            Path root = Path.of(System.getProperty("java.io.tmpdir"));
            String type = Files.getFileStore(root).type().toLowerCase(java.util.Locale.ROOT);
            if (type.equals("tmpfs") || type.equals("ramfs")) {
                Path project = Path.of(System.getProperty("user.dir"));
                String projectType = Files.getFileStore(project).type().toLowerCase(java.util.Locale.ROOT);
                if (!projectType.equals("tmpfs") && !projectType.equals("ramfs")) root = project;
            }
            // Reserve room for other work and simultaneous validation jobs.
            return Math.max(1, Files.getFileStore(root).getUsableSpace() / 8);
        } catch (IOException error) { throw new IllegalStateException("Cannot determine available validation storage", error); }
    }
    static long workFileLimit() {
        return setting("VALIDATOR_MAX_WORK_FILE_BYTES", DiskBudget.FILE);
    }
    static long scratchLimit() {
        return setting("VALIDATOR_MAX_SCRATCH_BYTES", DiskBudget.FILE * 2);
    }
    static long addressLimit(long heapMiB) {
        // RLIMIT_AS counts mapped files even when their pages are not resident.
        // Leave address space for all admitted scratch as well as heap/native work.
        return Math.addExact(Math.multiplyExact(heapMiB + 4096, 1024L * 1024), scratchLimit());
    }
    static java.util.List<String> boundedCommand(java.util.List<String> command, long heapMiB) throws IOException {
        if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("linux")) return command;
        Path limiter = Path.of("/usr/bin/prlimit");
        if (!Files.isExecutable(limiter)) throw new IOException("Linux validation requires /usr/bin/prlimit (util-linux)");
        var bounded = new java.util.ArrayList<String>();
        bounded.add(limiter.toString()); bounded.add("--as=" + addressLimit(heapMiB));
        bounded.add("--fsize=" + workFileLimit());
        bounded.add("--nofile=" + openFileLimit()); bounded.add("--core=0");
        bounded.add(command.getFirst());
        if (Path.of(command.getFirst()).getFileName().toString().equals("java")) {
            // Freeze the controller's disk share for the worker and its children.
            bounded.add("-DVALIDATOR_MAX_WORK_FILE_BYTES=" + workFileLimit());
            bounded.add("-DVALIDATOR_MAX_SCRATCH_BYTES=" + scratchLimit());
        }
        bounded.addAll(command.subList(1, command.size())); return bounded;
    }
    static boolean scratchExceeded(Path directory, Path report) throws IOException {
        long maximum = scratchLimit();
        long[] totals = {0};
        boolean[] exceeded = {false};
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path path, java.nio.file.attribute.BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && (attributes.size() > maximum - totals[0])) {
                    exceeded[0] = true; return FileVisitResult.TERMINATE;
                }
                if (attributes.isRegularFile()) totals[0] += attributes.size();
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException error) throws IOException {
                if (error instanceof NoSuchFileException) return FileVisitResult.CONTINUE;
                throw error;
            }
            @Override public FileVisitResult postVisitDirectory(Path path, IOException error) throws IOException {
                if (error != null && !(error instanceof NoSuchFileException)) throw error;
                return FileVisitResult.CONTINUE;
            }
        });
        if (exceeded[0]) return true;
        try { return Files.isRegularFile(report) && Files.size(report) > workFileLimit(); }
        catch (NoSuchFileException ignored) { return false; }
    }
    static long workerHeapMiB() {
        long available = -1;
        try {
            for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) if (line.startsWith("MemAvailable:")) {
                available = Long.parseLong(line.split("\\s+")[1]) * 1024; break;
            }
        } catch (Exception ignored) { }
        if (available < 0 && ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os)
            available = os.getFreeMemorySize();
        for (String[] pair : new String[][]{{"/sys/fs/cgroup/memory.max", "/sys/fs/cgroup/memory.current"},
                {"/sys/fs/cgroup/memory/memory.limit_in_bytes", "/sys/fs/cgroup/memory/memory.usage_in_bytes"}}) try {
            String limit = Files.readString(Path.of(pair[0])).strip();
            if (!"max".equals(limit)) {
                long remaining = Math.max(0, Long.parseLong(limit) - Long.parseLong(Files.readString(Path.of(pair[1])).strip()));
                available = available < 0 ? remaining : Math.min(available, remaining);
            }
        } catch (Exception ignored) { }
        if (available < 0) return 512;
        long mib = (available / 3 * 2) / (1024 * 1024);
        if (mib < 256) throw new IllegalStateException("Not enough available memory to start validation.");
        return mib;
    }
    static long timeoutSeconds(long bytes) {
        String configured = System.getenv("VALIDATOR_JOB_TIMEOUT");
        if (configured != null && !configured.isBlank()) return Math.max(1, Long.parseLong(configured));
        return Math.min(86400, Math.max(600, 600 + bytes / (128 * 1024)));
    }
    private static Path temporaryRoot() throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"));
        String type = Files.getFileStore(root).type().toLowerCase(java.util.Locale.ROOT);
        if (type.equals("tmpfs") || type.equals("ramfs")) {
            Path disk = Path.of(System.getProperty("user.dir"), ".generator_web_tmp");
            String diskType = Files.getFileStore(disk.toAbsolutePath().getParent()).type().toLowerCase(java.util.Locale.ROOT);
            if (!diskType.equals("tmpfs") && !diskType.equals("ramfs")) { Files.createDirectories(disk); root = disk; }
        }
        return root;
    }

    static Path temporaryDirectory() throws IOException {
        return privateDirectory(temporaryRoot(), "eapdf-validator-");
    }

    static Path controllerDirectory() throws IOException {
        Path root = temporaryRoot();
        ProcessHandle process = ProcessHandle.current();
        Path directory = privateDirectory(root, JOB_PREFIX + process.pid() + "-" + start(process).toEpochMilli() + "-");
        try (var candidates = Files.newDirectoryStream(root, JOB_PREFIX + "*")) {
            var owner = Files.getOwner(directory);
            for (Path candidate : candidates) {
                try {
                    if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS) || !Files.getOwner(candidate).equals(owner)) continue;
                    String[] identity = candidate.getFileName().toString().substring(JOB_PREFIX.length()).split("-", 3);
                    if (identity.length != 3 || !identity[0].matches("[0-9]+") || !identity[1].matches("[0-9]+")) continue;
                    long pid = Long.parseLong(identity[0]), started = Long.parseLong(identity[1]);
                    boolean active = ProcessHandle.of(pid).filter(ProcessHandle::isAlive)
                            .map(p -> p.info().startInstant().map(instant -> instant.toEpochMilli() == started).orElse(true)).orElse(false);
                    if (!active) removeDirectory(candidate);
                } catch (IOException | NumberFormatException error) {
                    System.err.println("Unable to reclaim abandoned validation scratch: " + error.getMessage());
                }
            }
        } catch (IOException error) {
            System.err.println("Unable to scan validation scratch: " + error.getMessage());
        }
        return directory;
    }

    private static Path privateDirectory(Path root, String prefix) throws IOException {
        return Files.getFileStore(root).supportsFileAttributeView("posix")
            ? Files.createTempDirectory(root, prefix, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")))
            : Files.createTempDirectory(root, prefix);
    }
    static void removeDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
