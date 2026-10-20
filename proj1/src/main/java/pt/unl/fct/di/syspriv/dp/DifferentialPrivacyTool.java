package pt.unl.fct.di.syspriv.dp;

import pt.unl.fct.di.syspriv.entities.DPResult;
import pt.unl.fct.di.syspriv.storage.Hibernate;

import java.util.List;

public class DifferentialPrivacyTool {

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
        var query = """
                SELECT COUNT(*)
                FROM health_data
                WHERE glucose<%d
                AND id IN (%s)
                """.formatted(upperGlucose, idsStr);
        var count = hibernate.sql(query, Integer.class).get(0);
        var res = new DPResult();
        res.setResultValue(count);
        return res;
    }

    // TODO Right now this method does not provide Differential Privacy. Change it
    public DPResult sumGlucoseValues(float privacyCost) {
        var query = """
                SELECT SUM(glucose)
                FROM health_data
                WHERE id IN (%s)
                """.formatted(idsStr);
        var sum = hibernate.sql(query, Float.class).get(0);
        var res = new DPResult();
        res.setResultValue(sum);
        return res;
    }

    /*
     * TODO Right now this method does not provide Differential Privacy. Change it
     *  Hint: It may help to consider that the average of a collection is: avg(col) = sum(col) / len(col)
     */
    public DPResult averageGlucoseValues(float privacyCost) {
        var query = """
                SELECT AVG(glucose)
                FROM health_data
                WHERE id IN (%s)
                """.formatted(idsStr);
        var avg = hibernate.sql(query, Float.class).get(0);
        var res = new DPResult();
        res.setResultValue(avg);
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

}
