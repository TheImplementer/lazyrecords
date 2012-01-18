package com.googlecode.lazyrecords.sql;

import com.googlecode.totallylazy.Callable1;
import com.googlecode.totallylazy.Callable2;
import com.googlecode.totallylazy.Iterators;
import com.googlecode.totallylazy.Sequence;
import com.googlecode.totallylazy.Sets;
import com.googlecode.lazyrecords.Record;
import com.googlecode.lazyrecords.sql.expressions.Expressible;
import com.googlecode.lazyrecords.sql.expressions.Expression;
import com.googlecode.lazyrecords.sql.expressions.SelectBuilder;
import com.googlecode.totallylazy.Unchecked;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Set;

import static java.lang.String.format;

class SingleValueSequence<T> extends Sequence<T> implements Expressible {
    private final Callable1<? super Record, ? extends T> callable;
    private final PrintStream logger;
    private final SqlRecords sqlRecords;
    private final SelectBuilder builder;

    public SingleValueSequence(final SqlRecords sqlRecords, final SelectBuilder builder, final Callable1<? super Record, ? extends T> callable, final PrintStream logger) {
        this.sqlRecords = sqlRecords;
        this.builder = builder;
        this.callable = callable;
        this.logger = logger;
    }

    public Iterator<T> iterator() {
        return execute(builder);
    }

    private Iterator<T> execute(final SelectBuilder builder) {
        return Iterators.map(sqlRecords.iterator(builder.build(), builder.select()), callable);
    }

    public Expression express() {
        return builder.express();
    }

    @Override
    public <S> S reduce(Callable2<? super S, ? super T, ? extends S> callable) {
        try{
            SelectBuilder reduce = builder.reduce(callable);
            return Unchecked.cast(sqlRecords.query(reduce.express(), reduce.select()).head().fields().head().second());
        } catch (UnsupportedOperationException ex) {
            logger.println(format("Warning: Unsupported Callable2 %s dropping down to client side sequence functionality", callable));
            return super.reduce(callable);
        }
    }

    @Override
    public <S extends Set<T>> S toSet(S set) {
        return Sets.set(set, execute(builder.distinct()));
    }
}
