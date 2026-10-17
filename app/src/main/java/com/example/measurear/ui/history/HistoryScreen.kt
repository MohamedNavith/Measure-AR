package com.example.measurear.ui.history

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.measurear.data.MeasurementEntity
import com.example.measurear.ui.camera.MeasureViewModel
import com.example.measurear.ui.components.GlassmorphicCard
import com.example.measurear.ui.theme.ArYellow
import com.example.measurear.ui.theme.TabularMeasureTextStyle
import com.example.measurear.util.MeasureUnit
import com.example.measurear.util.UnitConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MeasureViewModel,
    onNavigateBack: () -> Unit
) {
    val historyItems by viewModel.historyList.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<MeasurementEntity?>(null) }

    Scaffold(
        containerColor = Color(0xFF0B0B0E),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Measurement History",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to AR Camera",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B0B0E)
                )
            )
        }
    ) { innerPadding ->
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Straighten,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No saved measurements yet",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = historyItems,
                    key = { it.id }
                ) { item ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteHistoryItem(item)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val bgColor by animateColorAsState(
                                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                    Color(0xFFB71C1C)
                                } else {
                                    Color(0xFF222228)
                                },
                                label = "swipeDeleteBg"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(bgColor)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete measurement",
                                    tint = Color.White
                                )
                            }
                        }
                    ) {
                        HistoryRowCard(
                            entity = item,
                            onClick = { selectedItem = item }
                        )
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        MeasurementDetailDialog(
            entity = item,
            onDismiss = { selectedItem = null },
            onDelete = {
                viewModel.deleteHistoryItem(item)
                selectedItem = null
            }
        )
    }
}

@Composable
private fun HistoryRowCard(
    entity: MeasurementEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, entity.thumbnailPath) {
        value = withContext(Dispatchers.IO) {
            val path = entity.thumbnailPath ?: return@withContext null
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        }
    }

    GlassmorphicCard(
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap!!,
                    contentDescription = "Measurement capture thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Straighten,
                        contentDescription = null,
                        tint = ArYellow
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.formattedValue,
                    style = TabularMeasureTextStyle.copy(
                        color = ArYellow,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entity.modeName} • ${entity.pointCount} pts",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(entity.timestamp)),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MeasurementDetailDialog(
    entity: MeasurementEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy 'at' h:mm:ss a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A20),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = entity.formattedValue,
                style = TabularMeasureTextStyle.copy(color = ArYellow, fontSize = 24.sp)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mode: ${entity.modeName} (${entity.pointCount} points)")
                Text(
                    text = "All Units:\n" +
                        "• ${UnitConverter.formatDistance(entity.primaryMeters, MeasureUnit.CM)}\n" +
                        "• ${UnitConverter.formatDistance(entity.primaryMeters, MeasureUnit.M)}\n" +
                        "• ${UnitConverter.formatDistance(entity.primaryMeters, MeasureUnit.IN)}\n" +
                        "• ${UnitConverter.formatDistance(entity.primaryMeters, MeasureUnit.FT)}",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (entity.pointsSummary.isNotBlank()) {
                    Text(
                        text = "Segments: ${entity.pointsSummary}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = dateFormat.format(Date(entity.timestamp)),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = ArYellow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = Color(0xFFFF5252))
            }
        }
    )
}
