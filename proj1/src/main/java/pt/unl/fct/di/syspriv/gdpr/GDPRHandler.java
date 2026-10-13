package pt.unl.fct.di.syspriv.gdpr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import pt.unl.fct.di.syspriv.entities.*;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.util.ExportEntity;
import pt.unl.fct.di.syspriv.util.Exportable;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GDPRHandler implements Exportable {

    private static final Logger logger = LoggerFactory.getLogger(GDPRHandler.class);
    private static final ObjectMapper JSON = createJsonMapper();

    private final Hibernate hibernate;
    private final Object subject;
    private final String lawfulBasis;

    public GDPRHandler(Hibernate hibernate, Object subject, String lawfulBasis) {
        this.hibernate = hibernate;
        this.subject = subject;
        this.lawfulBasis = lawfulBasis;
    }

    public String handleRequest(String requestType, XACMLPDP pdp) throws Exception {
        if (!(subject instanceof Employee) && !(subject instanceof Patient)) {
            throw new IllegalArgumentException("GDPR requests require an employee or patient subject");
        }
        if (!"ACCESS".equals(requestType) && !"ERASURE".equals(requestType)) {
            throw new IllegalArgumentException("Unsupported GDPR request type: " + requestType);
        }
        if ("ACCESS".equals(requestType) && !isValidLawfulBasis(lawfulBasis)) {
            throw new IllegalArgumentException("A valid lawful basis is required for an ACCESS request");
        }

        GdprRequest request = createRequest(requestType);
        hibernate.persist(request);

        if ("ACCESS".equals(requestType)) {
            request.setStatus("COMPLETED");
            request.setCompletedAt(LocalDateTime.now());
            hibernate.update(request);
            return JSON.writeValueAsString(export(pdp));
        }

        performErasure(request);
        return null;
    }

    @Override
    public ExportEntity export(XACMLPDP pdp) {
        Long subjectId = getSubjectId(subject);
        ExportEntity result = new ExportEntity();

        if (subject instanceof Employee) {
            Employee employee = hibernate.get(Employee.class, subjectId);
            result.setEmployee(exportRecord(pdp, employee));
            result.setAppointments(exportRecords(pdp, hibernate.jpql(
                    "FROM Appointment a LEFT JOIN FETCH a.employee LEFT JOIN FETCH a.patient WHERE a.employee.id = "
                            + subjectId, Appointment.class)));
            result.setPayments(exportRecords(pdp, hibernate.jpql(
                    "FROM Payment p LEFT JOIN FETCH p.patient LEFT JOIN FETCH p.appointment a "
                            + "WHERE a.employee.id = " + subjectId, Payment.class)));
        } else {
            List<Patient> patients = hibernate.jpql(
                    "FROM Patient p LEFT JOIN FETCH p.primaryDoctor WHERE p.id = " + subjectId, Patient.class);
            result.setPatient(exportRecord(pdp, patients.isEmpty() ? null : patients.get(0)));
            result.setAppointments(exportRecords(pdp, hibernate.jpql(
                    "FROM Appointment a LEFT JOIN FETCH a.employee LEFT JOIN FETCH a.patient WHERE a.patient.id = "
                            + subjectId, Appointment.class)));
            result.setPayments(exportRecords(pdp, hibernate.jpql(
                    "FROM Payment p LEFT JOIN FETCH p.patient LEFT JOIN FETCH p.appointment WHERE p.patient.id = "
                            + subjectId, Payment.class)));
        }

        result.setConsentRecords(exportConsentRecords(pdp, findConsentRecords(subjectId)));
        result.setGdprRequests(exportGdprRequests(pdp, findGdprRequests(subjectId)));
        return result;
    }

    private GdprRequest createRequest(String requestType) {
        GdprRequest request = new GdprRequest();
        request.setSubjectType(subject instanceof Employee ? "EMPLOYEE" : "PATIENT");
        request.setRequestType(requestType);
        request.setLawfulBasis("ACCESS".equals(requestType) ? lawfulBasis : null);
        request.setRequestedAt(LocalDateTime.now());
        if (subject instanceof Employee employee) {
            request.setEmployeeSubject(employee);
        } else if (subject instanceof Patient patient) {
            request.setPatientSubject(patient);
        }
        return request;
    }

    private void performErasure(GdprRequest request) {
        request.setStatus("PENDING");
        hibernate.update(request);

        String anonymizedIdentifier = "ANON-" + UUID.randomUUID();
        List<Object> updates = new ArrayList<>();
        if (subject instanceof Employee employee) {
            Employee storedEmployee = hibernate.get(Employee.class, employee.getId());
            storedEmployee.setName(anonymizedIdentifier);
            storedEmployee.setUpdatedAt(LocalDateTime.now());
            updates.add(storedEmployee);
        } else if (subject instanceof Patient patient) {
            Patient storedPatient = hibernate.get(Patient.class, patient.getId());
            storedPatient.setName(anonymizedIdentifier);
            storedPatient.setAddress(anonymizedIdentifier);
            storedPatient.setUpdatedAt(LocalDateTime.now());
            updates.add(storedPatient);

            List<Payment> payments = hibernate.jpql(
                    "FROM Payment p WHERE p.patient.id = " + patient.getId(), Payment.class);
            for (Payment payment : payments) {
                payment.setCardNumber(anonymizedIdentifier);
                payment.setAnonymized(true);
                updates.add(payment);
            }
        }

        hibernate.update(updates.toArray());
        request.setStatus("COMPLETED");
        request.setCompletedAt(LocalDateTime.now());
        hibernate.update(request);
    }

    private <T> List<ExportEntity.Redactable<T>> exportRecords(XACMLPDP pdp, List<T> records) {
        List<ExportEntity.Redactable<T>> exported = new ArrayList<>();
        for (T record : records) {
            exported.add(exportRecord(pdp, record));
        }
        return exported;
    }

    private <T> ExportEntity.Redactable<T> exportRecord(XACMLPDP pdp, T record) {
        Long id = getRecordId(record);
        if (!isPermitted(pdp, record)) {
            return new ExportEntity.Redactable<>(null, id);
        }
        return new ExportEntity.Redactable<>(record, null);
    }

    private List<ConsentRecord> exportConsentRecords(XACMLPDP pdp, List<ConsentRecord> records) {
        List<ConsentRecord> exported = new ArrayList<>();
        for (ConsentRecord record : records) {
            if (isPermitted(pdp, record)) {
                exported.add(record);
            } else {
                ConsentRecord redacted = new ConsentRecord();
                redacted.setId(record.getId());
                redacted.setRedacted(true);
                exported.add(redacted);
            }
        }
        return exported;
    }

    private List<GdprRequest> exportGdprRequests(XACMLPDP pdp, List<GdprRequest> records) {
        List<GdprRequest> exported = new ArrayList<>();
        for (GdprRequest record : records) {
            if (isPermitted(pdp, record)) {
                exported.add(record);
            } else {
                GdprRequest redacted = new GdprRequest();
                redacted.setId(record.getId());
                redacted.setRedacted(true);
                exported.add(redacted);
            }
        }
        return exported;
    }

    private boolean isPermitted(XACMLPDP pdp, Object record) {
        try {
            return pdp.checkAccessGDPR(subject, record, "read", lawfulBasis, "Access");
        } catch (Exception exception) {
            logger.warn("PDP check failed for export record {}: {}", getRecordId(record), exception.getMessage());
            return false;
        }
    }

    private boolean isValidLawfulBasis(String basis) {
        return "consent".equals(basis)
                || "contract".equals(basis)
                || "legal-obligation".equals(basis)
                || "vital-interests".equals(basis)
                || "public-task".equals(basis)
                || "legitimate-interests".equals(basis);
    }

    private List<ConsentRecord> findConsentRecords(Long subjectId) {
        String subjectProperty = subject instanceof Employee ? "employeeSubject" : "patientSubject";
        return hibernate.jpql("FROM ConsentRecord c WHERE c." + subjectProperty + ".id = " + subjectId,
                ConsentRecord.class);
    }

    private List<GdprRequest> findGdprRequests(Long subjectId) {
        String subjectProperty = subject instanceof Employee ? "employeeSubject" : "patientSubject";
        return hibernate.jpql("FROM GdprRequest r WHERE r." + subjectProperty + ".id = " + subjectId,
                GdprRequest.class);
    }

    private Long getSubjectId(Object value) {
        if (value instanceof Employee employee) return employee.getId();
        if (value instanceof Patient patient) return patient.getId();
        throw new IllegalArgumentException("Unsupported subject type: " + value.getClass().getName());
    }

    private Long getRecordId(Object value) {
        if (value instanceof Employee employee) return employee.getId();
        if (value instanceof Patient patient) return patient.getId();
        if (value instanceof Appointment appointment) return appointment.getId();
        if (value instanceof Payment payment) return payment.getId();
        if (value instanceof ConsentRecord consent) return consent.getId();
        if (value instanceof GdprRequest request) return request.getId();
        throw new IllegalArgumentException("Unsupported export record: " + value.getClass().getName());
    }

    private static ObjectMapper createJsonMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider)
                    throws IOException {
                generator.writeString(value.toString());
            }
        });
        module.addSerializer(LocalDate.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDate value, JsonGenerator generator, SerializerProvider provider)
                    throws IOException {
                generator.writeString(value.toString());
            }
        });
        mapper.registerModule(module);
        return mapper;
    }
}
