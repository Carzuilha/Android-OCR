# Optical Character Recognition (OCR) for Android

This project shows the example of an Android app for Optical Character Recognition (OCR) on real-time.

## Introduction

Currently, this project is based on two other projects:

 - [Google Vision](https://developers.google.com/vision/);
 - [EzequielAdrianM's Camera2Vision](https://github.com/EzequielAdrianM/Camera2Vision).

These two projects were combined in this work. The decision to do this approach is based on the fact that the Google project utilizes the original [Camera](https://developer.android.com/reference/android/hardware/Camera.html) library (deprecated since API 21), while the Ezequiel's project utilizes the most recent [Camera2](https://developer.android.com/reference/android/hardware/camera2/package-summary), currently utilized as default in new projects.

## Utilized Material

 - [**Android Studio IDE**](https://developer.android.com/studio/);
 - A _smartphone_/_tablet_ on Android (API 21 or above).

## Usage of the Project Sources

you may use the sources of this project as follows: 

 - The [CameraControl_A.java](app/src/main/java/com/carzuilha/ocr/control/CameraControl_A.java) class utilizes the original `camera`;
 - The [CameraControl_B.java](app/src/main/java/com/carzuilha/ocr/controlCameraControl_B.java) class utilizes the recent `camera2`.

## Additional Info

The source code based on the `camera` (_CameraControl_A.java_) library presents a better performance than the code based on `camera2` (_CameraControl_B.java_) library. In the future, this code will be optimized, I promise. 😃 

## License

The available source codes here are under the Apache License, version 2.0 (see the attached `LICENSE` file for more details). Any questions can be submitted to my email: carloswdecarvalho@outlook.com.
