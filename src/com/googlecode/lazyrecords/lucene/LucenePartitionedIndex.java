package com.googlecode.lazyrecords.lucene;

import com.googlecode.lazyrecords.Definition;
import com.googlecode.totallylazy.CloseableList;
import com.googlecode.totallylazy.Files;
import com.googlecode.totallylazy.Function1;
import com.googlecode.totallylazy.Function2;
import com.googlecode.totallylazy.Function3;
import com.googlecode.totallylazy.Lazy;
import com.googlecode.totallylazy.Mapper;
import com.googlecode.totallylazy.Pair;
import com.googlecode.totallylazy.collections.PersistentMap;
import org.apache.lucene.store.Directory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.googlecode.lazyrecords.lucene.PartitionedIndex.functions.mmapDirectory;
import static com.googlecode.lazyrecords.lucene.PartitionedIndex.functions.ramDirectory;
import static com.googlecode.totallylazy.Callables.value;
import static com.googlecode.totallylazy.Closeables.safeClose;
import static com.googlecode.totallylazy.Files.directory;
import static com.googlecode.totallylazy.Functions.returns;
import static com.googlecode.totallylazy.Sequences.sequence;
import static com.googlecode.totallylazy.Zip.unzip;
import static com.googlecode.totallylazy.Zip.zip;
import static com.googlecode.totallylazy.collections.PersistentSortedMap.constructors.sortedMap;

public class LucenePartitionedIndex implements Closeable, Persistence, PartitionedIndex {
    private final ConcurrentMap<String, Lazy<LuceneStorage>> partitions = new ConcurrentHashMap<String, Lazy<LuceneStorage>>();
    private final CloseableList closeables = new CloseableList();
    private final Function1<String, Directory> directoryActivator;
    private final Function3<String, Directory, SearcherPool, LuceneStorage> luceneStorageActivator;

    private LucenePartitionedIndex(Function1<String, Directory> directoryActivator, Function3<String, Directory, SearcherPool, LuceneStorage> luceneStorageActivator) {
        this.directoryActivator = directoryActivator;
        this.luceneStorageActivator = luceneStorageActivator;
    }

    public static LucenePartitionedIndex partitionedIndex(final File rootDirectory) {
        return partitionedIndex(mmapDirectory(rootDirectory));
    }

    public static LucenePartitionedIndex partitionedIndex() {
        return partitionedIndex(ramDirectory());
    }

    public static LucenePartitionedIndex partitionedIndex(Function1<String, Directory> directoryActivator) {
        return partitionedIndex(directoryActivator, new Function3<String, Directory, SearcherPool, LuceneStorage>() {
            @Override
            public LuceneStorage call(String definition, Directory directory, SearcherPool searcherPool) throws Exception {
                return new OptimisedStorage(directory, searcherPool);
            }
        });
    }

    public static LucenePartitionedIndex partitionedIndex(Function1<String, Directory> directoryActivator, final Function2<Directory, SearcherPool, LuceneStorage> luceneStorageActivator) {
        return new LucenePartitionedIndex(directoryActivator, new Function3<String, Directory, SearcherPool, LuceneStorage>() {
            @Override
            public LuceneStorage call(String ignoredDefinition, Directory directory, SearcherPool searcherPool) throws Exception {
                return luceneStorageActivator.call(directory, searcherPool);
            }
        });
    }

    public static LucenePartitionedIndex partitionedIndex(Function1<String, Directory> directoryActivator, Function3<String, Directory, SearcherPool, LuceneStorage> luceneStorageActivator) {
        return new LucenePartitionedIndex(directoryActivator, luceneStorageActivator);
    }

    public void close() throws IOException {
        sequence(partitions.values()).map(value(LuceneStorage.class)).each(safeClose());
        partitions.clear();
        closeables.close();
    }

    @Override
    public LuceneStorage partition(Definition definition) throws IOException {
        return partition(definition.name());
    }

    @Override
    public LuceneStorage partition(final String definition) throws IOException {
        partitions.putIfAbsent(definition, lazyStorage(definition));
        return partitions.get(definition).value();
    }

    @Override
    public PersistentMap<String, LuceneStorage> partitions() {
        return sortedMap(sequence(partitions.entrySet()).map(new Mapper<Map.Entry<String, Lazy<LuceneStorage>>, Pair<String, LuceneStorage>>() {
            @Override
            public Pair<String, LuceneStorage> call(Map.Entry<String, Lazy<LuceneStorage>> entry) throws Exception {
                return Pair.pair(returns(entry.getKey()), entry.getValue());
            }
        }));
    }

    private Lazy<LuceneStorage> lazyStorage(final String definition) {
        return new Lazy<LuceneStorage>() {
            @Override
            protected LuceneStorage get() throws Exception {
                Directory directory = closeables.manage(directoryActivator.call(definition));
                SearcherPool searcherPool = closeables.manage(new LucenePool(directory));
                return luceneStorageActivator.call(definition, directory, searcherPool);
            }
        };
    }

    @Override
    public void deleteAll() throws IOException {
        for (Lazy<LuceneStorage> storageLazy : partitions.values()) {
            storageLazy.value().deleteAll();
        }
        close();
    }

    @Override
    public void backup(File bgb) throws Exception {
        File destination = tempUnzipLocation();
        Files.delete(destination);

        for (Map.Entry<String, Lazy<LuceneStorage>> entry : partitions.entrySet()) {
            String name = entry.getKey();
            LuceneStorage luceneStorage = entry.getValue().value();
            luceneStorage.backup(directory(destination, name));
        }

        zip(destination, bgb);
        Files.delete(destination);
    }

    @Override
    public void restore(File file) throws Exception {
        File sourceDirectory = unzipIfNeeded(file);
        deleteAll();
        for (File partition : Files.files(sourceDirectory)) {
            String name = partition.getName();
            LuceneStorage luceneStorage = partition(name);
            luceneStorage.restore(directory(sourceDirectory, name));
        }
    }

    private File tempUnzipLocation() {
        return Files.emptyVMDirectory("lucene-index-unzipped");
    }

    private File unzipIfNeeded(File source) throws IOException {
        if (source.isFile()) {
            File unzipped = tempUnzipLocation();
            unzip(source, unzipped);
            return unzipped;
        }
        return source;
    }
}