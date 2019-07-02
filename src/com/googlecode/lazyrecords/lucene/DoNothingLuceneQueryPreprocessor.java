package com.googlecode.lazyrecords.lucene;

import org.apache.lucene.search.*;

public class DoNothingLuceneQueryPreprocessor implements LuceneQueryPreprocessor {

    @Override
    public Query process(TermQuery query) {
        return query;
    }

    @Override
    public Query process(WildcardQuery query) {
        return query;
    }

    @Override
    public Query process(PhraseQuery query) {
        return query;
    }

    @Override
    public Query process(PrefixQuery query) {
        return query;
    }

    @Override
    public Query process(MultiPhraseQuery query) {
        return query;
    }

    @Override
    public Query process(FuzzyQuery query) {
        return query;
    }

    @Override
    public Query process(RegexpQuery query) {
        return query;
    }

    @Override
    public Query process(TermRangeQuery query) {
        return query;
    }

    @Override
    public Query process(MatchAllDocsQuery query) {
        return query;
    }
}
