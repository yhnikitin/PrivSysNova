package pt.unl.fct.di.syspriv.entities;

import java.util.Date;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnonymousHealthData implements Anonymizable {
    private Long id;

    private float glucose;
    
    private float patientBirthYear;

    private float patientPostalCode;

    private float diagnosisDate;

    public AnonymousHealthData() {}

    public AnonymousHealthData(Long id, float patientBirthYear, String addr,
                    float glucose, Date diagnosisDate) {
        this.id = id;
        this.patientBirthYear = patientBirthYear;
        this.patientPostalCode = Float.parseFloat(addr.substring(addr.length()-5));
        this.glucose = glucose;
        Calendar cal = Calendar.getInstance();
        cal.setTime(diagnosisDate);
        this.diagnosisDate = cal.get(Calendar.YEAR);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStringId() { return Long.toString(id); }
    public void setId(Long id) { this.id = id; }
    public void setPatientPostalCode(float patientPostalCode) {
            this.patientPostalCode = patientPostalCode;
    }
    public float getPatientPostalCode() { return patientPostalCode; }
    public void setPatientBirthYear(float patientBirthYear) {
            this.patientBirthYear = patientBirthYear;
    }
    public float getPatientBirthYear() { return patientBirthYear; }
    public float getGlucose() { return glucose; }
    public void setGlucose(float glucose) { this.glucose = glucose; }
    public float getDiagnosisDate() { return diagnosisDate; }
    public void setDiagnosisDate(Date diagnosisDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(diagnosisDate);
        this.diagnosisDate = cal.get(Calendar.YEAR);
    }

    @Override
    public Map<String, Object> getQuasiIdentifiers() {
        Map<String, Object> identifiers = new LinkedHashMap<>();
        identifiers.put("patientBirthYear", patientBirthYear);
        identifiers.put("patientPostalCode", patientPostalCode);
        identifiers.put("diagnosisDate", diagnosisDate);
        return identifiers;
    }

    @Override
    public void setQuasiIdentifiers(Map<String, Object> quasiIdentifiers) {
        id = ((Number) quasiIdentifiers.get("id")).longValue();
        patientBirthYear = ((Number) quasiIdentifiers.get("patientBirthYear")).floatValue();
        patientPostalCode = ((Number) quasiIdentifiers.get("patientPostalCode")).floatValue();
        diagnosisDate = ((Number) quasiIdentifiers.get("diagnosisDate")).floatValue();
    }

    @Override
    public Object getSensitiveValue() {
        return glucose;
    }

    // For debugging and logging purposes.
    @Override
    public String toString() {
        return "AnonymousHealthData={" +
                "id=" + getStringId() +
                ", glucose=" + Float.toString(glucose) + 
                ", diagnosisDate=" + Float.toString(diagnosisDate) +
                '}';
    }
}
