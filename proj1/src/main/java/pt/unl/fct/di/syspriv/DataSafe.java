package pt.unl.fct.di.syspriv;

import pt.unl.fct.di.syspriv.entities.*;
import pt.unl.fct.di.syspriv.gdpr.GDPRHandler;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class DataSafe {

    private static final int HEALTH_DATA = 5;
    private static final int BACK = 6;

    private static final Logger logger = LoggerFactory.getLogger(DataSafe.class);
    private final Hibernate hibernate = Hibernate.getInstance();
    private final XACMLPDP pdp;
    private Object loggedInPerson;

    public DataSafe() throws Exception {
        this.pdp = new XACMLPDP();
        logger.info("DataSafe instance initialized.");
    }

    public static void main(String[] args) {
        try {
            DataSafe app = new DataSafe();
            app.run();

        } catch (Exception e) {
            logger.error("Failed to initialize application: ", e);
           // e.printStackTrace();
        }
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

        if (!login(scanner)) {
            logger.error("Login failed. Exiting...");
            return;
        }
        displayWelcome();

        boolean running = true;
        while (running) {

            System.out.println("\n=== What would you like to do? ===");
            System.out.println("1. View hospital data\n2. My data rights (GDPR request)\n3. Exit");
            System.out.print("Select an option: ");

            int option = readInt(scanner);
            switch (option) {
                case 1 -> handleDataAccessMenu(scanner);
                case 2 -> handleGdprRequestMenu(scanner);
                case 3 -> {
                    System.out.println("Exiting...");
                    running = false;
                }
                default -> System.out.println("Invalid selection. Please try again.");
            }
        }
        scanner.close();
    }

    //--- LOG-IN and Welcome ----
    private boolean login(Scanner scanner) {
        System.out.println("Are you an employee or patient?");
        System.out.println("1. Employee");
        System.out.println("2. Patient");
        System.out.print("Select an option: ");

        String choice = scanner.nextLine();

        System.out.print("Enter your name to login: ");
        String loginName = scanner.nextLine().trim();

        return switch (choice) {
            case "1" -> loginEmployee(loginName);
            case "2" -> loginPatient(loginName);
            default -> {
                System.out.println("Invalid option.");
                yield false;
            }
        };
    }

    private boolean loginEmployee(String loginName){
        List<Employee> employees = hibernate.jpql("FROM Employee", Employee.class);
        for (Employee e : employees) {
            if (e.getName().equalsIgnoreCase(loginName)) {
                loggedInPerson = e;
                return true;
            }
        }
        System.out.println("Employee not found.");
        return false;
    }

    private boolean loginPatient (String loginName){
        List<Patient> patients = hibernate.jpql("FROM Patient", Patient.class);
        for (Patient p : patients) {
            if (p.getName().equalsIgnoreCase(loginName)) {
                loggedInPerson = p;
                return true;
            }
        }
        System.out.println("Patient not found.");
        return false;
    }

    private void displayWelcome() {
        if (loggedInPerson instanceof Employee emp) {
            System.out.println("Welcome, " + emp.getName() + " (" + emp.getRole() + ")!");
        } else if (loggedInPerson instanceof Patient pat) {
            System.out.println("Welcome, " + pat.getName() + "!");
        } else {
            System.out.println("Welcome!");
        }
    }

    // --- Main Menu Prompt 1 ----
    private String promptContext(Scanner scanner) {
        System.out.println("\n=== Select the context of this access ===");
        System.out.println("1. Billing and Payments\n2. Scheduling\n3. Check-in\n4. Hiring\n5. Appointment\n6. Emergency");
        System.out.print("Select an option: ");

        int choice = readInt(scanner);
        return switch (choice) {
            case 1 -> "Billing and Payments";
            case 2 -> "Scheduling";
            case 3 -> "Check-in";
            case 4 -> "Hiring";
            case 5 -> "Appointment";
            case 6 -> "Emergency";
            default -> null;
        };
    }

    // --- Main Menu Prompt 2 ----
    private int promptTableSelection(Scanner scanner) {
        System.out.println("\n=== Available tables to request data from ===");
        System.out.println("1. Employees\n2. Patients\n3. Appointments\n4. Payments\n5. Health Data (Anonymized Dataset Export)\n6. Back");
        System.out.print("Select an option: ");

        return readInt(scanner);
    }

    private Long promptName(int tableChoice, Scanner scanner) {
        System.out.print("Please enter the name of the person you'd like to access: ");
        String name = scanner.nextLine().trim();

        if (tableChoice == 1) {
            for (Employee e : hibernate.jpql("FROM Employee", Employee.class)) {
                if (e.getName().equalsIgnoreCase(name)) return e.getId();
            }
        } else if (tableChoice == 2) {
            for (Patient p : hibernate.jpql("FROM Patient", Patient.class)) {
                if (p.getName().equalsIgnoreCase(name)) return p.getId();
            }
        }
        return null;
    }

    private Long promptId(Scanner scanner) {
        System.out.print("Please enter the ID of the row you'd like to access: ");
        String input = scanner.nextLine();
        try {
            return Long.valueOf(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private Long handleAccessIdOrName(int tablechoice, Scanner scanner) {
        System.out.println("\n=== How would you like to access? ===");
        System.out.println("1. Access by name\n2. Access by ID");
        System.out.print("Select an option: ");

        int option = readInt(scanner);
        return switch(option){
            case 1 -> promptName(tablechoice, scanner);
            case 2 -> promptId(scanner);
            default -> null;
        };
    }

    // --- Main Menu Prompt 3 ----
    private String promptLawfulBasis(Scanner scanner) {
        System.out.println("\n=== Select the documented lawful basis for this access ===");
        System.out.println("1. Consent\n2. Contract\n3. Legal obligation\n4. Vital interests\n5. Public task\n6. Legitimate interests\n7. Cancel");
        System.out.print("Select an option: ");

        int option = readInt(scanner);
        return switch (option) {
            case 1 -> "consent";
            case 2 -> "contract";
            case 3 -> "legal-obligation";
            case 4 -> "vital-interests";
            case 5 -> "public-task";
            case 6 -> "legitimate-interests";
            default -> null;
        };
    }

    // --- Main Menu (main) ----
    private void handleDataAccessMenu(Scanner scanner) {
        String context = promptContext(scanner);
        if (context == null) {
            System.out.println("Invalid context. Please try again.");
            return;
        }

        int tableChoice = promptTableSelection(scanner);
        if (tableChoice == BACK) {
            return;
        }
        else if (tableChoice < 1 || tableChoice > BACK) {
            System.out.println("Invalid choice.");
            return;
        }

        Long id = null;
        if (tableChoice == 1 || tableChoice == 2) {
            id = handleAccessIdOrName(tableChoice, scanner);
        } else if (tableChoice != HEALTH_DATA) {
            id = promptId(scanner);
        }
        if (id == null && tableChoice != HEALTH_DATA) {
            System.out.println("Invalid ID or name. Please try again.");
            return;
        }

        String lawfulBasis = promptLawfulBasis(scanner);
        if (lawfulBasis == null) {
            System.out.println("Access cancelled: select a valid lawful basis.");
            return;
        }

        handleDataAccess(context, tableChoice, id, lawfulBasis, scanner);
    }


    private void handleGdprRequestMenu(Scanner scanner) {
        System.out.println("\n=== Select a GDPR request type ===");
        System.out.println("1. Access (Export my data)\n2. Erasure (anonymize my data)\n3. Cancel");
        System.out.print("Select an option: ");

        String requestType = switch (scanner.nextLine()) {
            case "1" -> "ACCESS";
            case "2" -> "ERASURE";
            default -> null;
        };
        if (requestType == null) {
            System.out.println("Request cancelled or invalid selection.");
            return;
        }

        String lawfulBasis = "ACCESS".equals(requestType) ? promptLawfulBasis(scanner) : null;
        if ("ACCESS".equals(requestType) && lawfulBasis == null) {
            System.out.println("Access request cancelled: select a valid lawful basis.");
            return;
        }

        try {
            String exportJson = new GDPRHandler(hibernate, loggedInPerson, lawfulBasis).handleRequest(requestType, pdp);
            if ("ACCESS".equals(requestType)) {
                java.nio.file.Files.writeString(java.nio.file.Path.of("export.json"), exportJson);
                System.out.println(exportJson);
                System.out.println("File exported as export.json");
            } else {
                System.out.println("GDPR erasure request completed.");
            }
        } catch (Exception e) {
            logger.error("Could not complete GDPR " + requestType + " request: " + e.getMessage(), e);
            System.out.println("Could not complete the GDPR request.");
        }
    }


    private void handleDataAccess(String context, int tableChoice, Long id, String lawfulBasis, Scanner scanner) {
        Class<?> entityClass;
        String entityName;

        if (tableChoice == HEALTH_DATA) {
            handleHealthDataAccess(context, lawfulBasis, scanner);
            return;
        }

        switch (tableChoice) {
            case 1:
                entityClass = Employee.class;
                entityName = "Employee";
                break;
            case 2:
                entityClass = Patient.class;
                entityName = "Patient";
                break;
            case 3:
                entityClass = Appointment.class;
                entityName = "Appointment";
                break;
            case 4:
                entityClass = Payment.class;
                entityName = "Payment";
                break;
            default:
                System.out.println("Invalid table selection.");
                return;
        }

        try {
            logger.info("Fetching from " + entityName + " ID " + id);
            Object result = hibernate.get(entityClass, id);

            if (result == null) {
                logger.error("No " + entityName + " found with ID: " + id);
                return;
            }

            boolean allowed = pdp.checkAccessGDPR(loggedInPerson, result, "read", lawfulBasis, context);

            if (allowed) {
                System.out.println("Access Granted!");
                System.out.println(result); //Added to sho result on the prompt
            } else {
                System.out.println("Access Denied: You do not have permission to view " + entityName + " data.");
            }

        } catch (Exception e) {
            logger.error("Error accessing data: " + e);
            //e.printStackTrace();
        }
    }

    private void handleHealthDataAccess(String context, String lawfulBasis, Scanner scanner) {

        try {
            System.out.print("Enter 'k' value for k-anonymity (e.g., 2): ");
            int k = Integer.parseInt(scanner.nextLine());

            System.out.print("Enter 't' value for t-closeness (e.g., 0.2): ");
            float t = Float.parseFloat(scanner.nextLine());

            List<HealthData> allHealthData = hibernate.jpql("FROM HealthData", HealthData.class);
            List<AnonymousHealthData> dataToAnonymize = new java.util.ArrayList<>();

            for (HealthData hd : allHealthData) {
                boolean allowed = pdp.checkAccessGDPR(loggedInPerson, hd, "read", lawfulBasis, context);
                
                if (allowed) {
                    Patient patient = (Patient) hibernate.get(Patient.class, hd.getPatientId());
                    dataToAnonymize.add(new AnonymousHealthData(hd.getId(), LocalDate.parse(patient.getBirthdate().substring(0, 10)).getYear(), patient.getAddress(), hd.getGlucoseLevel(), hd.getMeasurementDate()));
                }
            }

            if (dataToAnonymize.isEmpty()) {
                System.out.println("Access Denied or No Data: No health records matched the required research consent policy.");
                return;
            }

            KAnonymityTool<AnonymousHealthData> tool = new KAnonymityTool<>(k, t);
            List<AnonymousHealthData> anonymizedData = tool.runTCloseMondrain(dataToAnonymize);

            boolean isSafe = tool.isKAnonymous(anonymizedData);
            System.out.println("\nFinal Dataset K-Anonymous Status: " + (isSafe ? "PASSED" : "FAILED"));

            System.out.println("\n=== Anonymized Health Data Export ===");
            for (AnonymousHealthData data : anonymizedData) {
                System.out.println(data.getQuasiIdentifiers() + " -> Sensitive Value: " + data.getSensitiveValue());
            }

        } catch (Exception e) {
            logger.error("Error generating anonymized health data: " + e);
            e.printStackTrace();
        }
    }

    // --- AUX ----
    private int readInt(Scanner scanner) {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
