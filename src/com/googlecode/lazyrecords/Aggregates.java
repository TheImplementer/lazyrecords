package com.googlecode.lazyrecords;

import com.googlecode.totallylazy.Callable2;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Unchecked;
import com.googlecode.totallylazy.Value;

import static com.googlecode.lazyrecords.Record.constructors.record;
import static com.googlecode.totallylazy.Sequences.sequence;

public class Aggregates implements Callable2<Record, Record, Record>, Value<Sequence<Aggregate<?,?>>> {
    private final Sequence<Aggregate<?,?>> aggregates;

    public Aggregates(final Sequence<Aggregate<?,?>> aggregates) {
        this.aggregates = aggregates;
    }

    public Record call(final Record accumulator, final Record nextRecord) throws Exception {
        return aggregates.fold(Record.constructors.record(), new Callable2<Record, Aggregate<?, ?>, Record>() {
            @Override
            public Record call(Record record, Aggregate<?, ?> aggregate) throws Exception {
                Object current = accumulatorValue(accumulator, aggregate);
                Object next = nextRecord.get(aggregate.source());
                Aggregate<Object, Object> cast = Unchecked.cast(aggregate);
                return record.set(cast, cast.call(current, next));
            }
        });
    }

    private Object accumulatorValue(Record record, Aggregate<?,?> aggregate) {
        Object value = record.get(aggregate.source());
        if (value == null) {
            return record.get(aggregate);
        }
        return value;
    }

    public Sequence<Aggregate<?,?>> value() {
        return aggregates;
    }

    public static Aggregates to(final Aggregate<?,?>... aggregates) {
        return aggregates(sequence(aggregates));
    }

    public static Aggregates aggregates(final Sequence<Aggregate<?,?>> sequence) {
        return new Aggregates(sequence);
    }
}
