package net.ripe.rpki.monitor.expiration.fetchers;

import net.ripe.rpki.commons.rsync.Rsync;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Component("RsyncFetcher")
public class RsyncFetcher implements RepoFetcher {

    private final String rsyncUrl;

    @Autowired
    public RsyncFetcher(@Value("${rsync.url}") final String rsyncUrl) {
        this.rsyncUrl = rsyncUrl;
    }


    public Map<String, byte[]> fetchObjects() throws FetcherException {


        try {
            final String tempDirectory = Files.createTempDirectory("rsync-monitor").toString();
            final Rsync rsyncTa = new Rsync(rsyncUrl + "/ta", tempDirectory + "/ta");
            rsyncTa.addOptions("-a");
            rsyncTa.execute();

            final Rsync rsyncRepo = new Rsync(rsyncUrl + "/repository", tempDirectory + "/repository");
            rsyncRepo.addOptions("-a");
            rsyncRepo.execute();

            final Map<String, byte[]> objects = new HashMap<>();
            Files.walk(Paths.get(tempDirectory))
                    .filter(Files::isRegularFile)
                    .forEach(f -> {
                        final String objectUri = f.toString().replace(tempDirectory, rsyncUrl);
                        try {
                            objects.put(objectUri, Files.readAllBytes(f));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

            Files.walk(Paths.get(tempDirectory))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

            return objects;
        } catch (IOException | RuntimeException e) {
            throw new FetcherException(e);
        }
    }
}
