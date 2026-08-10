package de.astranox.nixperms.distribution;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalMetadataTest {

    @Test
    void exposesEveryPlatformDescriptorAndOneSharedConfig() throws Exception {
        assertContains("plugin.yml", "de.astranox.nixperms.paper.NixPermsBukkit");
        assertContains("paper-plugin.yml", "de.astranox.nixperms.paper.NixPermsBukkit");
        assertContains("bungee.yml", "de.astranox.nixperms.bungeecord.NixPermsBungee");
        assertContains("velocity-plugin.json", "de.astranox.nixperms.velocity.NixPermsVelocity");
        assertContains("config.yml", "resolution-policy: PRIMARY_WINS");
    }

    private void assertContains(String resource, String expected) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, () -> resource + " is missing");
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains(expected), () -> resource + " does not contain " + expected);
        }
    }
}
