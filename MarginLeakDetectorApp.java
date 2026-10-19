// Default package: Enables seamless execution via 'javac MarginLeakDetectorApp.java' and 'java MarginLeakDetectorApp'
// or single-command run: 'java MarginLeakDetectorApp.java' (Java 11+)

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ============================================================================
 * PROJECT TITLE: E-COMMERCE MARGIN LEAK DETECTOR
 * ============================================================================
 * 
 * OBJECTIVE:
 * Online marketplace merchants frequently suffer from hidden profit erosion
 * caused by platform referral fees, dimensional weight shipping penalties,
 * payment gateway percentages, and high customer return rates.
 * 
 * This application analyzes product economics, dynamically deducts categorized
 * operational expenses, and evaluates margin leak rules to flag vulnerabilities.
 * 
 * ----------------------------------------------------------------------------
 * COLLEGE VIVA & ORAL EXAM QUICK-REFERENCE (THE 4 OOP PILLARS):
 * ----------------------------------------------------------------------------
 * 1. ENCAPSULATION:
 *    - Demonstrated in classes like 'Product', 'Dimensions', 'ExpenseResult', and 'LeakAlert'.
 *    - All fields are declared 'private' to restrict unauthorized direct access.
 *    - Strong validation in constructors prevents invalid states (e.g., negative prices,
 *      weight <= 0, return rate outside [0, 100]).
 *    - Business logic calculations (e.g., getGrossProfit(), getNetProfit(),
 *      getProfitMarginPercent()) are encapsulated inside the Product class rather
 *      than computed externally.
 * 
 * 2. INHERITANCE:
 *    - Demonstrated through the abstract base class 'Expense'.
 *    - Subclasses extend 'Expense':
 *      * 'PlatformCommissionExpense'
 *      * 'ShippingExpense'
 *      * 'ReturnReserveExpense'
 *      * 'PaymentProcessingExpense'
 *    - Common attributes ('name', 'category') and method signatures are inherited,
 *      promoting DRY (Don't Repeat Yourself) code reuse.
 * 
 * 3. POLYMORPHISM:
 *    - Subtypes override the abstract method 'calculate(Product product)'.
 *    - Dynamic Method Dispatch (Runtime Polymorphism): The Product class maintains
 *      a 'List<Expense>'. When invoking 'calculateTotalExpenses()', Java dynamically
 *      determines at runtime which concrete calculation logic to execute based on
 *      the actual object instance.
 *    - Similarly, 'MarginLeakDetector' iterates over 'List<MarginLeakRule>' and calls
 *      'rule.evaluate(product)' polymorphically.
 * 
 * 4. ABSTRACTION & STRATEGY PATTERN:
 *    - Demonstrated via the 'MarginLeakRule' interface and abstract class 'Expense'.
 *    - Consumers interact with high-level contracts ('evaluate()', 'calculate()')
 *      without coupling to concrete algorithmic details.
 *    - Follows the Strategy Design Pattern and the Open/Closed Principle (OCP):
 *      New audit rules or expense types can be added without modifying existing code.
 * ============================================================================
 */

// ============================================================================
// DOMAIN ENUMS
// ============================================================================

enum ProductCategory {
    ELECTRONICS,
    APPAREL,
    FURNITURE,
    HOME_AND_KITCHEN,
    BEAUTY,
    ACCESSORIES
}

enum ExpenseCategory {
    COMMISSION,
    LOGISTICS,
    RETURNS_AND_ALLOWANCES,
    PAYMENT_PROCESSING,
    PACKAGING_AND_OTHER
}

enum Severity {
    CRITICAL("CRITICAL - Urgent Loss Per Unit"),
    HIGH("HIGH - Severe Margin Erosion"),
    WARNING("WARNING - Suboptimal Margin / Risk"),
    OK("HEALTHY - Margin Targets Satisfied");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

// ============================================================================
// OOP PILLAR 1: ENCAPSULATION
// ============================================================================

/**
 * Value Object representing physical package dimensions in centimeters.
 * Encapsulates the international volumetric (dimensional) weight formula.
 */
class Dimensions {
    private final double lengthCm;
    private final double widthCm;
    private final double heightCm;

    public Dimensions(double lengthCm, double widthCm, double heightCm) {
        if (lengthCm <= 0 || widthCm <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("Dimensions must be strictly positive values.");
        }
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
    }

    public double getLengthCm() {
        return lengthCm;
    }

    public double getWidthCm() {
        return widthCm;
    }

    public double getHeightCm() {
        return heightCm;
    }

    /**
     * Standard courier volumetric weight formula: (L x W x H) / Divisor
     * Default air/express freight divisor is typically 5000.
     */
    public double getVolumetricWeightKg(double divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be greater than zero.");
        }
        return (lengthCm * widthCm * heightCm) / divisor;
    }

    @Override
    public String toString() {
        return String.format("%.1f x %.1f x %.1f cm", lengthCm, widthCm, heightCm);
    }
}

/**
 * Product aggregate root encapsulating SKU economics, logistics metadata,
 * and associated operational expenses.
 */
class Product {
    private final String sku;
    private final String title;
    private final ProductCategory category;
    private final double sellingPrice;
    private final double costOfGoods; // COGS
    private final Dimensions dimensions;
    private final double actualWeightKg;
    private final double returnRatePercent; // e.g. 15.0 for 15%
    private final List<Expense> expenses;

    public Product(String sku, String title, ProductCategory category,
                   double sellingPrice, double costOfGoods,
                   Dimensions dimensions, double actualWeightKg,
                   double returnRatePercent) {
        
        // Defensive validation to ensure encapsulation of valid business state
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("Product SKU cannot be null or blank.");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Product title cannot be null or blank.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Product category must be specified.");
        }
        if (sellingPrice <= 0.0) {
            throw new IllegalArgumentException("Selling price must be greater than zero.");
        }
        if (costOfGoods < 0.0) {
            throw new IllegalArgumentException("Cost of goods sold (COGS) cannot be negative.");
        }
        if (dimensions == null) {
            throw new IllegalArgumentException("Product dimensions cannot be null.");
        }
        if (actualWeightKg <= 0.0) {
            throw new IllegalArgumentException("Actual weight must be greater than zero.");
        }
        if (returnRatePercent < 0.0 || returnRatePercent > 100.0) {
            throw new IllegalArgumentException("Return rate must be between 0.0% and 100.0%.");
        }

        this.sku = sku.trim();
        this.title = title.trim();
        this.category = category;
        this.sellingPrice = sellingPrice;
        this.costOfGoods = costOfGoods;
        this.dimensions = dimensions;
        this.actualWeightKg = actualWeightKg;
        this.returnRatePercent = returnRatePercent;
        this.expenses = new ArrayList<>();
    }

    // --- Expense Management ---
    public void addExpense(Expense expense) {
        if (expense == null) {
            throw new IllegalArgumentException("Expense cannot be null.");
        }
        this.expenses.add(expense);
    }

    public List<Expense> getExpenses() {
        return Collections.unmodifiableList(expenses);
    }

    // --- Getters ---
    public String getSku() {
        return sku;
    }

    public String getTitle() {
        return title;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public double getSellingPrice() {
        return sellingPrice;
    }

    public double getCostOfGoods() {
        return costOfGoods;
    }

    public Dimensions getDimensions() {
        return dimensions;
    }

    public double getActualWeightKg() {
        return actualWeightKg;
    }

    public double getReturnRatePercent() {
        return returnRatePercent;
    }

    // --- Encapsulated Financial Business Calculations ---

    /**
     * Billable shipping weight is the greater of actual dead weight
     * or dimensional volumetric weight.
     */
    public double getBillableWeightKg() {
        double volWeight = dimensions.getVolumetricWeightKg(5000.0);
        return Math.max(actualWeightKg, volWeight);
    }

    public double getGrossProfit() {
        return sellingPrice - costOfGoods;
    }

    public double getGrossMarginPercent() {
        return (getGrossProfit() / sellingPrice) * 100.0;
    }

    /**
     * OOP PILLAR 3 (POLYMORPHISM):
     * Dynamically invokes calculate() across heterogeneous Expense subclasses.
     */
    public double calculateTotalExpenses() {
        double total = 0.0;
        for (Expense expense : expenses) {
            total += expense.calculate(this); // Dynamic Method Dispatch
        }
        return total;
    }

    public double getNetProfit() {
        return getGrossProfit() - calculateTotalExpenses();
    }

    public double getProfitMarginPercent() {
        return (getNetProfit() / sellingPrice) * 100.0;
    }
}

// ============================================================================
// OOP PILLAR 2: INHERITANCE
// ============================================================================

/**
 * Abstract base class representing generic marketplace deductions.
 * Provides shared state (name, category) and enforces polymorphic contract.
 */
abstract class Expense {
    private final String name;
    private final ExpenseCategory category;

    public Expense(String name, ExpenseCategory category) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Expense name cannot be empty.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Expense category cannot be null.");
        }
        this.name = name.trim();
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    /**
     * OOP PILLAR 4 (ABSTRACTION):
     * Abstract method implemented by concrete subclasses to perform specific math.
     */
    public abstract double calculate(Product product);

    /**
     * Informative string explaining how the specific fee was determined.
     */
    public abstract String getCalculationDetails(Product product);
}

