# Tech Stack & Guidelines

- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite (fast dev server & HMR)
- **Routing**: React Router v6 (routes defined in `src/App.tsx`)
- **Styling**: Tailwind CSS (utility‑first classes for layout, spacing, colors, etc.)
- **UI Library**: shadcn/ui components (pre‑built, accessible, Tailwind‑based)
- **Icons**: lucide-react (consistent icon set)
- **State Management**: React Context or Zustand (if needed) – avoid over‑engineering
- **Data Fetching**: React Query / TanStack Query (for server state) or plain fetch/axios
- **Form Handling**: React Hook Form (with Zod validation if needed)
- **Linting/Formatting**: ESLint + Prettier (configured via existing setup)

## Usage Rules

1. **File Organization**
   - All source code lives in the `src/` directory.
   - Pages go in `src/pages/` (each page is a component exported as default).
   - Reusable UI components go in `src/components/`.
   - Custom hooks, utilities, and types go in `src/hooks/`, `src/utils/`, and `src/types/` respectively (create as needed).

2. **Routing**
   - Keep all route definitions in `src/App.tsx`.
   - Use `<Routes>` and `<Route>` from `react-router-dom`.
   - Lazy‑load pages with `React.lazy` and `<Suspense>` for code‑splitting when appropriate.

3. **Styling & UI**
   - Style components exclusively with Tailwind CSS classes.
   - Do **not** write custom CSS or SCSS files unless absolutely necessary (e.g., global styles in `src/index.css`).
   - Prefer shadcn/ui components for common UI elements (buttons, inputs, modals, tables, etc.).
   - If a shadcn/ui component needs modification, create a wrapper component in `src/components/` rather than editing the library file.
   - Use `lucide-react` for icons; import only the icons you need.

4. **State & Data**
   - Prefer React Query for server‑state caching, background updates, and deduplication.
   - Use React Context or a lightweight store (e.g., Zustand) only for truly global UI state (theme, auth, etc.).
   - Avoid prop‑drilling by lifting state only as far as needed; consider component composition.

5. **Forms**
   - Use React Hook Form for form state management.
   - Pair with Zod for schema validation when validation logic is non‑trivial.
   - Keep form components small and reusable.

6. **Code Quality**
   - Write functional components with TypeScript interfaces/props.
   - Enable strict TypeScript mode (`noImplicitAny`, `strictNullChecks`, etc.).
   - Follow ESLint and Prettier rules; run linting before committing.
   - Keep files small and focused; prefer single‑responsibility components.

7. **Imports & Dependencies**
   - Import shadcn/ui components from their local copies (e.g., `import { Button } from "@/components/ui/button"`).
   - Import icons from `lucide-react` (e.g., `import { LucideIcon } from "lucide-react"`).
   - Avoid importing from `@/` aliases unless configured; otherwise use relative paths.

8. **Updating the Main Page**
   - The default landing page is `src/pages/Index.tsx`.
   - Whenever you create a new component or feature that should be visible on the home page, **update** `src/pages/Index.tsx` to include it.
   - Do not leave new components unused; they must be referenced somewhere reachable from the router.

9. **Testing (if added later)**
   - Use Vitest + React Testing Library for unit tests.
   - Place test files alongside the source files with `.test.tsx` suffix.

10. **General**
    - Keep the app responsive and mobile‑first.
    - Follow accessibility guidelines (use semantic HTML, ARIA labels where needed, ensure focus management).
    - Do not introduce unnecessary libraries; prefer the existing stack.