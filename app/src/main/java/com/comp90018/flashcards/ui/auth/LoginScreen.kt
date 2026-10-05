package com.comp90018.flashcards.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isSigningIn by viewModel.isSigningIn.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    var form by remember { mutableStateOf(EmailForm()) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Flashcards", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create an account, or sign in to see your decks.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        EmailAuthSection(
            form = form,
            enabled = !isSigningIn,
            onFormChange = { form = it },
            onSubmit = {
                if (form.isRegistering) {
                    viewModel.register(form.email, form.password, form.confirmPassword)
                } else {
                    viewModel.signInWithEmail(form.email, form.password)
                }
            },
            onForgotPassword = { viewModel.sendPasswordReset(form.email) },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.signIn(context) },
            enabled = !isSigningIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (isSigningIn) "Please wait…" else "Continue with Google")
        }
        AuthMessages(errorMessage = errorMessage, infoMessage = infoMessage)
    }
}

@Composable
private fun EmailAuthSection(
    form: EmailForm,
    enabled: Boolean,
    onFormChange: (EmailForm) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        EmailFields(form = form, enabled = enabled, onFormChange = onFormChange)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSubmit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(text = if (form.isRegistering) "Create account" else "Sign in")
        }
        TextButton(
            onClick = { onFormChange(form.copy(isRegistering = !form.isRegistering)) },
            enabled = enabled,
        ) {
            Text(
                text = if (form.isRegistering) "Already have an account? Sign in" else "Need an account? Register",
            )
        }
        if (!form.isRegistering) {
            TextButton(onClick = onForgotPassword, enabled = enabled) {
                Text("Forgot password?")
            }
        }
    }
}

@Composable
private fun EmailFields(
    form: EmailForm,
    enabled: Boolean,
    onFormChange: (EmailForm) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = form.email,
            onValueChange = { onFormChange(form.copy(email = it)) },
            label = { Text("Email") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = form.password,
            onValueChange = { onFormChange(form.copy(password = it)) },
            label = { Text("Password") },
            singleLine = true,
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.isRegistering) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = form.confirmPassword,
                onValueChange = { onFormChange(form.copy(confirmPassword = it)) },
                label = { Text("Confirm password") },
                singleLine = true,
                enabled = enabled,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuthMessages(
    errorMessage: String?,
    infoMessage: String?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        if (infoMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = infoMessage, textAlign = TextAlign.Center)
        }
    }
}

private data class EmailForm(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isRegistering: Boolean = true,
)
