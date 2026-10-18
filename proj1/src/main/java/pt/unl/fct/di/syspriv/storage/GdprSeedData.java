package pt.unl.fct.di.syspriv.storage;

import pt.unl.fct.di.syspriv.entities.ConsentRecord;
import pt.unl.fct.di.syspriv.entities.Employee;
import pt.unl.fct.di.syspriv.entities.GdprRequest;
import pt.unl.fct.di.syspriv.entities.Patient;

import java.sql.Date;
import java.time.LocalDateTime;

public class GdprSeedData {

    public static void main(String[] args) {
        //seed();
        //reseed();
        seedStaff();
    }

    public static void reseed() {
        Hibernate db = Hibernate.getInstance();

        var old = db.jpql("from Patient p where p.name like 'GDPR Test%'", Patient.class);
        if (!old.isEmpty()) {
            db.delete(old.toArray());
        }
        seed();
    }

    public static void seed() {
        Hibernate db = Hibernate.getInstance();

        // não voltar a inserir se já correste isto
        var existing = db.jpql("from Patient p where p.name like 'GDPR Test%'", Patient.class);
        if (!existing.isEmpty()) {
            System.out.println("Seed GDPR já existe, nada a fazer.");
            return;
        }

        // um médico existente como primaryDoctor
        var employees = db.jpql("from Employee", Employee.class);
        Employee doctor = employees.isEmpty() ? null : employees.get(0);

        // 1. marketing com consentimento ativo
        Patient p1 = newPatient("GDPR Test Marketing", "Rua A, Lisboa", doctor);
        ConsentRecord c = new ConsentRecord();
        c.setPatientSubject(p1);
        c.setSubjectType("PATIENT");
        c.setPurpose("marketing");
        c.setGranted(true);
        c.setGrantedAt(LocalDateTime.now().minusDays(10));
        c.setWithdrawnAt(null);
        c.setPolicyVersion("v1");
        c.setSource("web");
        p1.getConsentRecords().add(c);

        // 2. pedido de apagamento
        Patient p2 = newPatient("GDPR Test Erasure", "Rua B, Porto", doctor);
        p2.setErasureRequested(true);
        GdprRequest r = new GdprRequest();
        r.setPatientSubject(p2);
        r.setSubjectType("PATIENT");
        r.setRequestType("ERASURE");
        r.setRequestedAt(LocalDateTime.now());
        r.setStatus("PENDING");
        p2.getGdprRequests().add(r);

        // 3. retenção expirada
        Patient p3 = newPatient("GDPR Test Retention", "Rua C, Faro", doctor);
        p3.setRetentionUntil(LocalDateTime.now().minusDays(30));

        db.persist(p1, p2, p3);
        System.out.println("Seed GDPR inserida.");
    }

    private static Patient newPatient(String name, String address, Employee doctor) {
        return new Patient(name, "1990-01-01 00:00:00", "TestInsurance", address, doctor);
    }

    public static void seedStaff() {
        Hibernate db = Hibernate.getInstance();

        var existing = db.jpql("from Employee e where e.name like 'GDPR Test%'", Employee.class);
        if (!existing.isEmpty()) {
            System.out.println("Staff de teste já existe, nada a fazer.");
            return;
        }

        Employee dpo = new Employee("GDPR Test DPO", "DPO", "Compliance", false);
        Employee auditor = new Employee("GDPR Test Auditor", "Auditor", "Compliance", false);
        db.persist(dpo, auditor);
        System.out.println("Staff de teste inserido.");
    }
}