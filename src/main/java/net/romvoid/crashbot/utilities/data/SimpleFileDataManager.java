package net.romvoid.crashbot.utilities.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class SimpleFileDataManager implements DataManager<List<String>> {
    public static final Pattern NEWLINE_PATTERN = Pattern.compile("\\r\\n?|\\r?\\n");
    private static final Logger log = LoggerFactory.getLogger(SimpleFileDataManager.class);
    private final List<String> data = new ArrayList<>();
    private final Path path;

    public SimpleFileDataManager(String file) {
        this.path = Paths.get(file);

        if (!this.path.toFile().exists()) {
            log.info("Could not find config file at " + this.path.toFile().getAbsolutePath() + ", creating a new one...");
            try {
                if (this.path.toFile().createNewFile()) {
                    log.info("Generated new config file at " + this.path.toFile().getAbsolutePath() + ".");
                    FileIOUtil.write(this.path, "");
                    log.info("Please, fill the file with valid properties.");
                } else {
                    log.error("Could not create config file at {}", file);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        try {
            Collections.addAll(data, NEWLINE_PATTERN.split(FileIOUtil.read(this.path)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        data.removeIf (s -> s.startsWith("//"));
    }

    @Override
    public List<String> get() {
        return data;
    }

    @Override
    public void save() {
        try {
            FileIOUtil.write(path, String.join("\n", this.data));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
