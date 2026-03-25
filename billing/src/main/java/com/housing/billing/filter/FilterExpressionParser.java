package com.housing.billing.filter;

import com.housing.billing.exception.InvalidFilterSyntaxException;

import java.util.ArrayList;
import java.util.List;

public class FilterExpressionParser {

    public sealed interface Node permits ConditionNode, LogicalNode {}

    public record ConditionNode(String field, ComparisonOperator operator, Literal literal) implements Node {}

    public record LogicalNode(Node left, LogicalOperator operator, Node right) implements Node {}

    public record Literal(Object value, LiteralType type) {}

    public enum LogicalOperator {
        AND,
        OR
    }

    public enum LiteralType {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    private enum TokenType {
        IDENTIFIER,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL,
        OPERATOR,
        AND,
        OR,
        LPAREN,
        RPAREN,
        EOF
    }

    private record Token(TokenType type, String text, int position) {}

    private final List<Token> tokens = new ArrayList<>();
    private int index;

    public Node parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new InvalidFilterSyntaxException("Filter expression cannot be blank");
        }
        tokenize(expression);
        index = 0;
        Node root = parseOr();
        if (peek().type != TokenType.EOF) {
            throw syntax("Unexpected token: '" + peek().text + "'", peek().position);
        }
        return root;
    }

    private Node parseOr() {
        Node left = parseAnd();
        while (match(TokenType.OR)) {
            Node right = parseAnd();
            left = new LogicalNode(left, LogicalOperator.OR, right);
        }
        return left;
    }

    private Node parseAnd() {
        Node left = parseTerm();
        while (match(TokenType.AND)) {
            Node right = parseTerm();
            left = new LogicalNode(left, LogicalOperator.AND, right);
        }
        return left;
    }

    private Node parseTerm() {
        if (match(TokenType.LPAREN)) {
            Node inner = parseOr();
            consume(TokenType.RPAREN, "Expected ')' after grouped expression");
            return inner;
        }
        return parseCondition();
    }

    private Node parseCondition() {
        Token field = consume(TokenType.IDENTIFIER, "Expected field name");
        Token operator = consume(TokenType.OPERATOR, "Expected comparison operator");
        Token value = consumeAny(
                List.of(TokenType.STRING, TokenType.NUMBER, TokenType.BOOLEAN, TokenType.NULL, TokenType.IDENTIFIER),
                "Expected value (string, number, boolean, null)"
        );
        return new ConditionNode(field.text, ComparisonOperator.fromSymbol(operator.text), tokenToLiteral(value));
    }

    private Literal tokenToLiteral(Token token) {
        return switch (token.type) {
            case STRING -> new Literal(token.text, LiteralType.STRING);
            case NUMBER -> new Literal(token.text, LiteralType.NUMBER);
            case BOOLEAN -> new Literal(Boolean.parseBoolean(token.text), LiteralType.BOOLEAN);
            case NULL -> new Literal(null, LiteralType.NULL);
            case IDENTIFIER -> new Literal(token.text, LiteralType.STRING);
            default -> throw syntax("Unsupported literal: '" + token.text + "'", token.position);
        };
    }

    private boolean match(TokenType type) {
        if (peek().type == type) {
            index++;
            return true;
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        Token token = peek();
        if (token.type != type) {
            throw syntax(message + ", found '" + token.text + "'", token.position);
        }
        index++;
        return token;
    }

    private Token consumeAny(List<TokenType> types, String message) {
        Token token = peek();
        if (!types.contains(token.type)) {
            throw syntax(message + ", found '" + token.text + "'", token.position);
        }
        index++;
        return token;
    }

    private Token peek() {
        return tokens.get(index);
    }

    private InvalidFilterSyntaxException syntax(String message, int position) {
        return new InvalidFilterSyntaxException(message + " at position " + position);
    }

    private void tokenize(String expression) {
        tokens.clear();
        int i = 0;
        int len = expression.length();

        while (i < len) {
            char ch = expression.charAt(i);

            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }

            if (ch == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", i));
                i++;
                continue;
            }
            if (ch == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", i));
                i++;
                continue;
            }
            if (ch == '&' && i + 1 < len && expression.charAt(i + 1) == '&') {
                tokens.add(new Token(TokenType.AND, "&&", i));
                i += 2;
                continue;
            }
            if (ch == '|' && i + 1 < len && expression.charAt(i + 1) == '|') {
                tokens.add(new Token(TokenType.OR, "||", i));
                i += 2;
                continue;
            }

            String two = i + 1 < len ? expression.substring(i, i + 2) : "";
            if ("==".equals(two) || "!=".equals(two) || ">=".equals(two) || "<=".equals(two)) {
                tokens.add(new Token(TokenType.OPERATOR, two, i));
                i += 2;
                continue;
            }
            if (ch == '>' || ch == '<') {
                tokens.add(new Token(TokenType.OPERATOR, Character.toString(ch), i));
                i++;
                continue;
            }

            if (ch == '"') {
                int start = i;
                i++;
                StringBuilder sb = new StringBuilder();
                boolean escaped = false;
                while (i < len) {
                    char c = expression.charAt(i);
                    if (escaped) {
                        sb.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break;
                    } else {
                        sb.append(c);
                    }
                    i++;
                }
                if (i >= len || expression.charAt(i) != '"') {
                    throw syntax("Unterminated string literal", start);
                }
                tokens.add(new Token(TokenType.STRING, sb.toString(), start));
                i++;
                continue;
            }

            if (Character.isDigit(ch) || (ch == '-' && i + 1 < len && Character.isDigit(expression.charAt(i + 1)))) {
                int start = i;
                i++;
                while (i < len && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, expression.substring(start, i), start));
                continue;
            }

            if (Character.isLetter(ch) || ch == '_') {
                int start = i;
                i++;
                while (i < len) {
                    char c = expression.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                        i++;
                    } else {
                        break;
                    }
                }
                String word = expression.substring(start, i);
                if ("true".equalsIgnoreCase(word) || "false".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.BOOLEAN, word.toLowerCase(), start));
                } else if ("null".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.NULL, "null", start));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, word, start));
                }
                continue;
            }

            throw syntax("Unexpected character: '" + ch + "'", i);
        }

        tokens.add(new Token(TokenType.EOF, "<eof>", expression.length()));
    }
}

