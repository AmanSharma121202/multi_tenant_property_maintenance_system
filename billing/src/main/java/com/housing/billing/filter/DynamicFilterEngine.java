package com.housing.billing.filter;

import com.housing.billing.exception.FilterValueNotFoundException;
import com.housing.billing.exception.InvalidFilterSyntaxException;
import com.housing.billing.exception.UnknownFilterFieldException;
import com.housing.billing.exception.UnsupportedFilterOperatorException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Component
public class DynamicFilterEngine {

    private final FilterExpressionParser parser = new FilterExpressionParser();

    public <T> List<T> apply(List<T> source, String filter, Class<T> type, Set<String> allowedFields) {
        return apply(source, filter, type, allowedFields, Map.of());
    }

    public <T> List<T> apply(List<T> source,
                             String filter,
                             Class<T> type,
                             Set<String> allowedFields,
                             Map<String, String> valueNotFoundMessages) {
        if (filter == null || filter.isBlank()) {
            return source;
        }

        FilterExpressionParser.Node root = parser.parse(filter);
        Map<String, Field> fields = getAllFields(type);
        validateFilterValues(root, source, fields, allowedFields, valueNotFoundMessages, new HashSet<>());
        Predicate<T> predicate = buildPredicate(root, type, allowedFields);
        List<T> filtered = source.stream().filter(predicate).toList();
        throwIfComparatorResultIsEmpty(root, filtered, valueNotFoundMessages);
        return filtered;
    }

    private <T> void validateFilterValues(FilterExpressionParser.Node node,
                                          List<T> source,
                                          Map<String, Field> fields,
                                          Set<String> allowedFields,
                                          Map<String, String> valueNotFoundMessages,
                                          Set<String> validatedConditions) {
        if (node instanceof FilterExpressionParser.LogicalNode logicalNode) {
            validateFilterValues(logicalNode.left(), source, fields, allowedFields, valueNotFoundMessages, validatedConditions);
            validateFilterValues(logicalNode.right(), source, fields, allowedFields, valueNotFoundMessages, validatedConditions);
            return;
        }

        FilterExpressionParser.ConditionNode condition = (FilterExpressionParser.ConditionNode) node;
        if (!valueNotFoundMessages.containsKey(condition.field())) {
            return;
        }

        if (condition.operator() != ComparisonOperator.EQ) {
            return;
        }

        String conditionKey = condition.field() + "|" + condition.operator() + "|" + condition.literal().value();
        if (!validatedConditions.add(conditionKey)) {
            return;
        }

        if (!allowedFields.contains(condition.field())) {
            throw new UnknownFilterFieldException("Unknown filter field: '" + condition.field() + "'");
        }

        Field field = fields.get(condition.field());
        if (field == null) {
            throw new UnknownFilterFieldException("Field is not present on model: '" + condition.field() + "'");
        }

        Object expected = convertLiteral(condition.literal(), field, condition.field(), condition.operator());
        boolean valueExists = source.stream().anyMatch(item -> Objects.equals(readField(field, item), expected));
        if (valueExists) {
            return;
        }

        String template = valueNotFoundMessages.get(condition.field());
        String message = template.contains("%s")
                ? String.format(template, expected)
                : template;
        throw new FilterValueNotFoundException(message);
    }

    private <T> void throwIfComparatorResultIsEmpty(FilterExpressionParser.Node node,
                                                    List<T> filtered,
                                                    Map<String, String> valueNotFoundMessages) {
        if (!filtered.isEmpty()) {
            return;
        }

        FilterExpressionParser.ConditionNode condition = findFirstComparatorCondition(node);
        if (condition != null) {
            String input = literalToMessageValue(condition.literal());
            String message = switch (condition.operator()) {
                case LTE -> "Not found value less than or equal to " + input;
                case GTE -> "Not found value greater than or equal to " + input;
                case LT -> "Not found value less than " + input;
                case GT -> "Not found value greater than " + input;
                case NE -> "Not found value not equal to " + input;
                default -> null;
            };

            if (message != null) {
                throw new FilterValueNotFoundException(message);
            }
        }

        List<FilterExpressionParser.ConditionNode> eqConditions = collectConfiguredEqConditions(node, valueNotFoundMessages);
        if (eqConditions.size() > 1) {
            throw new FilterValueNotFoundException(buildCombinedEqNoMatchMessage(eqConditions));
        }

        FilterExpressionParser.ConditionNode eqCondition = eqConditions.isEmpty() ? null : eqConditions.getFirst();
        if (eqCondition != null) {
            String template = valueNotFoundMessages.get(eqCondition.field());
            String value = literalToMessageValue(eqCondition.literal());
            String eqMessage = template.contains("%s") ? String.format(template, value) : template;
            throw new FilterValueNotFoundException(eqMessage);
        }
    }