/**
 * Concrete Expense 1: Marketplace referral commission fee.
 * Calculates dynamic percentage of selling price with a minimum fee floor.
 */
class PlatformCommissionExpense extends Expense {
    private final double commissionRatePercent;
    private final double minimumFee;

    public PlatformCommissionExpense(String name, double commissionRatePercent, double minimumFee) {
        super(name, ExpenseCategory.COMMISSION);
        if (commissionRatePercent < 0.0) {
            throw new IllegalArgumentException("Commission rate cannot be negative.");
        }
        if (minimumFee < 0.0) {
            throw new IllegalArgumentException("Minimum fee cannot be negative.");
        }
        this.commissionRatePercent = commissionRatePercent;
        this.minimumFee = minimumFee;
    }

    @Override
    public double calculate(Product product) {
        double percentageFee = product.getSellingPrice() * (commissionRatePercent / 100.0);
        return Math.max(minimumFee, percentageFee);
    }

    @Override
    public String getCalculationDetails(Product product) {
        double computed = product.getSellingPrice() * (commissionRatePercent / 100.0);
        if (computed < minimumFee) {
            return String.format("%.1f%% of $%.2f is $%.2f (Floored to Min Fee $%.2f)",
                    commissionRatePercent, product.getSellingPrice(), computed, minimumFee);
        }
        return String.format("%.1f%% of Selling Price ($%.2f)", commissionRatePercent, product.getSellingPrice());
    }
}

