# FridaMusic Site (Local)

## Opción 1 (Linux/macOS)

Desde la raíz del repo:

```bash
./site/serve-local.sh
```

Abre:

- http://127.0.0.1:8080/

## Opción 2 (Windows)

`start-server.bat` ahora fija la carpeta `site/`, abre el navegador automático y fuerza `127.0.0.1`.

Desde la raíz del repo:

```bat
start-server.bat
```

Abre:

- http://127.0.0.1:8080/

## Si ves "Directory listing for /"

- Estás sirviendo la raíz sin `index` o abriste otra carpeta.
- En este repo ya se redirige `/` hacia `/site/` automáticamente.
- También puedes abrir directo: `http://127.0.0.1:8080/site/`.

## Cambiar puerto

- Linux/macOS: `./site/serve-local.sh 4173`
- Windows: `start-server.bat 4173`

## Si aparece error de proxy

1. Usa `127.0.0.1` en lugar de `localhost`.
2. Desactiva "Use proxy for localhost" en navegador/IDE/sistema.
3. Asegura `NO_PROXY=localhost,127.0.0.1,::1`.

## Enlaces oficiales configurados en la web

- Repositorio: https://github.com/jagrdev-MX/FridaMusicOF
- Perfil GitHub (JAGR): https://github.com/jagrdev-MX?tab=overview&from=2026-04-01&to=2026-04-19
- Instagram Frida Labs: https://www.instagram.com/fridalabs_mx/
- Instagram JAGR Developer: https://www.instagram.com/jagr.dev/
- Soporte: mailto:fridalabs.soporte@gmail.com

## Nota de revisión

- La web reutiliza logos desde `app/src/main/res/mipmap-xxxhdpi/` para evitar agregar archivos binarios duplicados en el diff del PR.
