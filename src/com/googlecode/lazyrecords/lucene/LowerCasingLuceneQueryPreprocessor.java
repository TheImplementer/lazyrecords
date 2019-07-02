package com.googlecode.lazyrecords.lucene;

import com.googlecode.funclate.internal.lazyparsec.functors.Unary;
import com.googlecode.totallylazy.Sequence;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;

import static com.googlecode.totallylazy.Sequences.sequence;

public class LowerCasingLuceneQueryPreprocessor extends DoNothingLuceneQueryPreprocessor {

    @Override
    public Query process(TermQuery query) {
        final Term originalTerm = query.getTerm();
        return new TermQuery(asLowercaseTerm(originalTerm));
    }

    @Override
    public Query process(TermRangeQuery query) {
        final BytesRef lowerTerm = lowerCaseValueOf(query.getLowerTerm());
        final BytesRef upperTerm = lowerCaseValueOf(query.getUpperTerm());
        return new TermRangeQuery(query.getField(), lowerTerm, upperTerm, query.includesLower(), query.includesUpper());
    }

    @Override
    public Query process(FuzzyQuery query) {
        final Term originalTerm = query.getTerm();
        return new FuzzyQuery(asLowercaseTerm(originalTerm), query.getMaxEdits(), query.getPrefixLength());
    }

    @Override
    public Query process(WildcardQuery query) {
        final Term originalTerm = query.getTerm();
        return new WildcardQuery(asLowercaseTerm(originalTerm));
    }

    @Override
    public Query process(PhraseQuery query) {
        final Term[] terms = query.getTerms();
        final int[] positions = query.getPositions();
        final PhraseQuery.Builder toReturn = new PhraseQuery.Builder();
        toReturn.setSlop(query.getSlop());
        for (int i = 0; i < terms.length; i++) {
            toReturn.add(asLowercaseTerm(terms[i]), positions[i]);
        }
        return toReturn.build();
    }

    @Override
    public Query process(PrefixQuery query) {
        final Term prefix = query.getPrefix();
        return new PrefixQuery(asLowercaseTerm(prefix));
    }

    @Override
    public Query process(MultiPhraseQuery query) {
        final Term[][] termArrays = query.getTermArrays();
        final int[] positions = query.getPositions();
        final MultiPhraseQuery.Builder toReturn = new MultiPhraseQuery.Builder();
        toReturn.setSlop(query.getSlop());
        for (int i = 0; i < termArrays.length; i++) {
            final Sequence<Term> lowerCasedTerms = sequence(termArrays[i]).map(asLowerCaseTerm());
            toReturn.add(lowerCasedTerms.toArray(Term.class), positions[i]);
        }
        return toReturn.build();
    }

    private Unary<Term> asLowerCaseTerm() {
        return new Unary<Term>() {
            @Override
            public Term call(Term term) throws Exception {
                return asLowercaseTerm(term);
            }
        };
    }

    private Term asLowercaseTerm(Term originalTerm) {
        final String lowerCaseQuery = originalTerm.text().toLowerCase();
        return new Term(originalTerm.field(), lowerCaseQuery);
    }

    private BytesRef lowerCaseValueOf(BytesRef lowerTerm) {
        if (lowerTerm == null) return null;
        return new BytesRef(lowerTerm.utf8ToString().toLowerCase());
    }
}
