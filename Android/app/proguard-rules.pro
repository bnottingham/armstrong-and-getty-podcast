# Proguard rules for Armstrong & Getty Podcast

# Keep Room entities
-keep class com.nomnomsom.starwarsshop.data.model.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Keep Hilt
-keep class dagger.hilt.** { *; }
