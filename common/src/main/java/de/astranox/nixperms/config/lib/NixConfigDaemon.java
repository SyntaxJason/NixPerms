package de.astranox.nixperms.config.lib;

import de.astranox.nixperms.file.*;
import de.astranox.nixperms.logger.Logger;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

public abstract class NixConfigDaemon implements Runnable, AutoCloseable {
    private static final long SELF_WRITE_SUPPRESS_NS = TimeUnit.MILLISECONDS.toNanos(500);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile NixObject root;
    private Path file;
    private long debounceMs;
    private WatchService watchService;
    private Thread loopThread;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingReload;
    private volatile long lastOwnWriteNanos = 0L;

    private static String mmInfo(String text) {
        return "» " + text + "";
    }

    private static String mmWarn(String text) {
        return "⚠ " + text + "";
    }

    private static String mmErr(String text) {
        return "✗ " + text + "";
    }

    private static String mmCode(String s) {
        return "" + s + "";
    }

    private static String mmPath(String p) {
        return "<#8AA2FF>" + p + "</#8AA2FF>";
    }

    private static String mmHover(String base, String hover) {
        return "<hover:show_text:'" + hover + "'>" + base + "</hover>";
    }

    private static void logInfo(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.info(message);
    }

    private static void logWarn(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.warn(message);
    }

    private static void logError(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.error(message);
    }

    private static void logOk(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.info("✓ " + message);
    }

    private static void logDebug(String message) {
        var logger = Logger.getLogger();
        if (logger != null) logger.debug(message);
    }

    public final synchronized void bind(Path dataDir, String relativeFile, long debounceMs) throws IOException {
        this.file = dataDir.resolve(relativeFile);
        this.debounceMs = debounceMs;
        Files.createDirectories(this.file.getParent());

        boolean created = Files.notExists(file);
        this.root = created ? new NixObject() : readOrEmpty(file);

        if (created) {
            logInfo(mmInfo("Erzeuge <#BDE0FE>neue</#BDE0FE> Datei " + mmPath(file.toString())));
        } else {
            long size = Files.size(file);
            logInfo(mmInfo("Lade " + mmHover(mmPath(file.toString()), size + " Bytes")));
        }

        int applied = applyAnnotatedDefaultsCount(this);
        if (created || applied > 0) {
            writeIfChanged(file, this.root, true);
            logOk("Defaults angewendet: " + applied + " → " + mmPath(file.getFileName().toString()));
            return;
        }

        logDebug("· Bind: keine neuen Defaults, kein Write");
    }

