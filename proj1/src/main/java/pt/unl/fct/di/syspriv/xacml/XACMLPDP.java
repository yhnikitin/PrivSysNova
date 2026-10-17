package pt.unl.fct.di.syspriv.xacml;

import pt.unl.fct.di.syspriv.util.ConfigLoader;
import pt.unl.fct.di.syspriv.entities.*;
import pt.unl.fct.di.syspriv.storage.Hibernate;

import org.ow2.authzforce.core.pdp.api.*;
import org.ow2.authzforce.core.pdp.api.value.*;
import org.ow2.authzforce.core.pdp.impl.BasePdpEngine;
import org.ow2.authzforce.core.pdp.impl.PdpEngineConfiguration;
import org.ow2.authzforce.xacml.identifiers.XacmlStatusCode;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.logging.log4j.Level;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.time.LocalDateTime;
import org.apache.commons.io.IOUtils;

public class XACMLPDP {

    // Standard XACML category URNs
    private static final String SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject";
    private static final String RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource";
    private static final String ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action";
    private static final String ENV_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:environment";

    private final BasePdpEngine pdp;
    private Logger logger;

    public XACMLPDP() throws Exception {
        PdpEngineConfiguration config = PdpEngineConfiguration.getInstance(XACMLPDP.class.getClassLoader().getResource("pdp.xml").getPath());
        this.pdp = new BasePdpEngine(config);
        this.logger = LoggerFactory.getLogger(BasePdpEngine.class);
    }

    private String loadPolicyXml(String policyFile) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(policyFile);
        if (is != null) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        if (Files.exists(Paths.get(policyFile))) {
            return new String(Files.readAllBytes(Paths.get(policyFile)), StandardCharsets.UTF_8);
        }
        
