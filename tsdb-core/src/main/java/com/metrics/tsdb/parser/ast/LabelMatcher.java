package com.metrics.tsdb.parser.ast;

import lombok.Value;

@Value
public class LabelMatcher {
    String labelName;
    MatchType type;
    String value;
}
