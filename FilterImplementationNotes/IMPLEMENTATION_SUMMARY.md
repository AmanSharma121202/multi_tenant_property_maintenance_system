# Unified Dynamic Filter Logic Implementation - Technical Summary

## Executive Overview

We have successfully refactored all GET collection endpoints across the multi-tenant billing system to support a **single, flexible filter query parameter** instead of multiple hardcoded filter inputs. This enables clients to use expressive, readable filter syntax while maintaining strict tenant isolation and security.

### Key Improvements
✅ **Unified API**: Replace scattered query parameters with one `?filter=...` expression  
✅ **Flexible Querying**: Support logical AND (`&&`), OR (`||`), and comparison operators (`==`, `!=`, `>`, `>=`, `<`, `<=`)  
✅ **Tenant Isolation**: All queries remain scoped to the authenticated tenant  
✅ **Security**: Hardened get/update paths with tenant validation on every read/mutation  
✅ **Error Handling**: Standardized 400 responses with clear error messages for bad syntax, unknown fields, or unsupported operators  
✅ **API Documentation**: Enhanced Swagger with filter expression examples  

---

## Architecture Overview

### Before (Old Approach)
```
GET /tenants/{tenantId}/owners?status=ACTIVE&name=John
GET /tenants/{tenantId}/payments?method=UPI&unitId=A101
GET /tenants/{tenantId}/units?active=true&profileCode=1BHK
```
**Problems**: 
- Limited to predefined parameter combinations
- No support for complex logic (AND/OR across multiple fields)
- Inconsistent between endpoints
- Required backend changes to add new filter combinations

### After (New Approach)
```
GET /tenants/{tenantId}/owners?filter=status=="ACTIVE" && name=="John"
GET /tenants/{tenantId}/payments?filter=method=="UPI" && amount>=500
GET /tenants/{tenantId}/units?filter=active==true && profileCode=="1BHK"
```
**Benefits**:
- Single, composable filter parameter
- Unlimited filter combinations without code changes
- Readable, SQL-like syntax
- Consistent across all collection endpoints

---

## Implementation Details

### 1. **Filter Parser & Expression Engine**

#### File: `src/main/java/com/housing/billing/filter/ComparisonOperator.java`
Defines supported comparison operators used in filter expressions:
- `==` (equals)
- `!=` (not equals)
- `>`, `>=`, `<`, `<=` (numeric/temporal comparisons)

**Usecase**: Centralized enum ensures consistency across parser and engine.

---

#### File: `src/main/java/com/housing/billing/filter/FilterExpressionParser.java`
**Responsibility**: Tokenize and parse filter expressions into an Abstract Syntax Tree (AST).

**What it does**:
1. Tokenizes input string into meaningful tokens (identifiers, operators, literals, parentheses)
2. Parses tokens respecting operator precedence: `||` (lowest) → `&&` → comparison operators (highest)
3. Supports grouping with parentheses: `(method=="UPI" || method=="CHEQUE") && amount>=500`
4. Handles multiple literal types: strings (`"value"`), numbers (`123`, `-45.6`), booleans (`true`/`false`), null

**Example Parse Tree**:
```
Input: active==true && profileCode=="1BHK"
Output AST:
    LogicalNode(
      left: ConditionNode(active, ==, true),
      operator: AND,
      right: ConditionNode(profileCode, ==, "1BHK")
    )
```

**Usecase**: Provides a type-safe, structured representation of user-provided filter logic before execution.

---

#### File: `src/main/java/com/housing/billing/filter/DynamicFilterEngine.java`
**Responsibility**: Convert parsed AST into Java predicates and filter in-memory collections.

**What it does**:
1. Validates field names against an allowlist per entity (prevents injection/access to internal fields)
2. Validates operator-field compatibility (e.g., rejects `active > 1` for boolean fields)
3. Converts literal values to field types (e.g., `"123"` → `Integer(123)` for numeric fields)
4. Supports type conversion for: `String`, `Integer`, `Long`, `Double`, `Float`, `BigDecimal`, `Boolean`, `Instant`, `LocalDate`, `LocalDateTime`
5. Builds Java `Predicate<T>` objects and applies them to in-memory lists via `stream.filter().toList()`

**Usecase**: 
- Enforces security through field allowlisting
- Handles type safety and conversion errors gracefully
- Provides in-memory filtering for small-to-medium datasets (complements future N1QL integration for large datasets)

---

### 2. **Filter Exception Hierarchy**

Three new exception classes for fine-grained error reporting:

#### File: `src/main/java/com/housing/billing/exception/InvalidFilterSyntaxException.java`
Thrown when filter expression has **syntax errors** (e.g., unmatched parentheses, invalid tokens).

**Example**: `filter=status="ACTIVE"` → `"Expected comparison operator '==', found '='"`

