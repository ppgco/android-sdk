# Model danych i przepływ wiadomości In-App Messages SDK

## 3. Model danych i przepływ wiadomości

### 3.1. Struktura modelu danych wiadomości

#### 3.1.1. Model InAppMessage

Główna klasa modelu reprezentująca wiadomość w aplikacji:

```kotlin
data class InAppMessage(
    val id: String,
    val name: String,
    val project: String,
    val layout: InAppMessageLayout,
    val style: InAppMessageStyle,
    val title: String?,
    val description: String?,
    val image: String?,
    val actions: List<InAppMessageAction>?,
    val audience: InAppMessageAudience?,
    val settings: InAppMessageSettings?,
    val dismissible: Boolean?,
    val priority: Int?,
    val schedule: InAppMessageSchedule?,
    val expiration: InAppMessageExpiration?,
)
```

**Opis pól:**
- `id`, `name`, `project` - identyfikatory wiadomości i projektu
- `layout` - parametry układu (marginesy, wypełnienia, odstępy)
- `style` - parametry stylu (kolory, obramowania, cienie)
- `title`, `description` - tekstowa zawartość wiadomości
- `image` - URL obrazu (opcjonalnie)
- `actions` - przyciski i ich akcje
- `audience` - kryteria grupy docelowej
- `settings` - ustawienia behawioralne (wyzwalacze, opóźnienia)
- `dismissible` - czy wiadomość można zamknąć klikając poza jej obszarem
- `priority` - priorytet wyświetlania (wyższa wartość = wyższy priorytet)
- `schedule` - okna czasowe wyświetlania
- `expiration` - kiedy wiadomość wygasa

#### 3.1.2. InAppMessageLayout

```kotlin
data class InAppMessageLayout(
    val templateType: InAppMessageTemplateType,
    val placement: InAppMessagePlacement,
    val margin: InAppMessageSpacing?,
    val padding: InAppMessageSpacing?,
    val paddingBody: InAppMessageSpacing?,
    val spaceBetweenImageAndBody: Int?,
    val spaceBetweenTitleAndDescription: Int?,
    val spaceBetweenContentAndActions: Int?,
)
```

**Opis pól:**
- `templateType` - typ szablonu wiadomości (np. REVIEW_FOR_DISCOUNT, BANNER)
- `placement` - pozycja wiadomości na ekranie (TOP, BOTTOM, CENTER)
- `margin` - marginesy zewnętrzne
- `padding` - wypełnienie całej wiadomości
- `paddingBody` - wypełnienie treści wiadomości
- `spaceBetweenImageAndBody` - odstęp między obrazem a treścią
- `spaceBetweenTitleAndDescription` - odstęp między tytułem a opisem
- `spaceBetweenContentAndActions` - odstęp między treścią a przyciskami

#### 3.1.3. InAppMessageSettings

```kotlin
data class InAppMessageSettings(
    val triggerType: InAppTriggerType?,
    val key: String?,
    val value: String?,
    val showAgain: Boolean?,
    val showAgainDelay: Int?,
    val showAfterDelay: Int?,
)
```

**Opis pól:**
- `triggerType` - określa jak wiadomość jest wyzwalana (APP_OPEN, CUSTOM, ROUTE)
- `key`, `value` - parametry dla triggerów niestandardowych i triggerów routów
- `showAgain` - czy pokazywać wiadomość ponownie po zamknięciu
- `showAgainDelay` - czas w milisekundach przed ponownym pokazaniem
- `showAfterDelay` - czas w milisekundach przed pierwszym pokazaniem

#### 3.1.4. InAppMessageAction

```kotlin
data class InAppMessageAction(
    val type: InAppActionType,
    val label: String?,
    val url: String?,
    val js: String?,
    val style: InAppMessageActionStyle?,
)
```

**Opis pól:**
- `type` - rodzaj akcji (REDIRECT, JS, SUBSCRIBE, CLOSE)
- `label` - etykieta przycisku
- `url` - dla akcji REDIRECT
- `js` - dla akcji JS
- `style` - styl przycisku (kolory, obramowanie)