/**
 * Concrete Expense 2: Weight-based logistics shipping expense.
 * Evaluates actual dead weight against volumetric dimensional weight
 * and applies slab pricing.
 */
class ShippingExpense extends Expense {
    private final double baseRate;
    private final double baseWeightSlabKg;
    private final double incrementalRatePerKg;

    public ShippingExpense(String name, double baseRate, double baseWeightSlabKg, double incrementalRatePerKg) {
        super(name, ExpenseCategory.LOGISTICS);
        if (baseRate < 0.0 || baseWeightSlabKg <= 0.0 || incrementalRatePerKg < 0.0) {
            throw new IllegalArgumentException("Shipping rates and weight slabs must be non-negative.");
        }
        this.baseRate = baseRate;
        this.baseWeightSlabKg = baseWeightSlabKg;
        this.incrementalRatePerKg = incrementalRatePerKg;
    }

    @Override
    public double calculate(Product product) {
        double billableWeight = product.getBillableWeightKg();
        if (billableWeight <= baseWeightSlabKg) {
            return baseRate;
        }
        double extraWeight = billableWeight - baseWeightSlabKg;
        // Ceil to nearest full kg slab in commercial shipping
        double billableExtraSlabs = Math.ceil(extraWeight);
        return baseRate + (billableExtraSlabs * incrementalRatePerKg);
    }

