/*
 Full ictEsys.java - Enrollment System with improved error trapping (complete file)

 Features:
 - Console-based enrollment system (register, login, admin panel).
 - Strong input validation: letters-only prompts for name fields, numeric-only for numbers.
 - Error trapping: clear messages, loops until valid input, file IO guarded, logging to error.log.
 - Admin panel lists registered students and can show details.
 - Landing banner centered (no version text).
 - Curricula subject lists included.

 Compile:
   javac ictEsys.java
 Run:
   java ictEsys
*/

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

class GAga {
    private static final String ERROR_LOG = "error.log";

    // Admin password for the admin panel (demo)
    private static final String ADMIN_PASSWORD = "admin123";

    // Pricing rules
    private static final double UNIT_RATE = 350.0;
    private static final double INSTALLMENT_FEE = 2000.0;
    private static final double MIN_DOWN_PERCENT = 0.20; // 20%
    private static final int MIN_INSTALL_MONTHS = 2;
    private static final int MAX_INSTALL_MONTHS = 6;

    // In-memory runtime stores
    private static final Map<String, String> credentials = new HashMap<>(); // email -> password
    private static final Map<String, StudentProfile> profiles = new HashMap<>(); // email -> profile

    // Validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9\\s+\\-()]*$");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern DECIMAL_NUMBER = Pattern.compile("^[0-9]+(\\.[0-9]+)?$");
    // Name allowed: letters (including accented ranges), spaces, hyphens, apostrophes, dots
    private static final Pattern NAME_ALLOWED = Pattern.compile("^[A-Za-zÀ-ÖØ-öø-ÿ'\\.\\-\\s]+$");

    public static void main(String[] args) {
        loadCredentials();
        showLandingArt();
        mainMenu();
    }

    // ===== MAIN MENU (guarded) =====
    private static void mainMenu() {
        while (true) {
            try {
                printHeader("ISABELA STATE UNIVERSITY - SACARIAS ENROLLMENT");
                System.out.println("1) Register (New Student)");
                System.out.println("2) Login");
                System.out.println("3) Admin Panel");
                System.out.println("4) Exit");
                String choice = promptNonEmpty("Choose an option (1-4): ");
                switch (choice) {
                    case "1":
                        registerFlow();
                        break;
                    case "2":
                        loginFlow();
                        break;
                    case "3":
                        adminPanelFlow();
                        break;
                    case "4":
                        System.out.println("Goodbye — thank you!");
                        return;
                    default:
                        showError("Invalid option. Enter 1, 2, 3 or 4.");
                }
            } catch (Throwable t) {
                logError("Unexpected error in main menu: " + t.getMessage(), t);
                System.out.println("[ERROR] An unexpected error occurred. Returning to main menu.");
            }
        }
    }

