# Szczegółowa dokumentacja techniczna komponentów In-App Messages SDK

## 2. Szczegółowa dokumentacja techniczna

### 2.1. InAppMessagesSDK

**Klasa**: `InAppMessagesSDK`
**Ścieżka**: `com.pushpushgo.inappmessages.InAppMessagesSDK`

**Odpowiedzialności**:
- Inicjalizacja i konfiguracja całego SDK
- Tworzenie i zarządzanie instancjami wszystkich komponentów
- Dostarczanie publicznego API do wywoływania funkcjonalności SDK

**Kluczowe metody**:

```kotlin
// Inicjalizacja SDK
fun initialize(application: Application, projectId: String, apiKey: String, debug: Boolean = false)

// Pobranie instancji SDK
fun getInstance(): InAppMessagesSDK

// Pokazanie aktywnych wiadomości dla danego routa
fun showActiveMessages(currentRoute: String? = null)

// Pokazanie wiadomości dla określonego triggera
fun showMessagesOnTrigger(key: String, value: String? = null)

// Ustawienie handlera dla akcji JavaScript
fun setJsActionHandler(handler: (jsCall: String) -> Unit)

// Ustawienie interfejsu do integracji z SDK powiadomień push
fun setPushNotificationSubscriber(subscriber: PushNotificationSubscriber)

// Czyszczenie zasobów SDK
fun cleanup()
```

**Diagram zależności**:
- InAppMessagesSDK
  - Inicjalizuje → InAppMessageRepository
  - Inicjalizuje → InAppMessagePersistence
  - Inicjalizuje → InAppMessageManager
  - Inicjalizuje → InAppMessageDisplayer
  - Inicjalizuje → InAppUIController

### 2.2. InAppMessageManagerImpl

**Klasa**: `InAppMessageManagerImpl`
**Ścieżka**: `com.pushpushgo.inappmessages.manager.InAppMessageManagerImpl`
**Interfejs**: `InAppMessageManager`

**Odpowiedzialności**:
- Pobieranie i zarządzanie wiadomościami z API
- Określanie kwalifikacji wiadomości (filtrowanie)
- Obsługa triggerów wiadomości
- Zarządzanie harmonogramem i opóźnieniami
- Dostarczanie strumienia aktywnych wiadomości

**Kluczowe metody**:

```kotlin
// Odświeżenie listy aktywnych wiadomości
override suspend fun refreshActiveMessages(route: String?)

// Obsługa triggera wiadomości
override suspend fun trigger(key: String, value: String?): InAppMessage?

// Sprawdzenie kwalifikacji wiadomości
internal fun isMessageEligible(message: InAppMessage): Boolean

// Sprawdzenie czy wiadomość jest w harmonogramie
private fun isInScheduleWindow(message: InAppMessage): Boolean

// Sprawdzenie czy minął odpowiedni czas od pierwszej kwalifikacji
private fun isEligibleBasedOnFirstEligibleTime(message: InAppMessage): Boolean

// Sprawdzenie czy minął odpowiedni czas od ostatniego odrzucenia
private fun isEligibleAfterDismissal(message: InAppMessage): Boolean
```

**Główne mechanizmy**:
- **Mutex**: zapewnia bezpieczeństwo współbieżności podczas odświeżania wiadomości
- **Flow**: dostarcza reaktywny strumień wiadomości
- **Kryteria kwalifikacji**:
  1. Wiadomość nie jest oznaczona jako odrzucona (lub może być pokazana ponownie)
  2. Wiadomość nie wygasła
  3. Wiadomość jest w oknie harmonogramu
  4. Wiadomość pasuje do bieżącego urządzenia/OS
  5. Minął odpowiedni czas od ostatniego zamknięcia (showAgainDelay)
  6. Minął odpowiedni czas od pierwszej kwalifikacji (showAfterDelay)

### 2.3. InAppUIController

**Klasa**: `InAppUIController`
**Ścieżka**: `com.pushpushgo.inappmessages.ui.InAppUIController`

**Odpowiedzialności**:
- Obserwacja strumienia wiadomości
- Zarządzanie callbackami cyklu życia aktywności
- Synchronizacja pokazywania wiadomości z cyklem życia aktywności

**Kluczowe metody**:

```kotlin
// Rozpoczęcie obserwacji wiadomości
fun start()

// Zatrzymanie obserwacji
fun stop()

// Rejestracja callbacków cyklu życia aktywności
fun registerActivityCallbacks()

// Obsługa wznowienia aktywności
override fun onActivityResumed(activity: Activity)

// Obsługa zatrzymania aktywności
override fun onActivityPaused(activity: Activity)
```

**Główne mechanizmy**:
- **WeakReference**: bezpieczne przechowywanie referencji do aktywności
- **Application.ActivityLifecycleCallbacks**: monitorowanie cyklu życia aktywności
- **Flow collection**: reaktywna obserwacja strumienia wiadomości

### 2.4. InAppMessageDisplayerImpl

**Klasa**: `InAppMessageDisplayerImpl`
**Ścieżka**: `com.pushpushgo.inappmessages.ui.InAppMessageDisplayerImpl`
**Interfejs**: `InAppMessageDisplayer`

**Odpowiedzialności**:
- Tworzenie i wyświetlanie dialogów z wiadomościami
- Renderowanie szablonów Compose
- Obsługa opóźnionego wyświetlania wiadomości
- Przetwarzanie interakcji użytkownika i akcji

