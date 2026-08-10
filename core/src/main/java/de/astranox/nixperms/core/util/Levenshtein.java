package de.astranox.nixperms.core.util;

/** Bounded optimal-string-alignment Damerau-Levenshtein distance. */
public final class Levenshtein {

    private Levenshtein() { }

    public static int distance(String first, String second) {
        if (first == null || second == null) return Integer.MAX_VALUE;
        return distance(first, second, Math.max(first.length(), second.length()));
    }

    public static int distance(String first, String second, int maxDistance) {
        if (first == null || second == null) return maxDistance + 1;
        if (first.equals(second)) return 0;
        if (maxDistance < 0) return maxDistance + 1;
        if (Math.abs(first.length() - second.length()) > maxDistance) return maxDistance + 1;
        if (first.isEmpty()) return second.length() <= maxDistance ? second.length() : maxDistance + 1;
        if (second.isEmpty()) return first.length() <= maxDistance ? first.length() : maxDistance + 1;

        String rows = first;
        String columns = second;
        if (rows.length() < columns.length()) {
            rows = second;
            columns = first;
        }

        int[] previousPrevious = new int[columns.length() + 1];
        int[] previous = new int[columns.length() + 1];
        int[] current = new int[columns.length() + 1];
        for (int column = 0; column <= columns.length(); column++) previous[column] = column;

        for (int row = 1; row <= rows.length(); row++) {
            current[0] = row;
            int rowMinimum = current[0];
            char rowCharacter = rows.charAt(row - 1);

            for (int column = 1; column <= columns.length(); column++) {
                char columnCharacter = columns.charAt(column - 1);
                int substitution = previous[column - 1] + (rowCharacter == columnCharacter ? 0 : 1);
                int insertion = current[column - 1] + 1;
                int deletion = previous[column] + 1;
                int value = Math.min(substitution, Math.min(insertion, deletion));

                if (row > 1 && column > 1 &&
                        rowCharacter == columns.charAt(column - 2) &&
                        rows.charAt(row - 2) == columnCharacter) {
                    value = Math.min(value, previousPrevious[column - 2] + 1);
                }

                current[column] = value;
                if (value < rowMinimum) rowMinimum = value;
            }

            if (rowMinimum > maxDistance) return maxDistance + 1;
            int[] swap = previousPrevious;
            previousPrevious = previous;
            previous = current;
            current = swap;
        }

        int result = previous[columns.length()];
        return result <= maxDistance ? result : maxDistance + 1;
    }
}
