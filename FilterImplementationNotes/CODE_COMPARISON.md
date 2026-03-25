# Code Comparison: Before & After

## 1. UnitController

### Before
```java
@RestController
@RequestMapping("/tenants/{tenantId}/units")
@RequiredArgsConstructor
public class UnitController {
    private final UnitService unitService;

    @GetMapping
    public ResponseEntity<List<Unit>> list(
            @PathVariable String tenantId,
            @RequestParam(required = false) String unitNumber,
            @RequestParam(required = false) String profileCode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String ownerId) {
        return ResponseEntity.ok(unitService.list(tenantId, unitNumber, profileCode, active, ownerId));
    }
    // ... other methods
}
```

**Problems**:
- 4 separate query parameters  
- No documentation of parameter combinations
- Swagger UI shows 4 separate input boxes

### After
```java
@RestController
@RequestMapping("/tenants/{tenantId}/units")
@RequiredArgsConstructor
public class UnitController {
    private final UnitService unitService;

    @GetMapping
    public ResponseEntity<List<Unit>> list(
            @PathVariable String tenantId,
            @Parameter(
                    description = "Unified filter expression. Example: unitNumber==\"A102\" && active==true",
                    example = "unitNumber==\"A102\" && active==true"
            )
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(unitService.list(tenantId, filter));
    }
    // ... other methods
}
```

**Benefits**:
- Single, flexible filter parameter  
- Swagger example shows intended usage
- API documentation self-explanatory

---

## 2. UnitService

### Before
```java
@Service
@RequiredArgsConstructor
public class UnitService {
    private final UnitRepository unitRepository;
    private final OwnerRepository ownerRepository;

    public List<Unit> list(String tenantId, String unitNumber, String profileCode,
                           Boolean active, String ownerId) {
        // Cascading if-statements: complex branching logic
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

    public Unit get(String tenantId, String unitId) {
        // ⚠️ Missing tenant validation!
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
    }
    // ... other methods
}
```

**Problems**:
- Multiple repository method calls duplicating tenant scoping
- Cannot combine filters (e.g., unitNumber AND active)
- `get()` method has no tenant check → cross-tenant read vulnerability
- To add new filter combo, must modify this method
- ~20 lines of branching logic

### After
```java
@Service
@RequiredArgsConstructor
public class UnitService {
    private final UnitRepository unitRepository;
    private final OwnerRepository ownerRepository;
    private final DynamicFilterEngine dynamicFilterEngine;

    private static final Set<String> FILTERABLE_FIELDS = Set.of(
            "unitNumber", "profileCode", "active", "ownerId"
    );

    public List<Unit> list(String tenantId, String filter) {
        // Clean, reusable: single code path
        List<Unit> tenantScopedUnits = unitRepository.findByTenantId(tenantId);
        return dynamicFilterEngine.apply(tenantScopedUnits, filter, Unit.class, FILTERABLE_FIELDS);
    }

    public Unit get(String tenantId, String unitId) {
        // ✅ Tenant validation added
        return unitRepository.findById(unitId)
                .filter(unit -> tenantId.equals(unit.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
    }
    // ... other methods
}
```

**Benefits**:
- Single, reusable pattern for all filter combinations
- Explicit field allowlisting (security)
- Tenant validation on all `get()` calls
- ~6 lines of clean code
- New filters = no code changes (only client query changes)

---

## 3. New Exception Handling

### Before
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // ... 
    }
    
    // ⚠️ No filter-specific error handlers
}
```

**Problems**:
- Filter syntax errors caught generically
- Clients can't distinguish filter errors from validation errors
- No specific error codes for filter failures

### After
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // ... existing handlers ...
    
    // ✅ NEW: 400 - Invalid filter syntax (e.g. malformed expression)
    @ExceptionHandler(InvalidFilterSyntaxException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFilterSyntax(InvalidFilterSyntaxException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("INVALID_FILTER_SYNTAX", ex.getMessage()));
    }

    // ✅ NEW: 400 - Unknown field in filter
    @ExceptionHandler(UnknownFilterFieldException.class)
    public ResponseEntity<ErrorResponse> handleUnknownFilterField(UnknownFilterFieldException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("UNKNOWN_FILTER_FIELD", ex.getMessage()));
    }

    // ✅ NEW: 400 - Unsupported operator or invalid type conversion in filter
    @ExceptionHandler(UnsupportedFilterOperatorException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFilterOperator(UnsupportedFilterOperatorException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("UNSUPPORTED_FILTER_OPERATOR", ex.getMessage()));
    }
}
```

