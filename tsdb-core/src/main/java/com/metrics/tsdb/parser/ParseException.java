package com.metrics.tsdb.parser;

import lombok.Getter;

@Getter
public class ParseException extends RuntimeException {
    private final int line;
    private final int charPosition;

    public ParseException(String message, int line, int charPosition) {
        super(message);
        this.line = line;
        this.charPosition = charPosition;
    }
}
