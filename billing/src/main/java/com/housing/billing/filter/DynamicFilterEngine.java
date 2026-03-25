package com.housing.billing.filter;

import com.housing.billing.exception.UnknownFilterFieldException;
import com.housing.billing.exception.UnsupportedFilterOperatorException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@Component
public class DynamicFilterEngine {

    private final FilterExpressionParser parser = new FilterExpressionParser();

    public <T> List<T> apply(List<T> source, String filter, Class<T> type, Set<String> allowedFields) {
        if (filter == null || filter.isBlank()) {
            return source;
        }
        FilterExpressionParser.Node root = parser.parse(filter);
        Predicate<T> predicate = buildPredicate(root, type, allowedFields);
        return source.stream().filter(predicate).toList();
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

        if (value == null) {
            if (operator != ComparisonOperator.EQ && operator != ComparisonOperator.NE) {
                throw new UnsupportedFilterOperatorException("Operator '" + operator.getSymbol()
                        + "' is not supported with null for field '" + fieldName + "'");
            }
            return null;
        }

        try {
            if (type == String.class) {
                return String.valueOf(value);
            }
            if (type == Boolean.class) {
                if (literal.type() == FilterExpressionParser.LiteralType.BOOLEAN) {
                    return value;
                }
                return Boolean.parseBoolean(String.valueOf(value));
            }
            if (type == Integer.class) {
                return Integer.parseInt(String.valueOf(value));
            }
            if (type == Long.class) {
                return Long.parseLong(String.valueOf(value));
            }
            if (type == Double.class) {
                return Double.parseDouble(String.valueOf(value));
            }
            if (type == Float.class) {
                return Float.parseFloat(String.valueOf(value));
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(String.valueOf(value));
            }
            if (type == Instant.class) {
                return Instant.parse(String.valueOf(value));
            }
            if (type == LocalDate.class) {
                return LocalDate.parse(String.valueOf(value));
            }
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(String.valueOf(value));
            }
            throw new UnsupportedFilterOperatorException(
                    "Field type '" + type.getSimpleName() + "' is not supported for field '" + fieldName + "'"
            );
        } catch (RuntimeException ex) {
            throw new UnsupportedFilterOperatorException(
                    "Invalid value '" + value + "' for field '" + fieldName + "' of type '" + type.getSimpleName() + "'"
            );
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


