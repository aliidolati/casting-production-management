package com.factory.productmanager.repository;

/** Parse reference numbers before querying, avoiding MySQL CAST/CONCAT collation conflicts. */
public record MeltCandidateSearch(String keyword, boolean numeric, Integer pieceNumber,
                                  String projectCode, Integer rowNumber, Integer referencePieceNumber) {
    public static MeltCandidateSearch parse(String value) {
        String text = value == null ? "" : value.strip();
        PieceSearchTerm number = PieceSearchTerm.parse(text);
        if (number.numeric()) return new MeltCandidateSearch(text, true, number.pieceNumber(), null, null, null);
        int last = text.lastIndexOf('-');
        int previous = last <= 0 ? -1 : text.lastIndexOf('-', last - 1);
        if (previous > 0 && last < text.length() - 1) {
            PieceSearchTerm row = PieceSearchTerm.parse(text.substring(previous + 1, last));
            PieceSearchTerm piece = PieceSearchTerm.parse(text.substring(last + 1));
            if (row.numeric() && row.pieceNumber() != null && piece.numeric() && piece.pieceNumber() != null) {
                return new MeltCandidateSearch(text, false, null, text.substring(0, previous).strip(), row.pieceNumber(), piece.pieceNumber());
            }
        }
        return new MeltCandidateSearch(text, false, null, null, null, null);
    }
}
