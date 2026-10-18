package pt.unl.fct.di.syspriv.storage;

import pt.unl.fct.di.syspriv.entities.ConsentRecord;
import pt.unl.fct.di.syspriv.entities.Patient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public class CsvImporter {

    public static void main(String[] args) {
        run();
        System.out.println("Import done.");

        var h = Hibernate.getInstance();
        for (Patient p : h.jpql("FROM Patient", Patient.class)) {
            ConsentRecord c = new ConsentRecord();
            c.setPatientSubject(p);
            c.setSubjectType("PATIENT");
            c.setPurpose("research");
            c.setGranted(p.getId() % 2 == 0);
            c.setGrantedAt(java.time.LocalDateTime.now());
            h.persist(c);
        }
    }

    public static void run() {
        Hibernate.getInstance().execute(s -> {
            // limpar tudo (filhos primeiro)
            for (String e : List.of("GdprRequest", "ConsentRecord", "HealthData",
                    "Appointment", "Payment", "Patient", "Employee"))
                s.createMutationQuery("delete from " + e).executeUpdate();

            for (String[] r : read("csv/employee.csv"))
                s.createNativeMutationQuery(
                                "insert into employees (id, name, role, department, isAdmin) "
                                        + "values (:id, :name, :role, :dep, :admin)")
                        .setParameter("id", Long.valueOf(r[0]))
                        .setParameter("name", r[1])
                        .setParameter("role", r[2])
                        .setParameter("dep", r[3])
                        .setParameter("admin", Boolean.parseBoolean(r[4]))
                        .executeUpdate();

            for (String[] r : read("csv/patient.csv"))
                s.createNativeMutationQuery(
                                "insert into patients (id, name, birthdate, insurance, address, primaryDoctor_id) "
                                        + "values (:id, :name, :bd, :ins, :addr, :doc)")
                        .setParameter("id", Long.valueOf(r[0]))
                        .setParameter("name", r[1])
                        .setParameter("bd", r[2])
                        .setParameter("ins", r[3])
                        .setParameter("addr", r[4])
                        .setParameter("doc", Long.valueOf(r[5]))
                        .executeUpdate();

            for (String[] r : read("csv/health_data.csv")) {
                Date date = Date.from(LocalDate.parse(r[3]).atStartOfDay(ZoneId.systemDefault()).toInstant());
                s.createNativeMutationQuery(
                                "insert into health_data (id, patientId, glucoseLevel, measurementDate) "
                                        + "values (:id, :pid, :g, :d)")
                        .setParameter("id", Long.valueOf(r[0]))
                        .setParameter("pid", Long.valueOf(r[1]))
                        .setParameter("g", Integer.valueOf(r[2]))
                        .setParameter("d", date)
                        .executeUpdate();
            }
        });


    }

    private static List<String[]> read(String file) {
        try (var in = CsvImporter.class.getClassLoader().getResourceAsStream(file);
             var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return br.lines().filter(l -> !l.isBlank()).map(CsvImporter::split).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // separa por vírgulas, respeitando aspas
    private static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) { out.add(sb.toString().trim()); sb.setLength(0); }
            else sb.append(c);
        }
        out.add(sb.toString().trim());
        return out.toArray(new String[0]);
    }
}