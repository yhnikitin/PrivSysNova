package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "health_data")
public class HealthData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;

    @Column(name = "glucoseLevel", nullable = false)
    private int glucoseLevel;

    @Column(name = "measurementDate", nullable = false)
    private Date measurementDate;

    public HealthData() {}

    public HealthData(Long patientId, int glucoseLevel, Date measurementDate) {
        this.patientId = patientId;
        this.glucoseLevel = glucoseLevel;
        this.measurementDate = measurementDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStringId() { return Long.toString(id); }
    public void setId(Long id) { this.id = id; }
    public Long getPatientId() { return this.patientId;}
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public int getGlucoseLevel() { return glucoseLevel; }
    public void setGlucoseLevel(int glucoseLevel) { this.glucoseLevel = glucoseLevel; }
    public Date getMeasurementDate() { return measurementDate; }
    public void setMeasurementDate(Date measurementDate) { this.measurementDate = measurementDate; }

    // For debugging and logging purposes.
    @Override
    public String toString() {
        return "HealthData={" +
                "id=" + getStringId() +
                ", patientId=" + Long.toString(this.patientId) + 
                ", glucoseLevel=" + Integer.toString(glucoseLevel) +
                ", measurementDate=" + getMeasurementDate().toString() +
                '}';
    }
}
