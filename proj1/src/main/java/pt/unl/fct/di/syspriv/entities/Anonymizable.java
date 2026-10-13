package pt.unl.fct.di.syspriv.entities;

import java.util.Map;

public interface Anonymizable {
    /** Returns the QI values as a map (name → value). */
    Map<String, Object> getQuasiIdentifiers();

    /** Replaces the QI values with generalized values for anonymized output. */
    void setQuasiIdentifiers(Map<String, Object> quasiIdentifiers);

    /** Returns the sensitive attribute value. */
    Object getSensitiveValue();
}
