package com.googlecode.lazyrecords.lucene;

import org.apache.lucene.search.*;

public interface LuceneQueryPreprocessor {
    Query process(TermQuery query);

    Query process(WildcardQuery query);

    Query process(PhraseQuery query);

    Query process(PrefixQuery query);

    Query process(MultiPhraseQuery query);

    Query process(FuzzyQuery query);

    Query process(RegexpQuery query);

    Query process(TermRangeQuery query);

    Query process(MatchAllDocsQuery query);
}
