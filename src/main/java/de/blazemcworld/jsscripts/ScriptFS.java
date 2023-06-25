package de.blazemcworld.jsscripts;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.io.file.spi.FileSystemProviders;
import org.graalvm.polyglot.io.FileSystem;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ScriptFS implements FileSystem {

    @Override
    public Path parsePath(URI uri) {
        return Path.of(uri);
    }

    @Override
    public Path parsePath(String path) {
        return Path.of(path);
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
        String relative = JsScripts.MC.runDirectory.toPath().toAbsolutePath().toAbsolutePath().relativize(path.toAbsolutePath()).toString();
        if (relative.startsWith("$")) {
            return new SeekableInMemoryByteChannel("""
                    export default Java.type("%s");
                    """.formatted(relative.substring(1).replaceAll(Pattern.quote(File.separator), ".")).getBytes(StandardCharsets.UTF_8));
        }
        if (relative.startsWith("#")) {
            if (!Files.exists(ScriptManager.modDir.resolve("scripts").resolve(relative.substring(1)))) {
                JsScripts.displayChat(Text.literal("Script '" + relative.substring(1) + "' does not exist. Click to attempt to download from jspm.").formatted(Formatting.RED)
                        .styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/jsscripts download " + relative.substring(1)))));
            }
            return new SeekableInMemoryByteChannel("""
                    export * from "%s";
                    """.formatted("JsScripts/scripts/" + relative.substring(1) + "/index.js").getBytes(StandardCharsets.UTF_8));
        }
        if (!path.toAbsolutePath().toString().endsWith(".js")) {
            path = Path.of(path.toAbsolutePath() + ".js");
        }

        ScriptManager.loadedScripts.add(path.toAbsolutePath().toString());
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