    // ===== CREDENTIALS LOAD / SAVE =====
    private static void loadCredentials() {
        File f = new File("users.txt");
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ln; int line = 0;
            while ((ln = br.readLine()) != null) {
                line++;
                ln = ln.trim();
                if (ln.isEmpty()) continue;
                String[] parts = ln.split("\\|", 2);
                if (parts.length != 2) {
                    logError("Malformed users.txt line " + line + ": " + ln, null);
                    continue;
                }
                String email = parts[0].toLowerCase().trim();
                String pwd = parts[1];
                if (!isValidEmail(email)) {
                    logError("Invalid email in users.txt line " + line + ": " + email, null);
                    continue;
                }
                // keep first occurrence if duplicates
                credentials.putIfAbsent(email, pwd);
            }
        } catch (IOException e) {
            logError("Failed to read users file: " + e.getMessage(), e);
            System.out.println("[WARNING] Could not read users file; continuing with no credentials loaded.");
        }
    }

    private static void saveCredential(String email, String password) throws IOException {
        if (email == null || password == null) throw new IOException("email or password is null");
        try (FileWriter fw = new FileWriter("users.txt", true)) {
            fw.write(email.toLowerCase() + "|" + password + System.lineSeparator());
            fw.flush();
            credentials.put(email.toLowerCase(), password);
        } catch (IOException e) {
            logError("Failed to save credential for " + email + ": " + e.getMessage(), e);
            throw e;
        }
    }

    // ===== REGISTRATION FLOW =====
    private static void registerFlow() {
        printHeader("START REGISTRATION");
        StudentProfile p = new StudentProfile();

        // PERSONAL DATA (alpha-only where appropriate)
        System.out.println("[PERSONAL DATA]");
        p.lastName = promptAlpha("Last name: ");
        p.firstName = promptAlpha("First name: ");
        p.middleName = promptAlphaOptional("Middle name (optional): ");
        p.extensionName = promptAlphaOptional("Extension (Jr./III) (optional): ");
        p.permanentAddress = promptNonEmpty("Permanent address: ");
        p.birthday = promptNonEmpty("Birthday (YYYY-MM-DD): ");
        p.gender = promptAlpha("Gender (e.g., Male/Female/Other): ");
        p.phoneNumber = promptPhoneOptional("Phone number (optional): ");
        p.religion = promptAlphaOptional("Religion (optional): ");

        // PARENTS / GUARDIAN
        System.out.println("\n[PARENTS / GUARDIAN]");
        p.fatherName = promptAlpha("Father's full name: ");
        p.fatherOccupation = promptOptional("Father's occupation (optional): ");
        p.fatherContact = promptPhoneOptional("Father's contact (optional): ");
        p.motherName = promptAlpha("Mother's full name: ");
        p.motherOccupation = promptOptional("Mother's occupation (optional): ");
        p.motherContact = promptPhoneOptional("Mother's contact (optional): ");
        p.guardianName = promptAlphaOptional("Guardian (if any): ");
        p.guardianContact = promptPhoneOptional("Guardian contact (if any): ");

        // EDUCATIONAL BACKGROUND
        System.out.println("\n[EDUCATIONAL BACKGROUND - ELEMENTARY]");
        p.elementarySchool = promptNonEmpty("School name: ");
        p.elementarySchoolAddress = promptNonEmpty("School address: ");
        p.elementaryInclusiveDates = promptOptional("Inclusive Dates of Attendance: ");
        p.elementaryDegreeUnits = promptOptional("Degree/Units: ");
        p.elementaryHonors = promptOptional("Honors Received: ");

        System.out.println("\n[EDUCATIONAL BACKGROUND - JUNIOR HIGH]");
        p.juniorHighSchool = promptNonEmpty("School name: ");
        p.juniorHighSchoolAddress = promptNonEmpty("School address: ");
        p.juniorHighInclusiveDates = promptOptional("Inclusive Dates of Attendance: ");
        p.juniorHighDegreeUnits = promptOptional("Degree/Units: ");
        p.juniorHighHonors = promptOptional("Honors Received: ");

        System.out.println("\n[EDUCATIONAL BACKGROUND - SENIOR HIGH]");
        p.seniorHighSchool = promptNonEmpty("School name: ");
        p.seniorHighSchoolAddress = promptNonEmpty("School address: ");
        p.seniorHighInclusiveDates = promptOptional("Inclusive Dates of Attendance: ");
        p.seniorHighDegreeUnits = promptOptional("Degree/Units: ");
        p.seniorHighHonors = promptOptional("Honors Received: ");

        // ADMISSION INFORMATION
        System.out.println("\n[ADMISSION INFORMATION]");
        p.lrn = promptDigitsOptional("Learner Reference Number (LRN) (optional) — digits only: ");
        p.level = promptOptional("Admission level (e.g., Undergraduate): ");

        // GWA fields - numeric only
        p.gwa11_1 = promptDoubleInRange("GWA Grade 11 1st sem (0 if N/A): ", 0, 100);
        p.gwa11_2 = promptDoubleInRange("GWA Grade 11 2nd sem (0 if N/A): ", 0, 100);
        p.gwa12_1 = promptDoubleInRange("GWA Grade 12 1st sem (0 if N/A): ", 0, 100);
        p.gwaAverage = (p.gwa11_1 + p.gwa11_2 + p.gwa12_1) / 3.0;
        System.out.printf("Computed GWA average: %.2f%n", p.gwaAverage);

        // SHS track selection (numeric)
        String[] tracks = {"STEM", "HUMSS", "ABM", "GAS", "TVL", "Arts & Design", "Sports"};
        for (int i = 0; i < tracks.length; i++) System.out.printf("%d) %s%n", i + 1, tracks[i]);
        int t = promptIntRange("Choose SHS Track (1-" + tracks.length + "): ", 1, tracks.length);
        p.shsTrack = tracks[t - 1];

        // Campus selection
        String[] campuses = {"Echague", "Cauayan", "Roxas", "Ilagan", "Jones", "Angadanan", "Cabagan", "San Mateo", "Santiago"};
        for (int i = 0; i < campuses.length; i++) System.out.printf("%d) %s%n", i + 1, campuses[i]);
        int c = promptIntRange("Choose ISU Campus (1-" + campuses.length + "): ", 1, campuses.length);
        p.campus = campuses[c - 1];

        // PROGRAM SELECTION
        System.out.println("\n[PROGRAM SELECTION]");
        Program[] progs = Program.values();
        for (int i = 0; i < progs.length; i++) System.out.printf("%d) %s%n", i + 1, progs[i].displayName);
        int pick = promptIntRange("Choose program (1-" + progs.length + "): ", 1, progs.length);
        p.program = progs[pick - 1];

        if (p.program == Program.BSIT) {
            System.out.println("BSIT Specializations:");
            System.out.println("1) Web & Mobile App Development");
            System.out.println("2) Network Systems");
            int sp = promptIntRange("Choose specialization (1-2): ", 1, 2);
            p.bsitSpecialization = sp == 1 ? "Web & Mobile App Development" : "Network Systems";
            System.out.println("Selected specialization: " + p.bsitSpecialization);
        } else {
            p.bsitSpecialization = "";
        }

        // ENROLLMENT YEAR & TERM
        System.out.println("\n[ENROLLMENT - Year & Term]");
        p.yearLevel = promptIntRange("Choose year level (1-4): ", 1, 4);

        boolean mid = false;
        // Try to detect midyear option by checking the curriculum for midyear
        try {
            // Assuming Program is an enum and has a method or field for midyear check
            // CurriculumUtil is undefined. We'll use a simple fallback or TODO marker.
            // If there is a method in Program to check for midyear, use it. Otherwise, set to false for now.
            if (p.program == Program.BSIT) {
                // TODO: Implement actual curriculum midyear logic if/when available.
                mid = "Web & Mobile App Development".equals(p.bsitSpecialization) || "Network Systems".equals(p.bsitSpecialization);
            } else {
                mid = false; // Only BSIT has midyear in this stub.
            }
        } catch (Exception e) {
            mid = false; // Fallback to no midyear on any error
        }

        System.out.println("Semester options:\n1) 1st Semester\n2) 2nd Semester");
        if (mid) System.out.println("3) Midyear");
        int maxSem = mid ? 3 : 2;
        p.semester = promptIntRange("Choose semester (1-" + maxSem + "): ", 1, maxSem);

        // SUBJECTS & UNITS
        p.subjects = getSubjectsFor(p.program, p.yearLevel, p.semester, p.bsitSpecialization);
        p.totalUnits = p.subjects.stream().mapToInt(su -> su.units).sum();
        p.tuition = (int) Math.round(p.totalUnits * UNIT_RATE);

        System.out.println("\n--- SUBJECTS & UNITS SUMMARY ---");
        System.out.printf("%-8s %-60s %s%n", "Code", "Subject", "Units");
        System.out.println("--------------------------------------------------------------------------------");
        for (Subject s : p.subjects) System.out.printf("%-8s %-60s %2d%n", s.code, s.name, s.units);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Total Units: " + p.totalUnits);
        System.out.printf("Tuition (PHP %.2f per unit): PHP %,.2f%n", UNIT_RATE, (double) p.tuition);

        // PAYMENT
        System.out.println("\n[PAYMENT]");
        System.out.println("1) Cash (full)");
        System.out.println("2) Installment (adds PHP " + (int) INSTALLMENT_FEE + " fee)");
        int pay = promptIntRange("Choose payment method (1-2): ", 1, 2);

        if (pay == 1) {
            p.paymentMethod = PaymentMethod.CASH;
            p.surcharge = 0.0;
            p.totalDue = p.tuition;
            p.amountPaid = promptDoubleMin("Enter payment amount (PHP): ", 0.0);
            p.balance = Math.max(0.0, p.totalDue - p.amountPaid);
            p.isEnrolled = p.amountPaid >= p.totalDue;
            if (p.isEnrolled) System.out.println("Payment sufficient — YOU ARE NOW ENROLLED. Congratulations!");
            else System.out.println("Payment insufficient — NOT ENROLLED yet. Remaining: PHP " + money(p.balance));
        } else {
            p.paymentMethod = PaymentMethod.INSTALLMENT;
            p.surcharge = INSTALLMENT_FEE;
            p.totalDue = p.tuition + p.surcharge;
            System.out.printf("Total due (tuition + fee): PHP %,.2f%n", p.totalDue);
            double minDown = p.totalDue * MIN_DOWN_PERCENT;
            System.out.printf("Minimum down (%.0f%%): PHP %,.2f%n", MIN_DOWN_PERCENT * 100, minDown);
            p.amountPaid = promptDoubleMin("Enter downpayment amount (PHP): ", 0.0);
            p.installmentMonths = promptIntRange("Number of installments (" + MIN_INSTALL_MONTHS + "-" + MAX_INSTALL_MONTHS + "): ", MIN_INSTALL_MONTHS, MAX_INSTALL_MONTHS);
            p.balance = Math.max(0.0, p.totalDue - p.amountPaid);
            if (p.amountPaid < minDown) {
                p.isEnrolled = false;
                System.out.println("Downpayment less than minimum — NOT ENROLLED.");
            } else if (p.amountPaid >= p.totalDue) {
                p.isEnrolled = true;
                p.monthlyDue = 0.0;
                p.balance = 0.0;
                System.out.println("Full payment covered — YOU ARE NOW ENROLLED. Congratulations!");
            } else {
                p.isEnrolled = false;
                p.monthlyDue = p.balance / p.installmentMonths;
                System.out.printf("Installment accepted. Remaining: PHP %,.2f. Monthly: PHP %,.2f for %d months.%n",
                        p.balance, p.monthlyDue, p.installmentMonths);
            }
        }

        // CREDENTIALS
        System.out.println("\n[CREATE ACCOUNT] (Email will be your username)");
        String email;
        while (true) {
            email = promptNonEmpty("Email: ").toLowerCase();
            if (!isValidEmail(email)) { showError("Invalid email format. Example: user@example.com"); continue; }
            if (credentials.containsKey(email)) { showError("Email already registered. Use another email or choose Login."); continue; }
            break;
        }
        String password = promptPasswordWithConfirmation("Create password (min 6 chars): ", "Confirm password: ");

        // Persist credentials
        try {
            saveCredential(email, password);
            System.out.println("Credentials saved.");
        } catch (IOException e) {
            logError("Failed to save credentials for " + email + ": " + e.getMessage(), e);
            showError("Failed to save credentials: " + e.getMessage());
        }

        // store profile in memory and save assessment file
        profiles.put(email, p);
        try {
            saveAssessmentFile(p, email);
            System.out.println("Assessment saved to file: " + determineAssessmentFilename(p, email));
        } catch (IOException e) {
            logError("Failed to save assessment for " + email + ": " + e.getMessage(), e);
            showError("Failed to save assessment file: " + e.getMessage());
        }

        System.out.println("\nRegistration completed. You can now Login from the main menu using your email & password.");
        pause();
    }

    // ===== LOGIN FLOW =====
    private static void loginFlow() {
        printHeader("LOGIN");
        String email = promptNonEmpty("Email: ").toLowerCase();
        String pwd = promptNonEmpty("Password: ");

        String stored = credentials.get(email);
        if (stored == null) { showError("No account found for that email."); return; }
        if (!stored.equals(pwd)) { showError("Incorrect password."); return; }
        System.out.println("Login successful. Welcome!");

        StudentProfile p = profiles.get(email);
        if (p != null) {
            printAssessment(p);
            return;
        }

        // otherwise try to read saved assessment file
        File f = getAssessmentFileForEmail(email);
        if (f != null) {
            System.out.println("\nFound assessment file: " + f.getName() + " — showing contents:\n");
            displayFileContents(f);
        } else {
            System.out.println("No assessment file found for this account (maybe you registered in a previous run).");
        }
    }

    // ===== ADMIN PANEL =====
    private static void adminPanelFlow() {
        printHeader("ADMIN PANEL");
        String pwd = promptOptional("Enter admin password (blank to cancel): ");
        if (pwd.isEmpty()) { System.out.println("Cancelled admin access."); return; }
        if (!pwd.equals(ADMIN_PASSWORD)) { showError("Incorrect admin password."); return; }
        System.out.println("Admin access granted.");

        List<String> emails = new ArrayList<>(credentials.keySet());
        Collections.sort(emails);

        while (true) {
            System.out.println("\nRegistered Students (" + emails.size() + "):");
            System.out.println("Idx  Email                           Student Name                     Program                         Enrolled");
            System.out.println("---------------------------------------------------------------------------------------------------------------");
            for (int i = 0; i < emails.size(); i++) {
                String em = emails.get(i);
                StudentProfile sp = profiles.get(em);
                String name = (sp != null) ? (sp.lastName + ", " + sp.firstName) : "N/A";
                String prog = (sp != null && sp.program != null) ? sp.program.displayName : "N/A";
                String enrolled = (sp != null) ? (sp.isEnrolled ? "YES" : "NO") : "N/A";
                System.out.printf("%-4d %-32s %-30s %-32s %-8s%n", i + 1, truncate(em, 32), truncate(name, 30), truncate(prog, 32), enrolled);
            }
            System.out.println("\nOptions: [number] View details  |  r Refresh  |  q Quit to main menu");
            String cmd = promptNonEmpty("Choice: ").toLowerCase();
            if (cmd.equals("q")) return;
            if (cmd.equals("r")) {
                emails = new ArrayList<>(credentials.keySet());
                Collections.sort(emails);
                continue;
            }
            if (cmd.matches("\\d+")) {
                int idx = safeParseInt(cmd, -1) - 1;
                if (idx < 0 || idx >= emails.size()) { showError("Invalid index."); continue; }
                String selectedEmail = emails.get(idx);
                System.out.println("\n--- DETAILS FOR: " + selectedEmail + " ---");
                StudentProfile sp = profiles.get(selectedEmail);
                if (sp != null) {
                    printAssessment(sp);
                } else {
                    File af = getAssessmentFileForEmail(selectedEmail);
                    if (af != null) {
                        System.out.println("Assessment file: " + af.getName() + "\n");
                        displayFileContents(af);
                    } else {
                        System.out.println("No in-memory profile or assessment file found for this user.");
                    }
                }
                System.out.println("\nPress Enter to return to admin list...");
                new java.util.Scanner(System.in).nextLine();
                continue;
            }
            showError("Unknown command.");
        }
    }

    // ===== ASSESSMENT FILE HELPERS =====
    private static File getAssessmentFileForEmail(String email) {
        if (email == null || email.isEmpty()) return null;
        String base = "Assessment_" + safeFileNameFromEmail(email);
        File f = findAssessmentFileStartingWith(base);
        if (f != null) return f;
        return findAssessmentFileByScanningContentsForEmail(email);
    }

    private static File findAssessmentFileStartingWith(String prefix) {
        File cwd = new File(".");
        File[] files = cwd.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith(prefix) && f.getName().endsWith(".txt")) return f;
        }
        return null;
    }

    private static File findAssessmentFileByScanningContentsForEmail(String email) {
        if (email == null || email.isEmpty()) return null;
        File cwd = new File(".");
        File[] files = cwd.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt") && name.toLowerCase().startsWith("assessment_"));
        if (files == null) return null;
        for (File f : files) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String ln;
                while ((ln = br.readLine()) != null) {
                    if (ln.toLowerCase().contains(email.toLowerCase())) return f;
                }
            } catch (IOException e) {
                logError("Scanning file failed: " + f.getName() + " - " + e.getMessage(), e);
            }
        }
        return null;
    }

    private static void displayFileContents(File f) {
        if (f == null) { showError("No file specified."); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ln;
            while ((ln = br.readLine()) != null) System.out.println(ln);
        } catch (IOException e) {
            showError("Failed to read file: " + e.getMessage());
            logError("Failed to read file " + f.getName() + ": " + e.getMessage(), e);
        }
    }

    // ===== SAVE ASSESSMENT =====
    private static void saveAssessmentFile(StudentProfile p, String email) throws IOException {
        if (p == null) throw new IOException("StudentProfile is null");
        String filename = determineAssessmentFilename(p, email);
        try (FileWriter fw = new FileWriter(filename)) {
            fw.write("SACARIAS - ASSESSMENT\n");
            fw.write("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n");
            fw.write("Student Name : " + p.lastName + ", " + p.firstName + (p.middleName.isEmpty() ? "" : " " + p.middleName) + "\n");
            if (!p.lrn.isEmpty()) fw.write("LRN          : " + p.lrn + "\n");
            fw.write("Email        : " + email + "\n");
            fw.write("Program      : " + (p.program == null ? "N/A" : p.program.displayName) + (p.bsitSpecialization.isEmpty() ? "" : " (" + p.bsitSpecialization + ")") + "\n");
            fw.write("Year/Term    : Year " + p.yearLevel + " - " + semLabel(p.semester) + "\n\n");
            fw.write("Subjects:\n");
            for (Subject s : p.subjects) fw.write(String.format("  %s - %s (%d units)%n", s.code, s.name, s.units));
            fw.write("\nTotal Units: " + p.totalUnits + "\n");
            fw.write(String.format("Tuition: PHP %,.2f%n", (double) p.tuition));
            if (p.paymentMethod == PaymentMethod.INSTALLMENT) {
                fw.write(String.format("Install Fee: PHP %,.2f%n", p.surcharge));
                fw.write(String.format("Total Due: PHP %,.2f%n", p.totalDue));
                fw.write(String.format("Amount Paid: PHP %,.2f%n", p.amountPaid));
                fw.write(String.format("Balance: PHP %,.2f%n", p.balance));
                fw.write(String.format("Monthly due (%d months): PHP %,.2f%n", p.installmentMonths, p.monthlyDue));
            } else {
                fw.write(String.format("Amount Paid: PHP %,.2f%n", p.amountPaid));
                fw.write(String.format("Balance: PHP %,.2f%n", p.balance));
            }
            fw.write("\nENROLLED: " + (p.isEnrolled ? "YES" : "NO") + "\n");
            fw.flush();
        } catch (IOException e) {
            logError("Failed to write assessment file " + filename + ": " + e.getMessage(), e);
            throw e;
        }
    }

    private static String determineAssessmentFilename(StudentProfile p, String email) {
        String safe;
        if (p.firstName != null && !p.firstName.trim().isEmpty() && p.lastName != null && !p.lastName.trim().isEmpty()) {
            safe = safeFileName(p.firstName + "_" + p.lastName);
        } else if (email != null && !email.trim().isEmpty()) {
            safe = safeFileNameFromEmail(email);
        } else {
            safe = "unknown_" + System.currentTimeMillis();
        }
        return "Assessment_" + safe + ".txt";
    }

    // ===== VALIDATION & PROMPTS =====

    // Required non-empty string
    private static String promptNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) {
                showError("Input cannot be empty. Please provide a value.");
                continue;
            }
            return ln;
        }
    }

    // Optional free-text
    private static String promptOptional(String prompt) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) return "";
            return ln.trim();
        }
    }

    // Accept only alphabetic (name-like) input; reject numbers/symbols (except allowed punctuation)
    private static String promptAlpha(String prompt) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) {
                showError("Input cannot be empty. Please enter letters only.");
                continue;
            }
            if (!NAME_ALLOWED.matcher(ln).matches()) {
                showError("Please use letters, spaces, apostrophes ('), hyphens (-) or periods (.) only. Numbers are not allowed.");
                continue;
            }
            return ln;
        }
    }

    // Optional alpha field (empty allowed)
    private static String promptAlphaOptional(String prompt) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) return "";
            if (!NAME_ALLOWED.matcher(ln).matches()) {
                showError("Please use letters, spaces, apostrophes ('), hyphens (-) or periods (.) only. Numbers are not allowed.");
                continue;
            }
            return ln;
        }
    }

    // Phone optional with allowed characters
    private static String promptPhoneOptional(String prompt) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) return "";
            if (!PHONE_PATTERN.matcher(ln).matches()) {
                showError("Phone may contain only digits, spaces, +, -, and parentheses. Example: +63 912-345-6789");
                continue;
            }
            return ln;
        }
    }

    // Digits-only optional prompt (LRN)
    private static String promptDigitsOptional(String prompt) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) return "";
            if (!DIGITS_ONLY.matcher(ln).matches()) {
                showError("Digits only. Leave blank if not available.");
                continue;
            }
            return ln;
        }
    }

    // Integer range prompt - rejects letters
    private static int promptIntRange(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) { showError("Input cannot be empty. Enter a number."); continue; }
            if (!ln.matches("-?\\d+")) { showError("Invalid number. Enter digits only (no letters)."); continue; }
            try {
                int v = Integer.parseInt(ln);
                if (v < min || v > max) { showError("Enter a number between " + min + " and " + max + "."); continue; }
                return v;
            } catch (NumberFormatException e) {
                showError("Number out of range. Try again.");
            }
        }
    }

    // Double in range - rejects letters
    private static double promptDoubleInRange(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) { showError("Input cannot be empty. Enter a number."); continue; }
            if (!DECIMAL_NUMBER.matcher(ln).matches()) { showError("Invalid number format. Use digits and optional decimal point."); continue; }
            try {
                double v = Double.parseDouble(ln);
                if (v < min || v > max) { showError("Enter a value between " + min + " and " + max + "."); continue; }
                return v;
            } catch (NumberFormatException e) {
                showError("Invalid number. Try again.");
            }
        }
    }

    // Double minimum - rejects letters
    private static double promptDoubleMin(String prompt, double min) {
        while (true) {
            System.out.print(prompt);
            String ln = null;
            try (Scanner sc = new Scanner(System.in)) {
                ln = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (ln == null) ln = "";
            ln = ln.trim();
            if (ln.isEmpty()) { showError("Input cannot be empty. Enter a numeric amount."); continue; }
            if (!DECIMAL_NUMBER.matcher(ln).matches()) { showError("Invalid amount. Use digits and optional decimal point."); continue; }
            try {
                double v = Double.parseDouble(ln);
                if (v < min) { showError("Enter an amount >= " + money(min)); continue; }
                return v;
            } catch (NumberFormatException e) {
                showError("Invalid amount. Try again.");
            }
        }
    }

    // Password creation/confirmation
    private static String promptPasswordWithConfirmation(String promptCreate, String promptConfirm) {
        while (true) {
            System.out.print(promptCreate);
            String pass = null;
            try (Scanner sc = new Scanner(System.in)) {
                pass = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (pass == null) pass = "";
            if (pass.length() < 6) { showError("Password must be at least 6 characters."); continue; }
            System.out.print(promptConfirm);
            String confirm = null;
            try (Scanner sc = new Scanner(System.in)) {
                confirm = sc.nextLine();
            } catch (Exception e) {
                showError("Error reading input. Please try again.");
                continue;
            }
            if (!pass.equals(confirm)) { showError("Passwords do not match. Try again."); continue; }
            return pass;
        }
    }

    // ===== UTILITIES =====
    private static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private static int safeParseInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static String money(double v) { return String.format("%,.2f", v); }

    private static String safeFileName(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static String safeFileNameFromEmail(String email) {
        if (email == null) return "unknown";
        return email.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static void logError(String message, Throwable t) {
        try (FileWriter fw = new FileWriter(ERROR_LOG, true)) {
            fw.write(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " - " + message + System.lineSeparator());
            if (t != null) {
                fw.write("  Exception: " + t.toString() + System.lineSeparator());
                for (StackTraceElement el : t.getStackTrace()) fw.write("    at " + el.toString() + System.lineSeparator());
            }
        } catch (IOException ignored) {}
    }

    private static void printHeader(String title) {
        System.out.println();
        System.out.println("===================================================");
        System.out.println("   " + title);
        System.out.println("===================================================");
    }

    private static void showError(String msg) { System.out.println("[ERROR] " + msg); }

    private static void pause() {
        System.out.println("\nPress Enter to continue...");
        new java.util.Scanner(System.in).nextLine();
    }

    private static String truncate(String s, int len) {
        if (s == null) return "";
        if (s.length() <= len) return s;
        return s.substring(0, len - 3) + "...";
    }

    // ===== PRINT ASSESSMENT =====
    private static void printAssessment(StudentProfile p) {
        printHeader("SACARIAS ASSESSMENT");
        System.out.println("Student Name : " + p.lastName + ", " + p.firstName + (p.middleName.isEmpty() ? "" : " " + p.middleName));
        if (!p.lrn.isEmpty()) System.out.println("LRN          : " + p.lrn);
        System.out.println("Program      : " + (p.program == null ? "N/A" : p.program.displayName) + (p.bsitSpecialization.isEmpty() ? "" : " (" + p.bsitSpecialization + ")"));
        System.out.println("Year/Term    : Year " + p.yearLevel + " - " + semLabel(p.semester));
        System.out.println("-------------------------------------------------------");
        System.out.printf("%-8s %-60s %s%n","Code","Subject","Units");
        System.out.println("-------------------------------------------------------");
        for (Subject s : p.subjects) System.out.printf("%-8s %-60s %2d%n", s.code, s.name, s.units);
        System.out.println("-------------------------------------------------------");
        System.out.println("Total Units  : " + p.totalUnits);
        System.out.printf("Tuition      : PHP %,.2f%n", (double) p.tuition);
        if (p.paymentMethod == PaymentMethod.INSTALLMENT) {
            System.out.printf("Install Fee  : PHP %,.2f%n", p.surcharge);
            System.out.printf("Total Due    : PHP %,.2f%n", p.totalDue);
            System.out.printf("Amount Paid  : PHP %,.2f%n", p.amountPaid);
            System.out.printf("Balance      : PHP %,.2f%n", p.balance);
            if (p.installmentMonths > 0) System.out.printf("Monthly Due  : PHP %,.2f (%d months)%n", p.monthlyDue, p.installmentMonths);
        } else {
            System.out.printf("Amount Paid  : PHP %,.2f%n", p.amountPaid);
            System.out.printf("Balance      : PHP %,.2f%n", p.balance);
        }
        System.out.println("ENROLLED: " + (p.isEnrolled ? "YES" : "NO"));
        System.out.println("=======================================================\n");
    }

    // ===== CURRICULA & DATA CLASSES =====
    private static String semLabel(int sem) {
        switch (sem) {
            case 1: return "1st Semester";
            case 2: return "2nd Semester";
            case 3: return "Midyear";
            default: return "Unknown";
        }
    }

    private static List<Subject> getSubjectsFor(Program program, int year, int sem, String bsitSpec) {
        if (program == null) return new ArrayList<>();
        switch (program) {
            case BSIT:
                if (bsitSpec != null && bsitSpec.toLowerCase().contains("web")) return subjectsBSITWMAD(year, sem);
                else return subjectsBSITNS(year, sem);
            case BSCS: return subjectsBSCS(year, sem);
            case BSDSA: return subjectsBSDSA(year, sem);
            case BSIS: return subjectsBSIS(year, sem);
            case BSMIT: return subjectsBSMIT(year, sem);
            case BSBLIS: return subjectsBSBLIS(year, sem);
            case MIT: return subjectsMIT(year, sem);
            default: return new ArrayList<>();
        }
    }

    private static class Subject { String code, name; int units; Subject(String c, String n, int u){code=c;name=n;units=u;} }
    private enum Program {
        BSIT("BSIT - Bachelor of Science in Information Technology"),
        BSMIT("BSMIT - Bachelor of Science in Multimedia & IT"),
        BSCS("BSCS - Bachelor of Science in Computer Science"),
        BSIS("BSIS - Bachelor of Science in Information Systems"),
        BSDSA("BSDSA - BS in Data Science & Analytics"),
        BSBLIS("BSBLIS - Bachelor of Library & Information Science"),
        MIT("MIT - Master in Information Technology");
        final String displayName;
        Program(String d){ displayName = d; }
    }

    private static class StudentProfile {
        // personal
        String lastName = "", firstName = "", middleName = "", extensionName = "";
        String permanentAddress = "", birthday = "", gender = "", phoneNumber = "", religion = "";
        // parents
        String fatherName = "", fatherOccupation = "", fatherContact = "";
        String motherName = "", motherOccupation = "", motherContact = "";
        String guardianName = "", guardianContact = "";
        // education
        String elementarySchool = "", elementarySchoolAddress = "", elementaryInclusiveDates = "", elementaryDegreeUnits = "", elementaryHonors = "";
        String juniorHighSchool = "", juniorHighSchoolAddress = "", juniorHighInclusiveDates = "", juniorHighDegreeUnits = "", juniorHighHonors = "";
        String seniorHighSchool = "", seniorHighSchoolAddress = "", seniorHighInclusiveDates = "", seniorHighDegreeUnits = "", seniorHighHonors = "";
        // admission
        String lrn = "", level = "", shsTrack = "", campus = "";
        double gwa11_1 = 0, gwa11_2 = 0, gwa12_1 = 0, gwaAverage = 0;
        Program program = null; String bsitSpecialization = "";
        // enrollment
        int yearLevel = 1, semester = 1;
        List<Subject> subjects = new ArrayList<>();
        int totalUnits = 0, tuition = 0;
        // payment
        PaymentMethod paymentMethod = PaymentMethod.CASH;
        double surcharge = 0, totalDue = 0, amountPaid = 0, balance = 0, monthlyDue = 0;
        int installmentMonths = 0;
        boolean isEnrolled = false;
    }

    private enum PaymentMethod { CASH, INSTALLMENT }

    // ===== CURRICULA IMPLEMENTATIONS =====
    // (Full subject lists; same as previous versions)
    private static List<Subject> subjectsBSITWMAD(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("GEC4","Purposive Communication",3));
            list.add(new Subject("GEC5","Art Appreciation",3));
            list.add(new Subject("ITINST1","Climate Change & DRRM",2));
            list.add(new Subject("ITGEE1","Health & Wellness Science",3));
            list.add(new Subject("ITGEE2","Foreign Language 1",3));
            list.add(new Subject("IT111","Introduction to Computing",3));
            list.add(new Subject("IT112","Computer Programming 1",3));
            list.add(new Subject("PE1","Physical Activity I",2));
            list.add(new Subject("NSTP1","NSTP 1",3));
        } else if (year==1 && sem==2) {
            list.add(new Subject("GEC1","Understanding the Self",3));
            list.add(new Subject("GEC2","Readings in Philippine History",3));
            list.add(new Subject("GEC3","Mathematics in the Modern World",3));
            list.add(new Subject("GEC7","Ethics",3));
            list.add(new Subject("IT121","Computer Programming 2",3));
            list.add(new Subject("IT122","Human Computer Interaction 1",3));
            list.add(new Subject("IT123","Discrete Mathematics",3));
            list.add(new Subject("PE2","Physical Activity II",2));
            list.add(new Subject("NSTP2","NSTP 2",3));
        } else if (year==2 && sem==1) {
            list.add(new Subject("GEC6","Science, Technology & Society",3));
            list.add(new Subject("GEC8","The Contemporary World",3));
            list.add(new Subject("ITINST2","Creative & Critical Thinking",2));
            list.add(new Subject("ITGEE3","Foreign Language 2",3));
            list.add(new Subject("IT211","Data Structures & Algorithms",3));
            list.add(new Subject("ITelec1","Platform Technologies",3));
            list.add(new Subject("ITelec2","Object Oriented Programming",3));
            list.add(new Subject("ITBPO1","Business Communication",3));
            list.add(new Subject("PE3","Physical Activity III",2));
        } else if (year==2 && sem==2) {
            list.add(new Subject("ITGEE4","The Entrepreneurial Mind",3));
            list.add(new Subject("GEC9","Life & Works of Rizal",3));
            list.add(new Subject("IT221","Information Management",3));
            list.add(new Subject("IT222","Networking 1",3));
            list.add(new Subject("IT223","Quantitative Methods",3));
            list.add(new Subject("IT224","Integrative Programming & Tech",3));
            list.add(new Subject("IT225","Accounting for IT",3));
            list.add(new Subject("ITAPPDEV1","Fundamentals of Mobile Tech",3));
            list.add(new Subject("PE4","Physical Activity IV",2));
        } else if (year==2 && sem==3) {
            list.add(new Subject("IT226","Applications Dev & Emerging Tech",3));
            list.add(new Subject("ITelec3","Web Systems & Technologies",3));
        } else if (year==3 && sem==1) {
            list.add(new Subject("ITINST3","Data Science Analytics",2));
            list.add(new Subject("IT311","Advanced Database Systems",3));
            list.add(new Subject("IT312","Networking 2",3));
            list.add(new Subject("IT313","System Integration & Architecture",3));
            list.add(new Subject("IT314","Info Assurance & Security 1",3));
            list.add(new Subject("ITAPPDEV2","Web Applications",3));
            list.add(new Subject("ITAPPDEV3","Mobile Applications",3));
        } else if (year==3 && sem==2) {
            list.add(new Subject("ITGEE5","Multicultural Education",3));
            list.add(new Subject("IT321","Info Assurance & Security 2",3));
            list.add(new Subject("IT322","Social & Professional Issues",3));
            list.add(new Subject("IT323","Capstone Project & Research 1",3));
            list.add(new Subject("ITAPPDEV4","Game Development",3));
            list.add(new Subject("ITAPPDEV5","Cloud Computing",3));
        } else if (year==4 && sem==1) {
            list.add(new Subject("ITGEE6","Leadership & Management",3));
            list.add(new Subject("IT411","System Administration & Maintenance",3));
            list.add(new Subject("ITelec4","HCI 2",3));
            list.add(new Subject("IT412","Capstone Project & Research 2",3));
        } else if (year==4 && sem==2) {
            list.add(new Subject("IT421","Internship / OJT (486 hrs)",9));
        }
        return list;
    }

    private static List<Subject> subjectsBSITNS(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("IT101","Introduction to Computing",3));
            list.add(new Subject("IT102","Programming Fundamentals I",3));
            list.add(new Subject("GEC01","Understanding the Self",3));
            list.add(new Subject("MATH01","College Algebra",3));
            list.add(new Subject("PE01","Physical Activity I",2));
        } else if (year==1 && sem==2) {
            list.add(new Subject("IT111","Programming Fundamentals II",3));
            list.add(new Subject("IT112","Computer Systems & Hardware",3));
            list.add(new Subject("GEC02","Purposive Communication",3));
            list.add(new Subject("MATH02","Discrete Mathematics",3));
            list.add(new Subject("PE02","Physical Activity II",2));
        } else if (year==1 && sem==3) {
            list.add(new Subject("IT120","Hardware Lab (Midyear)",2));
        } else if (year==2 && sem==1) {
            list.add(new Subject("IT201","Networking Fundamentals I",3));
            list.add(new Subject("IT202","Operating Systems I",3));
            list.add(new Subject("IT203","Scripting for Admins",3));
            list.add(new Subject("GEC06","Science & Tech Society",3));
        } else if (year==2 && sem==2) {
            list.add(new Subject("IT211","Routing & Switching",3));
            list.add(new Subject("IT212","Network Security Basics",3));
            list.add(new Subject("IT213","Server Administration",3));
        } else if (year==2 && sem==3) {
            list.add(new Subject("IT220","Network Lab (Midyear)",2));
        } else if (year==3 && sem==1) {
            list.add(new Subject("IT301","Advanced Networking",3));
            list.add(new Subject("IT302","Network Design & Architecture",3));
            list.add(new Subject("IT303","Cloud Infrastructure",3));
        } else if (year==3 && sem==2) {
            list.add(new Subject("IT311","Network Automation",3));
            list.add(new Subject("IT312","Security Operations",3));
            list.add(new Subject("IT313","Systems Integration",3));
        } else if (year==3 && sem==3) {
            list.add(new Subject("IT320","Network Practicum / Immersion",2));
        } else if (year==4 && sem==1) {
            list.add(new Subject("IT401","Capstone Project I (Network)",3));
            list.add(new Subject("IT402","Enterprise Networking",3));
            list.add(new Subject("IT403","Incident Response & Forensics",3));
        } else if (year==4 && sem==2) {
            list.add(new Subject("IT411","Capstone Project II (Network)",3));
            list.add(new Subject("IT412","Professional Practice & Ethics",3));
        } else if (year==4 && sem==3) {
            list.add(new Subject("IT420","Practicum / OJT",2));
        }
        return list;
    }

    private static List<Subject> subjectsBSCS(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("CS101","Introduction to Computing",3));
            list.add(new Subject("CS102","Fundamentals of Programming",3));
            list.add(new Subject("GEC01","Understanding the Self",3));
            list.add(new Subject("GEC03","Math in the Modern World",3));
            list.add(new Subject("PE01","Physical Fitness I",2));
        } else if (year==1 && sem==2) {
            list.add(new Subject("CS111","Intro to Computing Lab",1));
            list.add(new Subject("CS112","Fundamentals of Programming II",3));
            list.add(new Subject("GEC04","Purposive Communication",3));
            list.add(new Subject("GEC05","Art Appreciation",3));
            list.add(new Subject("PE02","Physical Fitness II",2));
        } else if (year==1 && sem==3) {
            list.add(new Subject("CS120","Intro Web Development (Midyear)",2));
        } else if (year==2 && sem==1) {
            list.add(new Subject("CS201","Discrete Structures 1",3));
            list.add(new Subject("CS202","Data Structures & Algorithms",3));
            list.add(new Subject("CS203","OOP 1",3));
            list.add(new Subject("GEC06","Science & Tech Society",3));
            list.add(new Subject("PE03","Physical Fitness III",2));
        } else if (year==2 && sem==2) {
            list.add(new Subject("CS211","Discrete Structures 2",3));
            list.add(new Subject("CS212","Intermediate Programming / OOP",3));
            list.add(new Subject("CS213","Information Management",3));
            list.add(new Subject("GEC07","Ethics",3));
            list.add(new Subject("PE04","Physical Fitness IV",2));
        } else if (year==2 && sem==3) {
            list.add(new Subject("CS220","Data Structures Lab (Midyear)",2));
        } else if (year==3 && sem==1) {
            list.add(new Subject("CS301","Algorithms & Complexity",3));
            list.add(new Subject("CS302","Computer Organization & Architecture",3));
            list.add(new Subject("CS303","Software Engineering I",3));
            list.add(new Subject("CS304","Database Systems",3));
            list.add(new Subject("CS305","Information Assurance Basics",3));
        } else if (year==3 && sem==2) {
            list.add(new Subject("CS311","Automata Theory",3));
            list.add(new Subject("CS312","Software Engineering II",3));
            list.add(new Subject("CS313","Artificial Intelligence",3));
            list.add(new Subject("CS314","Programming Languages",3));
            list.add(new Subject("CS315","Research Methods",3));
        } else if (year==3 && sem==3) {
            list.add(new Subject("CS330","Domain Internship / Project (Midyear)",2));
        } else if (year==4 && sem==1) {
            list.add(new Subject("CS401","Capstone I",3));
            list.add(new Subject("CS402","Human-Computer Interaction",3));
            list.add(new Subject("CS403","Information Assurance & Security",3));
            list.add(new Subject("CS404","Emerging Technologies",3));
        } else if (year==4 && sem==2) {
            list.add(new Subject("CS411","Capstone II",3));
            list.add(new Subject("CS412","Professional Practice & Ethics",3));
            list.add(new Subject("CS413","Advanced Topics in CS",3));
        } else if (year==4 && sem==3) {
            list.add(new Subject("CS420","Practicum / OJT (Midyear)",2));
        }
        return list;
    }

    private static List<Subject> subjectsBSDSA(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("DSA101","Fundamentals of Programming",3));
            list.add(new Subject("DSA102","Discrete Structures",3));
            list.add(new Subject("GEC01","Understanding the Self",3));
            list.add(new Subject("GEC03","Mathematics in the Modern World",3));
        } else if (year==1 && sem==2) {
            list.add(new Subject("DSA111","Calculus for Data Science",3));
            list.add(new Subject("DSA112","Intro to Statistics",3));
            list.add(new Subject("DSA113","Intro to Data Science",3));
        } else if (year==2 && sem==1) {
            list.add(new Subject("DSA201","Data Structures & Algorithms",3));
            list.add(new Subject("DSA202","Linear Algebra for Data Science",3));
            list.add(new Subject("DSA203","Statistical Inference",3));
        } else if (year==2 && sem==2) {
            list.add(new Subject("DSA211","Programming for Data Science",3));
            list.add(new Subject("DSA212","Data Management & Warehousing",3));
            list.add(new Subject("DSA213","Data Visualization",3));
        } else if (year==3 && sem==1) {
            list.add(new Subject("DSA301","Machine Learning I",3));
            list.add(new Subject("DSA302","Exploratory Data Analysis",3));
            list.add(new Subject("DSA303","Business Intelligence",3));
        } else if (year==3 && sem==2) {
            list.add(new Subject("DSA311","Big Data Technologies",3));
            list.add(new Subject("DSA312","Applied Machine Learning",3));
            list.add(new Subject("DSA313","Research Methods",3));
        } else if (year==4 && sem==1) {
            list.add(new Subject("DSA401","Capstone I",3));
            list.add(new Subject("DSA402","Data Privacy & Ethics",3));
            list.add(new Subject("DSA403","Advanced Analytics",3));
        } else if (year==4 && sem==2) {
            list.add(new Subject("DSA411","Capstone II",3));
            list.add(new Subject("DSA412","Deployment & MLOps",3));
        }
        return list;
    }

    private static List<Subject> subjectsBSIS(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("IS101","Introduction to Information Systems",3));
            list.add(new Subject("IS102","Computer Programming I",3));
            list.add(new Subject("GEC01","Understanding the Self",3));
        } else if (year==1 && sem==2) {
            list.add(new Subject("IS111","Computer Programming II",3));
            list.add(new Subject("IS112","Fundamentals of IS",3));
            list.add(new Subject("IS113","Health & Wellness / GE Elective",3));
        } else if (year==2 && sem==1) {
            list.add(new Subject("IS201","Data Structures & Algorithms for IS",3));
            list.add(new Subject("IS202","IT Infrastructure & Networks",3));
            list.add(new Subject("IS203","Organization & Management Concepts",3));
        } else if (year==2 && sem==2) {
            list.add(new Subject("IS211","Systems Analysis & Design",3));
            list.add(new Subject("IS212","Financial Management for IS",3));
            list.add(new Subject("IS213","Service Management for BPO",3));
        } else if (year==3 && sem==1) {
            list.add(new Subject("IS301","Information Management",3));
            list.add(new Subject("IS302","Enterprise Architecture",3));
            list.add(new Subject("IS303","Business Process Management",3));
        } else if (year==3 && sem==2) {
            list.add(new Subject("IS311","Project Management for IS",3));
            list.add(new Subject("IS312","Evaluation of Business Performance",3));
            list.add(new Subject("IS313","Capstone Project I",3));
        } else if (year==4 && sem==1) {
            list.add(new Subject("IS401","IS Strategy, Management & Acquisition",3));
            list.add(new Subject("IS402","Applications Development & Emerging Tech",3));
            list.add(new Subject("IS403","Capstone Project II",3));
        } else if (year==4 && sem==2) {
            list.add(new Subject("IS411","Practicum / Internship (486 hrs)",3));
        }
        return list;
    }

    private static List<Subject> subjectsBSMIT(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("MIT101","Introduction to Multimedia Systems",3));
            list.add(new Subject("MIT102","Basic Graphic Design",3));
            list.add(new Subject("GEC01","Understanding the Self",3));
        } else if (year==1 && sem==2) {
            list.add(new Subject("MIT111","Fundamentals of Animation",3));
            list.add(new Subject("MIT112","Computer Programming for Multimedia",3));
        } else if (year==2 && sem==1) {
            list.add(new Subject("MIT201","Digital Storytelling & Scripting",3));
            list.add(new Subject("MIT202","Web Design & Development",3));
        } else if (year==2 && sem==2) {
            list.add(new Subject("MIT211","Motion Graphics",3));
            list.add(new Subject("MIT212","Interactive Media",3));
        } else if (year==3 && sem==1) {
            list.add(new Subject("MIT301","Advanced Web Technologies",3));
            list.add(new Subject("MIT302","3D Modeling and Rendering",3));
        } else if (year==3 && sem==2) {
            list.add(new Subject("MIT311","Advanced Animation Techniques",3));
            list.add(new Subject("MIT312","Mobile App Design",3));
        } else if (year==4 && sem==1) {
            list.add(new Subject("MIT401","Capstone Project I",3));
        } else if (year==4 && sem==2) {
            list.add(new Subject("MIT411","Capstone Project II",3));
        }
        return list;
    }

    private static List<Subject> subjectsBSBLIS(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("LIS101","Intro to Library Science",3));
            list.add(new Subject("LIS102","Information Sources & Services",3));
            list.add(new Subject("GEC01","Understanding the Self",3));
        } else if (year==1 && sem==2) {
            list.add(new Subject("LIS111","Cataloging & Classification I",3));
            list.add(new Subject("LIS112","Library Organization",3));
        } else if (year==1 && sem==3) {
            list.add(new Subject("LIS120","Library Skills Lab (Midyear)",2));
        } else if (year==2 && sem==1) {
            list.add(new Subject("LIS201","Research Methods for LIS",3));
            list.add(new Subject("LIS202","Collection Development",3));
        } else if (year==2 && sem==2) {
            list.add(new Subject("LIS211","ICT for Libraries",3));
            list.add(new Subject("LIS212","Preservation & Conservation",3));
        } else if (year==2 && sem==3) {
            list.add(new Subject("LIS220","Cataloging Lab (Midyear)",2));
        } else if (year==3 && sem==1) {
            list.add(new Subject("LIS301","Management of Libraries",3));
            list.add(new Subject("LIS302","Digital Libraries",3));
        } else if (year==3 && sem==2) {
            list.add(new Subject("LIS311","Information Literacy Programs",3));
            list.add(new Subject("LIS312","Archives & Records Management",3));
        } else if (year==3 && sem==3) {
            list.add(new Subject("LIS320","Industry Immersion",2));
        } else if (year==4 && sem==1) {
            list.add(new Subject("LIS401","Capstone I",3));
        } else if (year==4 && sem==2) {
            list.add(new Subject("LIS411","Capstone II",3));
        } else if (year==4 && sem==3) {
            list.add(new Subject("LIS420","Special Topics / Midyear",2));
        }
        return list;
    }

    private static List<Subject> subjectsMIT(int year, int sem) {
        List<Subject> list = new ArrayList<>();
        if (year==1 && sem==1) {
            list.add(new Subject("MIT201","Advanced Data Structure & Algorithm Analysis",3));
            list.add(new Subject("MIT202","Data Warehousing & Data Mining",3));
            list.add(new Subject("MIT203","Advanced Database Systems",3));
            list.add(new Subject("MIT204","Advanced Systems Design & Implementation",3));
        } else if (year==1 && sem==2) {
            list.add(new Subject("MIT211","IT Project Management",3));
            list.add(new Subject("MIT212","Web-based App Dev & Management",3));
            list.add(new Subject("MIT213","Distributed Database System",3));
            list.add(new Subject("MIT214","Security Management in IS",3));
        } else if (year==1 && sem==3) {
            list.add(new Subject("MIT215","Cloud Computing",3));
            list.add(new Subject("MIT216","Applied Machine Learning",3));
        } else if (year==2 && sem==1) {
            list.add(new Subject("MITINST1","Climate Change & DRRM",3));
            list.add(new Subject("MIT301","Capstone in IT 1",3));
        } else if (year==2 && sem==2) {
            list.add(new Subject("MIT302","Capstone in IT 2",3));
        }
        return list;
    }

    // ===== LANDING ART =====
    private static void showLandingArt() {
        int width = 80;
        String border = String.join("", Collections.nCopies(width, "="));
        String title1 = "ISABELA STATE UNIVERSITY";
        String title2 = "ENROLLMENT SYSTEM";
        String subtitle = "CCSICT Student Enrollment";
        String date = "Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String registered = "Registered accounts: " + credentials.size();

        System.out.println(border);
        printCentered("", width);
        printCentered(title1, width);
        printCentered(title2, width);
        printCentered(subtitle, width);
        printCentered("", width);
        printCentered("----------------------------------------", width);

        String infoLine = String.format("%s   |   %s", date, registered);
        printCentered(infoLine, width);

        printCentered("----------------------------------------", width);
        printCentered("", width);
        printCentered("Welcome! ", width);
        printCentered("", width);
        System.out.println(border);
    }

    private static void printCentered(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) {
            System.out.println(text);
            return;
        }
        int padding = (width - text.length()) / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < padding; i++) sb.append(' ');
        sb.append(text);
        System.out.println(sb.toString());
    }
}
