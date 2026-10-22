# NL-UniversePVP v1.2 (zonder Multiverse)

Voor Paper 1.21.11 / Java 21. Deze broncode is voorbereid voor GitHub Actions en **nog niet op een live server getest**.

## Spelverloop
1. In de HUB: `/nlpvp menu`.
2. Kies Warrior, Archer, Tank, Assassin of Mage.
3. Kies **1v1 Duel** of **Free For All**.
4. `/nlpvp leave` verlaat PvP en herstelt de inventaris.

## Vooraf
- Zorg dat `hub-world: nluniverse_hub` correct staat.
- Ga op de gewenste HUB-spawn staan en gebruik `/nlpvp sethub`.
- Maak per class als beheerder de uitrusting in je inventaris en gebruik `/nlpvp save warrior` (of andere class).
- Maak een **volledige serverback-up** voordat je PvP gaat testen.
- Test met een leeg inventaris en bij voorkeur een testaccount; zet PvP nog niet open voor spelers.

## Inventarisherstel
De plugin schrijft een herstelbestand weg in `plugins/NL-UniversePVP/recovery/<uuid>.yml` **voordat** de PvP-kit wordt toegepast. Bij normaal verlaten, uitloggen of herstart wordt de oorspronkelijke inventaris hersteld. Bij een crash wordt het herstelbestand bij opnieuw inloggen gelezen. Bewaar deze recovery-map bij backups.

## Geen Multiverse
Geen `Multiverse-Core` of `Multiverse-Inventories` vereist. De plugin maakt de duelwerelden en `nlu_ffa` via Bukkit/Paper aan.

## Bekende aandachtspunten
- De FFA-arena wordt door de plugin gebouwd; de eerste plaatsing kan serverlag geven.
- De FFA gebruikt één gedeelde arena; verdere gameplaybalans en spawn-bescherming moeten nog getest worden.
- `/nlpvp openmenu` is bedoeld voor een commandblock in de HUB. Het daadwerkelijke HUB-portaal koppelen we later.
- Test doodgaan, reconnecten en herstarten voordat spelers met waardevolle spullen PvP gebruiken.
