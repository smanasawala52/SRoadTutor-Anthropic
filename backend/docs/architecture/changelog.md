# Architecture Decision Changelog

Plain-text record of every change to `01-foundation-design.md`. Append-only.

## 2026-04-24 — Initial foundation design committed

- D1–D21 captured from product Q&A round 1 (roles, multi-school, schema metadata, V7 gating).
- D13–D16 added from WhatsApp/phone-number requirements round 2.
- §11 Q6 (WhatsApp verification mechanism) flagged as the single remaining blocker before PR2 schema work.
- PR sequence finalized as PR1 (Facebook removal) → PR2 (V8 schema) → PR3 (auth hardening) → PR4 (phone CRUD + WhatsApp) → PR5 (email + invitations) → PR6 (school + instructor controllers + RBAC matrix).
