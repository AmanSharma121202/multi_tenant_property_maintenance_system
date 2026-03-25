# Unified Filter Architecture Diagram

## Request Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│ CLIENT REQUEST                                                      │
│ GET /tenants/t1/units?filter=active==true && profileCode=="1BHK"    │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ SPRING CONTROLLER (UnitController.list)                             │
│ ✓ Validates tenantId from path                                     │
│ ✓ Extracts filter parameter                                        │
│ ✓ Calls unitService.list(tenantId, filter)                         │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ SERVICE LAYER (UnitService.list)                                    │
│                                                                     │
│  Step 1: Tenant-Scoped Base Query                                  │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │ unitRepository.findByTenantId(tenantId)                   │    │
│  │ → Returns: [Unit1(t1), Unit2(t1), Unit3(t1)]             │    │
│  │ (Only documents where tenantId == "t1")                  │    │
│  └───────────────────────────────────────────────────────────┘    │
│                                                                     │
│  Step 2: Apply Dynamic Filter                                     │
│  ┌───────────────────────────────────────────────────────────┐    │
│  │ dynamicFilterEngine.apply(                                │    │
│  │   tenantScopedUnits,                                      │    │
│  │   "active==true && profileCode==\"1BHK\"",               │    │
│  │   Unit.class,                                             │    │
│  │   FILTERABLE_FIELDS                                       │    │
│  │ )                                                          │    │
│  └───────────────────────────────────────────────────────────┘    │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ DYNAMIC FILTER ENGINE (DynamicFilterEngine)                         │
│                                                                     │
│  if (filter == null || filter.isBlank())                           │
│    return source as-is  ← No filtering needed                      │
│  else                                                              │
│    ┌──────────────────────────────────────────────────────────┐   │
│    │ 1. PARSE: Convert filter string to AST                  │   │
│    │    FilterExpressionParser.parse(filter)                 │   │
│    │    Input:  "active==true && profileCode==\"1BHK\""      │   │
│    │    Output: LogicalNode(                                 │   │
│    │              ConditionNode(active, ==, true),           │   │
│    │              AND,                                       │   │
│    │              ConditionNode(profileCode, ==, "1BHK")     │   │
│    │            )                                            │   │
│    └──────────────────────────────────────���───────────────────┘   │
│                         │                                          │
│                         ▼                                          │
│    ┌──────────────────────────────────────────────────────────┐   │
│    │ 2. VALIDATE: Check fields & operators                   │   │
│    │    - "active" in FILTERABLE_FIELDS? ✓                   │   │
│    │    - "profileCode" in FILTERABLE_FIELDS? ✓              │   │
│    │    - "==" with Boolean type? ✓                          │   │
│    │    - "==" with String type? ✓                           │   │
│    │    - ">" with Boolean type? ✗ REJECT                    │   │
│    │    - Unknown field "tenantId"? ✗ REJECT                 │   │
│    └──────────────────────────────────────────────────────────┘   │
│                         │                                          │
│                         ▼                                          │
│    ┌──────────────────────────────────────────────────────────┐   │
│    │ 3. BUILD PREDICATES: Create Java Predicate<Unit>        │   │
│    │    Predicate1: unit.isActive() == true                  │   │
│    │    Predicate2: unit.getProfileCode().equals("1BHK")     │   │
│    │    Combined:   Predicate1.and(Predicate2)               │   │
│    └──────────────────────────────────────────────────────────┘   │
│                         │                                          │
│                         ▼                                          │
│    ┌──────────────────────────────────────────────────────────┐   │
│    │ 4. FILTER: Apply predicates to stream                   │   │
│    │    source.stream()                                       │   │
│    │      .filter(predicate)                                  │   │
│    │      .toList()                                           │   │
│    │                                                          │   │
│    │    Input:  [Unit1(active,1BHK), Unit2(active,2BHK),     │   │
│    │            Unit3(inactive,1BHK), Unit4(active,1BHK)]    │   │
│    │    Output: [Unit1, Unit4]  ← Only matching both         │   │
│    └──────────────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ RESPONSE                                                            │
│ HTTP 200 OK                                                         │
│ [                                                                   │
│   {                                                                 │
│     "id": "unit::A101",                                            │
│     "active": true,                                                  │
│     "profileCode": "1BHK",                                         │
│     "ownerId": "owner::123",                                       │
│     ...                                                             │
│   },                                                                │
│   { ... }                                                           │
│ ]                                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Error Handling Flow

