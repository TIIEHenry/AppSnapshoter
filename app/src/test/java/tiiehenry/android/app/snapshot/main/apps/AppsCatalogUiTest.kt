package tiiehenry.android.app.snapshot.main.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsCatalogUiTest {

    @Test
    fun `unloaded catalog shows loading even when flags are false`() {
        assertTrue(
            AppsCatalogUi.shouldShowLoading(
                catalogLoaded = false,
                isAppsLoading = false,
                isLocalProcessing = false,
            )
        )
    }

    @Test
    fun `loaded idle catalog does not show loading`() {
        assertFalse(
            AppsCatalogUi.shouldShowLoading(
                catalogLoaded = true,
                isAppsLoading = false,
                isLocalProcessing = false,
            )
        )
    }

    @Test
    fun `catalog fetch or local bind keeps loading`() {
        assertTrue(
            AppsCatalogUi.shouldShowLoading(
                catalogLoaded = true,
                isAppsLoading = true,
                isLocalProcessing = false,
            )
        )
        assertTrue(
            AppsCatalogUi.shouldShowLoading(
                catalogLoaded = true,
                isAppsLoading = false,
                isLocalProcessing = true,
            )
        )
    }

    @Test
    fun `empty map without users is not a catalog`() {
        assertFalse(AppsCatalogUi.shouldBindCatalog(emptyMap<Int, List<String>>()))
        assertTrue(AppsCatalogUi.shouldBindCatalog(mapOf(0 to emptyList<String>())))
    }

    @Test
    fun `catalog is loaded only after users were fetched`() {
        assertFalse(AppsCatalogUi.isSuccessfulCatalog(userCount = 0, loadSucceeded = true))
        assertFalse(AppsCatalogUi.isSuccessfulCatalog(userCount = 1, loadSucceeded = false))
        assertTrue(AppsCatalogUi.isSuccessfulCatalog(userCount = 1, loadSucceeded = true))
    }

    @Test
    fun `visible retry requests only when unloaded and not in flight`() {
        assertTrue(
            AppsCatalogUi.shouldRequestCatalog(
                catalogLoaded = false,
                isAppsLoading = false,
                attemptsUsed = 0,
            )
        )
        assertFalse(
            AppsCatalogUi.shouldRequestCatalog(
                catalogLoaded = false,
                isAppsLoading = true,
                attemptsUsed = 0,
            )
        )
        assertFalse(
            AppsCatalogUi.shouldRequestCatalog(
                catalogLoaded = true,
                isAppsLoading = false,
                attemptsUsed = 0,
            )
        )
        assertFalse(
            AppsCatalogUi.shouldRequestCatalog(
                catalogLoaded = false,
                isAppsLoading = false,
                attemptsUsed = AppsCatalogUi.MAX_VISIBLE_ATTEMPTS,
            )
        )
    }

    @Test
    fun `retry delay increases with completed attempts`() {
        assertTrue(AppsCatalogUi.retryDelayMs(0) < AppsCatalogUi.retryDelayMs(1))
        assertTrue(AppsCatalogUi.retryDelayMs(1) < AppsCatalogUi.retryDelayMs(3))
    }
}
