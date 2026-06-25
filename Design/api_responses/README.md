# AISafe Simulations — API Response Bodies

This directory contains the complete entity layer for driving a dynamic frontend from backend data.

## Files

| File | Purpose |
|------|---------|
| `schema_index.json` | API schema definitions, endpoint contracts, model types, color tokens |
| `sim1_prompt_injection.json` | Full response body for Simulation 1 — Prompt Injection Attack |
| `sim2_data_privacy.json` | Full response body for Simulation 2 — Data Privacy in AI Tools |
| `sim3_ai_verification.json` | Full response body for Simulation 3 — Verifying AI-Generated Content |

## Entity Structure (Common)

```
Simulation
├── id, title, accent_color, badge
├── intro (icon, tag_line, heading, description, meta[], cta_text)
├── scenes[] (1..N)
│   ├── context_bar (icon, label, narrative)
│   ├── mockup (type-discriminated: chat_window | email | app_window | document)
│   ├── question (number_label, text, correct_answer, options[])
│   ├── feedback (correct{}, wrong{A,B,C,D})
│   └── next_button_text
└── results (titles{}, subtitles{}, breakdown[], score_ring_circumference)
```

## Mockup Types (Discriminated Union)

The `mockup.type` field determines the rendering strategy:

- **`chat_window`** — AI chat interface with messages, typing animation, status indicator
- **`email`** — Email client with sender, subject, body, highlighted threats
- **`app_window`** — Generic app with toolbar + sections (drafts, warnings, documents)
- **`document`** — Document viewer with fact-check rows, hallucination markers, warnings

## Custom HTML Tags (for frontend renderer)

- `<hallucination>text</hallucination>` — highlighted as potential AI fabrication (amber)
- `<pii>text</pii>` — highlighted as sensitive personal data (red)
- `<highlight>text</highlight>` — red-bordered alert block within messages
- `<strong>`, `<em>`, `<code>`, `<br>` — standard inline formatting

## Frontend Rendering Flow

1. Fetch simulation by ID → render intro screen
2. User clicks CTA → advance to scene[0]
3. Each scene: render context_bar → mockup (by type) → question card
4. On answer: lock options, show feedback, reveal next button
5. After final scene → compute score → render results with ring animation