---

#### File: `src/main/java/com/housing/billing/exception/UnknownFilterFieldException.java`
Thrown when filter references a **field that doesn't exist on the model or isn't filterable**.

**Example**: `filter=tenantId=="x"` → `"Unknown filter field: 'tenantId'"`

---

#### File: `src/main/java/com/housing/billing/exception/UnsupportedFilterOperatorException.java`
Thrown when **operator is incompatible with field type** or value conversion fails.

**Example**: `filter=active>1` → `"Operator '>' is not supported for field 'active'"`

---

### 3. **Global Exception Handler Integration**

#### File: `src/main/java/com/housing/billing/exception/GlobalExceptionHandler.java`
**New handlers added**:
```java
@ExceptionHandler(InvalidFilterSyntaxException.class)
→ HTTP 400 with code: INVALID_FILTER_SYNTAX

@ExceptionHandler(UnknownFilterFieldException.class)
→ HTTP 400 with code: UNKNOWN_FILTER_FIELD

@ExceptionHandler(UnsupportedFilterOperatorException.class)
→ HTTP 400 with code: UNSUPPORTED_FILTER_OPERATOR
```

**Usecase**: Standardized error responses allow clients to distinguish filter errors from other 400s and handle them appropriately.

---

### 4. **Service Layer Refactoring**

Each service's `list()` method now follows this pattern:

#### Pattern:
```java
public List<Entity> list(String tenantId, String filter) {
    // Step 1: Fetch all documents for this tenant (tenant-scoped base query)
    List<Entity> tenantScopedResults = repository.findByTenantId(tenantId);
    
    // Step 2: Apply dynamic filter (if provided)
    return dynamicFilterEngine.apply(
        tenantScopedResults, 
        filter, 
        Entity.class, 
        ALLOWED_FILTER_FIELDS
    );
}
```

**Tenant isolation**: The base query (`findByTenantId`) ensures no cross-tenant data leaks. The filter then further narrows results.

---

#### File: `src/main/java/com/housing/billing/service/UnitService.java`
**Changes**:
- Removed: `list(tenantId, unitNumber, profileCode, active, ownerId)` with branching logic
- Added: `list(tenantId, filter)` with unified filter engine
- Defined: `FILTERABLE_FIELDS = {"unitNumber", "profileCode", "active", "ownerId"}`
- **Hardened**: `get()`, `linkOwner()`, `unlinkOwner()` now validate tenant ownership

**Example Usage**:
```
GET /tenants/tenant-a/units?filter=active==true && profileCode=="1BHK"
```

---

#### File: `src/main/java/com/housing/billing/service/OwnerService.java`
**Changes**:
- Removed: `list(tenantId, name, email, phone, status)` with cascading if-statements
- Added: `list(tenantId, filter)` with allowlisted fields
- Defined: `FILTERABLE_FIELDS = {"name", "email", "phone", "status"}`
- **Hardened**: `get()`, `linkUnit()`, `unlinkUnit()` now filter by tenant

**Example Usage**:
```
GET /tenants/tenant-a/owners?filter=status=="ACTIVE" && name=="John"
```

---

#### File: `src/main/java/com/housing/billing/service/ProfileService.java`
**Changes**:
- Removed: `list(tenantId, code, active)` with conditional branches
- Added: `list(tenantId, filter)` with allowlisted fields
- Defined: `FILTERABLE_FIELDS = {"code", "label", "monthlyAmount", "active"}`
- **Hardened**: `get()` now validates tenant ownership

**Example Usage**:
```
GET /tenants/tenant-a/profiles?filter=code=="1BHK" && active==true
```

---

#### File: `src/main/java/com/housing/billing/service/InvoiceService.java`
**Changes**:
- Removed: `list(tenantId, unitId, ownerId, status, year, month)` with multiple method branches
- Added: `list(tenantId, filter)` with 10 filterable fields
- Defined: `FILTERABLE_FIELDS = {"unitId", "ownerId", "status", "year", "month", "issueDate", "dueDate", "openingBalance", "currentCharges", "closingBalance"}`
- **Hardened**: `generate()` and `recalculate()` now validate tenant on existing invoices before returning/updating

**Example Usage**:
```
GET /tenants/tenant-a/owners?filter=status=="ACTIVE" && phone=="9876543210"
GET /tenants/tenant-a/payments?filter=method=="UPI" && amount>=500
```

---

#### File: `src/main/java/com/housing/billing/service/PaymentService.java`
**Changes**:
- Removed: `list(tenantId, unitId, ownerId, invoiceId, method)` with cascading branches
- Added: `list(tenantId, filter)` with allowlisted fields
- Defined: `FILTERABLE_FIELDS = {"unitId", "ownerId", "invoiceId", "method", "amount", "receivedAt", "txnRef"}`
- **Hardened**: `get()` now validates tenant ownership

