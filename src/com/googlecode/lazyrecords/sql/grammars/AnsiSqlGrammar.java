package com.googlecode.lazyrecords.sql.grammars;

import com.googlecode.lazyrecords.Aggregate;
import com.googlecode.lazyrecords.Aliased;
import com.googlecode.lazyrecords.AliasedKeyword;
import com.googlecode.lazyrecords.CompositeKeyword;
import com.googlecode.lazyrecords.Definition;
import com.googlecode.lazyrecords.InnerJoin;
import com.googlecode.lazyrecords.Join;
import com.googlecode.lazyrecords.Joiner;
import com.googlecode.lazyrecords.Keyword;
import com.googlecode.lazyrecords.On;
import com.googlecode.lazyrecords.OuterJoin;
import com.googlecode.lazyrecords.Record;
import com.googlecode.lazyrecords.Using;
import com.googlecode.lazyrecords.sql.AnsiJoinBuilder;
import com.googlecode.lazyrecords.sql.Merger;
import com.googlecode.lazyrecords.sql.expressions.*;
import com.googlecode.totallylazy.*;
import com.googlecode.totallylazy.annotations.multimethod;
import com.googlecode.totallylazy.callables.JoinString;
import com.googlecode.totallylazy.comparators.AscendingComparator;
import com.googlecode.totallylazy.comparators.CompositeComparator;
import com.googlecode.totallylazy.comparators.DescendingComparator;
import com.googlecode.totallylazy.predicates.AlwaysFalse;
import com.googlecode.totallylazy.predicates.AlwaysTrue;
import com.googlecode.totallylazy.predicates.AndPredicate;
import com.googlecode.totallylazy.predicates.Between;
import com.googlecode.totallylazy.predicates.ContainsPredicate;
import com.googlecode.totallylazy.predicates.EndsWithPredicate;
import com.googlecode.totallylazy.predicates.EqualsPredicate;
import com.googlecode.totallylazy.predicates.GreaterThan;
import com.googlecode.totallylazy.predicates.GreaterThanOrEqualTo;
import com.googlecode.totallylazy.predicates.InPredicate;
import com.googlecode.totallylazy.predicates.LessThan;
import com.googlecode.totallylazy.predicates.LessThanOrEqualTo;
import com.googlecode.totallylazy.predicates.Not;
import com.googlecode.totallylazy.predicates.NullPredicate;
import com.googlecode.totallylazy.predicates.OrPredicate;
import com.googlecode.totallylazy.predicates.StartsWithPredicate;
import com.googlecode.totallylazy.predicates.WherePredicate;

import java.util.Comparator;
import java.util.Map;

import static com.googlecode.lazyrecords.sql.expressions.AnsiFromClause.fromClause;
import static com.googlecode.lazyrecords.sql.expressions.Expressions.empty;
import static com.googlecode.lazyrecords.sql.expressions.Expressions.columnReference;
import static com.googlecode.lazyrecords.sql.expressions.Expressions.textOnly;
import static com.googlecode.lazyrecords.sql.expressions.SetFunctionType.setFunctionType;
import static com.googlecode.totallylazy.Option.none;
import static com.googlecode.totallylazy.Option.some;
import static com.googlecode.totallylazy.Sequences.one;
import static com.googlecode.totallylazy.Sequences.repeat;
import static com.googlecode.totallylazy.Sequences.sequence;

public class AnsiSqlGrammar implements SqlGrammar {
    private final Map<Class, String> mappings;

    public AnsiSqlGrammar(Map<Class, String> mappings) {
        this.mappings = mappings;
    }

    public AnsiSqlGrammar() {
        this(ColumnDatatypeMappings.defaultMappings());
    }

