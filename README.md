# Cosmonautics: Expansión Planetaria (planetary_expansion)

Mod complementario de Create: Cosmonautics (NeoForge 1.21.1) que añade planetas aterrizables.
Es un mod solo de datos (`lowcodefml`): no necesita compilar Java.

## Contenido v0.2.0
| Planeta | Origen | Dimensión | Gravedad | Aire |
|---|---|---|---|---|
| Marte | sobrescribe `mars` de Cosmonautics (prioridad 1000) | `planetary_expansion:mars` | 3,7 | fino, irrespirable |
| Mundo helado | sobrescribe `ice_world` | `planetary_expansion:ice_world` | 11 | respirable hasta 5000 |
| Ignis | nuevo, orbita `gas_giant` | `planetary_expansion:ignis` | 1,8 | ninguno |

## Estructura
- `tools/generate.py` genera todo `src/main/resources` (no editar los JSON a mano).
- `tools/validate.py` comprueba referencias rotas y ciclos de orden de features.
- Cada planeta clona el enrutador de ruido vanilla con ruidos propios (`planetary_expansion:<planeta>/...`),
  así su relieve es único y no le afectan mods como Tectonic.

## Reconstruir
```
python3 -I tools/generate.py <datos_vanilla> src/main/resources
python3 -I tools/validate.py src/main/resources <datos_vanilla> <cosmonautics_extraido>
cd src/main/resources && zip -r -X ../../../build/planetary_expansion-0.4.0.jar META-INF/MANIFEST.MF . -x '.*'
```
`<datos_vanilla>` = contenido de `client-1.21.1-20240808.144430-extra.jar` (carpeta con `data/minecraft/worldgen`).

## Probar
`/execute in planetary_expansion:mars run tp @s 0 200 0` (igual con `ice_world` e `ignis`).

## Cielo con shaders (Complementary Reimagined)
Complementary trata cualquier dimensión desconocida como el Overworld (`dimension.world0=*`): en la Luna e Ignis
dibuja cielo diurno con atmósfera y nubes, que además tapan las estrellas de Cosmonautics (corte diagonal = borde de la capa de nubes).
`tools/patch_complementary.py` crea una copia del pack con una carpeta `world_space` (define `SPACE_DIM`) para las
dimensiones sin aire: cielo negro, sin nubes, niebla atmosférica, rayos de luz, aurora ni nebulosa, y sin oscurecer el cielo de Cosmonautics.
```
python3 -I tools/patch_complementary.py ComplementaryReimagined_r5.9.3.zip ComplementaryReimagined_r5.9.3_Cosmonautics.zip rocketnautics:moon planetary_expansion:ignis
```
Licencia de Complementary: modificar para uso personal está bien; NO se puede redistribuir ni incluir modificado en un modpack.

## Código Java (desde 0.2.0)
El mod pasó de `lowcodefml` a `javafml` para poder incluir compatibilidades.
- `PlanetaryExpansion`: clase principal; activa compatibilidades solo si el otro mod está instalado.
- `DeepSeasOxygenCompat`: escucha `LivingBreatheEvent` (prioridad LOW, después de Cosmonautics que va en HIGHEST).
  Si la entidad no puede respirar por la bandera `drowning` de Cosmonautics pero sus ojos están dentro de un
  compartimento sellado de Create Deep Seas (`CompartmentTracker.isInSealedExact`), puede respirar.
Compilar: `tools/build_jar.sh <libraries> <cosmonautics.jar> <create_submarine.jar>` (JDK 21; sin Gradle).

## Clima (desde 0.3.0)
Paquete `dev.antme08.planetaryexpansion.weather`.
- `StormType`: Marte = tormenta de arena (calma 1–2 días, dura 3–8 min); planeta helado = ventisca (calma 3–6 días, dura 2–5 min). Rampa de 30 s.
- `StormManager` (servidor): ciclo calma → subida → tormenta → bajada guardado en `data/planetary_expansion_storms.dat`;
  sincroniza con los clientes (`StormSyncPayload`); golpes de piedra (tipo de daño `planetary_expansion:dust_storm`, ~1 cada 4 s,
  afectado por armadura) y congelación (≈15 s hasta congelarse; al resguardo se descongela solo); empuja entidades;
  comando `/planet_weather status | start <planeta> [minutos] | stop <planeta>` (nivel de permiso 2).
- `StormPhysics`: empuja naves de Sable según su superficie expuesta, con aceleración máxima de 3 m/s²; no empuja naves bajo techo.
- `StormShelter`: expuesto = sin bloques encima (como la lluvia). Daño/frío no aplican dentro de compartimentos con oxígeno de
  Deep Seas; el empuje directo no aplica dentro de naves (se empuja la nave).
- `client/ClientStormEffects`: niebla y color, partículas con viento (polvo rojo / copos de nieve), sonido de viento, empuje del jugador.
- Configuración por mundo: `serverconfig/planetary_expansion-server.toml` (enabled, frequency, duration, windStrength, pushShips, dustDamage, blizzardFreezing).

### 0.3.1
- Niebla de tormenta más densa (Marte 9 bloques, ventisca 7). Con Complementary se dibuja en el shader parcheado:
  lee `fogStart`/`fogEnd`/`fogColor` de Iris (que el mod acorta y tiñe durante la tormenta) en `mainFog.glsl` y en el cielo de `deferred1`.
- Partículas: 110/130 por tick, el doble de rápidas, más pequeñas y nunca pegadas a la cámara.
- Planeta helado: noche permanente (`fixed_time: 18000` en su dimension_type y sin controlador de hora de Cosmonautics).

### 0.3.2
- Dos niveles de tormenta (como lluvia / tormenta eléctrica): **normal** = viento, partículas y poco daño
  (arena: ~1 golpe cada 8 s al 60 %; ventisca: ~35 s para congelarse); **fuerte** = además niebla (16 bloques) y más daño
  (arena: ~1 golpe cada 3 s; ventisca: ~12 s). Probabilidad de fuerte: Marte 35 %, planeta helado 40 %.
- Niebla naranja de arena roja en Marte y blanca de nieve en el planeta helado; de noche se oscurece como mucho a la mitad.
- Comando: `/planet_weather start <planeta> [normal|fuerte] [minutos]`.

### 0.3.3
- Molinos de Create (`WindmillBearingBlockEntity`) giran más rápido en tormentas: hasta ×1.6 (normal) y ×2.5 (fuerte),
  solo si no hay bloques encima del rodamiento. Mixin `mixin/CreateWindmillBearingMixin` (remap=false): multiplica
  `getGeneratedSpeed` cuando las aspas están montadas y revisa el factor cada segundo; si cambia llama a
  `updateGeneratedRotation()`. Más velocidad = más SU generadas. Config `windmillBoost`.
- Ahora depende explícitamente de Create (ya lo exigía Cosmonautics).
- Compilar: `tools/build_jar.sh <libraries> <cosmonautics> <create_submarine> <sable> <sable-companion> <create.jar> <jars_anidados_create>`.

### 0.3.4
- El viento ya no empuja estructuras de Sable apoyadas en el suelo (ni junto a chunks sin cargar). En 0.3.3 una ventisca
  arrastró una base sobre hielo hasta un borde; al caer superó los 25 m/s y el "Max-Q" de Cosmonautics
  (`GlobalSpacePhysicsHandler.applyMaxQStress`: |v|² > 625 → rompe bloques al azar) la destrozó.
- Molinos: se consideran expuestos si hay cielo abierto delante del rodamiento (donde están las aspas) o encima de él.
  Log `[Clima] Molino en ...` al empezar/terminar el efecto.

### 0.3.5
- Viento sobre estructuras de Sable según ruedas (etiqueta `planetary_expansion:wind_wheels`: Offroad, Offroad Step Up,
  Aero No Horizon; ampliable por datapack):
  - con ruedas: se empujan siempre; en el suelo hasta el 40 % de la velocidad del viento y con el 60 % de la fuerza;
  - sin ruedas en el suelo (bases): nunca se mueven;
  - sin ruedas en el aire (naves volando): se empujan (config `pushFlyingShips`, por defecto true).
- Detección: cada 2 s por estructura, mirando la paleta de bloques de cada sección de su plot (`PalettedContainer.maybeHas`).

### 0.3.6
- Mesa holográfica de Cosmonautics: marcador de color y nombre de cada cuerpo celeste (Sol, Tierra, Luna, Marte,
  Gigante gaseoso, Mundo helado, Ignis) con la distancia desde la mesa. Mixin cliente `HologramTableRendererMixin`
  (envuelve `DeepSpaceHandler.renderHologram`); el dibujo está en `client/HologramLabels`. Si dos etiquetas se tapan
  se muestra la del cuerpo más grande (acercar la escala de la mesa para ver lunas).
- Molinos: el chequeo de tormenta pasa de `TAIL` a `RETURN`. El `tick()` del rodamiento de molino de Create sale antes
  con `if (!queuedReassembly) return;` en el servidor, así que con `TAIL` casi nunca se ejecutaba.
- Mundo helado: `planet_extras` vuelve a incluir `light_source_name: sol` (se perdía al sustituir el bloque).

