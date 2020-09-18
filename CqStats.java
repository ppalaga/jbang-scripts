//usr/bin/env jbang "$0" "$@" ; exit $?
//
//DEPS org.eclipse.jgit:org.eclipse.jgit:5.9.0.202009080501-r

import static java.lang.System.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;

public class CqStats {
    static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault());

    public static void main(String... args) throws InvalidRemoteException, TransportException, GitAPIException {

        try {
            Path cloneDir = Paths.get("camel-quarkus");
            final Git git;
            if (!Files.exists(cloneDir)) {
                System.out.println("Cloning to " + cloneDir);
                git = Git.cloneRepository().setURI("https://github.com/apache/camel-quarkus.git")
                        .setDirectory(cloneDir.toFile()).call();
            } else {
                System.out.println("Repo cloned already " + cloneDir);
                git = Git.open(cloneDir.toFile());
                git.fetch().setRemote("origin").call();
                git.checkout().setName("master").call();
                git.reset().setMode(ResetType.HARD).setRef("origin/master").call();
            }

            Map<String, AtomicInteger> jvmExtensionsByMonths = new TreeMap<>();
            Map<String, AtomicInteger> nativeExtensionsByMonths = new TreeMap<>();
            final Path jvmPath = cloneDir.resolve("extensions-jvm");
            final Path nativePath = cloneDir.resolve("extensions");
            final Path corePath = cloneDir.resolve("extensions-core");

            for (RevCommit revCommit : git.log().call()) {
                long ts = revCommit.getAuthorIdent().getWhen().getTime();
                final String key = FORMAT.format(Instant.ofEpochMilli(ts));
                final int jvmCnt = countExtensions(key, jvmExtensionsByMonths, jvmPath);
                final int nativeCnt = countExtensions(key, nativeExtensionsByMonths, nativePath, corePath);
                System.out.println(revCommit.getId().getName() + " " + nativeCnt + "/" + jvmCnt + " "
                        + revCommit.getShortMessage());
                git.reset().setMode(ResetType.HARD).setRef(revCommit.getId().getName()).call();
            }

            System.out.println();
            System.out.println();
            System.out.println("Month\tNative\tJVM");
            for (Entry<String, AtomicInteger> en : nativeExtensionsByMonths.entrySet()) {
                System.out.println(en.getKey()+"\t"+ en.getValue().get() +"\t"+ jvmExtensionsByMonths.get(en.getKey()).get());
            }
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    static int countExtensions(String key, Map<String, AtomicInteger> extensionsByMonths, Path... extensionsDirs) throws IOException {
        int newCnt = 0;
        for (Path extensionsDir : extensionsDirs) {
            if (Files.isDirectory(extensionsDir)) {
                try (Stream<Path> extensions = Files.list(extensionsDir)) {
                    newCnt += (int) extensions.filter(p -> Files.isDirectory(p.resolve("runtime"))).count();
                }
            }
        }
        final AtomicInteger cnt = extensionsByMonths.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (cnt.get() < newCnt) {
            cnt.set(newCnt);
        }
        return newCnt;
    }
}