    public final void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            logWarn(mmWarn("Watcher bereits aktiv"));
            return;
        }

        Path directory = file.getParent();
        this.watchService = FileSystems.getDefault().newWatchService();
        directory.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cfg-daemon-" + file.getFileName() + "-debounce");
            t.setDaemon(true);
            return t;
        });

        this.loopThread = new Thread(() -> loop(directory, file.getFileName().toString()),
                "cfg-daemon-" + file.getFileName() + "-loop");
        this.loopThread.setDaemon(true);
        this.loopThread.start();

        logOk("Watcher aktiv für " + mmPath(directory.toString()) + " (" + mmCode(file.getFileName().toString()) + ")");
    }

    private void loop(Path directory, String fileName) {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logWarn(mmWarn("Watcher unterbrochen"));
                break;
            } catch (ClosedWatchServiceException exception) {
                logInfo(mmInfo("WatchService geschlossen"));
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW) {
                    logWarn(mmWarn("FS-Event OVERFLOW"));
                    continue;
                }

                Path context = (Path) event.context();
                if (context == null) {
                    continue;
                }

                logDebug("· FS-Event <#A6B1E1>" + event.kind().name() + "</#A6B1E1> → " + mmCode(String.valueOf(context)));

                if (!fileName.equals(context.getFileName().toString())) {
                    continue;
                }

                long sinceOwn = System.nanoTime() - lastOwnWriteNanos;
                if (sinceOwn >= 0 && sinceOwn < SELF_WRITE_SUPPRESS_NS) {
                    logDebug("· Ignoriere eigenes Write-Event (" + (sinceOwn / 1_000_000) + "ms)");
                    continue;
                }

                scheduleReload();
            }

            if (!key.reset()) {
                logWarn(mmWarn("WatchKey invalid"));
                break;
            }
        }
    }

    private void scheduleReload() {
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
        }

        pendingReload = scheduler.schedule(this::run, debounceMs, TimeUnit.MILLISECONDS);
        logDebug("· Reload geplant in " + debounceMs + "ms");
    }

    @Override
    public void run() {
        try {
            logInfo(mmInfo("Reload " + mmHover(mmPath(file.toString()), "Neu einlesen & Defaults anwenden")));
            NixObject active = readOrEmpty(file);
            this.root = active;

            int applied = applyAnnotatedDefaultsCount(this);
            boolean wrote = false;

            if (applied > 0) {
                if (writeIfChanged(file, this.root, false)) {
                    logOk("Reload abgeschlossen, Defaults " + applied + " gesetzt");
                    wrote = true;
                }
            }

            if (!wrote) {
                logDebug("· Reload: keine Änderungen zu persistieren");
            }

            if (applied == 0) {
                logDebug("· Reload: keine neuen Defaults, kein Write");
            }

            onReload(this.root);
        } catch (IOException exception) {
            logError(mmErr("Reload fehlgeschlagen: " + mmCode(exception.getMessage())));
        }
    }

    protected void onReload(NixObject updated) {
        // Override in subclasses
    }

    protected final synchronized int applyAnnotatedDefaultsCount(Object instance) {
        int count = 0;

        for (Field field : instance.getClass().getDeclaredFields()) {
            Value annotation = field.getAnnotation(Value.class);
            if (annotation == null) continue;
            count += ensureAnnotatedDefault(annotation.key(), annotation.def(), field.getType()) ? 1 : 0;
        }

        for (Method method : instance.getClass().getDeclaredMethods()) {
            Value annotation = method.getAnnotation(Value.class);
            if (annotation == null) continue;
            Class<?> returnType = method.getReturnType();
            if (returnType == Void.TYPE) continue;
            count += ensureAnnotatedDefault(annotation.key(), annotation.def(), returnType) ? 1 : 0;
        }

        return count;
    }

    private boolean ensureAnnotatedDefault(String dottedKey, String defaultString, Class<?> targetType) {
        if (hasPath(dottedKey)) return false;

        NixNode value = parseDefault(defaultString, targetType);
        putPath(root, dottedKey, value);
        logDebug("· Default " + mmCode(dottedKey) + " = " + mmCode(defaultString));
        return true;
    }

    private boolean hasPath(String dottedKey) {
        String[] pathParts = dottedKey.split("\\.");
        NixObject current = root;

        for (int i = 0; i < pathParts.length - 1; i++) {
            NixNode node = current.get(pathParts[i]);
            if (node == null || !node.isObject()) return false;
            current = node.asObject();
        }

        return current.has(pathParts[pathParts.length - 1]);
    }

    private void putPath(NixObject root, String dottedKey, NixNode value) {
        String[] pathParts = dottedKey.split("\\.");
        NixObject current = root;

        for (int i = 0; i < pathParts.length - 1; i++) {
            NixNode node = current.get(pathParts[i]);
            if (node == null || !node.isObject()) {
                NixObject next = new NixObject();
                current.put(pathParts[i], next);
                current = next;
            } else {
                current = node.asObject();
            }
        }

        current.put(pathParts[pathParts.length - 1], value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private NixNode parseDefault(String defaultString, Class<?> targetType) {
        if (targetType == String.class) return NixNode.of(defaultString);
        if (targetType == boolean.class || targetType == Boolean.class) return NixNode.of(Boolean.parseBoolean(defaultString));
        if (targetType == int.class || targetType == Integer.class) return NixNode.of(Integer.parseInt(defaultString));
        if (targetType == long.class || targetType == Long.class) return NixNode.of(Long.parseLong(defaultString));
        if (targetType == double.class || targetType == Double.class) return NixNode.of(Double.parseDouble(defaultString));
        if (Enum.class.isAssignableFrom(targetType)) return NixNode.of(Enum.valueOf((Class<? extends Enum>) targetType, defaultString).name());

        String trim = defaultString.trim();
        if (targetType == NixObject.class || trim.startsWith("{")) {
            try {
                return NixParser.parseUtf8File(java.nio.file.Paths.get(trim));
            } catch (Exception exception) {
                return new NixObject();
            }
        }

        if (targetType == NixArray.class || trim.startsWith("[")) {
            // Parse simple array format: [item1, item2, item3]
            NixArray array = new NixArray();
            if (trim.startsWith("[") && trim.endsWith("]")) {
                String content = trim.substring(1, trim.length() - 1).trim();
                if (!content.isEmpty()) {
                    String[] items = content.split(",");
                    for (String item : items) {
                        array.add(NixNode.of(item.trim()));
                    }
                }
            }
            return array;
        }

        return NixNode.of(defaultString);
    }

    public synchronized String getString(String path, String defaultValue) {
        ensure(path, NixNode.of(defaultValue));
        NixNode node = resolve(path).get(last(path));
        return node != null && node.isString() ? node.asString() : defaultValue;
    }

    public synchronized boolean getBoolean(String path, boolean defaultValue) {
        ensure(path, NixNode.of(defaultValue));
        NixNode node = resolve(path).get(last(path));
        return node != null && node.isBool() ? node.asBool() : defaultValue;
    }

    public synchronized int getInt(String path, int defaultValue) {
        ensure(path, NixNode.of(defaultValue));
        NixNode node = resolve(path).get(last(path));
        return node != null && node.isNumber() ? node.asInt() : defaultValue;
    }

    public synchronized long getLong(String path, long defaultValue) {
        ensure(path, NixNode.of(defaultValue));
        NixNode node = resolve(path).get(last(path));
        return node != null && node.isNumber() ? node.asLong() : defaultValue;
    }

    public synchronized double getDouble(String path, double defaultValue) {
        ensure(path, NixNode.of(defaultValue));
        NixNode node = resolve(path).get(last(path));
        return node != null && node.isNumber() ? node.asDouble() : defaultValue;
    }

    public synchronized NixObject getObject(String path, NixObject defaultValue) {
        ensure(path, defaultValue != null ? defaultValue : new NixObject());
        NixNode node = resolve(path).get(last(path));
        return node != null && node.isObject() ? node.asObject() : defaultValue;
    }

    public synchronized NixArray getArray(String path, NixArray defaultValue) {
        ensure(path, defaultValue != null ? defaultValue : new NixArray());
        NixNode node = resolve(path).get(last(path));
        return node != null && node.isArray() ? node.asArray() : defaultValue;
    }

    private void ensure(String dottedKey, NixNode defaultValue) {
        String[] pathParts = dottedKey.split("\\.");
        NixObject current = root != null ? root : new NixObject();
        if (root == null) root = current;

        for (int i = 0; i < pathParts.length - 1; i++) {
            NixNode node = current.get(pathParts[i]);
            if (node == null || !node.isObject()) {
                NixObject next = new NixObject();
                current.put(pathParts[i], next);
                current = next;
            } else {
                current = node.asObject();
            }
        }

        String leafKey = pathParts[pathParts.length - 1];
        if (current.has(leafKey)) return;

        current.put(leafKey, defaultValue);
        try {
            writeIfChanged(file, root, true);
            logDebug("· Persistiere fehlenden Key " + mmCode(dottedKey));
        } catch (IOException exception) {
            logError(mmErr("Persistenzfehler bei " + mmCode(dottedKey) + ": " + mmCode(exception.getMessage())));
        }
    }

    private NixObject resolve(String dottedKey) {
        String[] pathParts = dottedKey.split("\\.");
        NixObject current = root;

        for (int i = 0; i < pathParts.length - 1; i++) {
            NixNode node = current.get(pathParts[i]);
            if (node == null || !node.isObject()) return new NixObject();
            current = node.asObject();
        }

        return current;
    }

    private String last(String dottedKey) {
        String[] pathParts = dottedKey.split("\\.");
        return pathParts[pathParts.length - 1];
    }

    public synchronized void setString(String path, String value) {
        genericSet(path, NixNode.of(value));
    }

    public synchronized void setBoolean(String path, boolean value) {
        genericSet(path, NixNode.of(value));
    }

    public synchronized void setInt(String path, int value) {
        genericSet(path, NixNode.of(value));
    }

    public synchronized void setLong(String path, long value) {
        genericSet(path, NixNode.of(value));
    }

    public synchronized void setDouble(String path, double value) {
        genericSet(path, NixNode.of(value));
    }

    public synchronized void setObject(String path, NixObject object) {
        genericSet(path, object);
    }

    public synchronized void setArray(String path, NixArray array) {
        genericSet(path, array);
    }

    private void genericSet(String dottedKey, NixNode value) {
        if (root == null) root = new NixObject();

        String[] pathParts = dottedKey.split("\\.");
        NixObject current = root;

        for (int i = 0; i < pathParts.length - 1; i++) {
            NixNode node = current.get(pathParts[i]);
            if (node == null || !node.isObject()) {
                NixObject next = new NixObject();
                current.put(pathParts[i], next);
                current = next;
            } else {
                current = node.asObject();
            }
        }

        String leafKey = pathParts[pathParts.length - 1];
        NixNode oldValue = current.get(leafKey);

        if (nixEqual(oldValue, value)) {
            logDebug("· Set: unverändert " + mmCode(dottedKey));
            return;
        }

        current.put(leafKey, value);
        try {
            writeIfChanged(file, root, true);
            logOk("Setze " + mmCode(dottedKey) + " = " + mmCode(String.valueOf(value)));
        } catch (IOException exception) {
            logError(mmErr("Setzen fehlgeschlagen bei " + mmCode(dottedKey) + ": " + mmCode(exception.getMessage())));
        }
    }

    private NixObject readOrEmpty(Path filePath) {
        try {
            return NixParser.parseUtf8File(filePath);
        } catch (Exception exception) {
            return new NixObject();
        }
    }

    private void writePretty(Path filePath, NixObject object) throws IOException {
        NixPrinter.writeUtf8File(filePath, object);
    }

    private boolean writeIfChanged(Path filePath, NixObject newContent, boolean force) throws IOException {
        if (!force) {
            NixObject disk = readOrEmpty(filePath);
            if (nixEqual(disk, newContent)) return false;
        }

        lastOwnWriteNanos = System.nanoTime();
        writePretty(filePath, newContent);
        return true;
    }

    private boolean nixEqual(NixNode nodeA, NixNode nodeB) {
        if (nodeA == nodeB) return true;
        if (nodeA == null || nodeB == null) return false;

        if (nodeA.isObject() && nodeB.isObject()) {
            return nodeA.asObject().equals(nodeB.asObject());
        }

        if (nodeA.isArray() && nodeB.isArray()) {
            return nodeA.asArray().equals(nodeB.asArray());
        }

        if (nodeA.isString() && nodeB.isString()) {
            return nodeA.asString().equals(nodeB.asString());
        }

        if (nodeA.isBool() && nodeB.isBool()) {
            return nodeA.asBool() == nodeB.asBool();
        }

        if (nodeA.isNumber() && nodeB.isNumber()) {
            return nodeA.asBig().equals(nodeB.asBig());
        }

        return false;
    }

    protected final Path path() {
        return this.file;
    }

    protected final NixObject root() {
        return this.root;
    }

    @Override
    public void close() throws IOException {
        if (!running.compareAndSet(true, false)) {
            logWarn(mmWarn("Watcher bereits gestoppt"));
            return;
        }

        if (watchService != null) watchService.close();
        if (loopThread != null) loopThread.interrupt();
        if (scheduler != null) scheduler.shutdownNow();

        logOk("Watcher gestoppt");
    }
}
