# Ready Room

**AI-powered disaster preparedness scanner for your home.**

Scan a room with your phone. Ready Room detects furniture and fixtures, measures them in 3D using ARCore / ARKit, scores each hazard, highlights the safest cover zone, and gives you a fix-it checklist. You can then ask the built-in assistant questions about your room.

> ⚠️ **Ready Room is a preparedness aid, not an emergency tool.** It is designed to be used *before* a disaster, in calm conditions. It does not guarantee safety. Always follow official guidance from your local emergency services. **In an emergency in Australia, call 000.**

---

## Table of Contents

- [Features](#features)
- [How It Works](#how-it-works)
- [Tech Stack](#tech-stack)
- [AI Pipeline](#ai-pipeline)
- [Detection Classes](#detection-classes)
- [Datasets](#datasets)
- [Risk Scoring](#risk-scoring)
- [Safety Rules](#safety-rules)
- [Chat Assistant](#chat-assistant)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Model Training](#model-training)
- [Privacy](#privacy)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Features

### MVP (Earthquake mode)

- 📷 **AR room scan.** Guided scan using ARCore (Android) / ARKit (iOS) to capture depth and floor planes.
- 🔍 **Hazard detection.** On-device YOLO model detects furniture and fixtures in real time.
- 📏 **3D measurement.** Object height, distance to bed or sofa, fall radius, and proximity to glass.
- 🚦 **Risk tiers.** Each object is rated 🟢 Low / 🟡 Medium / 🔴 High, with a plain-language reason. No fake percentages.
- 🛡️ **Safe cover zone.** Highlights the best place to Drop, Cover and Hold On.
- ✅ **Fix checklist.** Ranked actions such as "Anchor this bookshelf to the wall" or "Move the mirror away from the bed".
- 🔁 **Rescan and track progress.** The room score improves as hazards are fixed.
- 💬 **Chat assistant.** Ask questions about your scanned room ("Can I hide under this table?").

### Planned

- 🌊 Flood mode: valuables height, power points, evacuation route.
- 🌀 Cyclone mode: windows, loose outdoor items.
- 🔥 Fire mode: exits, extinguisher, smoke alarm checks.
- 🏠 Multi-room home report (PDF export).
- 👨‍👩‍👧 Household emergency plan: meeting point, go-bag, contacts.
- 🏢 Renter mode: no-drill fixes.

---

## How It Works

```
┌────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  AR Scan   │──▶│  YOLO detect │──▶│  Geometry    │──▶│  Jev (fuzzy  │
│ ARCore /   │   │  (on-device) │   │  (depth +    │   │  judgments)  │
│ ARKit      │   │              │   │  your math)  │   │              │
└────────────┘   └──────────────┘   └──────────────┘   └──────┬───────┘
                                                              │
┌────────────┐   ┌──────────────┐   ┌──────────────┐          │
│  Chat LLM  │◀──│  Results UI  │◀──│  Risk engine │◀─────────┘
│ (explain)  │   │  tiers, zone,│   │  + hard      │
│            │   │  checklist   │   │  safety rules│
└────────────┘   └──────────────┘   └──────────────┘
```

1. **Scan.** The user slowly pans the phone around the room. ARCore / ARKit build depth maps and detect floor and wall planes.
2. **Detect.** The YOLO model finds objects (bookshelf, mirror, window, table, bed, and so on).
3. **Measure.** Each bounding box is projected into 3D using the AR depth map to get real-world height and distances.
4. **Judge.** Jev answers fuzzy yes/no questions per object ("top-heavy?", "items stacked on top?", "looks anchored?").
5. **Score.** A deterministic risk engine combines the measurements and judgments into a tier.
6. **Override.** Hard-coded safety rules always win over any model output.
7. **Explain.** Results are shown as an annotated photo, a cover zone and a checklist. The chat assistant answers follow-up questions.

---

## Tech Stack

| Layer | Technology |
|---|---|
| App | **Flutter** (Dart): one codebase for Android and iOS |
| AR / depth (Android) | **ARCore Depth API** via a native Kotlin module (platform channel) |
| AR / depth (iOS) | **ARKit** (plane detection, LiDAR `sceneDepth` on supported devices) via a native Swift module |
| Object detection | **Ultralytics YOLO** (nano), exported to **TFLite** (Android) and **Core ML** (iOS) |
| Decision layer | **Jev** (TypeSafe AI) for probabilistic yes/no judgments, behind an adapter |
| Chat assistant | Vision LLM API (Claude / GPT / Gemini), behind an adapter |
| Backend | **Supabase** (auth, scan history, saved rooms) |
| Annotation | Roboflow (private, data-licensing toggle off) or self-hosted CVAT |
| Training | Python, Ultralytics, FiftyOne |

---

## AI Pipeline

### 1. Object detection

- Model: YOLO nano, fine-tuned on merged public datasets plus custom home photos.
- Runs fully on-device. Photos never leave the phone for detection.
- Output: `[class, confidence, bbox]` per object.

### 2. Geometry (ARCore / ARKit + custom math)

| Measurement | Method |
|---|---|
| Object height | Top and bottom of the bbox projected to 3D via the depth map |
| Floor vs wall-mounted | AR plane detection |
| Fall radius | Approximately equal to object height |
| Proximity to bed or sofa | 3D distance between object footprints |
| Glass buffer | Distance from window, mirror or glass door (default 1.5 m) |
| Cover zone | Table area minus 🔴 fall-radius circles minus glass buffer |

### 3. Jev judgments

Jev receives the **detection and geometry JSON** (not the photo) and answers bounded questions:

| Question | Type |
|---|---|
| Is this object top-heavy? | yes/no (probability) |
| Are items stacked on top? | yes/no (probability) |
| Does it look anchored to the wall? | yes/no (probability) |
| Is this table sturdy enough for cover? | yes/no (probability) |

- Probabilities in the **uncertain band (0.4–0.6)** trigger a question to the user ("Is this shelf bolted to the wall? Y/N").
- Jev outputs are **inputs to the risk formula**. They are never averaged with it.
- If the Jev API is unavailable, the app falls back to asking the user directly.

### 4. Risk engine

Deterministic and explainable. See [Risk Scoring](#risk-scoring).

### 5. Chat LLM

Explains results in plain language. See [Chat Assistant](#chat-assistant).

---

## Detection Classes

### MVP (16 classes)

| # | Class | Group |
|---|---|---|
| 0 | `bookshelf` | Topple / fall |
| 1 | `wardrobe` | Topple / fall |
| 2 | `dresser` | Topple / fall |
| 3 | `refrigerator` | Topple / fall |
| 4 | `tv` | Topple / fall |
| 5 | `ceiling_fan` | Topple / fall |
| 6 | `ceiling_light` | Topple / fall |
| 7 | `wall_art` | Topple / fall |
| 8 | `wall_shelf` | Topple / fall |
| 9 | `window` | Glass / shatter |
| 10 | `mirror` | Glass / shatter |
| 11 | `glass_door` | Glass / shatter |
| 12 | `table` | Cover candidate |
| 13 | `bed` | Context zone |
| 14 | `sofa` | Context zone |
| 15 | `door` | Context zone (exit) |

### Planned

`stove`, `heater`, `power_point`, `extinguisher`, `smoke_alarm`

### Optional box attributes

`glass_top`, `items_on_top`, `wall_mounted`

### Annotation guidelines

- Box the **whole object**, including items sitting on top.
- Label an object if **at least 30% of it is visible**.
- **Mirror vs window:** a mirror reflects the room, a window shows the outside.
- Skip tiny or distant objects.
- One annotator or one written guide for consistency.

---

## Datasets

| Dataset | Use |
|---|---|
| [HomeObjects-3K](https://docs.ultralytics.com/datasets/detect/homeobjects-3k) | Base set: bed, sofa, chair, table, lamp, tv, wardrobe, window, door, photo frame |
| [Open Images V7](https://docs.ultralytics.com/datasets/detect/open-images-v7) | Gap fill: Mirror, Shelf, Bookcase, and others (subset via FiftyOne) |
| [COCO](https://cocodataset.org) | Extra samples: refrigerator, tv, bed, couch, dining table |
| Roboflow Universe indoor sets | Small top-ups (check license, typically CC BY 4.0) |
| **Custom photos** | Australian homes, phone angles, ceiling fans, hanging items. Kept as the **test set** |

Target: around 200–400 instances per class, 2–3k images total. Split 70 / 20 / 10.

---

## Risk Scoring

```
risk = class_weight
     × height_factor
     × proximity_factor        # near bed / sofa / desk
     × (1 - p_anchored)        # from Jev or user answer
     × modifiers               # items_on_top, glass, top_heavy
```

| Tier | Meaning |
|---|---|
| 🟢 Low | No action needed |
| 🟡 Medium | Recommended fix |
| 🔴 High | Fix as soon as possible |

Every tier comes with a reason, for example:

> 🔴 **Bookshelf.** 1.9 m tall, 0.8 m from the bed, probably not anchored. **Fix:** anchor it to a wall stud with anti-tip straps.

The app **never** displays a "% safe" number.

---

## Safety Rules

Hard-coded rules based on official guidance. **They override all model output, including the chat assistant.**

| Mode | Rule |
|---|---|
| Earthquake | Cover zone must be outside every 🔴 fall radius and outside the glass buffer. Advice: **Drop, Cover, Hold On.** |
| Flood | **Never** recommend sheltering low or under furniture. Advice: move to higher ground, evacuate if told to, never drive through floodwater. |
| Cyclone | Shelter in the strongest, smallest internal room, away from windows. |
| Fire | **Never** recommend hiding. Advice: get out, stay out, call 000. |

Rules live in `ai/rules/` and each rule links to its official source.

---

## Chat Assistant

### Context sent per message

1. System prompt: role, current hazard mode, hard safety rules.
2. **Latest photo only.** Older scans are summarized as JSON to save tokens.
3. Detection, geometry and risk JSON from the pipeline.
4. Official guidance snippets (retrieval from `ai/guidance/`).
5. The last ~10 chat turns.

### Example

> **User** (flood mode, photo of a table): Can I hide under this?
>
> **Assistant:** No. In a flood, don't shelter low or under furniture, because water can rise quickly. Move to higher ground, or evacuate if authorities tell you to. If you're trapped, call 000.

### Guardrails

- **"Happening now" detection.** If the user indicates an active emergency ("it's shaking now", "water is coming in"), the app skips the LLM and immediately shows a static emergency card (Drop, Cover, Hold On / get to high ground / call 000).
- **Output check.** Every LLM response is checked against the hard safety rules before display. Responses that contradict a rule are blocked and replaced.
- **Scope.** The assistant is for preparedness questions, not live rescue.

---

## Project Structure

```
ready-room/
├── app/                        # Flutter app
│   ├── lib/
│   │   ├── features/
│   │   │   ├── scan/           # AR scan flow
│   │   │   ├── results/        # Risk tiers, cover zone, checklist
│   │   │   ├── chat/           # Chat assistant UI
│   │   │   └── history/        # Saved rooms, rescans
│   │   ├── services/
│   │   │   ├── ar/             # Platform channel to native AR
│   │   │   ├── detector/       # TFLite / Core ML inference
│   │   │   ├── geometry/       # 3D measurement math
│   │   │   ├── risk/           # Risk engine + hard rules
│   │   │   ├── jev/            # Jev adapter
│   │   │   └── llm/            # Chat LLM adapter
│   │   └── main.dart
│   ├── android/                # Kotlin ARCore module
│   └── ios/                    # Swift ARKit module
├── ai/
│   ├── data/                   # Dataset download + merge scripts
│   ├── training/               # YOLO training configs and notebooks
│   ├── export/                 # TFLite / Core ML export scripts
│   ├── rules/                  # Hard safety rules (JSON + sources)
│   ├── guidance/               # Official guidance snippets for chat retrieval
│   └── eval/                   # Test-set evaluation, risk engine tests
├── backend/                    # Supabase migrations and functions
├── docs/                       # Architecture notes, annotation guide
└── README.md
```

---

## Getting Started

### Prerequisites

- Flutter SDK (stable channel)
- Android Studio with an **ARCore-supported device** (Depth API enabled)
- Xcode with an **ARKit-capable iPhone** (LiDAR models recommended for best depth)
- Python 3.10+ (for training)
- Supabase project (optional for local development)

### Run the app

```bash
git clone https://github.com/<your-username>/ready-room.git
cd ready-room/app
flutter pub get
cp .env.example .env            # add API keys
flutter run
```

### Environment variables

```env
SUPABASE_URL=
SUPABASE_ANON_KEY=
JEV_API_KEY=
LLM_API_KEY=
LLM_PROVIDER=claude             # claude | openai | gemini
```

> Never commit `.env`. It is listed in `.gitignore`.

> AR features do not work on emulators. Use a physical device.

---

## Model Training

```bash
cd ai
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# 1. Download and merge datasets
python data/download_homeobjects.py
python data/download_openimages.py --classes Mirror Shelf Bookcase "Ceiling fan"
python data/merge.py --config data/class_map.yaml

# 2. Train
yolo detect train data=data/ready_room.yaml model=yolo11n.pt epochs=100 imgsz=640

# 3. Evaluate on custom home photos
python eval/evaluate.py --weights runs/detect/train/weights/best.pt

# 4. Export
python export/export.py --weights best.pt --formats tflite coreml
```

Copy the exported models to `app/assets/models/`.

### Targets

- mAP50 of at least 0.6 on the main hazard classes.
- Weak classes (likely `mirror`, `ceiling_fan`, `wall_shelf`) get targeted extra data collection.

---

## Privacy

- **Detection runs on-device.** Room photos are not uploaded for detection.
- **Jev receives JSON only** (classes, sizes, distances), never images.
- **Chat sends the latest photo** to the LLM provider only when the user opens the chat. Users are told this clearly.
- **Nothing is stored by default.** Saving scan history is opt-in.
- **Training data:** consent is required for any non-personal home photos. EXIF data is stripped, and faces, documents and addresses are blurred.

---

## Roadmap

| Phase | Weeks | Goal |
|---|---|---|
| 0 | 1–2 | Flutter app, camera, native AR modules, stock YOLO demo on device |
| 1 | 3–5 | Dataset merge, labeling, fine-tune, export TFLite / Core ML |
| 2 | 6–7 | Geometry math, risk engine, hard rules, cover zone |
| 3 | 8 | Jev adapter, uncertain-band user questions |
| 4 | 9 | Fix checklist, rescan, Supabase history |
| 5 | 10 | Chat assistant and guardrails |
| 6 | 11–12 | Real-room testing, polish, iOS build |
| Later | | Flood, cyclone, fire modes. Home PDF report. Household plan |

---

## Contributing

1. Fork the repo and create a feature branch.
2. Follow the annotation guidelines for any dataset contributions.
3. Any change to `ai/rules/` must include a link to an official source.
4. Open a pull request with a clear description.

---

## License

TBD. Note: dataset and model licenses vary (for example CC BY 4.0 for many Roboflow Universe sets, and Ultralytics YOLO is AGPL-3.0). Check them before any commercial use.

---

## Disclaimer

Ready Room provides general preparedness suggestions only. It is not a substitute for professional structural assessment or official emergency guidance. Detection and risk scores may be wrong or incomplete. **In an emergency, follow instructions from local authorities and call 000 (Australia).**