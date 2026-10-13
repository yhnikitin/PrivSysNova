package pt.unl.fct.di.syspriv.util;

import java.util.List;
import pt.unl.fct.di.syspriv.entities.*;

public class ExportEntity {

    private Redactable<Employee> employee;

    private Redactable<Patient> patient;

    // Patients of a Doctor or Nurse
    private List<Redactable<Patient>> patients;

    // Doctors and Nurses of a Patient
    private List<Redactable<Employee>> clinicians;

    private List<Redactable<Appointment>> appointments;

    private List<Redactable<Payment>> payments;

    private List<ConsentRecord> consentRecords;

    private List<GdprRequest> gdprRequests;

    public ExportEntity() {}

    public ExportEntity(Redactable<Employee> employee, Redactable<Patient> patient, List<Redactable<Patient>> patients, List<Redactable<Employee>> clinicians, List<Redactable<Appointment>> appointments, List<Redactable<Payment>> payments, List<ConsentRecord> consentRecords, List<GdprRequest> gdprRequests) {
        this.employee = employee;
        this.patient = patient;
        this.patients = patients;
        this.clinicians = clinicians;
        this.appointments = appointments;
        this.payments = payments;
        this.consentRecords = consentRecords;
        this.gdprRequests = gdprRequests;
    }

    public Redactable<Employee> getEmployee() {
        return employee;
    }

    public void setEmployee(Redactable<Employee> employee) {
        this.employee = employee;
    }

    public Redactable<Patient> getPatient() {
        return patient;
    }

    public void setPatient(Redactable<Patient> patient) {
        this.patient = patient;
    }

    public List<Redactable<Patient>> getPatients() {
        return patients;
    }

    public void setPatients(List<Redactable<Patient>> patients) {
        this.patients = patients;
    }

    public List<Redactable<Employee>> getClinicians() {
        return clinicians;
    }

    public void setClinicians(List<Redactable<Employee>> clinicians) {
        this.clinicians = clinicians;
    }

    public List<Redactable<Appointment>> getAppointments() {
        return appointments;
    }

    public void setAppointments(List<Redactable<Appointment>> appointments) {
        this.appointments = appointments;
    }

    public List<Redactable<Payment>> getPayments() {
        return payments;
    }

    public void setPayments(List<Redactable<Payment>> payments) {
        this.payments = payments;
    }

    public List<ConsentRecord> getConsentRecords() {
        return consentRecords;
    }

    public void setConsentRecords(List<ConsentRecord> consentRecords) {
        this.consentRecords = consentRecords;
    }

    public List<GdprRequest> getGdprRequests() {
        return gdprRequests;
    }

    public void setGdprRequests(List<GdprRequest> gdprRequests) {
        this.gdprRequests = gdprRequests;
    }

    @Override
    public String toString() {
        return "ExportEntity{" +
                "employee=" + employee +
                ", patient=" + patient +
                ", patients=" + patients +
                ", clinicians=" + clinicians +
                ", appointments=" + appointments +
                ", payments=" + payments +
                ", consentRecords=" + consentRecords +
                ", gdprRequests=" + gdprRequests +
                '}';
    }

    public static class Redactable<V> {

        private V row;

        private Long redactedRowId;

        public Redactable() {}

        public Redactable(V row, Long id) {
            this.row = row;
            this.redactedRowId = id;
        }

        public V getRow() {
            return row;
        }

        public void setRow(V row) {
            this.row = row;
        }

        public Long getRedactedRowId() {
            return redactedRowId;
        }

        public void setRedactedRowId(Long redactedRowId) {
            this.redactedRowId = redactedRowId;
        }

        @Override
        public String toString() {
            return "Redactable{" +
                    "row=" + row +
                    ", redactedRowId=" + redactedRowId +
                    '}';
        }
    }
}
