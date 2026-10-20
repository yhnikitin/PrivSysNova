package pt.unl.fct.di.syspriv.dp;

import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.util.ConfigLoader;

import java.util.List;
import java.util.Random;

public class DifferentialPrivacyTool {

    private final int lower = ConfigLoader.getIntProperty("dp.glucose.lower", 0);
    private final int upper = ConfigLoader.getIntProperty("dp.glucose.upper", 400);
    private static final Random RANDOM = new Random();


    private float privacyBudget;

    private final Hibernate hibernate;

    private final List<Long> recordIds;

    // Auxiliary attribute to ensure queries only use the records in this dataset chunk.
    private final String idsStr;

    public DifferentialPrivacyTool(float privacyBudget) {
        this.privacyBudget = privacyBudget;
        this.hibernate = Hibernate.getInstance();
        this.recordIds = hibernate.sql("SELECT id FROM health_data", Long.class);
        this.idsStr = concatenateRecordIds(recordIds);
    }

    public DifferentialPrivacyTool(float privacyBudget, List<Long> recordIds) {
        this.privacyBudget = privacyBudget;
        this.hibernate = Hibernate.getInstance();
        this.recordIds = recordIds;
        this.idsStr = concatenateRecordIds(recordIds);
    }

    private static String concatenateRecordIds(List<Long> recordIds) {
        var idsStr = recordIds.stream().map("'%d'"::formatted).toList();
        return String.join(",", idsStr);
    }

    public DifferentialPrivacyTool[] split(int cutoffIdx) {
        var lftRecords = this.recordIds.subList(0, cutoffIdx);
        var rgtRecords = this.recordIds.subList(cutoffIdx, recordIds.size());
        var lft = new DifferentialPrivacyTool(privacyBudget, lftRecords);
        var rgt = new DifferentialPrivacyTool(privacyBudget, rgtRecords);
        this.privacyBudget = 0; //Ensure this chunk becomes unusable.
        return new DifferentialPrivacyTool[]{lft, rgt};
    }

    // TODO Right now this method does not provide Differential Privacy. Change it
    public DPResult countBelowGlucose(float privacyCost, int upperGlucose) {
        if (!spendBudget(privacyCost)) {
            return failResult();
        }

        var query = """
                SELECT COUNT(*)
                FROM health_data
                WHERE glucose<%d
                AND id IN (%s)
                """.formatted(upperGlucose, idsStr);
        var count = hibernate.sql(query, Integer.class).get(0);

        double sensitivity = 1.0;
        double noisy = count + laplaceNoise(sensitivity / privacyCost);


        var res = new DPResult();
        res.setResultValue(count);

        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(privacyBudget);
        return res;
    }

    // TODO Right now this method does not provide Differential Privacy. Change it
    public DPResult sumGlucoseValues(float privacyCost) {
        if (!spendBudget(privacyCost)) {
            return failResult();
        }

        var query = """
                SELECT SUM(glucose)
                FROM health_data
                WHERE id IN (%s)
                """.formatted(idsStr);
        var sum = hibernate.sql(query, Float.class).get(0);
        var res = new DPResult();
        res.setResultValue(sum);

        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(privacyBudget);
        return res;
    }

    /*
     * TODO Right now this method does not provide Differential Privacy. Change it
     *  Hint: It may help to consider that the average of a collection is: avg(col) = sum(col) / len(col)
     */
    public DPResult averageGlucoseValues(float privacyCost) {
        if (!spendBudget(privacyCost)) {
            return failResult();
        }

        var query = """
                SELECT AVG(glucose)
                FROM health_data
                WHERE id IN (%s)
                """.formatted(idsStr);
        var avg = hibernate.sql(query, Float.class).get(0);
        var res = new DPResult();
        res.setResultValue(avg);

        res.setResultCode(DPResult.ResultCode.SUCCESS);
        res.setPrivacyBudget(privacyBudget);
        return res;
    }


    // Getters and Setters
    /**
     * Get privacyBudget.
     *
     * @return privacyBudget as float.
     */
    public float getPrivacyBudget() {
        return privacyBudget;
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
        if (cost <= 0 || cost > privacyBudget) {
            return false;
        }
        privacyBudget -= cost;
        return true;
    }

    private DPResult failResult() {
        var res = new DPResult();
        res.setResultCode(DPResult.ResultCode.FAIL);
        res.setPrivacyBudget(privacyBudget);
        return res;
    }
}
