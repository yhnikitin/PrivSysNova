package pt.unl.fct.di.syspriv.dp;

import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.util.ConfigLoader;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class DifferentialPrivacyTool {

    private final int lower = ConfigLoader.getIntProperty("dp.glucose.lower", 0);
    private final int upper = ConfigLoader.getIntProperty("dp.glucose.upper", 400);
    private static final Random RANDOM = new Random();


    private final Hibernate hibernate;

    private final List<Long> recordIds;

    private final String datasetKey;

    private boolean active = true;

    // Auxiliary attribute to ensure queries only use the records in this dataset chunk.
    private final String idsStr;

    public DifferentialPrivacyTool(float privacyBudget) {
        this(privacyBudget, Hibernate.getInstance().sql("SELECT id FROM health_data", Long.class), "health_data");
    }

    public DifferentialPrivacyTool(float privacyBudget, List<Long> recordIds) {
        this(privacyBudget, recordIds, "health_data");
    }

    public DifferentialPrivacyTool(float privacyBudget, List<Long> queryRecordIds, String datasetId) {
        this(privacyBudget, queryRecordIds, datasetId, true);
    }


//i may be overengeneering this, but i think storing the budget in the database is good and init budget var is needed for split if we ever to use them 
    private DifferentialPrivacyTool(float initialBudget, List<Long> queryRecordIds, String datasetId,
            boolean initializeBudget) {
        this.hibernate = Hibernate.getInstance();
        this.recordIds = List.copyOf(queryRecordIds);
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("Dataset ID must not be blank");
        }
        this.datasetKey = datasetId;
        this.idsStr = concatenateRecordIds(queryRecordIds);
        if (initializeBudget) {
            ensureBudgetLedger(initialBudget);
        }
    }

    private static String concatenateRecordIds(List<Long> recordIds) {
        var idsStr = recordIds.stream().map("'%d'"::formatted).toList();
        return String.join(",", idsStr);
    }

    public DifferentialPrivacyTool[] split(int cutoffIdx) {
        if (!active || cutoffIdx < 0 || cutoffIdx > recordIds.size()) {
            throw new IllegalArgumentException("Invalid dataset split");
        }
        var lftRecords = List.copyOf(recordIds.subList(0, cutoffIdx));
        var rgtRecords = List.copyOf(recordIds.subList(cutoffIdx, recordIds.size()));
        var lft = new DifferentialPrivacyTool(0, lftRecords, datasetKey, false);
        var rgt = new DifferentialPrivacyTool(0, rgtRecords, datasetKey, false);
        this.active = false;
        return new DifferentialPrivacyTool[]{lft, rgt};
    }

    public DPResult countBelowGlucose(float privacyCost, int upperGlucose) {
        if (!spendBudget(privacyCost)) {
            return failResult();
        }

        var query = """
                SELECT COUNT(*)
                FROM health_data
                WHERE glucoseLevel<%d
                AND id IN (%s)
                """.formatted(upperGlucose, idsStr);
        var count = hibernate.sql(query, Integer.class).get(0);

        double sensitivity = 1.0;
        double noise = count + laplaceNoise(sensitivity / privacyCost);

        var res = new DPResult();
        res.setResultType(DPResult.ResultType.COUNT);

        res.setResultValue((float) noise);
        res.setQuerySensitivity((float) sensitivity);

        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(getPrivacyBudget());
        return res;
    }

    public DPResult sumGlucoseValues(float privacyCost) {
        if (!spendBudget(privacyCost)) {
            return failResult();
        }

        var query = """
                SELECT SUM(MIN(MAX(glucoseLevel, %d), %d))
                FROM health_data
                WHERE id IN (%s)
                """.formatted(lower, upper, idsStr);
        Float sum = hibernate.sql(query, Float.class).get(0);
        if (sum == null) {
            sum = 0f;
        }

        double sensitivity = Math.max(Math.abs(lower), Math.abs(upper));
        double noise = sum + laplaceNoise(sensitivity / privacyCost);

        var res = new DPResult();
        res.setResultType(DPResult.ResultType.SUM);

        res.setResultValue((float) noise);
        res.setQuerySensitivity((float) sensitivity);
        res.setClippingLower((float) lower);
        res.setClippingUpper((float) upper);

        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(getPrivacyBudget());
        return res;
    }

    public DPResult averageGlucoseValues(float privacyCost) {
        if (!spendBudget(privacyCost)) {
            return failResult();
        }
        /*var query = """
                SELECT AVG(glucose)
                FROM health_data
                WHERE id IN (%s)
                """.formatted(idsStr);
        var avg = hibernate.sql(query, Float.class).get(0);
                */

        var sumQuery = """
            SELECT SUM(MIN(MAX(glucoseLevel, %d), %d))
            FROM health_data
            WHERE id IN (%s)
            """.formatted(lower, upper, idsStr);
        var countQuery = """
            SELECT COUNT(glucoseLevel)
            FROM health_data
            WHERE id IN (%s)
            """.formatted(idsStr);
        Float sum = hibernate.sql(sumQuery, Float.class).get(0);
        Integer count = hibernate.sql(countQuery, Integer.class).get(0);
        if (sum == null || count == null) {
            sum = 0f;
            count = 0;
        }

        double halfCost = privacyCost / 2.0;
        double sumSensitivity = Math.max(Math.abs(lower), Math.abs(upper));
        double noisySum = sum + laplaceNoise(sumSensitivity / halfCost);
        double noisyCount = Math.max(1.0, count + laplaceNoise(1.0 / halfCost));

        double avg = Math.min(Math.max(noisySum / noisyCount, lower), upper);
        var res = new DPResult();
        res.setResultType(DPResult.ResultType.AVERAGE);
        res.setResultValue((float) avg);
        res.setQuerySensitivity((float) (upper - lower));

        res.setClippingLower((float) lower);
        res.setClippingUpper((float) upper);

        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(getPrivacyBudget());
        return res;
    }


    //it is said to provide groupcounts+histograms queries but im generally not sure what do they mean by queries
    public DPResult glucoseHistogram(float privacyCost) {
        if (!spendBudget(privacyCost)) {
            return failResult();
        }

        var query = """
                SELECT MIN(MAX(glucoseLevel, %d), %d), COUNT(*)
                FROM health_data
                WHERE id IN (%s)
                GROUP BY MIN(MAX(glucoseLevel, %d), %d)
                """.formatted(lower, upper, idsStr, lower, upper);
        List<Object[]> rows = hibernate.sql(query, Object[].class);
        Map<String, Float> histogram = new LinkedHashMap<>();
        for (int value = lower; value <= upper; value++) {
            histogram.put(Integer.toString(value), (float) laplaceNoise(1.0 / privacyCost));
        }
        for (Object[] row : rows) {
            int value = Math.max(lower, Math.min(upper, ((Number) row[0]).intValue()));
            float noisyCount = histogram.get(Integer.toString(value)) + ((Number) row[1]).floatValue();
            histogram.put(Integer.toString(value), noisyCount);
        }

        var result = new DPResult();
        result.setResultType(DPResult.ResultType.HISTOGRAM);
        result.setHistogramResultValue(histogram);
        result.setQuerySensitivity(1.0f);
        result.setClippingLower((float) lower);
        result.setClippingUpper((float) upper);
        result.setResultCode(DPResult.ResultCode.SUCCESS);
        result.setPrivacyBudget(getPrivacyBudget());
        return result;
    }


    // Getters and Setters
    /**
     * Get privacyBudget.
     *
     * @return privacyBudget as float.
     */
    public float getPrivacyBudget() {
        return (float) readPrivacyBudget();
    }

    public int getDatasetSize() {
        return recordIds.size();
    }

    /* ---- LaPlace ----*/
    private double laplaceNoise(double scale) {
        double u;
        do {
            u = RANDOM.nextDouble() - 0.5;
        } while (u == -0.5);
        return -scale * Math.signum(u) * Math.log(1 - 2 * Math.abs(u));
    }

    private boolean spendBudget(float cost) {
        if (!active || !Float.isFinite(cost) || cost <= 0) {
            return false;
        }
        int[] updatedRows = {0};
        hibernate.execute(session -> updatedRows[0] = session.createNativeMutationQuery(
            "UPDATE dp_privacy_budget SET remaining_budget = MAX(0, remaining_budget - :cost) "
                + "WHERE dataset_key = :datasetKey AND remaining_budget >= :cost")
                .setParameter("cost", cost)
                .setParameter("datasetKey", datasetKey)
                .executeUpdate());
        return updatedRows[0] == 1;
    }

    private DPResult failResult() {
        var res = new DPResult();
        res.setResultCode(DPResult.ResultCode.FAIL);
        res.setPrivacyBudget(getPrivacyBudget());
        return res;
    }

    //again, this method is arguable and im not sure about this approach
    private void ensureBudgetLedger(float initialBudget) {
        if (!Float.isFinite(initialBudget) || initialBudget < 0) {
            throw new IllegalArgumentException("Initial privacy budget must be finite and non-negative");
        }
        hibernate.execute(session -> session.createNativeMutationQuery(
                "CREATE TABLE IF NOT EXISTS dp_privacy_budget ("
                        + "dataset_key TEXT PRIMARY KEY, remaining_budget REAL NOT NULL)").executeUpdate());
        hibernate.execute(session -> session.createNativeMutationQuery(
                "INSERT OR IGNORE INTO dp_privacy_budget (dataset_key, remaining_budget) VALUES (:datasetKey, :budget)")
                .setParameter("datasetKey", datasetKey)
                .setParameter("budget", initialBudget)
                .executeUpdate());
    }

    private double readPrivacyBudget() {
        List<Double> budgets = hibernate.sql(
                "SELECT remaining_budget FROM dp_privacy_budget WHERE dataset_key = '" + datasetKey + "'",
                Double.class);
        return budgets.isEmpty() ? 0 : budgets.get(0);
    }

}
