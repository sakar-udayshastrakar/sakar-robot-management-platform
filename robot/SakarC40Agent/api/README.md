# api module

Placeholder for the future integration surface between `SakarC40Agent` and
the Sakar Backend (see the second architecture diagram in the root
`README.md`).

This module intentionally contains **no networking code**: no REST client,
no MQTT client, no WebSocket server. Per the current project scope, the
cloud/backend/web portion is explicitly out of scope until a physical C40
is available and the local diagnostic build is validated.

`SakarBackendApi` below documents the intended shape of that future
boundary so the rest of the app can be designed against a stable interface
later, without committing to a transport today.