        throw new IllegalStateException("Policy file not found: " + policyFile);
    }

    public boolean checkAccess(Object requester, Object resource, String context) throws Exception {
        Map<String, String> subjectAttrs = new HashMap<>();
        Map<String, String> resourceAttrs = new HashMap<>();
        Map<String, String> actionAttrs = new HashMap<>();
        Map<String, String> envAttrs = new HashMap<>();

        // Subject
         if (requester instanceof Employee employee) {
            subjectAttrs.put("role", employee.getRole());
            subjectAttrs.put("id", String.valueOf(employee.getId()));
        } else if (requester instanceof Patient patient) {
            subjectAttrs.put("role", "Patient");
            subjectAttrs.put("id", String.valueOf(patient.getId()));
        }

         //Resource
        if (resource instanceof Employee employee) {
            resourceAttrs.put("id", String.valueOf(employee.getId()));
            resourceAttrs.put("resource-type", "EmployeeData");
        } else if (resource instanceof Patient patient) {
            resourceAttrs.put("id", String.valueOf(patient.getId()));
            resourceAttrs.put("resource-type", "PatientData");
            //if (patient.getPrimaryDoctor() != null) { all patients have primary doc
            resourceAttrs.put("doctorId", String.valueOf(patient.getPrimaryDoctor().getId()));
            //}
        } else if (resource instanceof Appointment appointment) {
            resourceAttrs.put("id", String.valueOf(appointment.getId()));
            resourceAttrs.put("resource-type", "AppointmentData");
        } else if (resource instanceof Payment payment) {
            resourceAttrs.put("id", String.valueOf(payment.getId()));
            resourceAttrs.put("resource-type", "PaymentData");
        }

        //Context
        if (context != null && !context.isBlank()) {
            envAttrs.put("context", context);
        }
        
        actionAttrs.put("action-id", "read");

        DecisionRequestBuilder<?> requestBuilder = pdp.newRequestBuilder(-1, -1);

        addAttributes(requestBuilder, SUBJECT_CATEGORY, subjectAttrs);
        addAttributes(requestBuilder, RESOURCE_CATEGORY, resourceAttrs);
        addAttributes(requestBuilder, ACTION_CATEGORY, actionAttrs);
        addAttributes(requestBuilder, ENV_CATEGORY, envAttrs);

        var request = requestBuilder.build(true);
        logger.debug(request.toString());
        DecisionResult result = pdp.evaluate(request);
        
        logger.debug(result.toString());
        return result.getDecision() == DecisionType.PERMIT;
    }

        public boolean checkAccessGDPR(Object requester, Object resource, String action, String lawfulBasis,
            String context) throws Exception {
        Objects.requireNonNull(requester, "requester must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if ("read".equals(action) && (lawfulBasis == null || lawfulBasis.isBlank())) {
            throw new IllegalArgumentException("A lawful basis is required for read actions");
        }

        Map<String, String> subjectAttrs = new HashMap<>();
        Map<String, String> resourceAttrs = new HashMap<>();
        Map<String, String> actionAttrs = new HashMap<>();
        Map<String, String> envAttrs = new HashMap<>();

        List<ConsentRecord> consents = findConsentRecords(requester);
        List<GdprRequest> requests = findGdprRequests(requester);
        ConsentRecord marketingConsent = latestConsent(consents, "marketing");
        List<ConsentRecord> researchConsents = consents;

        //questionable but should limit amount of queries
        if (resource instanceof HealthData healthData
            && (!(requester instanceof Patient patient)
            || !Objects.equals(patient.getId(), healthData.getPatientId()))) {
            researchConsents = Hibernate.getInstance().jpql(
                "FROM ConsentRecord c WHERE c.patientSubject.id = " + healthData.getPatientId()
                    + " ORDER BY c.grantedAt",
                ConsentRecord.class);
        }
        ConsentRecord researchConsent = latestConsent(researchConsents, "research");

        if (requester instanceof Employee employee) {
            subjectAttrs.put("role", employee.getRole());
            subjectAttrs.put("id", String.valueOf(employee.getId()));
        } else if (requester instanceof Patient patient) {
            subjectAttrs.put("role", "Patient");
            subjectAttrs.put("id", String.valueOf(patient.getId()));
        } else {
            throw new IllegalArgumentException("Unsupported requester type: " + requester.getClass().getName());
        }

        subjectAttrs.put("consent-marketing", Boolean.toString(isConsentActive(marketingConsent)));
        subjectAttrs.put("consent-withdrawn", Boolean.toString(
            marketingConsent != null && marketingConsent.getWithdrawnAt() != null));
        subjectAttrs.put("consent-research", Boolean.toString(isConsentActive(researchConsent)));
        subjectAttrs.put("erasure-requested", Boolean.toString(requests.stream().anyMatch(request ->
            "ERASURE".equalsIgnoreCase(request.getRequestType()))));

        String resourceType = getResourceType(resource);
        resourceAttrs.put("id", String.valueOf(getResourceId(resource)));
        resourceAttrs.put("resource-type", resourceType);
        resourceAttrs.put("data-category", isHealthData(resource) ? "health-data" : "personal-data");
        subjectAttrs.put("retention-expired", Boolean.toString(isRetentionExpired(resource)));

        actionAttrs.put("action-id", action);
        if (lawfulBasis != null && !lawfulBasis.isBlank()) {
            subjectAttrs.put("lawful-basis", lawfulBasis);
        }
        if (context != null && !context.isBlank()) {
            envAttrs.put("context", context);
        }

        DecisionRequestBuilder<?> requestBuilder = pdp.newRequestBuilder(-1, -1);
        addAttributes(requestBuilder, SUBJECT_CATEGORY, subjectAttrs);
        addAttributes(requestBuilder, RESOURCE_CATEGORY, resourceAttrs);
        addAttributes(requestBuilder, ACTION_CATEGORY, actionAttrs);
        addAttributes(requestBuilder, ENV_CATEGORY, envAttrs);

        var request = requestBuilder.build(true);
        logger.debug(request.toString());
        DecisionResult result = pdp.evaluate(request);

        logger.debug(result.toString());
        return result.getDecision() == DecisionType.PERMIT;
    }

    private List<ConsentRecord> findConsentRecords(Object subject) {
        Long subjectId = getSubjectId(subject);
        if (subject instanceof Employee) {
            return Hibernate.getInstance().jpql(
                "FROM ConsentRecord c WHERE c.employeeSubject.id = " + subjectId + " ORDER BY c.grantedAt",
                ConsentRecord.class);
        }
        return Hibernate.getInstance().jpql(
            "FROM ConsentRecord c WHERE c.patientSubject.id = " + subjectId + " ORDER BY c.grantedAt",
            ConsentRecord.class);
    }

    private List<GdprRequest> findGdprRequests(Object subject) {
        Long subjectId = getSubjectId(subject);
        if (subject instanceof Employee) {
            return Hibernate.getInstance().jpql(
                "FROM GdprRequest r WHERE r.employeeSubject.id = " + subjectId + " ORDER BY r.requestedAt",
                GdprRequest.class);
        }
        return Hibernate.getInstance().jpql(
            "FROM GdprRequest r WHERE r.patientSubject.id = " + subjectId + " ORDER BY r.requestedAt",
            GdprRequest.class);
    }

    private Long getSubjectId(Object subject) {
        if (subject instanceof Employee employee) return employee.getId();
        if (subject instanceof Patient patient) return patient.getId();
        throw new IllegalArgumentException("Unsupported subject type: " + subject.getClass().getName());
    }

    private ConsentRecord latestConsent(List<ConsentRecord> records, String purpose) {
        return records.stream()
                .filter(record -> purpose.equalsIgnoreCase(record.getPurpose()))
                .max(Comparator.comparing(record ->
                        Optional.ofNullable(record.getGrantedAt()).orElse(LocalDateTime.MIN)))
                .orElse(null);
    }

    private boolean isConsentActive(ConsentRecord consent) {
        return consent != null && consent.isGranted() && consent.getWithdrawnAt() == null;
    }

    private boolean isRetentionExpired(Object resource) {
        LocalDateTime retentionUntil = null;
        if (resource instanceof Employee employee) {
            retentionUntil = employee.getRetentionUntil();
        } else if (resource instanceof Patient patient) {
            retentionUntil = patient.getRetentionUntil();
        } else if (resource instanceof Appointment appointment) {
            List<Appointment> appointments = Hibernate.getInstance().jpql(
                    "FROM Appointment a LEFT JOIN FETCH a.patient LEFT JOIN FETCH a.employee WHERE a.id = "
                        + appointment.getId(),
                    Appointment.class);
            if (!appointments.isEmpty()) {
                Appointment loaded = appointments.get(0);
                return isExpired(loaded.getPatient() == null ? null : loaded.getPatient().getRetentionUntil())
                        || isExpired(loaded.getEmployee() == null ? null : loaded.getEmployee().getRetentionUntil());
            }
        } else if (resource instanceof Payment payment) {
            List<Payment> payments = Hibernate.getInstance().jpql(
                    "FROM Payment p LEFT JOIN FETCH p.patient WHERE p.id = " + payment.getId(),
                    Payment.class);
            if (!payments.isEmpty() && payments.get(0).getPatient() != null) {
                retentionUntil = payments.get(0).getPatient().getRetentionUntil();
            }
        }
        return isExpired(retentionUntil);
    }

    private boolean isExpired(LocalDateTime retentionUntil) {
        return retentionUntil != null && retentionUntil.isBefore(LocalDateTime.now());
    }

    private boolean isHealthData(Object resource) {
        return resource instanceof HealthData;
    }

    private String getResourceType(Object resource) {
        if (resource instanceof Employee) return "EmployeeData";
        if (resource instanceof Patient) return "PatientData";
        if (resource instanceof Appointment) return "AppointmentData";
        if (resource instanceof Payment) return "PaymentData";
        if (resource instanceof HealthData) return "HealthData";
        if (resource instanceof ConsentRecord) return "ConsentData";
        if (resource instanceof GdprRequest) return "GdprRequestData";
        throw new IllegalArgumentException("Unsupported resource type: " + resource.getClass().getName());
    }

    private Long getResourceId(Object resource) {
        if (resource instanceof Employee employee) return employee.getId();
        if (resource instanceof Patient patient) return patient.getId();
        if (resource instanceof Appointment appointment) return appointment.getId();
        if (resource instanceof Payment payment) return payment.getId();
        if (resource instanceof HealthData healthData) return healthData.getId();
        if (resource instanceof ConsentRecord consentRecord) return consentRecord.getId();
        if (resource instanceof GdprRequest gdprRequest) return gdprRequest.getId();
        throw new IllegalArgumentException("Unsupported resource type: " + resource.getClass().getName());
    }

    private void addAttributes(DecisionRequestBuilder<?> requestBuilder, String category, Map<String, String> attrs) {
            for (var entry : attrs.entrySet()) {
            AttributeFqn fqn = AttributeFqns.newInstance(category, Optional.empty(), entry.getKey());
            AttributeBag<?> bag = Bags.singletonAttributeBag(
                StandardDatatypes.STRING,
                new StringValue(entry.getValue())
            );
            requestBuilder.putNamedAttributeIfAbsent(fqn, bag);
        }
    }
}
