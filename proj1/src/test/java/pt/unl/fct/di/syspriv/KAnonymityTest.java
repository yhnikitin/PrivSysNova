package pt.unl.fct.di.syspriv;
import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.*;
import pt.unl.fct.di.syspriv.entities.AnonymousHealthData;
import pt.unl.fct.di.syspriv.entities.HealthData;
import pt.unl.fct.di.syspriv.entities.KAnonymityTool;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.storage.CsvImporter;
import pt.unl.fct.di.syspriv.storage.Hibernate;

class KAnonymityTest {

    private static final int K = 3;
    private static final float T = 0.2f;

    // Usa a BD de teste (src/test/resources/hibernate.cfg.xml) e importa os CSV nela
    @BeforeAll
    static void loadData() {
        CsvImporter.run();
    }

    // --- Teste 1: isKAnonymous ---
    @Test
    void isKAnonymous_acceptsKAnonymousData() {
        var tool = new KAnonymityTool<AnonymousHealthData>(2, T);
        List<AnonymousHealthData> data = List.of(
                rec(1L, 1960, "Oak St, Springfield, IL 62701", 100),
                rec(2L, 1960, "Elm St, Springfield, IL 62701", 150),
                rec(3L, 1980, "Pine St, Chicago, IL 60614", 90),
                rec(4L, 1980, "Maple St, Chicago, IL 60614", 120));
        assertTrue(tool.isKAnonymous(data));
    }

    @Test
    void isKAnonymous_rejectsUniqueRecord() {
        var tool = new KAnonymityTool<AnonymousHealthData>(2, T);
        List<AnonymousHealthData> data = List.of(
                rec(1L, 1960, "Oak St, Springfield, IL 62701", 100),
                rec(2L, 1960, "Elm St, Springfield, IL 62701", 150),
                rec(3L, 1980, "Pine St, Chicago, IL 60614", 90));
        assertFalse(tool.isKAnonymous(data));
    }

    // --- Teste 2: runMondrain ---
    @Test
    void runMondrain_resultIsKAnonymous() {
        var tool = new KAnonymityTool<AnonymousHealthData>(K, T);
        List<AnonymousHealthData> data = loadFromDb();

        List<AnonymousHealthData> result = tool.runMondrain(data);

        assertFalse(data.isEmpty());
        assertEquals(data.size(), result.size());
        assertTrue(tool.isKAnonymous(result));
    }

    // --- Teste 3: runTCloseMondrain ---
    @Test
    void runTCloseMondrain_resultIsKAnonymousAndTClose() {
        var tool = new KAnonymityTool<AnonymousHealthData>(K, T);
        List<AnonymousHealthData> data = loadFromDb();

        List<AnonymousHealthData> result = tool.runTCloseMondrain(data);

        assertEquals(data.size(), result.size());
        assertTrue(tool.isKAnonymous(result));

        // cada grupo anonimizado tem de estar a distância <= t da distribuição total
        var groups = result.stream()
                .collect(Collectors.groupingBy(AnonymousHealthData::getQuasiIdentifiers));
        for (var group : groups.values())
            assertTrue(tool.isTClose(group, result));
    }

    @Test
    void runTCloseMondrianTriesNextQiWhenLargestRangeCannotSplit() {
        var tool = new KAnonymityTool<SplitTestRecord>(2, 0.2f);
        List<SplitTestRecord> data = List.of(
                new SplitTestRecord(0, 0, 0),
                new SplitTestRecord(0, 0, 0),
                new SplitTestRecord(0, 0, 1),
                new SplitTestRecord(0, 1, 0),
                new SplitTestRecord(0, 1, 1),
                new SplitTestRecord(10, 1, 1));

        List<SplitTestRecord> result = tool.runTCloseMondrain(data);

        assertEquals(data.size(), result.size());
        assertTrue(tool.isKAnonymous(result));
        var groups = result.stream().collect(Collectors.groupingBy(SplitTestRecord::getQuasiIdentifiers));
        assertEquals(2, groups.size());
        for (var group : groups.values()) {
            assertTrue(tool.isTClose(group, result));
        }
    }

    private static final class SplitTestRecord implements pt.unl.fct.di.syspriv.entities.Anonymizable {
        private double widestQi;
        private double fallbackQi;
        private final double sensitiveValue;

        private SplitTestRecord(double widestQi, double fallbackQi, double sensitiveValue) {
            this.widestQi = widestQi;
            this.fallbackQi = fallbackQi;
            this.sensitiveValue = sensitiveValue;
        }

        @Override
        public Map<String, Object> getQuasiIdentifiers() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("widestQi", widestQi);
            values.put("fallbackQi", fallbackQi);
            return values;
        }

        @Override
        public void setQuasiIdentifiers(Map<String, Object> values) {
            widestQi = ((Number) values.get("widestQi")).doubleValue();
            fallbackQi = ((Number) values.get("fallbackQi")).doubleValue();
        }

        @Override
        public Object getSensitiveValue() {
            return sensitiveValue;
        }
    }

    // --- auxiliares ---
    private static AnonymousHealthData rec(long id, int birthYear, String address, int glucose) {
        Date date = Date.from(LocalDate.of(2024, 5, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        return new AnonymousHealthData(id, birthYear, address, glucose, date);
    }

    private static List<AnonymousHealthData> loadFromDb() {
        var h = Hibernate.getInstance();
        List<AnonymousHealthData> list = new ArrayList<>();
        for (HealthData hd : h.jpql("FROM HealthData", HealthData.class)) {
            Patient p = h.get(Patient.class, hd.getPatientId());
            list.add(new AnonymousHealthData(hd.getId(),
                    LocalDate.parse(p.getBirthdate().substring(0, 10)).getYear(),
                    p.getAddress(), hd.getGlucoseLevel(), hd.getMeasurementDate()));
        }
        return list;
    }
}