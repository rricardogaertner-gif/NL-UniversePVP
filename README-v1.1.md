# NL-UniversePVP v1.1 – 1v1 en Free For All

Na `/nlpvp menu` kies je eerst Warrior, Archer, Tank, Assassin of Mage. Daarna kies je **1v1 Duel** of **Free For All**.

- 1v1 zoekt een tegenstander met dezelfde class en maakt een duel-arena.
- FFA brengt alle classes samen in de wereld `nlu_ffa`, met een gedeelde arena.
- `/nlpvp save warrior` (of archer/tank/assassin/mage) slaat een kit uit je inventaris op.
- `/nlpvp sethub` op het centrale HUB-plein legt de terugkeerlocatie vast.
- `/nlpvp leave` verlaat een wedstrijd of FFA.
- Bij dood kom je terug naar de HUB en krijg je een keuzemenu.

## Installatie

Behoud `.github/workflows/gradle.yml` en vervang de projectmap `NL-UniversePVP-source` in GitHub. Controleer een groene build voordat je de JAR installeert. Maak een serverbackup en verwijder oude PvP-JARs. Configureer de HUB via `/nlpvp sethub`.

**Let op:** de oorspronkelijke plugin bewaart inventarissnapshots alleen in RAM. Bij een servercrash tijdens een PvP-sessie kan verlies optreden. Test dus eerst met onbelangrijke items en open PvP nog niet publiekelijk. Deze versie implementeert het keuzemenu en FFA; persistente inventarisbeveiliging is een afzonderlijke noodzakelijke verbetering. De FFA-wereld is persistent, de arena wordt eenmaal gebouwd volgens `ffa-built`.
