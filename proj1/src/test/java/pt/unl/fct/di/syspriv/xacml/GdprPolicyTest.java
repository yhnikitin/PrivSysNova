package pt.unl.fct.di.syspriv.xacml;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pt.unl.fct.di.syspriv.entities.*;
import pt.unl.fct.di.syspriv.storage.Hibernate;

class GdprPolicyTest {

    private static final String BASIS = "legal-obligation";

    private static XACMLPDP pdp;
    private static Employee doctor, dpo, auditor, itAdmin;

    @BeforeAll
    static void setUp() throws Exception {
        pdp = new XACMLPDP();
        doctor = new Employee("Test Doctor", "Doctor", "Cardiology", false);
        dpo = new Employee("Test DPO", "DPO", "Compliance", false);
        auditor = new Employee("Test Auditor", "Auditor", "Compliance", false);
        itAdmin = new Employee("Test ITAdmin", "ITAdmin", "IT", true);
        Hibernate.getInstance().persist(doctor, dpo, auditor, itAdmin);
    }

    // ---------- helpers ----------

    private Patient newPatient(String name) {
        return new Patient(name, "1990-01-01 00:00:00", "TestInsurance", "Rua Teste", doctor);
    }

    private void addConsent(Patient p, String purpose, boolean granted, LocalDateTime withdrawnAt) {
        ConsentRecord c = new ConsentRecord();
        c.setPatientSubject(p);
        c.setSubjectType("PATIENT");
        c.setPurpose(purpose);
        c.setGranted(granted);
        c.setGrantedAt(LocalDateTime.now().minusDays(5));
        c.setWithdrawnAt(withdrawnAt);
        c.setPolicyVersion("v1");
        c.setSource("test");
        p.getConsentRecords().add(c);
    }

    private void addErasureRequest(Patient p) {
        GdprRequest r = new GdprRequest();
        r.setPatientSubject(p);
        r.setSubjectType("PATIENT");
        r.setRequestType("ERASURE");
        r.setRequestedAt(LocalDateTime.now());
        r.setStatus("PENDING");
        p.getGdprRequests().add(r);
    }

    private Patient save(Patient p) {
        Hibernate.getInstance().persist(p);
        return p;
    }

    // ---------- 1-3: marketing ----------

    @Test
    void marketingPermittedWhenConsentGrantedAndNotWithdrawn() throws Exception {
        Patient p = newPatient("T1");
        addConsent(p, "marketing", true, null);
        save(p);
        assertTrue(pdp.checkAccessGDPR(doctor, p, "marketing", null, null));
    }

    @Test
    void marketingDeniedWhenConsentWithdrawn() throws Exception {
        Patient p = newPatient("T2");
        addConsent(p, "marketing", true, LocalDateTime.now().minusDays(1));
        save(p);
        assertFalse(pdp.checkAccessGDPR(doctor, p, "marketing", null, null));
    }

    @Test
    void marketingDeniedWhenConsentNeverGranted() throws Exception {
        Patient p = save(newPatient("T3"));
        assertFalse(pdp.checkAccessGDPR(doctor, p, "marketing", null, null));
    }

    // ---------- 4-5: erasure ----------

    @Test
    void doctorCannotAccessWhenErasureRequested() throws Exception {
        Patient p = newPatient("T4");
        addErasureRequest(p);
        save(p);
        assertFalse(pdp.checkAccessGDPR(doctor, p, "read", BASIS, "Appointment"));
    }

    @Test
    void dpoCanAccessWhenErasureRequested() throws Exception {
        Patient p = newPatient("T5");
        addErasureRequest(p);
        save(p);
        assertTrue(pdp.checkAccessGDPR(dpo, p, "read", BASIS, "Appointment"));
    }

    // ---------- 6-7: retention ----------

    @Test
    void doctorCannotAccessWhenRetentionExpired() throws Exception {
        Patient p = newPatient("T6");
        p.setRetentionUntil(LocalDateTime.now().minusDays(1));
        save(p);
        assertFalse(pdp.checkAccessGDPR(doctor, p, "read", BASIS, "Appointment"));
    }

