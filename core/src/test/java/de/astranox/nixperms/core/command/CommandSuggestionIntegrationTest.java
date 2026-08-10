package de.astranox.nixperms.core.command;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.command.NixCommandSender;
import de.astranox.nixperms.api.group.IGroupManager;
import de.astranox.nixperms.api.platform.Platform;
import de.astranox.nixperms.api.user.IUserManager;
import de.astranox.nixperms.core.command.subcommand.UserSubcommand;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSuggestionIntegrationTest {

    @Test
    void includesTheSenderAndNavigablePermissionRoots() {
        AnnotationCommandProcessor processor = new AnnotationCommandProcessor(api());
        processor.register(new UserSubcommand());
        NixCommandSender sender = new Sender();

        assertTrue(processor.suggest(
                sender, new String[]{"user", "addperm", ""}
        ).contains("SyntaxJason"));

        assertTrue(processor.suggest(
                sender, new String[]{"user", "addperm", "SyntaxJason", ""}
        ).contains("nixperms."));
    }

    private INixPermsAPI api() {
        IUserManager users = proxy(IUserManager.class, "loaded", List.of());
        IGroupManager groups = proxy(IGroupManager.class, "loaded", List.of());
        return (INixPermsAPI) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{INixPermsAPI.class},
                (instance, method, arguments) -> switch (method.getName()) {
                    case "users" -> users;
                    case "groups" -> groups;
                    case "version" -> "test";
                    case "serverId" -> "test";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, String methodName, Object value) {
        return (T) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{type},
                (instance, method, arguments) -> method.getName().equals(methodName)
                        ? value
                        : defaultValue(method.getReturnType())
        );
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class Sender implements NixCommandSender {
        @Override public String name() { return "SyntaxJason"; }
        @Override public UUID uniqueId() { return new UUID(0L, 1L); }
        @Override public boolean isPlayer() { return true; }
        @Override public boolean hasPermission(String node) { return true; }
        @Override public String locale() { return "en_us"; }
        @Override public Platform platform() { return Platform.BUKKIT; }
        @Override public void sendComponent(Component component) { }
    }
}
