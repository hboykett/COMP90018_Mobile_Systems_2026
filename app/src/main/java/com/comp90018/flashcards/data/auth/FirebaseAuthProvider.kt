package com.comp90018.flashcards.data.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase is only initialised when google-services.json was present at build time.
 * Without that file, [auth] stays null and the login screen explains how to configure it.
 */
@Singleton
class FirebaseAuthProvider
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        val auth: FirebaseAuth? =
            (
                FirebaseApp.initializeApp(context)
                    ?: FirebaseApp.getApps(context).firstOrNull()
            )?.let(FirebaseAuth::getInstance)
    }
