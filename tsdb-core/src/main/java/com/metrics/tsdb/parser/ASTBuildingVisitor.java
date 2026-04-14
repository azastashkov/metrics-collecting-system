package com.metrics.tsdb.parser;

import com.metrics.tsdb.parser.ast.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ASTBuildingVisitor extends PromQLBaseVisitor<Expr> {

    @Override
    public Expr visitBinaryExpr(PromQLParser.BinaryExprContext ctx) {
        Expr left = visit(ctx.expression(0));
        Expr right = visit(ctx.expression(1));
        BinaryOp op = switch (ctx.op.getText()) {
            case "+" -> BinaryOp.ADD;
            case "-" -> BinaryOp.SUB;
            case "*" -> BinaryOp.MUL;
            case "/" -> BinaryOp.DIV;
            default -> throw new ParseException("Unknown operator: " + ctx.op.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        };
        return new BinaryExpr(left, op, right);
    }

    @Override
    public Expr visitAggregationExpr(PromQLParser.AggregationExprContext ctx) {
        return visit(ctx.aggregation());
    }

    @Override
    public Expr visitFunctionCallExpr(PromQLParser.FunctionCallExprContext ctx) {
        return visit(ctx.functionCall());
    }

    @Override
    public Expr visitVectorSelectorExpr(PromQLParser.VectorSelectorExprContext ctx) {
        return visit(ctx.vectorSelector());
    }

    @Override
    public Expr visitParenExpr(PromQLParser.ParenExprContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Expr visitVectorSelector(PromQLParser.VectorSelectorContext ctx) {
        String metricName = ctx.metricName().getText();
        List<LabelMatcher> matchers = parseMatchers(ctx.labelMatchers());

        if (ctx.range() != null) {
            Duration range = parseDuration(ctx.range().duration());
            return new RangeVectorSelector(metricName, matchers, range);
        }

        return new VectorSelector(metricName, matchers);
    }

    @Override
    public Expr visitAggregation(PromQLParser.AggregationContext ctx) {
        AggregateOp op = switch (ctx.aggOp().getText()) {
            case "sum" -> AggregateOp.SUM;
            case "avg" -> AggregateOp.AVG;
            case "min" -> AggregateOp.MIN;
            case "max" -> AggregateOp.MAX;
            case "count" -> AggregateOp.COUNT;
            default -> throw new ParseException("Unknown aggregation: " + ctx.aggOp().getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        };

        Expr inner = visit(ctx.expression());

        GroupingType groupingType = null;
        List<String> groupLabels = Collections.emptyList();

        if (ctx.grouping() != null) {
            var grouping = ctx.grouping();
            groupingType = grouping.getText().startsWith("by") ? GroupingType.BY : GroupingType.WITHOUT;
            groupLabels = new ArrayList<>();
            for (var label : grouping.labelList().labelName()) {
                groupLabels.add(label.getText());
            }
        }

        return new AggregationExpr(op, inner, groupingType, groupLabels);
    }

    @Override
    public Expr visitFunctionCall(PromQLParser.FunctionCallContext ctx) {
        String funcName = ctx.funcName().getText();
        Expr arg = visit(ctx.expression());
        return new FunctionCallExpr(funcName, arg);
    }

    private List<LabelMatcher> parseMatchers(PromQLParser.LabelMatchersContext ctx) {
        if (ctx == null) {
            return Collections.emptyList();
        }

        List<LabelMatcher> matchers = new ArrayList<>();
        for (var matcherCtx : ctx.matcher()) {
            String labelName = matcherCtx.labelName().getText();
            String rawValue = matcherCtx.STRING().getText();
            // Strip quotes
            String value = rawValue.substring(1, rawValue.length() - 1);

            MatchType type = switch (matcherCtx.matchOp().getText()) {
                case "=" -> MatchType.EQUAL;
                case "!=" -> MatchType.NOT_EQUAL;
                case "=~" -> MatchType.REGEX_MATCH;
                case "!~" -> MatchType.REGEX_NOT_MATCH;
                default -> throw new ParseException("Unknown match operator", matcherCtx.start.getLine(), matcherCtx.start.getCharPositionInLine());
            };

            matchers.add(new LabelMatcher(labelName, type, value));
        }
        return matchers;
    }

    private Duration parseDuration(PromQLParser.DurationContext ctx) {
        String numStr = ctx.NUMBER().getText();
        long num = Long.parseLong(numStr);
        String unit = ctx.IDENTIFIER().getText();

        return switch (unit) {
            case "s" -> Duration.ofSeconds(num);
            case "m" -> Duration.ofMinutes(num);
            case "h" -> Duration.ofHours(num);
            case "d" -> Duration.ofDays(num);
            default -> throw new ParseException("Unknown duration unit: " + unit, ctx.start.getLine(), ctx.start.getCharPositionInLine());
        };
    }
}
