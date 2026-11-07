package in.ac.iitm.slotbook.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvSupportTest {
    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        List<String> values = CsvSupport.parseLine("EE1000,\"Signals, Systems\",\"He said \"\"yes\"\"\"");
        assertEquals(List.of("EE1000", "Signals, Systems", "He said \"yes\""), values);
    }

    @Test
    void aRowWithTheWrongNumberOfValuesNamesItsLine(@TempDir Path dir) throws Exception {
        // PIT NO_COVERAGE: the error path, including the 1-based line number in the message.
        Path file = Files.writeString(dir.resolve("x.csv"), "a,b\n1,2\n\n3,4,5\n");
        IOException error = assertThrows(IOException.class, () -> CsvSupport.read(file));
        assertTrue(error.getMessage().contains("line 4 has 3 values; expected 2"), error.getMessage());
    }
}
