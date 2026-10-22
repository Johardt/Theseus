package me.johardt.theseus.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Detects duplicate object keys before Gson can silently replace the first value. */
public final class JsonDuplicateKeyDetector {
    /** Raw JSON container depth is separate from the quest task nesting limit. */
    public static final int MAX_RAW_NESTING_DEPTH = 80;
    public static final int MAX_RETAINED_DUPLICATE_PATHS = 32;
    public static final int MAX_DUPLICATE_PATH_LENGTH = 512;

    private static final String TRUNCATED_PATH_MARKER = "…[path truncated]";
    private static final String OMITTED_DUPLICATES_PATH = "$ [additional duplicate paths omitted]";

    private JsonDuplicateKeyDetector() {}

    public static List<String> findDuplicates(String json) {
        Parser parser = new Parser(json);
        parser.value(0);
        parser.space();
        if (!parser.end()) {
            throw new IllegalArgumentException("Unexpected content at character " + parser.index);
        }
        return List.copyOf(parser.duplicates);
    }

    static final class NestingLimitException extends IllegalArgumentException {
        NestingLimitException() {
            super("JSON nesting exceeds the maximum raw depth of " + MAX_RAW_NESTING_DEPTH);
        }
    }

    private static final class Parser {
        private final String input;
        private final StringBuilder path = new StringBuilder("$");
        private final List<String> duplicates = new ArrayList<>();
        private int index;
        private boolean duplicatePathsTruncated;

        private Parser(String input) {
            this.input = input;
        }

        private boolean end() {
            return index == input.length();
        }

        private void space() {
            while (!end() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private void value(int parentDepth) {
            space();
            if (end()) {
                throw malformed();
            }
            switch (input.charAt(index)) {
                case '{' -> object(parentDepth);
                case '[' -> array(parentDepth);
                case '"' -> string();
                default -> primitive();
            }
        }

        private void object(int parentDepth) {
            int childDepth = enterContainer(parentDepth);
            index++;
            space();
            Set<String> keys = new HashSet<>();
            if (consume('}')) {
                return;
            }
            while (true) {
                space();
                if (end() || input.charAt(index) != '"') {
                    throw malformed();
                }
                String key = string();
                if (!keys.add(key)) {
                    recordDuplicatePath(key);
                }
                space();
                expect(':');
                int previousPathLength = path.length();
                path.append('.').append(key);
                value(childDepth);
                path.setLength(previousPathLength);
                space();
                if (consume('}')) {
                    return;
                }
                expect(',');
            }
        }

        private void array(int parentDepth) {
            int childDepth = enterContainer(parentDepth);
            index++;
            space();
            int element = 0;
            if (consume(']')) {
                return;
            }
            while (true) {
                int previousPathLength = path.length();
                path.append('[').append(element++).append(']');
                value(childDepth);
                path.setLength(previousPathLength);
                space();
                if (consume(']')) {
                    return;
                }
                expect(',');
            }
        }

        private int enterContainer(int parentDepth) {
            if (parentDepth >= MAX_RAW_NESTING_DEPTH) {
                throw new NestingLimitException();
            }
            return parentDepth + 1;
        }

        private void recordDuplicatePath(String key) {
            if (duplicates.size() < MAX_RETAINED_DUPLICATE_PATHS) {
                duplicates.add(boundedPath(key));
            } else if (!duplicatePathsTruncated) {
                duplicates.set(
                    MAX_RETAINED_DUPLICATE_PATHS - 1,
                    OMITTED_DUPLICATES_PATH
                );
                duplicatePathsTruncated = true;
            }
        }

        private String boundedPath(String key) {
            int keySeparatorLength = 1;
            int fullLength = path.length() + keySeparatorLength + key.length();
            if (fullLength <= MAX_DUPLICATE_PATH_LENGTH) {
                return path + "." + key;
            }

            StringBuilder bounded = new StringBuilder(MAX_DUPLICATE_PATH_LENGTH);
            int contentLimit = MAX_DUPLICATE_PATH_LENGTH - TRUNCATED_PATH_MARKER.length();
            appendPrefix(bounded, path, contentLimit);
            if (bounded.length() < contentLimit) {
                bounded.append('.');
                appendPrefix(bounded, key, contentLimit);
            }
            bounded.append(TRUNCATED_PATH_MARKER);
            return bounded.toString();
        }

        private void appendPrefix(StringBuilder target, CharSequence value, int maximumLength) {
            int remaining = maximumLength - target.length();
            if (remaining > 0) {
                target.append(value, 0, Math.min(value.length(), remaining));
            }
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (!end()) {
                char character = input.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character < 0x20) {
                    throw malformed();
                }
                if (character == '\\') {
                    if (end()) {
                        throw malformed();
                    }
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> {
                            int decoded = 0;
                            for (int digit = 0; digit < 4; digit++) {
                                decoded = (decoded << 4) | hexDigit();
                            }
                            value.append((char) decoded);
                        }
                        default -> throw malformed();
                    }
                } else {
                    value.append(character);
                }
            }
            throw malformed();
        }

        private int hexDigit() {
            if (end()) {
                throw malformed();
            }
            char digit = input.charAt(index++);
            if (digit >= '0' && digit <= '9') return digit - '0';
            if (digit >= 'a' && digit <= 'f') return digit - 'a' + 10;
            if (digit >= 'A' && digit <= 'F') return digit - 'A' + 10;
            throw malformed();
        }

        private void primitive() {
            int start = index;
            while (!end() && " \t\r\n,]}".indexOf(input.charAt(index)) < 0) {
                index++;
            }
            if (start == index) {
                throw malformed();
            }
        }

        private boolean consume(char expected) {
            if (!end() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            space();
            if (!consume(expected)) {
                throw malformed();
            }
        }

        private IllegalArgumentException malformed() {
            return new IllegalArgumentException("Malformed JSON near character " + index);
        }
    }
}
