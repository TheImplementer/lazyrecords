package com.googlecode.lazyrecords.lucene.mappings;

import com.googlecode.lazyrecords.Record;
import com.googlecode.lazyrecords.*;
import com.googlecode.lazyrecords.lucene.Lucene;
import com.googlecode.lazyrecords.mappings.StringMappings;
import com.googlecode.totallylazy.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;

import static com.googlecode.lazyrecords.Definition.methods.sortFields;
import static com.googlecode.lazyrecords.Record.functions.updateValues;
import static com.googlecode.totallylazy.Predicates.*;
import static com.googlecode.totallylazy.Sequences.sequence;

public class LuceneMappings {
    private final StringMappings stringMappings;

    public LuceneMappings(StringMappings stringMappings) {
        this.stringMappings = stringMappings;
    }

    public LuceneMappings() {
        this(new StringMappings());
    }

    public StringMappings stringMappings() {
        return stringMappings;
    }

    public ToRecord<Document> asRecord(final Sequence<Keyword<?>> definitions) {
        return new ToRecord<Document>() {
            public Record call(Document document) throws Exception {
                return sequence(document.getFields()).
                        map(asPair(definitions)).
                        filter(where(Callables.<Keyword<?>>first(), is(Predicates.<Keyword<?>>not(Lucene.RECORD_KEY)).and(in(definitions)))).
                        fold(SourceRecord.record(document), updateValues());
            }
        };
    }

    public ToRecord<Document> asUnfilteredRecord(final Sequence<Keyword<?>> definitions) {
        return new ToRecord<Document>() {
            public Record call(Document document) throws Exception {
                return sequence(document.getFields()).
                        map(asPair(definitions)).
                        filter(where(Callables.<Keyword<?>>first(), is(Predicates.<Keyword<?>>not(Lucene.RECORD_KEY)))).
                        fold(SourceRecord.record(document), updateValues());
            }
        };
    }

    public Function1<IndexableField, Pair<Keyword<?>, Object>> asPair(final Sequence<Keyword<?>> definitions) {
        return new Function1<IndexableField, Pair<Keyword<?>, Object>>() {
            public Pair<Keyword<?>, Object> call(IndexableField fieldable) throws Exception {
                String name = fieldable.name();
                Keyword<?> keyword = Keyword.methods.matchKeyword(name, definitions);
                return Pair.<Keyword<?>, Object>pair(keyword, stringMappings.toValue(keyword.forClass(), fieldable.stringValue()));
            }
        };
    }

    public Function1<Pair<Keyword<?>, Object>, Pair<IndexableField, SortedDocValuesField>> asField(final Sequence<Keyword<?>> definitions) {
        return new Function1<Pair<Keyword<?>, Object>, Pair<IndexableField, SortedDocValuesField>>() {
            @Override
            public Pair<IndexableField, SortedDocValuesField> call(Pair<Keyword<?>, Object> pair) throws Exception {
                if (pair.second() == null) {
                    return null;
                }

                String name = pair.first().name();
                Keyword<?> keyword = Keyword.methods.matchKeyword(name, definitions);
                FieldType fieldType = new FieldType(TextField.TYPE_STORED);
                fieldType.setOmitNorms(false);
                SortedDocValuesField sortedDocValuesField = new SortedDocValuesField(name, new BytesRef(LuceneMappings.this.stringMappings.toString(keyword.forClass(), pair.second())));
                Field field = new Field(name, LuceneMappings.this.stringMappings.toString(keyword.forClass(), pair.second()), fieldType);
                return Pair.pair(field, sortedDocValuesField);
            }
        };
    }

    public RecordTo<Document> asDocument(final Definition definition) {
        return new RecordTo<Document>() {
            public Document call(Record record) throws Exception {
                return sortFields(definition, record).fields().
                        append(Pair.<Keyword<?>, Object>pair(Lucene.RECORD_KEY, definition)).
                        map(asField(definition.fields())).
                        filter(notNullValue()).
                        fold(new Document(), intoFields());
            }
        };
    }

    public static Function2<? super Document, Pair<IndexableField, SortedDocValuesField>, Document> intoFields() {
        return new Function2<Document, Pair<IndexableField, SortedDocValuesField>, Document>() {
            @Override
            public Document call(Document document, Pair<IndexableField, SortedDocValuesField> fieldable) throws Exception {
                document.add(fieldable.first());
                document.add(fieldable.second());
                return document;
            }
        };
    }


}
