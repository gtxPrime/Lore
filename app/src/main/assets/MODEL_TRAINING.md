# Nurtale Mood Classifier — Model Training Guide

This document describes how to generate the `mood_classifier.tflite` model file
that powers Layer 4 of Nurtale's on-device AI mood detection system.

Place the generated file at:
```
app/src/main/assets/mood_classifier.tflite
```

---

## Architecture

- **Base model**: MobileBERT (INT8 quantized via MediaPipe Model Maker)
- **Task**: Text classification → 6 mood categories
- **Input**: Raw text string (up to 128 tokens)
- **Output**: 6 probability scores (one per mood)
- **Target size**: ~20MB after INT8 quantization
- **Inference latency**: ~15–50ms on modern Android (CPU), ~5–15ms (GPU delegate)

## Output Labels (must match exactly)

The model must output these 6 category names in any order:

| Label | Nurtale Mood | Training Examples |
|---|---|---|
| `bright` | BRIGHT | joy, love, gratitude, excitement, celebration |
| `calm` | CALM | peace, serenity, relaxation, mindfulness, contentment |
| `heavy` | HEAVY | sadness, grief, loneliness, exhaustion, loss |
| `tangled` | TANGLED | anxiety, stress, overwhelm, confusion, panic |
| `dark` | DARK | anger, rage, hatred, frustration, despair |
| `blank` | BLANK | emptiness, numbness, uncertainty, neutral |

---

## Step 1 — Get the Training Dataset

Use the **dair-ai/emotion** dataset (6 classes) mapped to Nurtale's taxonomy:

```python
# In Google Colab:
pip install datasets transformers mediapipe-model-maker

from datasets import load_dataset
dataset = load_dataset("dair-ai/emotion")

# Emotion → Nurtale mood mapping
LABEL_MAP = {
    0: "heavy",    # sadness
    1: "bright",   # joy
    2: "bright",   # love  (mapped to bright)
    3: "tangled",  # anger → remap to dark below
    4: "dark",     # fear  → remap to dark
    5: "blank",    # surprise
}

# Remap anger specifically
import re
def remap(example):
    label = example["label"]
    text  = example["text"].lower()
    # Anger pattern → dark
    if label == 3:
        mapped = "dark"
    else:
        mapped = LABEL_MAP.get(label, "blank")
    return {"text": example["text"], "label_str": mapped}

dataset = dataset.map(remap)
```

---

## Step 2 — Fine-tune with MediaPipe Model Maker

```python
import mediapipe as mp
from mediapipe.tasks.python.text import text_classifier
from mediapipe.model_maker import text_classifier as mm_text_classifier

# Prepare CSV
import pandas as pd
train_df = pd.DataFrame({
    "text":  dataset["train"]["text"],
    "label": dataset["train"]["label_str"]
})
test_df = pd.DataFrame({
    "text":  dataset["test"]["text"],
    "label": dataset["test"]["label_str"]
})
train_df.to_csv("train.csv", index=False)
test_df.to_csv("test.csv",  index=False)

# Load data
train_data = mm_text_classifier.Dataset.from_csv("train.csv", text_column="text", label_column="label")
test_data  = mm_text_classifier.Dataset.from_csv("test.csv",  text_column="text", label_column="label")

# Configure MobileBERT fine-tuning
spec = mm_text_classifier.SupportedModels.MOBILEBERT_CLASSIFIER
hparams = mm_text_classifier.HParams(
    epochs=5,
    batch_size=32,
    learning_rate=3e-5
)
options = mm_text_classifier.TextClassifierOptions(
    supported_model=spec,
    hparams=hparams
)

# Train
model = mm_text_classifier.TextClassifier.create(train_data, options=options)
loss, accuracy = model.evaluate(test_data)
print(f"Accuracy: {accuracy:.2%}")

# Export as INT8 quantized TFLite
model.export_model(model_name="mood_classifier.tflite")
print("Model saved → mood_classifier.tflite")
```

---

## Step 3 — Copy to Android Project

```bash
# From Colab, download mood_classifier.tflite then:
cp mood_classifier.tflite <project>/app/src/main/assets/
```

---

## Step 4 — Verify in Android

```kotlin
// In Application.onCreate():
EmotionModelManager.initialize(context)

// Check it loaded:
Log.d("AI", "Model ready: ${EmotionModelManager.isModelReady()}")
```

---

## Expected Performance

| Metric | Value |
|---|---|
| Accuracy (6-class) | ~85–91% |
| Inference latency (CPU) | ~15–50ms |
| Inference latency (GPU) | ~5–15ms |
| Model size (INT8) | ~19–22MB |
| Privacy | 100% on-device, no network calls |

---

## Updating the Model

To ship a model update without a Play Store release, upload the new `.tflite` file
to Firebase ML Console and switch to the Firebase ML Model Downloader approach.
See the commented-out dependency in `build.gradle` for that path.

Alternatively, ship a new APK version with the updated model in `assets/`.