    @Override
    public AnsiJoinBuilder join(ExpressionBuilder primary, ExpressionBuilder secondary, JoinType type, JoinSpecification specification) {
        if (primary instanceof AnsiJoinBuilder) {
            AnsiJoinBuilder builder = (AnsiJoinBuilder) primary;
            return AnsiJoinBuilder.join(this, Merger.merger(builder.build(), (SelectExpression) secondary.build(), type, specification).merge());
        }
        return AnsiJoinBuilder.join(this, Merger.merger((SelectExpression) primary.build(), (SelectExpression) secondary.build(), type, specification).merge());
    }

    @Override
    public AnsiJoinBuilder join(final ExpressionBuilder builder, final Join join) {
        ExpressionBuilder secondary = (ExpressionBuilder) ((Expressible) join.records()).build();
        return join(builder, secondary, joinType(join), joinSpecification(join.joiner()));
    }

    @Override
    public JoinSpecification joinSpecification(final Joiner joiner) {
        if (joiner instanceof On<?>)
            return JoinCondition.joinCondition(columnReference(((On) joiner).left()), columnReference(((On) joiner).right()));
        if (joiner instanceof Using)
            return NamedColumnsJoin.namedColumnsJoin(((Using) joiner).keywords().map(Keyword.functions.name));
        throw new UnsupportedOperationException();
    }

    @Override
    public JoinType joinType(final Join join) {
        if (join instanceof InnerJoin) return AnsiJoinType.inner;
        if (join instanceof OuterJoin) return AnsiJoinType.left;
        throw new UnsupportedOperationException();
    }


    @Override
    public SelectExpression selectExpression(Option<SetQuantifier> setQuantifier,
                                             Sequence<? extends Keyword<?>> selectList,
                                             Definition fromClause,
                                             Option<Predicate<? super Record>> whereClause,
                                             Option<Comparator<? super Record>> orderByClause,
                                             Option<Sequence<? extends Keyword<?>>> groupByClause,
                                             Option<Integer> offsetClause, Option<Integer> fetchClause) {
        return AnsiSelectExpression.selectExpression(setQuantifier, selectList(selectList), fromClause(fromClause),
                whereClause(whereClause), orderByClause.map(functions.orderByClause(this)),
                groupByClause.map(functions.groupByClause(this)),
                offsetClause.map(OffsetClause.functions.offsetClause()),
                fetchClause.map(FetchClause.functions.fetchClause()));
    }

    @Override
    public SelectList selectList(Sequence<? extends Keyword<?>> select) {
        return AnsiSelectList.selectList(select.map(functions.derivedColumn(this)));
    }

    @Override
    public FromClause fromClause(Definition definition) {
        return AnsiFromClause.fromClause(definition);
    }

    @Override
    public OrderByClause orderByClause(Comparator<? super Record> orderBy) {
        return AnsiOrderByClause.orderByClause(sortSpecification(orderBy));
    }

    @Override
    public OffsetClause offsetClause(int number) {
        return new AnsiOffsetClause(number);
    }

    @Override
    public FetchClause fetchClause(int number) {
        return new AnsiFetchClause(number);
    }

    @Override
    public GroupByClause groupByClause(Sequence<? extends Keyword<?>> columns) {
        return AnsiGroupByClause.groupByClause(sequence(columns).map(new Callable1<Keyword<?>, DerivedColumn>() {
            @Override
            public DerivedColumn call(Keyword<?> keyword) throws Exception {
                return derivedColumn(keyword);
            }
        }));
    }

    private multi multiSS;
    public Sequence<SortSpecification> sortSpecification(Comparator<? super Record> comparator) {
        if(multiSS == null) multiSS = new multi(){};
        return multiSS.<Sequence<SortSpecification>>methodOption(comparator).getOrThrow(new UnsupportedOperationException("Unsupported comparator " + comparator));
    }

    @multimethod public Sequence<SortSpecification> sortSpecification(AscendingComparator<? super Record, ?> comparator) {
        return one(AnsiSortSpecification.sortSpecification(valueExpression(comparator.callable()), OrderingSpecification.asc));
    }

    @multimethod public Sequence<SortSpecification> sortSpecification(DescendingComparator<? super Record, ?> comparator) {
        return one(AnsiSortSpecification.sortSpecification(valueExpression(comparator.callable()), OrderingSpecification.desc));
    }

