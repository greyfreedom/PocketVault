package com.turisla.hellopocket.ui.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.turisla.hellopocket.R
import com.turisla.hellopocket.model.Category
import com.turisla.hellopocket.utils.AppConstants

@Composable
fun getCategoryDisplayName(category: Category): String {
    return when (category.id) {
        AppConstants.CATEGORY_ID_ALL -> stringResource(R.string.all_categories)
        AppConstants.CATEGORY_ID_UNCATEGORIZED -> stringResource(R.string.uncategorized)
        AppConstants.CATEGORY_ID_FAVORITES -> stringResource(R.string.favorites)
        else -> category.name
    }
}
