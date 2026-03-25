# Quick Reference: Filter Implementation Guide


**What Was Done:**  
Refactored all GET collection endpoints to use a single `?filter=...` query parameter instead of multiple hardcoded parameters.

**Why It Matters:**  
- Clients can now filter by any combination of fields without backend code changes
- Security hardened with tenant isolation checks on every read/mutation


---

## For Your Development Team

### File Structure

```
src/main/java/com/housing/billing/
├── filter/                          ← NEW: Filter infrastructure
│   ├── ComparisonOperator.java      (9 lines)   - Operator enums
│   ├── FilterExpressionParser.java  (256 lines) - Parser/tokenizer/AST builder
│   └── DynamicFilterEngine.java     (200 lines) - Predicate builder & executor
│
├── exception/                       ← EXTENDED: New filter exceptions
│   ├── InvalidFilterSyntaxException.java        - Bad syntax errors
│   ├── UnknownFilterFieldException.java         - Unknown field errors
│   └── UnsupportedFilterOperatorException.java  - Type mismatch errors
│
├── service/                         ← REFACTORED: 5 services updated
│   ├── UnitService.java             - list() method refactored
│   ├── OwnerService.java            - list() method refactored
│   ├── ProfileService.java          - list() method refactored
│   ├── InvoiceService.java          - list() method refactored
│   └── PaymentService.java          - list() method refactored
│
├── controller/                      ← UPDATED: 5 controllers
│   ├── UnitController.java          - @RequestParam filter + Swagger
│   ├── OwnerController.java         - @RequestParam filter + Swagger
│   ├── ProfileController.java       - @RequestParam filter + Swagger
│   ├── InvoiceController.java       - @RequestParam filter + Swagger
│   └── PaymentController.java       - @RequestParam filter + Swagger
│
└── config/
    └── SwaggerConfig.java           - ENHANCED: Filter syntax docs
```

---

### How to Use the Filter Engine in Your Code

#### Pattern 1: Simple List with Filter

```java
public List<Unit> list(String tenantId, String filter) {
    // Step 1: Fetch all docs for this tenant (tenant-scoped base query)
    List<Unit> tenantScopedUnits = unitRepository.findByTenantId(tenantId);
    
    // Step 2: Apply dynamic filter (if provided)
    return dynamicFilterEngine.apply(
        tenantScopedUnits, 
        filter, 
        Unit.class, 
        FILTERABLE_FIELDS
    );
}
```

#### Pattern 2: Define Filterable Fields

```java
// In service class:
private static final Set<String> FILTERABLE_FIELDS = Set.of(
    "unitNumber", "profileCode", "active", "ownerId"
);
```

⚠️ **Security**: Only list fields clients should be able to filter by. Omit internal fields like `tenantId`, `id`, `createdAt`, `updatedAt`.

#### Pattern 3: Add Tenant Validation to get/update/delete

```java
public Unit get(String tenantId, String unitId) {
    return unitRepository.findById(unitId)
            .filter(unit -> tenantId.equals(unit.getTenantId()))  // ← Tenant check
            .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
}
```

#### Pattern 4: Update Controller with Swagger Docs

```java
@GetMapping
public ResponseEntity<List<Unit>> list(
    @PathVariable String tenantId,
    @Parameter(
        description = "Unified filter expression. Example: active==true && profileCode==\"1BHK\"",
        example = "active==true && profileCode==\"1BHK\""
    )
    @RequestParam(required = false) String filter
) {
    return ResponseEntity.ok(unitService.list(tenantId, filter));
}
```

---

### Filter Expression Syntax Cheat Sheet

#### Basic Operators

```
==  Equals           filter=status=="ACTIVE"
!=  Not equals       filter=status!="INACTIVE"
>   Greater than     filter=amount>500
>=  Greater/equal    filter=amount>=500
<   Less than        filter=monthlyAmount<6000
<=  Less/equal       filter=monthlyAmount<=6000
```

#### Logical Operators

```
&&  AND logic        filter=active==true && profileCode=="1BHK"
||  OR logic         filter=method=="UPI" || method=="CHEQUE"
()  Grouping         filter=(method=="UPI" || method=="CHEQUE") && amount>=500
```

#### Supported Types

```
String      "value"              filter=status=="ACTIVE"
Integer     123                  filter=amount==500
Long        123456789            filter=id==123456789
Double      123.45               filter=amount==123.45
BigDecimal  123.45               filter=monthlyAmount==123.45
Boolean     true / false          filter=active==true
Instant     ISO-8601 timestamp   filter=receivedAt>="2025-01-01T00:00:00Z"
LocalDate   YYYY-MM-DD           filter=date=="2025-01-15"
null        null                 filter=notes==null
```

#### Real-World Examples

```
Single field:
  filter=status=="ACTIVE"
  filter=method=="UPI"
  filter=active==true

Multiple fields (AND):
  filter=status=="ACTIVE" && name=="John"
  filter=active==true && profileCode=="1BHK"
  filter=amount>=500 && method=="UPI"

OR conditions:
  filter=status=="ACTIVE" || status=="INACTIVE"
  filter=method=="UPI" || method=="BANK_TRANSFER"

Complex logic:
  filter=(method=="UPI" || method=="CHEQUE") && amount>=500
  filter=active==true && (profileCode=="1BHK" || profileCode=="2BHK")
  filter=monthlyAmount!=0 && createdAt>="2025-01-01T00:00:00Z"
```