### 0.3.7
- Molinos: cada vela/ala tiene su potencia (1 = vela de Create), con etiquetas
  `planetary_expansion:windmill_sail_power/<valor>` (0.5, 0.75, 1, 1.25, 1.5, 2, 3). Mixin `BearingContraptionSailMixin`
  en `BearingContraption.addBlock`: suma la diferencia con lo que cuenta Create; las fracciones se acumulan.
  - 0.5 marco de vela ardiente (Burnt) · 0.75 velas quemadas/ardientes (Burnt)
  - 1 vela integrada I (Flight Control), ala copycat 8 (Propulsion)
  - 1.25 velas de Create Deep Seas, ala copycat 12
  - 1.5 ala y ala copycat (Propulsion), vela integrada II, vela fantasma (Alex's Mobs)
  - 2 ala templada (Propulsion), vela integrada III, vela espectral (Alex's Mobs)
  - el resto de `create:windmill_sails` (velas de Create, lana, velas simétricas de Simulated, terciopelo) vale 1.
- Molinos sobre estructuras de Sable: el cielo abierto se comprueba en la posición real del mundo
  (`Sable.HELPER.projectOutOfSubLevel`), no en el plot.
- Compilación: stub de `org.apache.commons.lang3.tuple.Pair` en `tools/stubs` (no se empaqueta).

### 0.3.8
- Mesa holográfica: texto más pequeño y sin dibujarse a través de otros objetos; la etiqueta queda encima de su
  marcador (antes se dibujaba hacia abajo y lo tapaba). Los cuerpos muy juntos (p. ej. Tierra y Luna) comparten
  una etiqueta de varias líneas en vez de ocultarse. Marcadores más grandes para cuerpos más grandes.
  Nueva etiqueta "Tú"/"You" en tu posición (el cubito cian de Cosmonautics).

### 0.3.9
- Sin asteroides naturales en Marte, el Mundo helado ni Ignis. Cosmonautics los genera alrededor de cualquier jugador
  por encima de y=5000 en cualquier dimensión; en un planeta con gravedad caen y destrozan lo que haya debajo (así se
  destruyó la base de Marte del usuario). Mixin `AsteroidSpawnerMixin` (HEAD de `AsteroidSpawner.onPlayerTick`).
  En el espacio profundo, la Tierra y la Luna no cambia nada; `/rn asteroid spawn` sigue funcionando.

### 0.3.10
- Base de Marte destruida (2026-11-05): no fue el asteroide (no había ningún bloque de asteroide entre los objetos) ni
  la tormenta (Marte en calma). Los objetos tenían ~220 ticks: se rompió ~4 s después de volver a Marte, al cargarse
  la zona. La rompió el Max-Q de Cosmonautics (explosiones en estructuras a más de 25 m/s); qué la lanzó no queda en
  el log.
- `StructureLoadGuard`: en los planetas del mod, durante 5 s tras cargarse una estructura que estaba en el suelo,
  se frena si se mueve a más de 3 m/s y Max-Q no le rompe bloques (`GlobalSpacePhysicsMixin`, HEAD de
  `applyMaxQStress`). Avisos en el log: `[Estructuras] ... se movió a X m/s justo al cargarse` y
  `[Estructuras] ... Cosmonautics le rompe bloques por exceso de velocidad (Max-Q)`.
- El bloqueo de asteroides de 0.3.9 se mantiene (es correcto aunque no fuera la causa).

### 0.6.2
- Thalassa tiene ventiscas como el planeta helado (mismo viento, partículas y congelación), pero más frecuentes (calma de 1 a 2 días en vez de 3 a 6) y casi siempre normales: la fuerte, con niebla, sale el 15 % de las veces (40 % en el planeta helado). `/planet_weather start thalassa [normal|fuerte]`.
- Durante una ventisca de Thalassa el frío de la superficie no se suma al de la ventisca.

## 0.6.1
- Thalassa sin lava en el fondo: con los acuíferos desactivados Minecraft rellena con lava todo hueco por debajo de y -54, y el fondo bajaba hasta ~y -67. Ahora la superficie está en y 75 y el fondo en y -45 ±6 (nunca por debajo de -51): el mar sigue teniendo ~120 bloques. El aire llega hasta y 90 (15 sobre el hielo).
- Menos tirones: los ruidos 2D se calculan una vez por columna (cache_2d) y ya no hay lava y agua chocando (provocaba miles de actualizaciones de bloques).
- Textura desde el espacio: tres biomas (corteza de hielo, campos de hielo azul y "lineae", franjas como las de Europa) con colores distintos; en el suelo solo cambia la capa de arriba.

## 0.6.0
- Nuevo planeta: **Thalassa**, luna oceánica del gigante gaseoso (idea del usuario).
  - Corteza de hielo compacto de 3-6 bloques (y 58-63) con nieve, grietas y algún agujero.
  - Mar vacío de ~120 bloques; fondo de hielo azul (~y -60) de 7-12 bloques.
  - Debajo (hasta y -128): zonas de piedra entre hielo compacto con hierro, carbón y titanio abundantes y diamante raro.
  - Aire respirable solo hasta y 78 (15 bloques sobre el hielo); gravedad 2 m/s²; sin mobs ni luz bajo el hielo.
  - Frío en la superficie (te congelas en ~70 s a la intemperie fuera del agua); la Glacial Armor lo reduce y el traje completo lo anula.
- `tools/validate.py` acepta dimensiones con un solo bioma (`minecraft:fixed`).

## 0.5.2
- Botas de los dos trajes: llevan un lingote de netherita entre las dos láminas de arriba (lámina · netherita · lámina / titanio · vacío · titanio). Cada traje completo cuesta 7 lingotes de netherita.

## 0.5.1
- Recetas de crafteo de las dos armaduras, tal como las dejó el usuario en cofres. La Impact usa láminas de hierro industrial (Create Deco) y la Glacial láminas de hierro de Create; la pechera Impact pasa a usar también láminas de hierro industrial para no chocar con la Glacial, que tenía la misma receta.
- La mano en primera persona con el traje: el cuerpo solo se oculta en la vista previa del inventario (se detecta por la pila de llamadas: renderEntityInInventory). En 0.4.7 la pasada de sombras de Iris, también ortográfica, la volvía a ocultar.

## 0.5.0
- Glacial Armor (traje del planeta helado): el mismo diseño que la Impact en blanco apagado con visor azul oscuro (elegido por el usuario con vista previa; `tools/designs/glacial_armor.bbmodel`). Funciona como el traje de Cosmonautics (casco espacial, jetpack, propulsores de pierna, botas ancla) y además:
  - cada pieza: -25 % de congelación en las ventiscas, -15 % de daño por congelación y -20 % de empuje del viento;
  - traje completo: nunca te congelas (tampoco en nieve polvo) y el viento de las tormentas solo te empuja un 20 %;
  - botas: se puede andar sobre la nieve polvo.
- En la pestaña "Cosmonautics World", detrás de la Impact Armor. Iconos provisionales en blanco y azul.
- El jetpack original de Cosmonautics no se dibuja encima de ninguno de los trajes del mod.

## 0.4.7
- La mano en primera persona con el traje ya no desaparece al abrir el chat u otra pantalla: en primera persona solo se oculta el cuerpo en la vista previa del inventario (dibujada con la proyección de la GUI).

## 0.4.6
- Mesa holográfica: los cuerpos que no caben en el holograma a la escala elegida ahora salen en el borde, en su dirección, con un marcador apagado y la etiqueta "(fuera de escala)" y su distancia. El planeta helado está mucho más lejos que el resto (órbita de 56 días), por eso desde Marte no cabía y desde el planeta helado no cabía nada más.

## 0.4.5
- Arreglo de las bases que se rompían al alejarse y volver (o al morir y reaparecer): Sable elimina las estructuras cuya masa queda en 0 y rompe todos sus bloques; a veces la masa se queda en 0 al recargar aunque los bloques estén. Ahora, justo antes, se recalcula la masa desde los bloques y la estructura se salva (aviso `[Estructuras] Sable iba a destruir ...` en el log).
- El diagnóstico de 0.4.4 crasheaba el juego al detectar la rotura (un fallo mío); corregido y protegido para que nunca pueda tirar el juego.

## 0.4.4
- Dentro de una estructura con oxígeno de Create Deep Seas el traje ya no gasta aire: el permiso para respirar se da antes de que Create descuente aire del traje.
- Mano en primera persona con el traje: en primera persona ya no se oculta ninguna parte del jugador, y al dibujar el brazo se fuerza que sea visible (si estaba oculto se avisa una vez en el log con `[Armaduras]`).
- Diagnóstico: si en un planeta del mod se rompen muchos bloques de golpe (16 o más objetos en 10 ticks) o una explosión rompe bloques, el log muestra `[Estructuras] ...` con la pila de llamadas, que dice qué mod lo hizo.

## 0.4.3
- La mano en primera persona ya no desaparece con la pechera puesta ni parpadea: las partes del jugador ocultas por el traje se vuelven a mostrar en cuanto se termina de dibujar al jugador (TaCZ y los shaders dibujan la mano con el mismo modelo).
- Tormentas: una estructura montada encima de otra (p. ej. un molino sobre un rodamiento giratorio de Simulated sobre la base) ya no se trata como una nave volando; antes el viento la empujaba y tiraba de la base hasta romperla.
- Aviso `[Clima] El viento empuja '<estructura>' ...` en el log, una vez por estructura y tormenta.

## 0.4.2
- Textura de la Impact Armor con sombreado (relieve en paneles y marcos, degradado, desgaste suave y reflejo en la visera). El sombreado va dentro de `tools/designs/impact_armor.bbmodel`, así que el diseño de Blockbench y el juego coinciden.
- `tools/shade_armor.py` (aplica ese sombreado a una textura) y `tools/preview_armor.py` (vista previa del traje sin abrir el juego).

## 0.4.1
- La Impact Armor ahora sale en el inventario creativo: al final de la pestaña "Cosmonautics World" (en Cosmonautics 26.08.307 su propio traje no está en ninguna pestaña, por eso antes no aparecía).
- Con una pieza de un traje del mod puesta se oculta la parte del cuerpo que cubre (y la capa exterior de la skin), para que el pelo o la ropa de la skin no asomen por encima de la armadura.

## 0.4.0 — Armadura de impacto (Impact Armor)
- Diseño del usuario en Blockbench: `tools/designs/impact_armor.bbmodel` (plantilla de armadura de GeckoLib).
  `generate.py` lo convierte en `assets/planetary_expansion/armor_models/impact_armor.json` (coordenadas de Minecraft:
  x invertida, y = 24 − y; rotaciones (−x, −y, z); cubos girados → sub-partes) y extrae la textura embebida a
  `textures/models/armor/impact_armor.png`. `client/ArmorModels` construye un HumanoidModel por pieza al primer
  dibujo (se recarga con F3+T). Grupos: `armorHead`→casco, `armorBody/RightArm/LeftArm`→pechera,
  `armorRightLeg/LeftLeg`→pantalones, `armorRightBoot/LeftBoot`→botas; `dontTouch` y `reference*` se ignoran.
- Piezas (heredan de Cosmonautics, que las reconoce con instanceof): casco → `SpaceHelmetItem`, pechera →
  `JetpackItem` (combustible con capability propia, fuente de aire de Create), pantalones → `LegThrustersItem`,
  botas → `AnchorBootsItem`. Material netherita. `JetpackLayerMixin` evita que se dibuje el jetpack original.
- Extras: −25 % daño de tormenta de arena y −15 % de fuego por pieza; traje completo inmune a ambos (y lava);
  botas −60 % daño por caída. En la pestaña de Cosmonautics tras las botas ancla.
- Iconos provisionales generados (16×16); si existe `tools/designs/<item>.png` se usa ese.
- Compilación: añadido gson al classpath.

## 0.7.0 — Venus y Hazard Armor
- **Venus** (órbita del Sol por dentro de la Tierra): llanuras de basalto (y ~70), volcanes en escudo con caldera
  y lago de magma (borde y ~100, fondo y ~83) y mesetas de teselas agrietadas (+28). Sin aire, atmósfera densa
  (presión de Sable 2.5), gravedad 8.9, neblina amarilla permanente (40 bloques; 30 en la lluvia fuerte).
- **Calor**: en todo el planeta salvo en compartimentos sellados con oxígeno (Deep Seas). **Lluvia ácida**
  (frecuente, 30 % fuerte con rayos solo visuales): solo a la intemperie. Primero desgastan la armadura puesta
  (aviso en la barra de acción: calor / lluvia / ambos); sin armadura, medio corazón por segundo.
  Impact completa: calor a la mitad. Config: venusHeat, acidRainDamage, acidRainCorrosion, acidRainLightning.
- La lluvia ácida corroe lana, madera y plantas a la intemperie (etiqueta `acid_rain_corrodes`).
- **Agua**: se evapora en el terreno (cubos y agua a la intemperie), pero no dentro de estructuras físicas de Sable
  (`VenusWaterMixin` sobre `FluidType.isVaporizedOnPlacement`). La dimensión no es ultracaliente.
- Minerales: hierro, cobre, oro; con Create Nuclear, uranio 10, torio 12, plomo 16, nitrato 10 por chunk
  (biome modifier condicionado).
- **Hazard Armor**: como las piezas de Cosmonautics; -25 % calor y lluvia ácida por pieza (completa: inmune) y
  +0.25 de `createnuclear:generic.irradiated_resistance` por pieza. Sin receta todavía (pestaña creativa).

## 0.7.1 — Receta de la Hazard Armor
- Nueva **Lámina de plomo** (`lead_sheet`): lingote de plomo de Create Nuclear en la prensa mecánica de Create.
- Hazard Armor con la misma base que la Impact (patrones de la Impact) usando láminas de plomo. Las recetas solo
  existen con Create Nuclear instalado.

## 0.7.2 — Create Nuclear obligatorio
- Create Nuclear (2.0+) pasa a ser dependencia obligatoria: se quitan las condiciones de los minerales de Venus,
  la lámina de plomo y las recetas de la Hazard Armor.

## 0.7.3
- El casco Hazard ya no está en `createnuclear:anti_radiation_helmet` (ponía la vista de máscara de gas verde de
  Create Nuclear). La resistencia a la radiación sigue (atributo).
- Holograma: las etiquetas también se separan en pantalla (antes, etiquetas lejanas en 3D podían taparse según el
  ángulo, p. ej. Marte sobre las lunas "fuera de escala").

## 0.7.4
- Venus: la neblina deja ver ~60 bloques (antes 40) y ~40 en la lluvia ácida fuerte (antes 30), y es más suave
  de cerca (empieza al 30 % de la distancia).

## 0.7.5
- Las máquinas con patas de Let it Walk! (núcleo, articulaciones o pies: etiqueta `planetary_expansion:wind_legs`)
  cuentan como apoyadas en el suelo: el viento de las tormentas no las empuja.

## 0.7.6
- Venus menos blanco: neblina ámbar más oscura y saturada (0.70, 0.52, 0.24), lluvia ácida oliva, y cielo/niebla del
  bioma más oscuros. Con el Complementary modificado la niebla se pinta con fogColor² × 1.8 y el color claro de
  antes salía casi blanco.

## 0.7.7
- Venus más variado en el mapa/holograma: provincias a escala de planeta (ruido -23) con nuevo bioma
  `venus_dark_plains` (llanuras de basalto oscuro) y "tierras altas" (ruido -22) donde se juntan las teselas.
- Ríos de lava: `planetary_expansion:venus_lava_vent` (feature Java) pone respiraderos en las faldas altas de los
  volcanes (y 86-106, con pendiente); la lava se programa con tick y baja sola por la ladera.
- Lluvia ácida mucho más densa: ~300 gotas por tick en la fuerte, en 24×24 bloques, gotas con estela y salpicaduras.

## 0.7.8
- Arregla el crash al abrir mundos de 0.7.7 con C2ME (aceleración OpenCL): "Cannot register more caches". Venus
  tenía un `cache_2d` dentro de otro. validate.py ahora lo detecta.

## 0.7.9
- Mapa/holograma de Marte, planeta helado, Ignis y Thalassa con zonas a escala de planeta (como Venus en 0.7.7):
  `province()` suma un ruido de millones de bloques a un parámetro de clima que solo elige bioma (no el relieve).
  Marte: provincias frías (casquetes) y oscuras/claras (altiplanos/dunas). Helado: agujas (azul) / glaciar.
  Ignis: azufre / ceniza. Thalassa: red de lineae a escala de planeta + provincias de hielo azul.

## 0.7.10
- Arregla el crash al crear mundo de 0.7.9: los rangos de clima de multi_noise solo admiten [-2, 2] (había -3/3).
  validate.py ahora lo comprueba.

## 0.7.11
- Arreglo para Cosmonautics: desde lo alto todos los planetas se veían como la Tierra. El cliente guarda la textura
  del planeta de abajo y solo la vuelve a pedir si cambia la altura o la zona, sin mirar la dimensión.
  `client/PlanetMapRefresh` la pide otra vez (PlanetMapRequestPayload) 1 s después de cambiar de dimensión o de mundo.

## 0.7.12
- Holograma: el tamaño del texto de cada planeta se adapta al tamaño con que se dibuja el planeta (máximo el de
  antes; cubitos pequeños → texto al 40 %, fuera de escala 35 %, "You" 60 %). Cada línea de un grupo lleva su tamaño.

## 0.7.13
- Holograma: las etiquetas solo se juntan si los cuerpos están casi encima (1.6 líneas de texto, antes 5).
  El resto lo separa el paso de pantalla.

## 0.7.14
- Arreglo para Cosmonautics: la mesa de hologramas dibuja órbitas/trayectorias con `RenderType.lineStrip()`, que
  comparte un búfer: cada tira empezaba donde acababa la anterior (líneas sueltas entre planetas y, con dos mesas,
  una línea de una mesa a otra). `HologramLineStripMixin` cierra la tira (endBatch) antes y después de cada trazo.

## 0.7.15
- Las líneas sueltas del holograma (0.7.14 no lo arreglaba con Iris/Sodium y hasta salían más): ahora toda la mesa
  dibuja con `StripToLinesSource`, que convierte cada `lineStrip` en segmentos sueltos de `RenderType.lines()`.
  Cada trazo es independiente, aunque el búfer sea compartido.

## 0.7.16
- Arreglo para Cosmonautics: al cruzar planeta ↔ espacio profundo el jugador quedaba fuera de la nave (y, sentado,
  flotando en pose de sentado). Cosmonautics teletransporta al jugador y copia la nave por separado (nave nueva).
  `weather/ShipTransferGuard` apunta cada tick dónde va el jugador dentro de su nave (relativo al centro de masa) y
  su asiento de Create; tras cruzar, durante 4 s busca la copia de la nave y lo vuelve a sentar (SeatBlock.sitDown)
  o lo mantiene en su sitio con la velocidad de la nave.

## 0.7.17
- **Generador de gravedad** (bloque nuevo, pestaña de Cosmonautics). Bloque cinético de Create: consume 16 SU por RPM
  y, mientras gira sin sobrecarga, todo lo que esté dentro o encima de la misma nave (jugadores, mobs, objetos) tiene
  la gravedad normal aunque esté en el espacio profundo. La gravedad apunta hacia abajo del mundo, así que funciona
  mejor con la nave derecha. Receta: láminas de latón, mecanismo de precisión, ejes, carcasa de latón y ojo de ender.
  Código: `gravity/` y `mixin/EntityGravityMixin`.
- **Nave congelada al desconectarse** (`weather/ShipFreeze`): en un planeta, si sales con la nave en el aire y no
  queda nadie a bordo, se guarda su velocidad y se fija en el sitio (como la varita física). Al volver te sienta en
  tu asiento, cuenta 3 s y la suelta con la misma velocidad que llevaba.

## 0.7.18
- El generador de gravedad ahora también necesita una **señal de redstone** para encenderse (además de la rotación).
  Las gafas de Create dicen si está apagado por falta de señal o por falta de rotación.

## 0.7.19
- Todo el texto del juego en inglés por defecto y traducción completa al español (es_es, es_mx y demás variantes):
  nombre y descripción del mod, tormentas, salida del comando `/planet_weather` (ahora `start <planeta> severe`
  en vez de `fuerte`) y comentarios del archivo de configuración.

## 0.7.20
- **Navegación espacial con el HUD del casco** (tecla Y, en el espacio profundo): nombre y distancia de cada planeta
  sobre él en el cielo, flechas en el borde de la pantalla para los que no se ven ("Sun >"), y marcadores de
  prógrado (hacia dónde avanzas, celeste) y retrógrado (naranja), también con flecha en el borde. Pensado para la
  cámara en tercera persona de los asientos. Código: `client/SpaceNavOverlay`.

## 0.7.21
- Texto de la navegación espacial más pequeño (nombres al 60 %, distancias al 50 %).

## 0.7.22
- **Órbitas perdidas al volver al espacio con un waystone** (`weather/OrbitRescue`): Cosmonautics retira la instancia
  del espacio profundo si te vas sin volar y la deja sin nadie, aunque la nave siga ahí; al volver no salía el HUD
  del casco, la mesa de hologramas no dibujaba nada y los motores no cambiaban ninguna órbita. Ahora se guarda cada
  segundo una copia de cada órbita y, si un jugador está en el espacio sin instancia, se recrea en el mismo hueco
  con su órbita. Si no hay copia (naves perdidas antes de este arreglo), órbita circular de emergencia a 100 km del
  último planeta del que viniste (o de la Tierra).

## 0.7.23
- **Flecha de empuje**: en la mesa de hologramas sale una flecha verde desde "Tú" hacia donde están empujando los
  motores, y con el HUD del casco un marcador verde "Thrust X m/s²" (también con flecha en el borde de la pantalla).
  El servidor suma lo que Cosmonautics pasa a la órbita en `DeepSpaceInstance.applyVelocity`
  (`mixin/DeepSpaceThrustMixin`, `weather/ThrustTracker`) y lo manda cada medio segundo.

## 0.7.24
- **Sea Diver Armor** (4.ª armadura, idea del usuario): latón y azul marino, mismo modelo que la Glacial
  (`tools/designs/sea_diver_armor.bbmodel`). Casco/pechera/pantalones como los otros trajes (aire, jetpack,
  propulsores); botas normales para poder nadar. Frío como la Glacial (`GlacialArmor.coldPieces`), +12.5 % de nado
  por pieza, y con el traje completo sin daño por presión de Create Deep Seas hasta 600 m
  (`mixin/DiverPressureMixin`; más hondo cuenta como el traje de cobre de Deep Seas). Recetas con láminas de latón.
- **Create Deep Seas pasa a ser obligatorio** (como Create Nuclear).
- Iconos nuevos de los cascos Impact, Glacial y Hazard dibujados por el usuario.

## 0.7.25
- Sea Diver Armor con el diseño del usuario: traje azul marino liso, placa de pecho, cinturón y guantes de latón, e iconos dibujados por él (la placa también en el icono de la pechera).

## 0.7.26
- **Mega cuevas submarinas en Thalassa** (idea del usuario, vista previa aprobada): la dimensión baja hasta y -320.
  Red de túneles enormes (20-30 bloques, para submarinos) entre y -60 y -300, mega cavernas, simas en embudo desde
  el fondo del mar y fosas en U por cuyas paredes salen los túneles. Todo inundado y oscuro
  (`mixin/ThalassaFluidPickerMixin`: sin él, los huecos bajo y -54 se llenarían de lava). Más minerales (titanio,
  hierro, diamante), geodas de amatista y prismarina sin brotes (sin luz), columnas de espeleotemas poco comunes y
  chimeneas hidrotermales raras de basalto con columna de burbujas (`worldgen/ThalassaVentFeature`).
- Las dimensiones de Thalassa ya generadas se movieron a `_to_delete` (cambia la altura de la dimensión).

## 0.7.27
- Arreglo del crash al crear mundo de 0.7.26: `environment_scan` de las chimeneas de Thalassa con max_steps 40 (máximo 32). El validador ahora revisa esos límites.

## 0.7.28
- **Minerales abundantes por planeta** (contenido de final de juego, cantidades altas a propósito):
  Marte = zinc de Create, vetas enormes de crimsita (hierro) y algo de ocrum (oro), más hierro;
  Mundo helado = vetas enormes de asurina (zinc), zinc y diamante; Ignis = oro, piedra negra dorada, cobre y vetas
  enormes de ocrum y veridio (en la piedra negra/basalto); Venus = minerales de Create Nuclear al doble;
  Thalassa sigue con mucho titanio, hierro y diamante. Solo en terreno nuevo.

## 0.7.29
- **Amortiguador inercial** (idea del usuario): multibloque de 3x3x3 con el **núcleo** en el centro y 26 bloques de
  **carcasa** alrededor (carcasa, **entrada de rotación** o **puerto de refrigerante**). Paquete `dampener/`.
  - Se carga con rotación de Create por las entradas (24 SU/RPM; 30 s a 256 RPM, 2 min a 64 RPM; varias entradas suman).
  - Una señal de redstone (flanco de subida) en cualquier bloque de la estructura lo dispara si está al 100 % y frío.
  - En un planeta quita a la nave toda la velocidad y el giro. En el espacio profundo deja la órbita circular a la
    distancia actual (mismo plano y sentido) y para el giro; se re-aplica 2 ticks para absorber impulsos en cola.
  - Después queda al 100 % de calor: aire 10 min, agua 3 min (4000 mB), nitrógeno líquido de Create Nuclear 45 s
    (1000 mB, tag `c:nitrogen`). Tanque de 8000 mB en el núcleo; los puertos lo exponen a tuberías y cubetas.
  - Goggles de Create en la entrada o el puerto: estructura, carga, calor, refrigerante y "listo".
  - Recetas caras: núcleo = 4 lingotes de netherita + 4 mecanismos de precisión + estrella del Nether; carcasa (x4) =
    láminas de latón, láminas robustas y carcasa de ferrocarril; entrada = carcasa + caja de engranajes + mecanismo;
    puerto = carcasa + tanque de fluidos + lámina de cobre.

## 0.7.30
- Amortiguador inercial con diseño nuevo (petición del usuario): sin latón, colores de netherita y de estrella del
  Nether (los materiales de la receta). Vetas diagonales y destellos de estrella en cada bloque.
- **Texturas conectadas** con el sistema de Create (`CTModel` + hoja OMNIDIRECTIONAL generada a partir de la
  disposición de `brass_casing_connected`): carcasa, puerto y entrada se conectan entre sí, así cada cara del cubo
  se ve como un solo panel con borde solo por fuera.

## 0.7.31
- Carcasa del amortiguador sin el destello de estrella del centro: solo la equis de vetas (petición del usuario).

## 0.7.32
- **Entrada de redstone del amortiguador** (petición del usuario, como en los reactores de Create Nuclear): nuevo
  bloque de carcasa. Ahora solo una señal que llegue a una entrada de redstone dispara el amortiguador (antes valía
  cualquier bloque del cubo, y un circuito pegado podía dispararlo sin querer). Con un comparador da la carga
  (0-14) y 15 cuando está listo. Receta: carcasa + bloque de redstone + comparador. Textura conectada.
- Todas las piezas (entrada de eje, puerto de refrigerante, entrada de redstone) pueden ir en cualquiera de los 26
  lugares de la carcasa, y se puede poner más de una de cada una.

## 0.7.33
- Arreglo: al subir al espacio manejando **de pie** y rápido, el jugador quedaba desprendido de la nave y a la deriva
  (log del usuario: ninguna línea "[Naves]" al cruzar a las 17:18:51 y luego el rescate de órbitas cada segundo).
  - `ShipTransferGuard` toma la nave del jugador de Sable (`getTrackingOrVehicleSubLevel`) y, si no, de la caja de la
    nave con 10 bloques de margen (como Cosmonautics); antes 1.5 y a mucha velocidad el jugador quedaba fuera.
  - La copia de la nave se reconoce también por la masa (±2 %) y se busca hasta 512 bloques.
  - Red de seguridad: jugador en el espacio profundo fuera de cualquier instancia durante 3 s → se le devuelve a su
    última nave (y queda 4 s pegado a ella / en su asiento).
  - Más registro en el log: "cruzó ... sin ir en ninguna nave", "no se encontró la copia", "a la deriva".
- `OrbitRescue`: ya no crea la órbita de emergencia si el jugador no quedaría dentro de esa instancia (se repetía cada
  segundo sin servir de nada).

## 0.7.34
- **Generador de gravedad con modelo 3D de 1x2** (petición del usuario; vista previa aprobada): base de latón con
  rejillas violetas y el eje atravesándola en horizontal, y encima una jaula abierta con un núcleo violeta flotante
  entre dos emisores.
  - `GravityGeneratorBlock` pasa a `HorizontalAxisKineticBlock` (el eje entra por los lados de la base) y necesita un
    bloque libre encima. Nuevo `gravity_generator_top`: invisible, sin objeto; ocupa el sitio de la jaula.
    Romper cualquiera de las dos partes quita la otra; si la de arriba falta, la base la repone cada 2 s.
  - El núcleo es un modelo suelto (`PartialModel`) que dibuja `GravityGeneratorClient.Renderer`: gira, sube y baja y
    brilla mientras el generador está encendido; se frena al apagarse.
  - Textura y modelos: `tools/designs/gravity_generator_model.py --export tools/designs` (atlas de 128x128 y JSON de
    bloque, núcleo e ítem); la vista previa sale del mismo script.
  - Los generadores ya colocados con el eje vertical quedan con el eje en horizontal (hay que reconectar el eje).

## 0.7.35
- **Armaduras con más detalle y más piezas 3D** (propuestas aprobadas por el usuario): Impact, Hazard, Glacial y
  Sea Diver. Las piezas nuevas toman sus colores de la textura de cada traje (mismo estilo de panel) para que se vean
  integradas. Sin cresta en el casco Impact ni gorro en el Glacial (no le gustaron).
  - `tools/designs/armor_concepts.py tools/designs/armor_v1 <salida> tools/designs` genera los `.bbmodel` nuevos a
    partir de la versión 1 (guardada en `tools/designs/armor_v1/`); `generate.py` los convierte como siempre.
  - Sea Diver: marco atornillado y rejilla sobre el visor del propio casco, ventanillas laterales (sustituyen a las
    orejeras), foco, collar, tanques, hélice, aletas, profundímetro y lastres.
- Generador de gravedad:
  - En la prueba del usuario no estaba roto: le llegaba rotación (159 RPM) pero no tenía señal de redstone
    (`powered=false` en el guardado y ninguna palanca cerca). Ahora se pueden pegar palancas y botones a las dos
    partes (cara de apoyo completa) y la señal cuenta si llega a la base o a la jaula.
  - El núcleo gira despacio y apagado cuando hay rotación pero falta la redstone (en espera), y rápido y brillante
    cuando está encendido.
  - Los cojinetes del eje son medio píxel más cortos que el eje de Create: antes acababan en el mismo plano y la
    textura parpadeaba.

## 0.7.36
- Arreglo: teletransportarse con un waystone a una nave descargada fallaba cada vez con "The destination is outside
  the world bounds" y solo funcionaba tras reiniciar el juego. Sable guarda la nave descargada en otro "holding
  chunk" pero el `HoldingSubLevel` en memoria conserva el puntero viejo; waystonessable lo usa para `snatchAndLoad`
  y Sable no encuentra la nave. `mixin/SableSnatchPointerMixin` (HEAD de `SubLevelHoldingChunkMap.snatchAndLoad`):
  si la nave no está en el chunk del puntero, la busca en los holding chunks cargados y, si no, en su tracking
  point, y repite la llamada con el puntero bueno.

## 0.7.37 — Motor de distorsión y consola de navegación
- **Consola de navegación** (2x2, mesa con un sistema solar holográfico animado). Clic derecho: mapa del sistema solar
  a pantalla completa con las posiciones reales de Cosmonautics. Rueda = zoom hacia el ratón, arrastrar = mover,
  ratón encima = ficha del astro (tipo, descripción, gravedad y radio calculados, aire, temperatura, peligros,
  armadura recomendada, recursos, distancia). Clic derecho en un astro: "Viajar (orbitar)" / "Leer descripción".
  Botones "Sol" y "Mi posición". Barra de estado con el estado del motor.
- **Motor de distorsión** (4x4, 3 de alto, modelo 3D dibujado por BER a escala x2). Dos entradas de eje (segunda fila,
  a los lados) que consumen 64 SU/RPM cada una; carga completa en 60 s con una entrada a 256 RPM (30 s con las dos).
  Tanque de 32 000 mB solo de nitrógeno líquido (`c:nitrogen`, por tubería en cualquier bloque o con cubeta):
  cada salto gasta 4000 mB y después hay que enfriarlo con otros 8000 mB (60 s); sin nitrógeno no se enfría.
- El salto solo funciona en el espacio profundo, con el motor montado en una nave; la consola usa el motor de la
  misma nave. La instancia entera pasa a una órbita circular ecuatorial a max(200 km, 0,3 radios) del planeta
  (limitada al 60 % de su zona de influencia). No se puede orbitar el Sol.
- Efecto para todos los de la instancia (8 s): estrellas que se estiran, destello blanco/violeta, túnel (el cambio de
  órbita ocurre a los 4 s) y el nombre del planeta con la altura de la órbita.
- Recetas: motor en la mesa mecánica de Create (5x5: netherita, estrellas del Nether, mecanismos de precisión,
  tanques, láminas de titanio y un cristal del End); consola 3x3 con la mesa de hologramas de Cosmonautics.
- Código: `warp/` (Footprint, WarpAnchorBlock, WarpPartBlock, WarpDriveInputBlock, WarpDriveBlockEntity, payloads)
  y `warp/client/` (NavMapScreen, WarpEffectOverlay, renderizadores). Modelos: `tools/designs/jump_drive_model.py --export`.

## 0.7.38
- Arreglo: al saltar con el motor de distorsión el jugador acababa fuera de la nave. Los planetas de Cosmonautics son
  cubos (radio = medio lado) y la órbita de llegada (radio R + altura) pasaba por dentro del cubo en las diagonales:
  Cosmonautics bajaba la nave al planeta en el acto (en el log: salto a la Luna y, al momento, cambio de dimensión
  a `rocketnautics:moon`). Ahora el radio libra las esquinas: medio lado · √2 · 1,02 + max(200 km, 0,3 R)
  (o R + altura si Cosmonautics usa planetas esféricos), limitado al 90 % de la zona de influencia.
- Arreglo del parpadeo del anillo: 16 segmentos de latón y 16 violetas girados a lo largo de la circunferencia
  (giros de 22,5° en z) que ya no se solapan; el núcleo tenía dos cajas con caras en el mismo plano.
- Los 4 tanques ahora son de cristal escarchado con el nitrógeno dentro, que sube y baja con el nivel del motor
  (partes sueltas `warp_drive_glass` y `warp_drive_fluid`, translúcidas; el nivel se suaviza en el cliente).

## 0.7.39
- El juego se colgó al salir de la Luna. Causa: el salto fallido de 0.7.37 dejó la nave en la Luna en x = -24 975 514
  (fuera de la cara del planeta, que mide ±750 000). Al subir al espacio, el jugador quedó sentado en un asiento a
  millones de bloques de él (la caja jugador+asiento medía 2,5e7 bloques) y el juego se quedó congelado.
- `ShipTransferGuard`: antes de volver a sentar al jugador tras cruzar de dimensión, si está a más de 16 bloques del
  asiento se le lleva primero junto al asiento y se le sienta al tick siguiente.
- Para rescatar el mundo "test 9": en la Luna, `/sable teleport @n 0 18900 0` (nave más cercana) y `/tp @s 0 18905 0`.

## 0.7.40
- Holograma de la consola como el de la mesa de hologramas de Cosmonautics (planetas texturizados con su render
  `DeepSpaceHandler.renderHoloPlanet`), pero fijo: siempre el sistema solar entero, sin nombres, cada astro en su
  posición real y su órbita en puntos (`RenderType.lightning`). Distancias en raíz cuadrada; lunas a distancia fija
  de su planeta. Si falta la textura de un planeta se pide con `PlanetRenderRequestPayload` (cada 10 s como mucho) y
  mientras tanto se dibuja un cubito de su color. Sin datos del universo, la animación de antes.
- Mapa de la consola: dibuja la órbita de la nave (órbita kepleriana de Cosmonautics muestreada en un periodo, o
  hacia delante si es de escape), alrededor del astro que la controla y con la misma escala que el marcador "Tú".

## 0.7.41
- **Logros**: pestaña "Planetary Expansion" (fondo de estrellas) con 23 logros (vista previa aprobada; "Space Race" y
  "One Small Step" son ideas del usuario). Los de llegar a planetas, armaduras, construir y diamante son solo datos;
  los de tormentas (`StormManager.stormSurvived`), minar en otro planeta, generador de gravedad, amortiguador y saltos
  usan `minecraft:impossible` y los da `advancement/Achievements`. "Interplanetary Commuter" = 5 de 8 destinos
  (requisitos en forma CNF: cada grupo de 4 destinos debe tener uno visitado).
- **Camas fuera de la Tierra** (`mixin/BedBlockMixin` + `weather/SpaceBeds`): en las dimensiones `rocketnautics:*` y
  `planetary_expansion:*` no explotan y te puedes acostar a cualquier hora. En los planetas guardan la reaparición;
  en el espacio profundo solo te acuestas (dimensión no natural). La noche no se salta: la hora de cada planeta la
  marca su Sol real en Cosmonautics.

## 0.7.42 — Nitrógeno líquido en el mundo helado y en Thalassa
- `worldgen/NitrogenPocketFeature` ("nitrogen_pocket"): elipsoide hueco, la parte de abajo con nitrógeno líquido de
  Create Nuclear (`createnuclear:nitrogen`), el resto aire y borde de hielo azul donde el líquido toca la roca. Solo
  se pone si el hueco y su borde son roca maciza (no se abre a cuevas, agua ni roca madre). Tamaños variados:
  pequeñas (radio 2,5–4,5) y grandes (6,5–11), con probabilidad de grande configurable.
- Mundo helado: comunes (2 intentos por chunk, y -48 a 48, 30 % grandes). Thalassa: poco comunes (1 de cada 6
  chunks, y -290 a -80, 35 % grandes).
- Mundo helado sin lava: fuera los lagos de lava subterráneos y los manantiales de lava; los huecos profundos que
  Minecraft llenaba de lava (por debajo de y -54 y acuíferos de lava) quedan vacíos (`IceWorldFluids.NoLava` en
  `ThalassaFluidPickerMixin` + `IceWorldAquiferMixin`). Solo afecta a chunks nuevos.

## 0.7.43
- Sin la lluvia de la Tierra en los planetas (`mixin/PlanetWeatherMixin`): en Minecraft todas las dimensiones que no
  son el Overworld comparten su clima, así que cuando llovía en la Tierra nevaba en el mundo helado y en todos los
  planetas el cielo se nublaba y se perdían las estrellas (visto en "test 15": `raining=1` en level.dat).
  `Level.getRainLevel`/`getThunderLevel` = 0 en `planetary_expansion:*` y `rocketnautics:*`; el clima propio de
  cada planeta sigue siendo el de `StormManager`.
- Confirmado por el usuario: las bolsas de nitrógeno (0.7.42) funcionan.

## 0.7.44 — Estructuras de Marte (construidas por el usuario)
- Origen: el usuario las construye en un mundo plano ("estructuras") y las guarda con bloques de estructura en
  `saves/estructuras/generated/minecraft/structures/`. Se copian a `tools/structures/raw/` y
  `python3 tools/structures/prepare.py` las prepara en `tools/structures/mars/` (quita el aire por encima de la losa de
  3 capas de la base para conservar el terreno; deja el aire de dentro de la losa para excavar cráteres; pone
  `LootTable` en los cofres). `build_structures()` en generate.py las registra (jigsaw de una pieza,
  `beard_thin`, `project_start_to_heightmap: WORLD_SURFACE_WG`).
- Meteoritos (3 variantes: titanio, uranio, torio en el núcleo): hundidos 3 bloques; `random_spread` 22/9.
- Restos viejos 1-3: hundidos 4 bloques (la arena los cubre en parte); poco comunes, `random_spread` 36/14.
  Botín: estructura 1 = minerales en bruto al azar; estructura 3 = cubetas vacías, lingotes de hierro, algo de cobre y
  tuberías de fluido de Create. La 2 (plato estrellado) no tiene cofre.
- Biomas: dunas, altiplanos, cañones y cuencas de Marte (no en el casquete polar).
- validate.py ignora los .nbt.

## 0.7.45 — Estructuras de Marte fundidas con el terreno
- En 0.7.44 la losa de arena roja de la base asomaba como una plataforma naranja cuadrada (en laderas y en zonas donde
  el suelo de Marte no es arena roja) y `beard_thin` aplanaba el terreno alrededor en escalones cuadrados.
- `worldgen/TerrainBlendProcessor` (`planetary_expansion:terrain_blend`, lista `mars/blend`): la losa (3 capas de
  arena roja) no se pone nunca (queda el terreno natural); el aire de la losa solo excava bajo la superficie (cráter);
  la arena de encima de la losa toma el bloque de la superficie de esa columna (mismo color que el suelo).
- `terrain_adaptation: none`.

## 0.7.46 — Meteoritos de Marte como cráteres
- Los meteoritos ya no son estructuras (salían muy enterrados o con arena flotando). Ahora son una feature
  (`planetary_expansion:meteorite_crater`, `worldgen/MeteoriteCraterFeature`) que excava un cuenco en el suelo
  (radio 4–8, profundidad ≈0,55×radio, borde algo irregular), igual que los cráteres naturales, y pone el núcleo
  del meteorito (las plantillas `mars/meteorite_1..3` sin la arena roja) medio clavado en el fondo y girado al azar.
- Solo quita terreno: nunca deja bloques flotando. Rareza 1 de cada 420 chunks, en dunas, altiplanos, cañones y
  cuenca (no en el casquete polar). Pesos: meteorito 1 ×2, meteoritos 2 y 3 ×1.
- Se quitaron la estructura `meteorite`, su pool y el structure_set `meteorites`. Las estructuras antiguas no cambian.

## 0.7.47 — Cimientos para las estructuras de Marte
- En los bordes de barrancos y laderas fuertes, las estructuras antiguas quedaban flotando.
- `TerrainBlendProcessor` tiene ahora `foundation` (24 en `mars/blend`) y usa `finalizeProcessing`: bajo cada
  columna de la estructura rellena hasta el suelo (máx. 24 bloques) con el bloque de la superficie de esa columna, y
  alrededor, dentro del contorno de la plantilla, hace un talud que baja 2 bloques por cada bloque de distancia.
- La losa (y < slab) se resuelve ahora en `finalizeProcessing` (hace falta para conocer el contorno); el resultado es
  el mismo que en 0.7.45. Los meteoritos (cráteres de 0.7.46) no cambian.

## 0.7.48 — Estrellas de día en Ignis
- Las estrellas de los planetas son las de Minecraft (las de Cosmonautics solo salen con `enableCustomSky`, que viene
  apagado, o en el espacio profundo), y Minecraft solo las dibuja de noche. Ignis tiene el cielo negro (sin aire), así
  que de día quedaba un cielo negro vacío.
- `mixin/AirlessStarsMixin` (cliente): en los planetas sin atmósfera (por ahora solo Ignis) el brillo de las estrellas
  es siempre como de noche cerrada (0,5). En los planetas con atmósfera no cambia nada.

## 0.7.49 — Cielo personalizado de Cosmonautics
- El usuario activó `enableCustomSky = true` (config de cliente de Cosmonautics, en las dos instancias).
- Con el cielo personalizado, los efectos `rocketnautics:moon` (Ignis y la Luna) se quedan en `SkyType.NONE` y, como
  nuestros planetas tienen `render_universe_in_dimension: false`, el cielo quedaba negro y vacío.
  `mixin/AirlessSkyTypeMixin` (cliente) devuelve `NORMAL` en esas dos dimensiones: estrellas y sol de Minecraft.

## 0.7.50 — Planetas visibles desde Ignis (prueba)
- Ignis: `render_universe_in_dimension: true`. Con `enableCustomSky = true`, Cosmonautics dibuja en su cielo el Sol,
  los planetas y las lunas en su posición real, más sus estrellas y nebulosa (de día también: no hay aire).
- `mixin/SpaceStarsCullMixin` (cliente): `SkyHandler.renderSpaceStars` recortaba las estrellas a un cono alrededor de
  la mirada sin tener en cuenta la rotación del planeta (medio cielo vacío fuera de la Tierra). Fuera del Overworld el
  límite 0.35 pasa a -2 (se dibujan las 1600). En la Tierra no cambia.
- `AirlessSkyTypeMixin` ya no fuerza el cielo de Minecraft en Ignis (solo en la Luna): ahí el cielo lo dibuja
  Cosmonautics, como en su Luna original.

## 0.7.51 — Universo en el cielo de todos los planetas (prueba)
- `render_universe_in_dimension: true` en Marte, mundo helado, Ignis, Thalassa, Venus y el override de la Luna.
- `client/UniverseSky` + `mixin/UniverseSkyMixin` (cliente, MixinExtras `@WrapOperation` en `LevelRenderer.renderSky`):
  en esos planetas se quitan el sol, la luna y las estrellas de Minecraft (los dibuja Cosmonautics; si no, salían
  dos soles).
- `SpaceStarsCullMixin`: en Marte, Thalassa y Venus (con atmósfera) la nebulosa y las estrellas de Cosmonautics se
  multiplican por `starFade()` = brillo de estrellas de Minecraft × 2 (0 de día, 1 de noche cerrada). El Sol y los
  planetas se siguen viendo de día.
- Se quita `AirlessSkyTypeMixin`: la Luna vuelve a su cielo original de Cosmonautics (`NONE` + universo).
- Compilación: stubs de MixinExtras (`WrapOperation`, `Operation`) en `tools/stubs`; los stubs se compilan con el
  classpath y `-processorpath` vacío.

## 0.7.52 — Cielo de día en Marte/Venus/Thalassa y planetas bajo el agua
- Causa del cielo negro y la niebla gris de día en Marte: con `enableCustomSky = true`, el `ClientLevelMixin` de
  Cosmonautics (`darkenSkyAtAltitude`, RETURN de `ClientLevel.getSkyColor`) devuelve negro en toda dimensión cuya hora
  controla el Sol (`shouldOverrideLevelTime`). La niebla se mezcla con ese color y se veía gris (también con shaders).
- `mixin/PlanetSkyColorMixin` (cliente, MixinExtras `@WrapMethod` de `getSkyColor`): en Marte, Thalassa y Venus calcula
  el color como Minecraft (bioma × luz del día + destello de rayos) y lo oscurece por encima de 1000 m como Cosmonautics.
- `mixin/UniverseUnderwaterMixin` (cliente): cancela `DeepSpaceHandler.renderUniverseForLevel` con la cámara en agua,
  lava o nieve polvo, fuera de la Tierra (bajo el mar de Thalassa se veían los planetas).
- Stub de compilación `WrapMethod` en `tools/stubs`.

## 0.7.53 — Noche permanente del mundo helado con el universo
- `mixin/NightSunMixin` (cliente): HEAD cancelable de `DeepSpaceHandler.renderPlanet` → no se dibuja el Sol (`sol`)
  en `planetary_expansion:ice_world`. La dimensión sigue con `fixed_time 18000` (aurora de Complementary); los demás
  planetas, lunas y estrellas del universo se siguen viendo y moviendo.

## 0.7.54 — Leviatán de Thalassa (ambiente)
- `client/ThalassaLeviathan` (solo cliente, registrado en el bus de NeoForge): serpiente marina de ~90 bloques
  (40 segmentos de cajas, cresta, aletas pectorales y de cola) que a veces pasa a lo lejos bajo el agua de Thalassa.
  Cuerpo = color de la niebla × 0,35 con transparencia según la distancia; luces turquesa aditivas con pulso lento.
- Aparece con los ojos en el agua y por debajo de y 40, una vez cada 20–40 min de buceo, a 55–120 bloques (según el
  final de la niebla), solo si hay agua en ≥75 % de los puntos del recorrido. Variantes: `cross` (cruza), `below`
  (pasa por debajo), `tail` (la cola se aleja). Si te acercas a menos de ~0,42 × niebla, se desvanece.
- Sonidos: `ambient.underwater.loop.additions.ultra_rare` (tono 0,55) al aparecer y `elder_guardian.ambient` (tono 0,5)
  a mitad del paso.
- Comando de cliente: `/leviathan [cross|below|tail]` (dentro del agua de Thalassa).
- Dibujo en `RenderLevelStageEvent` AFTER_PARTICLES con `position_color`, prueba de profundidad y sin escribir profundidad.

## 0.7.55 — Leviatán: el triple de grande, más detalle y cabeza monstruosa
- ~275 bloques (64 segmentos de 4,3; radio hasta 7), ondulación 14 (lateral) / 5 (vertical), longitud de onda 120,
  velocidad 0,75 bloques/tick. Pasa a 60–130 bloques; por debajo, 45–70 bloques más abajo.
- Detalle: vientre más claro, placas dorsales inclinadas (dos tamaños), costillas-aleta laterales, aletas pectorales
  (segmentos 8 y 30), cola con aleta vertical y horizontal.
- Cabeza (`Head`): hocico, pico ganchudo, mandíbula abierta que se mueve (22° ± 8°), interior de la boca muy oscuro,
  dientes y colmillos arriba y abajo, gancho en la punta de la mandíbula, seis ojos en cuencas, cejas, corona de púas
  hacia atrás, púas en las mejillas y bajo la barbilla.
- Luces más tenues (alpha 0,45, halo ×2,2 al 6 %); el cuerpo y las luces se desvanecen a la misma distancia
  (1,4 × niebla), así que sin shaders ya no quedan luces flotando sin cuerpo.

## 0.7.56 — Leviatán: más lejos, opaco, rugido y temblor
- Distancias fijas (antes dependían de `RenderSystem.getShaderFogEnd()`, que bajo el agua podía ser corta y con shaders
  no coincide con lo que se ve): `cross` a 115–150 bloques; se funde con el agua de 110 a 240 bloques. Huye a < 50.
  Velocidad 1 bloque/tick. El comando prueba más cerca (80–100) si no encuentra sitio lejos.
- Cuerpo opaco y escribiendo profundidad (antes semitransparente: se veían las caras de dentro). La distancia y la
  aparición mezclan el color hacia el del agua en vez de usar transparencia. Colores fijos azul petróleo muy oscuro
  (antes salían del color de la niebla y con shaders quedaban azul vivo).
- Línea de vista: al menos el 60 % de los puntos del recorrido tienen agua entre el jugador y el punto (rayo cada
  5 bloques). La roca escondida por la niebla tapaba partes del cuerpo y se veía "cortado".
- Al aparecer: rugido fuerte sin atenuación (`warden.roar` tono 0,5 + `ender_dragon.growl` tono 0,55) y temblor de
  cámara de 3 s (`ViewportEvent.ComputeCameraAngles`, hasta ~1° que se va calmando).

## 0.7.57 — Leviatán: modo shaders, rugidos repetidos, temblor continuo
- Con shaders (Iris, detectado por reflexión con `IrisApi.isShaderPackInUse()`), el cuerpo ya no se mezcla hacia el
  color del agua de Minecraft (se veía demasiado azul): usa sus colores propios y deja la niebla al shader; solo usa
  transparencia al aparecer y al huir. Sin shaders sigue igual que en 0.7.56.
- Ruge varias veces mientras pasa (al aparecer y luego cada 8–14 s, salvo cuando huye). Cada rugido es más fuerte:
  cuatro capas a la vez en la categoría MASTER y sin atenuación (`warden.roar` tonos 0,5 y 0,43,
  `ender_dragon.growl` 0,55, `ravager.roar` 0,5), con un golpe de temblor de 1,2°.
- Temblor suave continuo (0,3°) todo el tiempo que está pasando, que baja al aparecer y al huir.

## 0.7.58 — Sin cantos "de ballena" en Thalassa
- `client/ThalassaAmbience` (`PlaySoundEvent`): en Thalassa se cancelan `ambient.underwater.loop.additions.rare` y
  `.ultra_rare` cuando vienen del ambiente de Minecraft (`UnderwaterAmbientSoundInstances.SubSound`). Se quedan el
  murmullo del mar, las burbujas y el canto con el que aparece el leviatán (es un `SimpleSoundInstance`).

## 0.7.59 — Sin cantos "de ballena" en ningún planeta
- `ThalassaAmbience` cancela `ambient.underwater.loop.additions.rare` / `.ultra_rare` del ambiente de Minecraft en
  todas las dimensiones `planetary_expansion:*` y `rocketnautics:*` (antes solo en Thalassa). La Tierra no cambia.

## 0.7.60 — Leviatán más fuerte
- Cada rugido son ahora 8 capas (antes 4), algunas con 1–3 ticks de retraso: `warden.roar` ×3 (tonos 0,5 / 0,43 /
  0,47), `ender_dragon.growl` ×2 (0,55 / 0,5), `ravager.roar` ×2 (0,5 / 0,45) y `elder_guardian.curse` (0,5). Todas en
  MASTER y sin atenuación (un solo sonido no pasa de volumen 1).
- El canto al aparecer (`ultra_rare` ×2) y el gruñido de mitad de paso (`elder_guardian.ambient` ×2) también suenan sin
  atenuación por distancia (antes bajaban con la distancia).

## 0.7.61 — Criaturas de Thalassa
- Paquete `creatures`: pez farol (`LanternFish`, banco, cubeta), cangrejo de chimenea (`VentCrab`, neutral, defiende
  su chimenea; sale al generarse cada chimenea), cangrejo rey azul (`KingCrab`, territorial, frente blindado al 60 %),
  acechador abisal (`AbyssalStalker`, ciego, caza por ruido como el Warden; por debajo de y −150) y el arpón lanzado.
- Modelos: `tools/designs/thalassa_mobs.py` genera textura, textura de brillo, `.bbmodel` y el modelo para el juego
  (`assets/planetary_expansion/mob_models/*.json`, lo lee `creatures/client/JsonMobModel`). Objetos:
  `tools/designs/thalassa_items.py`.
- Objetos: pez farol (crudo/cocinado con visión nocturna), gel luminoso, lámpara de gel, cubeta con pez farol,
  carne de cangrejo, quitina volcánica (refuerza la Sea Diver Armor en la mesa de herrería: +1,5 de dureza y
  +5 % de resistencia al empuje por pieza), patas de cangrejo rey, caparazón de rey azul, escudo de caparazón
  (durabilidad 1008), pinza del rey (en la mano secundaria: +2,5 de alcance de bloques y +1 de entidades),
  colmillo abisal, arpón, órgano sonar, sonar (marca con brillo a las criaturas a 48 bloques), barbilla luminosa y
  lámpara abisal. Huevos generadores.
- Spawns en los biomas de Thalassa; `no_earth_mobs` ya no borra las criaturas propias (`neoforge:not`).

## 0.7.62 — Iconos de los objetos de Thalassa mejorados
- `thalassa_items.py`: `autoshade()` (borde de arriba/izquierda claro, de abajo/derecha oscuro y contorno del color de
  cada zona, como los iconos de Minecraft) y dibujos más detallados: escamas, aletas y señuelo del pez; burbujas del gel;
  fibras de la carne; crestas y lava de la quitina; púas y parte de abajo de las patas; pinza abierta; encía del
  colmillo; anillas y púas del arpón; venas del órgano; pantalla con barrido y ecos del sonar; lámparas con marco.
- Escudo de caparazón: placa abombada con degradado, púas con punta oscura, borde con luz y sombra, reverso más oscuro.

## 0.7.63 — Pinzas de los cangrejos corregidas
- Las pinzas del cangrejo rey apuntaban hacia adentro y se cruzaban bajo el frente (parecían al revés); las del cangrejo de
  chimenea quedaban separadas del cuerpo.
- `thalassa_mobs.py`: nuevas `seg()` (segmento orientado en cualquier dirección, con ángulos de Euler calculados) y
  `crab_claw()` (brazo hacia afuera y adelante → antebrazo hacia adelante → mano hacia adelante y un poco al centro →
  dedo fijo abajo y dedo móvil arriba algo abierto; piezas solapadas para que no haya huecos). Derecha grande, izquierda
  más pequeña (vent ×1 / ×0,75; rey ×1,9 / ×1,45, con articulaciones naranjas). Pivotes de `claw_r`/`claw_l` en el hombro.

## 0.7.64 — Progresión de trajes (traje básico + plantillas por planeta), traje térmico y caracoles de magma
- **Traje espacial básico** (`BasicSuit`, `basic_*`): traje de presión naranja; hace lo mismo que el equipo de Cosmonautics
  (casco espacial, jetpack, propulsores, botas ancla) sin protecciones especiales; material de hierro (durabilidad 22).
  Única armadura que se fabrica en la mesa de trabajo (hierro, láminas de hierro, titanio, vidrio tintado, depósito).
- **Traje térmico** (`ThermalSuit`, `thermal_*`), el de Ignis: -15 % de fuego/lava/magma por pieza, inmune con el traje
  completo (y se apaga al salir de la lava); el casco deja ver 7 bloques dentro de la lava (12 con el traje completo,
  `client/ThermalLavaVision`); el traje completo reduce a la mitad el calor de Venus (antes la Impact).
- **Impact**: ya no protege del fuego ni de la lava; ahora -10 % de daño de explosiones y proyectiles por pieza.
- Piezas genéricas en `armor/SuitPieces` (casco/jetpack/propulsores/botas con `Info`). Diseños: `tools/designs/new_suits.py`
  (parten de `armor_v1/glacial_armor`, textura repintada por zonas) → `basic_armor.bbmodel`, `thermal_armor.bbmodel`.
- **Plantillas** (`SuitUpgrades`, `SmithingTemplateItem`): `impact/glacial/hazard/sea_diver/thermal_upgrade_template`.
  Primera: 8 materiales del planeta (o 4+4) alrededor de una lámina de aleación de titanio. Duplicar: plantilla + 7
  diamantes + 1 material → 2. Herrería: plantilla + pieza básica + lingote de netherita. Se quitaron las recetas directas.
- **Materiales** (modificadores `neoforge:add_table` con `loot_table_id` + `location_check` de dimensión):
  fragmento de meteorito (60 % del basalto lunar envejecido de los meteoritos de Marte), cristal criogénico (6 % del hielo
  azul/compacto del mundo helado), azufre venusiano (5 % del basalto/basalto liso/magma de Venus). Fortuna suma.
- **Caracoles de magma** (`IgnisCreatures`, `MagmaSnail`, `MagmaTitan`; modelos `tools/designs/ignis_mobs.py`):
  caracol pasivo, inmune al fuego, se desliza sobre la lava, se esconde 5 s al recibir un golpe (25 % del daño);
  titán (90 de vida) que escupe ráfagas de 3 bolas de fuego si lo atacas. Spawn en Ignis solo con mucha lava cerca
  (12+ / 40+ bloques en 11×5×11). Botín: concha ígnea, núcleo de magma (combustible sobrecalentado del blaze burner de
  Create y de horno), baba de magma (lámpara de magma), oro en bruto (titán).
- Iconos: `tools/designs/suit_items.py`. El logro "Moda para cada mundo" pide ahora los cinco trajes.

## 0.7.65 — Los cangrejos caminan de verdad
- Antes todas las patas eran un solo grupo (`legs`) que apenas se balanceaba, y con lo poco que se mueven bajo el agua
  casi no se notaba.
- `thalassa_mobs.py`: cada pata es su propio grupo (`leg_l0..2`, `leg_r0..2`; `mirror_x` cambia `leg_l` → `leg_r`) con el
  pivote en la cadera (chimenea ±8/8/z+1; rey ±13/18/z). La geometría quieta es idéntica (comprobado).
- `CreaturesClient.crab`: dos grupos alternos de patas (par de un lado con impar del otro): las que avanzan se
  levantan, las otras empujan hacia atrás; el cuerpo sube y baja con cada paso. Más sensible al movimiento (×4) y más
  rápido (×1,6). `JsonMobModel` registra los grupos `leg_l*`/`leg_r*`.

## 0.7.66 — Kits de supervivencia y más materiales (para que la primera expedición sea justa)
- Problema (señalado por el usuario): para conseguir el traje de un planeta hay que sobrevivir allí sin él.
- **Kits** (`armor/SurvivalKits`): consumibles de la Tierra (pila de 16) que dan un efecto de 3 min. Con el efecto, el
  peligro llega al 40 % (−60 %). No dan inmunidad a propósito (pedido del usuario), y se suman a la armadura.
  - Gel refrigerante (efecto Refrigerado) → calor de Venus (`VenusHazards`). Receta: 2 hielo compacto + bola de slime + 2 frascos → 2.
  - Calentador químico (Abrigado) → frío de Thalassa y ventiscas (`GlacialArmor.coldFactor`). 2 pepitas de hierro + carbón + 2 papel → 2.
  - Filtro de polvo (Filtrado) → tormentas de Marte (`ImpactArmor.dustStormFactor`). Lana + carbón vegetal + hilo + pepita de hierro → 2.
- **Más probabilidad:** cristal criogénico 6 % → 10 % y azufre venusiano 5 % → 10 % (el fragmento de meteorito sigue al 60 %).
- Iconos de los kits y de los efectos en `tools/designs/suit_items.py`.

## 0.7.67 — Camas en el espacio: también guardan el punto de reaparición
- Pedido del usuario: en planetas y espacio, las camas no explotan, te dejan acostarte y guardan la reaparición, pero
  no pasan la noche. Casi todo existía desde 0.7.41 (`BedBlockMixin`, `SpaceBeds`); faltaba la reaparición en el
  espacio profundo (Cosmonautics lo marca "no natural" y Minecraft corta `startSleepInBed` antes de guardarla).
- `mixin/SpaceBedNaturalMixin` (`@WrapOperation` de `DimensionType.natural()` en `ServerPlayer.startSleepInBed`,
  `require = 0`): en el espacio y los planetas cuenta como natural solo ahí; así Minecraft hace las comprobaciones
  normales (distancia, cama tapada) y guarda la cama. Respaldo en `SpaceBeds.onCanSleep` si el mixin no se aplicara.
- `SpaceBeds.onSleepFinished`: dormir fuera de la Tierra nunca adelanta la hora.
- En naves: Sable 2.0.6 ya trae camas en sub-levels (distancia en coordenadas de la nave, acostarse, y la reaparición
  sigue a la nave con un punto de seguimiento).

## 0.7.68 — Logo del mod
- `tools/designs/logo.py` → `tools/designs/logo.png` (pixel art 64×64 escalado a 512): Marte con casquete, Thalassa,
  Venus, Ignis y el mundo helado en órbita, nave con estela. `build_meta` lo copia a `logo.png` en la raíz del jar.
- `neoforge.mods.toml`: `logoFile="logo.png"`, `logoBlur=false` (pixel art nítido), `displayURL` al repositorio de GitHub
  y descripción actualizada (cinco planetas, trajes con plantillas, kits, criaturas y máquinas de nave).
- Código fuente publicado en https://github.com/Antme08s/planetary_expansion (GPL-3.0).

## 1.0.0 — Primera versión pública (CurseForge, como Beta)
- Mismo contenido que 0.7.68; solo cambia el número de versión (`MOD_VERSION = "1.0.0"`).

## 1.0.1 — Correcciones: caracol titán, frío en el agua de Thalassa y casco Sea Diver
- **Caracol titán:** los racimos de oro de la concha flotaban (estaban a una altura fija por encima de la espiral y uno
  quedaba fuera de ella). `ignis_mobs.py` calcula ahora la altura real de la concha en cada punto (`shell_surface`,
  buscando de arriba abajo dentro de las cajas de la espiral) y hunde la base dorada y los cristales en ella,
  inclinados según la pendiente. Modelo regenerado en `tools/designs/mobs/magma_titan.*`.
- **Frío en Thalassa:** meterse al agua ya no quita el frío. `PlanetCold` sigue enfriando en el agua (está helada)
  al mismo ritmo que a la intemperie, aunque estés bajo techo; durante una ventisca, a quien está a la intemperie lo
  sigue congelando la ventisca (no se suman). Las armaduras glacial / Sea Diver y el calentador químico lo reducen igual.
- **Casco Sea Diver:** fuera las dos barritas doradas que cruzaban el visor y las dos válvulas grises de debajo
  (`armor_concepts.py`: sin la rejilla y `remove((5, 73))`; `sea_diver_armor.bbmodel` regenerado; las otras
  armaduras salen idénticas).

## 1.0.2 — Los caracoles de magma ya no se ahogan en Ignis
- Ignis no tiene aire: Cosmonautics (`LivingBreatheEvent`, prioridad HIGHEST, bandera `drowning`) deja sin respirar
  a toda criatura, y los caracoles se ahogaban a los pocos segundos (el log no lo muestra: las muertes de mobs sin
  nombre no se registran). `IgnisCreatures.breathe` (prioridad HIGH) les devuelve el aire al caracol y al titán,
  salvo con los ojos bajo el agua. Son las únicas criaturas del mod que viven en un planeta sin aire.

## 1.0.3 — (en desarrollo: todo lo nuevo se junta en esta versión)
### Segundo sistema solar: prueba mínima
- **Estrella Ámbar** (`universe_planets/amber`): enana naranja que cuelga de la raíz del universo (`"parent": "root"`),
  posición fija en x = 1,5e12 m (~94 veces la distancia del mundo helado), 70 % de la masa del Sol, radio 2,2e8 m,
  textura naranja lisa (`tools/designs/amber_star.png`). Con dos estrellas fijas, a la nave la controla la más cercana.
- **Planeta de prueba** (`amber_test`): generación de la Tierra (ruido y biomas de Minecraft, `preset overworld`), aire
  hasta 5000 m, gravedad y arrastre de la Tierra, año de 604 800 s alrededor de Ámbar, luz y día de Ámbar. Temporal:
  solo para comprobar gravedad, luz, cielo y salto; el planeta de verdad se diseñará después.
- **`warp/StarSystems`**: estrella de cada astro (la que cuelga de la raíz), lista de estrellas.
- **Mapa de la consola**: solo dibuja el sistema donde estás; botón "Sistema: X" (si hay más de una estrella) para ver
  el otro; el botón "Sol" ahora es "Estrella". **Holograma**: solo el sistema donde está la consola.
- **Motor de distorsión**: no orbita ninguna estrella (antes solo el Sol). De momento el motor normal puede saltar al
  otro sistema; el motor mejorado vendrá después.
- `UniverseSky`: el planeta de prueba dibuja el universo y tiene cielo con atmósfera. `validate.py` acepta `preset`.
- **Prueba en el juego (usuario):** el salto a `amber_test` y el regreso funcionan.
### Sonido en el espacio profundo
- Al entrar al mundo con la nave en el espacio profundo no se oía nada. Causa: Cosmonautics (`SkyHandler.onPlaySound`)
  silencia todo menos el viento con la cámara entre y = 1000 y 2000 (capa de viento de la Tierra), también en el
  espacio profundo, donde las naves quedan a esa altura (~1730). `client/DeepSpaceSound` (prioridad LOWEST) devuelve el
  sonido original solo en el espacio profundo y solo en esa franja.
- `HologramLabels`: los astros de otro sistema ya no salen como "(fuera de escala)" (se amontonaban).

### Color de Ámbar
- Cosmonautics dibuja todas las estrellas igual (textura de plasma propia y resplandor, vértices en blanco) sin mirar
  su textura. `mixin/StarTintMixin` (`@ModifyArgs` en `DeepSpaceHandler.renderPlanet` → `renderCubeOrSphere`, args 5–7 =
  RGB) tiñe Ámbar (1,0 / 0,52 / 0,22). El Sol no cambia. Con shaders el resplandor lo dibuja Complementary.
### Planeta de Ámbar: concepto
- Decisiones del usuario: alienígena con especies propias, colores verdes y azules; biomas: pradera de pasto alto,
  pradera de pasto bajo, bosque gigante, bosque de árboles pequeños, pantano, océano y ríos.
- Vista previa de concepto: `tools/designs/amber_planet_preview.py` (isométrico de cada bioma con la paleta).

### Cerúlea en el juego (primera versión)
- Sustituye al planeta de prueba `amber_test` (dimensión `planetary_expansion:cerulea`, órbita de Ámbar, mismo año y
  radio). Relieve: enrutador de `large_biomes` clonado (`RouterCloner`), mar a y = 63, acuíferos, piedra cerúlea por
  defecto y piedra profunda por debajo de y = 0–8 (gradiente, como la pizarra profunda).
- **Biomas (8):** pradera de pasto alto, pradera de pasto bajo, bosque gigante, bosque violeta (árboles pequeños),
  pantano, río, costa y océano. Reparto como el de la Tierra (continentalidad → mar/costa; valles → ríos; humedad,
  erosión y temperatura → tipo de tierra). Cielo azul con niebla dorada.
- **Bloques y objetos** (`cerulea/CeruleaBlocks`, `CeruleaPlants`): pasto (dos), tierra, lodo, arena pálida, piedra,
  roca, ladrillos, piedra pulida, piedra profunda; 10 minerales × 2 capas (los de Minecraft, zinc de Create y cristal
  cerúleo, que da cristales); tres maderas (tronco, tablones, hojas, retoño) y arbusto; pasto bajo y alto, flor azul,
  flor luminosa (luz 9), hongo luminoso (luz 11), liana colgante (trepable), nenúfar azul; comestibles: arbusto de
  bayas azules (se cosecha con clic derecho), fruta violeta (cuelga de las hojas del árbol violeta y vuelve a salir),
  raíz cerúlea (cultivo de 4 etapas; también crece despacio en pasto), junco del pantano (vainas), raíz asada y sopa
  de hongo luminoso (visión nocturna 60 s).
- **Árboles:** violeta con `minecraft:tree` (tronco recto, copa redonda, fruta con `attached_to_leaves`); plateado
  gigante y del pantano con `cerulea/CeruleaTreeFeature` (tronco 3×3 con copa en tres pisos; raíces en arco y lianas).
  Las hojas quedan con su distancia al tronco, así se caen si se tala.
- **Etiquetas:** el pasto, la tierra y el lodo están en `#minecraft:dirt` (plantas, árboles y retoños de Minecraft
  funcionan); la azada los convierte en tierra arada; maderas en `logs`/`planks` (todas las recetas de madera); roca en
  `stone_crafting_materials`/`stone_tool_materials`; menas en sus etiquetas de Minecraft y `c:ores/*`.
- Texturas: `tools/designs/cerulea_assets.py` → `tools/designs/cerulea/{block,item}` (generate.py las copia).
- Sin criaturas todavía (en los biomas de Cerúlea no sale ningún mob).
### Escaleras, losas, puertas y trampillas de Cerúlea
- Escaleras y losas de piedra, roca, ladrillos, piedra pulida, piedra profunda y las tres maderas (`CeruleaBlocks.stairsAndSlab`;
  `Stairs` porque el constructor de `StairBlock` es protegido). Recetas normales y de cortapiedras; la losa doble suelta 2.
- Puertas (`DoubleHighBlockItem`) y trampillas de las tres maderas con `BlockSetType` de abedul, roble oscuro y mangle
  (sonidos y redstone como las de madera). Modelos de Minecraft (`door_*`, `template_orientable_trapdoor_*`) y
  estados generados en `generate.py` con las mismas rotaciones que los de Minecraft.
- Texturas en `cerulea_assets.py`: puerta plateada con ventana de cuatro cristales, violeta con ojo de buey y X, del
  pantano con travesaños y rendijas (pintadas, no huecas); iconos dibujados a 16×16 con los mismos detalles; trampillas
  con huecos. Vista previa: `cerulea_building_preview.py`.
- Etiquetas `stairs`, `slabs`, `wooden_*`, `doors`, `trapdoors` y herramientas correctas.
### Arreglos de Cerúlea tras la primera prueba en juego
- **Arbustos flotando:** el arbusto es un bloque de hojas (sobrevive en el aire) y el `random_patch` usa `y_spread` 3;
  ahora el parche exige `#minecraft:dirt` debajo (`ON_GROUND`).
- **Árboles encima de otros árboles:** el heightmap `OCEAN_FLOOR` cuenta las hojas y `minecraft:tree` no revisa el suelo.
  Los árboles de pradera, arboleda y bosque gigante llevan un `block_predicate_filter` con `#minecraft:dirt` debajo (los
  del pantano, también arena). Solo afecta a chunks nuevos.
- **Flores con pétalos separados:** `amber_textures.flower` dibuja la cabeza en forma polar (cinco pétalos unidos al
  centro, nervio y sombra entre pétalos) y las hojas pegadas al tallo; con mods que dan volumen a los sprites ya no hay
  píxeles sueltos.
- **Hojas sin huecos:** `cerulea_assets.fill_holes` rellena los pocos píxeles transparentes de las hojas y del arbusto.
### Criaturas de Cerúlea
- **Java:** `cerulea/mobs/` — `CeruleaMobs` (registro, spawn, pestaña, poción de salto con la pata de saltarín),
  `Grazer`, `Hopper`, `Glider`, `Lurker`, `Croaker`, `Ribbonfin`, `Runner`, `FangArrow`/`FangArrowItem`, `Predators`
  (miedo a la luz); cliente en `cerulea/mobs/client/CeruleaMobsClient` (usa `CreatureRenderer` y `JsonMobModel`, que
  ahora carga todos los grupos del JSON).
- **Pastoreador:** manadas en las praderas; pasta (pasto cerúleo → tierra), estampida si atacas a uno o ve corredores,
  se cría con raíz cerúlea; antenas que brillan de noche.
- **Saltarín:** a saltos; huye si corres o te acercas sin agacharte; bayas azules para atraerlo y criarlo.
- **Planeador:** en las copas del bosque gigante; planea de árbol en árbol (busca hojas más bajas a 8–24 bloques),
  trepa troncos como las arañas (`onClimbable`), huye planeando; sin daño por caída; membranas plegadas/abiertas.
- **Acechador:** monstruo nocturno del pantano (raro: nunca dos a menos de 32 bloques ni más de 3 en 96); acecha quieto,
  salta y muerde; teme a la luz; de día desaparece si no hay nadie cerca.
- **Croador:** nenúfares y orillas del pantano; croa de noche con el saco inflado; cubeta.
- **Pez de cinta:** cardúmenes en ríos (más el verde rayado) y mar (más el azul); cubeta que guarda el color.
- **Corredor:** manadas en praderas y bosques; acecha agachado, se queda quieto y salta; de día solo caza animales y al
  jugador que se acerca demasiado le da un golpe y se aleja; de noche caza jugadores; teme a la luz; se doma solo de
  día tirándole carne de pastoreador y alejándose (3–5 veces); domado sigue, se sienta y no suelta nada.
- **Luz:** luz de bloque ≥ 10 o un jugador con un bloque que dé esa luz en la mano.
- **Objetos y recetas:** carnes y pescado (horno, ahumador, fogata), cuero cerúleo y piel rayada → cuero, baba → bola de
  slime, escama → tinte cian, lámpara de bulbo (4 bulbos + vidrio), bloque de escamas luminosas (luz 12), daga de
  colmillo (colmillo + piel rayada; 5 de daño, 2,4 golpes/s), flechas de colmillo ×4 (colmillo + palo + pluma; 3 de
  daño). `#planetary_expansion:fangs` acepta los dos colmillos. Texturas: `tools/designs/cerulea_items.py`.
- Arreglos tras la primera prueba: la punta de la cola del corredor quedaba suelta (estaba bajada en el diseño y además
  girada con la cola; ahora va al final de la cola en su marco sin girar). El saltarín no podía subir bloques (salto de
  0,36): ahora salta normal (1,25 bloques) cuando choca y hace saltitos bajos (0,3) solo al caminar en llano.
- Segunda ronda: el mechón de la cola del pastoreador quedaba suelto (mismo error que la cola del corredor). La
  mandíbula ahora cuelga de la cabeza en `JsonMobModel` (antes era una parte aparte con su propio pivote y se despegaba
  al voltear la cabeza; vale también para el pez farol y el acechador abisal). El corredor sentado ya no flota: el
  cuerpo baja 6 y se inclina, la grupa toca el suelo, las patas de delante se acortan y las de atrás quedan dobladas.
- **Sonidos propios** (el usuario pidió que sonaran más alienígenas que la vaca, el lobo y el conejo):
  `tools/designs/cerulea_sounds.py` los sintetiza (glissandos, formantes que cambian, modulación en anillo, pulsos,
  burbujas, ruido filtrado; nada copiado) en `tools/designs/mobs/sounds_cerulea/<criatura>/<sonido><n>.ogg` más un
  `preview.mp3`. `generate.py` los copia a `sounds/cerulea/` y escribe `sounds.json` con subtítulos;
  `cerulea/mobs/CeruleaSounds` registra los 23 eventos. El pez de cinta sigue con los del salmón.
- Línea clara en el lomo del corredor: con medidas fraccionarias (cuerpo de 6,8 de ancho) dos caras de una caja
  comparten el texel del borde y ganaba la última pintada (el vientre). `cerulea_mobs.paint` pinta ahora primero cada
  texel con la cara que contiene su centro y luego rellena solo los vacíos. Afecta a las texturas de todas las criaturas
  de Cerúlea (solo en los bordes).
- Planeador en el suelo: no tenía patas (el cuerpo quedaba a 4 px del suelo, "flotando") ni animación de caminar.
  Ahora tiene cuatro patas cortas que llegan al suelo y caminan en diagonal como un lagarto; al planear se recogen.
- Pez de cinta: quitadas las cintas sueltas de atrás y de abajo; la aleta del lomo baja hasta la cola y la del vientre
  va pegada al cuerpo.
### Plantas del mar de Cerúlea
- Texturas: `tools/designs/cerulea_sea_plants.py` (segunda versión con más detalle; escribe en `tools/designs/cerulea/{block,item}`
  y una vista previa 3D bajo el agua). Java: `cerulea/CeruleaSea` (bloques, objetos, pestaña y `SeaPlantsFeature`).
- **Alga de cinta** (`RibbonKelp`/`RibbonKelpPlant`, subclases del kelp): crece en columna; suelta alga de cinta;
  horno/ahumador/fogata → alga de cinta seca (comida rápida); 9 → bloque de alga de cinta seca (combustible 4001,
  data map `neoforge:furnace_fuels`, ahora con `datamap_add` que no borra lo anterior).
- **Alga de perlas** (idea del usuario, "como las bayas luminosas pero marinas"): kelp con `berries`; luz 14 con
  perlas; clic derecho cosecha 1–2 perlas luminosas; vuelven a salir solas (tick aleatorio 1/10). Las perlas se comen,
  se plantan (`ItemNameBlockItem`) y 2 → saco de tinta luminosa. El tallo no suelta nada.
- **Pasto marino cerúleo** bajo y alto: solo con tijeras (el alto da 2); harina de hueso hace el alto.
- **Tubos luminosos** (luz 10) y **abanico azul**: `SeaDecor`, dentro o fuera del agua, sueltan el propio bloque.
- **Generación** (`planetary_expansion:cerulea_sea_plants`, config `kind`/`tries`/`radius`; fondo con el heightmap
  OCEAN_FLOOR, solo en agua): mar con praderas de pasto, algas de cinta (2–8 si hay hondura), algas de perlas (35 % de
  trozos con perlas), tubos y abanicos; ríos con pasto y algún tubo. Solo en chunks nuevos.
- Compostables (data map) para todas.
### Ironjaw
- Idea del usuario: "como el dunkleosteus pero más alienígena". Modelo en `cerulea_mobs.py` (`ironjaw(open_)`,
  `python3 -I tools/designs/cerulea_mobs.py tools/designs/mobs ironjaw` → modelo, textura, brillo y vista previa):
  coraza de placas con grietas que brillan, dos pares de ojos, boca de cuatro pétalos en punta (hueso por fuera, carne
  con filas de dientes por dentro) y garganta en túnel con tres anillos de dientes; bisagras de los pétalos en el borde
  de la garganta (a pedido del usuario, un pétalo parecía suelto).
- `cerulea/mobs/Ironjaw` (Monster acuático, como el acechador abisal): lo atrae el ruido (`AbyssalStalker.noisy`,
  32 bloques) y te persigue si estás en el agua a menos de 12; embiste (se aleja, apunta, se lanza a ~0,9 bloques/tick
  con la boca abierta, muerde y sigue de largo); caza peces de cinta; la coraza de delante deja pasar el 40 % del daño.
  Spawn: mar de Cerúlea con 12+ bloques de agua encima, muy raro (1/6 y ninguno a menos de 80).
- Sonidos propios (`ironjaw.ambient/hurt/death/charge/bite`). Botín: placa de coraza 1–3 y cuchilla de mandíbula (15 %),
  todavía sin receta.
### Motor de distorsión estelar
- Idea acordada con el usuario: un **núcleo de distorsión estelar** hecho con un objeto de cada astro del sistema solar;
  se pone con **clic derecho sobre el motor ya montado** (no hay que desmontarlo). Si se rompe el motor, suelta el núcleo.
- Receta (mesa de crafteo): `HNM / VSC / KNI` → cristal de helio-3 (Luna), lingote de netherita ×2 (Tierra), fragmento
  de meteorito (Marte), azufre venusiano (Venus), estrella del Nether (Tierra, en el centro), cristal criogénico (mundo
  helado), caparazón de rey azul (Thalassa) y núcleo de magma (Ignis).
- **Cristal de helio-3**: ruedas trituradoras de Create con regolito lunar suelto (15 %, más arena 50 % y pepita de
  experiencia 25 %). Cosmonautics ya tritura el regolito normal y el fragmentado (regolito → fragmentado → suelto), así
  que se usa el suelto, que no tenía receta.
- Reglas (`WarpDriveBlockEntity.interstellarProblem`, la usan el servidor y la consola):
  - motor normal: solo salta dentro de su sistema; **siempre puede volver al Sol** (para que nadie se quede atrapado),
    con 12 000 mB y el 100 % de carga;
  - motor estelar: la carga llega al **200 %**; dentro del sistema igual que antes (4000 mB, gasta 100 %); a otra
    estrella gasta **12 000 mB y el 200 %** y el salto dura el doble (320 ticks, la órbita cambia en el 160).
- Aspecto (`jump_drive_model.py --stellar <png>` para la vista previa; `--export` saca `warp_drive_frame_stellar`,
  `warp_drive_core_stellar`, `warp_drive_shards` con la textura `warp_drive_stellar`): franjas violetas, núcleo
  blanco-violeta más pequeño y 6 esquirlas que giran en el plano del anillo al revés que el núcleo. Texturas de los
  objetos: `tools/designs/stellar_items.py`.
- Efecto del salto interestelar (`WarpEffectOverlay`, etapa `START_STELLAR` del `WarpEffectPayload`): todo a la mitad
  de velocidad, túnel blanco y violeta con estrellas que pasan de largo, al llegar se enciende la estrella del sistema
  nuevo y sale "Sistema X" encima del planeta; sonidos más graves.
- Consola: "Salto interestelar (orbitar)" en el menú cuando el destino es de otro sistema, con el motivo si no se
  puede; la barra de estado dice "Motor estelar". Goggles: nombre "Motor de distorsión estelar" y aviso de sobrecarga.
- Logro **Más allá del Sol** (desafío, 500 XP): saltar a otro sistema solar.
### Velo, luna de cristal de Cerúlea
- El usuario pidió dos lunas para Cerúlea; de cuatro propuestas eligió **Velo** (cristal) y **Ferra** (óxido, pendiente).
  Pidió que Velo fuera "prácticamente vacía, solo polvo blanco y cristales celestes" y una lámpara de cristal.
- Texturas y vistas previas: `tools/designs/velo_textures.py <salida>` (hoja de bloques y biomas en isométrico con
  Cerúlea en el cielo); `--export tools/designs/velo` escribe las del juego en `velo/block`.
- Java `velo/VeloBlocks`: polvo de Velo (cae), roca de Velo, ladrillos y pulida (+ escaleras y losas), bloque de cristal
  cerúleo, cristal brotante (`BuddingCrystalBlock`, crece como la amatista: brote pequeño → mediano → grande → racimo, con
  luz 2/4/6/8), lámpara de cristal (`RedstoneLampBlock`, luz 15 encendida), menas de hierro, oro, diamante, cristal
  cerúleo y titanio (suelta `rocketnautics:raw_titanium`). `velo/CrystalSpireFeature` (`velo_crystal_spire`): agujas de
  cristal de 5–14 de alto con racimo en la punta y brotes alrededor.
- Botín: el racimo da 4 cristales cerúleos con pico (fortuna) o 2 sin él; los brotes solo con toque de seda; el brotante
  no se recoge. Recetas: 4 cristales → bloque; lámpara `PCP/CGC/PCP` (roca pulida, cristal, polvo de piedra luminosa);
  roca pulida y ladrillos; escaleras y losas (y cortapiedras); hierro y oro en horno/alto horno.
- Terreno (`build_velo`): relieve propio sencillo (altura 72, lomas de 10 bloques y detalle de 3 con dos ruidos 2D),
  4+ capas de polvo sobre la roca, cráteres con el carver de Cosmonautics (`rocketnautics:crater`, etiqueta
  `velo_carvable`). Biomas: llanuras de polvo, dunas de polvo, campos de cristal (agujas, brotes y racimos) y campos de
  cráteres (muchos cráteres y geodas rotas medio enterradas). Geodas de cristal bajo tierra en todos (la de la amatista
  de Minecraft con nuestros bloques). Sin aire, sin criaturas, gravedad 1,6.
- Órbita: alrededor de Cerúlea, 8 h (como la Luna), acoplamiento de marea, luz y día de Ámbar; cielo negro con
  estrellas de día (`AirlessStarsMixin`) y el universo de Cosmonautics (`UniverseSky`). Colores en el mapa y en el
  holograma de la consola.
### Ferra, luna de óxido de Cerúlea
- Ideas del usuario: tormentas como en Marte, "una luna hostil" y "que aparezca demasiado hierro, titanio, cobre,
  redstone y oro". Texturas y vistas previas: `tools/designs/ferra_textures.py` (`--export tools/designs/ferra`).
- Java `ferra/FerraBlocks`: polvo de óxido (cae), roca de Ferra, ladrillos y pulida (+ escaleras y losas), roca ocre
  (estratos de los cañones), pátina de cobre (orillas de las charcas), veta de hierro (3–6 hierros crudos), menas de
  hierro, cobre, oro, redstone (`RedStoneOreBlock`) y titanio (`rocketnautics:raw_titanium`). `ferra/RustPillarFeature`
  (`ferra_rust_pillar`): chimeneas de 6–17 bloques en franjas de roca y ocre con vetas y menas de hierro y titanio.
- Terreno (`build_ferra`): meseta a 76 con lomas; cañones de ~30 bloques de paredes rectas donde el ruido `ferra/canyon`
  pasa cerca de 0 (el mismo ruido da el bioma de cañón); paredes ocres (regla STEEP y 34 bloques de ocre en los
  cañones); charcas de agua verde con orilla de pátina (`minecraft:lake`) en las zonas bajas; pocos cráteres.
  Biomas: llanuras de óxido, cañones de óxido, charcas de cobre, chimeneas de hierro.
- Minerales (varias veces lo de la Tierra): hierro 40×14, vetas de hierro 5×22, titanio 22×10, cobre 28×16,
  redstone 18×10 y oro 14×10 por chunk, en roca y ocre.
- Tormentas: `StormType.FERRA_DUST_STORM` (como la de Marte, más frecuente: calma de medio día a día y cuarto, fuerte
  la mitad de las veces, más viento); `StormType.isDust()` agrupa las dos para el daño, las partículas y el sonido.
  El logro "Lijado por la arena" vale en las dos y el mensaje de muerte ya no dice "marciana".
- Aire fino no respirable, gravedad 2,4, órbita de Cerúlea en 16,7 h empezando en el lado contrario a Velo.
### Monstruos de Ferra
- Plan del usuario: "monstruos gusanos de tierra" y "cuadrúpedos que cacen en manada, ciegos como el Warden, que cacen
  por ruido". El gusano, enemigo normal pero no común, en las zonas despejadas (llanuras de óxido y charcas); los
  rastreadores en las chimeneas de hierro y los cañones. Modelos: `tools/designs/ferra_mobs.py tools/designs/mobs`.
- `ferra/mobs/RustWorm` (gusano de óxido, ~5 bloques, 40 de vida, 6 de armadura, 8 de daño): fases BURIED →
  EMERGING → OUT → DIVING. Enterrado es invisible e intocable; si nota pasos a menos de 20 bloques (jugador en el suelo
  que camina, corre, mina o pelea; agachado o quieto no) avanza bajo el polvo con rastro de partículas y temblor, a 2
  bloques sale mordiendo lo que tenga encima, se queda fuera 5–8 s mordiendo y se vuelve a enterrar. El cliente lo
  dibuja casi de pie (70°) con la mitad de atrás enterrada, subiendo y bajando al salir y meterse; los anillos ondulan.
  Spawn: 1/3 de los intentos y nunca otro a menos de 64 bloques; de día y de noche.
- `ferra/mobs/BlindStalker` (rastreador ciego, 24 de vida, 6 de daño): oye a los jugadores ruidosos a menos de 24 bloques
  (`AbyssalStalker.noisy`) y avisa a la manada (16 bloques); sin presa va a mirar los ruidos recientes de Ferra (bloques
  rotos, impactos de proyectiles: una bola de nieve lejos los distrae); se olvida tras 5 s de silencio salvo a 2,5
  bloques. Manadas de 3–5, como mucho ~8 en 48 bloques. Sonidos de Warden más agudos. Brillan más cazando.
- Botín: gusano 2–4 hierro crudo, 0–2 titanio crudo, 0–2 cobre crudo; rastreador 0–2 huesos y 5 % fragmento de eco.
- La capa de brillo de `CreatureRenderer` ya no se dibuja en entidades invisibles (el gusano enterrado).
- Arreglo: el gusano y los rastreadores se ahogaban (Cosmonautics deja sin respirar a todo en Ferra, que no tiene aire
  respirable). `FerraMobs.breathe` (LivingBreatheEvent, prioridad HIGH, como los caracoles de Ignis) les deja respirar
  salvo bajo el agua.
- Arreglo del oído: solo detectaban poner bloques. Se miraba la velocidad del jugador en el servidor, que casi siempre es
  0. Ahora `FerraMobs` escucha todas las vibraciones de Minecraft (`VanillaGameEvent`, las mismas del Warden y los sensores
  de sculk: pasos, saltos, caídas, poner/romper bloques, golpes, comer, puertas…) y guarda la última de cada jugador
  (`FerraMobs.heard`, 1,5 s). Agachado, los pasos no cuentan (`GameEventTags.IGNORE_VIBRATIONS_SNEAKING`, como el Warden).
  Los proyectiles que caen siguen sirviendo para distraer a la manada.
