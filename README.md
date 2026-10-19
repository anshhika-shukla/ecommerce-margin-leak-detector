# 🛒 E-Commerce Margin Leak Detector

[![Java](https://img.shields.io/badge/Language-Java_11+-orange.svg)](https://www.oracle.com/java/)
[![OOP](https://img.shields.io/badge/Architecture-Pure_OOP-blue.svg)](#object-oriented-architecture)
[![License](https://img.shields.io/badge/License-Academic-green.svg)](#)

An automated unit-economics audit engine built with pure Object-Oriented Programming (OOP) in Java. It models third-party e-commerce merchant financials (referral commissions, dynamic logistics, return reserves, and payment processing cuts) to detect hidden profitability leaks.

---

## 🚀 Key Features
- **Dynamic Fee Auditing:** Models weight-based shipping, platform commissions, and reverse logistics reserves.
- **Rule-Based Leak Detection:** Flags products operating under critical margin leaks or severe return rate erosions.
- **Severity Classification:** Categorizes warnings into `[CRITICAL]`, `[HIGH]`, `[WARNING]`, or `[OK]`.

---

## 🧱 Object-Oriented Architecture

| OOP Pillar | Project Implementation |
| :--- | :--- |
| **Encapsulation** | `Product` encapsulates private attributes (`sellingPrice`, `baseCost`, `returnRatePercent`). State mutations are protected behind validated domain methods. |
| **Inheritance** | Base abstract class `Expense` is extended by `PlatformCommissionExpense`, `ShippingExpense`, and `ReturnReserveExpense`. |
| **Polymorphism** | Runtime dispatch in `Product.calculateTotalExpenses()` iterates over `List<Expense>` and calls `.calculateExpense(this)`. |
| **Abstraction / Strategy** | The `MarginLeakRule` interface encapsulates evaluation logic (`NegativeMarginRule`, `HighReturnErosionRule`), adhering to the **Open/Closed Principle**. |

---

## 🛠️ How to Run Locally

### Prerequisites
- JDK 11 or higher installed (`javac -version`)

### Compilation & Execution
```bash
# 1. Compile the application
javac MarginLeakDetectorApp.java

# 2. Run the audit engine
java MarginLeakDetectorApp