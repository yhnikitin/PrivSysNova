package pt.unl.fct.di.syspriv.dp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.storage.CsvImporter;
import pt.unl.fct.di.syspriv.storage.Hibernate;

class DifferentialPrivacyToolTest {

    @BeforeAll
    static void importTestData() {
        CsvImporter.run();
    }

    @Test
    void budgetPersistsAcrossToolsAndStopsQueriesWhenExhausted() {
        List<Long> ids = healthDataIds();
        String datasetId = uniqueDatasetId();
        DifferentialPrivacyTool first = new DifferentialPrivacyTool(1.0f, ids, datasetId);

        DPResult successfulQuery = first.sumGlucoseValues(0.4f);
        assertEquals(DPResult.ResultCode.SUCCESS, successfulQuery.getResultCode());
        assertEquals(0.6f, successfulQuery.getPrivacyBudget(), 0.0001f);

        DifferentialPrivacyTool reopened = new DifferentialPrivacyTool(1.0f, ids, datasetId);
        assertEquals(0.6f, reopened.getPrivacyBudget(), 0.0001f);
        assertEquals(DPResult.ResultCode.FAIL, reopened.countBelowGlucose(0.7f, 120).getResultCode());
        assertEquals(0.6f, reopened.getPrivacyBudget(), 0.0001f);

        assertEquals(DPResult.ResultCode.SUCCESS, reopened.countBelowGlucose(0.6f, 120).getResultCode());
        assertEquals(0.0f, reopened.getPrivacyBudget(), 0.0001f);
        assertEquals(DPResult.ResultCode.FAIL, reopened.averageGlucoseValues(0.1f).getResultCode());
    }

    @Test
    void splitChunksShareOneDatasetBudget() {
        List<Long> ids = healthDataIds();
        DifferentialPrivacyTool parent = new DifferentialPrivacyTool(1.0f, ids, uniqueDatasetId());
        DifferentialPrivacyTool[] chunks = parent.split(ids.size() / 2);

        assertEquals(DPResult.ResultCode.SUCCESS, chunks[0].countBelowGlucose(0.6f, 120).getResultCode());
        assertEquals(DPResult.ResultCode.FAIL, chunks[1].sumGlucoseValues(0.5f).getResultCode());
        assertEquals(0.4f, chunks[0].getPrivacyBudget(), 0.0001f);
        assertEquals(DPResult.ResultCode.FAIL, parent.countBelowGlucose(0.1f, 120).getResultCode());
    }

    @Test
    void groupedCountsReturnNoisyHistogramAndSpendOnce() {
        List<Long> ids = healthDataIds();
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0f, ids, uniqueDatasetId());

        DPResult result = tool.glucoseHistogram(0.25f);

        assertEquals(DPResult.ResultCode.SUCCESS, result.getResultCode());
        assertEquals(DPResult.ResultType.HISTOGRAM, result.getResultType());
        assertNotNull(result.getHistogramResultValue());
        assertFalse(result.getHistogramResultValue().isEmpty());
        assertEquals(0.75f, result.getPrivacyBudget(), 0.0001f);
    }

    @Test
    void averageReportsSensitivityAndUpdatedBudget() {
        List<Long> ids = healthDataIds();
        DifferentialPrivacyTool tool = new DifferentialPrivacyTool(1.0f, ids, uniqueDatasetId());

        DPResult result = tool.averageGlucoseValues(0.2f);

        assertEquals(DPResult.ResultCode.SUCCESS, result.getResultCode());
        assertEquals(DPResult.ResultType.AVERAGE, result.getResultType());
        assertEquals(400.0f, result.getQuerySensitivity());
        assertEquals(0.8f, result.getPrivacyBudget(), 0.0001f);
    }

    private static List<Long> healthDataIds() {
        return Hibernate.getInstance().sql("SELECT id FROM health_data ORDER BY id", Long.class);
    }

    private static String uniqueDatasetId() {
        return "dp-test-" + System.nanoTime();
    }
}