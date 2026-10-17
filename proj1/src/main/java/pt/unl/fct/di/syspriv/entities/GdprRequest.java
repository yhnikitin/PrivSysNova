package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@Entity
@Table(name = "gdpr_requests")
//@JsonInclude(JsonInclude.Include.NON_NULL)
public class GdprRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_subject_id")
    //@JsonIgnore
    private Employee employeeSubject;

    @ManyToOne
    @JoinColumn(name = "patient_subject_id")
    //@JsonIgnore
    private Patient patientSubject;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType;

    @Column(name = "lawful_basis", length = 30)
    private String lawfulBasis;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(length = 20)
    private String status = "PENDING";

    @ManyToOne
    @JoinColumn(name = "handled_by")
    //@JsonIgnore
    private Employee handledBy;

    @Transient
    private Boolean redacted;

    public GdprRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employee getEmployeeSubject() { return employeeSubject; }
    public void setEmployeeSubject(Employee employeeSubject) { this.employeeSubject = employeeSubject; }
    public Patient getPatientSubject() { return patientSubject; }
    public void setPatientSubject(Patient patientSubject) { this.patientSubject = patientSubject; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getLawfulBasis() { return lawfulBasis; }
    public void setLawfulBasis(String lawfulBasis) { this.lawfulBasis = lawfulBasis; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Employee getHandledBy() { return handledBy; }
    public void setHandledBy(Employee handledBy) { this.handledBy = handledBy; }
    public Boolean getRedacted() { return redacted; }
    public void setRedacted(Boolean redacted) { this.redacted = redacted; }
}