**Kluczowe metody**:

```kotlin
// Wyświetlenie wiadomości
override fun showMessage(activity: Activity, message: InAppMessage)

// Opóźnione wyświetlanie wiadomości
private fun showMessageWithDelay(activity: Activity, message: InAppMessage)

// Natychmiastowe wyświetlanie wiadomości
private fun showMessageImmediately(activity: Activity, message: InAppMessage)

// Tworzenie widoku Compose dla wiadomości
private fun createComposeView(activity: Activity, message: InAppMessage)

// Wyświetlanie wiadomości w kontenerze
private fun displayMessageInContainer(activity: Activity, message: InAppMessage)

// Zamknięcie wiadomości
override fun dismissMessage(messageId: String)

// Anulowanie oczekujących wiadomości
override fun cancelPendingMessages()
```

**Główne mechanizmy**:
- **Dialog z dispatchTouchEvent**: wykrywanie kliknięć poza obszarem wiadomości
- **ComposeView**: renderowanie UI w Jetpack Compose
- **CoroutineScope**: zarządzanie asynchronicznymi operacjami
- **WeakReference**: zapobieganie wyciekom pamięci
- **Window gravity**: pozycjonowanie dialogu na podstawie konfiguracji z backendu

### 2.5. InAppMessageRepositoryImpl

**Klasa**: `InAppMessageRepositoryImpl`
**Ścieżka**: `com.pushpushgo.inappmessages.repository.InAppMessageRepositoryImpl`
**Interfejs**: `InAppMessageRepository`

**Odpowiedzialności**:
- Pobieranie wiadomości z API
- Mapowanie danych API na model domeny
- Obsługa błędów komunikacji sieciowej

**Kluczowe metody**:

```kotlin
// Pobieranie listy wiadomości
override suspend fun fetchMessages(): List<InAppMessage>

// Wysyłanie zdarzeń wiadomości do API
override suspend fun sendEvent(messageId: String, eventType: String)
```

### 2.6. InAppMessagePersistenceImpl

**Klasa**: `InAppMessagePersistenceImpl`
**Ścieżka**: `com.pushpushgo.inappmessages.persistence.InAppMessagePersistenceImpl`
**Interfejs**: `InAppMessagePersistence`

**Odpowiedzialności**:
- Zarządzanie stanem wiadomości w SharedPreferences
- Przechowywanie informacji o odrzuconych wiadomościach
- Zapisywanie i odczytywanie timestamp'ów

**Kluczowe metody**:

```kotlin
// Sprawdzenie czy wiadomość została odrzucona
override fun isMessageDismissed(messageId: String): Boolean

// Oznaczenie wiadomości jako odrzuconej
override fun markMessageDismissed(messageId: String)

// Pobranie czasu ostatniego odrzucenia wiadomości
override fun getLastDismissedAt(messageId: String): Long?

// Ustawienie czasu ostatniego odrzucenia
override fun setLastDismissedAt(messageId: String, timestamp: Long)

// Pobranie czasu pierwszej kwalifikacji wiadomości
override fun getFirstEligibleAt(messageId: String): Long?

// Ustawienie czasu pierwszej kwalifikacji
override fun setFirstEligibleAt(messageId: String, timestamp: Long)

// Resetowanie czasu pierwszej kwalifikacji
override fun resetFirstEligibleAt(messageId: String)
```

### 2.7. Szablony UI (Jetpack Compose)

#### 2.7.1. TemplateReviewForDiscount

**Klasa**: `TemplateReviewForDiscount`
**Ścieżka**: `com.pushpushgo.inappmessages.ui.composables.TemplateReviewForDiscount`

**Odpowiedzialności**:
- Renderowanie szablonu wiadomości typu "review for discount"
- Obsługa layoutu i stylizacji na podstawie parametrów z backendu
- Obsługa przycisków akcji

**Kluczowe elementy**:

```kotlin
@Composable
fun TemplateReviewForDiscount(
    message: InAppMessage,
    onAction: (InAppMessageAction) -> Unit,
    onDismiss: () -> Unit
) {
    // Renderowanie layoutu na podstawie parametrów z backendu
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(parsePadding(message.layout.margin))
    ) {
        // ...
    }
}
```

#### 2.7.2. TemplateBannerMessage

**Klasa**: `TemplateBannerMessage`
**Ścieżka**: `com.pushpushgo.inappmessages.ui.composables.TemplateBannerMessage`

**Odpowiedzialności**:
- Renderowanie szablonu wiadomości typu "banner"
- Obsługa layoutu i stylizacji na podstawie parametrów z backendu

**Kluczowe elementy**:

```kotlin
@Composable
fun TemplateBannerMessage(
    message: InAppMessage,
    onAction: (InAppMessageAction) -> Unit,
    onDismiss: () -> Unit
) {
    // ...
}
```

#### 2.7.3. TemplateRichMessage

**Klasa**: `TemplateRichMessage`
**Ścieżka**: `com.pushpushgo.inappmessages.ui.composables.TemplateRichMessage`

**Odpowiedzialności**:
- Renderowanie szablonu wiadomości typu "rich"
- Obsługa layoutu i stylizacji na podstawie parametrów z backendu

**Kluczowe elementy**:

```kotlin
@Composable
fun TemplateRichMessage(
    message: InAppMessage,
    onAction: (InAppMessageAction) -> Unit,
    onDismiss: () -> Unit
) {
    // ...
}
```
