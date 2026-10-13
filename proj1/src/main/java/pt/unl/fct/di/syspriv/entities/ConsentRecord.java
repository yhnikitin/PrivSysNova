package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@Entity
@Table(name = "consent_records")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_subject_id")
    @JsonIgnore
    private Employee employeeSubject;

    @ManyToOne
    @JoinColumn(name = "patient_subject_id")
    @JsonIgnore
    private Patient patientSubject;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "purpose", nullable = false, length = 50)
    private String purpose;

    @Column(name = "granted", nullable = false)
    private Boolean granted;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "policy_version", length = 20)
    private String policyVersion;

    @Column(name = "source", length = 50)
    private String source;

    @Transient
    private Boolean redacted;

    public ConsentRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employee getEmployeeSubject() { return employeeSubject; }
    public void setEmployeeSubject(Employee employeeSubject) { this.employeeSubject = employeeSubject; }
    public Patient getPatientSubject() { return patientSubject; }
    public void setPatientSubject(Patient patientSubject) { this.patientSubject = patientSubject; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Boolean isGranted() { return granted; }
    public void setGranted(Boolean granted) { this.granted = granted; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(LocalDateTime withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getRedacted() { return redacted; }
    public void setRedacted(Boolean redacted) { this.redacted = redacted; }
}
