package pro.javacard.sdk;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ExportFileHelper {

    public enum ExportFileVersion {
        V21, V23
    }

    private ExportFileHelper() {
    }

    public static Optional<ExportFileVersion> getVersion(Path path) throws IOException {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(path))) {
            int magic = dis.readInt();

            byte minor = dis.readByte();
            byte major = dis.readByte();

            if (magic != 0x00FACADE)
                return Optional.empty();
            if (major != 2)
                throw new IOException("Invalid major version: " + major);
            if (minor == 1)
                return Optional.of(ExportFileVersion.V21);
            if (minor == 3)
                return Optional.of(ExportFileVersion.V23);
            throw new IOException("Invalid minor version: " + minor);
        }
    }
}
