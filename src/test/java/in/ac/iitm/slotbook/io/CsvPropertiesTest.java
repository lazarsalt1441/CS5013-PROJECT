package in.ac.iitm.slotbook.io;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property: any row of single-line fields, written the way RFC 4180 says, parses back to the same
 * fields. Quotes, commas and spaces are over-represented on purpose. (Fields never contain line
 * breaks: the reader is line-based, and the portal strips control characters before export.)
 */
class CsvPropertiesTest {

    private static String rfc4180(String field) {
        boolean quote = field.contains(",") || field.contains("\"") || !field.equals(field.strip());
        return quote ? "\"" + field.replace("\"", "\"\"") + "\"" : field;
    }

    @Provide
    Arbitrary<List<String>> rows() {
        Arbitrary<String> field = Arbitraries.strings()
                .withChars(',', '"', ' ', 'a', 'Z', '9', '-', '|', '\'', 'é', '数')
                .ofMinLength(0).ofMaxLength(12);
        return field.list().ofMinSize(1).ofMaxSize(8);
    }

    @Property(tries = 3000)
    void writtenRowsParseBackToTheSameFields(@ForAll("rows") List<String> fields) {
        String line = fields.stream().map(CsvPropertiesTest::rfc4180).collect(Collectors.joining(","));
        assertEquals(fields, CsvSupport.parseLine(line), line);
    }
}
