package com.turisla.hellopocket.ui.feature.addPaymentCard

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.PaymentCardBrand
import com.turisla.hellopocket.ui.feature.common.AttachmentSection
import com.turisla.hellopocket.ui.feature.common.CustomFieldEditSection
import com.turisla.hellopocket.ui.feature.common.EntryCategorySelectionSection
import com.turisla.hellopocket.ui.feature.common.EntryFormCard
import com.turisla.hellopocket.ui.feature.common.EntryFormDivider
import com.turisla.hellopocket.ui.feature.common.EntryFormSectionHeader
import com.turisla.hellopocket.ui.feature.common.EntryFormSelectionField
import com.turisla.hellopocket.ui.feature.common.EntryFormTextArea
import com.turisla.hellopocket.ui.feature.common.EntryFormTextField
import com.turisla.hellopocket.ui.feature.common.PaymentCardNumberVisualTransformation
import com.turisla.hellopocket.ui.feature.common.ResetSensitiveStateOnBackground
import com.turisla.hellopocket.ui.feature.common.labelResId
import com.turisla.hellopocket.ui.feature.common.paymentCardNumberDigits
import com.turisla.hellopocket.ui.feature.common.selectablePaymentCardBrands
import com.turisla.hellopocket.ui.feature.common.toProtoCustomFields
import com.turisla.hellopocket.ui.feature.home.HomePageViewModel
import com.turisla.hellopocket.utils.AppConstants
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentCardScreen(
    onNavigateBack: () -> Unit,
    onLoading: (Boolean) -> Unit = {},
    viewModel: HomePageViewModel = koinViewModel(),
    draftViewModel: AddPaymentCardDraftViewModel = koinViewModel(),
) {
    val draft by draftViewModel.draft.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val isAttachmentLoading by viewModel.isAttachmentLoading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnLoading by rememberUpdatedState(onLoading)
    var error by remember { mutableStateOf<String?>(null) }
    var customFieldValidationAttempted by remember { mutableStateOf(false) }
    var cardNumberVisible by remember { mutableStateOf(false) }
    var securityCodeVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val isBusy = isSaving || isAttachmentLoading

    ResetSensitiveStateOnBackground {
        cardNumberVisible = false
        securityCodeVisible = false
    }

    LaunchedEffect(isSaving, isAttachmentLoading) {
        currentOnLoading(isSaving || isAttachmentLoading)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnLoading(false) }
    }

    BackHandler {
        if (!isBusy) {
            draftViewModel.clear()
            onNavigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.add_payment_card),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            draftViewModel.clear()
                            onNavigateBack()
                        },
                        enabled = !isBusy,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
                .pointerInput(focusManager) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
        ) {
            EntryFormSectionHeader(title = stringResource(R.string.title))
            EntryFormCard {
                EntryFormTextField(
                    label = stringResource(R.string.title_required),
                    value = draft.title,
                    onValueChange = {
                        draftViewModel.updateTitle(it)
                        error = null
                    },
                    isError = error != null && draft.title.isBlank(),
                )
            }

            EntryFormSectionHeader(title = stringResource(R.string.card_information))
            EntryFormCard {
                EntryFormTextField(
                    label = stringResource(R.string.cardholder_name_optional),
                    value = draft.cardholderName,
                    onValueChange = draftViewModel::updateCardholderName,
                    trailingIcon = Icons.Outlined.PersonOutline,
                )
                EntryFormDivider()
                SensitiveCardTextField(
                    value = draft.cardNumber,
                    onValueChange = {
                        draftViewModel.updateCardNumber(it)
                        error = null
                    },
                    label = stringResource(R.string.card_number_required),
                    isVisible = cardNumberVisible,
                    onVisibilityToggle = { cardNumberVisible = !cardNumberVisible },
                    isError = error != null && paymentCardNumberDigits(draft.cardNumber).length !in
                        AppConstants.MIN_PAYMENT_CARD_NUMBER_LENGTH..
                            AppConstants.MAX_PAYMENT_CARD_NUMBER_LENGTH,
                    visibleVisualTransformation = PaymentCardNumberVisualTransformation,
                )
                EntryFormDivider()
                PaymentCardDropdown(
                    label = stringResource(R.string.card_brand),
                    value = stringResource(draft.cardBrand.labelResId()),
                    options = selectablePaymentCardBrands.map { brand ->
                        brand to stringResource(brand.labelResId())
                    },
                    onSelect = draftViewModel::updateCardBrand,
                    isValueSet = draft.cardBrand !=
                        PaymentCardBrand.PAYMENT_CARD_BRAND_UNSPECIFIED,
                )
                EntryFormDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    PaymentCardDropdown(
                        label = stringResource(R.string.expiration_month),
                        value = if (draft.expirationMonth == 0) {
                            stringResource(R.string.not_set)
                        } else {
                            draft.expirationMonth.toString().padStart(2, '0')
                        },
                        options = listOf(0 to stringResource(R.string.not_set)) +
                            (1..12).map { month ->
                                month to month.toString().padStart(2, '0')
                            },
                        onSelect = draftViewModel::updateExpirationMonth,
                        modifier = Modifier.weight(1f),
                        isValueSet = draft.expirationMonth != 0,
                        isError = error != null &&
                            (draft.expirationMonth == 0) != (draft.expirationYear == 0),
                    )
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp,
                    )
                    val currentYear = remember {
                        Calendar.getInstance().get(Calendar.YEAR)
                    }
                    val years = remember(currentYear) {
                        ((currentYear - 20).coerceAtLeast(AppConstants.MIN_EXPIRATION_YEAR)..
                            (currentYear + 30)).toList()
                    }
                    PaymentCardDropdown(
                        label = stringResource(R.string.expiration_year),
                        value = if (draft.expirationYear == 0) {
                            stringResource(R.string.not_set)
                        } else {
                            draft.expirationYear.toString()
                        },
                        options = listOf(0 to stringResource(R.string.not_set)) +
                            years.map { year -> year to year.toString() },
                        onSelect = draftViewModel::updateExpirationYear,
                        modifier = Modifier.weight(1f),
                        isValueSet = draft.expirationYear != 0,
                        isError = error != null &&
                            (draft.expirationMonth == 0) != (draft.expirationYear == 0),
                    )
                }
                EntryFormDivider()
                SensitiveCardTextField(
                    value = draft.securityCode,
                    onValueChange = {
                        draftViewModel.updateSecurityCode(it)
                        error = null
                    },
                    label = stringResource(R.string.security_code_optional),
                    isVisible = securityCodeVisible,
                    onVisibilityToggle = { securityCodeVisible = !securityCodeVisible },
                    isError = error != null && draft.securityCode.isNotEmpty() &&
                        draft.securityCode.length !in
                            AppConstants.MIN_SECURITY_CODE_LENGTH..
                                AppConstants.MAX_SECURITY_CODE_LENGTH,
                )
            }

            CustomFieldEditSection(
                fields = draft.customFields,
                parentScrollState = scrollState,
                snackbarHostState = snackbarHostState,
                showValidationErrors = customFieldValidationAttempted,
                onAdd = { type ->
                    error = null
                    customFieldValidationAttempted = false
                    draftViewModel.addCustomField(type)
                },
                onNameChange = { id, value ->
                    error = null
                    customFieldValidationAttempted = false
                    draftViewModel.updateCustomFieldName(id, value)
                },
                onValueChange = draftViewModel::updateCustomFieldValue,
                onTypeChange = draftViewModel::updateCustomFieldType,
                onRemove = draftViewModel::removeCustomField,
                onRestore = draftViewModel::restoreCustomField,
                onMove = draftViewModel::moveCustomField,
                onCopy = viewModel::copyCustomFieldValue,
            )

            EntryFormSectionHeader(title = stringResource(R.string.notes))
            EntryFormCard {
                EntryFormTextArea(
                    label = stringResource(R.string.notes_optional),
                    value = draft.notes,
                    onValueChange = draftViewModel::updateNotes,
                )
            }

            EntryFormSectionHeader(title = stringResource(R.string.category))
            EntryFormCard {
                EntryCategorySelectionSection(
                    categories = categories,
                    selectedCategoryIds = draft.selectedCategoryIds,
                    onCategoryAdd = draftViewModel::addCategory,
                    onCategoryRemove = draftViewModel::removeCategory,
                    onCreateCategory = viewModel::addCategory,
                )
            }

            EntryFormSectionHeader(title = stringResource(R.string.attachments))
            EntryFormCard {
                AttachmentSection(
                    attachmentIds = draft.selectedAttachmentIds,
                    allAttachments = attachments,
                    onAddAttachment = { uri ->
                        scope.launch {
                            try {
                                draftViewModel.addAttachment(viewModel.addAttachment(uri))
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: IllegalArgumentException) {
                                Toast.makeText(
                                    context,
                                    R.string.attachment_add_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    R.string.attachment_add_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onRemoveAttachment = draftViewModel::removeAttachment,
                    loadThumbnail = viewModel::loadAttachmentThumbnail,
                )
            }

            error?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        draftViewModel.clear()
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val cardDigits = paymentCardNumberDigits(draft.cardNumber)
                        val hasInvalidCustomField = draft.customFields.any { it.name.isBlank() }
                        val hasIncompleteExpiration =
                            (draft.expirationMonth == 0) != (draft.expirationYear == 0)
                        customFieldValidationAttempted = hasInvalidCustomField
                        val validationError = when {
                            draft.title.isBlank() -> context.getString(R.string.title_required)
                            cardDigits.length !in
                                AppConstants.MIN_PAYMENT_CARD_NUMBER_LENGTH..
                                    AppConstants.MAX_PAYMENT_CARD_NUMBER_LENGTH -> {
                                context.getString(R.string.invalid_card_number)
                            }
                            hasIncompleteExpiration -> {
                                context.getString(R.string.expiration_date_incomplete)
                            }
                            draft.securityCode.isNotEmpty() && draft.securityCode.length !in
                                AppConstants.MIN_SECURITY_CODE_LENGTH..
                                    AppConstants.MAX_SECURITY_CODE_LENGTH -> {
                                context.getString(R.string.invalid_security_code)
                            }
                            hasInvalidCustomField -> {
                                context.getString(R.string.custom_field_name_required)
                            }
                            else -> null
                        }
                        if (validationError != null) {
                            error = validationError
                        } else {
                            scope.launch {
                                isSaving = true
                                try {
                                    viewModel.addPaymentCard(
                                        title = draft.title,
                                        cardholderName = draft.cardholderName,
                                        cardNumber = cardDigits,
                                        cardBrand = draft.cardBrand,
                                        expirationMonth = draft.expirationMonth,
                                        expirationYear = draft.expirationYear,
                                        securityCode = draft.securityCode,
                                        notes = draft.notes.text,
                                        categoryIds = draft.selectedCategoryIds.toList(),
                                        attachmentIds = draft.selectedAttachmentIds.toList(),
                                        customFields = draft.customFields.toProtoCustomFields(),
                                    )
                                    customFieldValidationAttempted = false
                                    draftViewModel.clear()
                                    onNavigateBack()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    error = context.getString(R.string.save_failed)
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun SensitiveCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    isError: Boolean,
    visibleVisualTransformation: VisualTransformation = VisualTransformation.None,
) {
    EntryFormTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        visualTransformation = if (isVisible) {
            visibleVisualTransformation
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            autoCorrectEnabled = false,
        ),
        trailingContent = {
            IconButton(onClick = onVisibilityToggle) {
                Icon(
                    imageVector = if (isVisible) {
                        Icons.Outlined.Visibility
                    } else {
                        Icons.Outlined.VisibilityOff
                    },
                    contentDescription = stringResource(
                        if (isVisible) R.string.hide_sensitive_value else R.string.show_sensitive_value,
                    ),
                )
            }
        },
    )
}

@Composable
private fun <T> PaymentCardDropdown(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isValueSet: Boolean = true,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Box(modifier = modifier.fillMaxWidth()) {
        EntryFormSelectionField(
            label = label,
            value = value,
            isValueSet = isValueSet,
            isError = isError,
            onClick = {
                focusManager.clearFocus()
                expanded = true
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { (option, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
