package com.factory.productmanager.service;

import com.factory.productmanager.exception.DuplicateResourceException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.PageResponse;
import com.factory.productmanager.model.dto.SpectrometryDto.*;
import com.factory.productmanager.model.entity.Piece;
import com.factory.productmanager.model.entity.SpectrometryBatchReceipt;
import com.factory.productmanager.model.entity.SpectrometryTest;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.PieceRepository;
import com.factory.productmanager.repository.SpectrometryBatchReceiptRepository;
import com.factory.productmanager.repository.SpectrometryRepository;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SpectrometryService {
    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);
    private final SpectrometryRepository tests;
    private final SpectrometryBatchReceiptRepository receipts;
    private final PieceRepository pieces;
    private final UserRepository users;

    @Transactional
    public BatchResult saveBatch(String username, Batch batch) {
        List<Input> rows = new ArrayList<>(batch.rows().size());
        for (int i = 0; i < batch.rows().size(); i++) {
            rows.add(validate(batch.rows().get(i), "ردیف " + (i + 1) + " · "));
        }
        String fingerprint = fingerprint(rows);
        // Serialize first submission and retries for this account before reading the receipt.
        User actor = users.lockForCredentialsByUsername(username)
                .filter(user -> !user.isDisabled())
                .orElseThrow(() -> new AccessDeniedException("حساب کاربری فعال نیست"));
        var previous = receipts.findByActorIdAndRequestId(actor.getId(), batch.requestId());
        if (previous.isPresent()) {
            if (!previous.get().getFingerprint().equals(fingerprint)) {
                throw new DuplicateResourceException("این شناسهٔ ثبت قبلاً برای محتوای دیگری استفاده شده است");
            }
            return new BatchResult(previous.get().getRowCount(), true);
        }

        List<UUID> pieceIds = rows.stream().map(Input::pieceId).distinct().toList();
        Map<UUID, Piece> byId = pieces.findAllById(pieceIds).stream()
                .collect(Collectors.toMap(Piece::getId, Function.identity()));
        List<SpectrometryTest> newTests = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Input row = rows.get(i);
            Piece piece = byId.get(row.pieceId());
            if (piece == null) {
                throw new IllegalArgumentException("ردیف " + (i + 1) + " · قطعه پیدا نشد");
            }
            SpectrometryTest test = new SpectrometryTest();
            apply(test, piece, row);
            newTests.add(test);
        }
        tests.saveAll(newTests);
        receipts.save(new SpectrometryBatchReceipt(null, actor.getId(), batch.requestId(), fingerprint, rows.size()));
        return new BatchResult(rows.size(), false);
    }

    @Transactional
    public void update(UUID id, Update update) {
        SpectrometryTest test = findVersion(id, update.version());
        Input input = validate(update.result(), "");
        Piece piece = test.getPiece();
        if (!piece.getId().equals(input.pieceId())) {
            piece = pieces.findById(input.pieceId())
                    .orElseThrow(() -> new IllegalArgumentException("قطعه پیدا نشد"));
        }
        apply(test, piece, input);
    }

    @Transactional
    public void delete(UUID id, long version) {
        tests.delete(findVersion(id, version));
    }

    private SpectrometryTest findVersion(UUID id, long version) {
        SpectrometryTest test = tests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("آزمون کوانتومتری پیدا نشد"));
        if (test.getVersion() != version) {
            throw new DuplicateResourceException("آزمون در این فاصله تغییر کرده است؛ سوابق را دوباره بارگیری کنید");
        }
        return test;
    }

    public List<PieceOption> pieceOptions(UUID definitionId) {
        return pieces.findPieceNumberOptions(definitionId);
    }

    public PageResponse<Row> history(Filters filters, int page, int size, boolean ascending) {
        Search query = search(filters);
        var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), sort(ascending));
        var result = tests.history(query.project(), query.name(), query.number(), query.from(), query.to(), pageable);
        return PageResponse.of(result, result.getContent());
    }

    public List<Point> points(Filters filters) {
        Search query = search(filters);
        return tests.points(query.project(), query.name(), query.number(), query.from(), query.to());
    }

    public void export(OutputStream output, Filters filters, boolean ascending) throws IOException {
        Search query = search(filters);
        var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        try (Stream<Row> rows = tests.export(query.project(), query.name(), query.number(),
                query.from(), query.to(), sort(ascending))) {
            writer.write("\uFEFFکد پروژه,نام قطعه,شماره قطعه,تاریخ آزمون,Al,Si,Mg,Cu,Fe,Mn,Zn,Ti\r\n");
            var iterator = rows.iterator();
            while (iterator.hasNext()) {
                writeCsvRow(writer, iterator.next());
            }
            writer.flush();
        }
    }

    private static void writeCsvRow(Writer writer, Row row) throws IOException {
        writer.write(String.join(",", csvText(row.projectCode()), csvText(row.pieceName()),
                String.valueOf(row.pieceNumber()), row.testDate().toString(), row.al(), row.si(), row.mg(),
                row.cu(), row.fe(), row.mn(), row.zn(), row.ti()));
        writer.write("\r\n");
    }

    private static String csvText(String value) {
        String leading = value.stripLeading();
        if ((!leading.isEmpty() && "=+-@＝＋－＠".indexOf(leading.charAt(0)) >= 0)
                || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static Sort sort(boolean ascending) {
        return Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, "testDate", "id");
    }

    private record Search(String project, String name, Integer number, LocalDate from, LocalDate to) {}

    public void validateFilters(Filters filters) {
        search(filters);
    }

    private static Search search(Filters filters) {
        if (filters.dateFrom() != null && filters.dateTo() != null && filters.dateFrom().isAfter(filters.dateTo())) {
            throw new IllegalArgumentException("ابتدای بازهٔ تاریخ باید قبل از پایان بازه یا برابر با آن باشد");
        }
        Integer number = null;
        if (filters.pieceNumber() != null && !filters.pieceNumber().isBlank()) {
            try {
                number = Integer.valueOf(filters.pieceNumber().strip());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("شماره قطعه باید عدد صحیح باشد");
            }
        }
        return new Search(pattern(filters.projectCode()), pattern(filters.pieceName()), number,
                filters.dateFrom(), filters.dateTo());
    }

    private static String pattern(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static Input validate(Input row, String prefix) {
        if (row.pieceId() == null) {
            throw new IllegalArgumentException(prefix + "شماره قطعه را انتخاب کنید");
        }
        if (row.testDate() == null || row.testDate().getYear() < 1000 || row.testDate().getYear() > 3798) {
            throw new IllegalArgumentException(prefix + "تاریخ آزمون معتبر نیست");
        }
        return new Input(row.pieceId(), row.testDate(),
                percent(row.al(), prefix, "Al"), percent(row.si(), prefix, "Si"), percent(row.mg(), prefix, "Mg"),
                percent(row.cu(), prefix, "Cu"), percent(row.fe(), prefix, "Fe"), percent(row.mn(), prefix, "Mn"),
                percent(row.zn(), prefix, "Zn"), percent(row.ti(), prefix, "Ti"));
    }

    private static BigDecimal percent(BigDecimal value, String prefix, String element) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_PERCENT) > 0
                || value.stripTrailingZeros().scale() > SpectrometryTest.DECIMAL_SCALE) {
            throw new IllegalArgumentException(prefix + element + " باید عددی بین ۰ و ۱۰۰ با حداکثر ۱۰ رقم اعشار باشد");
        }
        return value.setScale(SpectrometryTest.DECIMAL_SCALE);
    }

    private static String fingerprint(List<Input> rows) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rows.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static void apply(SpectrometryTest test, Piece piece, Input input) {
        test.setPiece(piece);
        test.setTestDate(input.testDate());
        test.setAl(input.al());
        test.setSi(input.si());
        test.setMg(input.mg());
        test.setCu(input.cu());
        test.setFe(input.fe());
        test.setMn(input.mn());
        test.setZn(input.zn());
        test.setTi(input.ti());
    }
}
