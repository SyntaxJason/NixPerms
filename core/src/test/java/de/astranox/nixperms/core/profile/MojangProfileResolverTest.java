package de.astranox.nixperms.core.profile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MojangProfileResolverTest {

    @Test
    void parsesCompactAndDashedProfileIds() {
        ResolvedProfile compact = MojangProfileResolver.parseProfile(
                "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}"
        );
        ResolvedProfile dashed = MojangProfileResolver.parseProfile(
                "{\"name\":\"Notch\",\"id\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\"}"
        );

        UUID expected = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertEquals(expected, compact.uniqueId());
        assertEquals("Notch", compact.name());
        assertEquals(expected, dashed.uniqueId());
    }

    @Test
    void rejectsIncompleteOrInvalidProfiles() {
        assertNull(MojangProfileResolver.parseProfile("{}"));
        assertNull(MojangProfileResolver.parseProfile("{\"id\":\"invalid\",\"name\":\"Notch\"}"));
        assertNull(MojangProfileResolver.parseProfile("{\"id\":\"069a79f444e94726a5befca90e38aaf5\"}"));
    }
}
