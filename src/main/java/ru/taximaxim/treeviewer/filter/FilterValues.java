package ru.taximaxim.treeviewer.filter;


/**
 * Enum for types of column filters
 */
public enum FilterValues {

    NONE(""),
    EQUALS("="),
    NOT_EQUALS("!="),
    CONTAINS("~"),
    GREATER(">"),
    GREATER_OR_EQUAL(">="),
    LESS("<"),
    LESS_OR_EQUAL("<=");

    private final String conditionText;

    FilterValues(String conditionText) {
        this.conditionText = conditionText;
    }

    @Override
    public String toString() {
        return conditionText;
    }

    public String getConditionText() {
        return conditionText;
    }

    public static FilterValues find(String text) {
        FilterValues[] list = FilterValues.values();
        for (FilterValues filterValues : list) {
            if (filterValues.getConditionText().equals(text)) {
                return filterValues;
            }
        }
        return FilterValues.NONE;
    }
}