    @Override
    public String getCalculationDetails(Product product) {
        double actual = product.getActualWeightKg();
        double vol = product.getDimensions().getVolumetricWeightKg(5000.0);
        double billable = product.getBillableWeightKg();
        boolean isVolumetric = vol > actual;

        return String.format("Billable: %.2f kg [%s: Act=%.2f kg, Vol=%.2f kg] (Base: $%.2f + Extra: $%.2f)",
                billable,
                isVolumetric ? "Volumetric Penalty" : "Actual Weight",
                actual,
                vol,
                baseRate,
                Math.max(0, calculate(product) - baseRate));
    }
}

/**
 * Concrete Expense 3: Return reserve and customer returns handling expense.
 * Estimates per-unit expected loss based on return frequency, reverse freight,
 * and restocking/liquidation depreciation.
 */
class ReturnReserveExpense extends Expense {
    private final double reverseLogisticsCost;
    private final double repackagingLossCost;

    public ReturnReserveExpense(String name, double reverseLogisticsCost, double repackagingLossCost) {
        super(name, ExpenseCategory.RETURNS_AND_ALLOWANCES);
        if (reverseLogisticsCost < 0.0 || repackagingLossCost < 0.0) {
            throw new IllegalArgumentException("Return reserve parameters cannot be negative.");
        }
        this.reverseLogisticsCost = reverseLogisticsCost;
        this.repackagingLossCost = repackagingLossCost;
    }

    @Override
    public double calculate(Product product) {
        double returnProbability = product.getReturnRatePercent() / 100.0;
        double lossPerReturn = reverseLogisticsCost + repackagingLossCost;
        // Expected loss reserve allocated per unit sold
        return returnProbability * lossPerReturn;
    }

    @Override
    public String getCalculationDetails(Product product) {
        double lossPerReturn = reverseLogisticsCost + repackagingLossCost;
        return String.format("%.1f%% Return Rate x $%.2f Loss/Return (Freight: $%.2f, Damage/Restock: $%.2f)",
                product.getReturnRatePercent(), lossPerReturn, reverseLogisticsCost, repackagingLossCost);
    }
}

/**
 * Concrete Expense 4: Payment gateway processing expense (e.g., Stripe, PayPal).
 * Standard merchant charge of flat fixed fee + interchange percentage.
 */
class PaymentProcessingExpense extends Expense {
    private final double fixedFee;
    private final double feePercentage;

    public PaymentProcessingExpense(String name, double fixedFee, double feePercentage) {
        super(name, ExpenseCategory.PAYMENT_PROCESSING);
        if (fixedFee < 0.0 || feePercentage < 0.0) {
            throw new IllegalArgumentException("Payment processing fee parameters cannot be negative.");
        }
        this.fixedFee = fixedFee;
        this.feePercentage = feePercentage;
    }

    @Override
    public double calculate(Product product) {
        return fixedFee + (product.getSellingPrice() * (feePercentage / 100.0));
    }

    @Override
    public String getCalculationDetails(Product product) {
        return String.format("$%.2f Fixed + %.2f%% of Selling Price ($%.2f)",
                fixedFee, feePercentage, product.getSellingPrice());
    }
}

// ============================================================================
// OOP PILLAR 4: ABSTRACTION & STRATEGY DESIGN PATTERN
// ============================================================================

/**
 * Immutable diagnostic record representing a detected margin leak alert.
 */
class LeakAlert {
    private final String ruleName;
    private final Severity severity;
    private final String diagnosis;
    private final String suggestedAction;

    public LeakAlert(String ruleName, Severity severity, String diagnosis, String suggestedAction) {
        this.ruleName = Objects.requireNonNull(ruleName, "Rule name cannot be null");
        this.severity = Objects.requireNonNull(severity, "Severity cannot be null");
        this.diagnosis = Objects.requireNonNull(diagnosis, "Diagnosis cannot be null");
        this.suggestedAction = Objects.requireNonNull(suggestedAction, "Action cannot be null");
    }

    public String getRuleName() {
        return ruleName;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }
}

/**
 * STRATEGY INTERFACE:
 * Abstraction decoupling the audit engine from concrete inspection rules.
 */
