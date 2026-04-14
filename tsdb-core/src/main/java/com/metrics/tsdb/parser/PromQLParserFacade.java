package com.metrics.tsdb.parser;

import com.metrics.tsdb.parser.ast.Expr;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class PromQLParserFacade {

    public Expr parse(String promql) {
        var charStream = CharStreams.fromString(promql);
        var lexer = new PromQLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser = new PromQLParser(tokenStream);

        // Replace default error listener
        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        ErrorCollector errorCollector = new ErrorCollector();
        lexer.addErrorListener(errorCollector);
        parser.addErrorListener(errorCollector);

        var tree = parser.expression();

        if (errorCollector.hasErrors()) {
            throw new ParseException(
                    errorCollector.getMessage(),
                    errorCollector.getLine(),
                    errorCollector.getCharPosition());
        }

        return new ASTBuildingVisitor().visit(tree);
    }

    private static class ErrorCollector extends BaseErrorListener {
        private String message;
        private int line;
        private int charPosition;
        private boolean hasError;

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg, RecognitionException e) {
            if (!hasError) {
                this.message = msg;
                this.line = line;
                this.charPosition = charPositionInLine;
                this.hasError = true;
            }
        }

        boolean hasErrors() { return hasError; }
        String getMessage() { return message; }
        int getLine() { return line; }
        int getCharPosition() { return charPosition; }
    }
}