```
┌──────────────────────────────────────────────────────────┐
│ CLIENT REQUEST WITH BAD FILTER                           │
│ GET /tenants/t1/owners?filter=status="ACTIVE"            │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ FilterExpressionParser.parse()│
        │ Tokenizes: status = ACTIVE    │
        │ Expected: status == "ACTIVE"  │
        │ ERROR: Unexpected token '='   │
        └──────────────┬────────────────┘
                       │
                       ▼
           InvalidFilterSyntaxException
            "Expected comparison operator '==', 
             found '=' at position 6"
                       │
                       ▼
        ┌────────────────────────────────┐
        │ GlobalExceptionHandler catches │
        │ Maps to HTTP 400               │
        └────────────────┬───────────────┘
                         │
                         ▼
        ┌──────────────────────────────────────────┐
        │ HTTP 400 BAD REQUEST                     │
        │ {                                        │
        │   "code": "INVALID_FILTER_SYNTAX",      │
        │   "message": "Expected comparison ...",  │
        │   "timestamp": "2025-03-25T10:30:00Z"   │
        │ }                                        │
        └──────────────────────────────────────────┘
```

### Error Scenarios

```
┌─────────────────────────────────────────────────────────────┐
│ 1. SYNTAX ERROR                                             │
│    filter=status="ACTIVE"                                   │
│    → InvalidFilterSyntaxException                           │
│    → HTTP 400: INVALID_FILTER_SYNTAX                        │
├─────────────────────────────────────────────────────────────┤
│ 2. UNKNOWN FIELD                                            │
│    filter=tenantId=="t1"  (tenantId not in allowlist)       │
│    → UnknownFilterFieldException                            │
│    → HTTP 400: UNKNOWN_FILTER_FIELD                         │
├─────────────────────────────────────────────────────────────┤
│ 3. UNSUPPORTED OPERATOR/TYPE                                │
│    filter=active>1  (can't use > on boolean)                │
│    → UnsupportedFilterOperatorException                     │
│    → HTTP 400: UNSUPPORTED_FILTER_OPERATOR                  │
├─────────────────────────────────────────────────────────────┤
│ 4. INVALID VALUE TYPE                                       │
│    filter=active=="abc"  (expecting boolean, got string)     │
│    → UnsupportedFilterOperatorException                     │
│    → HTTP 400: UNSUPPORTED_FILTER_OPERATOR                  │
├─────────────────────────────────────────────────────────────┤
│ 5. TENANT MISMATCH (get/update only)                        │
│    GET /tenants/t1/units/unit::A101                        │
│    But unit.tenantId == "t2"                                │
│    → ResourceNotFoundException                              │
│    → HTTP 404: NOT_FOUND                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Data Security Model

```
┌────────────────────────────────────────────────���────────────┐
│ TENANT ISOLATION LAYERS                                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ LAYER 1: Repository Query (Base Scoping)                  │
│ ┌─────────────────────────────────────────────────────────┐
│ │ unitRepository.findByTenantId(tenantId)                 │
│ │ ↓                                                       │
│ │ Couchbase Query:                                        │
│ │ SELECT * FROM units WHERE tenantId == "t1"             │
│ │ ↓                                                       │
│ │ Result: [Unit1(t1), Unit2(t1)]                         │
│ │ ✓ Never includes Unit3(t2)                             │
│ └─────────────────────────────────────────────────────────┘
│                         │                                   │
│                         ▼                                   │
│ LAYER 2: Dynamic Filter Engine (Secondary Filtering)      │
│ ┌─────────────────────────────────────────────────────────┐
│ │ Receives: [Unit1(t1), Unit2(t1)]                       │
│ │ Applies: active==true && profileCode=="1BHK"          │
│ │ Result: [Unit1(t1)]                                    │
│ │ ✓ Tenant filter already applied in LAYER 1             │
│ └─────────────────────────────────────────────────────────┘
│                         │                                   │
│                         ▼                                   │
│ LAYER 3: Field Allowlisting (Access Control)              │
│ ┌─────────────────────────────────────────────────────────┐
│ │ FILTERABLE_FIELDS = {unitNumber, profileCode, active}  │
│ │                                                         │
│ │ Allowed:   filter=active==true      ✓                  │
│ │            filter=profileCode=="1BHK" ✓               │
│ │                                                         │
│ │ Rejected:  filter=tenantId=="x"   ✗                    │
│ │            filter=id=="unit::1"   ���                    │
│ │            filter=createdAt>"..."  ✗                   │
│ │ ✓ Prevents accidental or malicious access to           │
│ │   internal/sensitive fields                            │
│ └─────────────────────────────────────────────────────────┘
│                         │                                   │
│                         ▼                                   │
│ LAYER 4: Tenant Check on get/update/delete (Per-Op)      │
│ ┌─────────────────────────────────────────────────────────┐
│ │ GET /tenants/t1/units/{unitId}                         │
│ │ ↓                                                       │
│ │ Service: unitRepository.findById(unitId)               │
│ │           .filter(unit → unit.tenantId == "t1")        │
│ │ ↓                                                       │
│ │ If matches: return unit  ✓                             │
│ │ If mismatch: throw 404      ✓                          │
│ │ ✓ Prevents cross-tenant read/update/delete             │
│ └─────────────────────────────────────────────────────────┘
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Filter Expression Examples

