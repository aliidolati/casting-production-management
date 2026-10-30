package com.factory.productmanager.repository;

/** Numeric-only tracking searches identify a piece number; other terms remain keywords. */
public record PieceSearchTerm(String keyword, boolean numeric, Integer pieceNumber) {
    public static PieceSearchTerm parse(String value) {
        String keyword = value == null ? "" : value.strip();
        if (keyword.isEmpty()) {
            return new PieceSearchTerm(keyword, false, null);
        }
        StringBuilder digits = new StringBuilder();
        for (int offset = 0; offset < keyword.length();) {
            int codePoint = keyword.codePointAt(offset);
            int digit = Character.digit(codePoint, 10);
            if (digit < 0) {
                return new PieceSearchTerm(keyword, false, null);
            }
            digits.append(digit);
            offset += Character.charCount(codePoint);
        }
        try {
            return new PieceSearchTerm(keyword, true, new java.math.BigInteger(digits.toString()).intValueExact());
        } catch (ArithmeticException e) {
            // An out-of-range number must not fall back to partial name/project matching.
            return new PieceSearchTerm(keyword, true, null);
        }
    }
}
