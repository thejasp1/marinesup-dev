# JSW Toon Remote Enterprise – installeren op eigen iPhone

Deze route gebruikt een gratis Apple Account en Xcode Personal Team. Er is geen betaald Apple Developer Program en geen App Store-publicatie nodig.

## Benodigd
- Een Mac met een recente versie van Xcode.
- Je eigen iPhone.
- Een gewone Apple Account met twee-factor-authenticatie.
- De iPhone en Mac bij voorkeur op dezelfde wifi, plus een USB-kabel voor de eerste koppeling.

## 1. Project ophalen
1. Open GitHub op de Mac.
2. Open repository `thejasp1/marinesup-dev`.
3. Kies branch `ios-toon-remote`.
4. Download/clone de repository.
5. Open `toon-remote-ios/ToonRemoteJSW.xcodeproj` in Xcode.

## 2. Apple Account toevoegen aan Xcode
1. Open Xcode > Settings > Accounts.
2. Klik op `+` en kies Apple Account.
3. Log in met je eigen Apple Account.
4. Xcode toont daarna je `Personal Team`.

## 3. Eigen iPhone koppelen
1. Verbind de iPhone met de Mac.
2. Ontgrendel de iPhone.
3. Kies `Vertrouw deze computer` als iOS dat vraagt.
4. Selecteer bovenin Xcode je eigen iPhone als run destination.

## 4. Gratis signing instellen
1. Klik links op project `ToonRemoteJSW`.
2. Selecteer target `ToonRemoteJSW`.
3. Open `Signing & Capabilities`.
4. Zet `Automatically manage signing` aan.
5. Kies bij `Team` jouw `Personal Team`.
6. Bundle Identifier staat standaard op `nl.jaspsoftware.toonremote`.
7. Als Apple meldt dat deze identifier niet beschikbaar is, maak hem uniek, bijvoorbeeld `nl.jaspsoftware.toonremote.jasp`.

## 5. App op iPhone zetten
1. Kies Product > Run of druk op de Play-knop in Xcode.
2. Xcode bouwt en signeert de app met jouw gratis Personal Team.
3. Als iOS Developer Mode vereist: Instellingen > Privacy en beveiliging > Developer Mode, inschakelen en iPhone herstarten.
4. Start daarna opnieuw vanuit Xcode.
5. Als iOS vraagt de ontwikkelaar te vertrouwen, volg de melding onder Instellingen > Algemeen > VPN en apparaatbeheer.

## 6. Toon verbinden
1. Zorg dat iPhone en gerootte Toon 2 op hetzelfde lokale netwerk zitten.
2. Open JSW Toon Remote Enterprise.
3. Vul het lokale IP-adres van Toon in, bijvoorbeeld `192.168.1.100`.
4. De app test de echte Toon API. Alleen bij een geldige response verschijnt `Verbonden`.

## Belangrijk bij gratis signing
Gratis Personal Team-signing is bedoeld voor ontwikkeling op je eigen toestel. Apple beperkt de geldigheidsduur; als de app later niet meer opent, sluit de iPhone opnieuw op de Mac aan en druk opnieuw op Run in Xcode. Voor blijvende distributie aan anderen is een betaald Apple Developer Program nodig.

## Beveiliging
De huidige lokale koppeling gebruikt Toon lokaal. Stel de gewone Toon HTTP-poort 80 niet rechtstreeks open naar internet. Externe bediening hoort later via een beveiligde HTTPS/443-gateway te lopen.