#### 3.1.5. Typy enumeracyjne

**InAppTriggerType**:
```kotlin
enum class InAppTriggerType {
    APP_OPEN,    // Wyzwalane automatycznie po otwarciu aplikacji
    CUSTOM,      // Wyzwalane przez niestandardowy klucz i wartość
    ROUTE        // Wyzwalane przez zmianę routa/ekranu
}
```

**InAppActionType**:
```kotlin
enum class InAppActionType {
    REDIRECT,    // Przekierowanie do URL
    JS,          // Wykonanie kodu JavaScript
    SUBSCRIBE,   // Uruchomienie procesu subskrypcji powiadomień
    CLOSE        // Zamknięcie wiadomości
}
```

**InAppMessagePlacement**:
```kotlin
enum class InAppMessagePlacement {
    TOP,         // Góra ekranu
    BOTTOM,      // Dół ekranu
    CENTER       // Środek ekranu
}
```

**InAppMessageTemplateType**:
```kotlin
enum class InAppMessageTemplateType {
    REVIEW_FOR_DISCOUNT,  // Szablon oceny z rabatem
    BANNER                // Szablon baneru
}
```

### 3.2. Przepływ danych wiadomości

#### 3.2.1. Pozyskiwanie wiadomości

1. Wiadomości są pobierane z API przez `InAppMessageRepositoryImpl`:

```kotlin
override suspend fun fetchMessages(): List<InAppMessage> {
    return try {
        api.getMessagesList(projectId, apiKey).messages?.mapNotNull { 
            it.toInAppMessage() 
        } ?: emptyList()
    } catch (e: Exception) {
        Timber.e(e, "Error fetching in-app messages")
        emptyList()
    }
}
```

2. Surowe dane API są mapowane na model domeny `InAppMessage` przez metodę `toInAppMessage()`

3. Wiadomości są przechowywane w `InAppMessageManagerImpl` w strumieniu:

```kotlin
private val _messagesFlow = MutableStateFlow<List<InAppMessage>>(emptyList())
override val messagesFlow: Flow<List<InAppMessage>> = _messagesFlow.asStateFlow()
```

#### 3.2.2. Filtrowanie aktywnych wiadomości

`InAppMessageManagerImpl` filtruje wiadomości na podstawie kilku kryteriów:

```kotlin
suspend fun refreshActiveMessages(route: String? = null) {
    val fetchedMessages = repository.fetchMessages().filter { message ->
        val isEligible = isMessageEligible(message)
        val matchesRoute = route?.let { 
            message.settings?.triggerType == InAppTriggerType.ROUTE && 
            message.settings.key == route 
        } ?: false
        val isOpenTrigger = message.settings?.triggerType == InAppTriggerType.APP_OPEN
        
        isEligible && (matchesRoute || isOpenTrigger)
    }.sortedByDescending { it.priority ?: 0 }
    
    _messagesFlow.emit(fetchedMessages)
}
```

Kryteria kwalifikacji w `isMessageEligible`:
1. Wiadomość nie jest oznaczona jako odrzucona (lub może być pokazana ponownie)
2. Wiadomość nie wygasła
3. Wiadomość jest w oknie harmonogramu
4. Wiadomość pasuje do bieżącego urządzenia/OS
5. Minął odpowiedni czas od ostatniego zamknięcia (showAgainDelay)
6. Minął odpowiedni czas od pierwszej kwalifikacji (showAfterDelay)

#### 3.2.3. Wyzwalanie wiadomości

Wiadomości mogą być wyzwalane na trzy sposoby:

1. **Automatycznie przy otwarciu aplikacji** (triggerType == APP_OPEN)
   - Aktywowane podczas inicjalizacji SDK lub po wznowieniu aktywności

2. **Przy zmianie ekranu/routa** (triggerType == ROUTE, key == nazwa_routa)
   - Wywoływane przez `showActiveMessages(route)`