```
┌─────────────────────────────────────────────────────────────────┐
│ FILTER EXPRESSIONS: EXAMPLES                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ SIMPLE EQUALITY:                                                │
│  filter=status=="ACTIVE"                                        │
│  filter=method=="UPI"                                           │
│                                                                 │
│ NUMERIC COMPARISON:                                             │
│  filter=amount>=500                                             │
│  filter=monthlyAmount>2000 && monthlyAmount<10000               │
│  filter=amount<=5000                                             │
│                                                                 │
│ LOGICAL AND:                                                    │
│  filter=status=="ACTIVE" && name=="John"                       │
│  filter=active==true && profileCode=="1BHK"                     │
│                                                                 │
│ LOGICAL OR:                                                     │
│  filter=status=="ACTIVE" || status=="INACTIVE"                  │
│  filter=method=="UPI" || method=="CHEQUE"                       │
│                                                                 │
│ GROUPING WITH PARENTHESES:                                      │
│  filter=(method=="UPI" || method=="CHEQUE") && amount>=500      │
│  filter=(active==true && profileCode=="1BHK") || (active==true  │
│           && profileCode=="2BHK")                               │
│                                                                 │
│ NEGATION:                                                       │
│  filter=status!="INACTIVE"                                      │
│  filter=active!=false                                           │
│                                                                 │
│ DATE/TEMPORAL:                                                  │
│  filter=receivedAt>="2025-01-01T00:00:00Z"                      │
│  filter=createdAt<"2025-12-31T23:59:59Z"                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Before vs After Comparison

### Old Approach (Before Refactor)

```java
// UnitController.java
@GetMapping
public ResponseEntity<List<Unit>> list(
    @PathVariable String tenantId,
    @RequestParam(required = false) String unitNumber,
    @RequestParam(required = false) String profileCode,
    @RequestParam(required = false) Boolean active,
    @RequestParam(required = false) String ownerId
) {
    return ResponseEntity.ok(unitService.list(tenantId, unitNumber, profileCode, active, ownerId));
}

// UnitService.java
public List<Unit> list(String tenantId, String unitNumber, String profileCode, 
                       Boolean active, String ownerId) {
    // Multiple branching paths - poor maintainability
    if (unitNumber != null && !unitNumber.isBlank()) {
        return unitRepository.findByTenantIdAndUnitNumber(tenantId, "%" + unitNumber + "%");
    }
    if (profileCode != null && !profileCode.isBlank()) {
        return unitRepository.findByTenantIdAndProfileCode(tenantId, profileCode);
    }
    if (active != null) {
        return unitRepository.findByTenantIdAndActive(tenantId, active);
    }
    if (ownerId != null && !ownerId.isBlank()) {
        return unitRepository.findByTenantIdAndOwnerId(tenantId, ownerId);
    }
    return unitRepository.findByTenantId(tenantId);
}

// API Call:
GET /tenants/t1/units?unitNumber=A102&active=true
// Cannot combine: unitNumber AND active together!
// To add new filter combination, modify service.
```

### New Approach (After Refactor)

```java
// UnitController.java
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

// UnitService.java
public List<Unit> list(String tenantId, String filter) {
    // Clean, reusable - single code path
    List<Unit> tenantScopedUnits = unitRepository.findByTenantId(tenantId);
    return dynamicFilterEngine.apply(tenantScopedUnits, filter, Unit.class, FILTERABLE_FIELDS);
}

// API Calls - all work without code changes:
GET /tenants/t1/units?filter=unitNumber=="A102"
GET /tenants/t1/units?filter=active==true
GET /tenants/t1/units?filter=unitNumber=="A102" && active==true
GET /tenants/t1/units?filter=active==true || profileCode=="2BHK"
// Future combinations: just change query, no backend changes!
```

---



