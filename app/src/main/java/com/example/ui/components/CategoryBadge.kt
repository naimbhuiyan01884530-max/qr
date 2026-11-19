package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.data.local.ScanRecordEntity
import com.example.domain.model.ParsedBarcode
import com.example.domain.model.ScanCategory
import com.example.domain.parser.OfflineBarcodeParser
import com.example.ui.theme.CategoryContactColor
import com.example.ui.theme.CategoryProductColor
import com.example.ui.theme.CategoryTextColor
import com.example.ui.theme.CategoryTwoFaColor
import com.example.ui.theme.CategoryUrlColor
import com.example.ui.theme.CategoryWifiColor

fun ScanCategory.icon(): ImageVector = when (this) {
    ScanCategory.URL -> Icons.Rounded.Link
    ScanCategory.PRODUCT -> Icons.Rounded.ShoppingBag
    ScanCategory.WIFI -> Icons.Rounded.Wifi
    ScanCategory.TWO_FA -> Icons.Rounded.Key
    ScanCategory.CONTACT -> Icons.Rounded.ContactPage
    ScanCategory.TEXT -> Icons.Rounded.Notes
}

fun ScanCategory.accentColor(): Color = when (this) {
    ScanCategory.URL -> CategoryUrlColor
    ScanCategory.PRODUCT -> CategoryProductColor
    ScanCategory.WIFI -> CategoryWifiColor
    ScanCategory.TWO_FA -> CategoryTwoFaColor
    ScanCategory.CONTACT -> CategoryContactColor
    ScanCategory.TEXT -> CategoryTextColor
}

fun ScanRecordEntity.parsedCategory(): ScanCategory {
    return runCatching { ScanCategory.valueOf(category) }.getOrDefault(ScanCategory.TEXT)
}

fun ScanRecordEntity.toParsedBarcode(): ParsedBarcode {
    return OfflineBarcodeParser.parse(rawValue, formatCode)
}

@Composable
fun CategoryIconBadge(
    category: ScanCategory,
    modifier: Modifier = Modifier
) {
    val accent = category.accentColor()
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = category.icon(),
            contentDescription = category.displayName,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun FormatTagPill(
    label: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor
        )
    }
}