interface MarginLeakRule {
    Optional<LeakAlert> evaluate(Product product);
}

/**
 * Concrete Strategy 1: Checks if net profit is negative.
 * Triggers CRITICAL alert when selling every unit causes cash drainage.
 */
class NegativeMarginRule implements MarginLeakRule {
    @Override
    public Optional<LeakAlert> evaluate(Product product) {
        double netProfit = product.getNetProfit();
        if (netProfit < 0.0) {
            String diag = String.format("Product is selling at a NET LOSS of $%.2f per unit (Net Margin: %.2f%%).",
                    Math.abs(netProfit), product.getProfitMarginPercent());
            String action = String.format("URGENT: Halt ad spend immediately. Increase price by at least $%.2f to break even.",
                    Math.abs(netProfit));
            return Optional.of(new LeakAlert("Negative Margin Rule", Severity.CRITICAL, diag, action));
        }
        return Optional.empty();
    }
}

/**
 * Concrete Strategy 2: Checks if return reserves are eroding healthy gross profit.
 * Triggers HIGH alert if return rate > 20% or if return reserves absorb > 30% of gross margin.
 */
class HighReturnErosionRule implements MarginLeakRule {
    private final double returnRateThresholdPercent;
    private final double grossProfitErosionThresholdPercent;

    public HighReturnErosionRule(double returnRateThresholdPercent, double grossProfitErosionThresholdPercent) {
        this.returnRateThresholdPercent = returnRateThresholdPercent;
        this.grossProfitErosionThresholdPercent = grossProfitErosionThresholdPercent;
    }

    public HighReturnErosionRule() {
        this(20.0, 30.0);
    }

    @Override
    public Optional<LeakAlert> evaluate(Product product) {
        double returnRate = product.getReturnRatePercent();
        double returnExpense = 0.0;
        for (Expense exp : product.getExpenses()) {
            if (exp.getCategory() == ExpenseCategory.RETURNS_AND_ALLOWANCES) {
                returnExpense += exp.calculate(product);
            }
        }

        double grossProfit = product.getGrossProfit();
        double erosionPercent = (grossProfit > 0) ? (returnExpense / grossProfit) * 100.0 : 100.0;

        if (returnRate >= returnRateThresholdPercent || erosionPercent >= grossProfitErosionThresholdPercent) {
            String diag = String.format("Return rate of %.1f%% results in $%.2f return reserve/unit, consuming %.1f%% of gross profit.",
                    returnRate, returnExpense, erosionPercent);
            String action = "Audit product quality, update apparel sizing charts, or enforce buyer-paid return shipping.";
            return Optional.of(new LeakAlert("High Return Erosion Rule", Severity.HIGH, diag, action));
        }
        return Optional.empty();
    }
}

/**
 * Concrete Strategy 3: Checks if net margin is below targeted safety floor (e.g. 15%).
 * Triggers WARNING alert for thin margins that cannot absorb sudden PPC or courier fee hikes.
 */
class LowMarginThresholdRule implements MarginLeakRule {
    private final double targetMarginPercent;

    public LowMarginThresholdRule(double targetMarginPercent) {
        this.targetMarginPercent = targetMarginPercent;
    }

    public LowMarginThresholdRule() {
        this(15.0);
    }

    @Override
    public Optional<LeakAlert> evaluate(Product product) {
        double netMargin = product.getProfitMarginPercent();
        // Only trigger warning if margin is positive but thin
        if (netMargin >= 0.0 && netMargin < targetMarginPercent) {
            String diag = String.format("Net margin of %.2f%% is below target safety threshold of %.1f%%.",
                    netMargin, targetMarginPercent);
            String action = "Negotiate bulk volume discount with manufacturer or bundle with high-margin accessories.";
            return Optional.of(new LeakAlert("Low Margin Threshold Rule", Severity.WARNING, diag, action));
        }
        return Optional.empty();
    }
}

/**
 * Concrete Strategy 4: Detects whether any single expense category consumes > 30% of selling price.
 * Triggers WARNING / HIGH alert for runaway cost components (e.g., volumetric shipping blowout).
 */
