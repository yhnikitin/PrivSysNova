package pt.unl.fct.di.syspriv.xacml;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ow2.authzforce.core.pdp.api.AttributeFqn;
import org.ow2.authzforce.core.pdp.api.AttributeFqns;
import org.ow2.authzforce.core.pdp.api.DecisionRequestBuilder;
import org.ow2.authzforce.core.pdp.api.DecisionResult;
import org.ow2.authzforce.core.pdp.api.value.AttributeBag;
import org.ow2.authzforce.core.pdp.api.value.Bags;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.StringValue;
import org.ow2.authzforce.core.pdp.impl.BasePdpEngine;
import org.ow2.authzforce.core.pdp.impl.PdpEngineConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GDPRPolicyTest {

    private static final String XACML_NS = "urn:oasis:names:tc:xacml:3.0:core:schema:wd-17";
    private static final String SUBJECT = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject";
    private static final String RESOURCE = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource";
    private static final String ACTION = "urn:oasis:names:tc:xacml:3.0:attribute-category:action";
    private static BasePdpEngine pdp;

    @BeforeAll
    static void loadGdprPoliciesOnly() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document source;
        try (InputStream stream = GDPRPolicyTest.class.getClassLoader().getResourceAsStream("ci_policy.xml")) {
            if (stream == null) {
                throw new IllegalStateException("ci_policy.xml was not found on the test classpath");
            }
            source = factory.newDocumentBuilder().parse(new InputSource(stream));
        }

        Element gdprSet = findGdprPolicySet(source);
        Document isolatedDocument = factory.newDocumentBuilder().newDocument();
        Element isolatedSet = (Element) isolatedDocument.importNode(gdprSet, true);
        isolatedDocument.appendChild(isolatedSet);
        isolatedSet.setAttribute("PolicySetId", "GDPRPolicyTestSet");
        isolatedSet.appendChild(createPermitFallback(isolatedDocument));

        Path directory = Files.createTempDirectory("gdpr-policy-tests");
        Path policyPath = directory.resolve("gdpr-policy.xml");
        Path configurationPath = directory.resolve("pdp.xml");
        TransformerFactory.newInstance().newTransformer().transform(
                new DOMSource(isolatedDocument), new StreamResult(policyPath.toFile()));
        Files.writeString(configurationPath, pdpConfiguration(policyPath));

        PdpEngineConfiguration configuration = PdpEngineConfiguration.getInstance(configurationPath.toString());
        pdp = new BasePdpEngine(configuration);
    }

    @Test
    void permitsMarketingWhenConsentIsGrantedAndNotWithdrawn() throws Exception {
        assertDecision(DecisionType.PERMIT, request("Patient", "marketing", null,
                true, false, false, false, false, "personal-data"));
    }

    @Test
    void deniesMarketingWhenConsentIsWithdrawn() throws Exception {
        assertDecision(DecisionType.DENY, request("Patient", "marketing", null,
                true, true, false, false, false, "personal-data"));
    }

    @Test
    void deniesMarketingWhenConsentWasNeverGranted() throws Exception {
        assertDecision(DecisionType.DENY, request("Patient", "marketing", null,
                false, false, false, false, false, "personal-data"));
    }

    @Test
    void deniesDoctorAccessDuringErasure() throws Exception {
        assertDecision(DecisionType.DENY, request("Doctor", "read", "legal-obligation",
                false, false, true, false, true, "personal-data"));
    }

    @Test
    void doesNotDenyDpoDuringErasure() throws Exception {
        assertDecision(DecisionType.PERMIT, request("DPO", "read", "legal-obligation",
                false, false, true, false, true, "personal-data"));
    }

    @Test
    void deniesDoctorAccessAfterRetentionExpires() throws Exception {
        assertDecision(DecisionType.DENY, request("Doctor", "read", "legal-obligation",
                false, false, false, true, true, "personal-data"));
    }

    @Test
    void doesNotDenyAuditorAfterRetentionExpires() throws Exception {
        assertDecision(DecisionType.PERMIT, request("Auditor", "read", "legal-obligation",
                false, false, false, true, true, "personal-data"));
    }

    @Test
    void healthDataRequiresResearchConsent() throws Exception {
        assertDecision(DecisionType.DENY, request("Doctor", "read", "legal-obligation",
                false, false, false, false, false, "health-data"));
        assertDecision(DecisionType.PERMIT, request("Doctor", "read", "legal-obligation",
                false, false, false, false, true, "health-data"));
    }

    private static Map<String, Map<String, String>> request(String role, String action, String lawfulBasis,
            boolean marketingConsent, boolean marketingWithdrawn, boolean erasureRequested,
            boolean retentionExpired, boolean researchConsent, String dataCategory) {
        Map<String, Map<String, String>> attributes = new HashMap<>();
        Map<String, String> subject = new HashMap<>();
        subject.put("role", role);
        subject.put("consent-marketing", Boolean.toString(marketingConsent));
        subject.put("consent-withdrawn", Boolean.toString(marketingWithdrawn));
        subject.put("erasure-requested", Boolean.toString(erasureRequested));
        subject.put("retention-expired", Boolean.toString(retentionExpired));
        subject.put("consent-research", Boolean.toString(researchConsent));
        if (lawfulBasis != null) {
            subject.put("lawful-basis", lawfulBasis);
        }
        attributes.put(SUBJECT, subject);
        attributes.put(RESOURCE, Map.of("data-category", dataCategory));
        attributes.put(ACTION, Map.of("action-id", action));
        return attributes;
    }

    private static void assertDecision(DecisionType expected,
            Map<String, Map<String, String>> categories) throws Exception {
        DecisionRequestBuilder<?> builder = pdp.newRequestBuilder(-1, -1);
        for (Map.Entry<String, Map<String, String>> category : categories.entrySet()) {
            for (Map.Entry<String, String> attribute : category.getValue().entrySet()) {
                AttributeFqn fqn = AttributeFqns.newInstance(
                        category.getKey(), Optional.empty(), attribute.getKey());
                AttributeBag<?> bag = Bags.singletonAttributeBag(
                        StandardDatatypes.STRING, new StringValue(attribute.getValue()));
                builder.putNamedAttributeIfAbsent(fqn, bag);
            }
        }
        DecisionResult result = pdp.evaluate(builder.build(true));
        assertEquals(expected, result.getDecision());
    }

    private static Element findGdprPolicySet(Document document) {
        NodeList policySets = document.getElementsByTagNameNS(XACML_NS, "PolicySet");
        for (int index = 0; index < policySets.getLength(); index++) {
            Element policySet = (Element) policySets.item(index);
            if ("GDPRPolicySet".equals(policySet.getAttribute("PolicySetId"))) {
                return policySet;
            }
        }
        throw new IllegalStateException("GDPRPolicySet was not found in ci_policy.xml");
    }

    private static Element createPermitFallback(Document document) {
        Element policy = document.createElementNS(XACML_NS, "Policy");
        policy.setAttribute("PolicyId", "GDPRPolicyTestPermitFallback");
        policy.setAttribute("Version", "1.0");
        policy.setAttribute("RuleCombiningAlgId",
                "urn:oasis:names:tc:xacml:1.0:rule-combining-algorithm:first-applicable");
        policy.appendChild(document.createElementNS(XACML_NS, "Target"));
        Element rule = document.createElementNS(XACML_NS, "Rule");
        rule.setAttribute("RuleId", "PermitIfNoGdprRuleDenied");
        rule.setAttribute("Effect", "Permit");
        policy.appendChild(rule);
        return policy;
    }

    private static String pdpConfiguration(Path policyPath) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<pdp xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns=\"http://authzforce.github.io/core/xmlns/pdp/8\" version=\"8.1\">"
                + "<policyProvider id=\"testPolicyProvider\" xsi:type=\"StaticPolicyProvider\">"
                + "<policyLocation>" + policyPath.toUri() + "</policyLocation>"
                + "</policyProvider></pdp>";
    }
}
