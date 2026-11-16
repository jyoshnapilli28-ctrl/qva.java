# QVA 2.1 frontend

The Spring Boot JAR built with the `qva-web` profile serves the React workspace at `/app/` and classic QVA at `/`. Use Node 22+ with npm, Java 17 and Maven. The build uses `npm ci` and writes frontend assets to Maven's build output. A clean build without the profile produces the classic JAR and needs only Java and Maven.

From the repository root:

```sh
mvn -Pqva-web -DskipTests clean package
java -DDB_PATH=./target/qva-frontend-dev.db -jar target/private-chat-server.jar \
  --server.address=127.0.0.1 --server.port=18080 --set-password=jyoshna
java -DDB_PATH=./target/qva-frontend-dev.db -jar target/private-chat-server.jar \
  --server.address=127.0.0.1 --server.port=18080
```

Use a new test-only password and the disposable database shown above. Open http://127.0.0.1:18080/app/. To develop from `frontend/`, run `npm ci` and `npm run dev`. Vite serves http://127.0.0.1:5173/app/ and proxies the backend on port 18080. `QVA_API_TARGET` can point the development proxy at another test backend.

## Structure

- `src/app/routes.tsx` declares routes and navigation. `src/layouts/` owns the authenticated shell and focus management. `src/pages/` composes feature views.
- `src/services/` owns HTTP and wire contracts. `src/hooks/` manages session state, refresh and expiry. `src/types.ts` defines the data shapes.
- `src/components/ui/` provides Button, Panel, Notice and Avatar. Use CSS theme tokens for colors, consistent status text, labelled icon-only controls, and native elements for keyboard behavior. Keep the pages focused on composition.
- `src/components/network/` holds the device-independent topology presentation and classic roster display. `topologyFromHealth` supplies the observed relay. A future device API can feed the host and client node model. Account presence does not identify devices or verify VPN connectivity.

Future Activity Monitor, Devices, Security Center, Analytics and Messenger features each get a page, a service where needed, and feature components. Register a route in the frontend and the Spring shell route allowlist when the page is functional. Features requiring authorization must also enforce it at the relevant API. Navigation includes the currently usable pages.

## Tests

From `frontend/`, run `npm run test:components`. For browser tests, install Chromium with `npx playwright install chromium --only-shell` or supply its executable path in `QVA_CHROMIUM_PATH`. Set `QVA_TEST_PASSWORD` to the disposable account's password and run `npm test`. To test the packaged JAR directly, set `QVA_TEST_BASE_URL=http://127.0.0.1:18080` as well. Optional `QVA_TEST_USERNAME` selects a different test account. The browser suite checks the real server session and injects temporary API errors to exercise recovery.

See [the authentication contract](../docs/qva2/authentication.md) for request and session behavior.

## Host and device workspace

See [QVA workspace operations](../docs/qva2/operations.md) for the current role model, local HOST provisioning, device approval, application presence, API contract, migration, and deployment acceptance. Host and member browser integration tests require `QVA_TEST_OPERATIONS=1` and an isolated fixture with a synthetic HOST grant.
