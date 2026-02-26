# Changelog 2.4.2

- **Staff Ranks**: Added display for staff ranks (Founder, Dev, Mod, Admin).
- **Ban Indicator**: Added a skull icon (💀) for players banned from the tier list.
- **Optimization**: Implemented request rate-limiting (5 req/sec) to prevent server overload.
- **Cache Improvement**: Increased ban/staff list cache to 15 minutes and fixed "thundering herd" issues.
- **Safety**: Mod now respects the "Enabled" toggle and stops all network activity when disabled.

## Cambios y Optimizaciones (Español)
Para resolver los problemas de sobrecarga en el servidor y la página web, se han implementado las siguientes mejoras:

- **Limitación de Consultas (Rate-Limiting)**: Se añadió un sistema de cola que procesa las peticiones de jugadores de forma secuencial (máximo 5 por segundo). Esto evita que el servidor reciba cientos de consultas al mismo tiempo cuando un usuario entra a un lobby o partida.
- **Optimización de Caché**:
  - Se aumentó el tiempo de vida de la caché de baneos y staff de **5 a 15 minutos**, reduciendo la frecuencia de actualizaciones.
  - Se implementó protección contra el efecto "Thundering Herd": ahora, si varios procesos necesitan la misma información, todos esperan a una única solicitud en segundo plano en lugar de iniciar varias copias de la misma consulta.
- **Control Mod Ergonómico**: El mod ahora respeta estrictamente el estado "Desactivado", deteniendo inmediatamente cualquier actividad de red si el usuario lo apaga.
- **Compatibilidad**: Actualizado para soportar Minecraft **1.21.1, 1.21.4 y 1.21.5**.
