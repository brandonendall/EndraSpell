import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.endra.spell"; compileSdk = 35
 defaultConfig { applicationId = "com.endra.spell.test"; minSdk = 26; targetSdk = 35; versionCode = 34; versionName = "0.1.34"; testInstrumentationRunner = "com.endra.spell.CheckpointInstrumentation" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 val signingFile = file("../../signing-test/signing.properties")
 val signingKey = file("../../signing-test/endraspell-test.jks")
 if (signingFile.isFile && signingKey.isFile) {
  val signingValues = Properties().apply { signingFile.inputStream().use { load(it) } }
  signingConfigs { create("persistentTest") {
   storeFile = signingKey
   storePassword = signingValues.getProperty("storePassword")
   keyAlias = "endraspell-test"
   keyPassword = signingValues.getProperty("storePassword")
  } }
  buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("persistentTest") } }
 }
 buildFeatures { compose = true }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
 implementation("com.google.mlkit:text-recognition:16.0.1")
 implementation("androidx.exifinterface:exifinterface:1.3.7")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.mockito:mockito-core:5.14.2")
 testImplementation("org.json:json:20240303")
 implementation(platform("androidx.compose:compose-bom:2025.01.00"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
