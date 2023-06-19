package de.blazemcworld.jsscripts;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.file.spi.FileSystemProviders;
import org.graalvm.polyglot.io.FileSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ScriptFS implements FileSystem {

    private static final HashMap<Path, String> paths = new HashMap<>();

    @Override
    public Path parsePath(URI uri) {
        return Path.of(uri);
    }

    @Override
    public Path parsePath(String path) {
        Path p = Path.of(path);
        paths.put(p, path);
        return p;
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {

    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        FileSystemProviders.getFileSystemProvider(dir).createDirectory(dir, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        FileSystemProviders.getFileSystemProvider(path).delete(path);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        String actual = paths.get(path);

        if (actual.startsWith("jvm/")) {
            return new SeekableInMemoryByteChannel("""
                    export default Java.type("%s");
                    """.formatted(actual.substring(4).replaceAll("\\/", ".")).getBytes(StandardCharsets.UTF_8));
        }
        if (actual.startsWith("local/") || actual.startsWith("jspm/")) {
            if (actual.startsWith("local")) {
                actual = actual.replaceFirst("local", "scripts");
            }
            path = ScriptManager.modDir.resolve(actual);
            ScriptManager.loadedScripts.add(path.toAbsolutePath().toString());
        }

        return FileSystemProviders.getFileSystemProvider(path).newByteChannel(path, options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return FileSystemProviders.getFileSystemProvider(dir).newDirectoryStream(dir, filter);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return path.toAbsolutePath();
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        return path;
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return FileSystemProviders.getFileSystemProvider(path).readAttributes(path, attributes, options);
    }
}
