package com.willowtreeapps.common.middleware

import com.willowtreeapps.common.Actions
import com.willowtreeapps.common.Actions.ChangeNumQuestionsSettingsAction
import com.willowtreeapps.common.AppState
import com.willowtreeapps.common.UserSettings
import com.willowtreeapps.common.repo.LocalStorageSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.reduxkotlin.Middleware
import org.reduxkotlin.middleware
import kotlin.coroutines.CoroutineContext

/**
 * Save and Loads user settingsRepo from local storage
 */
internal fun settingsMiddleware(settingsRepo: LocalStorageSettingsRepository,
                                backgroundContext: CoroutineContext): Middleware<AppState> {
    val backgroundScope = CoroutineScope(backgroundContext)

    return middleware { store, next, action ->
        backgroundScope.launch {
            when (action) {
                is ChangeNumQuestionsSettingsAction -> settingsRepo.numRounds = action.num

                is Actions.ChangeCategorySettingsAction -> settingsRepo.categoryId = action.categoryId

                is Actions.LoadAllSettingsAction -> {
                    val settings = UserSettings(numQuestions = settingsRepo.numRounds,
                            categoryId = settingsRepo.categoryId,
                            microphoneMode = settingsRepo.microphoneMode)
                    next(Actions.SettingsLoadedAction(settings))
                }

                is Actions.ChangeMicrophoneModeSettingsAction -> settingsRepo.microphoneMode = action.enabled
            }
        }
        next(action)
    }
}

