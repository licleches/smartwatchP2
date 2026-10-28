# MedTimeWatch

Proyecto Wear OS de MedTime.

La aplicacion usa Kotlin, Jetpack Compose Wear Material 3, ViewModel/StateFlow,
Ktor y Supabase Edge Functions.

Implementado:

- Vinculacion del reloj mediante codigo de seis digitos y autorizacion desde
  MedTime Mobile.
- Polling del estado de vinculacion con expiracion de cinco minutos y backoff.
- Credencial del dispositivo cifrada con Android Keystore y AES-GCM.
- Home con dosis obtenidas mediante `watch-doses-list`.
- Acciones `confirm`, `postpone` y `omit` mediante `watch-dose-update`.
- Posposicion limitada a 15, 30, 60 o 120 minutos.

El login anterior se conserva en el codigo solo como referencia. Las Edge
Functions deben estar desplegadas para probar la vinculacion; la aplicacion no
simula una vinculacion aprobada.

La configuracion local requiere `supabase.url` y `supabase.publishableKey` en
`local.properties`. Ese archivo esta ignorado por Git. No se debe usar una
clave `service_role` en el reloj.

Para compilar en Windows usando el JBR de Android Studio:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug --no-daemon
```

La aplicacion conserva el namespace y el applicationId existentes:
`com.example.medtime`.
