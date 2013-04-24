package com.googlecode.lazyrecords.lucene;

import com.googlecode.totallylazy.Callable1;
import com.googlecode.totallylazy.Closeables;
import com.googlecode.totallylazy.Files;
import com.googlecode.totallylazy.Function2;
import com.googlecode.totallylazy.Sequence;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.googlecode.totallylazy.Closeables.using;
import static com.googlecode.totallylazy.Lists.list;
import static com.googlecode.totallylazy.Runnables.VOID;

public class OptimisedStorage implements LuceneStorage {
    public static final IOContext DEFAULT_IOCONTEXT = new IOContext(IOContext.Context.DEFAULT);
    private final Directory directory;
    private final Version version;
    private final Analyzer analyzer;
    private final IndexWriterConfig.OpenMode mode;
    private final Object lock = new Object();

    private final SearcherPool pool;
    private IndexWriter writer;
    private SnapshotDeletionPolicy snapShotter;

    public OptimisedStorage(Directory directory, SearcherPool searcherPool) {
        this(directory, Version.LUCENE_42, new KeywordAnalyzer(), IndexWriterConfig.OpenMode.CREATE_OR_APPEND, searcherPool);
    }

    public OptimisedStorage(Directory directory, Version version, Analyzer analyzer, IndexWriterConfig.OpenMode mode, SearcherPool pool) {
        this.directory = directory;
        this.version = version;
        this.analyzer = analyzer;
        this.mode = mode;
        this.pool = pool;
    }

    @Override
    public Number add(Sequence<Document> documents) throws IOException {
        ensureIndexIsSetup();
        List<Document> docs = documents.toList();
        writer.addDocuments(docs);
        return docs.size();
    }

    @Override
    public Number delete(Query query) throws IOException {
        int count = count(query);
        deleteNoCount(query);
        return count;
    }

    @Override
    public void deleteNoCount(Query query) throws IOException {
        ensureIndexIsSetup();
        writer.deleteDocuments(query);
    }

    @Override
    public void deleteAll() throws IOException {
        if (writer == null) return;

        writer.deleteAll();
        flush();
        deleteAllSegments(directory);
        close();
        writer = null;
    }

    @Override
    public int count(final Query query) throws IOException {
        return search(new Callable1<Searcher, Integer>() {
            @Override
            public Integer call(Searcher searcher) throws Exception {
                return searcher.count(query);
            }
        });
    }

    @Override
    public <T> T search(Callable1<Searcher, T> callable) throws IOException {
        return using(searcher(), callable);
    }

    @Override
    public Searcher searcher() throws IOException {
        ensureIndexIsSetup();
        return pool.searcher();
    }

    @Override
    public void backup(final File folder) throws Exception {
        Files.delete(folder);
        String id = UUID.randomUUID().toString();
        try {
            IndexCommit indexCommit = snapShotter.snapshot(id);
            using(directoryFor(folder), copy(indexCommit.getFileNames()).apply(directory));
        } finally {
            snapShotter.release(id);
        }
    }

    private Directory directoryFor(File file) throws IOException {
        return new NIOFSDirectory(file);
    }

    public static Function2<Directory, Directory, Void> copy(final Collection<String> strings) {
        return new Function2<Directory, Directory, Void>() {
            @Override
            public Void call(Directory source, Directory destination) throws Exception {
                copy(source, destination, strings);
                return VOID;
            }
        };
    }

    public static void copy(Directory source, Directory destination, Collection<String> strings) throws IOException {
        for (String segment : strings) {
            source.copy(destination, segment, segment, DEFAULT_IOCONTEXT);
        }
    }

    @Override
    public void restore(File source) throws Exception {
        synchronized (lock) {
            ensureIndexIsSetup();
            deleteAll();
            Directory sourceDirectory = directoryFor(source);
            using(sourceDirectory, copy(list(sourceDirectory.listAll())).flip().apply(directory));
            resetReadersAndWriters();
        }
    }


    private void resetReadersAndWriters() throws IOException {
        close();
        writer = null;
        pool.markAsDirty();
    }

    private void deleteAllSegments(Directory directory) throws IOException {
        for (String segment : directory.listAll()) {
            directory.deleteFile(segment);
        }
    }


    @Override
    public void close() throws IOException {
        try {
            Closeables.close(writer);
        } catch (Throwable ignored) {
        } finally {
            try {
                ensureDirectoryUnlocked();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void flush() throws IOException {
        writer.commit();
        pool.markAsDirty();
    }

    private void ensureDirectoryUnlocked() throws IOException {
        if (IndexWriter.isLocked(directory)) {
            IndexWriter.unlock(directory);
        }
    }

    private void ensureIndexIsSetup() throws IOException {
        synchronized (lock) {
            if (writer == null) {
                snapShotter = new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
                writer = new IndexWriter(directory, new IndexWriterConfig(version, analyzer).setOpenMode(mode).setIndexDeletionPolicy(snapShotter));
                writer.commit();
            }
        }
    }
}
