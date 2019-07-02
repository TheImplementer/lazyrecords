package com.googlecode.lazyrecords.lucene;

import com.googlecode.totallylazy.annotations.multimethod;
import com.googlecode.totallylazy.multi;
import org.apache.lucene.search.*;

import java.util.ArrayList;
import java.util.List;

import static com.googlecode.totallylazy.Sequences.sequence;

public class LuceneQueryVisitor {

    private final LuceneQueryPreprocessor preprocessor;

    public LuceneQueryVisitor(LuceneQueryPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    private multi multi;

    public Query visit(Query query) {
        if (multi == null) multi = new multi() {
        };
        return multi.<Query>methodOption(query).getOrThrow(new UnsupportedOperationException());
    }

    @multimethod
    private Query visit(BooleanQuery query) {
        BooleanQuery.Builder visitedQuery = new BooleanQuery.Builder();
        for (BooleanClause clause : query) {
            visitedQuery.add(visit(clause.getQuery()), clause.getOccur());
        }
        return visitedQuery.build();
    }

    @multimethod
    private Query visit(ConstantScoreQuery query) {
        if (query.getQuery() == null) {
            throw new UnsupportedOperationException();
        }
        return new ConstantScoreQuery(visit(query.getQuery()));
    }

    @multimethod
    private Query visit(DisjunctionMaxQuery query) {
        List<Query> queries = sequence(query).map(this::visit).toList();

        return new DisjunctionMaxQuery(queries, query.getTieBreakerMultiplier());
    }

    @multimethod
    private Query visit(TermQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(WildcardQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(PhraseQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(PrefixQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(MultiPhraseQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(FuzzyQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(RegexpQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(TermRangeQuery query) {
        return preprocessor.process(query);
    }

    @multimethod
    private Query visit(MatchAllDocsQuery query) {
        return preprocessor.process(query);
    }

}
