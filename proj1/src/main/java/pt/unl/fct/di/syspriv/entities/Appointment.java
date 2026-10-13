package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dateTime", nullable = false)
    private LocalDate dateTime;

    @JoinColumn()
    @ManyToOne
    @JsonIgnore
    private Employee employee;

    @JoinColumn()
    @ManyToOne
    @JsonIgnore
    private Patient patient;

    public Appointment() {}

    public Appointment(LocalDate dateTime, Employee employee, Patient patient) {
        this.dateTime = dateTime;
        this.employee = employee;
        this.patient = patient;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStringId() { return Long.toString(id); }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDateTime() { return dateTime; }
    public void setDateTime(LocalDate dateTime) { this.dateTime = dateTime; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    // For debugging and logging purposes.
    @Override
    public String toString() {
        return "Appointment{" +
                "id=" + Long.toString(id) +
                ", DateTime='" + dateTime.toString() + '\'' +
                ", employee=" + employee.toString() +
                ", patient='" + patient.toString() + '\'' +
                '}';
    }
}
