import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.internal.resource.filesystem.FileSystemResource;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;

/** Offline inventory using the project's own Flyway checksum implementation. No DB access. */
class MigrationManifest {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length == 0 ? "src/main/resources/db/migration" : args[0]).toAbsolutePath();
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> p.getFileName().toString().matches("V[0-9]+__.*\\.sql"))
                .sorted(Comparator.comparingInt(MigrationManifest::version)).toList();
        }
        System.out.println("version,script,description,flyway_checksum,sha256");
        for (Path file : files) {
            String name = file.getFileName().toString();
            var resource = new FileSystemResource(new Location("filesystem:" + directory),
                file.toString(), StandardCharsets.UTF_8, false);
            int checksum = ChecksumCalculator.calculate(resource);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            String description = name.substring(name.indexOf("__") + 2, name.length() - 4).replace('_', ' ');
            System.out.printf("%d,%s,%s,%d,%s%n", version(file), name, description, checksum, sha);
        }
    }
    private static int version(Path path) {
        String name = path.getFileName().toString();
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }
}
