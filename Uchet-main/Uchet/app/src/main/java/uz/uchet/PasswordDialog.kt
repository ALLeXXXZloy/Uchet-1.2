package uz.uchet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDialog(
    title: String,
    needsConfirm: Boolean,
    currentLang: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val t = { key: String -> Translations.t(key, currentLang) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val check = BackupEncryption.validatePassword(password)
    val passwordsMatch = !needsConfirm || password == confirm
    val canConfirm = check.valid && passwordsMatch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(t("backup_password")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                if (needsConfirm) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        label = { Text(t("backup_password_confirm")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = confirm.isNotEmpty() && password != confirm,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
                    )
                    if (confirm.isNotEmpty() && password != confirm) {
                        Text(t("backup_passwords_dont_match"), color = RED, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RequirementRow(t("backup_req_length"), check.hasLength)
                        RequirementRow(t("backup_req_upper"), check.hasUppercase)
                        RequirementRow(t("backup_req_lower"), check.hasLowercase)
                        RequirementRow(t("backup_req_digit"), check.hasDigit)
                        RequirementRow(t("backup_req_special"), check.hasSpecial)
                    }
                }

                Text(
                    t("backup_password_warning"),
                    style = MaterialTheme.typography.bodySmall,
                    color = YELLOW
                )

                error?.let { Text(it, color = RED, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    if (!check.valid) { error = t("backup_password_weak"); return@TextButton }
                    if (!passwordsMatch) { error = t("backup_passwords_dont_match"); return@TextButton }
                    onConfirm(password)
                }
            ) { Text(t("confirm")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

@Composable
private fun RequirementRow(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓ " else "○ ",
            color = if (ok) GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text,
            color = if (ok) GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
