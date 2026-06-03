# Property Billing UI — Overview

**Document purpose:** Explain the billing-ui application for demos, onboarding, and handoffs.

---

## What it is

**Property Billing UI** is a React single-page application for managing housing-society billing. It connects to a Spring Boot API (default: `http://localhost:8081`). In development, the UI calls `/api` and Vite proxies those requests to the backend.

| Layer | Technology |
|--------|------------|
| Framework | React 19 + TypeScript |
| Routing | React Router v7 |
| Styling | Plain CSS (`index.css`) — dark theme, no UI component library |
| State | React Context + local page state |
| Authentication | JWT stored in browser `localStorage` (`accessToken`) |

The application is **admin-focused**: only **SUPERADMIN** and **TENANT_ADMIN** roles receive full dashboards. Other backend roles (`CSR`, `OWNER`) exist in the API but have no dedicated screens in this UI yet.

---

## High-level user flow

1. **Super Admin** signs up → creates **tenants** (housing societies) → copies each tenant ID (e.g. `tenant::...`).
2. **Tenant Admin** signs up with that tenant ID → configures **profiles** → **units** → **owners** → links owners to units.
3. **Invoices** are generated (from the UI or by the backend scheduler); **payments** are recorded and viewed in lists.

### Role-based landing pages

After login, `/dashboard` redirects users automatically:

| Role | Landing page |
|------|----------------|
| SUPERADMIN | `/superadmin/tenants` |
| TENANT_ADMIN | `/tenant/units` |

Unauthenticated users are sent to `/login`. Logged-in users cannot open login or signup again.

---

## Application structure

### Public routes

- **Login** (`/login`) — Email and password; stores JWT and redirects to dashboard.
- **Signup** (`/signup`) — Choose **Super Admin** or **Tenant Admin**. Tenant admins must enter the tenant ID created by a super admin.

### Super Admin area

- **Path:** `/superadmin/tenants`
- **Title in UI:** Platform administration
- **Screen:** Tenants — list, create, and edit housing societies (currency, billing date, late-fee rules, address). Tenant IDs are displayed for sharing with society admins.

### Tenant Admin area

- **Path prefix:** `/tenant/...`
- **Title in UI:** Society administration
- **Sidebar navigation:** Profiles → Units → Owners → Invoices → Payments
- **Default home:** Units (most day-to-day work happens here)

| Screen | Purpose |
|--------|---------|
| **Profiles** | Billing templates (e.g. 1BHK, 2BHK) with monthly charge amounts |
| **Units** | Flats/units: number, profile, owner link, balance, due amount; record payments |
| **Owners** | Resident records; link/unlink units |
| **Invoices** | Monthly bills; manual invoice generation for a period |
| **Payments** | Payment history (new payments are recorded from the Units screen) |

---

## Layout and navigation

Both admin experiences share **DashboardLayout**:

- **Left sidebar:** Brand (“Billing”), navigation links, signed-in user name and roles, Sign out
- **Main area:** Page title and content for the active route

---

## Typical business setup order

Explain the domain as a **dependency chain**:

1. **Tenant** (super admin) — Society exists in the system
2. **Profiles** — How much each unit type pays per month
3. **Units** — Physical flats tied to a profile
4. **Owners** — People linked to units
5. **Invoices** — Monthly bills from profiles, balances, and tenant rules
6. **Payments** — Money received; affects balances and future invoices

The sidebar lists Profiles before Units, but the app **lands on Units** because that is where operators manage balances, owners, and payments.

---

## How the UI connects to the API

```
Browser  →  /api/...  →  (Vite dev proxy)  →  localhost:8081/...
```

- Shared HTTP client attaches `Authorization: Bearer <token>` on each request
- API modules: auth, tenants, profiles, units, owners, invoices, payments
- Tenant-scoped URLs encode Couchbase-style IDs (e.g. `unit::uuid`)

On startup, if a token exists, the app loads the current user via **GET /me**. All tenant-admin data is scoped to the user’s `tenantId`.

---

## Common UI patterns

| Pattern | Purpose |
|---------|---------|
| Data tables | Browse entities |
| Toolbar | Row count, filters, primary actions |
| Modals | Create and edit without leaving the page |
| Badges | Active/inactive, invoice status |
| Spinners and alerts | Loading and error feedback |
| Filter boxes | Server-side query strings (Couchbase N1QL style) |

**Visual design:** Dark blue/gray theme, blue primary actions, green success indicators — implemented with custom CSS (no Material/Tailwind).

---

## Units page — operational hub

The Units screen is the busiest tenant-admin page:

- List units with profile, owner, active status, balance, and due amount
- Add, edit, and delete units
- Link or unlink owners
- View details and **record payments** (Cash, UPI, bank transfer, cheque)
- Navigate to **Invoices** filtered by unit

---

## One-sentence summary

> Property Billing UI is a role-based React admin portal: platform admins onboard housing societies as tenants; society admins configure billing profiles and units, assign owners, generate monthly invoices, and record payments — all against a JWT-secured REST API.

---

## Running the application

**Prerequisites:** Node.js 18+, backend API on port 8081

```bash
cd billing-ui
npm install
npm run dev
```

Open **http://localhost:5173** in a browser.

For production builds, set `VITE_API_URL` if the API is not served under `/api`.

---

*Generated for the billing project — billing-ui module.*
