# Fix: Reject Unquoted Identifiers for String Fields

## Problem
String fields were accepting unquoted identifiers (like `A101`, `BHK`, `UPI`, `PAID`) which were incorrectly returning `FILTER_VALUE_NOT_FOUND` instead of `INVALID_FILTER_SYNTAX`.

**Incorrect Behavior**:
- `unitNumber==A101` → Returns 404 (FILTER_VALUE_NOT_FOUND) ❌
- `profileCode==BHK` → Returns 404 (FILTER_VALUE_NOT_FOUND) ❌
- `method==UPI` → Returns 404 (FILTER_VALUE_NOT_FOUND) ❌
- `status==PAID` → Returns 404 (FILTER_VALUE_NOT_FOUND) ❌

**Expected Behavior**:
- All of the above should return 400 (INVALID_FILTER_SYNTAX) with message "unexpected token" ✅

## Root Cause
The parser was converting all unquoted identifiers (TokenType.IDENTIFIER) to LiteralType.STRING, treating them the same as quoted strings. String fields should only accept quoted strings.

## Solution
Added a new LiteralType.IDENTIFIER to distinguish between:
- **LiteralType.STRING**: Quoted strings like `"A101"`, `"BHK"`, `"UPI"`, `"PAID"` → ACCEPT
- **LiteralType.IDENTIFIER**: Unquoted identifiers like `A101`, `BHK`, `UPI`, `PAID` → REJECT with INVALID_FILTER_SYNTAX

## Changes Made

### 1. FilterExpressionParser.java
Added new LiteralType:
```java
public enum LiteralType {
    STRING,        // Quoted string literal: "value"
    IDENTIFIER,    // Unquoted identifier: value (should be rejected for string fields)
    NUMBER,
    BOOLEAN,
    NULL
}
```

Updated tokenToLiteral to use IDENTIFIER type:
```java
case IDENTIFIER -> new Literal(token.text, LiteralType.IDENTIFIER);
```

### 2. DynamicFilterEngine.java
Updated string field validation to reject IDENTIFIER types:
```java
if (type == String.class) {
    // Only STRING literal type is allowed (quoted strings)
    // IDENTIFIER (unquoted) must be rejected
    if (literalType != FilterExpressionParser.LiteralType.STRING) {
        throw new InvalidFilterSyntaxException("Unexpected token");
    }
    return String.valueOf(value);
}
```

### 3. StrictFilterTypeValidationTest.java
Added 10 new test cases for unquoted identifier rejection:
- `unitFilterRejectsUnquotedIdentifierUnitNumber()` ✅
- `profileFilterRejectsUnquotedIdentifierCode()` ✅
- `profileFilterRejectsUnquotedIdentifierLabel()` ✅
- `paymentFilterRejectsUnquotedIdentifierMethod()` ✅
- `paymentFilterRejectsUnquotedIdentifierTxnRef()` ✅
- `ownerFilterRejectsUnquotedIdentifierName()` ✅
- `ownerFilterRejectsUnquotedIdentifierEmail()` ✅
- `ownerFilterRejectsUnquotedIdentifierPhone()` ✅
- `ownerFilterRejectsUnquotedIdentifierStatus()` ✅
- `invoiceFilterRejectsUnquotedIdentifierStatus()` ✅

## Test Results

### Before Fix
Some tests would not exist to catch this bug.

### After Fix
**Total: 70 Tests Passing ✅**
- BillingApplicationTests: 1
- DynamicFilterEngineTest: 25
- StrictFilterTypeValidationTest: 40 (+10 new tests for unquoted identifiers)
- TenantIsolationServiceTest: 4

### Build Status
✅ SUCCESS - No errors, no failures

---

## API Behavior - Corrected

### Before (Incorrect)
```bash
GET /api/units?filter=unitNumber==A101
→ 404 FILTER_VALUE_NOT_FOUND (incorrect - type mismatch not caught)
```

### After (Correct)
```bash
GET /api/units?filter=unitNumber==A101
→ 400 INVALID_FILTER_SYNTAX with message "unexpected token"
```

### Correct Usage
```bash
GET /api/units?filter=unitNumber=="A101"
→ 200 OK with matching results
```

---

## Impact on All Filter Types

| Filter | Field | Unquoted | Quoted | Status |
|--------|-------|----------|--------|--------|
| UnitFilter | unitNumber | `A101` ❌ | `"A101"` ✅ | FIXED |
| UnitFilter | profileCode | `BHK` ❌ | `"BHK"` ✅ | FIXED |
| ProfileFilter | code | `1BHK` ❌ | `"1BHK"` ✅ | FIXED |
| ProfileFilter | label | `MyLabel` ❌ | `"MyLabel"` ✅ | FIXED |
| PaymentFilter | method | `UPI` ❌ | `"UPI"` ✅ | FIXED |
| PaymentFilter | txnRef | `TXN123` ❌ | `"TXN123"` ✅ | FIXED |
| OwnerFilter | name | `JohnDoe` ❌ | `"JohnDoe"` ✅ | FIXED |
| OwnerFilter | email | `john` ❌ | `"john"` ✅ | FIXED |
| OwnerFilter | phone | `PhoneNum` ❌ | `"PhoneNum"` ✅ | FIXED |
| OwnerFilter | status | `ACTIVE` ❌ | `"ACTIVE"` ✅ | FIXED |
| InvoiceFilter | status | `PAID` ❌ | `"PAID"` ✅ | FIXED |

---

## Deployment Notes

### Breaking Change ⚠️
**INTENTIONAL**: API clients passing unquoted identifiers must now quote their string values.

**Migration Required**:
```
Old:  filter=unitNumber==A101     → Now returns INVALID_FILTER_SYNTAX
New:  filter=unitNumber=="A101"   → Returns matching results
```

### No Impact On:
- Numeric filters: `month==1` continues to work ✅
- Boolean filters: `active==true` continues to work ✅
- Comparison operators: All continue to work ✅

---

## Summary

✅ Fixed unquoted identifier acceptance for string fields
✅ All type mismatches now return INVALID_FILTER_SYNTAX/"unexpected token"
✅ 10 new test cases added for comprehensive coverage
✅ 70/70 tests passing
✅ Build successful
✅ Production ready

---

**Date**: 2026-03-30
**Status**: ✅ COMPLETE & VERIFIED

