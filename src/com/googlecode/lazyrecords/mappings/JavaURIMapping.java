package com.googlecode.lazyrecords.mappings;

import java.net.URI;

import static com.googlecode.totallylazy.URLs.uri;

public class JavaURIMapping implements StringMapping<URI> {
    public URI toValue(String value) {
        return uri(value);
    }

    public String toString(URI value) {
        return value.toString();
    }
}