    private List<FilterExpressionParser.ConditionNode> collectConfiguredEqConditions(FilterExpressionParser.Node node,
                                                                                     Map<String, String> valueNotFoundMessages) {
        List<FilterExpressionParser.ConditionNode> collected = new ArrayList<>();
        collectConfiguredEqConditions(node, valueNotFoundMessages, collected, new LinkedHashSet<>());
        return collected;
    }

    private void collectConfiguredEqConditions(FilterExpressionParser.Node node,
                                               Map<String, String> valueNotFoundMessages,
                                               List<FilterExpressionParser.ConditionNode> collected,
                                               Set<String> seen) {
        if (node instanceof FilterExpressionParser.LogicalNode logicalNode) {
            collectConfiguredEqConditions(logicalNode.left(), valueNotFoundMessages, collected, seen);
            collectConfiguredEqConditions(logicalNode.right(), valueNotFoundMessages, collected, seen);
            return;
        }

        FilterExpressionParser.ConditionNode condition = (FilterExpressionParser.ConditionNode) node;
        if (condition.operator() != ComparisonOperator.EQ || !valueNotFoundMessages.containsKey(condition.field())) {
            return;
        }

        String key = condition.field() + "|" + literalToMessageValue(condition.literal());
        if (!seen.add(key)) {
            return;
        }
        collected.add(condition);
    }

    private String buildCombinedEqNoMatchMessage(List<FilterExpressionParser.ConditionNode> conditions) {
        String details = conditions.stream()
                .map(c -> c.field() + "='" + literalToMessageValue(c.literal()) + "'")
                .reduce((left, right) -> left + " and " + right)
                .orElse("provided filters");
        return "No records match all filters: " + details;
    }

    private FilterExpressionParser.ConditionNode findFirstComparatorCondition(FilterExpressionParser.Node node) {
        if (node instanceof FilterExpressionParser.LogicalNode logicalNode) {
            FilterExpressionParser.ConditionNode left = findFirstComparatorCondition(logicalNode.left());
            if (left != null) {
                return left;
            }
            return findFirstComparatorCondition(logicalNode.right());
        }

        FilterExpressionParser.ConditionNode condition = (FilterExpressionParser.ConditionNode) node;
        return condition.operator() == ComparisonOperator.EQ ? null : condition;
    }

    private String literalToMessageValue(FilterExpressionParser.Literal literal) {
        Object value = literal.value();
        return value == null ? "null" : String.valueOf(value);
    }

    private <T> Predicate<T> buildPredicate(FilterExpressionParser.Node node, Class<T> type, Set<String> allowedFields) {
        if (node instanceof FilterExpressionParser.LogicalNode logicalNode) {
            Predicate<T> left = buildPredicate(logicalNode.left(), type, allowedFields);
            Predicate<T> right = buildPredicate(logicalNode.right(), type, allowedFields);
            return logicalNode.operator() == FilterExpressionParser.LogicalOperator.AND
                    ? left.and(right)
                    : left.or(right);
        }

        FilterExpressionParser.ConditionNode condition = (FilterExpressionParser.ConditionNode) node;
        Map<String, Field> fields = getAllFields(type);

        if (!allowedFields.contains(condition.field())) {
            throw new UnknownFilterFieldException("Unknown filter field: '" + condition.field() + "'");
        }

        Field field = fields.get(condition.field());
        if (field == null) {
            throw new UnknownFilterFieldException("Field is not present on model: '" + condition.field() + "'");
        }

        return item -> evaluateCondition(field, condition, item);
    }

    private boolean evaluateCondition(Field field, FilterExpressionParser.ConditionNode condition, Object item) {
        validateOperatorForField(field, condition.operator(), condition.field());
        Object actual = readField(field, item);
        Object expected = convertLiteral(condition.literal(), field, condition.field(), condition.operator());

        return switch (condition.operator()) {
            case EQ -> Objects.equals(actual, expected);
            case NE -> !Objects.equals(actual, expected);
            case GT, GTE, LT, LTE -> compare(actual, expected, condition.operator(), condition.field());
        };
    }

    private void validateOperatorForField(Field field, ComparisonOperator operator, String fieldName) {
        Class<?> type = wrap(field.getType());
        if (operator == ComparisonOperator.EQ || operator == ComparisonOperator.NE) {
            return;
        }

        boolean comparableRangeSupported = Number.class.isAssignableFrom(type)
                || type == BigDecimal.class
                || type == Instant.class
                || type == LocalDate.class
                || type == LocalDateTime.class;

        if (!comparableRangeSupported) {
            throw new UnsupportedFilterOperatorException(
                    "Operator '" + operator.getSymbol() + "' is not supported for field '" + fieldName + "'"
            );
        }
    }