3. **Przez niestandardowy wyzwalacz** (triggerType == CUSTOM, key == klucz_wyzwalacza)
   - Wywoływane przez `showMessagesOnTrigger(key, value)`

```kotlin
suspend fun trigger(key: String, value: String?): InAppMessage? {
    val eligibleMessages = repository.fetchMessages().filter { 
        isMessageEligible(it) &&
        it.settings?.triggerType == InAppTriggerType.CUSTOM &&
        it.settings.key == key &&
        (value == null || it.settings.value == value)
    }
    
    return eligibleMessages.maxByOrNull { it.priority ?: 0 }
}
```

#### 3.2.4. Przepływ danych do UI

1. `InAppUIController` obserwuje strumień wiadomości:

```kotlin
private fun observeMessages() {
    scope.launch {
        manager.messagesFlow.collect { messages ->
            val currentActivity = currentActivity.get() ?: return@collect
            if (messages.isNotEmpty()) {
                displayer.showMessage(currentActivity, messages.first())
            }
        }
    }
}
```

2. `InAppMessageDisplayerImpl` renderuje wiadomości:

```kotlin
override fun showMessage(activity: Activity, message: InAppMessage) {
    if (message.settings?.showAfterDelay != null && message.settings.showAfterDelay > 0) {
        showMessageWithDelay(activity, message)
    } else {
        showMessageImmediately(activity, message)
    }
}
```

3. Wiadomość jest renderowana przez odpowiedni szablon Compose na podstawie `message.layout.templateType`

### 3.3. Opóźnienia i harmonogram wiadomości

#### 3.3.1. Opóźnione wyświetlanie (showAfterDelay)

```kotlin
private fun showMessageWithDelay(activity: Activity, message: InAppMessage) {
    // Zapisujemy referencję do aktywności
    val weakActivity = WeakReference(activity)
    
    // Tworzymy coroutine z opóźnieniem
    val job = scope.launch {
        val delayMs = message.settings?.showAfterDelay?.toLong() ?: 0L
        delay(delayMs)
        
        // Sprawdzamy czy aktywność nadal istnieje
        val currentActivity = weakActivity.get() ?: return@launch
        if (currentActivity.isFinishing || currentActivity.isDestroyed) {
            return@launch
        }
        
        // Pokazujemy wiadomość
        showMessageImmediately(currentActivity, message)
    }
    
    // Zapisujemy zadanie do anulowania w razie potrzeby
    pendingMessages[message.id] = job
}
```

#### 3.3.2. Harmonogram wiadomości

```kotlin
private fun isInScheduleWindow(message: InAppMessage): Boolean {
    val schedule = message.schedule ?: return true
    val now = LocalDateTime.now()
    
    // Sprawdzamy czy jest w ramach czasowych
    val afterStartDate = schedule.startDate?.let { 
        LocalDate.parse(it).atStartOfDay().isBefore(now) 
    } ?: true
    
    val beforeEndDate = schedule.endDate?.let { 
        LocalDate.parse(it).plusDays(1).atStartOfDay().isAfter(now) 
    } ?: true
    
    // Sprawdzamy dzień tygodnia
    val isDayMatching = schedule.weekDays?.isEmpty() != false || 
        schedule.weekDays.contains(now.dayOfWeek.value)
    
    // Sprawdzamy przedział godzinowy
    val isHourMatching = schedule.hourStart == null || schedule.hourEnd == null ||
        isTimeInRange(schedule.hourStart, schedule.hourEnd, now.toLocalTime())
    
    return afterStartDate && beforeEndDate && isDayMatching && isHourMatching
}
```

### 3.4. Synchronizacja stanu wiadomości

#### 3.4.1. Przechowywanie stanu w SharedPreferences

`InAppMessagePersistenceImpl` zarządza stanem wiadomości używając SharedPreferences:

