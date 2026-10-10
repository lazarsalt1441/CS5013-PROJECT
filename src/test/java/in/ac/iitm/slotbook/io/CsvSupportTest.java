package in.ac.iitm.slotbook.io;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvSupportTest {
    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        List<String> values = CsvSupport.parseLine("EE1000,\"Signals, Systems\",\"He said \"\"yes\"\"\"");
        assertEquals(List.of("EE1000", "Signals, Systems", "He said \"yes\""), values);
    }
}

