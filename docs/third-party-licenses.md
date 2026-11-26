# Сторонние компоненты и лицензии

Приложение распространяет следующие bundled-компоненты. Полный текст Apache License 2.0 —
в [`licenses/APACHE-2.0.txt`](licenses/APACHE-2.0.txt).

## Tesseract4Android 4.9.0

- Источник: https://github.com/adaptech-cz/Tesseract4Android
- Лицензия wrapper-кода: Apache License 2.0, Copyright 2019 Adaptech s.r.o., Robert Pösel.
- Включает Tesseract OCR 5.5.1 (Apache-2.0), Leptonica 1.85.0 (Apache-2.0), libjpeg v9f и libpng 1.6.48.
- Модель `rus.traineddata` 4.0.0: https://github.com/tesseract-ocr/tessdata, лицензия Apache-2.0.

## RapidOcrAndroidOnnx 1.3.0

- Источник: https://github.com/RapidAI/RapidOcrAndroidOnnx
- Лицензия кода: Apache License 2.0.
- ONNX-модели det/rec/cls: авторские права принадлежат PaddleOCR (https://github.com/PaddlePaddle/PaddleOCR).
- Нативные зависимости внутри AAR: ONNX Runtime 1.14.0, opencv-mobile 3.4.15.

Оба AAR и русская модель лежат в репозитории, чтобы сборка была воспроизводимой и не требовала
сети. Ни один из компонентов не запрашивает разрешение `INTERNET`: распознавание полностью локальное.