    @multimethod public Sequence<SortSpecification> sortSpecification(CompositeComparator<Record> comparator) {
        return comparator.comparators().flatMap(sortSpecification);
    }

    public Mapper<Comparator<? super Record>, Sequence<SortSpecification>> sortSpecification = new Mapper<Comparator<? super Record>, Sequence<SortSpecification>>() {
        @Override
        public Sequence<SortSpecification> call(Comparator<? super Record> comparator) throws Exception {
            return sortSpecification(comparator);
        }
    };

    @Override
    public DerivedColumn derivedColumn(Callable1<? super Record, ?> callable) {
        ValueExpression expression = valueExpression(callable);
        Keyword<?> keyword = (Keyword<?>) callable;
        if (callable instanceof Aliased) {
            return AnsiDerivedColumn.derivedColumn(expression, some(asClause(keyword.name())), keyword.forClass());
        }
        return AnsiDerivedColumn.derivedColumn(expression, none(AsClause.class), keyword.forClass());
    }

    @Override
    public AsClause asClause(String alias) {
        return AnsiAsClause.asClause(alias);
    }
    private static multi multiVE;
    @Override
    public ValueExpression valueExpression(Callable1<? super Record, ?> callable) {
        if(multiVE == null) multiVE = new multi(){};
        return multiVE.<ValueExpression>methodOption(callable).getOrThrow(new UnsupportedOperationException("Unsupported reducer " + callable));
    }

    @Override @multimethod
    public ValueExpression valueExpression(Keyword<?> keyword) {
        return Expressions.columnReference(keyword);
    }

    @Override @multimethod
    public ValueExpression valueExpression(AliasedKeyword aliasedKeyword) {
        return valueExpression(aliasedKeyword.source());
    }

    @Override @multimethod
    public ValueExpression valueExpression(Aggregate aggregate) {
        return setFunctionType(aggregate.reducer(), aggregate.source());
    }

    @Override @multimethod
    public ValueExpression valueExpression(CompositeKeyword<?> composite) {
        Binary<?> combiner = composite.combiner();
        if (combiner instanceof JoinString) {
            return concat(composite.keywords());
        }
        throw new UnsupportedOperationException("Unsupported combiner " + combiner);
    }

    @Override
    public ValueExpression concat(Sequence<? extends Keyword<?>> keywords) {
        return new CompositeExpression(keywords.map(columnReference()), "(", "||", ")");
    }

    @Override
    public WhereClause whereClause(Predicate<? super Record> predicate) {
        return AnsiWhereClause.whereClause(toSql(predicate));
    }

    public Option<WhereClause> whereClause(Option<? extends Predicate<? super Record>> predicate) {
        return predicate.map(whereClause());
    }

    public Mapper<Predicate<? super Record>, WhereClause> whereClause() {
        return new Mapper<Predicate<? super Record>, WhereClause>() {
            public WhereClause call(Predicate<? super Record> predicate) throws Exception {
                return whereClause(predicate);
            }
        };
    }

    private multi multiToSql;
    public Expression toSql(Predicate<?> predicate) {
        if(multiToSql == null) multiToSql = new multi(){};
        return multiToSql.<Expression>methodOption(predicate).getOrThrow(new UnsupportedOperationException());
    }

    @multimethod public Expression toSql(AlwaysTrue predicate) { return textOnly("1 = 1"); }
    @multimethod public Expression toSql(AlwaysFalse predicate) { return textOnly("1 != 1"); }

    @multimethod public Expression toSql(AndPredicate<?> predicate) {
        if (predicate.predicates().isEmpty()) return empty();
        return AndExpression.andExpression(toExpressions(predicate.predicates()));
    }

    @multimethod public Expression toSql(OrPredicate<?> predicate) {
        if (predicate.predicates().isEmpty()) return empty();
        if (!predicate.predicates().safeCast(AlwaysTrue.class).isEmpty()) return empty();
        return OrExpression.orExpression(toExpressions(predicate.predicates()));
    }