**Benefits**:
- Standardized error responses for filter failures
- Clients can programmatically identify error type
- Clear, actionable error messages
- Centralized error handling

---

## 4. API Documentation

### Before
```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
            .info(new Info()
                    .title("Property Billing System API")
                    .version("1.0")
                    .description("Multi-tenant housing society billing REST API"))
            .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
            .components(new Components().addSecuritySchemes("BearerAuth", ...));
}
```

**Problems**:
- No filter documentation
- Users don't know how to construct queries
- Swagger shows 4–5 separate input boxes

### After
```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
            .info(new Info()
                    .title("Property Billing System API")
                    .version("1.0")
                    .description("""
                            Multi-tenant housing society billing REST API.

                            GET collection endpoints accept a single optional `filter` query parameter.
                            Supported operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `&&`, `||`.

                            Examples:
                            - `unitNumber=="A-102"`
                            - `active==true && profileCode=="1BHK"`
                            - `method=="UPI" && amount>=500`
                            """))
            .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
            .components(new Components().addSecuritySchemes("BearerAuth", ...));
}
```

**Benefits**:
- Clear syntax specification in OpenAPI docs
- 3 real-world examples for quick learning
- Self-documenting API
- Swagger UI shows filter examples

---

## 5. Request/Response Examples

### Before

```bash
# Scenario: Find active 1BHK units
# Problem: Cannot filter by active AND profileCode together!

curl "http://api/tenants/t1/units?active=true"
curl "http://api/tenants/t1/units?profileCode=1BHK"
curl "http://api/tenants/t1/units?ownerId=owner::123"

# Response: 200 OK
[
  { "id": "unit::1", "active": true, "profileCode": "1BHK", "ownerId": "owner::123" },
  { "id": "unit::2", "active": true, "profileCode": "2BHK", "ownerId": "owner::123" },
  { "id": "unit::3", "active": false, "profileCode": "1BHK", "ownerId": "owner::999" }
]
# ⚠️ Returns inactive units from call 2
# ⚠️ Returns 2BHK units from call 1
# ⚠️ No way to combine both filters in single request
```

### After

```bash
# Scenario: Find active 1BHK units
# Solution: Single request with combined filter

curl "http://api/tenants/t1/units?filter=active==true%20%26%26%20profileCode==%221BHK%22"
# URL decoded: ?filter=active==true && profileCode=="1BHK"

# Response: 200 OK
[
  { "id": "unit::1", "active": true, "profileCode": "1BHK", "ownerId": "owner::123" }
]
# ✅ Exact match: only active 1BHK units

# Error Handling Examples:

# Invalid syntax (single "=" instead of "=="):
curl "http://api/tenants/t1/owners?filter=status=%22ACTIVE%22"
# Response: 400 BAD REQUEST
{
  "code": "INVALID_FILTER_SYNTAX",
  "message": "Expected comparison operator '==', found '=' at position 7",
  "timestamp": "2025-03-25T10:30:00Z"
}

# Unknown field:
curl "http://api/tenants/t1/owners?filter=tenantId==%22x%22"
# Response: 400 BAD REQUEST
{
  "code": "UNKNOWN_FILTER_FIELD",
  "message": "Unknown filter field: 'tenantId'",
  "timestamp": "2025-03-25T10:31:00Z"
}

# Unsupported operator (> on boolean field):
curl "http://api/tenants/t1/units?filter=active%3E1"
# Response: 400 BAD REQUEST
{
  "code": "UNSUPPORTED_FILTER_OPERATOR",
  "message": "Operator '>' is not supported for field 'active'",
  "timestamp": "2025-03-25T10:32:00Z"
}
```

---

## Summary Table

| Aspect | Before | After | Improvement |
|--------|--------|-------|-------------|
| **Query Parameters** | 4–5 per endpoint | 1 unified | ✅ Simpler |
| **Filter Combinations** | Fixed (if-else logic) | Unlimited | ✅ Flexible |
| **Logical Operators** | None | AND, OR, () | ✅ Powerful |
| **Service Code** | 20+ lines/method | 6 lines/method | ✅ 70% reduction |
| **Tenant Validation** | Partial | Complete | ✅ Secure |
| **Error Codes** | Generic | Specific (3 new) | ✅ Clear |
| **API Documentation** | Missing | Comprehensive | ✅ Self-documenting |
| **Maintenance** | High (add filters = code change) | Low (add fields = allowlist update) | ✅ Scalable |