---

### Error Handling

#### Syntax Error
```
Request:  ?filter=status="ACTIVE"
Response: 400 BAD REQUEST
{
  "code": "INVALID_FILTER_SYNTAX",
  "message": "Expected comparison operator '==', found '=' at position 7"
}
```

#### Unknown Field
```
Request:  ?filter=tenantId=="t1"
Response: 400 BAD REQUEST
{
  "code": "UNKNOWN_FILTER_FIELD",
  "message": "Unknown filter field: 'tenantId'"
}
```

#### Type Mismatch
```
Request:  ?filter=active>1
Response: 400 BAD REQUEST
{
  "code": "UNSUPPORTED_FILTER_OPERATOR",
  "message": "Operator '>' is not supported for field 'active'"
}
```

---

### Testing Checklist

```
☐ Parser Tests (DynamicFilterEngineTest.java)
  ├── Single condition: status=="ACTIVE"
  ├── AND condition: active==true && profileCode=="1BHK"
  ├── OR condition: method=="UPI" || method=="CHEQUE"
  ├── Grouped: (method=="UPI" || method=="CHEQUE") && amount>=500
  ├── Invalid syntax: status="ACTIVE" → Throws InvalidFilterSyntaxException
  ├── Unknown field: tenantId=="x" → Throws UnknownFilterFieldException
  └── Type mismatch: active>1 → Throws UnsupportedFilterOperatorException

☐ Service Tests (TenantIsolationServiceTest.java)
  ├── get() with matching tenantId → Returns document
  ├── get() with non-matching tenantId → Throws 404
  ├── linkUnit() validates tenant → Prevents cross-tenant linking
  └── update() validates tenant → Prevents cross-tenant mutation

☐ Integration Tests (Manual/E2E)
  ├── Test via Swagger UI
  ├── Test complex filters (3–4 conditions)
  ├── Test error paths
  ├── Load test with 10K+ records
  └── Verify response time acceptable
```

---

### Performance Notes

#### In-Memory Filtering (Current)
- **Scalability**: < 100K records = fast and acceptable
- **Complexity**: O(n) where n = record count
- **Latency**: Typically < 100ms for 10K records

#### Future Optimization: Server-Side N1QL
```java
// Future: Translate AST directly to Couchbase N1QL
// SELECT * FROM units WHERE tenantId = "t1" 
//   AND active = true AND profileCode = "1BHK"
// → Server-side filtering for large datasets
```

---

### Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| **Filter not working** | Syntax error in expression | Check error response for exact position of error |
| **Returns too many results** | Field not in allowlist | Add field to FILTERABLE_FIELDS in service |
| **404 Not Found** | Tenant mismatch | Ensure tenantId in URL matches document's tenantId |
| **Type error** | Wrong literal type | Use correct type: "string" (quotes), 123 (no quotes), true (no quotes) |
| **Slow response** | Large dataset (>100K) | Consider pagination or future N1QL optimization |

---

### Code Review Checklist

When reviewing filter-related changes:

```
☐ Filter parameter is @RequestParam(required = false) — allows no filter
☐ Service calls dynamicFilterEngine.apply() with:
  ├── tenantScopedResults (from findByTenantId)
  ├── filter parameter
  ├── Entity.class
  └── FILTERABLE_FIELDS set

☐ FILTERABLE_FIELDS defined as private static final Set<String>

☐ All get/update/delete methods validate tenant:
  return repo.findById(id)
      .filter(doc -> tenantId.equals(doc.getTenantId()))  ← Check this!
      .orElseThrow(...)

☐ Controller has @Parameter with description + example

☐ No branching if-statements for different filter combinations

☐ GlobalExceptionHandler has filter exception handlers

☐ Tests cover:
  ├── Valid filter expressions
  ├── Invalid syntax
  ├── Unknown fields
  ├── Type mismatches
  └── Tenant isolation
```

---

### Common Modifications

#### Adding a New Filterable Field

1. **Service**: Add field to FILTERABLE_FIELDS
   ```java
   private static final Set<String> FILTERABLE_FIELDS = Set.of(
       "status", "month", "year", "newField"  ← Add here
   );
   ```

2. **Done** — No controller or engine changes needed!

#### Adding a New Entity to Support Filtering

1. Create/update service list method:
   ```java
   public List<MyEntity> list(String tenantId, String filter) {
       List<MyEntity> scoped = repository.findByTenantId(tenantId);
       return dynamicFilterEngine.apply(scoped, filter, MyEntity.class, FILTERABLE_FIELDS);
   }
   ```

2. Define FILTERABLE_FIELDS:
   ```java
   private static final Set<String> FILTERABLE_FIELDS = Set.of("field1", "field2");
   ```

3. Update controller:
   ```java
   @GetMapping
   public ResponseEntity<List<MyEntity>> list(
       @PathVariable String tenantId,
       @Parameter(description = "...", example = "field1==\"value\"")
       @RequestParam(required = false) String filter
   ) {
       return ResponseEntity.ok(service.list(tenantId, filter));
   }
   ```

4. Validate tenant in get/update/delete:
   ```java
   public MyEntity get(String tenantId, String id) {
       return repo.findById(id)
           .filter(doc -> tenantId.equals(doc.getTenantId()))
           .orElseThrow(...);
   }
   ```

---