class ExpenseOverrunRule implements MarginLeakRule {
    private final double overrunThresholdPercent;

    public ExpenseOverrunRule(double overrunThresholdPercent) {
        this.overrunThresholdPercent = overrunThresholdPercent;
    }

    public ExpenseOverrunRule() {
        this(30.0);
    }

    @Override
    public Optional<LeakAlert> evaluate(Product product) {
        for (Expense expense : product.getExpenses()) {
            double cost = expense.calculate(product);
            double expenseRatio = (cost / product.getSellingPrice()) * 100.0;
            if (expenseRatio >= overrunThresholdPercent) {
                Severity sev = expenseRatio >= 45.0 ? Severity.HIGH : Severity.WARNING;
                String diag = String.format("Expense '%s' is $%.2f, consuming %.1f%% of selling price (exceeds %.0f%% ceiling).",
                        expense.getName(), cost, expenseRatio, overrunThresholdPercent);
                String action = expense.getCategory() == ExpenseCategory.LOGISTICS
                        ? "Logistics overrun: Redesign packaging box or compress product to reduce volumetric weight."
                        : "Cost overrun: Renegotiate platform contract or optimize fee tier.";
                return Optional.of(new LeakAlert("Expense Overrun Rule [" + expense.getName() + "]", sev, diag, action));
            }
        }
        return Optional.empty();
    }
}

// ============================================================================
// AUDIT ENGINE & DASHBOARD RENDERER
// ============================================================================

/**
 * Audit engine orchestrating rule evaluations polymorphically.
 */
class MarginLeakDetector {
    private final List<MarginLeakRule> rules;

    public MarginLeakDetector() {
        this.rules = new ArrayList<>();
        // Register default audit strategies
        this.rules.add(new NegativeMarginRule());
        this.rules.add(new HighReturnErosionRule());
        this.rules.add(new ExpenseOverrunRule());
        this.rules.add(new LowMarginThresholdRule());
    }

    public void registerRule(MarginLeakRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule cannot be null.");
        }
        this.rules.add(rule);
    }

    public List<LeakAlert> audit(Product product) {
        List<LeakAlert> alerts = new ArrayList<>();
        for (MarginLeakRule rule : rules) {
            rule.evaluate(product).ifPresent(alerts::add);
        }
        return alerts;
    }
}

/**
 * Terminal UI formatter rendering visual dashboard cards.
 */
class DashboardPrinter {

