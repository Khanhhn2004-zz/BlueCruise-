package com.vibegravity.bluecruise.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vibegravity.bluecruise.domain.auth.AuthSession
import com.vibegravity.bluecruise.domain.repository.AuthSessionRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class AuthPreferencesStore(
    private val dataStore: DataStore<Preferences>
) : AuthSessionRepository {

    override val sessionFlow: Flow<AuthSession> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            AuthSession(
                accessToken = preferences[ACCESS_TOKEN].orEmpty(),
                userId = preferences[USER_ID].orEmpty()
            )
        }

    override suspend fun saveSession(session: AuthSession) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[USER_ID] = session.userId
        }
    }

    override suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(USER_ID)
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        val USER_ID = stringPreferencesKey("auth_user_id")
    }
}
