package pt.unl.fct.di.syspriv.util;

import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

public interface Exportable {

    ExportEntity export(XACMLPDP pdp);

}