    public static void printReport(Product product, List<LeakAlert> alerts) {
        String border = "==========================================================================================";
        String divider = "------------------------------------------------------------------------------------------";

        System.out.println();
        System.out.println(border);
        System.out.printf("  E-COMMERCE MARGIN AUDIT REPORT: %s (%s)%n", product.getTitle(), product.getSku());
        System.out.println(border);

        // Product Metadata
        System.out.println(" [PRODUCT SPECIFICATIONS]");
        System.out.printf("  Category       : %-20s  Dimensions    : %s%n",
                product.getCategory(), product.getDimensions());
        System.out.printf("  Actual Weight  : %-7.2f kg              Volumetric Wt : %.2f kg (Div: 5000)%n",
                product.getActualWeightKg(), product.getDimensions().getVolumetricWeightKg(5000.0));
        System.out.printf("  Billable Weight: %-7.2f kg              Return Rate   : %.1f%%%n",
                product.getBillableWeightKg(), product.getReturnRatePercent());
        System.out.println(divider);

        // Financial Ledger Breakdown
        System.out.println(" [FINANCIAL & FEE BREAKDOWN]");
        System.out.printf("  (+) Selling Price (Gross Revenue)    :  $%8.2f%n", product.getSellingPrice());
        System.out.printf("  (-) Cost of Goods Sold (COGS)        : -$%8.2f%n", product.getCostOfGoods());
        System.out.printf("  (=) Gross Profit (Before Deductions) :  $%8.2f  (Gross Margin: %.1f%%)%n",
                product.getGrossProfit(), product.getGrossMarginPercent());
        System.out.println("  ----------------------------------------------------------------------");
        System.out.println("  ITEMIZED OPERATIONAL EXPENSES (Polymorphic Dynamic Dispatch):");

        for (Expense expense : product.getExpenses()) {
            double cost = expense.calculate(product);
            double pctOfRev = (cost / product.getSellingPrice()) * 100.0;
            System.out.printf("    * %-30s : -$%8.2f  (%5.1f%% rev) | %s%n",
                    expense.getName(), cost, pctOfRev, expense.getCalculationDetails(product));
        }

        double totalExpenses = product.calculateTotalExpenses();
        System.out.println("  ----------------------------------------------------------------------");
        System.out.printf("  Total Operational Expenses           : -$%8.2f  (%5.1f%% rev)%n",
                totalExpenses, (totalExpenses / product.getSellingPrice()) * 100.0);
        System.out.println(divider);

        // Net Result Summary
        double netProfit = product.getNetProfit();
        double netMargin = product.getProfitMarginPercent();
        String healthStatus = netProfit < 0 ? "[CRITICAL LOSS]" : (netMargin < 15.0 ? "[LOW PROFIT]" : "[HEALTHY MARGIN]");

        System.out.printf("  NET PROFIT PER UNIT                  :  $%8.2f  (%s)%n", netProfit, healthStatus);
        System.out.printf("  NET PROFIT MARGIN                    :   %7.2f%%%n", netMargin);
        System.out.println(divider);

        // Margin Leak Rules Audit Section
        System.out.println(" [MARGIN LEAK AUDIT FINDINGS]");
        if (alerts.isEmpty()) {
            System.out.println("  [OK] No profitability leaks detected. Product margins exceed all target benchmarks!");
        } else {
            for (LeakAlert alert : alerts) {
                String badge = "[" + alert.getSeverity().name() + "]";
                System.out.printf("  %-10s Rule: %s%n", badge, alert.getRuleName());
                System.out.printf("             Diagnosis : %s%n", alert.getDiagnosis());
                System.out.printf("             Remedy    : %s%n", alert.getSuggestedAction());
                System.out.println();
            }
        }
        System.out.println(border);
    }
}

// ============================================================================
// MAIN APPLICATION ENTRY POINT & TEST SCENARIOS
// ============================================================================

public class MarginLeakDetectorApp {

