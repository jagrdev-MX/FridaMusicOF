# FridaMusic Site (Local)

## Opción 1 (Linux/macOS)

Desde la raíz del repo:

```bash
./site/serve-local.sh
```

Abre:

- http://127.0.0.1:8080/

## Opción 2 (Windows)

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
