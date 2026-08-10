package de.astranox.nixperms.distribution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalJarIT {

    private static final List<String> REQUIRED_ENTRIES = List.of(
            "plugin.yml",
            "paper-plugin.yml",
            "bungee.yml",
            "velocity-plugin.json",
            "config.yml",
            "de/astranox/nixperms/paper/NixPermsBukkit.class",
            "de/astranox/nixperms/bungeecord/NixPermsBungee.class",
            "de/astranox/nixperms/velocity/NixPermsVelocity.class",
            "de/astranox/nixperms/libs/hikari/HikariDataSource.class",
            "de/astranox/nixperms/libs/snakeyaml/Yaml.class"
    );

    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "org/bukkit/",
            "com/velocitypowered/",
            "net/md_5/",
            "com/zaxxer/hikari/",
            "org/slf4j/"
    );

    @Test
    void shadedJarIsAPlatformIsolatedUniversalPlugin() throws Exception {
        try (ZipFile jar = new ZipFile(artifact().toFile())) {
            for (String entry : REQUIRED_ENTRIES) {
                assertNotNull(jar.getEntry(entry), () -> entry + " is missing");
            }
            for (String prefix : FORBIDDEN_PREFIXES) {
                assertFalse(
                        jar.stream().map(ZipEntry::getName).anyMatch(name ->
                                name.startsWith(prefix) || name.contains("/" + prefix)
                        ),
                        () -> "Provided platform/library classes were bundled under " + prefix
                );
            }

            assertDrivers(jar);
            assertManifest(jar);
        }
    }

    @Test
    void startsRelocatedCoreWithSqliteInIsolation(@TempDir Path dataDirectory) throws Exception {
        URL slf4jApi = Class.forName("org.slf4j.Logger")
                .getProtectionDomain().getCodeSource().getLocation();
        URL[] classPath = {artifact().toUri().toURL(), slf4jApi};

        try (URLClassLoader isolated = new URLClassLoader(
                classPath, ClassLoader.getPlatformClassLoader()
        )) {
            Class<?> settingsLoader = isolated.loadClass(
                    "de.astranox.nixperms.platform.common.PlatformSettingsLoader"
            );
            Supplier<InputStream> config = () -> new ByteArrayInputStream("""
                    server-id: isolated-test
                    database:
                      type: sqlite
                      sqlite-file: smoke.db
                    network:
                      enabled: false
                    """.getBytes(StandardCharsets.UTF_8));
            Object settings = settingsLoader
                    .getMethod("load", Path.class, Supplier.class)
                    .invoke(null, dataDirectory, config);

            Class<?> coreType = isolated.loadClass(
                    "de.astranox.nixperms.core.NixPermsCore"
            );
            CompletableFuture<?> startup = (CompletableFuture<?>) coreType
                    .getMethod("create", settings.getClass())
                    .invoke(null, settings);
            Object core = startup.get(10, TimeUnit.SECONDS);
            try {
                assertEquals(
                        System.getProperty("nixperms.version"),
                        coreType.getMethod("version").invoke(core)
                );
            } finally {
                coreType.getMethod("close").invoke(core);
            }
        }

        assertTrue(Files.isRegularFile(dataDirectory.resolve("smoke.db")));
    }

    private void assertDrivers(ZipFile jar) throws IOException {
        String drivers = text(jar, "META-INF/services/java.sql.Driver");
        assertTrue(drivers.contains("org.mariadb.jdbc.Driver"));
        assertTrue(drivers.contains("com.mysql.cj.jdbc.Driver"));
        assertTrue(drivers.contains("org.postgresql.Driver"));
        assertTrue(drivers.contains("org.sqlite.JDBC"));
    }

    private void assertManifest(ZipFile jar) throws IOException {
        try (InputStream input = input(jar, "META-INF/MANIFEST.MF")) {
            Attributes attributes = new Manifest(input).getMainAttributes();
            assertEquals("NixPerms", attributes.getValue("Implementation-Title"));
            assertEquals(
                    System.getProperty("nixperms.version"),
                    attributes.getValue("Implementation-Version")
            );
            assertEquals("true", attributes.getValue("Multi-Release"));
        }
    }

    private String text(ZipFile jar, String entry) throws IOException {
        try (InputStream input = input(jar, entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream input(ZipFile jar, String entry) throws IOException {
        ZipEntry found = jar.getEntry(entry);
        assertNotNull(found, () -> entry + " is missing");
        return jar.getInputStream(found);
    }

    private Path artifact() {
        String value = System.getProperty("nixperms.artifact");
        assertNotNull(value, "The Maven artifact path was not provided");
        return Path.of(value);
    }
}