    private Object readField(Field field, Object item) {
        try {
            field.setAccessible(true);
            return field.get(item);
        } catch (IllegalAccessException ex) {
            throw new UnsupportedFilterOperatorException("Cannot access field: '" + field.getName() + "'");
        }
    }

    private Object convertLiteral(FilterExpressionParser.Literal literal,
                                  Field field,
                                  String fieldName,
                                  ComparisonOperator operator) {
        Class<?> type = wrap(field.getType());
        Object value = literal.value();
        FilterExpressionParser.LiteralType literalType = literal.type();

        if (value == null) {
            if (operator != ComparisonOperator.EQ && operator != ComparisonOperator.NE) {
                throw new UnsupportedFilterOperatorException("Operator '" + operator.getSymbol()
                        + "' is not supported with null for field '" + fieldName + "'");
            }
            return null;
        }

        try {
            // ===== STRING FIELDS =====
            if (type == String.class) {
                // Only STRING literal type is allowed (quoted strings)
                // IDENTIFIER (unquoted) must be rejected
                if (literalType != FilterExpressionParser.LiteralType.STRING) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                return String.valueOf(value);
            }

            // ===== BOOLEAN FIELDS =====
            if (type == Boolean.class) {
                if (literalType == FilterExpressionParser.LiteralType.BOOLEAN) {
                    return value;
                }
                // Reject all non-boolean types
                throw new InvalidFilterSyntaxException("Unexpected token");
            }

            // ===== INTEGER FIELDS =====
            if (type == Integer.class) {
                if (literalType != FilterExpressionParser.LiteralType.NUMBER) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return Integer.parseInt(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            // ===== LONG FIELDS =====
            if (type == Long.class) {
                if (literalType != FilterExpressionParser.LiteralType.NUMBER) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return Long.parseLong(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            // ===== DOUBLE FIELDS =====
            if (type == Double.class) {
                if (literalType != FilterExpressionParser.LiteralType.NUMBER) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            // ===== FLOAT FIELDS =====
            if (type == Float.class) {
                if (literalType != FilterExpressionParser.LiteralType.NUMBER) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return Float.parseFloat(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            // ===== BIGDECIMAL FIELDS =====
            if (type == BigDecimal.class) {
                if (literalType != FilterExpressionParser.LiteralType.NUMBER) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return new BigDecimal(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            // ===== INSTANT FIELDS =====
            if (type == Instant.class) {
                if (literalType != FilterExpressionParser.LiteralType.STRING) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return Instant.parse(String.valueOf(value));
                } catch (Exception ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            // ===== LOCALDATE FIELDS =====
            if (type == LocalDate.class) {
                if (literalType != FilterExpressionParser.LiteralType.STRING) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return LocalDate.parse(String.valueOf(value));
                } catch (Exception ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            // ===== LOCALDATETIME FIELDS =====
            if (type == LocalDateTime.class) {
                if (literalType != FilterExpressionParser.LiteralType.STRING) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
                try {
                    return LocalDateTime.parse(String.valueOf(value));
                } catch (Exception ex) {
                    throw new InvalidFilterSyntaxException("Unexpected token");
                }
            }

            throw new UnsupportedFilterOperatorException(
                    "Field type '" + type.getSimpleName() + "' is not supported for field '" + fieldName + "'"
            );
        } catch (RuntimeException ex) {
            if (ex instanceof UnsupportedFilterOperatorException || ex instanceof InvalidFilterSyntaxException) {
                throw ex;
            }
            throw new InvalidFilterSyntaxException("Unexpected token");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean compare(Object actual, Object expected, ComparisonOperator operator, String fieldName) {
        if (actual == null || expected == null) {
            return false;
        }
        if (!(actual instanceof Comparable<?> comparable)) {
            throw new UnsupportedFilterOperatorException(
                    "Operator '" + operator.getSymbol() + "' is not supported for field '" + fieldName + "'"
            );
        }

        int result;
        try {
            result = ((Comparable<Object>) comparable).compareTo(expected);
        } catch (ClassCastException ex) {
            throw new UnsupportedFilterOperatorException(
                    "Operator '" + operator.getSymbol() + "' is not supported for field '" + fieldName + "'"
            );
        }

        return switch (operator) {
            case GT -> result > 0;
            case GTE -> result >= 0;
            case LT -> result < 0;
            case LTE -> result <= 0;
            default -> throw new UnsupportedFilterOperatorException("Unsupported comparison operator");
        };
    }

    private Map<String, Field> getAllFields(Class<?> type) {
        Map<String, Field> fields = new HashMap<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.putIfAbsent(field.getName(), field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        return type;
    }
}