**Example Usage**:
```
GET /tenants/tenant-a/payments?filter=method=="UPI" && amount>=500
GET /tenants/tenant-a/payments?filter=ownerId=="owner::123" && receivedAt>="2025-01-01T00:00:00Z"
```

---

### 5. **Controller Layer Refactoring**

All GET list endpoints now accept a single, documented `filter` parameter:

#### File: `src/main/java/com/housing/billing/controller/UnitController.java`
```java
@GetMapping
public ResponseEntity<List<Unit>> list(
    @PathVariable String tenantId,
    @Parameter(
        description = "Unified filter expression. Example: unitNumber==\"A102\" && active==true",
        example = "unitNumber==\"A102\" && active==true"
    )
    @RequestParam(required = false) String filter
) {
    return ResponseEntity.ok(unitService.list(tenantId, filter));
}
```

**Before**: 4 separate `@RequestParam` fields  
**After**: 1 `@RequestParam` + Swagger documentation

---

#### File: `src/main/java/com/housing/billing/controller/OwnerController.java`
```java
@GetMapping
public ResponseEntity<List<Owner>> list(
    @PathVariable String tenantId,
    @Parameter(
        description = "Unified filter expression. Example: status==\"ACTIVE\" && name==\"John\"",
        example = "status==\"ACTIVE\" && name==\"John\""
    )
    @RequestParam(required = false) String filter
) {
    return ResponseEntity.ok(ownerService.list(tenantId, filter));
}
```

---

#### File: `src/main/java/com/housing/billing/controller/ProfileController.java`
```java
@GetMapping
public ResponseEntity<List<Profile>> list(
    @PathVariable String tenantId,
    @Parameter(
        description = "Unified filter expression. Example: code==\"1BHK\" && active==true",
        example = "code==\"1BHK\" && active==true"
    )
    @RequestParam(required = false) String filter
) {
    return ResponseEntity.ok(profileService.list(tenantId, filter));
}
```

---

#### File: `src/main/java/com/housing/billing/controller/InvoiceController.java`
```java
@GetMapping
public ResponseEntity<List<Invoice>> list(
    @PathVariable String tenantId,
    @Parameter(
        description = "Unified filter expression. Example: ownerId==\"owner::123\" && unitId==\"unit::A102\"",
        example = "ownerId==\"owner::123\" && unitId==\"unit::A102\""
    )
    @RequestParam(required = false) String filter
) {
    return ResponseEntity.ok(invoiceService.list(tenantId, filter));
}
```

---

#### File: `src/main/java/com/housing/billing/controller/PaymentController.java`
```java
@GetMapping
public ResponseEntity<List<Payment>> list(
    @PathVariable String tenantId,
    @Parameter(
        description = "Unified filter expression. Example: method==\"UPI\" && amount>=500",
        example = "method==\"UPI\" && amount>=500"
    )
    @RequestParam(required = false) String filter
) {
    return ResponseEntity.ok(paymentService.list(tenantId, filter));
}
```

---

### 6. **API Documentation**

#### File: `src/main/java/com/housing/billing/config/SwaggerConfig.java`
**Enhancement**: Updated OpenAPI bean description with filter syntax guide:

```
Multi-tenant housing society billing REST API.

GET collection endpoints accept a single optional `filter` query parameter.
Supported operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `&&`, `||`.

Examples:
- `unitNumber=="A-102"`
- `active==true && profileCode=="1BHK"`
- `method=="UPI" && amount>=500`
```

**Usecase**: Swagger UI now guides API consumers with:
- Syntax specification
- Operator reference
- Real-world example filters

---

### 7. **Testing & Validation**

#### File: `src/test/java/com/housing/billing/filter/DynamicFilterEngineTest.java`
**Test Coverage**:
- ✅ AND predicates with string and boolean fields
- ✅ OR predicates with numeric comparisons
- ✅ Exception handling for unknown fields
- ✅ Exception handling for bad syntax
- ✅ Exception handling for unsupported operator/type combinations

**Result**: All tests pass successfully.

---

#### File: `src/test/java/com/housing/billing/service/TenantIsolationServiceTest.java`
**Test Coverage**:
- ✅ Unit GET succeeds when tenant matches
- ✅ Unit GET throws when tenant mismatches
- ✅ Payment GET throws when tenant mismatches
- ✅ Profile GET throws when tenant mismatches

**Result**: Tenant isolation hardening verified.

---

## Security Improvements

### 1. **Field Allowlisting**
Each entity defines exactly which fields are filterable. Attempts to filter by internal fields (e.g., `tenantId`, `id`, `createdAt`) are rejected:

```java
// UnitService
FILTERABLE_FIELDS = {"unitNumber", "profileCode", "active", "ownerId"}
// NOT allowed: tenantId, id, createdAt, updatedAt, type
```