    @Test
    void auditorCanAccessWhenRetentionExpired() throws Exception {
        Patient p = newPatient("T7");
        p.setRetentionUntil(LocalDateTime.now().minusDays(1));
        save(p);
        assertTrue(pdp.checkAccessGDPR(auditor, p, "read", BASIS, "Appointment"));
    }

    // ---------- 8: health data ----------

    private HealthData newHealthData(Patient p) {
        HealthData hd = new HealthData();
        hd.setPatientId(p.getId());          // adapta ao teu HealthData
        return hd;
    }

    @Test
    void healthDataDeniedWithoutResearchConsent() throws Exception {
        Patient p = save(newPatient("T8a"));
        HealthData hd = newHealthData(p);
        assertFalse(pdp.checkAccessGDPR(itAdmin, hd, "read", BASIS, "Emergency"));
    }

    @Test
    void healthDataPermittedWithResearchConsent() throws Exception {
        Patient p = newPatient("T8b");
        addConsent(p, "research", true, null);
        save(p);
        HealthData hd = newHealthData(p);
        assertTrue(pdp.checkAccessGDPR(itAdmin, hd, "read", BASIS, "Emergency"));
    }

    @Test
    void patientCanAccessOwnedAppointmentAndPayment() throws Exception {
        Patient patient = save(newPatient("T9"));
        Appointment appointment = new Appointment(LocalDate.now(), doctor, patient);
        Hibernate.getInstance().persist(appointment);
        Payment payment = new Payment(LocalDate.now(), patient, 25.0f, appointment, "4111111111111111");
        Hibernate.getInstance().persist(payment);

        assertTrue(pdp.checkAccessGDPR(patient, appointment, "read", BASIS, "Appointment"));
        assertTrue(pdp.checkAccessGDPR(patient, payment, "read", BASIS, "Billing and Payments"));
    }

    @Test
    void employeeCanAccessOwnedAppointment() throws Exception {
        Patient patient = save(new Patient("T10", "1990-01-01 00:00:00", "TestInsurance", "Rua Teste", dpo));
        Appointment appointment = new Appointment(LocalDate.now(), doctor, patient);
        Hibernate.getInstance().persist(appointment);

        assertTrue(pdp.checkAccessGDPR(doctor, appointment, "read", BASIS, "Appointment"));
    }

    @Test
    void clinicianCanAccessOwnPatientOutsideEmergency() throws Exception {
        Patient patient = save(newPatient("T11"));

        assertTrue(pdp.checkAccessGDPR(doctor, patient, "read", BASIS, "Appointment"));
    }

    @Test
    void unrelatedPatientCannotAccessAppointmentOrPatientRecord() throws Exception {
        Patient owner = save(newPatient("T12"));
        Patient unrelated = save(new Patient("T13", "1990-01-01 00:00:00", "TestInsurance", "Other St", dpo));
        Appointment appointment = new Appointment(LocalDate.now(), doctor, owner);
        Hibernate.getInstance().persist(appointment);

        assertFalse(pdp.checkAccessGDPR(unrelated, owner, "read", BASIS, "Appointment"));
        assertFalse(pdp.checkAccessGDPR(unrelated, appointment, "read", BASIS, "Appointment"));
    }

    @Test
    void healthDataRetentionFollowsPatientRetentionDate() throws Exception {
        Patient activePatient = newPatient("T14");
        addConsent(activePatient, "research", true, null);
        save(activePatient);
        HealthData activeHealthData = new HealthData(activePatient.getId(), 110, new Date());
        Hibernate.getInstance().persist(activeHealthData);

        Patient expiredPatient = newPatient("T15");
        expiredPatient.setRetentionUntil(LocalDateTime.now().minusDays(1));
        addConsent(expiredPatient, "research", true, null);
        save(expiredPatient);
        HealthData expiredHealthData = new HealthData(expiredPatient.getId(), 115, new Date());
        Hibernate.getInstance().persist(expiredHealthData);

        assertTrue(pdp.checkAccessGDPR(itAdmin, activeHealthData, "read", BASIS, "Emergency"));
        assertFalse(pdp.checkAccessGDPR(itAdmin, expiredHealthData, "read", BASIS, "Emergency"));
    }
}