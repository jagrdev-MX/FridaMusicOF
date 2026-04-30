# FridaMusic Site (Local)

## Opción 1 (Linux/macOS)

Desde la raíz del repo:

```bash
./site/serve-local.sh
```

Abre:

- http://127.0.0.1:8080/

## Opción 2 (Windows)

El nuevo `start-server.bat` sirve directamente la carpeta `site/`, abre navegador automático y añade `?v=RANDOM` para refrescar caché.

```bat
start-server.bat
```

Abre (sin `/site/`):

- http://127.0.0.1:8080/

## Cambiar puerto

- Linux/macOS: `./site/serve-local.sh 4173`
- Windows: `start-server.bat 4173`

## Si aparece error de proxy

1. Usa `127.0.0.1` en lugar de `localhost`.
2. Desactiva "Use proxy for localhost" en navegador/IDE/sistema.
3. Asegura `NO_PROXY=localhost,127.0.0.1,::1`.

## Validación rápida (opcional)

```bash
./site/validate-site.sh
```

Verifica que archivos clave, logos SVG y enlaces oficiales estén presentes.

## Enlaces oficiales configurados en la web

- Repositorio: https://github.com/jagrdev-MX/FridaMusicOF
- Perfil GitHub (JAGR): https://github.com/jagrdev-MX?tab=overview&from=2026-04-01&to=2026-04-19
- Instagram Frida Labs: https://www.instagram.com/fridalabs_mx/
- Instagram JAGR Developer: https://www.instagram.com/jagr.dev/
- Soporte: mailto:fridalabs.soporte@gmail.com

## Assets visuales

- `site/assets/fridamusic-logo.svg`
- `site/assets/fridamusic-logo-round.svg`

> Nota: Si sigues viendo la versión vieja, cierra el servidor anterior y vuelve a correr `start-server.bat`.

## Sección automática de desarrolladores

La sección de desarrolladores se genera desde:

- `site/assets/developers.json`

Para agregar un nuevo colaborador, añade un objeto con:

- `name`
- `role`
- `title`
- `profile_url`
- `avatar_url`
