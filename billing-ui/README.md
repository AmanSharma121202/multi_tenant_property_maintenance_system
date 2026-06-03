# Billing UI

React frontend for the Property Billing API.

## Prerequisites

- Node.js 18+
- Backend running on `http://localhost:8081` (see `billing/` module)
- Couchbase and optional Kafka for full backend features

## Run

```bash
cd billing-ui
npm install
npm run dev
```

Open http://localhost:5173

API calls are proxied from `/api` to the backend (see `vite.config.ts`).

## Roles

| Role | Dashboard |
|------|-----------|
| **SUPERADMIN** | List/create/edit tenants |
| **TENANT_ADMIN** | Units, profiles, owners, invoices, payments |

### Getting started

1. **Super admin** — Sign up with account type “Super Admin”, then create tenants.
2. **Tenant admin** — Copy a tenant ID (e.g. `tenant::...`) from the super admin screen, sign up as “Tenant Admin” with that ID.
3. As tenant admin, create profiles → units → owners, link owners to units, view invoices, register payments.

## Build

```bash
npm run build
```

Set `VITE_API_URL` for production (e.g. `https://api.example.com`) if not using the dev proxy.
