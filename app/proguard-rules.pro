# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class de.hubiplan.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class de.hubiplan.app.data.**$$serializer { *; }

# WorkManager erzeugt seine Room-Datenbank beim Start per Reflexion über den leeren Konstruktor.
# Die Regel aus Room behält nur die Klasse, nicht den Konstruktor (Absturz von 1.0.0 beim Start).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
