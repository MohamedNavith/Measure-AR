# Keep ARCore native & Jni methods
-keep class com.google.ar.core.** { *; }
-keepclassmembers class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**

# Keep SceneView & Filament classes
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn io.github.sceneview.**

# Room entities
-keep class com.example.measurear.data.MeasurementEntity { *; }
