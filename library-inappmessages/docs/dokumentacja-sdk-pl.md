# Dokumentacja Techniczna - Biblioteka In-App Messages SDK

## 1. Ogólny przegląd architektury

Biblioteka In-App Messages SDK została zaimplementowana zgodnie z zasadami Clean Architecture, SOLID oraz wzorcem MVVM. Głównym celem biblioteki jest dostarczenie kompleksowego rozwiązania do wyświetlania wiadomości w aplikacji, które są konfigurowane i zarządzane przez backend.

### 1.1. Główne warstwy

Architektura SDK składa się z czterech głównych warstw:

#### 1.1.1. Warstwa UI (Prezentacja)
- Wykorzystuje **Jetpack Compose** do renderowania wiadomości
- Zawiera szablony wiadomości (np. `TemplateReviewForDiscount`, `TemplateBannerMessage`)
- Implementuje mechanimy interakcji z użytkownikiem (kliknięcia, zamykanie)

#### 1.1.2. Warstwa kontrolerów (Prezentacja)
- `InAppUIController` - zarządza cyklem życia aktywności i synchronizacją wyświetlania
- `InAppMessageDisplayerImpl` - odpowiada za wyświetlanie dialogów i obsługę interakcji

#### 1.1.3. Warstwa biznesowa (Domena)
- `InAppMessageManagerImpl` - zarządza cyklem życia wiadomości, kwalifikacją i wyzwalaczami
- Obsługuje harmonogram, opóźnienia i logikę biznesową wiadomości

#### 1.1.4. Warstwa danych
- `InAppMessageRepositoryImpl` - pobiera wiadomości z API
- `InAppMessagePersistenceImpl` - zarządza stanem wiadomości (odrzucone, wygasłe, timestampy)
- Klienty API do komunikacji z backendem

### 1.2. Główne komponenty i ich odpowiedzialności

#### 1.2.1. InAppMessagesSDK
Główny punkt wejścia do biblioteki, odpowiedzialny za:
- Inicjalizację wszystkich komponentów
- Konfigurację API i klientów sieciowych
- Dostarczenie publicznego API do pokazywania wiadomości i ustawiania handlerów
- Zarządzanie cyklem życia SDK

#### 1.2.2. InAppMessageManager
Zarządza logiką biznesową wiadomości:
- Pobiera i przechowuje wiadomości
- Określa kwalifikację wiadomości (eligibility)
- Obsługuje wyzwalacze i harmonogram
- Dostarcza strumień aktywnych wiadomości

#### 1.2.3. InAppUIController
Łączy warstwę biznesową z UI:
- Obserwuje strumień wiadomości
- Zarządza callbackami cyklu życia aktywności
- Deleguje wyświetlanie do InAppMessageDisplayer

#### 1.2.4. InAppMessageDisplayer
Odpowiada za wyświetlanie wiadomości:
- Tworzy i zarządza dialogami
- Renderuje szablony Compose
- Obsługuje opóźnione wyświetlanie wiadomości
- Przetwarza interakcje użytkownika

### 1.3. Przepływ danych

1. **Inicjalizacja**: SDK inicjalizuje wszystkie komponenty i pobiera wiadomości z API
2. **Wyzwalanie**: Wiadomości są wyzwalane przez APP_OPEN, ROUTE lub CUSTOM trigger
3. **Filtrowanie**: InAppMessageManager filtruje wiadomości na podstawie eligibility
4. **Wyświetlanie**: InAppUIController obserwuje strumień wiadomości i deleguje wyświetlanie
5. **Interakcja**: InAppMessageDisplayer obsługuje interakcje użytkownika i akcje
6. **Zamknięcie**: Po zamknięciu, stan wiadomości jest aktualizowany w persistence

### 1.4. Kluczowe mechanizmy

- **Opóźnienia wiadomości** (showAfterDelay, showAgainDelay)
- **Harmonogram wiadomości** (dzień tygodnia, przedział godzin, daty)
- **Wyzwalacze wiadomości** (APP_OPEN, ROUTE, CUSTOM)
- **Akcje wiadomości** (REDIRECT, JS, SUBSCRIBE, CLOSE)
- **Persystencja stanu** (zapisywanie stanu odrzucenia, timestamp'ów)
- **Bezpieczne zarządzanie cyklem życia** (WeakReference do aktywności)
