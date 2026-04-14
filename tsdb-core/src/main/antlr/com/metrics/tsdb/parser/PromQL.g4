grammar PromQL;

// Parser rules
expression
    : expression op=('*'|'/') expression                     # BinaryExpr
    | expression op=('+'|'-') expression                     # BinaryExpr
    | aggregation                                             # AggregationExpr
    | functionCall                                            # FunctionCallExpr
    | vectorSelector                                          # VectorSelectorExpr
    | '(' expression ')'                                      # ParenExpr
    ;

vectorSelector
    : metricName labelMatchers? range?
    ;

range
    : '[' duration ']'
    ;

labelMatchers
    : '{' matcher (',' matcher)* '}'
    ;

matcher
    : labelName matchOp STRING
    ;

matchOp
    : '='
    | '!='
    | '=~'
    | '!~'
    ;

aggregation
    : aggOp grouping? '(' expression ')'
    | aggOp '(' expression ')' grouping?
    ;

grouping
    : ('by' | 'without') '(' labelList ')'
    ;

labelList
    : labelName (',' labelName)*
    ;

aggOp
    : 'sum' | 'avg' | 'min' | 'max' | 'count'
    ;

functionCall
    : funcName '(' expression ')'
    ;

funcName
    : 'rate' | 'irate' | 'increase'
    ;

metricName
    : IDENTIFIER
    ;

labelName
    : IDENTIFIER
    ;

duration
    : NUMBER IDENTIFIER
    ;

// Lexer rules
NUMBER        : [0-9]+ ('.' [0-9]+)? ;
STRING        : '"' (~["\\\r\n] | '\\' .)* '"'
              | '\'' (~['\\\r\n] | '\\' .)* '\''
              ;
IDENTIFIER    : [a-zA-Z_:] [a-zA-Z0-9_:]* ;
WS            : [ \t\r\n]+ -> skip ;
