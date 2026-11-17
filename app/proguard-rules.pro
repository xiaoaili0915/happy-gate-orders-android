# Keep kotlinx.serialization generated serializers for API models
-keepclassmembers class nl.happygate.orders.data.** { *** Companion; }
-keepclasseswithmembers class nl.happygate.orders.data.** { kotlinx.serialization.KSerializer serializer(...); }