    public static void main(String[] args) {
        System.out.println("##########################################################################################");
        System.out.println("           E-COMMERCE MARGIN LEAK DETECTOR - OBJECT ORIENTED AUDIT SYSTEM                ");
        System.out.println("##########################################################################################");

        MarginLeakDetector detector = new MarginLeakDetector();

        // --------------------------------------------------------------------
        // TEST CASE 1: OVERSIZED ITEM (Dimensional Weight Shipping Blowout)
        // --------------------------------------------------------------------
        // Selling an oak coffee table for $89.99. Appears profitable (COGS $42.00).
        // But dimensions 95x65x40 cm cause a massive 49.4 kg volumetric weight.
        // Logistics cost explodes to $68.50, turning the sale into an outright LOSS!
        Product coffeeTable = new Product(
                "SKU-FURN-001",
                "Nordic Minimalist Oak Coffee Table",
                ProductCategory.FURNITURE,
                89.99,
                42.00,
                new Dimensions(95.0, 65.0, 40.0), // Volumetric: (95*65*40)/5000 = 49.4 kg!
                16.0,                             // Actual weight: 16 kg
                4.0                               // 4% return rate
        );
        // Deductions
        coffeeTable.addExpense(new PlatformCommissionExpense("Amazon Referral Fee", 15.0, 1.50));
        coffeeTable.addExpense(new ShippingExpense("Courier Express Logistics", 12.50, 2.0, 1.20));
        coffeeTable.addExpense(new ReturnReserveExpense("Customer Return Reserve", 18.00, 10.00));
        coffeeTable.addExpense(new PaymentProcessingExpense("Stripe Gateway Fee", 0.30, 2.9));

        List<LeakAlert> alerts1 = detector.audit(coffeeTable);
        DashboardPrinter.printReport(coffeeTable, alerts1);

        // --------------------------------------------------------------------
        // TEST CASE 2: HIGH-RETURN APPAREL (Return Reserve Margin Erosion)
        // --------------------------------------------------------------------
        // A luxury evening dress sold for $149.00 with low COGS ($38.00).
        // Gross margin looks huge at 74.5%!
        // However, a 34% return rate (wardrobing/sizing) inflicts severe reverse freight
        // and restocking expenses, consuming most of the gross profit.
        Product eveningGown = new Product(
                "SKU-APP-772",
                "Aura Haute Velvet Evening Dress",
                ProductCategory.APPAREL,
                149.00,
                38.00,
                new Dimensions(35.0, 25.0, 5.0), // Compact packaging
                0.85,
                34.0                             // 34% return rate!
        );
        eveningGown.addExpense(new PlatformCommissionExpense("Apparel Category Fee", 17.0, 1.00));
        eveningGown.addExpense(new ShippingExpense("Standard Air Courier", 6.50, 1.0, 1.50));
        eveningGown.addExpense(new ReturnReserveExpense("Reverse Logistics & Dry Cleaning", 14.00, 18.00));
        eveningGown.addExpense(new PaymentProcessingExpense("Shopify Payments", 0.30, 2.7));

        List<LeakAlert> alerts2 = detector.audit(eveningGown);
        DashboardPrinter.printReport(eveningGown, alerts2);

        // --------------------------------------------------------------------
        // TEST CASE 3: LOW-PRICED ACCESSORY (Suboptimal Thin Margin)
        // --------------------------------------------------------------------
        // A budget USB-C cable sold for $9.99. COGS is $3.80.
        // Fixed minimum commissions ($1.50 floor) and courier base rates leave only pennies.
        Product usbCable = new Product(
                "SKU-ACC-108",
                "Braided Fast Charge USB-C Cable 2M",
                ProductCategory.ACCESSORIES,
                9.99,
                3.80,
                new Dimensions(12.0, 8.0, 2.0),
                0.12,
                2.5
        );
        usbCable.addExpense(new PlatformCommissionExpense("Electronics Commission (Floored)", 12.0, 1.50));
        usbCable.addExpense(new ShippingExpense("Economy Parcel Freight", 3.20, 0.5, 0.80));
        usbCable.addExpense(new ReturnReserveExpense("Return Provision", 4.00, 1.00));
        usbCable.addExpense(new PaymentProcessingExpense("Merchant Interchange", 0.30, 2.9));

        List<LeakAlert> alerts3 = detector.audit(usbCable);
        DashboardPrinter.printReport(usbCable, alerts3);

        // --------------------------------------------------------------------
        // TEST CASE 4: OPTIMIZED TECH PRODUCT (Healthy Margin [OK])
        // --------------------------------------------------------------------
        // High perceived value, compact packaging, low return rate.
        // Healthy 45%+ net profit margin with zero leaks flagged.
        Product wirelessEarbuds = new Product(
                "SKU-TECH-505",
                "SonicPro Wireless ANC Earbuds",
                ProductCategory.ELECTRONICS,
                79.99,
                24.50,
                new Dimensions(10.0, 8.0, 4.0), // Very compact: 0.06 kg volumetric
                0.22,                            // 220g actual
                3.0                              // Low 3% return rate
        );
        wirelessEarbuds.addExpense(new PlatformCommissionExpense("Consumer Electronics Fee", 8.0, 1.00));
        wirelessEarbuds.addExpense(new ShippingExpense("Tracked Mail Logistic", 4.50, 1.0, 1.00));
        wirelessEarbuds.addExpense(new ReturnReserveExpense("Defect Warranty Reserve", 6.00, 4.00));
        wirelessEarbuds.addExpense(new PaymentProcessingExpense("Standard Gateway Cut", 0.30, 2.9));

        List<LeakAlert> alerts4 = detector.audit(wirelessEarbuds);
        DashboardPrinter.printReport(wirelessEarbuds, alerts4);

        System.out.println(">> ALL 4 TEST SCENARIOS EXECUTED SUCCESSFULLY.");
        System.out.println(">> End of E-Commerce Margin Leak Detector Simulation.");
    }
}
