package util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CsvWriterTest {

    @Test
    public void quotesCellsWithCommasAndQuotes() {
        Assert.assertEquals(CsvWriter.cell("Stove, \"left\""), "\"Stove, \"\"left\"\"\"");
        Assert.assertEquals(CsvWriter.row(1, "Bếp chính", null), "1,Bếp chính,\r\n");
    }

    @Test
    public void neutralisesSpreadsheetFormulasButNotNegativeNumbers() {
        Assert.assertEquals(CsvWriter.cell("=HYPERLINK(\"x\")"), "\"'=HYPERLINK(\"\"x\"\")\"");
        Assert.assertEquals(CsvWriter.cell(-5.0), "-5.0");
    }
}