    @multimethod public Expression toSql(Not<?> predicate) {
        return textOnly("not (").join(toSql(predicate.predicate())).join(textOnly(")"));
    }

    @multimethod public PredicateExpression toSql(WherePredicate<Record, ?> predicate) {
        ValueExpression valueExpression = valueExpression(predicate.callable());
        Expression predicateSql = toSql(predicate.predicate());
        return AnsiPredicateExpression.predicateExpression(valueExpression, predicateSql);
    }

    @multimethod public Expression toSql(NullPredicate<?> predicate) {
        return textOnly("is null");
    }

    @multimethod public Expression toSql(EqualsPredicate<?> predicate) {
        return Expressions.expression("= ?", predicate.value());
    }

    @multimethod public Expression toSql(GreaterThan<?> predicate) {
        return Expressions.expression("> ?", predicate.value());
    }

    @multimethod public Expression toSql(GreaterThanOrEqualTo<?> predicate) {
        return Expressions.expression(">= ?", predicate.value());
    }

    @multimethod public Expression toSql(LessThan<?> predicate) {
        return Expressions.expression("< ?", predicate.value());
    }

    @multimethod public Expression toSql(LessThanOrEqualTo<?> predicate) {
        return Expressions.expression("<= ?", predicate.value());
    }

    @multimethod public Expression toSql(Between<?> predicate) {
        return Expressions.expression("between ? and ?", sequence(predicate.lower(), predicate.upper()));
    }

    @multimethod public Expression toSql(InPredicate<?> predicate) {
        Sequence<Object> sequence = sequence(predicate.values());
        if (sequence instanceof Expressible) {
            Expression pair = ((Expressible) sequence).build();
            return Expressions.expression("in ( " + pair.text() + ")", pair.parameters());
        }
        return Expressions.expression(repeat("?").take(sequence.size()).toString("in (", ",", ")"), sequence);
    }

    @multimethod public Expression toSql(StartsWithPredicate predicate) {
        return Expressions.expression("like ?", sequence(predicate.value() + "%"));
    }

    @multimethod public Expression toSql(EndsWithPredicate predicate) {
        return Expressions.expression("like ?", sequence("%" + predicate.value()));
    }

    @multimethod public Expression toSql(ContainsPredicate predicate) {
        return Expressions.expression("like ?", sequence("%" + predicate.value() + "%"));
    }

    public Sequence<Expression> toExpressions(Sequence<? extends Predicate<?>> predicates) {
        return predicates.map(toSql);
    }

    public Mapper<Predicate<?>, Expression> toSql = new Mapper<Predicate<?>, Expression>() {
        public Expression call(Predicate<?> predicate) throws Exception {
            return toSql(predicate);
        }
    };

    @Override
    public Expression insertStatement(Definition definition, Record record) {
        return InsertStatement.insertStatement(definition, record);
    }

    @Override
    public UpdateStatement updateStatement(Definition definition, Predicate<? super Record> predicate, Record record) {
        return AnsiUpdateStatement.updateStatement(Expressions.tableName(definition), setClause(definition, record), whereClause(predicate));
    }

    public SetClause setClause(Definition definition, Record record) {
        Sequence<Keyword<?>> updatingKeywords = Record.methods.filter(record, definition.fields()).keywords();
        return AnsiSetClause.setClause(updatingKeywords.map(columnReference()), record.valuesFor(updatingKeywords));
    }

    @Override
    public Expression deleteStatement(Definition definition, Option<? extends Predicate<? super Record>> predicate) {
        return AnsiDeleteStatement.deleteStatement(fromClause(definition), whereClause(predicate));
    }

    @Override
    public Expression createTable(Definition definition) {
        return TableDefinition.createTable(definition, mappings);
    }

    @Override
    public Expression dropTable(Definition definition) {
        return TableDefinition.dropTable(definition);
    }

}