```kotlin
class InAppMessagePersistenceImpl(context: Context) : InAppMessagePersistence {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("in_app_messages_prefs", Context.MODE_PRIVATE)

    override fun isMessageDismissed(messageId: String): Boolean =
        prefs.getBoolean("dismissed_$messageId", false)

    override fun markMessageDismissed(messageId: String) {
        prefs.edit { putBoolean("dismissed_$messageId", true) }
        setLastDismissedAt(messageId, System.currentTimeMillis())
    }

    override fun getLastDismissedAt(messageId: String): Long? =
        if (prefs.contains("last_dismissed_$messageId")) 
            prefs.getLong("last_dismissed_$messageId", 0L) 
        else null

    override fun getFirstEligibleAt(messageId: String): Long? =
        if (prefs.contains("first_eligible_at_$messageId")) 
            prefs.getLong("first_eligible_at_$messageId", 0L) 
        else null
}
```

#### 3.4.2. Aktualizacja stanu po zamknięciu wiadomości

Po zamknięciu wiadomości, jej stan jest aktualizowany:

```kotlin
override fun dismissMessage(messageId: String) {
    pendingMessages[messageId]?.cancel()
    pendingMessages.remove(messageId)
    
    // Aktualizacja stanu w persystencji
    persistence.markMessageDismissed(messageId)
    
    // Odświeżanie listy aktywnych wiadomości
    scope.launch {
        manager.refreshActiveMessages()
    }
}
```

#### 3.4.3. Obsługa ponownego pokazywania wiadomości (showAgain)

```kotlin
// Fragment z InAppMessageManagerImpl.kt
private fun isEligibleAfterDismissal(message: InAppMessage): Boolean {
    val messageId = message.id
    val isMessageDismissed = persistence.isMessageDismissed(messageId)
    
    // Jeśli wiadomość nie była odrzucona, jest kwalifikująca
    if (!isMessageDismissed) {
        return true
    }
    
    // Jeśli wiadomość była odrzucona i nie może być pokazana ponownie
    if (message.settings?.showAgain != true) {
        return false
    }
    
    // Jeśli wiadomość może być pokazana ponownie, sprawdzamy czy minął wymagany czas
    val lastDismissedAt = persistence.getLastDismissedAt(messageId) ?: return true
    val currentTime = System.currentTimeMillis()
    val showAgainDelay = message.settings.showAgainDelay?.toLong() ?: 0L
    
    // Zwracamy true jeśli minął wystarczający czas od ostatniego odrzucenia
    return currentTime - lastDismissedAt >= showAgainDelay
}
```

### 3.5. Wskazówki implementacyjne

#### 3.5.1. Kluczowe wzorce projektowe

1. **Observer Pattern**: `messagesFlow` jest obserwowany przez `InAppUIController`
2. **Factory Method**: Tworzenie odpowiednich szablonów wiadomości na podstawie typu
3. **Repository Pattern**: Abstrakcja dostępu do danych API
4. **Dependency Injection**: Wstrzykiwanie zależności przez konstruktor

#### 3.5.2. Bezpieczny dostęp do aktywności

Ponieważ wiadomości są wyświetlane asynchronicznie, SDK wykorzystuje `WeakReference<Activity>` aby uniknąć wycieków pamięci:

```kotlin
// Przechowywanie słabej referencji do aktywności
private val currentActivity = WeakReference<Activity>(null)

// Bezpieczne użycie aktywności
val activity = currentActivity.get() ?: return
if (activity.isFinishing || activity.isDestroyed) {
    return
}
```

#### 3.5.3. Prawidłowa obsługa cyklu życia wiadomości

```kotlin
// Zatrzymanie wyświetlania wiadomości gdy aktywność jest niszczona
override fun onActivityDestroyed(activity: Activity) {
    if (currentActivity.get() == activity) {
        displayer.cancelPendingMessages()
        currentActivity.clear()
    }
}

// Anulowanie wszystkich oczekujących wiadomości
override fun cancelPendingMessages() {
    pendingMessages.forEach { (_, job) -> job.cancel() }
    pendingMessages.clear()
}
```