### 2. **Tenant Validation on All Reads**
Every `get()`, `linkUnit()`, `unlinkUnit()`, and mutation now validates the document's `tenantId`:

```java
return repository.findById(id)
    .filter(doc -> tenantId.equals(doc.getTenantId()))  // ENFORCED
    .orElseThrow(() -> new ResourceNotFoundException(...));
```

### 3. **Operator Type Safety**
Invalid operator/field combinations are rejected before evaluation:

```java
// These are all rejected:
filter=active>1              // can't use > on boolean
filter=status>=value         // can't use >= on string
filter=closingBalance=="yes" // can't compare number to string
```

---

## API Contract Changes

### Summary Table

| Entity | Old Parameters | New Parameter | Filterable Fields |
|--------|---|---|---|
| **Unit** | `unitNumber`, `profileCode`, `active`, `ownerId` | `filter` | 4 fields |
| **Owner** | `name`, `email`, `phone`, `status` | `filter` | 4 fields |
| **Profile** | `code`, `active` | `filter` | 4 fields |
| **Invoice** | `unitId`, `ownerId`, `status`, `year`, `month` | `filter` | 10 fields |
| **Payment** | `unitId`, `ownerId`, `invoiceId`, `method` | `filter` | 7 fields |

### Migration Guide for Clients

**Old API Call**:
```bash
curl "http://api/tenants/t1/units?active=true&profileCode=1BHK"
```

**New API Call**:
```bash
curl "http://api/tenants/t1/units?filter=active==true%20%26%26%20profileCode==%221BHK%22"
```
(URL-decoded: `?filter=active==true && profileCode=="1BHK"`)

### Backward Compatibility
**Status**: ⚠️ Breaking Change  
**Mitigation**: 
- Old query parameters are no longer accepted
- Clear error messages guide to new syntax
- Swagger examples show migration path
- Consider deprecation phase if gradual rollout needed

---

## Performance Characteristics

### Scalability
- **Small datasets** (<10K records): In-memory filtering is fast and efficient
- **Medium datasets** (10K–100K records): Performance remains acceptable for most queries
- **Large datasets** (>100K): Consider future optimization via Couchbase N1QL predicates

### Complexity
- **Parser**: O(n) where n = filter string length
- **Filter execution**: O(m) where m = dataset size
- **Overall**: O(n + m) — linear with respect to input and data

### Future Optimization Path
Current implementation filters in-memory post-fetch. Future versions can:
1. Translate filter AST directly to Couchbase N1QL for server-side filtering
2. Add pagination support (`?filter=...&limit=50&offset=0`)
3. Add sorting support (`?filter=...&sort=field,asc`)


---

## Summary of Files Changed

### New Files Created:
1. `src/main/java/com/housing/billing/filter/ComparisonOperator.java`
2. `src/main/java/com/housing/billing/filter/FilterExpressionParser.java`
3. `src/main/java/com/housing/billing/filter/DynamicFilterEngine.java`
4. `src/main/java/com/housing/billing/exception/InvalidFilterSyntaxException.java`
5. `src/main/java/com/housing/billing/exception/UnknownFilterFieldException.java`
6. `src/main/java/com/housing/billing/exception/UnsupportedFilterOperatorException.java`
7. `src/test/java/com/housing/billing/filter/DynamicFilterEngineTest.java`
8. `src/test/java/com/housing/billing/service/TenantIsolationServiceTest.java`

### Modified Files:
1. `src/main/java/com/housing/billing/service/UnitService.java` — Unified filter + tenant validation
2. `src/main/java/com/housing/billing/service/OwnerService.java` — Unified filter + tenant validation
3. `src/main/java/com/housing/billing/service/ProfileService.java` — Unified filter + tenant validation
4. `src/main/java/com/housing/billing/service/InvoiceService.java` — Unified filter + tenant validation
5. `src/main/java/com/housing/billing/service/PaymentService.java` — Unified filter + tenant validation
6. `src/main/java/com/housing/billing/controller/UnitController.java` — Single filter parameter + Swagger docs
7. `src/main/java/com/housing/billing/controller/OwnerController.java` — Single filter parameter + Swagger docs
8. `src/main/java/com/housing/billing/controller/ProfileController.java` — Single filter parameter + Swagger docs
9. `src/main/java/com/housing/billing/controller/InvoiceController.java` — Single filter parameter + Swagger docs
10. `src/main/java/com/housing/billing/controller/PaymentController.java` — Single filter parameter + Swagger docs
11. `src/main/java/com/housing/billing/exception/GlobalExceptionHandler.java` — New filter error handlers
12. `src/main/java/com/housing/billing/config/SwaggerConfig.java` — Enhanced API documentation

---



