# Calude para Android

Cliente Android nativo de chat con OpenRouter, búsqueda web Tavily y memoria local. Versión inicial estable: **1.0**.

## Funciones

- Conversaciones guardadas localmente, con búsqueda, creación y eliminación.
- Conexión con cualquier modelo compatible con OpenRouter; modelo configurable por ID.
- Clave de OpenRouter y clave de Tavily almacenadas en preferencias cifradas con Android Keystore.
- Búsqueda web opcional con Tavily y resultados incluidos como contexto de la respuesta.
- Adjuntar texto, archivos e imágenes; cámara y dictado con los servicios disponibles en Android.
- Proyectos con instrucciones propias y chats asociados.
- Memoria local editable, con extracción automática de preferencias expresadas explícitamente.
- Artefactos guardados localmente y controles de privacidad en el dispositivo.
- Interfaz adaptable con selector de modelo y animación de carga original.
- Tipografía serif Lora para las respuestas y algunos encabezados; se incluye su licencia SIL Open Font License.

La memoria se procesa localmente con reglas simples; no es el sistema propietario de memoria de Claude. PDF y otros formatos binarios que no sean imágenes se pueden seleccionar, pero su contenido no se extrae en esta versión.

## Configuración

1. Instala el APK de la compilación de GitHub Actions.
2. Abre **Configuración** y agrega tu clave de OpenRouter.
3. Configura el ID del modelo, por ejemplo `anthropic/claude-sonnet-4`.
4. Para búsquedas, agrega una clave de Tavily y activa **Búsqueda web**.

Las claves se guardan en el almacenamiento cifrado de Android. Las conversaciones y memorias quedan en almacenamiento privado local de la app.

## Compilar

Requiere JDK 17+, Android SDK 35 y Gradle 8.13.

```sh
gradle :app:assembleRelease
```

GitHub Actions compila el APK Release en cada actualización de `main` y publica el archivo como artefacto descargable. Al crear una etiqueta `v*`, también crea un lanzamiento con el APK.
